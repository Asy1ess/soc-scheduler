import SwiftData
import SwiftUI

/// 정기 점검. 날짜를 옮겨 가며 그날 해당하는 점검을 체크한다.
struct ChecklistView: View {

    @Environment(\.modelContext) private var context

    @Query private var runs: [CheckRun]
    @Query(sort: \CheckTemplate.sortOrder) private var templates: [CheckTemplate]

    @State private var day: Day = .today
    @State private var noteTarget: CheckRun?

    private var todayRuns: [CheckRun] {
        runs
            .filter { $0.epochDay == day.epochDay && $0.template?.active == true }
            .sorted { ($0.template?.sortOrder ?? 0) < ($1.template?.sortOrder ?? 0) }
    }

    private var doneCount: Int { todayRuns.filter(\.done).count }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                dayBar

                if todayRuns.isEmpty {
                    EmptyNotice(text: "이 날짜에 해당하는 점검 항목이 없습니다.\n'항목 관리'에서 반복 점검을 등록하세요.")
                    Spacer()
                } else {
                    List {
                        ForEach(todayRuns) { run in
                            row(run)
                        }
                    }
                }
            }
            .navigationTitle("정기 점검")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink("항목 관리") {
                        TemplateListView()
                    }
                }
            }
            .sheet(item: $noteTarget) { run in
                NoteSheet(run: run)
                    .presentationDetents([.medium])
            }
            .onAppear { prepare() }
            .onChange(of: day) { _, _ in prepare() }
        }
    }

    private var dayBar: some View {
        VStack(spacing: 8) {
            HStack {
                Button { day = day.adding(days: -1) } label: {
                    Image(systemName: "chevron.left")
                }
                .accessibilityLabel("이전 날")

                Spacer()
                Text(day.full).font(.headline)
                Spacer()

                Button { day = day.adding(days: 1) } label: {
                    Image(systemName: "chevron.right")
                }
                .accessibilityLabel("다음 날")

                Button("오늘") { day = .today }
                    .font(.subheadline)
            }

            if !todayRuns.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("완료 \(doneCount) / \(todayRuns.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ProgressView(value: Double(doneCount), total: Double(max(todayRuns.count, 1)))
                }
            }
        }
        .padding(16)
    }

    private func row(_ run: CheckRun) -> some View {
        HStack(spacing: 12) {
            Button {
                run.done.toggle()
                run.doneAt = run.done ? Date() : nil
                try? context.save()
            } label: {
                Image(systemName: run.done ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(run.done ? .green : .secondary)
                    .font(.title3)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                Text(run.template?.title ?? "삭제된 항목")
                    .strikethrough(run.done)
                if let memo = run.template?.memo, !memo.isEmpty {
                    Text(memo)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if !run.note.isEmpty {
                    Text("메모: \(run.note)")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }

            Spacer()

            if let time = run.template?.timeLabel, !time.isEmpty {
                Text(time)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { noteTarget = run }
    }

    private func prepare() {
        ScheduleWriter(context: context).ensureCheckRuns(for: day)
    }
}

// MARK: - 점검 메모

private struct NoteSheet: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Bindable var run: CheckRun
    @State private var text = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("점검 메모 (특이사항)") {
                    TextField("메모", text: $text, axis: .vertical)
                        .lineLimit(3...8)
                }
            }
            .navigationTitle(run.template?.title ?? "점검")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") {
                        run.note = text
                        try? context.save()
                        dismiss()
                    }
                }
            }
            .onAppear { text = run.note }
        }
    }
}

// MARK: - 점검 항목 관리

struct TemplateListView: View {

    @Environment(\.modelContext) private var context

    @Query(sort: \CheckTemplate.sortOrder) private var templates: [CheckTemplate]

    @State private var editing: CheckTemplate?
    @State private var isCreating = false

    var body: some View {
        Group {
            if templates.isEmpty {
                EmptyNotice(text: "등록된 점검 항목이 없습니다.")
            } else {
                List {
                    ForEach(templates) { template in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(template.title)
                            HStack(spacing: 6) {
                                Text(template.recurrenceDetail)
                                if !template.timeLabel.isEmpty { Text(template.timeLabel) }
                                if !template.active { Text("· 사용 안 함") }
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { editing = template }
                        .swipeActions {
                            Button("삭제", role: .destructive) { remove(template) }
                        }
                    }
                }
            }
        }
        .navigationTitle("점검 항목 관리")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isCreating = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("항목 추가")
            }
        }
        .sheet(isPresented: $isCreating) {
            TemplateEditView(template: nil)
        }
        .sheet(item: $editing) { template in
            TemplateEditView(template: template)
        }
    }

    private func remove(_ template: CheckTemplate) {
        let id = template.persistentModelID
        let descriptor = FetchDescriptor<CheckRun>()
        let related = ((try? context.fetch(descriptor)) ?? [])
            .filter { $0.template?.persistentModelID == id }
        related.forEach { context.delete($0) }
        context.delete(template)
        try? context.save()
    }
}

private struct TemplateEditView: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    var template: CheckTemplate?

    @State private var title = ""
    @State private var memo = ""
    @State private var recurrence: Recurrence = .daily
    @State private var weekDays: Set<Int> = [2]
    @State private var monthDay = 1
    @State private var timeLabel = ""
    @State private var active = true

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("점검 항목", text: $title)
                    TextField("설명", text: $memo, axis: .vertical)
                        .lineLimit(1...4)
                }

                Section("반복") {
                    Picker("반복", selection: $recurrence) {
                        ForEach(Recurrence.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    if recurrence == .weekly {
                        HStack(spacing: 6) {
                            ForEach(1...7, id: \.self) { index in
                                Button(Day.weekdayLabels[index - 1]) {
                                    if weekDays.contains(index) { weekDays.remove(index) }
                                    else { weekDays.insert(index) }
                                }
                                .buttonStyle(.bordered)
                                .tint(weekDays.contains(index) ? .accentColor : .gray)
                            }
                        }
                    }

                    if recurrence == .monthly {
                        Picker("매월 며칠", selection: $monthDay) {
                            ForEach(1...31, id: \.self) { Text("\($0)일").tag($0) }
                        }
                    }
                }

                Section {
                    TextField("점검 시각 (예: 09:00)", text: $timeLabel)
                    Toggle("사용", isOn: $active)
                }
            }
            .navigationTitle(template == nil ? "점검 항목 추가" : "점검 항목 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { save() }
                        .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let template else { return }
        title = template.title
        memo = template.memo
        recurrence = template.recurrence
        weekDays = Set(template.weekDays)
        monthDay = template.monthDay
        timeLabel = template.timeLabel
        active = template.active
    }

    private func save() {
        let target: CheckTemplate
        if let template {
            target = template
        } else {
            target = CheckTemplate(title: title)
            context.insert(target)
        }
        target.title = title.trimmingCharacters(in: .whitespaces)
        target.memo = memo
        target.recurrence = recurrence
        target.weekDays = weekDays.sorted()
        target.monthDay = monthDay
        target.timeLabel = timeLabel
        target.active = active
        try? context.save()
        ScheduleWriter(context: context).ensureCheckRuns(for: .today)
        dismiss()
    }
}
