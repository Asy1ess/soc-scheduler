package com.soc.scheduler.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.soc.scheduler.Graph
import com.soc.scheduler.MainActivity
import com.soc.scheduler.data.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifications {
    const val CHANNEL_ID = "task_reminder"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "일정 알림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "등록한 일정과 점검 시간을 알려 줍니다."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** 일정 알림 예약/취소. 정확 알람이 막혀 있으면 근사 알람으로 낮춘다. */
object AlarmScheduler {

    fun schedule(context: Context, task: TaskItem) {
        cancel(context, task.id)
        if (task.done || task.reminderMinutesBefore < 0) return

        val triggerAt = task.dueAtMillis - task.reminderMinutesBefore * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, task)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    suspend fun rescheduleAll(context: Context) {
        val tasks = Graph.repo.taskDao.pendingReminders(System.currentTimeMillis())
        tasks.forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, task: TaskItem): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, task.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, task.title)
            putExtra(ReminderReceiver.EXTRA_DUE, task.dueAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "일정 알림"
        val due = intent.getLongExtra(EXTRA_DUE, 0L)

        val contentIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val time = if (due > 0) {
            SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREA).format(Date(due))
        } else {
            ""
        }

        val notification: Notification = androidx.core.app.NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(time)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id.toInt(), notification) }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DUE = "extra_due"
    }
}

/** 재부팅 후에도 알림이 남아 있도록 다시 예약한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(context)
                DailyBrief.reschedule(context)
                ShiftAlarms.reschedule(context)
                com.soc.scheduler.widget.WidgetUpdater.updateAll(context)
                com.soc.scheduler.widget.WidgetUpdater.scheduleMidnight(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
