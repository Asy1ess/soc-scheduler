package com.soc.scheduler.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.domain.PatternPresets
import com.soc.scheduler.ui.common.full
import com.soc.scheduler.ui.common.pickDate
import com.soc.scheduler.ui.common.pickTime
import com.soc.scheduler.ui.common.toColor
import com.soc.scheduler.ui.shift.PickTypeDialog

/**
 * 첫 실행 시 보여 주는 초기 설정 마법사.
 * 설정 화면에서 다시 열 수도 있으며, 그때는 [editMode] 로 현재 설정을 불러온다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    editMode: Boolean = false,
    onCancel: (() -> Unit)? = null,
    vm: OnboardingViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(editMode) {
        if (editMode) vm.loadCurrent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (editMode) "근무 설정 변경" else "초기 설정")
                        Text(
                            "${ui.step + 1}/${OnboardingViewModel.LAST_STEP + 1} · " +
                                OnboardingViewModel.STEP_TITLES[ui.step],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) { Text("취소") }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ui.step > 0) {
                    OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) {
                        Text("이전")
                    }
                }
                if (ui.step < OnboardingViewModel.LAST_STEP) {
                    Button(
                        onClick = vm::next,
                        enabled = ui.canGoNext,
                        modifier = Modifier.weight(2f),
                    ) { Text("다음") }
                } else {
                    Button(
                        onClick = { vm.finish(onDone) },
                        enabled = !ui.saving && ui.cycle.isNotEmpty(),
                        modifier = Modifier.weight(2f),
                    ) { Text(if (editMode) "저장" else "시작하기") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LinearProgressIndicator(
                progress = { (ui.step + 1f) / (OnboardingViewModel.LAST_STEP + 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (ui.step) {
                    0 -> StepShiftForm(ui, vm)
                    1 -> StepCycle(ui, vm)
                    2 -> StepTimes(ui, vm)
                    else -> StepRoutine(ui, vm)
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------- 1단계: 근무 형태

@Composable
private fun StepShiftForm(ui: OnboardingUi, vm: OnboardingViewModel) {
    StepHeader(
        "어떤 형태로 근무하시나요?",
        "가까운 것을 고르면 다음 단계에서 세부 조정을 할 수 있습니다.",
    )

    PatternPresets.ALL.forEach { preset ->
        val selected = ui.presetName == preset.name
        SelectableCard(selected = selected, onClick = { vm.selectPreset(preset) }) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            Text(
                preset.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SelectableCard(selected = ui.isCustom, onClick = { vm.selectCustom() }) {
        Text("직접 만들기", style = MaterialTheme.typography.titleMedium)
        Text(
            "근무 일수와 순서를 처음부터 직접 지정합니다",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            content()
        }
    }
}

// ------------------------------------------------------------- 2단계: 근무 주기

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCycle(ui: OnboardingUi, vm: OnboardingViewModel) {
    val context = LocalContext.current
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    StepHeader(
        "근무 주기를 확인해 주세요",
        "교대 근무를 시작한 날과, 그날이 사이클의 몇 번째 날인지를 정하면 근무표 전체가 맞춰집니다.",
    )

    Text("교대 근무 시작일", style = MaterialTheme.typography.titleMedium)
    Text(
        "이 날짜부터 아래 주기가 반복됩니다. 이전 날짜는 기본 주간 일정(평일 주간, 주말 휴무)으로 채워집니다.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = {
        pickDate(context, ui.startDate) { vm.setStartDate(it) }
    }) {
        Text(ui.startDate.full())
    }

    if (ui.isCustom) {
        Text("주기 길이", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2, 3, 4, 5, 6, 7, 8, 10, 12, 14).forEach { length ->
                FilterChip(
                    selected = ui.cycle.size == length,
                    onClick = { vm.setCycleLength(length) },
                    label = { Text("${length}일") },
                )
            }
        }
    }

    Text("주기 구성", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ui.cycle.forEachIndexed { index, typeId ->
            val type = ui.typeMap[typeId]
            val color = type?.colorArgb?.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.18f))
                    .clickable { editingIndex = index }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("${index + 1}일차", style = MaterialTheme.typography.labelSmall)
                Text(type?.shortLabel ?: "?", style = MaterialTheme.typography.titleMedium, color = color)
            }
        }
    }

    Text("시작일이 몇 일차인가요?", style = MaterialTheme.typography.titleMedium)
    Text(
        "${ui.startDate.full()}에 실제로 했던 근무와 같은 칸을 고르세요.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ui.cycle.forEachIndexed { index, typeId ->
            val type = ui.typeMap[typeId]
            FilterChip(
                selected = ui.startIndex == index,
                onClick = { vm.setStartIndex(index) },
                label = { Text("${index + 1}일차 ${type?.shortLabel ?: ""}") },
            )
        }
    }

    val preview = ui.preview()
    if (preview.isNotEmpty()) {
        Text("이렇게 적용됩니다", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            preview.forEach { (date, type) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${date.monthValue}/${date.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        type?.shortLabel ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        color = type?.colorArgb?.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "맨 왼쪽이 교대 근무 시작일입니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editingIndex?.let { index ->
        PickTypeDialog(
            title = "${index + 1}일차 근무",
            types = ui.types,
            onPick = {
                vm.setCycleDay(index, it.id)
                editingIndex = null
            },
            onDismiss = { editingIndex = null },
        )
    }
}

// ------------------------------------------------------------- 3단계: 근무 시간

@Composable
private fun StepTimes(ui: OnboardingUi, vm: OnboardingViewModel) {
    val context = LocalContext.current
    val used = ui.cycle.toSet()

    StepHeader(
        "근무 시간을 맞춰 주세요",
        "실제 교대 시각과 다르면 눌러서 수정하세요. 나중에 설정에서도 바꿀 수 있습니다.",
    )

    ui.types.filter { it.isWorking && used.contains(it.id) }.forEach { type ->
        ShiftTimeRow(type = type) { isStart ->
            val current = if (isStart) type.startTime else type.endTime
            val hour = current.substringBefore(":").toIntOrNull() ?: 9
            val minute = current.substringAfter(":", "").toIntOrNull() ?: 0
            pickTime(context, hour, minute) { h, m ->
                val value = String.format("%02d:%02d", h, m)
                if (isStart) {
                    vm.setShiftTime(type, value, type.endTime)
                } else {
                    vm.setShiftTime(type, type.startTime, value)
                }
            }
        }
    }

    if (ui.types.none { it.isWorking && used.contains(it.id) }) {
        Text(
            "이 주기에는 근무일이 없습니다. 이전 단계에서 주기를 확인해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ShiftTimeRow(type: ShiftType, onPick: (isStart: Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(type.colorArgb.toColor())
            )
            Text(type.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onPick(true) }) {
                Text(type.startTime.ifBlank { "시작" })
            }
            Text("~")
            OutlinedButton(onClick = { onPick(false) }) {
                Text(type.endTime.ifBlank { "종료" })
            }
        }
    }
}

// ------------------------------------------------------------- 4단계: 점검 루틴

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepRoutine(ui: OnboardingUi, vm: OnboardingViewModel) {
    StepHeader(
        "정기 점검 루틴을 고르세요",
        "선택한 항목이 주기에 맞춰 매일 자동으로 만들어집니다. 나중에 추가·수정할 수 있습니다.",
    )

    ui.templates.forEach { template ->
        val checked = ui.activeTemplateIds.contains(template.id)
        Card(
            Modifier
                .fillMaxWidth()
                .clickable { vm.toggleTemplate(template.id) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { vm.toggleTemplate(template.id) })
                Column(Modifier.weight(1f)) {
                    Text(template.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            com.soc.scheduler.data.Recurrence.label(template.recurrence),
                            template.timeLabel.takeIf { it.isNotBlank() },
                            template.memo.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (ui.overrideCount > 0) {
        Text("직접 바꾼 근무 정리", style = MaterialTheme.typography.titleMedium)
        Text(
            "예전에 직접 바꿔 둔 근무가 ${ui.overrideCount}건 있습니다. " +
                "새 스케줄을 적용할 때 어떻게 할지 골라 주세요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = ui.clearMode == OverrideClearMode.FUTURE,
                onClick = { vm.setClearMode(OverrideClearMode.FUTURE) },
                label = { Text("시작일 이후만 지우기 (${ui.futureOverrideCount})") },
            )
            FilterChip(
                selected = ui.clearMode == OverrideClearMode.ALL,
                onClick = { vm.setClearMode(OverrideClearMode.ALL) },
                label = { Text("전부 지우기 (${ui.overrideCount})") },
            )
            FilterChip(
                selected = ui.clearMode == OverrideClearMode.KEEP,
                onClick = { vm.setClearMode(OverrideClearMode.KEEP) },
                label = { Text("그대로 두기") },
            )
        }
        Text(
            "지운 날짜는 새 근무표대로 다시 계산됩니다. 시작일 이전에 직접 바꿔 둔 근무를 남기려면 '시작일 이후만'을 고르세요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (ui.templates.isEmpty()) {
        Text(
            "등록된 점검 항목이 없습니다. 나중에 점검 탭에서 추가하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Box(Modifier.height(8.dp))
}
