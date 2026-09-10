import Foundation
import SwiftData
import SwiftUI

// MARK: - 근무 유형

/// 근무 유형: 주간 / 오후 / 야간 / 비번 / 휴무 / 연차 등
///
/// `code` 는 안드로이드판의 행 id 와 같은 값이다 (1 주간, 2 오후, 3 야간,
/// 4 비번, 5 휴무, 6 연차, 7 교육/출장). 패턴 사이클이 이 값을 가리키므로
/// 자동 증가 대신 고정 코드를 쓴다.
@Model
final class ShiftType {
    @Attribute(.unique) var code: Int
    var name: String
    var shortLabel: String
    var startTime: String
    var endTime: String
    /// AARRGGBB 8자리 16진수
    var colorHex: String
    var isWorking: Bool
    var sortOrder: Int

    init(
        code: Int,
        name: String,
        shortLabel: String,
        startTime: String,
        endTime: String,
        colorHex: String,
        isWorking: Bool,
        sortOrder: Int
    ) {
        self.code = code
        self.name = name
        self.shortLabel = shortLabel
        self.startTime = startTime
        self.endTime = endTime
        self.colorHex = colorHex
        self.isWorking = isWorking
        self.sortOrder = sortOrder
    }

    var color: Color { Color(hex: colorHex) }

    var timeRange: String {
        startTime.isEmpty ? "" : "\(startTime) ~ \(endTime)"
    }

    var isNight: Bool { name.contains("야간") || shortLabel == "야" }
    var isOffDuty: Bool { !isWorking && (name.contains("비번") || shortLabel == "비") }
}

// MARK: - 교대 패턴

/// 교대 패턴. `cycle` 길이의 사이클을 `anchorEpochDay` 부터 반복한다.
/// `myOffset` 은 "내 조"가 사이클의 몇 번째 날에서 시작하는지를 뜻한다.
@Model
final class ShiftPattern {
    var name: String
    var teamCount: Int
    var anchorEpochDay: Int
    var myOffset: Int
    var isActive: Bool
    /// 근무 시작일. 이 날짜 이전은 교대 근무를 하지 않은 것으로 본다(수습 기간 등).
    var startEpochDay: Int?
    /// 사이클 하루하루의 근무 유형 코드
    var cycle: [Int]

    init(
        name: String,
        teamCount: Int,
        anchorEpochDay: Int,
        myOffset: Int,
        isActive: Bool,
        startEpochDay: Int?,
        cycle: [Int]
    ) {
        self.name = name
        self.teamCount = teamCount
        self.anchorEpochDay = anchorEpochDay
        self.myOffset = myOffset
        self.isActive = isActive
        self.startEpochDay = startEpochDay
        self.cycle = cycle
    }

    var cycleDays: Int { cycle.count }

    var startDay: Day? { startEpochDay.map { Day(epochDay: $0) } }
}

// MARK: - 수동 변경

/// 특정 날짜의 근무를 직접 바꾼 기록 (연차, 대타, 교육 등)
@Model
final class ShiftOverride {
    @Attribute(.unique) var epochDay: Int
    var shiftTypeCode: Int
    var memo: String

    init(epochDay: Int, shiftTypeCode: Int, memo: String = "") {
        self.epochDay = epochDay
        self.shiftTypeCode = shiftTypeCode
        self.memo = memo
    }
}

// MARK: - 기상 알람

/// 근무 유형별 기상 알람. 예: 야간 근무인 날 16:00 에 깨우기.
///
/// iOS 는 앱이 시스템 알람을 만들 수 없어 알림(Notification)으로 구현한다.
/// 자세한 한계는 `ShiftAlarms.swift` 주석 참고.
@Model
final class ShiftAlarm {
    @Attribute(.unique) var shiftTypeCode: Int
    var enabled: Bool
    var hour: Int
    var minute: Int
    /// 번들에 넣은 알림음 파일 이름. 비어 있으면 시스템 기본음
    var soundName: String
    /// 다시 울림 간격(분). 0 이면 사용 안 함
    var snoozeMinutes: Int

    init(
        shiftTypeCode: Int,
        enabled: Bool = false,
        hour: Int = 6,
        minute: Int = 0,
        soundName: String = "",
        snoozeMinutes: Int = 5
    ) {
        self.shiftTypeCode = shiftTypeCode
        self.enabled = enabled
        self.hour = hour
        self.minute = minute
        self.soundName = soundName
        self.snoozeMinutes = snoozeMinutes
    }

    var timeLabel: String { String(format: "%02d:%02d", hour, minute) }
}

/// 알람을 처음 켤 때 적용되는 기본 기상 시각.
///
/// 주간 07:30, 야간 16:00 은 정해 둔 기본값이고,
/// 그 외 근무는 근무 시작 1시간 전으로 잡는다.
/// 비번·휴무 같은 비근무 유형은 알람 화면에 아예 나오지 않는다.
func defaultAlarm(for type: ShiftType) -> ShiftAlarm {
    let time: (hour: Int, minute: Int)
    if type.isNight {
        time = (16, 0)
    } else if type.name.contains("주간") || type.shortLabel == "주" {
        time = (7, 30)
    } else if type.name.contains("오후") || type.shortLabel == "오" {
        time = (12, 30)
    } else {
        time = oneHourBefore(type.startTime)
    }
    return ShiftAlarm(shiftTypeCode: type.code, hour: time.hour, minute: time.minute)
}

private func oneHourBefore(_ startTime: String) -> (hour: Int, minute: Int) {
    let parts = startTime.split(separator: ":")
    guard let h = parts.first.flatMap({ Int($0) }) else { return (7, 0) }
    let m = parts.count > 1 ? (Int(parts[1]) ?? 0) : 0
    let total = ((h * 60 + m) - 60 + 24 * 60) % (24 * 60)
    return (total / 60, total % 60)
}

// MARK: - 일정 / 할 일

@Model
final class TaskItem {
    /// 알림 식별자로 쓴다. 앱을 껐다 켜도 같은 값이어야 예약을 취소할 수 있다.
    var uuid: String = UUID().uuidString
    var title: String
    var memo: String
    var dueAt: Date
    var category: String
    /// 0 보통, 1 중요
    var priority: Int
    /// 몇 분 전에 알릴지. -1 이면 알리지 않음
    var reminderMinutesBefore: Int
    var done: Bool
    var doneAt: Date?

    init(
        uuid: String = UUID().uuidString,
        title: String,
        memo: String = "",
        dueAt: Date,
        category: String = "업무",
        priority: Int = 0,
        reminderMinutesBefore: Int = -1,
        done: Bool = false,
        doneAt: Date? = nil
    ) {
        self.uuid = uuid
        self.title = title
        self.memo = memo
        self.dueAt = dueAt
        self.category = category
        self.priority = priority
        self.reminderMinutesBefore = reminderMinutesBefore
        self.done = done
        self.doneAt = doneAt
    }

    var day: Day { Day(dueAt) }
    var isImportant: Bool { priority > 0 }
}

enum TaskCategory {
    static let all = ["업무", "점검", "보고", "회의", "교육", "개인"]
}

enum ReminderOffset {
    /// (분, 이름). -1 은 알리지 않음
    static let all: [(minutes: Int, label: String)] = [
        (-1, "없음"),
        (0, "정시"),
        (10, "10분 전"),
        (30, "30분 전"),
        (60, "1시간 전"),
        (180, "3시간 전"),
        (1440, "1일 전"),
    ]

    static func label(for minutes: Int) -> String {
        all.first { $0.minutes == minutes }?.label ?? "없음"
    }
}

// MARK: - 정기 점검

enum Recurrence: String, CaseIterable {
    case daily = "DAILY"
    case weekday = "WEEKDAY"
    case weekly = "WEEKLY"
    case monthly = "MONTHLY"

    var label: String {
        switch self {
        case .daily: "매일"
        case .weekday: "평일"
        case .weekly: "매주"
        case .monthly: "매월"
        }
    }
}

@Model
final class CheckTemplate {
    var title: String
    var memo: String
    var recurrenceRaw: String
    /// 매주 반복일 때 쓰는 요일. 일요일 1 … 토요일 7
    var weekDays: [Int]
    var monthDay: Int
    var timeLabel: String
    var active: Bool
    var sortOrder: Int

    init(
        title: String,
        memo: String = "",
        recurrence: Recurrence = .daily,
        weekDays: [Int] = [],
        monthDay: Int = 1,
        timeLabel: String = "",
        active: Bool = true,
        sortOrder: Int = 0
    ) {
        self.title = title
        self.memo = memo
        self.recurrenceRaw = recurrence.rawValue
        self.weekDays = weekDays
        self.monthDay = monthDay
        self.timeLabel = timeLabel
        self.active = active
        self.sortOrder = sortOrder
    }

    var recurrence: Recurrence {
        get { Recurrence(rawValue: recurrenceRaw) ?? .daily }
        set { recurrenceRaw = newValue.rawValue }
    }

    /// 이 날짜에 이 점검이 해당되는가
    func matches(_ day: Day) -> Bool {
        switch recurrence {
        case .daily: true
        case .weekday: !day.isWeekend
        case .weekly: weekDays.contains(day.weekday)
        case .monthly: day.dayOfMonth == min(max(monthDay, 1), day.lengthOfMonth)
        }
    }

    /// 매주 · 매월 조건을 사람이 읽는 말로
    var recurrenceDetail: String {
        switch recurrence {
        case .weekly:
            let names = weekDays.sorted().map { Day.weekdayLabels[$0 - 1] }.joined(separator: " ")
            return names.isEmpty ? "매주" : "매주 \(names)요일"
        case .monthly:
            return "매월 \(monthDay)일"
        default:
            return recurrence.label
        }
    }
}

/// 특정 날짜에 만들어진 점검 인스턴스
@Model
final class CheckRun {
    var template: CheckTemplate?
    var epochDay: Int
    var done: Bool
    var doneAt: Date?
    var note: String

    init(template: CheckTemplate?, epochDay: Int, done: Bool = false, doneAt: Date? = nil, note: String = "") {
        self.template = template
        self.epochDay = epochDay
        self.done = done
        self.doneAt = doneAt
        self.note = note
    }
}

// MARK: - 색

extension Color {
    /// "FF2E7D32" 처럼 알파가 앞에 붙은 8자리, 또는 6자리 16진수를 읽는다.
    init(hex: String) {
        var value = UInt64(0)
        Scanner(string: hex).scanHexInt64(&value)
        let hasAlpha = hex.count > 6
        let a = hasAlpha ? Double((value >> 24) & 0xFF) / 255 : 1
        let r = Double((value >> 16) & 0xFF) / 255
        let g = Double((value >> 8) & 0xFF) / 255
        let b = Double(value & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}
