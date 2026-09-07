package com.soc.scheduler.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.soc.scheduler.Graph
import com.soc.scheduler.MainActivity
import com.soc.scheduler.data.Prefs
import com.soc.scheduler.ui.common.endOfDayMillis
import com.soc.scheduler.ui.common.startOfDayMillis
import com.soc.scheduler.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 매일 정해진 시각에 그날 근무를 알려 준다.
 *
 * 알림 자체는 잠금화면에도 표시되므로, 잠금화면 위젯을 못 쓰는 기기에서도
 * 아침에 오늘 근무를 확인할 수 있다.
 */
object DailyBrief {

    const val CHANNEL_ID = "daily_brief"
    private const val NOTIFICATION_ID = 8001
    private const val REQUEST_CODE = 91_001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "아침 근무 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "매일 정해진 시각에 그날의 근무를 알려 줍니다."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 설정에 따라 다음 알림을 예약하거나 취소한다. 설정을 바꿀 때마다 호출한다. */
    fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyBriefReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (!Prefs.isBriefEnabled(context)) {
            alarmManager.cancel(pending)
            return
        }

        val time = LocalTime.of(Prefs.briefHour(context), Prefs.briefMinute(context))
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    /** 오늘 근무를 읽어 알림을 띄운다. 알릴 내용이 없으면 아무것도 하지 않는다. */
    suspend fun notifyToday(context: Context) {
        val today = LocalDate.now()
        val data = WidgetData.load(today, today)
        val shift = data.shiftAt(today)
        val type = shift.type

        if (type == null && !Prefs.briefOnRestDays(context)) return
        if (type != null && !type.isWorking && !Prefs.briefOnRestDays(context)) return

        val repo = Graph.repo
        runCatching { repo.ensureCheckRuns(today) }
        val checks = runCatching { repo.checkDao.runsOnce(today.toEpochDay()) }.getOrDefault(emptyList())
        val tasks = runCatching {
            repo.taskDao.tasksBetween(today.startOfDayMillis(), today.endOfDayMillis())
                .filter { !it.done }
        }.getOrDefault(emptyList())

        val title = when {
            type == null -> "오늘은 근무 정보가 없습니다"
            type.isWorking -> "오늘은 ${type.name} 근무입니다"
            else -> "오늘은 ${type.name}입니다"
        }

        val lines = buildList {
            if (type != null && type.startTime.isNotBlank()) {
                add("근무 시간 ${type.startTime} ~ ${type.endTime}")
            }
            if (checks.isNotEmpty()) {
                add("점검 ${checks.size}건")
            }
            tasks.take(3).forEach { add("일정 · ${it.title}") }
            if (tasks.size > 3) add("일정 외 ${tasks.size - 3}건")
        }

        val summary = lines.joinToString(" · ").ifBlank { "등록된 일정과 점검이 없습니다." }

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n").ifBlank { summary }))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        type?.colorArgb?.let { builder.setColor(it.toInt()).setColorized(false) }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }
}

/** 예약된 시각에 깨어나 알림을 띄우고 다음 날을 다시 예약한다. */
class DailyBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyBrief.notifyToday(context)
            } catch (e: Exception) {
                // 알림 실패가 다음 예약까지 막지 않도록 삼킨다.
            } finally {
                DailyBrief.reschedule(context)
                pendingResult.finish()
            }
        }
    }
}
