package com.soc.scheduler.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.TaskItem
import com.soc.scheduler.notify.AlarmScheduler
import com.soc.scheduler.ui.common.endOfDayMillis
import com.soc.scheduler.widget.WidgetUpdater
import com.soc.scheduler.ui.common.startOfDayMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskGroups(
    val overdue: List<TaskItem> = emptyList(),
    val today: List<TaskItem> = emptyList(),
    val upcoming: List<TaskItem> = emptyList(),
    val done: List<TaskItem> = emptyList(),
)

object TaskCategory {
    val ALL = listOf("업무", "점검", "보고", "회의", "교육", "개인")
}

class TaskViewModel : ViewModel() {

    private val repo = Graph.repo
    private val dao = repo.taskDao

    val groups: StateFlow<TaskGroups> = dao.observeAll().map { tasks ->
        val todayStart = LocalDate.now().startOfDayMillis()
        val todayEnd = LocalDate.now().endOfDayMillis()
        TaskGroups(
            overdue = tasks.filter { !it.done && it.dueAtMillis < todayStart },
            today = tasks.filter { !it.done && it.dueAtMillis in todayStart..todayEnd },
            upcoming = tasks.filter { !it.done && it.dueAtMillis > todayEnd },
            done = tasks.filter { it.done }.sortedByDescending { it.doneAtMillis ?: it.dueAtMillis },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskGroups())

    fun save(task: TaskItem) = viewModelScope.launch {
        val id = dao.upsert(task)
        val saved = task.copy(id = if (task.id == 0L) id else task.id)
        AlarmScheduler.schedule(Graph.appContext, saved)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun toggleDone(task: TaskItem) = viewModelScope.launch {
        val done = !task.done
        dao.setDone(task.id, done, if (done) System.currentTimeMillis() else null)
        if (done) {
            AlarmScheduler.cancel(Graph.appContext, task.id)
        } else {
            AlarmScheduler.schedule(Graph.appContext, task.copy(done = false))
        }
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun delete(task: TaskItem) = viewModelScope.launch {
        AlarmScheduler.cancel(Graph.appContext, task.id)
        dao.delete(task.id)
        WidgetUpdater.updateAll(Graph.appContext)
    }
}
