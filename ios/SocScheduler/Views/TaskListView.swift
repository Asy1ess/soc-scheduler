import SwiftData
import SwiftUI

/// 일정 · 할 일. 지난 일정 / 오늘 / 예정으로 나눠 보여 준다.
struct TaskListView: View {

    @Environment(\.modelContext) private var context

    @Query(sort: \TaskItem.dueAt) private var tasks: [TaskItem]

    @State private var showDone = false
    @State private var editing: TaskItem?
    @State private var isCreating = false

    private var visible: [TaskItem] {
        tasks.filter { $0.done == showDone }
    }

    private var groups: [(title: String, items: [TaskItem])] {
        let today = Day.today
        let past = visible.filter { $0.day < today }
        let now = visible.filter { $0.day == today }
        let future = visible.filter { $0.day > today }
        return [
            ("지난 일정", past),
            ("오늘", now),
            ("예정", future),
        ].filter { !$0.1.isEmpty }
    }

    var body: some View {
        NavigationStack {
            Group {
                if visible.isEmpty {
                    EmptyNotice(text: showDone
                        ? "완료한 일정이 없습니다."
                        : "등록된 일정이 없습니다. 오른쪽 위 + 로 추가하세요.")
                } else {
                    List {
                        ForEach(groups, id: \.title) { group in
                            Section(group.title) {
                                ForEach(group.items) { task in
                                    row(task)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("일정 · 할 일")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Picker("", selection: $showDone) {
                        Text("진행 중").tag(false)
                        Text("완료 (\(tasks.filter(\.done).count))").tag(true)
                    }
                    .pickerStyle(.segmented)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isCreating = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("일정 추가")
                }
            }
            .sheet(isPresented: $isCreating) {
                TaskEditView(task: nil)
            }
            .sheet(item: $editing) { task in
                TaskEditView(task: task)
            }
        }
    }

    private func row(_ task: TaskItem) -> some View {
        HStack(spacing: 12) {
            Button {
                toggle(task)
            } label: {
                Image(systemName: task.done ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(task.done ? .green : .secondary)
                    .font(.title3)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if task.isImportant {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundStyle(.orange)
                            .font(.caption)
                    }
                    Text(task.title)
                        .strikethrough(task.done)
                }
                HStack(spacing: 6) {
                    Text(task.category)
                    Text(task.dueAt, format: .dateTime.month().day().hour().minute())
                    if task.reminderMinutesBefore >= 0 {
                        Text("· 알림 설정됨")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .contentShape(Rectangle())
        .onTapGesture { editing = task }
        .swipeActions {
            Button("삭제", role: .destructive) {
                context.delete(task)
                try? context.save()
            }
        }
    }

    private func toggle(_ task: TaskItem) {
        task.done.toggle()
        task.doneAt = task.done ? Date() : nil
        try? context.save()
        ShiftAlarms.rescheduleTask(task)
    }
}

// MARK: - 일정 편집

struct TaskEditView: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    var task: TaskItem?

    @State private var title = ""
    @State private var memo = ""
    @State private var dueAt = Date()
    @State private var category = TaskCategory.all[0]
    @State private var important = false
    @State private var reminder = -1

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("제목", text: $title)
                    TextField("메모", text: $memo, axis: .vertical)
                        .lineLimit(2...5)
                }

                Section {
                    DatePicker("일시", selection: $dueAt)
                    Picker("분류", selection: $category) {
                        ForEach(TaskCategory.all, id: \.self) { Text($0) }
                    }
                    Picker("알림", selection: $reminder) {
                        ForEach(ReminderOffset.all, id: \.minutes) { option in
                            Text(option.label).tag(option.minutes)
                        }
                    }
                    Toggle("중요", isOn: $important)
                }
            }
            .navigationTitle(task == nil ? "일정 추가" : "일정 수정")
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
        guard let task else { return }
        title = task.title
        memo = task.memo
        dueAt = task.dueAt
        category = task.category
        important = task.isImportant
        reminder = task.reminderMinutesBefore
    }

    private func save() {
        let target: TaskItem
        if let task {
            target = task
        } else {
            target = TaskItem(title: title, dueAt: dueAt)
            context.insert(target)
        }
        target.title = title.trimmingCharacters(in: .whitespaces)
        target.memo = memo
        target.dueAt = dueAt
        target.category = category
        target.priority = important ? 1 : 0
        target.reminderMinutesBefore = reminder
        try? context.save()
        ShiftAlarms.rescheduleTask(target)
        dismiss()
    }
}
