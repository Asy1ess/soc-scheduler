package com.soc.scheduler.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soc.scheduler.notify.AlarmRingService
import com.soc.scheduler.notify.ShiftAlarms
import com.soc.scheduler.ui.theme.SocSchedulerTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 알람이 울릴 때 잠금화면 위에 뜨는 화면. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val shiftName = intent.getStringExtra(ShiftAlarms.EXTRA_SHIFT_NAME) ?: "근무"
        val shiftTime = intent.getStringExtra(ShiftAlarms.EXTRA_SHIFT_TIME).orEmpty()
        val snooze = intent.getIntExtra(ShiftAlarms.EXTRA_SNOOZE, 5)

        setContent {
            SocSchedulerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AlarmScreen(
                        shiftName = shiftName,
                        shiftTime = shiftTime,
                        snoozeMinutes = snooze,
                        onDismiss = {
                            AlarmRingService.stop(this)
                            finish()
                        },
                        onSnooze = {
                            AlarmRingService.snooze(this, intent)
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmScreen(
    shiftName: String,
    shiftTime: String,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            now.format(dateFmt),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            now.format(timeFmt),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "오늘은 $shiftName 근무입니다",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (shiftTime.isNotBlank()) {
            Text(
                shiftTime,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("알람 해제") }

        if (snoozeMinutes > 0) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("${snoozeMinutes}분 뒤 다시 울리기") }
        }
    }
}
