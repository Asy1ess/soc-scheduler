import SwiftData
import SwiftUI
import WidgetKit

/// 홈 화면 위젯. 앱과 같은 App Group 저장소를 읽는다.
///
/// 안드로이드판은 위젯이 넷(오늘 근무 · 이번 주 · 월간 · 오늘 할 일)인데,
/// 여기서는 크기에 따라 하나가 모습을 바꾼다. iOS 는 같은 위젯이 small /
/// medium / large 로 늘어나므로 그쪽 관례를 따랐다.

struct ShiftEntry: TimelineEntry {
    var date: Date
    var day: Day
    /// 오늘부터 7일간의 근무 (위젯 크기에 따라 앞부분만 쓴다)
    var week: [(day: Day, name: String, label: String, colorHex: String, changed: Bool)]
    var timeRange: String
    var hasPattern: Bool
}

struct ShiftProvider: TimelineProvider {

    func placeholder(in context: Context) -> ShiftEntry {
        ShiftEntry(
            date: Date(),
            day: .today,
            week: [],
            timeRange: "",
            hasPattern: false
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (ShiftEntry) -> Void) {
        completion(load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ShiftEntry>) -> Void) {
        // 자정에 다시 그린다. 날짜가 바뀌면 오늘 근무도 바뀌기 때문이다.
        let entry = load()
        let nextMidnight = Day.today.adding(days: 1).startOfDay
        completion(Timeline(entries: [entry], policy: .after(nextMidnight)))
    }

    private func load() -> ShiftEntry {
        let container = Store.container
        let reader = ScheduleReader(context: ModelContext(container))
        let today = Day.today
        let schedule = reader.shifts(from: today, count: 7)

        var week: [(Day, String, String, String, Bool)] = []
        for offset in 0..<7 {
            let day = today.adding(days: offset)
            guard let resolved = schedule[day] else { continue }
            week.append((
                day,
                resolved.type?.name ?? "근무 없음",
                resolved.type?.shortLabel ?? "–",
                resolved.type?.colorHex ?? "FF9E9E9E",
                resolved.isOverride
            ))
        }

        let todayShift = schedule[today]
        return ShiftEntry(
            date: Date(),
            day: today,
            week: week.map { (day: $0.0, name: $0.1, label: $0.2, colorHex: $0.3, changed: $0.4) },
            timeRange: todayShift?.type?.timeRange ?? "",
            hasPattern: reader.activePattern != nil
        )
    }
}

struct ShiftWidgetView: View {

    @Environment(\.widgetFamily) private var family
    var entry: ShiftEntry

    private var today: (day: Day, name: String, label: String, colorHex: String, changed: Bool)? {
        entry.week.first
    }

    var body: some View {
        switch family {
        case .systemSmall: small
        default: wide
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(entry.day.short)
                .font(.caption2)
                .foregroundStyle(.secondary)

            if !entry.hasPattern {
                Text("패턴을 설정해 주세요")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let today {
                Text(today.name)
                    .font(.title2.bold())
                    .foregroundStyle(Color(hex: today.colorHex))
                Text(entry.timeRange.isEmpty ? "휴식" : entry.timeRange)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if today.changed {
                    Text("변경됨")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var wide: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("이번 주 근무")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 6) {
                ForEach(entry.week, id: \.day) { item in
                    VStack(spacing: 3) {
                        Text(item.day.weekdayLabel)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Text("\(item.day.dayOfMonth)")
                            .font(.caption2)
                            .fontWeight(item.day.isToday ? .bold : .regular)
                        Text(item.label)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color(hex: item.colorHex))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color(hex: item.colorHex).opacity(item.day.isToday ? 0.22 : 0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 7))
                }
            }
            Spacer(minLength: 0)
        }
    }
}

struct ShiftWidget: Widget {
    let kind = "ShiftWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ShiftProvider()) { entry in
            ShiftWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("근무표")
        .description("오늘 근무와 이번 주 근무를 홈 화면에서 바로 봅니다.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct SocSchedulerWidgetBundle: WidgetBundle {
    var body: some Widget {
        ShiftWidget()
    }
}
