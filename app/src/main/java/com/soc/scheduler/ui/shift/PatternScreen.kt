package com.soc.scheduler.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.domain.PatternPreset
import com.soc.scheduler.domain.PatternPresets
import com.soc.scheduler.ui.common.SectionCard
import com.soc.scheduler.ui.common.full
import com.soc.scheduler.ui.common.pickDate
import com.soc.scheduler.ui.common.toColor
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatternScreen(onBack: () -> Unit, vm: PatternViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var presetToApply by remember { mutableStateOf<PatternPreset?>(null) }
    var editingDayIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("교대 패턴") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "현재 패턴") {
                val pattern = ui.pattern
                if (pattern == null) {
                    Text("적용된 패턴이 없습니다. 아래에서 하나를 선택해 주세요.")
                } else {
                    Text(pattern.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pattern.cycleDays}일 주기 · ${pattern.teamCount}개 조",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("기준일", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            pickDate(context, LocalDate.ofEpochDay(pattern.anchorEpochDay)) { vm.setAnchor(it) }
                        }) {
                            Text(LocalDate.ofEpochDay(pattern.anchorEpochDay).full())
                        }
                    }
                    Text(
                        "기준일이 사이클 첫째 날입니다. 실제 근무와 어긋나면 기준일 또는 조 번호를 조정하세요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("근무 시작일", style = MaterialTheme.typography.bodyMedium)
                        val start = pattern.startEpochDay?.let { LocalDate.ofEpochDay(it) }
                        OutlinedButton(onClick = {
                            pickDate(context, start ?: LocalDate.now()) { vm.setStartDate(it) }
                        }) {
                            Text(start?.full() ?: "설정 안 함")
                        }
                        if (start != null) {
                            TextButton(onClick = { vm.setStartDate(null) }) { Text("해제") }
                        }
                    }
                    Text(
                        "수습 기간처럼 교대 근무를 하지 않은 구간을 빼려면 실제 근무 시작일을 지정하세요. 그 이전 날짜는 근무표에 표시되지 않습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (pattern.teamCount > 1) {
                        Text("내 조", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..pattern.teamCount).forEach { team ->
                                FilterChip(
                                    selected = vm.teamNumberOf(pattern) == team,
                                    onClick = { vm.setTeam(team) },
                                    label = { Text("${team}조") },
                                )
                            }
                        }
                    }

                    Text("사이클 (탭하여 변경)", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.days.forEach { day ->
                            val type = ui.types.firstOrNull { it.id == day.shiftTypeId }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background((type?.colorArgb?.toColor() ?: MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.18f))
                                    .clickable { editingDayIndex = day.dayIndex }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text("${day.dayIndex + 1}일차", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    type?.shortLabel ?: "?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = type?.colorArgb?.toColor() ?: MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    Text("다음 7일 미리보기", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.preview.forEach { (date, type) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${date.monthValue}/${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    type?.shortLabel ?: "-",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = type?.colorArgb?.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SectionCard(title = "패턴 프리셋") {
                PatternPresets.ALL.forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { presetToApply = preset }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(preset.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                preset.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("적용", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    presetToApply?.let { preset ->
        ApplyPresetDialog(
            preset = preset,
            onApply = { anchor, team ->
                vm.applyPreset(preset, anchor, team)
                presetToApply = null
            },
            onDismiss = { presetToApply = null },
        )
    }

    editingDayIndex?.let { index ->
        PickTypeDialog(
            title = "${index + 1}일차 근무",
            types = ui.types,
            onPick = {
                vm.setCycleDay(index, it.id)
                editingDayIndex = null
            },
            onDismiss = { editingDayIndex = null },
        )
    }
}

@Composable
private fun ApplyPresetDialog(
    preset: PatternPreset,
    onApply: (LocalDate, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var team by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(preset.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "기준일에 사이클 1일차가 시작됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { pickDate(context, anchor) { anchor = it } }) {
                    Text("기준일: ${anchor.full()}")
                }
                if (preset.teamCount > 1) {
                    Text("내 조 선택", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..preset.teamCount).forEach { number ->
                            FilterChip(
                                selected = team == number,
                                onClick = { team = number },
                                label = { Text("${number}조") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(anchor, team) }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun PickTypeDialog(
    title: String,
    types: List<ShiftType>,
    onPick: (ShiftType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                types.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(type) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(type.colorArgb.toColor())
                        )
                        Text(type.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
