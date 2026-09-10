import SwiftData
import SwiftUI

/// 하루의 근무를 직접 바꾼다.
///
/// 현재 패턴에 쓰이지 않는 근무는 접어 둔다. 4조 2교대로 바꾸면 3교대 전용인
/// "오후" 가 목록에서 빠지고, 필요하면 펼쳐서 고를 수 있다.
struct OverrideSheet: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    var day: Day
    var resolved: ResolvedShift?

    @State private var alsoNextDayOff = true
    @State private var showOthers = false

    private var reader: ScheduleReader { ScheduleReader(context: context) }

    private var nightType: ShiftType? {
        reader.relevantTypes.first { $0.isNight }
    }

    private var offDutyName: String {
        reader.sortedTypes.first { $0.isOffDuty }?.name ?? "비번"
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if let base = resolved?.baseType {
                        Text("기존 근무: \(base.name)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    ForEach(reader.relevantTypes) { type in
                        row(for: type)
                    }

                    if !reader.otherTypes.isEmpty {
                        if showOthers {
                            ForEach(reader.otherTypes) { type in
                                row(for: type)
                            }
                        } else {
                            Button("다른 근무 유형도 보기 (\(reader.otherTypes.count))") {
                                showOthers = true
                            }
                        }
                    }
                }

                if let nightType {
                    Section {
                        Toggle(isOn: $alsoNextDayOff) {
                            Text("\(nightType.name) 선택 시 다음 날도 \(offDutyName)으로 함께 변경")
                                .font(.subheadline)
                        }
                    }
                }

                if resolved?.isOverride == true {
                    Section {
                        Button("패턴대로 되돌리기", role: .destructive) {
                            ScheduleWriter(context: context).clearOverride(day: day)
                            ShiftAlarms.rescheduleAll(context: context)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("\(day.full) 근무 변경")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
                }
            }
        }
    }

    private func row(for type: ShiftType) -> some View {
        Button {
            ScheduleWriter(context: context).setOverride(
                day: day,
                type: type,
                alsoNextDayOff: alsoNextDayOff
            )
            ShiftAlarms.rescheduleAll(context: context)
            dismiss()
        } label: {
            HStack(spacing: 10) {
                Circle()
                    .fill(type.color)
                    .frame(width: 12, height: 12)
                Text(type.name)
                    .foregroundStyle(.primary)
                if !type.startTime.isEmpty {
                    Text(type.timeRange)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if resolved?.baseType?.code == type.code {
                    Text("기존")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if resolved?.type?.code == type.code {
                    Image(systemName: "checkmark")
                        .foregroundStyle(.tint)
                }
            }
        }
    }
}
