import SwiftData
import SwiftUI

/// 첫 실행과 "근무 설정 다시 하기" 에서 쓰는 설정 화면.
///
/// 안드로이드판과 같은 순서다. 근무 형태를 고르고, 교대 근무를 시작한 날과
/// 그날이 사이클의 몇 번째 날인지를 정하면 근무표 전체가 그에 맞춰진다.
struct OnboardingView: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    var isFirstRun: Bool
    var onDone: () -> Void

    @State private var step = 0
    @State private var preset: PatternPreset? = PatternPresets.all[0]
    @State private var cycle: [Int] = PatternPresets.all[0].cycle
    @State private var startDay: Day = .today
    @State private var startIndex = 0
    @State private var clearMode: ClearMode = .fromStart

    /// 스케줄을 다시 정할 때 기존 수동 변경을 어떻게 할지
    enum ClearMode: String, CaseIterable, Identifiable {
        case keep, fromStart, all
        var id: String { rawValue }
        var label: String {
            switch self {
            case .keep: "그대로 두기"
            case .fromStart: "시작일 이후만 지우기"
            case .all: "전부 지우기"
            }
        }
    }

    private var reader: ScheduleReader { ScheduleReader(context: context) }
    private var writer: ScheduleWriter { ScheduleWriter(context: context) }

    private var types: [Int: ShiftType] { reader.types }

    private var overrideCount: Int { writer.overrideCount(from: nil) }
    private var futureOverrideCount: Int { writer.overrideCount(from: startDay) }

    var body: some View {
        NavigationStack {
            Form {
                switch step {
                case 0: formStep
                case 1: cycleStep
                default: previewStep
                }
            }
            .navigationTitle(isFirstRun ? "초기 설정" : "근무 설정 변경")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if step > 0 {
                        Button("이전") { step -= 1 }
                    } else if !isFirstRun {
                        Button("취소") { dismiss(); onDone() }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if step < 2 {
                        Button("다음") { step += 1 }
                            .disabled(cycle.isEmpty)
                    } else {
                        Button(isFirstRun ? "시작하기" : "저장") { finish() }
                    }
                }
            }
        }
        .onAppear(perform: loadCurrent)
    }

    // MARK: 1단계 — 근무 형태

    private var formStep: some View {
        Section {
            ForEach(PatternPresets.all) { item in
                Button {
                    preset = item
                    cycle = item.cycle
                    startIndex = 0
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name).foregroundStyle(.primary)
                            Text(item.description)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if preset?.name == item.name {
                            Image(systemName: "checkmark").foregroundStyle(.tint)
                        }
                    }
                }
            }
        } header: {
            Text("어떤 형태로 근무하시나요?")
        } footer: {
            Text("가까운 것을 고르면 다음 단계에서 세부 조정을 할 수 있습니다.")
        }
    }

    // MARK: 2단계 — 시작일과 일차

    private var cycleStep: some View {
        Group {
            Section {
                DatePicker(
                    "교대 근무 시작일",
                    selection: Binding(
                        get: { startDay.noon },
                        set: { startDay = Day($0) }
                    ),
                    displayedComponents: .date
                )
            } header: {
                Text("근무 주기를 확인해 주세요")
            } footer: {
                Text("이 날짜부터 아래 주기가 반복됩니다. 이전 날짜는 기본 주간 일정(평일 주간, 주말 휴무)으로 채워집니다.")
            }

            Section {
                Picker("시작일의 일차", selection: $startIndex) {
                    ForEach(Array(cycle.enumerated()), id: \.offset) { index, code in
                        Text("\(index + 1)일차 \(types[code]?.shortLabel ?? "")").tag(index)
                    }
                }
                .pickerStyle(.wheel)
            } header: {
                Text("시작일이 몇 일차인가요?")
            } footer: {
                Text("\(startDay.full)에 실제로 했던 근무와 같은 칸을 고르세요.")
            }

            if overrideCount > 0 {
                Section {
                    Picker("정리 방법", selection: $clearMode) {
                        ForEach(ClearMode.allCases) { mode in
                            Text(label(for: mode)).tag(mode)
                        }
                    }
                } header: {
                    Text("직접 바꾼 근무 정리")
                } footer: {
                    Text("지운 날짜는 새 근무표대로 다시 계산됩니다. 시작일 이전에 직접 바꿔 둔 근무를 남기려면 '시작일 이후만'을 고르세요.")
                }
            }
        }
    }

    private func label(for mode: ClearMode) -> String {
        switch mode {
        case .keep: mode.label
        case .fromStart: "\(mode.label) (\(futureOverrideCount))"
        case .all: "\(mode.label) (\(overrideCount))"
        }
    }

    // MARK: 3단계 — 미리보기

    private var previewStep: some View {
        Section {
            ForEach(0..<7, id: \.self) { offset in
                let day = startDay.adding(days: offset)
                let code = cycle.isEmpty ? nil : cycle[(startIndex + offset) % cycle.count]
                let type = code.flatMap { types[$0] }
                HStack(spacing: 10) {
                    Circle()
                        .fill(type?.color ?? .gray)
                        .frame(width: 10, height: 10)
                    Text(day.short)
                    Spacer()
                    Text(type?.name ?? "–")
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("이렇게 적용됩니다")
        } footer: {
            Text("맨 위가 교대 근무 시작일입니다.")
        }
    }

    // MARK: 불러오기 / 저장

    private func loadCurrent() {
        guard let pattern = reader.activePattern, !pattern.cycle.isEmpty else { return }
        cycle = pattern.cycle
        preset = PatternPresets.all.first { $0.name == pattern.name }
        if let start = pattern.startDay {
            startDay = start
            startIndex = ShiftEngine.cycleIndex(pattern: pattern, date: start)
        }
    }

    private func finish() {
        guard !cycle.isEmpty else { dismiss(); onDone(); return }

        writer.applyCycle(
            name: preset?.name ?? "직접 설정",
            cycle: cycle,
            startDay: startDay,
            startIndex: startIndex,
            teamCount: preset?.teamCount ?? 1
        )

        // 스케줄을 다시 정했으므로 예전에 직접 바꿔 둔 근무를 선택에 따라 정리한다.
        // 기준은 오늘이 아니라 근무 시작일이다. 시작일 이전 기록은 건드리지 않는다.
        switch clearMode {
        case .keep: break
        case .fromStart: writer.clearOverrides(from: startDay)
        case .all: writer.clearOverrides(from: nil)
        }

        writer.ensureCheckRuns(for: .today)
        ShiftAlarms.rescheduleAll(context: context)

        dismiss()
        onDone()
    }
}
