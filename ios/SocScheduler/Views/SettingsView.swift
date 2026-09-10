import SwiftData
import SwiftUI

struct SettingsView: View {

    @Environment(\.modelContext) private var context
    @Environment(ThemeStore.self) private var theme

    @State private var showOnboarding = false
    @State private var briefEnabled = Prefs.isBriefEnabled
    @State private var briefTime = Day.today.at(hour: Prefs.briefHour, minute: Prefs.briefMinute)
    @State private var briefOnRestDays = Prefs.briefOnRestDays

    var body: some View {
        @Bindable var theme = theme

        NavigationStack {
            List {
                Section("근무 관리") {
                    Button {
                        showOnboarding = true
                    } label: {
                        settingRow("근무 설정 다시 하기", "근무 형태 · 주기 · 근무 시간을 처음 설정 화면에서 다시 정합니다")
                    }

                    NavigationLink {
                        TemplateListView()
                    } label: {
                        settingRow("정기 점검 항목", "매일 · 매주 · 매월 반복 점검을 관리합니다")
                    }
                }

                Section {
                    Picker("화면 테마", selection: $theme.mode) {
                        ForEach(ThemeMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("화면 테마")
                } footer: {
                    Text("기기 설정을 따라가거나, 밝게 · 어둡게로 고정할 수 있습니다.")
                }

                Section {
                    Toggle("매일 오늘 근무 알리기", isOn: $briefEnabled)
                        .onChange(of: briefEnabled) { _, value in
                            Prefs.isBriefEnabled = value
                            ShiftAlarms.rescheduleAll(context: context)
                        }

                    if briefEnabled {
                        DatePicker("알림 시각", selection: $briefTime, displayedComponents: .hourAndMinute)
                            .onChange(of: briefTime) { _, value in
                                let parts = Calendar.current.dateComponents([.hour, .minute], from: value)
                                Prefs.briefHour = parts.hour ?? 7
                                Prefs.briefMinute = parts.minute ?? 30
                                ShiftAlarms.rescheduleAll(context: context)
                            }

                        Toggle("휴무 · 비번인 날에도 알리기", isOn: $briefOnRestDays)
                            .onChange(of: briefOnRestDays) { _, value in
                                Prefs.briefOnRestDays = value
                                ShiftAlarms.rescheduleAll(context: context)
                            }
                    }
                } header: {
                    Text("아침 근무 알림")
                } footer: {
                    Text("지정한 시각에 그날 근무 · 점검 · 일정을 알림으로 보여 줍니다.")
                }

                Section("근무 유형") {
                    ForEach(ScheduleReader(context: context).sortedTypes) { type in
                        HStack(spacing: 10) {
                            Circle()
                                .fill(type.color)
                                .frame(width: 10, height: 10)
                            Text(type.name)
                            Spacer()
                            Text(type.startTime.isEmpty ? "휴식" : type.timeRange)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section {
                    Text("관제 스케줄러 1.0")
                } footer: {
                    Text("모든 데이터는 이 기기 안에만 저장되며 외부로 전송되지 않습니다.")
                }
            }
            .navigationTitle("설정")
            .fullScreenCover(isPresented: $showOnboarding) {
                OnboardingView(isFirstRun: false) {
                    showOnboarding = false
                }
            }
        }
    }

    private func settingRow(_ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).foregroundStyle(.primary)
            Text(detail)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
