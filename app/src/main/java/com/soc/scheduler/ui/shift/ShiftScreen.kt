package com.soc.scheduler.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.ui.common.EmptyState
import com.soc.scheduler.ui.common.SectionCard
import com.soc.scheduler.ui.common.full
import com.soc.scheduler.ui.common.toColor
import com.soc.scheduler.ui.common.toLocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ShiftScreen(vm: ShiftViewModel = viewModel()) {
    val ui by vm.monthUi.collectAsStateWithLifecycle()
    val detail by vm.dayDetail.collectAsStateWithLifecycle()
    val allTypes by vm.shiftTypes.collectAsStateWithLifecycle()
    val relevantTypes by vm.relevantTypes.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var showOverrideDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthHeader(
            title = "${ui.month.year}년 ${ui.month.monthValue}월",
            subtitle = ui.patternName,
            onPrev = { vm.moveMonth(-1) },
            onNext = { vm.moveMonth(1) },
            onToday = { vm.goToday() },
        )

        if (!ui.hasPattern) {
            Text(
                "교대 패턴이 없습니다. 설정 › 교대 패턴에서 먼저 등록해 주세요.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        CalendarGrid(
            cells = ui.cells,
            selected = selected,
            onSelect = vm::select,
        )

        if (ui.summary.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ui.summary.take(5).forEach { (type, count) ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${type.shortLabel} $count", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = type.colorArgb.toColor().copy(alpha = 0.14f),
                            labelColor = type.colorArgb.toColor(),
                        ),
                    )
                }
            }
        }

        DayDetailCard(
            detail = detail,
            onChangeShift = { showOverrideDialog = true },
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showOverrideDialog) {
        OverrideDialog(
            date = selected,
            types = relevantTypes,
            allTypes = allTypes,
            baseType = detail.shift.baseType,
            isOverride = detail.shift.isOverride,
            nightType = relevantTypes.firstOrNull { vm.isNight(it) },
            offDutyName = vm.offDutyType()?.name.orEmpty(),
            onPick = { type, alsoNextDayOff ->
                vm.setOverride(selected, type, alsoNextDayOff)
                showOverrideDialog = false
            },
            onReset = {
                vm.clearOverride(selected)
                showOverrideDialog = false
            },
            onDismiss = { showOverrideDialog = false },
        )
    }
}

@Composable
private fun MonthHeader(
    title: String,
    subtitle: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
        TextButton(onClick = onToday) { Text("오늘") }
    }
}

@Composable
private fun CalendarGrid(
    cells: List<DayCell>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            ShiftViewModel.WEEKDAY_LABELS.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (index) {
                        0 -> Color(0xFFD32F2F)
                        6 -> Color(0xFF1976D2)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    DayCellView(
                        cell = cell,
                        isSelected = cell.date == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(cell.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCellView(
    cell: DayCell,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val type = cell.shift.type
    val base = type?.colorArgb?.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = if (cell.inMonth) 0.16f else 0.05f
    val isToday = cell.date == LocalDate.now()

    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(base.copy(alpha = alpha))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else if (isToday) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !cell.inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    cell.date.dayOfWeek.value == 7 -> Color(0xFFD32F2F)
                    cell.date.dayOfWeek.value == 6 -> Color(0xFF1976D2)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = type?.shortLabel ?: "-",
                style = MaterialTheme.typography.titleMedium,
                color = if (cell.inMonth) base else base.copy(alpha = 0.4f),
            )
            if (cell.taskCount > 0) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
        if (cell.shift.isOverride) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

@Composable
private fun DayDetailCard(detail: DayDetail, onChangeShift: () -> Unit) {
    val type = detail.shift.type
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    SectionCard(
        title = detail.date.full(),
        trailing = {
            OutlinedButton(onClick = onChangeShift) { Text("근무 변경") }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(type?.colorArgb?.toColor() ?: Color.Gray)
            )
            Text(
                text = type?.name ?: "근무 없음",
                style = MaterialTheme.typography.titleMedium,
            )
            if (type != null && type.startTime.isNotBlank()) {
                Text(
                    "${type.startTime} ~ ${type.endTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.shift.isOverride) {
                Text(
                    "수동 변경",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (detail.shift.beforeStart) {
            Text(
                "교대 근무 시작 전이라 기본 주간 일정이 적용된 날입니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (detail.shift.changedFromBase) {
            Text(
                "기존 근무: ${detail.shift.baseType?.name.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (detail.shift.memo.isNotBlank()) {
            Text(
                "변경 사유: ${detail.shift.memo}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("일정 ${detail.tasks.size}건", style = MaterialTheme.typography.labelSmall)

        if (detail.tasks.isEmpty()) {
            EmptyState("등록된 일정이 없습니다.")
        } else {
            detail.tasks.forEach { task ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        task.dueAtMillis.toLocalDateTime().toLocalTime().format(timeFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverrideDialog(
    date: LocalDate,
    types: List<ShiftType>,
    allTypes: List<ShiftType>,
    baseType: ShiftType?,
    isOverride: Boolean,
    nightType: ShiftType?,
    offDutyName: String,
    onPick: (ShiftType, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    var alsoNextDayOff by remember { mutableStateOf(true) }
    val shown = if (showAll) allTypes else types
    val hiddenCount = allTypes.size - types.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.full()} 근무 변경") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (baseType != null) {
                    Text(
                        "기존 근무: ${baseType.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                shown.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(type, alsoNextDayOff) }
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
                        Text(type.name, style = MaterialTheme.typography.bodyMedium)
                        if (type.startTime.isNotBlank()) {
                            Text(
                                "${type.startTime}~${type.endTime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (baseType != null && type.id == baseType.id) {
                            Text(
                                "기존",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (!showAll && hiddenCount > 0) {
                    TextButton(onClick = { showAll = true }) {
                        Text("다른 근무 유형도 보기 ($hiddenCount)")
                    }
                }

                if (nightType != null && offDutyName.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { alsoNextDayOff = !alsoNextDayOff }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = alsoNextDayOff, onCheckedChange = { alsoNextDayOff = it })
                        Text(
                            "${nightType.name} 선택 시 다음 날도 ${offDutyName}으로 함께 변경",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isOverride) {
                TextButton(onClick = onReset) { Text("패턴대로 되돌리기") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
