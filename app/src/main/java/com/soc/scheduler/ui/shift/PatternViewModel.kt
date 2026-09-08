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
    /** 직접 바꿔 둔 근무(연차·대타 등)의 개수 */
    val overrideCount: Int = 0,
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
        dao.observeOverrideCount(),
    ) { pattern, days, types, overrideCount ->
        val typeMap = types.associateBy { it.id }
        val from = pattern?.startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        val preview = (0..6).map { offset ->
            val date = from.plusDays(offset.toLong())
            date to ShiftEngine.shiftTypeIdFor(pattern, days, date)?.let { typeMap[it] }
        }
        PatternUi(pattern, days, types, preview, overrideCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatternUi())

    /** 근무 시작일이 사이클의 몇 번째 날인가 (0부터) */
    fun startIndexOf(pattern: ShiftPattern): Int {
        val start = pattern.startEpochDay ?: return 0
        return ShiftEngine.cycleIndex(pattern, LocalDate.ofEpochDay(start))
    }

    fun applyPreset(preset: PatternPreset, anchor: LocalDate, team: Int) = viewModelScope.launch {
        repo.applyPreset(preset, anchor, team)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    /**
     * 교대 근무 시작일을 바꾼다.
     * 시작일이 사이클에서 차지하는 일차는 유지한 채 기준일을 다시 계산하므로,
     * 근무표 전체가 시작일을 축으로 움직인다.
     */
    fun setStartDate(date: LocalDate) = viewModelScope.launch {
        val pattern = dao.activePattern() ?: return@launch
        val index = pattern.startEpochDay
            ?.let { ShiftEngine.cycleIndex(pattern, LocalDate.ofEpochDay(it)) }
            ?: 0
        dao.updatePattern(
            pattern.copy(
                startEpochDay = date.toEpochDay(),
                anchorEpochDay = date.minusDays(index.toLong()).toEpochDay(),
                myOffset = 0,
            )
        )
        WidgetUpdater.updateAll(Graph.appContext)
    }

    /** 근무 시작일이 사이클의 몇 번째 날인지 지정한다. */
    fun setStartIndex(index: Int) = viewModelScope.launch {
        val pattern = dao.activePattern() ?: return@launch
        val start = pattern.startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        dao.updatePattern(
            pattern.copy(
                startEpochDay = start.toEpochDay(),
                anchorEpochDay = start.minusDays(index.toLong()).toEpochDay(),
                myOffset = 0,
            )
        )
        WidgetUpdater.updateAll(Graph.appContext)
    }

    /** 직접 바꾼 근무를 모두 지우고 패턴대로 되돌린다. */
    fun clearOverrides() = viewModelScope.launch {
        dao.clearOverrides()
        WidgetUpdater.updateAll(Graph.appContext)
    }

    /** 오늘 이후의 수동 변경만 지운다. 과거 기록은 남긴다. */
    fun clearOverridesFromToday() = viewModelScope.launch {
        dao.clearOverridesFrom(LocalDate.now().toEpochDay())
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun setCycleDay(dayIndex: Int, shiftTypeId: Long) = viewModelScope.launch {
        val pattern = dao.activePattern() ?: return@launch
        repo.updateCycleDay(pattern.id, dayIndex, shiftTypeId)
        WidgetUpdater.updateAll(Graph.appContext)
    }
}
