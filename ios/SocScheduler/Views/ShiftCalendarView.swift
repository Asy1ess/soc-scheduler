import SwiftData
import SwiftUI

/// 월 달력 + 고른 날의 상세. 안드로이드판 ShiftScreen 에 대응한다.
struct ShiftCalendarView: View {

    @Environment(\.modelContext) private var context

    @State private var anchorMonth: Day = Day.today.monthStart
    @State private var selected: Day = .today
    @State private var showOverrideSheet = false

    /// SwiftData 변경을 화면에 전달받기 위한 구독. 값 자체는 아래에서 다시 읽는다.
    @Query private var overrideRows: [ShiftOverride]
    @Query private var patterns: [ShiftPattern]
    @Query private var taskRows: [TaskItem]

    private var reader: ScheduleReader { ScheduleReader(context: context) }

    private var gridStart: Day { anchorMonth.weekStart }

    private var schedule: [Day: ResolvedShift] {
        reader.shifts(from: gridStart, count: 42)
    }

    private var pattern: ShiftPattern? { patterns.first { $0.isActive } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    monthHeader
                    weekdayHeader
                    monthGrid
                    summaryChips
                    dayDetail
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(anchorMonth.monthLabel)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("오늘") { goToday() }
                }
            }
            .sheet(isPresented: $showOverrideSheet) {
                OverrideSheet(day: selected, resolved: schedule[selected])
                    .presentationDetents([.medium, .large])
            }
        }
    }

    // MARK: 머리말

    private var monthHeader: some View {
        HStack {
            Button { move(-1) } label: {
                Image(systemName: "chevron.left")
            }
            .accessibilityLabel("이전 달")

            Spacer()

            VStack(spacing: 2) {
                Text(anchorMonth.monthLabel)
                    .font(.title3.bold())
                if let pattern {
                    Text(pattern.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            Button { move(1) } label: {
                Image(systemName: "chevron.right")
            }
            .accessibilityLabel("다음 달")
        }
    }

    private var weekdayHeader: some View {
        HStack(spacing: 4) {
            ForEach(Array(Day.weekdayLabels.enumerated()), id: \.offset) { index, label in
                Text(label)
                    .font(.caption)
                    .foregroundStyle(weekdayColor(index))
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func weekdayColor(_ index: Int) -> Color {
        switch index {
        case 0: .red
        case 6: .blue
        default: .secondary
        }
    }

    // MARK: 달력

    private var monthGrid: some View {
        let days = (0..<42).map { gridStart.adding(days: $0) }
        let taskCounts = openTaskCounts()

        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
            ForEach(days) { day in
                DayCell(
                    day: day,
                    shift: schedule[day],
                    inMonth: day.month == anchorMonth.month,
                    isSelected: day == selected,
                    hasTask: (taskCounts[day] ?? 0) > 0
                )
                .onTapGesture { select(day) }
            }
        }
    }

    private func openTaskCounts() -> [Day: Int] {
        var counts: [Day: Int] = [:]
        for task in taskRows where !task.done {
            counts[task.day, default: 0] += 1
        }
        return counts
    }

    // MARK: 이 달 집계

    private var summaryChips: some View {
        let counts = monthCounts()
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(counts, id: \.type.code) { entry in
                    HStack(spacing: 6) {
                        Text(entry.type.shortLabel)
                            .fontWeight(.semibold)
                            .foregroundStyle(entry.type.color)
                        Text("\(entry.count)")
                            .foregroundStyle(.secondary)
                    }
                    .font(.subheadline)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(entry.type.color.opacity(0.12))
                    .clipShape(Capsule())
                }
            }
        }
    }

    private func monthCounts() -> [(type: ShiftType, count: Int)] {
        var counts: [Int: Int] = [:]
        var byCode: [Int: ShiftType] = [:]
        for offset in 0..<42 {
            let day = gridStart.adding(days: offset)
            guard day.month == anchorMonth.month, day.year == anchorMonth.year,
                  let type = schedule[day]?.type else { continue }
            counts[type.code, default: 0] += 1
            byCode[type.code] = type
        }
        return counts
            .compactMap { code, count in byCode[code].map { (type: $0, count: count) } }
            .sorted { $0.type.sortOrder < $1.type.sortOrder }
    }

    // MARK: 고른 날 상세

    private var dayDetail: some View {
        let resolved = schedule[selected]
        let tasks = tasksOn(selected)

        return SectionCard(title: selected.full) {
            HStack {
                if let type = resolved?.type {
                    Circle()
                        .fill(type.color)
                        .frame(width: 12, height: 12)
                    Text(type.name)
                        .font(.title3.bold())
                    if !type.startTime.isEmpty {
                        Text(type.timeRange)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Text("근무 없음").foregroundStyle(.secondary)
                }

                Spacer()

                if resolved?.isOverride == true {
                    Text("수동 변경")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }

            if resolved?.beforeStart == true {
                Text("교대 근무 시작 전이라 기본 주간 일정이 적용된 날입니다.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if resolved?.changedFromBase == true, let base = resolved?.baseType {
                Text("기존 근무: \(base.name)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let memo = resolved?.memo, !memo.isEmpty {
                Text("변경 사유: \(memo)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Divider()

            Text("일정 \(tasks.count)건")
                .font(.subheadline)
            if tasks.isEmpty {
                Text("등록된 일정이 없습니다.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(tasks) { task in
                    HStack {
                        Image(systemName: task.done ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(task.done ? .green : .secondary)
                        Text(task.title)
                            .strikethrough(task.done)
                        Spacer()
                    }
                    .font(.subheadline)
                }
            }

            Button {
                showOverrideSheet = true
            } label: {
                Text("근무 변경")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .padding(.top, 4)
        }
    }

    private func tasksOn(_ day: Day) -> [TaskItem] {
        taskRows
            .filter { $0.day == day }
            .sorted { $0.dueAt < $1.dueAt }
    }

    // MARK: 동작

    private func move(_ delta: Int) {
        anchorMonth = anchorMonth.adding(months: delta)
    }

    private func select(_ day: Day) {
        selected = day
        if day.month != anchorMonth.month || day.year != anchorMonth.year {
            anchorMonth = day.monthStart
        }
    }

    private func goToday() {
        let today = Day.today
        anchorMonth = today.monthStart
        selected = today
    }
}

// MARK: - 달력 한 칸

private struct DayCell: View {
    var day: Day
    var shift: ResolvedShift?
    var inMonth: Bool
    var isSelected: Bool
    var hasTask: Bool

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 2) {
                Text("\(day.dayOfMonth)")
                    .font(.caption)
                    .fontWeight(day.isToday ? .bold : .regular)
                if hasTask {
                    Circle()
                        .fill(.pink)
                        .frame(width: 4, height: 4)
                }
            }
            Text(shift?.type?.shortLabel ?? "")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(shift?.type?.color ?? .secondary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 52)
        .background((shift?.type?.color ?? .gray).opacity(inMonth ? 0.12 : 0.05))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.accentColor : .clear, lineWidth: 2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .opacity(inMonth ? 1 : 0.4)
    }
}
