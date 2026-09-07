package com.soc.scheduler.domain

import com.soc.scheduler.data.PatternDay
import com.soc.scheduler.data.ShiftOverride
import com.soc.scheduler.data.ShiftPattern
import com.soc.scheduler.data.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 교대 패턴으로부터 특정 날짜의 근무를 계산한다.
 *
 * 사이클 인덱스 = (기준일로부터 지난 일수 + 내 조 오프셋) mod 사이클 길이
 */
object ShiftEngine {

    fun cycleIndex(pattern: ShiftPattern, date: LocalDate): Int {
        if (pattern.cycleDays <= 0) return 0
        val diff = date.toEpochDay() - pattern.anchorEpochDay
        val raw = (diff + pattern.myOffset) % pattern.cycleDays
        return ((raw + pattern.cycleDays) % pattern.cycleDays).toInt()
    }

    fun shiftTypeIdFor(pattern: ShiftPattern?, days: List<PatternDay>, date: LocalDate): Long? {
        if (pattern == null || days.isEmpty()) return null
        val index = cycleIndex(pattern, date)
        return days.firstOrNull { it.dayIndex == index }?.shiftTypeId
    }

    /** 근무 시작일 이전인가 (수습 기간 등 교대 근무를 하지 않던 구간) */
    fun isBeforeStart(pattern: ShiftPattern?, date: LocalDate): Boolean {
        val start = pattern?.startEpochDay ?: return false
        return date.toEpochDay() < start
    }

    /**
     * 날짜별 근무를 확정한다.
     *
     * 우선순위는 수동 변경 > 패턴 순이며, 수동 변경이 있어도
     * [ResolvedShift.baseType] 으로 원래 근무를 함께 돌려준다.
     *
     * 근무 시작일 이전(수습 기간 등)은 교대 패턴 대신 기본 주간 일정을 깔아 둔다.
     * 그 구간에도 직접 지정한 근무가 있으면 그것을 우선한다.
     */
    fun resolve(
        pattern: ShiftPattern?,
        days: List<PatternDay>,
        types: Map<Long, ShiftType>,
        overrides: Map<Long, ShiftOverride>,
        date: LocalDate,
    ): ResolvedShift {
        val beforeStart = isBeforeStart(pattern, date)
        val base = if (beforeStart) {
            preStartType(types, date)
        } else {
            shiftTypeIdFor(pattern, days, date)?.let { types[it] }
        }

        val override = overrides[date.toEpochDay()]
        if (override != null) {
            return ResolvedShift(types[override.shiftTypeId], true, override.memo, base, beforeStart)
        }
        return ResolvedShift(base, false, "", base, beforeStart)
    }

    /**
     * 근무 시작일 이전 구간의 기본 근무.
     * 수습 때는 보통 교대가 아니라 평일 주간 근무를 하므로 평일은 주간, 주말은 휴무로 채운다.
     */
    private fun preStartType(types: Map<Long, ShiftType>, date: LocalDate): ShiftType? {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val all = types.values
        return if (weekend) {
            all.firstOrNull { it.name.contains("휴무") || it.shortLabel == "휴" }
                ?: all.firstOrNull { !it.isWorking }
        } else {
            all.firstOrNull { it.name.contains("주간") || it.shortLabel == "주" }
                ?: all.firstOrNull { it.isWorking }
        }
    }

    /** 조 번호(1부터)로부터 사이클 오프셋을 구한다. */
    fun offsetForTeam(cycleDays: Int, teamCount: Int, teamNumber: Int): Int {
        if (teamCount <= 0 || cycleDays <= 0) return 0
        val step = cycleDays / teamCount
        return ((teamNumber - 1) * step).mod(cycleDays)
    }
}

data class ResolvedShift(
    val type: ShiftType?,
    val isOverride: Boolean,
    val memo: String,
    /** 수동 변경 전 패턴상의 원래 근무 */
    val baseType: ShiftType? = null,
    /** 근무 시작일 이전이라 교대 패턴 대신 기본 주간 일정이 적용된 날 */
    val beforeStart: Boolean = false,
) {
    /** 수동 변경으로 원래와 달라졌는가 */
    val changedFromBase: Boolean
        get() = isOverride && baseType != null && baseType.id != type?.id
}

/** 미리 정의된 교대 패턴. shiftTypeId 는 기본 시드 데이터 기준(1 주간, 2 오후, 3 야간, 4 비번, 5 휴무). */
data class PatternPreset(
    val name: String,
    val teamCount: Int,
    val description: String,
    val cycle: List<Long>,
) {
    val cycleDays: Int get() = cycle.size
}

object PatternPresets {
    val ALL = listOf(
        PatternPreset(
            name = "4조 3교대",
            teamCount = 4,
            description = "주 주 · 오 오 · 야 야 · 비 휴 (8일 주기)",
            cycle = listOf(1, 1, 2, 2, 3, 3, 4, 5),
        ),
        PatternPreset(
            name = "3조 2교대",
            teamCount = 3,
            description = "주 주 · 야 야 · 비 휴 (6일 주기)",
            cycle = listOf(1, 1, 3, 3, 4, 5),
        ),
        PatternPreset(
            name = "4조 2교대",
            teamCount = 4,
            description = "주 주 · 야 야 · 비 비 · 휴 휴 (8일 주기)",
            cycle = listOf(1, 1, 3, 3, 4, 4, 5, 5),
        ),
        PatternPreset(
            name = "2조 2교대",
            teamCount = 2,
            description = "주 야 · 비 휴 (4일 주기)",
            cycle = listOf(1, 3, 4, 5),
        ),
        PatternPreset(
            name = "주간 전담",
            teamCount = 1,
            description = "평일 주간 근무, 주말 휴무 (7일 주기)",
            cycle = listOf(1, 1, 1, 1, 1, 5, 5),
        ),
    )
}
