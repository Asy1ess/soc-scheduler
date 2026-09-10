import Foundation
import SwiftData

/// SwiftData 컨테이너와, 앱과 위젯이 함께 쓰는 읽기·쓰기 묶음.
///
/// 위젯은 별도 프로세스라 같은 파일을 열어야 한다. 그래서 App Group 컨테이너에
/// 저장한다. `project.yml` 의 entitlements 와 이 식별자가 같아야 한다.
enum Store {

    static let appGroup = "group.com.soc.scheduler"

    static let schema = Schema([
        ShiftType.self,
        ShiftPattern.self,
        ShiftOverride.self,
        ShiftAlarm.self,
        TaskItem.self,
        CheckTemplate.self,
        CheckRun.self,
    ])

    static let container: ModelContainer = {
        let configuration = ModelConfiguration(
            schema: schema,
            groupContainer: .identifier(appGroup)
        )
        do {
            let container = try ModelContainer(for: schema, configurations: configuration)
            seedIfNeeded(container.mainContext)
            return container
        } catch {
            // App Group 이 설정되지 않은 경우에도 앱은 뜨게 한다. 이때 위젯과는
            // 자료를 나눠 갖지 못하므로, 위젯이 비어 보이면 이 경로를 의심하면 된다.
            let fallback = ModelConfiguration(schema: schema)
            let container = try! ModelContainer(for: schema, configurations: fallback)
            seedIfNeeded(container.mainContext)
            return container
        }
    }()

    // MARK: 최초 실행 시 기본값

    /// 최초 설치 시 기본 근무 유형과 교대 패턴을 넣어 준다.
    /// 점검 항목은 쓰는 사람마다 달라서 미리 넣지 않는다.
    static func seedIfNeeded(_ context: ModelContext) {
        let existing = (try? context.fetch(FetchDescriptor<ShiftType>())) ?? []
        guard existing.isEmpty else { return }

        let types: [ShiftType] = [
            ShiftType(code: 1, name: "주간", shortLabel: "주", startTime: "06:00", endTime: "14:00",
                      colorHex: "FF2E7D32", isWorking: true, sortOrder: 0),
            ShiftType(code: 2, name: "오후", shortLabel: "오", startTime: "14:00", endTime: "22:00",
                      colorHex: "FFE65100", isWorking: true, sortOrder: 1),
            ShiftType(code: 3, name: "야간", shortLabel: "야", startTime: "22:00", endTime: "06:00",
                      colorHex: "FF283593", isWorking: true, sortOrder: 2),
            ShiftType(code: 4, name: "비번", shortLabel: "비", startTime: "", endTime: "",
                      colorHex: "FF616161", isWorking: false, sortOrder: 3),
            ShiftType(code: 5, name: "휴무", shortLabel: "휴", startTime: "", endTime: "",
                      colorHex: "FF9E9E9E", isWorking: false, sortOrder: 4),
            ShiftType(code: 6, name: "연차", shortLabel: "연", startTime: "", endTime: "",
                      colorHex: "FF00838F", isWorking: false, sortOrder: 5),
            ShiftType(code: 7, name: "교육/출장", shortLabel: "교", startTime: "", endTime: "",
                      colorHex: "FF6A1B9A", isWorking: false, sortOrder: 6),
        ]
        types.forEach { context.insert($0) }

        let preset = PatternPresets.all[0]
        let today = Day.today
        context.insert(
            ShiftPattern(
                name: preset.name,
                teamCount: preset.teamCount,
                anchorEpochDay: today.epochDay,
                myOffset: 0,
                isActive: true,
                startEpochDay: nil,
                cycle: preset.cycle
            )
        )
        try? context.save()
    }
}

// MARK: - 조회 묶음

/// 화면과 위젯이 같은 방식으로 근무표를 읽도록 모아 둔 곳.
struct ScheduleReader {

    var context: ModelContext

    var types: [Int: ShiftType] {
        let all = (try? context.fetch(FetchDescriptor<ShiftType>())) ?? []
        return Dictionary(uniqueKeysWithValues: all.map { ($0.code, $0) })
    }

    var sortedTypes: [ShiftType] {
        let descriptor = FetchDescriptor<ShiftType>(sortBy: [SortDescriptor(\.sortOrder)])
        return (try? context.fetch(descriptor)) ?? []
    }

    var activePattern: ShiftPattern? {
        let descriptor = FetchDescriptor<ShiftPattern>(predicate: #Predicate { $0.isActive })
        return (try? context.fetch(descriptor))?.first
    }

    func overrides(from: Day, to: Day) -> [Int: ShiftOverride] {
        let lower = from.epochDay
        let upper = to.epochDay
        let descriptor = FetchDescriptor<ShiftOverride>(
            predicate: #Predicate { $0.epochDay >= lower && $0.epochDay <= upper }
        )
        let rows = (try? context.fetch(descriptor)) ?? []
        return Dictionary(uniqueKeysWithValues: rows.map { ($0.epochDay, $0) })
    }

    func shift(on day: Day) -> ResolvedShift {
        ShiftEngine.resolve(
            pattern: activePattern,
            types: types,
            overrides: overrides(from: day, to: day),
            date: day
        )
    }

    /// 달력 한 판(6주)에 들어갈 근무를 한 번에 계산한다.
    func shifts(from: Day, count: Int) -> [Day: ResolvedShift] {
        let pattern = activePattern
        let typeMap = types
        let last = from.adding(days: count - 1)
        let overrideMap = overrides(from: from, to: last)

        var result: [Day: ResolvedShift] = [:]
        for offset in 0..<count {
            let day = from.adding(days: offset)
            result[day] = ShiftEngine.resolve(
                pattern: pattern,
                types: typeMap,
                overrides: overrideMap,
                date: day
            )
        }
        return result
    }

    /// 현재 패턴에 실제로 쓰이는 근무 + 비근무 유형.
    /// 4조 2교대로 바꾸면 3교대 전용인 "오후" 가 빠진다.
    var relevantTypes: [ShiftType] {
        let used = Set(activePattern?.cycle ?? [])
        return sortedTypes.filter { !$0.isWorking || used.contains($0.code) }
    }

    /// 패턴에 쓰이지 않는 근무 유형 (필요할 때 펼쳐 보는 용도)
    var otherTypes: [ShiftType] {
        let used = Set(activePattern?.cycle ?? [])
        return sortedTypes.filter { $0.isWorking && !used.contains($0.code) }
    }
}

// MARK: - 쓰기 묶음

struct ScheduleWriter {

    var context: ModelContext

    /// 근무를 직접 바꾼다.
    ///
    /// 야간으로 바꾸면서 `alsoNextDayOff` 가 켜져 있으면 다음 날도 비번으로
    /// 함께 바꾼다. 야간 다음 날은 보통 비번이라 두 번 고치는 수고를 던다.
    func setOverride(day: Day, type: ShiftType, alsoNextDayOff: Bool = false, memo: String = "") {
        put(day: day, code: type.code, memo: memo)

        if alsoNextDayOff, type.isNight {
            let reader = ScheduleReader(context: context)
            if let offDuty = reader.sortedTypes.first(where: { $0.isOffDuty }) {
                put(day: day.adding(days: 1), code: offDuty.code, memo: "")
            }
        }
        save()
    }

    private func put(day: Day, code: Int, memo: String) {
        let epochDay = day.epochDay
        let descriptor = FetchDescriptor<ShiftOverride>(predicate: #Predicate { $0.epochDay == epochDay })
        if let existing = (try? context.fetch(descriptor))?.first {
            existing.shiftTypeCode = code
            existing.memo = memo
        } else {
            context.insert(ShiftOverride(epochDay: epochDay, shiftTypeCode: code, memo: memo))
        }
    }

    func clearOverride(day: Day) {
        let epochDay = day.epochDay
        let descriptor = FetchDescriptor<ShiftOverride>(predicate: #Predicate { $0.epochDay == epochDay })
        (try? context.fetch(descriptor))?.forEach { context.delete($0) }
        save()
    }

    func clearOverrides(from day: Day?) {
        let descriptor: FetchDescriptor<ShiftOverride>
        if let day {
            let start = day.epochDay
            descriptor = FetchDescriptor<ShiftOverride>(predicate: #Predicate { $0.epochDay >= start })
        } else {
            descriptor = FetchDescriptor<ShiftOverride>()
        }
        (try? context.fetch(descriptor))?.forEach { context.delete($0) }
        save()
    }

    func overrideCount(from day: Day?) -> Int {
        let descriptor: FetchDescriptor<ShiftOverride>
        if let day {
            let start = day.epochDay
            descriptor = FetchDescriptor<ShiftOverride>(predicate: #Predicate { $0.epochDay >= start })
        } else {
            descriptor = FetchDescriptor<ShiftOverride>()
        }
        return (try? context.fetchCount(descriptor)) ?? 0
    }

    /// 초기 설정에서 만든 사이클을 적용한다.
    ///
    /// `startDay` 는 교대 근무를 시작한 날, `startIndex` 는 그날이 사이클의 몇
    /// 번째 날인가이다. 이 둘로 사이클 기준일을 역산하므로 근무표는 항상
    /// 시작일을 기준으로 맞춰진다.
    func applyCycle(name: String, cycle: [Int], startDay: Day, startIndex: Int, teamCount: Int) {
        (try? context.fetch(FetchDescriptor<ShiftPattern>()))?.forEach { context.delete($0) }

        let anchor = startDay.adding(days: -startIndex)
        context.insert(
            ShiftPattern(
                name: name,
                teamCount: teamCount,
                anchorEpochDay: anchor.epochDay,
                myOffset: 0,
                isActive: true,
                startEpochDay: startDay.epochDay,
                cycle: cycle
            )
        )
        save()
    }

    /// 그날 필요한 점검 인스턴스를 만들어 둔다. 이미 있으면 그대로 둔다.
    func ensureCheckRuns(for day: Day) {
        let templates = ((try? context.fetch(FetchDescriptor<CheckTemplate>())) ?? [])
            .filter { $0.active && $0.matches(day) }
        guard !templates.isEmpty else { return }

        let epochDay = day.epochDay
        let descriptor = FetchDescriptor<CheckRun>(predicate: #Predicate { $0.epochDay == epochDay })
        let existing = (try? context.fetch(descriptor)) ?? []
        let covered = Set(existing.compactMap { $0.template?.persistentModelID })

        for template in templates where !covered.contains(template.persistentModelID) {
            context.insert(CheckRun(template: template, epochDay: epochDay))
        }
        save()
    }

    func save() {
        try? context.save()
    }
}
