package com.soc.scheduler.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.HandoverNote
import com.soc.scheduler.data.ShiftPattern
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.data.TaskItem
import com.soc.scheduler.domain.ResolvedShift
import com.soc.scheduler.domain.ShiftEngine
import com.soc.scheduler.ui.common.endOfDayMillis
import com.soc.scheduler.widget.WidgetUpdater
import com.soc.scheduler.ui.common.startOfDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val shift: ResolvedShift,
    val taskCount: Int,
)

data class MonthUi(
    val month: YearMonth = YearMonth.now(),
    val cells: List<DayCell> = emptyList(),
    val summary: List<Pair<ShiftType, Int>> = emptyList(),
    val patternName: String = "",
    val hasPattern: Boolean = true,
)

data class DayDetail(
    val date: LocalDate = LocalDate.now(),
    val shift: ResolvedShift = ResolvedShift(null, false, ""),
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<HandoverNote> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftViewModel : ViewModel() {

    private val repo = Graph.repo
    private val shiftDao = repo.shiftDao

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selected = MutableStateFlow(LocalDate.now())
    val selected: StateFlow<LocalDate> = _selected

    val shiftTypes: StateFlow<List<ShiftType>> = shiftDao.observeTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val typeMap = shiftTypes.map { list -> list.associateBy { it.id } }

    private val pattern: StateFlow<ShiftPattern?> = shiftDao.observeActivePattern()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val patternDays = pattern.flatMapLatest { p ->
        if (p == null) kotlinx.coroutines.flow.flowOf(emptyList()) else shiftDao.observePatternDays(p.id)
    }

    /**
     * 근무 변경 시 고를 만한 근무 유형.
     * 현재 패턴에 실제로 쓰이는 근무 + 비번/휴무/연차 같은 비근무 유형만 남긴다.
     * (예: 4조 2교대로 바꾸면 3교대 전용인 "오후"는 빠진다)
     */
    val relevantTypes: StateFlow<List<ShiftType>> = combine(
        shiftTypes,
        patternDays,
    ) { types, days ->
        val used = days.map { it.shiftTypeId }.toSet()
        types.filter { !it.isWorking || used.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val overrides = _month.flatMapLatest { m ->
        shiftDao.observeOverrides(
            m.atDay(1).minusDays(10).toEpochDay(),
            m.atEndOfMonth().plusDays(10).toEpochDay(),
        )
    }.map { list -> list.associateBy { it.epochDay } }

    private val monthTasks = _month.flatMapLatest { m ->
        repo.taskDao.observeRange(
            m.atDay(1).minusDays(10).startOfDayMillis(),
            m.atEndOfMonth().plusDays(10).endOfDayMillis(),
        )
    }

    val monthUi: StateFlow<MonthUi> = combine(
        _month,
        pattern,
        patternDays,
        typeMap,
        overrides,
    ) { month, pattern, days, types, overrides ->
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7 // 일요일 시작
        val gridStart = first.minusDays(leading.toLong())
        val cells = (0 until 42).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            DayCell(
                date = date,
                inMonth = YearMonth.from(date) == month,
                shift = ShiftEngine.resolve(pattern, days, types, overrides, date),
                taskCount = 0,
            )
        }
        val summary = cells.filter { it.inMonth }
            .mapNotNull { it.shift.type }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first.sortOrder }
        MonthUi(
            month = month,
            cells = cells,
            summary = summary,
            patternName = pattern?.name.orEmpty(),
            hasPattern = pattern != null && days.isNotEmpty(),
        )
    }.combine(monthTasks) { ui, tasks ->
        val counts = tasks.filter { !it.done }
            .groupingBy { java.time.Instant.ofEpochMilli(it.dueAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            .eachCount()
        ui.copy(cells = ui.cells.map { it.copy(taskCount = counts[it.date] ?: 0) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthUi())

    private val selectedTasks = _selected.flatMapLatest { date ->
        repo.taskDao.observeRange(date.startOfDayMillis(), date.endOfDayMillis())
    }

    private val selectedNotes = _selected.flatMapLatest { date ->
        repo.handoverDao.observeDay(date.toEpochDay())
    }

    val dayDetail: StateFlow<DayDetail> = combine(
        _selected,
        monthUi,
        selectedTasks,
        selectedNotes,
    ) { date, ui, tasks, notes ->
        val shift = ui.cells.firstOrNull { it.date == date }?.shift ?: ResolvedShift(null, false, "")
        DayDetail(date, shift, tasks, notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayDetail())

    fun showMonth(month: YearMonth) {
        _month.value = month
    }

    fun moveMonth(delta: Long) {
        _month.value = _month.value.plusMonths(delta)
    }

    fun select(date: LocalDate) {
        _selected.value = date
        if (YearMonth.from(date) != _month.value) _month.value = YearMonth.from(date)
    }

    fun goToday() {
        val today = LocalDate.now()
        _month.value = YearMonth.from(today)
        _selected.value = today
    }

    fun setOverride(date: LocalDate, shiftTypeId: Long, memo: String = "") = viewModelScope.launch {
        repo.setOverride(date, shiftTypeId, memo)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    fun clearOverride(date: LocalDate) = viewModelScope.launch {
        repo.clearOverride(date)
        WidgetUpdater.updateAll(Graph.appContext)
    }

    companion object {
        val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

        fun isWeekend(date: LocalDate) =
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }
}
