import Foundation

/// 시각이 없는 "달력의 하루".
///
/// 안드로이드판은 `java.time.LocalDate` 와 `toEpochDay()` 를 쓴다. 두 앱이 같은
/// 근무표를 그리려면 같은 정수를 써야 하므로, 여기서도 1970-01-01 로부터 지난
/// 날짜 수를 그대로 쓴다. 시각·표준시가 끼어들면 하루가 밀리므로 계산은 모두
/// 정오를 기준으로 한다.
struct Day: Hashable, Comparable, Identifiable, Codable {

    /// 1970-01-01 로부터 지난 날짜 수
    var epochDay: Int

    var id: Int { epochDay }

    init(epochDay: Int) {
        self.epochDay = epochDay
    }

    init(_ date: Date) {
        self.epochDay = Day.calendar.dateComponents(
            [.day],
            from: Day.origin,
            to: Day.calendar.startOfDay(for: date)
        ).day ?? 0
    }

    static var today: Day { Day(Date()) }

    // MARK: 변환

    private static let calendar = Calendar(identifier: .gregorian)

    /// 1970-01-01 00:00 (현지)
    private static let origin: Date = {
        var components = DateComponents()
        components.year = 1970
        components.month = 1
        components.day = 1
        return calendar.date(from: components) ?? Date(timeIntervalSince1970: 0)
    }()

    /// 그날 자정
    var startOfDay: Date {
        Day.calendar.date(byAdding: .day, value: epochDay, to: Day.origin) ?? Day.origin
    }

    /// 그날 정오. 표준시가 바뀌는 날에도 날짜가 밀리지 않는다.
    var noon: Date {
        Day.calendar.date(byAdding: .hour, value: 12, to: startOfDay) ?? startOfDay
    }

    func at(hour: Int, minute: Int) -> Date {
        Day.calendar.date(
            bySettingHour: hour, minute: minute, second: 0, of: startOfDay
        ) ?? startOfDay
    }

    // MARK: 이동

    func adding(days: Int) -> Day { Day(epochDay: epochDay + days) }

    func adding(months: Int) -> Day {
        guard let moved = Day.calendar.date(byAdding: .month, value: months, to: noon) else { return self }
        return Day(moved)
    }

    // MARK: 달력 요소

    private var components: DateComponents {
        Day.calendar.dateComponents([.year, .month, .day, .weekday], from: noon)
    }

    var year: Int { components.year ?? 1970 }
    var month: Int { components.month ?? 1 }
    var dayOfMonth: Int { components.day ?? 1 }

    /// 일요일 1 … 토요일 7 (Foundation 규칙)
    var weekday: Int { components.weekday ?? 1 }

    var isWeekend: Bool { weekday == 1 || weekday == 7 }

    var isToday: Bool { self == Day.today }

    /// 이 날이 속한 주의 일요일. 앱 달력이 일요일 시작이라 그에 맞춘다.
    var weekStart: Day { adding(days: -(weekday - 1)) }

    /// 이 달의 1일
    var monthStart: Day {
        var c = DateComponents()
        c.year = year
        c.month = month
        c.day = 1
        guard let date = Day.calendar.date(from: c) else { return self }
        return Day(date)
    }

    /// 이 달의 마지막 날
    var monthEnd: Day {
        monthStart.adding(months: 1).adding(days: -1)
    }

    var lengthOfMonth: Int { monthEnd.dayOfMonth }

    // MARK: 표시

    static let weekdayLabels = ["일", "월", "화", "수", "목", "금", "토"]

    var weekdayLabel: String { Day.weekdayLabels[weekday - 1] }

    /// 2026년 9월 10일 (목)
    var full: String { "\(year)년 \(month)월 \(dayOfMonth)일 (\(weekdayLabel))" }

    /// 9월 10일 (목)
    var short: String { "\(month)월 \(dayOfMonth)일 (\(weekdayLabel))" }

    /// 2026년 9월
    var monthLabel: String { "\(year)년 \(month)월" }

    static func < (lhs: Day, rhs: Day) -> Bool { lhs.epochDay < rhs.epochDay }
}
