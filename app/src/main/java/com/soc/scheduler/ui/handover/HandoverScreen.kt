package com.soc.scheduler.ui.handover

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.HandoverCategory
import com.soc.scheduler.data.HandoverNote
import com.soc.scheduler.data.Severity
import com.soc.scheduler.ui.common.EmptyState
import com.soc.scheduler.ui.common.full
import com.soc.scheduler.ui.common.pickDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HandoverScreen(vm: HandoverViewModel = viewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.categoryFilter.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HandoverNote?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("인수인계 · 업무일지") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = HandoverNote(
                    epochDay = LocalDate.now().toEpochDay(),
                    shiftLabel = "",
                    category = HandoverCategory.ALL.first(),
                    severity = Severity.NORMAL,
                    title = "",
                    content = "",
                    createdAtMillis = System.currentTimeMillis(),
                )
            }) { Icon(Icons.Default.Add, "기록 추가") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("내용 검색") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            FlowRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { vm.setCategory(null) },
                    label = { Text("전체") },
                )
                HandoverCategory.ALL.forEach { name ->
                    FilterChip(
                        selected = category == name,
                        onClick = { vm.setCategory(name) },
                        label = { Text(name) },
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (notes.isEmpty()) {
                    item { EmptyState("기록이 없습니다. 오른쪽 아래 + 로 추가하세요.") }
                }
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { editing = note },
                        onDelete = { vm.delete(note) },
                    )
                }
            }
        }
    }

    editing?.let { note ->
        NoteEditDialog(
            initial = note,
            onSave = {
                vm.save(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun severityColor(severity: Int): Color = when (severity) {
    Severity.CRITICAL -> Color(0xFFD32F2F)
    Severity.CAUTION -> Color(0xFFF57C00)
    else -> Color(0xFF388E3C)
}

@Composable
private fun NoteCard(note: HandoverNote, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(note.category, style = MaterialTheme.typography.labelSmall) },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(Severity.label(note.severity), style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = severityColor(note.severity).copy(alpha = 0.15f),
                            labelColor = severityColor(note.severity),
                        ),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(note.title, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(LocalDate.ofEpochDay(note.epochDay).full())
                    if (note.shiftLabel.isNotBlank()) append(" · ${note.shiftLabel}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note.content.isNotBlank()) {
                Text(note.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditDialog(
    initial: HandoverNote,
    onSave: (HandoverNote) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(initial.epochDay)) }
    var shiftLabel by remember { mutableStateOf(initial.shiftLabel) }
    var category by remember { mutableStateOf(initial.category) }
    var severity by remember { mutableStateOf(initial.severity) }
    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "기록 추가" else "기록 수정") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }) {
                    Text(date.full())
                }
                OutlinedTextField(
                    value = shiftLabel,
                    onValueChange = { shiftLabel = it },
                    label = { Text("근무조 / 담당 (예: 야간 A조)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("내용 / 조치사항") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )

                Text("분류", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HandoverCategory.ALL.forEach { name ->
                        FilterChip(
                            selected = category == name,
                            onClick = { category = name },
                            label = { Text(name) },
                        )
                    }
                }

                Text("중요도", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Severity.NORMAL, Severity.CAUTION, Severity.CRITICAL).forEach { level ->
                        FilterChip(
                            selected = severity == level,
                            onClick = { severity = level },
                            label = { Text(Severity.label(level)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            epochDay = date.toEpochDay(),
                            shiftLabel = shiftLabel.trim(),
                            category = category,
                            severity = severity,
                            title = title.trim(),
                            content = content.trim(),
                            createdAtMillis = if (initial.id == 0L) System.currentTimeMillis() else initial.createdAtMillis,
                        )
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
