package com.soc.scheduler.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.CheckTemplate
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.domain.PatternPreset
import com.soc.scheduler.domain.PatternPresets
import com.soc.scheduler.domain.ShiftEngine
import com.soc.scheduler.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 스케줄을 다시 정할 때 기존 수동 변경을 어떻게 할지 */
enum class OverrideClearMode { KEEP, FUTURE, ALL }

/** 직접 만들기를 골랐을 때의 기본 사이클: 주 주 야 야 비 휴 */
private val CUSTOM_DEFAULT = listOf(1L, 1L, 3L, 3L, 4L, 5L)

data class OnboardingUi(
    val step: Int = 0,
    val presetName: String? = null,
    val isCustom: Boolean = false,
    val cycle: List<Long> = emptyList(),
    val todayIndex: Int = 0,
    val types: List<ShiftType> = emptyList(),
    val templates: List<CheckTemplate> = emptyList(),
    val activeTemplateIds: Set<Long> = emptySet(),
    /** 근무 시작일. 수습 기간처럼 교대를 하지 않은 구간을 빼기 위한 값. */
    val startDate: LocalDate? = null,
    /** 직접 바꿔 둔 근무 전체 / 오늘 이후 개수 */
    val overrideCount: Int = 0,
    val futureOverrideCount: Int = 0,
    val clearMode: OverrideClearMode = OverrideClearMode.FUTURE,
    val saving: Boolean = false,
) {
    val typeMap: Map<Long, ShiftType> get() = types.associateBy { it.id }

    /** 오늘부터 7일간 이 설정이 어떤 근무가 되는지 미리 보여 준다. */
    fun preview(): List<Pair<LocalDate, ShiftType?>> {
        if (cycle.isEmpty()) return emptyList()
        val today = LocalDate.now()
        val map = typeMap
        return (0..6).map { offset ->
            val index = (todayIndex + offset) % cycle.size
            today.plusDays(offset.toLong()) to map[cycle[index]]
        }
    }

    val canGoNext: Boolean
        get() = when (step) {
            0 -> presetName != null || isCustom
            1 -> cycle.isNotEmpty()
            else -> true
        }
}

class OnboardingViewModel : ViewModel() {

    private val repo = Graph.repo

    private val _state = MutableStateFlow(OnboardingUi())
    val state: StateFlow<OnboardingUi> = _state

    init {
        viewModelScope.launch {
            val types = repo.shiftDao.types()
            val templates = repo.checkDao.templatesOnce()
            val today = LocalDate.now().toEpochDay()
            _state.value = _state.value.copy(
                types = types,
                templates = templates,
                activeTemplateIds = templates.filter { it.active }.map { it.id }.toSet(),
                overrideCount = repo.shiftDao.overrideCount(),
                futureOverrideCount = repo.shiftDao.overrideCountFrom(today),
            )
        }
    }

    fun selectPreset(preset: PatternPreset) {
        _state.value = _state.value.copy(
            presetName = preset.name,
            isCustom = false,
            cycle = preset.cycle,
            todayIndex = 0,
        )
    }

    fun selectCustom() {
        _state.value = _state.value.copy(
            presetName = null,
            isCustom = true,
            cycle = CUSTOM_DEFAULT,
            todayIndex = 0,
        )
    }

    fun setCycleLength(length: Int) {
        val current = _state.value.cycle
        val next = when {
            length <= current.size -> current.take(length)
            else -> current + List(length - current.size) { 5L } // 늘어난 칸은 휴무로 채운다
        }
        _state.value = _state.value.copy(
            cycle = next,
            todayIndex = _state.value.todayIndex.coerceAtMost(next.lastIndex.coerceAtLeast(0)),
        )
    }

    fun setCycleDay(index: Int, typeId: Long) {
        val next = _state.value.cycle.toMutableList()
        if (index in next.indices) {
            next[index] = typeId
            _state.value = _state.value.copy(cycle = next)
        }
    }

    fun setTodayIndex(index: Int) {
        _state.value = _state.value.copy(todayIndex = index)
    }

    fun setStartDate(date: LocalDate?) {
        _state.value = _state.value.copy(startDate = date)
    }

    fun setClearMode(mode: OverrideClearMode) {
        _state.value = _state.value.copy(clearMode = mode)
    }

    fun setShiftTime(type: ShiftType, start: String, end: String) = viewModelScope.launch {
        repo.shiftDao.upsertType(type.copy(startTime = start, endTime = end))
        _state.value = _state.value.copy(types = repo.shiftDao.types())
    }

    fun toggleTemplate(id: Long) {
        val current = _state.value.activeTemplateIds
        _state.value = _state.value.copy(
            activeTemplateIds = if (current.contains(id)) current - id else current + id
        )
    }

    fun goTo(step: Int) {
        _state.value = _state.value.copy(step = step.coerceIn(0, LAST_STEP))
    }

    fun next() = goTo(_state.value.step + 1)

    fun back() = goTo(_state.value.step - 1)

    /** 설정을 실제 DB에 반영한다. */
    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (s.cycle.isEmpty()) {
            onDone()
            return@launch
        }
        _state.value = s.copy(saving = true)

        val teamCount = PatternPresets.ALL.firstOrNull { it.name == s.presetName }?.teamCount ?: 1
        repo.applyCycle(
            name = s.presetName ?: "직접 설정",
            cycle = s.cycle,
            todayIndex = s.todayIndex,
            teamCount = teamCount,
            startEpochDay = s.startDate?.toEpochDay(),
        )

        // 스케줄을 다시 정했으므로 예전에 직접 바꿔 둔 근무를 선택에 따라 정리한다.
        when (s.clearMode) {
            OverrideClearMode.ALL -> repo.shiftDao.clearOverrides()
            OverrideClearMode.FUTURE -> repo.shiftDao.clearOverridesFrom(LocalDate.now().toEpochDay())
            OverrideClearMode.KEEP -> Unit
        }

        s.templates.forEach { template ->
            val shouldBeActive = s.activeTemplateIds.contains(template.id)
            if (template.active != shouldBeActive) {
                repo.checkDao.upsertTemplate(template.copy(active = shouldBeActive))
            }
        }
        repo.ensureCheckRuns(LocalDate.now())

        WidgetUpdater.updateAll(Graph.appContext)
        _state.value = _state.value.copy(saving = false)
        onDone()
    }

    /** 설정에서 다시 들어왔을 때 현재 패턴을 그대로 불러온다. */
    fun loadCurrent() = viewModelScope.launch {
        val pattern = repo.shiftDao.activePattern() ?: return@launch
        val days = repo.shiftDao.patternDays(pattern.id)
        if (days.isEmpty()) return@launch
        val cycle = days.sortedBy { it.dayIndex }.map { it.shiftTypeId }
        val todayIndex = ShiftEngine.cycleIndex(pattern, LocalDate.now())
        val isPreset = PatternPresets.ALL.any { it.name == pattern.name }
        _state.value = _state.value.copy(
            presetName = if (isPreset) pattern.name else null,
            isCustom = !isPreset,
            cycle = cycle,
            todayIndex = todayIndex,
            startDate = pattern.startEpochDay?.let { LocalDate.ofEpochDay(it) },
        )
    }

    companion object {
        const val LAST_STEP = 3
        val STEP_TITLES = listOf("근무 형태", "근무 주기", "근무 시간", "점검 루틴")
    }
}
