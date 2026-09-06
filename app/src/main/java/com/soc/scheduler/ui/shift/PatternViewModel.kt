package com.soc.scheduler.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.PatternDay
import com.soc.scheduler.data.ShiftPattern
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.domain.PatternPreset
import com.soc.scheduler.domain.ShiftEngine
import com.soc.scheduler.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PatternUi(
    val pattern: ShiftPattern? = null,
    val days: List<PatternDay> = emptyList(),
    val types: List<ShiftType> = emptyList(),
    val preview: List<Pair<LocalDate, ShiftType?>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PatternViewModel : ViewModel() {

    private val repo = Graph.repo
    private val dao = repo.shiftDao

    private val patternFlow = dao.observeActivePattern()
    private val daysFlow = patternFlow.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else dao.observePatternDays(p.id)
    }

    val state: StateFlow<PatternUi> = combine(
        patternFlow,
        daysFlow,
        dao.observeTypes(),
    ) { pattern, days, types ->
        val typeMap = types.associateBy { it.id }
        val today = LocalDate.now()
        val preview = (0..6).map { offset ->
            val date = today.plusDays(offset.toLong())
            date to ShiftEngine.shiftTypeIdFor(pattern, days, date)?.let { typeMap[it] }
        }
        PatternUi(pattern, days, types, preview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatternUi())

    fun teamNumberOf(pattern: ShiftPattern): Int {
        if (pattern.teamCount <= 1) return 1
        val step = pattern.cycleDays / pattern.teamCount
        if (step <= 0) return 1
        return pattern.myOffset / step + 1
    }

    fun applyPreset(preset: PatternPreset, anchor: LocalDate, team: Int) = viewModelScope.launch {
        repo.applyPreset(preset, anchor, team)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun setAnchor(date: LocalDate) = viewModelScope.launch {
        val pattern = dao.activePattern() ?: return@launch
        dao.updatePattern(pattern.copy(anchorEpochDay = date.toEpochDay()))
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun setTeam(team: Int) = viewModelScope.launch {
        val pattern = dao.activePattern() ?: return@launch
        val offset = ShiftEngine.offsetForTeam(pattern.cycleDays, pattern.teamCount, team)
        dao.updatePattern(pattern.copy(myOffset = offset))
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun setCycleDay(dayIndex: Int, shiftTypeId: Long) = viewModelScope.launch {
        val pattern = dao.activePattern() ?: return@launch
        repo.updateCycleDay(pattern.id, dayIndex, shiftTypeId)
        WidgetUpdater.updateAll(Graph.appContext)
    }
}
