import Foundation
import SwiftData
import UserNotifications

/// 근무 유형별 기상 알람과 아침 근무 알림을 예약한다.
///
/// ## iOS 에서의 한계 — 안드로이드판과 다른 점
///
/// 안드로이드판은 `AlarmManager.setAlarmClock()` 으로 진짜 알람을 걸어, 잠금화면
/// 위에 전체화면을 띄우고 무음 모드를 뚫고 소리를 낸다. iOS 는 앱이 그렇게 할 수
/// 없다. 시스템 시계 앱만 가능하다. 그래서 여기서는 알림으로 구현한다.
///
/// - 알림음은 길어야 30초. 끌 때까지 계속 울리지는 않는다.
/// - 무음 스위치를 올려 두면 소리가 나지 않는다.
/// - 집중 모드는 `interruptionLevel = .timeSensitive` 로 뚫는다. 사용자가
///   설정에서 이 앱의 "시간 민감" 알림을 허용해 둬야 한다.
/// - 무음까지 뚫으려면 Critical Alerts 권한이 필요한데, 애플에 따로 신청해
///   승인받아야 한다. 개인용 앱은 대체로 받기 어렵다.
///
/// 그래서 이 앱의 알람은 "확실히 깨워 주는 알람"이 아니라 "그 시각에 눈에 띄게
/// 알려 주는 알림"이다. 실제 기상은 시계 앱 알람과 함께 쓰시길 권한다.
enum ShiftAlarms {

    static let center = UNUserNotificationCenter.current()

    private static let alarmPrefix = "shift-alarm-"
    private static let briefPrefix = "daily-brief-"
    private static let taskPrefix = "task-"

    static let categoryIdentifier = "SHIFT_ALARM"
    static let snoozeAction = "SNOOZE"
    static let stopAction = "STOP"

    /// 며칠 앞까지 예약할지. iOS 는 앱당 예약 알림이 64개로 제한되므로 넉넉히
    /// 잡되 한도를 넘지 않게 한다.
    private static let horizonDays = 30

    // MARK: 권한과 동작 등록

    static func registerCategories() {
        let snooze = UNNotificationAction(
            identifier: snoozeAction,
            title: "다시 울리기",
            options: []
        )
        let stop = UNNotificationAction(
            identifier: stopAction,
            title: "해제",
            options: [.destructive]
        )
        let category = UNNotificationCategory(
            identifier: categoryIdentifier,
            actions: [snooze, stop],
            intentIdentifiers: [],
            options: []
        )
        center.setNotificationCategories([category])
    }

    static func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    // MARK: 예약

    /// 근무표가 바뀌었을 때마다 다시 부른다. 예전 예약을 지우고 새로 건다.
    static func rescheduleAll(context: ModelContext) {
        Task {
            guard await requestAuthorization() else { return }
            await clear(prefixes: [alarmPrefix, briefPrefix])
            await scheduleShiftAlarms(context: context)
            await scheduleDailyBrief(context: context)
        }
    }

    private static func clear(prefixes: [String]) async {
        let pending = await center.pendingNotificationRequests()
        let ids = pending
            .map(\.identifier)
            .filter { id in prefixes.contains { id.hasPrefix($0) } }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// 앞으로 30일 중 알람이 켜진 근무인 날에 알림을 건다.
    private static func scheduleShiftAlarms(context: ModelContext) async {
        let reader = ScheduleReader(context: context)
        let alarms = ((try? context.fetch(FetchDescriptor<ShiftAlarm>())) ?? [])
            .filter(\.enabled)
        guard !alarms.isEmpty else { return }

        let byCode = Dictionary(uniqueKeysWithValues: alarms.map { ($0.shiftTypeCode, $0) })
        let today = Day.today
        let schedule = reader.shifts(from: today, count: horizonDays)

        for offset in 0..<horizonDays {
            let day = today.adding(days: offset)
            guard let resolved = schedule[day],
                  let type = resolved.type,
                  type.isWorking,
                  let alarm = byCode[type.code]
            else { continue }

            let fireAt = day.at(hour: alarm.hour, minute: alarm.minute)
            guard fireAt > Date() else { continue }

            let content = UNMutableNotificationContent()
            content.title = "\(type.name) 근무 알람"
            content.body = "일어날 시간입니다"
            content.sound = sound(for: alarm)
            content.categoryIdentifier = categoryIdentifier
            content.interruptionLevel = .timeSensitive
            content.userInfo = ["snoozeMinutes": alarm.snoozeMinutes]

            let components = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: fireAt
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(
                identifier: "\(alarmPrefix)\(day.epochDay)",
                content: content,
                trigger: trigger
            )
            try? await center.add(request)
        }
    }

    private static func sound(for alarm: ShiftAlarm) -> UNNotificationSound {
        guard !alarm.soundName.isEmpty else { return .default }
        return UNNotificationSound(named: UNNotificationSoundName(alarm.soundName))
    }

    /// 알림을 누르지 않고 "다시 울리기" 를 골랐을 때
    static func snooze(minutes: Int, title: String) async {
        guard minutes > 0 else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = "다시 알려 드립니다"
        content.sound = .default
        content.categoryIdentifier = categoryIdentifier
        content.interruptionLevel = .timeSensitive

        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: TimeInterval(minutes * 60),
            repeats: false
        )
        let request = UNNotificationRequest(
            identifier: "\(alarmPrefix)snooze-\(UUID().uuidString)",
            content: content,
            trigger: trigger
        )
        try? await center.add(request)
    }

    // MARK: 아침 근무 알림

    /// 지정한 시각에 그날 근무·점검·일정을 한 줄로 알려 준다.
    private static func scheduleDailyBrief(context: ModelContext) async {
        guard Prefs.isBriefEnabled else { return }

        let reader = ScheduleReader(context: context)
        let today = Day.today
        let schedule = reader.shifts(from: today, count: 7)

        for offset in 0..<7 {
            let day = today.adding(days: offset)
            guard let resolved = schedule[day] else { continue }
            let type = resolved.type

            if let type, !type.isWorking, !Prefs.briefOnRestDays { continue }

            let fireAt = day.at(hour: Prefs.briefHour, minute: Prefs.briefMinute)
            guard fireAt > Date() else { continue }

            let content = UNMutableNotificationContent()
            if let type {
                content.title = type.isWorking ? "오늘은 \(type.name) 근무입니다" : "오늘은 \(type.name)입니다"
            } else {
                content.title = "오늘은 근무 정보가 없습니다"
            }
            content.body = briefBody(context: context, day: day, type: type)
            content.sound = .default

            let components = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: fireAt
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(
                identifier: "\(briefPrefix)\(day.epochDay)",
                content: content,
                trigger: trigger
            )
            try? await center.add(request)
        }
    }

    private static func briefBody(context: ModelContext, day: Day, type: ShiftType?) -> String {
        var lines: [String] = []

        if let type, type.isWorking, !type.startTime.isEmpty {
            lines.append("근무 시간 \(type.timeRange)")
        }

        let epochDay = day.epochDay
        let runDescriptor = FetchDescriptor<CheckRun>(predicate: #Predicate { $0.epochDay == epochDay })
        let runs = (try? context.fetch(runDescriptor)) ?? []
        if !runs.isEmpty { lines.append("점검 \(runs.count)건") }

        let start = day.startOfDay
        let end = day.adding(days: 1).startOfDay
        let taskDescriptor = FetchDescriptor<TaskItem>(
            predicate: #Predicate { $0.dueAt >= start && $0.dueAt < end && !$0.done },
            sortBy: [SortDescriptor(\.dueAt)]
        )
        let tasks = (try? context.fetch(taskDescriptor)) ?? []
        for task in tasks.prefix(3) { lines.append("일정 · \(task.title)") }
        if tasks.count > 3 { lines.append("일정 외 \(tasks.count - 3)건") }

        return lines.isEmpty ? "등록된 일정과 점검이 없습니다." : lines.joined(separator: " · ")
    }

    // MARK: 일정 알림

    /// 일정 하나의 알림을 다시 건다. 저장할 때마다 부른다.
    static func rescheduleTask(_ task: TaskItem) {
        let id = "\(taskPrefix)\(task.uuid)"
        center.removePendingNotificationRequests(withIdentifiers: [id])

        guard task.reminderMinutesBefore >= 0, !task.done else { return }
        let fireAt = task.dueAt.addingTimeInterval(TimeInterval(-task.reminderMinutesBefore * 60))
        guard fireAt > Date() else { return }

        let content = UNMutableNotificationContent()
        content.title = task.title
        content.body = task.memo.isEmpty ? "일정 시간입니다" : task.memo
        content.sound = .default

        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute], from: fireAt
        )
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}

/// "다시 울리기" 를 눌렀을 때를 받는다.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard response.actionIdentifier == ShiftAlarms.snoozeAction else { return }
        let info = response.notification.request.content.userInfo
        let minutes = info["snoozeMinutes"] as? Int ?? 5
        await ShiftAlarms.snooze(minutes: minutes, title: response.notification.request.content.title)
    }
}
