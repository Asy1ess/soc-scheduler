import SwiftData
import SwiftUI

/// 근무 유형별 기상 알람.
///
/// 켜기만 하면 기본 시각(주간 07:30, 야간 16:00)이 들어가고, 근무표에서 그
/// 근무인 날에만 울린다. 비번·휴무처럼 근무가 없는 유형은 목록에 나오지 않는다.
struct AlarmSettingsView: View {

    @Environment(\.modelContext) private var context

    @Query private var alarms: [ShiftAlarm]
    @Query private var patterns: [ShiftPattern]

    private var reader: ScheduleReader { ScheduleReader(context: context) }

    /// 현재 패턴에 쓰이는 근무 유형만. 4조 2교대면 "오후" 는 나오지 않는다.
    private var workingTypes: [ShiftType] {
        reader.relevantTypes.filter(\.isWorking)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("근무 유형별로 켜기만 하면 됩니다. 켜면 기본 시각(주간 07:30, 야간 16:00)이 들어가고, 근무표에서 그 근무인 날에만 알람이 울립니다.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("비번 · 휴무처럼 근무가 없는 날은 알람이 울리지 않습니다.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section {
                    ForEach(workingTypes) { type in
                        if let alarm = alarms.first(where: { $0.shiftTypeCode == type.code }) {
                            AlarmRow(type: type, alarm: alarm)
                        }
                    }
                } header: {
                    Text("근무별 알람")
                } footer: {
                    if let next = nextAlarmLabel() {
                        Text("다음 알람 \(next)")
                    }
                }

                Section {
                    Text("iOS 는 앱이 시스템 알람을 만들 수 없어 알림으로 울립니다. 소리는 30초까지 나고, 무음 스위치를 올려 두면 소리가 나지 않습니다. 확실히 깨어나야 하는 날은 시계 앱 알람과 함께 쓰세요.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("알아 두실 점")
                }
            }
            .navigationTitle("근무 기상 알람")
            .task { createMissingAlarms() }
        }
    }

    /// 아직 없는 근무 유형의 알람을 기본값으로 만들어 둔다.
    ///
    /// 화면을 그리는 도중에 DB 를 건드리면 SwiftUI 가 같은 프레임 안에서 상태가
    /// 바뀌었다고 보고 갱신이 꼬인다. 그래서 body 가 아니라 여기서 채운다.
    private func createMissingAlarms() {
        let existing = Set(alarms.map(\.shiftTypeCode))
        var added = false
        for type in workingTypes where !existing.contains(type.code) {
            context.insert(defaultAlarm(for: type))
            added = true
        }
        if added { try? context.save() }
    }

    private func nextAlarmLabel() -> String? {
        let enabled = alarms.filter(\.enabled)
        guard !enabled.isEmpty else { return nil }
        let byCode = Dictionary(uniqueKeysWithValues: enabled.map { ($0.shiftTypeCode, $0) })

        let today = Day.today
        let schedule = reader.shifts(from: today, count: 30)
        for offset in 0..<30 {
            let day = today.adding(days: offset)
            guard let type = schedule[day]?.type,
                  type.isWorking,
                  let alarm = byCode[type.code] else { continue }
            let fireAt = day.at(hour: alarm.hour, minute: alarm.minute)
            if fireAt > Date() {
                return "\(day.short) \(alarm.timeLabel)"
            }
        }
        return nil
    }
}

// MARK: - 근무 한 줄

private struct AlarmRow: View {

    @Environment(\.modelContext) private var context

    var type: ShiftType
    @Bindable var alarm: ShiftAlarm

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle(isOn: $alarm.enabled) {
                HStack(spacing: 10) {
                    Circle()
                        .fill(type.color)
                        .frame(width: 10, height: 10)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(type.name)
                        Text(type.startTime.isEmpty ? "휴식" : "근무 \(type.timeRange)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .onChange(of: alarm.enabled) { _, _ in commit() }

            if alarm.enabled {
                DatePicker(
                    "기상 시각",
                    selection: Binding(
                        get: { Day.today.at(hour: alarm.hour, minute: alarm.minute) },
                        set: { newValue in
                            let parts = Calendar.current.dateComponents([.hour, .minute], from: newValue)
                            alarm.hour = parts.hour ?? alarm.hour
                            alarm.minute = parts.minute ?? alarm.minute
                            commit()
                        }
                    ),
                    displayedComponents: .hourAndMinute
                )

                Picker("다시 울림", selection: $alarm.snoozeMinutes) {
                    Text("안 함").tag(0)
                    ForEach([5, 10, 15, 30], id: \.self) { Text("\($0)분").tag($0) }
                }
                .onChange(of: alarm.snoozeMinutes) { _, _ in commit() }
            }
        }
        .padding(.vertical, 2)
    }

    private func commit() {
        try? context.save()
        ShiftAlarms.rescheduleAll(context: context)
    }
}
