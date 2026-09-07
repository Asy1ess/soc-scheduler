package com.soc.scheduler.ui.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.ShiftAlarm
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.ui.common.SectionCard
import com.soc.scheduler.ui.common.pickTime
import com.soc.scheduler.ui.common.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsScreen(
    onBack: () -> Unit,
    vm: AlarmSettingsViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingFor by remember { mutableStateOf<Long?>(null) }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val typeId = pickingFor
        pickingFor = null
        if (result.resultCode != Activity.RESULT_OK || typeId == null) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(
                it,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            )
        }
        val type = ui.workingTypes.firstOrNull { it.id == typeId }
        if (type != null) vm.setSound(type, uri?.toString().orEmpty())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("근무 기상 알람") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "근무 유형별로 켜기만 하면 됩니다. 켜면 기본 시각(주간 07:30, 야간 16:00)이 들어가고, " +
                    "근무표에서 그 근무인 날에만 알람이 울립니다. 시각은 눌러서 바꿀 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "비번·휴무처럼 근무가 없는 날은 알람이 울리지 않습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (ui.nextText.isNotBlank()) {
                SectionCard(title = "다음 알람") {
                    Text(ui.nextText, style = MaterialTheme.typography.titleMedium)
                }
            }

            ui.workingTypes.forEach { type ->
                val alarm = ui.alarmOf(type)
                AlarmCard(
                    type = type,
                    alarm = alarm,
                    soundLabel = soundLabel(context, alarm.soundUri),
                    onToggle = { vm.setEnabled(type, it) },
                    onPickTime = {
                        pickTime(context, alarm.hour, alarm.minute) { h, m ->
                            vm.setTime(type, h, m)
                        }
                    },
                    onPickSound = {
                        pickingFor = type.id
                        ringtonePicker.launch(ringtonePickerIntent(alarm.soundUri))
                    },
                    onToggleVibrate = { vm.setVibrate(type, it) },
                    onSnoozeChange = { vm.setSnooze(type, it) },
                )
            }

            Text(
                "알람은 정확한 알람 권한이 필요합니다. 울리지 않으면 설정 › 알림 › 정확한 알람 허용을 확인하세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmCard(
    type: ShiftType,
    alarm: ShiftAlarm,
    soundLabel: String,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onPickSound: () -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onSnoozeChange: (Int) -> Unit,
) {
    SectionCard(
        title = type.name,
        trailing = { Switch(checked = alarm.enabled, onCheckedChange = onToggle) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(type.colorArgb.toColor())
            )
            Text(
                if (type.startTime.isBlank()) "휴식" else "근무 ${type.startTime} ~ ${type.endTime}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (alarm.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("기상 시각", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPickTime) {
                    Text(String.format("%02d:%02d", alarm.hour, alarm.minute))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("알람음", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPickSound) { Text(soundLabel) }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("진동", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = alarm.vibrate, onCheckedChange = onToggleVibrate)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("다시 울림", style = MaterialTheme.typography.bodyMedium)
                listOf(0, 5, 10, 15).forEach { minutes ->
                    androidx.compose.material3.FilterChip(
                        selected = alarm.snoozeMinutes == minutes,
                        onClick = { onSnoozeChange(minutes) },
                        label = { Text(if (minutes == 0) "안 함" else "${minutes}분") },
                    )
                }
            }
        }
    }
}

private fun ringtonePickerIntent(current: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "알람음 선택")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            current.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
        )
    }

private fun soundLabel(context: Context, uri: String): String {
    if (uri.isBlank()) return "기본 알람음"
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) ?: "선택한 음원"
    }.getOrDefault("선택한 음원")
}
