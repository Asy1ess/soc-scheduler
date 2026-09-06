package com.soc.scheduler.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.CheckTemplate
import com.soc.scheduler.data.Recurrence
import com.soc.scheduler.ui.common.EmptyState
import com.soc.scheduler.ui.common.pickTime

private val WEEKDAY_NAMES = listOf(1 to "월", 2 to "화", 3 to "수", 4 to "목", 5 to "금", 6 to "토", 7 to "일")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(onBack: () -> Unit, vm: ChecklistViewModel = viewModel()) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CheckTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("점검 항목 관리") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = CheckTemplate(title = "", recurrence = Recurrence.DAILY, timeLabel = "09:00")
            }) { Icon(Icons.Default.Add, "항목 추가") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (templates.isEmpty()) {
                item { EmptyState("등록된 점검 항목이 없습니다.") }
            }
            items(templates, key = { it.id }) { template ->
                Card(
                    Modifier.fillMaxWidth().clickable { editing = template },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(template.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                describe(template),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = template.active,
                            onCheckedChange = { vm.saveTemplate(template.copy(active = it)) },
                        )
                        IconButton(onClick = { vm.deleteTemplate(template.id) }) {
                            Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    editing?.let { template ->
        TemplateEditDialog(
            initial = template,
            onSave = {
                vm.saveTemplate(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun describe(template: CheckTemplate): String = buildString {
    append(Recurrence.label(template.recurrence))
    when (template.recurrence) {
        Recurrence.WEEKLY -> {
            val names = template.weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                .mapNotNull { value -> WEEKDAY_NAMES.firstOrNull { it.first == value }?.second }
            if (names.isNotEmpty()) append(" ${names.joinToString("·")}요일")
        }
        Recurrence.MONTHLY -> append(" ${template.monthDay}일")
        else -> Unit
    }
    if (template.timeLabel.isNotBlank()) append(" · ${template.timeLabel}")
    if (template.memo.isNotBlank()) append(" · ${template.memo}")
    if (!template.active) append(" · 사용 안 함")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditDialog(
    initial: CheckTemplate,
    onSave: (CheckTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var memo by remember { mutableStateOf(initial.memo) }
    var recurrence by remember { mutableStateOf(initial.recurrence) }
    var weekDays by remember {
        mutableStateOf(initial.weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet())
    }
    var monthDay by remember { mutableStateOf(initial.monthDay) }
    var timeLabel by remember { mutableStateOf(initial.timeLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "점검 항목 추가" else "점검 항목 수정") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("점검 항목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("설명") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Text("반복", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.ALL.forEach { value ->
                        FilterChip(
                            selected = recurrence == value,
                            onClick = { recurrence = value },
                            label = { Text(Recurrence.label(value)) },
                        )
                    }
                }

                if (recurrence == Recurrence.WEEKLY) {
                    Text("요일", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEKDAY_NAMES.forEach { (value, name) ->
                            FilterChip(
                                selected = weekDays.contains(value),
                                onClick = {
                                    weekDays = if (weekDays.contains(value)) weekDays - value else weekDays + value
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }

                if (recurrence == Recurrence.MONTHLY) {
                    Text("매월 며칠", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 5, 10, 15, 20, 25, 28).forEach { day ->
                            FilterChip(
                                selected = monthDay == day,
                                onClick = { monthDay = day },
                                label = { Text("${day}일") },
                            )
                        }
                    }
                }

                OutlinedButton(onClick = {
                    val parts = timeLabel.split(":")
                    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    pickTime(context, hour, minute) { h, m ->
                        timeLabel = String.format("%02d:%02d", h, m)
                    }
                }) {
                    Text(if (timeLabel.isBlank()) "시각 선택" else "점검 시각 $timeLabel")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            memo = memo.trim(),
                            recurrence = recurrence,
                            weekDays = weekDays.sorted().joinToString(","),
                            monthDay = monthDay,
                            timeLabel = timeLabel,
                        )
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
