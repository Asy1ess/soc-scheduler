import SwiftData
import SwiftUI

/// 안드로이드판과 같은 다섯 갈래: 근무표 · 일정 · 알람 · 점검 · 설정
struct RootView: View {

    @Environment(\.modelContext) private var context

    var body: some View {
        TabView {
            ShiftCalendarView()
                .tabItem { Label("근무표", systemImage: "calendar") }

            TaskListView()
                .tabItem { Label("일정", systemImage: "square.and.pencil") }

            AlarmSettingsView()
                .tabItem { Label("알람", systemImage: "bell") }

            ChecklistView()
                .tabItem { Label("점검", systemImage: "checkmark.circle") }

            SettingsView()
                .tabItem { Label("설정", systemImage: "gearshape") }
        }
        .task {
            ScheduleWriter(context: context).ensureCheckRuns(for: .today)
            ShiftAlarms.rescheduleAll(context: context)
        }
    }
}

// MARK: - 화면 곳곳에서 쓰는 조각

/// 제목이 붙은 묶음. 안드로이드판의 SectionCard 와 같은 역할이다.
struct SectionCard<Content: View>: View {
    var title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

/// 근무 유형을 나타내는 작은 알약
struct ShiftBadge: View {
    var type: ShiftType?
    var compact = false

    var body: some View {
        Text(type?.shortLabel ?? "–")
            .font(compact ? .caption : .subheadline)
            .fontWeight(.semibold)
            .foregroundStyle(type?.color ?? .secondary)
    }
}

struct EmptyNotice: View {
    var text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
    }
}
