package com.soc.scheduler.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 근무 유형: 주간 / 오후 / 야간 / 비번 / 휴무 / 연차 등 */
@Entity(tableName = "shift_type")
data class ShiftType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shortLabel: String,
    val startTime: String,
    val endTime: String,
    val colorArgb: Long,
    val isWorking: Boolean,
    val sortOrder: Int = 0,
)

/**
 * 교대 패턴. cycleDays 길이의 사이클을 anchorEpochDay 부터 반복한다.
 * myOffset 은 "내 조"가 사이클의 몇 번째 날에서 시작하는지를 뜻한다.
 */
@Entity(tableName = "shift_pattern")
data class ShiftPattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cycleDays: Int,
    val teamCount: Int,
    val anchorEpochDay: Long,
    val myOffset: Int,
    val isActive: Boolean = false,
    /** 근무 시작일. 이 날짜 이전은 근무가 없는 것으로 본다(수습 기간 제외용). null 이면 제한 없음. */
    val startEpochDay: Long? = null,
)

/** 패턴 사이클의 하루 = 근무 유형 */
@Entity(
    tableName = "pattern_day",
    indices = [Index(value = ["patternId", "dayIndex"], unique = true)]
)
data class PatternDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val dayIndex: Int,
    val shiftTypeId: Long,
)

/** 특정 날짜의 근무를 수동으로 덮어쓴다 (연차, 대타, 교육 등) */
@Entity(tableName = "shift_override")
data class ShiftOverride(
    @PrimaryKey val epochDay: Long,
    val shiftTypeId: Long,
    val memo: String = "",
)

/** 개인 일정 / 할 일 */
@Entity(tableName = "task")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val memo: String = "",
    val dueAtMillis: Long,
    val allDay: Boolean = false,
    val category: String = "업무",
    val priority: Int = 1,
    val reminderMinutesBefore: Int = -1,
    val done: Boolean = false,
    val doneAtMillis: Long? = null,
)

/** 인수인계 / 업무일지 */
@Entity(tableName = "handover", indices = [Index("epochDay")])
data class HandoverNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val shiftLabel: String,
    val category: String,
    val severity: Int,
    val title: String,
    val content: String,
    val createdAtMillis: Long,
)

/** 정기 점검 템플릿 */
@Entity(tableName = "check_template")
data class CheckTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val memo: String = "",
    val recurrence: String,
    val weekDays: String = "",
    val monthDay: Int = 1,
    val timeLabel: String = "",
    val active: Boolean = true,
    val sortOrder: Int = 0,
)

/** 특정 날짜에 생성된 점검 인스턴스 */
@Entity(
    tableName = "check_run",
    indices = [Index(value = ["templateId", "epochDay"], unique = true), Index("epochDay")]
)
data class CheckRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val epochDay: Long,
    val done: Boolean = false,
    val doneAtMillis: Long? = null,
    val note: String = "",
)

/** 화면 표시용 조인 결과 */
data class CheckRunView(
    val runId: Long,
    val templateId: Long,
    val title: String,
    val memo: String,
    val timeLabel: String,
    val done: Boolean,
    val note: String,
)

object Recurrence {
    const val DAILY = "DAILY"
    const val WEEKDAY = "WEEKDAY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    val ALL = listOf(DAILY, WEEKDAY, WEEKLY, MONTHLY)

    fun label(value: String): String = when (value) {
        DAILY -> "매일"
        WEEKDAY -> "평일"
        WEEKLY -> "매주"
        MONTHLY -> "매월"
        else -> value
    }
}

object HandoverCategory {
    val ALL = listOf("인수인계", "침해시도", "오탐", "장애", "정책변경", "기타")
}

object Severity {
    const val NORMAL = 0
    const val CAUTION = 1
    const val CRITICAL = 2

    fun label(value: Int): String = when (value) {
        CRITICAL -> "긴급"
        CAUTION -> "주의"
        else -> "정상"
    }
}
