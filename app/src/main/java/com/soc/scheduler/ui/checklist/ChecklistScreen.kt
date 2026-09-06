package com.soc.scheduler.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.data.CheckRunView
import com.soc.scheduler.ui.common.EmptyState
import com.soc.scheduler.ui.common.full
import com.soc.scheduler.ui.common.pickDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onManageTemplates: () -> Unit,
    vm: ChecklistViewModel = viewModel(),
) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var noteTarget by remember { mutableStateOf<CheckRunView?>(null) }

    val doneCount = runs.count { it.done }
    val progress = if (runs.isEmpty()) 0f else doneCount.toFloat() / runs.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정기 점검") },
                actions = {
                    TextButton(onClick = onManageTemplates) { Text("항목 관리") }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.moveDay(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 날") }
                Text(
                    date.full(),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { pickDate(context, date) { vm.setDate(it) } },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { vm.moveDay(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 날") }
                TextButton(onClick = { vm.setDate(LocalDate.now()) }) { Text("오늘") }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "완료 $doneCount / ${runs.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (runs.isEmpty()) {
                    item { EmptyState("이 날짜에 해당하는 점검 항목이 없습니다.\n‘항목 관리’에서 반복 점검을 등록하세요.") }
                }
                items(runs, key = { it.runId }) { run ->
                    Card(
                        Modifier.fillMaxWidth().clickable { noteTarget = run },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = run.done, onCheckedChange = { vm.toggle(run) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    run.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (run.done) TextDecoration.LineThrough else null,
                                )
                                val sub = listOfNotNull(
                                    run.timeLabel.takeIf { it.isNotBlank() },
                                    run.memo.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (run.note.isNotBlank()) {
                                    Text(
                                        "메모: ${run.note}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    noteTarget?.let { run ->
        var note by remember(run.runId) { mutableStateOf(run.note) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text(run.title) },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("점검 메모 (특이사항)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.setNote(run.runId, note.trim())
                    noteTarget = null
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text("취소") } },
        )
    }
}
