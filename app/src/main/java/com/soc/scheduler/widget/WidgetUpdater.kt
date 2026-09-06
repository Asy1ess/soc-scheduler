package com.soc.scheduler.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/** 데이터가 바뀌었을 때 홈 화면 위젯을 다시 그리게 한다. */
object WidgetUpdater {

    private val PROVIDERS = listOf(
        TodayShiftWidget::class.java,
        WeekShiftWidget::class.java,
        MonthShiftWidget::class.java,
        TodayTaskWidget::class.java,
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        PROVIDERS.forEach { provider ->
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, provider))
            }.getOrNull() ?: return@forEach
            if (ids.isEmpty()) return@forEach

            val intent = Intent(context, provider)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    /** 자정이 지나면 "오늘"이 바뀌므로 날짜가 넘어갈 때 한 번 갱신한다. */
    fun scheduleMidnight(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() + 60_000L

        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_MIDNIGHT,
            Intent(context, MidnightReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC, triggerAt, pending)
            }
        }
    }

    private const val REQUEST_MIDNIGHT = 90_001
}

/** 자정 알람 수신 → 위젯 갱신 후 다음 자정 재예약 */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.updateAll(context)
        WidgetUpdater.scheduleMidnight(context)
    }
}
