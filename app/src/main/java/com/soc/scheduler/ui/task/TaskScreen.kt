package com.soc.scheduler.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.TaskItem
import com.soc.scheduler.ui.common.EmptyState
import com.soc.scheduler.ui.common.display
import com.soc.scheduler.ui.common.pickDate
import com.soc.scheduler.ui.common.pickTime
import com.soc.scheduler.ui.common.toLocalDateTime
import com.soc.scheduler.ui.common.toMillis
import java.time.LocalDate
import java.time.LocalDateTime

private val REMINDER_OPTIONS = listOf(
    -1 to "없음",
    0 to "정시",
    10 to "10분 전",
    30 to "30분 전",
    60 to "1시간 전",
    180 to "3시간 전",
    1440 to "1일 전",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(vm: TaskViewModel = viewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var showDone by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("일정 · 할 일") },
                actions = {
                    TextButton(onClick = { showDone = !showDone }) {
                        Text(if (showDone) "진행 중" else "완료 (${groups.done.size})")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = TaskItem(
                    title = "",
                    dueAtMillis = LocalDateTime.now().withMinute(0).plusHours(1).toMillis(),
                )
            }) { Icon(Icons.Default.Add, "일정 추가") }
        },
    ) { padding ->
        val list = if (showDone) {
            listOf("완료" to groups.done)
        } else {
            listOf(
                "지난 일정" to groups.overdue,
                "오늘" to groups.today,
                "예정" to groups.upcoming,
            )
        }

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (list.all { it.second.isEmpty() }) {
                item { EmptyState("등록된 일정이 없습니다. 오른쪽 아래 + 로 추가하세요.") }
            }
            list.forEach { (title, tasks) ->
                if (tasks.isNotEmpty()) {
                    item(key = "header-$title") {
                        Text(
                            "$title (${tasks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { vm.toggleDone(task) },
                            onClick = { editing = task },
                            onDelete = { vm.delete(task) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { task ->
        TaskEditDialog(
            initial = task,
            onSave = {
                vm.save(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.priority >= 2) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    )
                }
                Text(
                    buildString {
                        append(task.dueAtMillis.toLocalDateTime().display())
                        append(" · ")
                        append(task.category)
                        if (task.reminderMinutesBefore >= 0) {
                            append(" · 알림 ")
                            append(REMINDER_OPTIONS.firstOrNull { it.first == task.reminderMinutesBefore }?.second ?: "설정됨")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task.memo.isNotBlank()) {
                    Text(
                        task.memo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditDialog(
    initial: TaskItem,
    onSave: (TaskItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var memo by remember { mutableStateOf(initial.memo) }
    var category by remember { mutableStateOf(initial.category) }
    var priority by remember { mutableStateOf(initial.priority) }
    var reminder by remember { mutableStateOf(initial.reminderMinutesBefore) }
    var dateTime by remember { mutableStateOf(initial.dueAtMillis.toLocalDateTime()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "일정 추가" else "일정 수정") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickDate(context, dateTime.toLocalDate()) { picked ->
                                dateTime = LocalDateTime.of(picked, dateTime.toLocalTime())
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("${dateTime.monthValue}/${dateTime.dayOfMonth}")
                    }
                    OutlinedButton(
                        onClick = {
                            pickTime(context, dateTime.hour, dateTime.minute) { hour, minute ->
                                dateTime = dateTime.withHour(hour).withMinute(minute)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(String.format("%02d:%02d", dateTime.hour, dateTime.minute))
                    }
                }

                Text("분류", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskCategory.ALL.forEach { name ->
                        FilterChip(
                            selected = category == name,
                            onClick = { category = name },
                            label = { Text(name) },
                        )
                    }
                }

                Text("알림", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REMINDER_OPTIONS.forEach { (minutes, label) ->
                        FilterChip(
                            selected = reminder == minutes,
                            onClick = { reminder = minutes },
                            label = { Text(label) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("중요", style = MaterialTheme.typography.labelSmall)
                    FilterChip(
                        selected = priority >= 2,
                        onClick = { priority = if (priority >= 2) 1 else 2 },
                        label = { Text(if (priority >= 2) "중요 표시됨" else "보통") },
                    )
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
                            category = category,
                            priority = priority,
                            reminderMinutesBefore = reminder,
                            dueAtMillis = dateTime.toMillis(),
                        )
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
