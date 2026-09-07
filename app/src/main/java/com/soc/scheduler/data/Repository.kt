package com.soc.scheduler.data

import com.soc.scheduler.domain.PatternPreset
import com.soc.scheduler.domain.ShiftEngine
import java.time.DayOfWeek
import java.time.LocalDate

class Repository(private val db: AppDatabase) {

    val shiftDao: ShiftDao get() = db.shiftDao()
    val taskDao: TaskDao get() = db.taskDao()
    val handoverDao: HandoverDao get() = db.handoverDao()
    val checkDao: CheckDao get() = db.checkDao()
    val shiftAlarmDao: ShiftAlarmDao get() = db.shiftAlarmDao()

    // ---------------------------------------------------------------- 교대 패턴

    /** 프리셋을 새 패턴으로 저장하고 활성화한다. */
    suspend fun applyPreset(preset: PatternPreset, anchor: LocalDate, teamNumber: Int): Long {
        val offset = ShiftEngine.offsetForTeam(preset.cycleDays, preset.teamCount, teamNumber)
        val patternId = shiftDao.insertPattern(
            ShiftPattern(
                name = preset.name,
                cycleDays = preset.cycleDays,
                teamCount = preset.teamCount,
                anchorEpochDay = anchor.toEpochDay(),
                myOffset = offset,
                isActive = false,
            )
        )
        shiftDao.deletePatternDays(patternId)
        shiftDao.insertPatternDays(
            preset.cycle.mapIndexed { index, typeId ->
                PatternDay(patternId = patternId, dayIndex = index, shiftTypeId = typeId)
            }
        )
        activatePattern(patternId)
        return patternId
    }

    /**
     * 초기 설정에서 만든 사이클을 적용한다.
     * [todayIndex] 는 "오늘이 사이클의 몇 번째 날인가"이며, 이것으로 기준일을 역산한다.
     * 이전에 쓰던 패턴은 정리해서 하나만 남긴다.
     */
    suspend fun applyCycle(
        name: String,
        cycle: List<Long>,
        todayIndex: Int,
        teamCount: Int = 1,
        startEpochDay: Long? = null,
    ): Long {
        val anchor = LocalDate.now().minusDays(todayIndex.toLong())
        val patternId = shiftDao.insertPattern(
            ShiftPattern(
                name = name,
                cycleDays = cycle.size,
                teamCount = teamCount,
                anchorEpochDay = anchor.toEpochDay(),
                myOffset = 0,
                isActive = false,
                startEpochDay = startEpochDay,
            )
        )
        shiftDao.deletePatternDays(patternId)
        shiftDao.insertPatternDays(
            cycle.mapIndexed { index, typeId ->
                PatternDay(patternId = patternId, dayIndex = index, shiftTypeId = typeId)
            }
        )
        shiftDao.allPatternIds().filter { it != patternId }.forEach { old ->
            shiftDao.deletePatternDays(old)
            shiftDao.deletePattern(old)
        }
        activatePattern(patternId)
        return patternId
    }

    suspend fun activatePattern(patternId: Long) {
        shiftDao.clearActivePatterns()
        shiftDao.markActive(patternId)
    }

    suspend fun updateCycleDay(patternId: Long, dayIndex: Int, shiftTypeId: Long) {
        val existing = shiftDao.patternDays(patternId).firstOrNull { it.dayIndex == dayIndex }
        shiftDao.insertPatternDays(
            listOf(
                PatternDay(
                    id = existing?.id ?: 0,
                    patternId = patternId,
                    dayIndex = dayIndex,
                    shiftTypeId = shiftTypeId,
                )
            )
        )
    }

    suspend fun setOverride(date: LocalDate, shiftTypeId: Long, memo: String) =
        shiftDao.upsertOverride(ShiftOverride(date.toEpochDay(), shiftTypeId, memo))

    suspend fun clearOverride(date: LocalDate) = shiftDao.deleteOverride(date.toEpochDay())

    // ---------------------------------------------------------------- 점검 체크리스트

    /** 해당 날짜에 필요한 점검 인스턴스를 만들어 둔다. 이미 있으면 무시된다. */
    suspend fun ensureCheckRuns(date: LocalDate) {
        val templates = checkDao.activeTemplates().filter { matches(it, date) }
        if (templates.isEmpty()) return
        checkDao.insertRuns(
            templates.map { CheckRun(templateId = it.id, epochDay = date.toEpochDay()) }
        )
    }

    private fun matches(template: CheckTemplate, date: LocalDate): Boolean = when (template.recurrence) {
        Recurrence.DAILY -> true
        Recurrence.WEEKDAY -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        Recurrence.WEEKLY -> template.weekDays
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .contains(date.dayOfWeek.value)
        Recurrence.MONTHLY -> {
            val day = template.monthDay.coerceIn(1, date.lengthOfMonth())
            date.dayOfMonth == day
        }
        else -> false
    }

    suspend fun deleteTemplate(id: Long) {
        checkDao.deleteRunsOfTemplate(id)
        checkDao.deleteTemplate(id)
    }
}
