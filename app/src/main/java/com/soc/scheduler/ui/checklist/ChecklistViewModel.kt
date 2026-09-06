package com.soc.scheduler.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.CheckRunView
import com.soc.scheduler.data.CheckTemplate
import com.soc.scheduler.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistViewModel : ViewModel() {

    private val repo = Graph.repo
    private val dao = repo.checkDao

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date

    init {
        _date
            .onEach { repo.ensureCheckRuns(it) }
            .launchIn(viewModelScope)
    }

    val runs: StateFlow<List<CheckRunView>> = _date
        .flatMapLatest { dao.observeRuns(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<CheckTemplate>> = dao.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDate(value: LocalDate) {
        _date.value = value
    }

    fun moveDay(delta: Long) {
        _date.value = _date.value.plusDays(delta)
    }

    fun toggle(run: CheckRunView) = viewModelScope.launch {
        dao.setDone(run.runId, !run.done, if (!run.done) System.currentTimeMillis() else null)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun setNote(runId: Long, note: String) = viewModelScope.launch {
        dao.setNote(runId, note)
    }

    fun saveTemplate(template: CheckTemplate) = viewModelScope.launch {
        dao.upsertTemplate(template)
        repo.ensureCheckRuns(_date.value)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch {
        repo.deleteTemplate(id)
        WidgetUpdater.updateAll(Graph.appContext)
    }
}
