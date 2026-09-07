package com.soc.scheduler.notify

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.soc.scheduler.ui.alarm.AlarmActivity

/**
 * 알람이 울리는 동안 소리와 진동을 담당하는 포그라운드 서비스.
 *
 * 화면이 꺼져 있어도 소리가 나야 하므로 액티비티가 아니라 서비스에서 재생한다.
 * 전체화면 알림으로 [AlarmActivity] 를 띄워 잠금화면 위에 해제 버튼을 보여 준다.
 */
class AlarmRingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(ShiftAlarms.EXTRA_SNOOZE, 5)
                if (minutes > 0) ShiftAlarms.snooze(this, minutes, intent)
                stopEverything()
                return START_NOT_STICKY
            }
        }

        val shiftName = intent?.getStringExtra(ShiftAlarms.EXTRA_SHIFT_NAME) ?: "근무"
        val shiftTime = intent?.getStringExtra(ShiftAlarms.EXTRA_SHIFT_TIME).orEmpty()
        val soundUri = intent?.getStringExtra(ShiftAlarms.EXTRA_SOUND).orEmpty()
        val vibrate = intent?.getBooleanExtra(ShiftAlarms.EXTRA_VIBRATE, true) ?: true
        val snooze = intent?.getIntExtra(ShiftAlarms.EXTRA_SNOOZE, 5) ?: 5

        startForeground(NOTIFICATION_ID, buildNotification(shiftName, shiftTime, snooze, intent))
        acquireWakeLock()
        startSound(soundUri)
        if (vibrate) startVibration()

        return START_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 내부

    private fun buildNotification(
        shiftName: String,
        shiftTime: String,
        snoozeMinutes: Int,
        source: Intent?,
    ): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            10,
            Intent(this, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtras(source?.extras ?: android.os.Bundle())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismiss = PendingIntent.getService(
            this,
            11,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, ShiftAlarms.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$shiftName 근무 알람")
            .setContentText(shiftTime.ifBlank { "일어날 시간입니다" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "해제", dismiss)

        if (snoozeMinutes > 0) {
            val snoozeIntent = Intent(this, AlarmRingService::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtras(source?.extras ?: android.os.Bundle())
            val snoozePending = PendingIntent.getService(
                this,
                12,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "${snoozeMinutes}분 뒤 다시", snoozePending)
        }

        return builder.build()
    }

    private fun startSound(soundUri: String) {
        val uri: Uri = soundUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return

        runCatching {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmRingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            // 사용자가 고른 음원을 못 읽으면 기본 알람음으로 한 번 더 시도한다.
            if (soundUri.isNotBlank()) startSound("")
        }

        // 알람 볼륨이 0이면 소리가 안 나므로 최소한으로 올려 준다.
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, max / 2, 0)
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 700, 700)
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SocScheduler:alarm",
            ).apply { acquire(10 * 60 * 1000L) }
        }
    }

    private fun stopEverything() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.soc.scheduler.alarm.START"
        const val ACTION_STOP = "com.soc.scheduler.alarm.STOP"
        const val ACTION_SNOOZE = "com.soc.scheduler.alarm.SNOOZE"
        private const val NOTIFICATION_ID = 9001

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP)
                )
            }
        }

        fun snooze(context: Context, source: Intent) {
            runCatching {
                context.startService(
                    Intent(context, AlarmRingService::class.java)
                        .setAction(ACTION_SNOOZE)
                        .putExtras(source.extras ?: android.os.Bundle())
                )
            }
        }
    }
}
