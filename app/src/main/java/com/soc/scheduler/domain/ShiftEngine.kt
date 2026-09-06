package com.soc.scheduler.domain

import com.soc.scheduler.data.PatternDay
import com.soc.scheduler.data.ShiftOverride
import com.soc.scheduler.data.ShiftPattern
import com.soc.scheduler.data.ShiftType
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

    /** 덮어쓰기가 있으면 그것을 우선한다. */
    fun resolve(
        pattern: ShiftPattern?,
        days: List<PatternDay>,
        types: Map<Long, ShiftType>,
        overrides: Map<Long, ShiftOverride>,
        date: LocalDate,
    ): ResolvedShift {
        val override = overrides[date.toEpochDay()]
        if (override != null) {
            return ResolvedShift(types[override.shiftTypeId], true, override.memo)
        }
        val typeId = shiftTypeIdFor(pattern, days, date)
        return ResolvedShift(typeId?.let { types[it] }, false, "")
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
)

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
