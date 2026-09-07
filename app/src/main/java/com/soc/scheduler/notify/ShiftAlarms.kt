package com.soc.scheduler.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.soc.scheduler.Graph
import com.soc.scheduler.data.ShiftAlarm
import com.soc.scheduler.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 다음에 울릴 알람 하나 */
data class PendingShiftAlarm(
    val at: LocalDateTime,
    val shiftTypeId: Long,
    val shiftName: String,
    val shiftTime: String,
    val alarm: ShiftAlarm,
)

/**
 * 근무 유형별 기상 알람.
 *
 * 예를 들어 야간 근무인 날 16:00 에 깨우도록 설정하면,
 * 근무표에서 야간인 날마다 그 시각에 알람이 울린다.
 *
 * 앞으로 울릴 알람 중 **가장 이른 것 하나만** 예약해 두고,
 * 울린 뒤 다시 다음 것을 예약하는 방식이다. (대기 중인 알람이 쌓이지 않는다)
 */
object ShiftAlarms {

    const val CHANNEL_ID = "shift_alarm"
    private const val REQUEST_CODE = 92_001
    private const val LOOKAHEAD_DAYS = 90L

    const val EXTRA_TYPE_ID = "extra_type_id"
    const val EXTRA_SHIFT_NAME = "extra_shift_name"
    const val EXTRA_SHIFT_TIME = "extra_shift_time"
    const val EXTRA_SOUND = "extra_sound"
    const val EXTRA_VIBRATE = "extra_vibrate"
    const val EXTRA_SNOOZE = "extra_snooze"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "근무 기상 알람",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "근무일에 맞춰 울리는 기상 알람입니다."
            // 소리는 서비스가 직접 재생하므로 채널 자체는 무음으로 둔다.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 앞으로 울릴 알람 중 가장 이른 것을 찾는다. */
    suspend fun findNext(from: LocalDateTime = LocalDateTime.now()): PendingShiftAlarm? {
        val alarms = Graph.repo.shiftAlarmDao.all().filter { it.enabled }
        if (alarms.isEmpty()) return null
        val byType = alarms.associateBy { it.shiftTypeId }

        val startDate = from.toLocalDate()
        val data = WidgetData.load(startDate, startDate.plusDays(LOOKAHEAD_DAYS))

        var date = startDate
        val limit = startDate.plusDays(LOOKAHEAD_DAYS)
        while (!date.isAfter(limit)) {
            val type = data.shiftAt(date).type
            val alarm = type?.let { byType[it.id] }
            if (type != null && alarm != null) {
                val at = LocalDateTime.of(date, LocalTime.of(alarm.hour, alarm.minute))
                if (at.isAfter(from)) {
                    val timeText = if (type.startTime.isBlank()) {
                        ""
                    } else {
                        "${type.startTime} ~ ${type.endTime}"
                    }
                    return PendingShiftAlarm(at, type.id, type.name, timeText, alarm)
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** 근무표나 알람 설정이 바뀔 때마다 호출한다. */
    suspend fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = findNext()

        if (next == null) {
            alarmManager.cancel(basePendingIntent(context, Intent(context, ShiftAlarmReceiver::class.java)))
            return
        }

        val intent = Intent(context, ShiftAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE_ID, next.shiftTypeId)
            putExtra(EXTRA_SHIFT_NAME, next.shiftName)
            putExtra(EXTRA_SHIFT_TIME, next.shiftTime)
            putExtra(EXTRA_SOUND, next.alarm.soundUri)
            putExtra(EXTRA_VIBRATE, next.alarm.vibrate)
            putExtra(EXTRA_SNOOZE, next.alarm.snoozeMinutes)
        }
        val pending = basePendingIntent(context, intent)
        val triggerAt = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canExact) {
                // 알람 시계로 등록하면 절전 모드에서도 정확히 울리고 상태바에 알람 아이콘이 뜬다.
                val showIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE + 1,
                    Intent(context, com.soc.scheduler.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    pending,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    /** 스누즈: 지정한 분 뒤에 같은 알람을 한 번 더 울린다. */
    fun snooze(context: Context, minutes: Int, intent: Intent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val next = Intent(context, ShiftAlarmReceiver::class.java).apply {
            putExtras(intent.extras ?: android.os.Bundle())
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE + 2,
            next,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun basePendingIntent(context: Context, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** 알람 시각에 깨어나 울림 서비스를 띄우고 다음 알람을 예약한다. */
class ShiftAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_START
            putExtras(intent.extras ?: android.os.Bundle())
        }
        runCatching { context.startForegroundService(service) }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ShiftAlarms.reschedule(context)
            } catch (e: Exception) {
                // 다음 예약 실패가 지금 울리는 알람을 막지 않도록 삼킨다.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
