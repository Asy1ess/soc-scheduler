import Foundation

/// 교대 패턴으로부터 특정 날짜의 근무를 계산한다.
///
/// 사이클 인덱스 = (기준일로부터 지난 일수 + 내 조 오프셋) mod 사이클 길이
///
/// 안드로이드판 `ShiftEngine.kt` 와 같은 규칙을 따른다. 두 앱이 같은 근무표를
/// 그려야 하므로 이 파일을 고칠 때는 안드로이드 쪽도 함께 고쳐야 한다.
enum ShiftEngine {

    /// 나머지가 음수가 되지 않게 맞춘다. 기준일 이전 날짜도 올바른 일차를 얻는다.
    static func floorMod(_ value: Int, _ modulus: Int) -> Int {
        guard modulus > 0 else { return 0 }
        return ((value % modulus) + modulus) % modulus
    }

    static func cycleIndex(pattern: ShiftPattern, date: Day) -> Int {
        guard pattern.cycleDays > 0 else { return 0 }
        let diff = date.epochDay - pattern.anchorEpochDay
        return floorMod(diff + pattern.myOffset, pattern.cycleDays)
    }

    static func shiftTypeCode(pattern: ShiftPattern?, date: Day) -> Int? {
        guard let pattern, !pattern.cycle.isEmpty else { return nil }
        let index = cycleIndex(pattern: pattern, date: date)
        guard index < pattern.cycle.count else { return nil }
        return pattern.cycle[index]
    }

    /// 근무 시작일 이전인가 (수습 기간 등 교대 근무를 하지 않던 구간)
    static func isBeforeStart(pattern: ShiftPattern?, date: Day) -> Bool {
        guard let start = pattern?.startEpochDay else { return false }
        return date.epochDay < start
    }

    /// 날짜별 근무를 확정한다.
    ///
    /// 우선순위는 수동 변경 > 패턴 순이며, 수동 변경이 있어도
    /// `ResolvedShift.baseType` 으로 기존 근무를 함께 돌려준다.
    ///
    /// 근무 시작일 이전(수습 기간 등)은 교대 패턴 대신 기본 주간 일정을 깔아 둔다.
    /// 그 구간에도 직접 지정한 근무가 있으면 그것을 우선한다.
    static func resolve(
        pattern: ShiftPattern?,
        types: [Int: ShiftType],
        overrides: [Int: ShiftOverride],
        date: Day
    ) -> ResolvedShift {
        let beforeStart = isBeforeStart(pattern: pattern, date: date)
        let base: ShiftType? = beforeStart
            ? preStartType(types: types, date: date)
            : shiftTypeCode(pattern: pattern, date: date).flatMap { types[$0] }

        if let override = overrides[date.epochDay] {
            return ResolvedShift(
                type: types[override.shiftTypeCode],
                isOverride: true,
                memo: override.memo,
                baseType: base,
                beforeStart: beforeStart
            )
        }
        return ResolvedShift(type: base, isOverride: false, memo: "", baseType: base, beforeStart: beforeStart)
    }

    /// 근무 시작일 이전 구간의 기본 근무.
    /// 수습 때는 보통 교대가 아니라 평일 주간 근무를 하므로 평일은 주간, 주말은 휴무로 채운다.
    private static func preStartType(types: [Int: ShiftType], date: Day) -> ShiftType? {
        let all = types.values.sorted { $0.sortOrder < $1.sortOrder }
        if date.isWeekend {
            return all.first { $0.name.contains("휴무") || $0.shortLabel == "휴" }
                ?? all.first { !$0.isWorking }
        }
        return all.first { $0.name.contains("주간") || $0.shortLabel == "주" }
            ?? all.first { $0.isWorking }
    }

    /// 조 번호(1부터)로부터 사이클 오프셋을 구한다.
    static func offsetForTeam(cycleDays: Int, teamCount: Int, teamNumber: Int) -> Int {
        guard teamCount > 0, cycleDays > 0 else { return 0 }
        let step = cycleDays / teamCount
        return floorMod((teamNumber - 1) * step, cycleDays)
    }
}

struct ResolvedShift {
    var type: ShiftType?
    var isOverride: Bool
    var memo: String
    /// 수동 변경 전 패턴상의 기존 근무
    var baseType: ShiftType?
    /// 근무 시작일 이전이라 교대 패턴 대신 기본 주간 일정이 적용된 날
    var beforeStart: Bool

    /// 수동 변경으로 기존과 달라졌는가
    var changedFromBase: Bool {
        isOverride && baseType != nil && baseType?.code != type?.code
    }
}

// MARK: - 프리셋

/// 미리 정의된 교대 패턴.
/// 사이클 값은 근무 유형 코드다 (1 주간, 2 오후, 3 야간, 4 비번, 5 휴무).
struct PatternPreset: Identifiable, Hashable {
    var name: String
    var teamCount: Int
    var description: String
    var cycle: [Int]

    var id: String { name }
    var cycleDays: Int { cycle.count }
}

enum PatternPresets {
    static let all: [PatternPreset] = [
        PatternPreset(
            name: "4조 3교대",
            teamCount: 4,
            description: "주 주 · 오 오 · 야 야 · 비 휴 (8일 주기)",
            cycle: [1, 1, 2, 2, 3, 3, 4, 5]
        ),
        PatternPreset(
            name: "3조 2교대",
            teamCount: 3,
            description: "주 주 · 야 야 · 비 휴 (6일 주기)",
            cycle: [1, 1, 3, 3, 4, 5]
        ),
        PatternPreset(
            name: "4조 2교대",
            teamCount: 4,
            description: "주 주 · 야 야 · 비 비 · 휴 휴 (8일 주기)",
            cycle: [1, 1, 3, 3, 4, 4, 5, 5]
        ),
        PatternPreset(
            name: "2조 2교대",
            teamCount: 2,
            description: "주 야 · 비 휴 (4일 주기)",
            cycle: [1, 3, 4, 5]
        ),
        PatternPreset(
            name: "주간 전담",
            teamCount: 1,
            description: "평일 주간 근무, 주말 휴무 (7일 주기)",
            cycle: [1, 1, 1, 1, 1, 5, 5]
        ),
    ]
}
