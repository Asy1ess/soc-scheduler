package com.soc.scheduler.widget

import com.soc.scheduler.Graph
import com.soc.scheduler.data.PatternDay
import com.soc.scheduler.data.ShiftOverride
import com.soc.scheduler.data.ShiftPattern
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.domain.ResolvedShift
import com.soc.scheduler.domain.ShiftEngine
import java.time.LocalDate

/** 위젯 한 번 그리는 데 필요한 데이터를 한꺼번에 읽어 온다. */
data class WidgetData(
    val pattern: ShiftPattern? = null,
    val days: List<PatternDay> = emptyList(),
    val types: Map<Long, ShiftType> = emptyMap(),
    val overrides: Map<Long, ShiftOverride> = emptyMap(),
) {
    val patternName: String get() = pattern?.name.orEmpty()

    fun shiftAt(date: LocalDate): ResolvedShift =
        ShiftEngine.resolve(pattern, days, types, overrides, date)

    companion object {
        suspend fun load(from: LocalDate, to: LocalDate): WidgetData {
            val dao = Graph.repo.shiftDao
            val pattern = dao.activePattern()
            return WidgetData(
                pattern = pattern,
                days = pattern?.let { dao.patternDays(it.id) } ?: emptyList(),
                types = dao.types().associateBy { it.id },
                overrides = dao.overridesBetween(from.toEpochDay(), to.toEpochDay())
                    .associateBy { it.epochDay },
            )
        }
    }
}
