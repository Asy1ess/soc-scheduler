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

/**
 * 특정 날짜의 근무를 수동으로 덮어쓴다 (연차, 대타, 교육 등).
 *
 * 폰과 웹이 같은 기록을 나눠 갖는다. 행마다 [updatedAtMillis] 를 두고 나중에
 * 저장한 쪽이 이기며, 지울 때는 진짜 지우지 않고 [deleted] 만 켠다. 그래야
 * "지웠다"는 사실도 서버로 전해진다. 화면은 deleted 가 꺼진 행만 본다.
 */
@Entity(tableName = "shift_override")
data class ShiftOverride(
    @PrimaryKey val epochDay: Long,
    val shiftTypeId: Long,
    val memo: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/**
 * 근무 유형별 기상 알람.
 * 예: 야간 근무인 날 16:00 에 깨우기.
 */
@Entity(tableName = "shift_alarm")
data class ShiftAlarm(
    @PrimaryKey val shiftTypeId: Long,
    val enabled: Boolean = false,
    val hour: Int = 6,
    val minute: Int = 0,
    /** 알람음 URI. 비어 있으면 시스템 기본 알람음 */
    val soundUri: String = "",
    val vibrate: Boolean = true,
    /** 다시 울림 간격(분). 0 이면 사용 안 함 */
    val snoozeMinutes: Int = 5,
)

/**
 * 알람을 처음 켤 때 적용되는 기본 기상 시각.
 *
 * 주간 07:30, 야간 16:00 은 요청된 기본값이고,
 * 그 외 근무는 근무 시작 1시간 전으로 잡는다.
 * 비번·휴무 같은 비근무 유형은 알람 화면에 아예 나오지 않는다.
 */
fun defaultAlarmFor(type: ShiftType): ShiftAlarm {
    val (hour, minute) = when {
        type.name.contains("야간") || type.shortLabel == "야" -> 16 to 0
        type.name.contains("주간") || type.shortLabel == "주" -> 7 to 30
        type.name.contains("오후") || type.shortLabel == "오" -> 12 to 30
        else -> oneHourBefore(type.startTime)
    }
    return ShiftAlarm(shiftTypeId = type.id, hour = hour, minute = minute)
}

private fun oneHourBefore(startTime: String): Pair<Int, Int> {
    val parts = startTime.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return 7 to 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val total = ((h * 60 + m) - 60 + 24 * 60) % (24 * 60)
    return total / 60 to total % 60
}

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
