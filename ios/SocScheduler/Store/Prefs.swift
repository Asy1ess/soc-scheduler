import Foundation
import SwiftUI

/// 화면 테마. `system` 이면 기기의 다크 모드 설정을 따라간다.
enum ThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: "기기 설정"
        case .light: "밝게"
        case .dark: "어둡게"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

/// 초기 설정 완료 여부처럼 단순한 값만 담는다.
///
/// 위젯도 읽을 수 있도록 App Group 의 UserDefaults 를 쓴다.
enum Prefs {

    private static let defaults = UserDefaults(suiteName: Store.appGroup) ?? .standard

    private enum Key {
        static let setupDone = "setup_done"
        static let theme = "theme_mode"
        static let briefEnabled = "brief_enabled"
        static let briefHour = "brief_hour"
        static let briefMinute = "brief_minute"
        static let briefOnRestDays = "brief_on_rest_days"
    }

    static var isSetupDone: Bool {
        get { defaults.bool(forKey: Key.setupDone) }
        set { defaults.set(newValue, forKey: Key.setupDone) }
    }

    static var themeMode: ThemeMode {
        get { ThemeMode(rawValue: defaults.string(forKey: Key.theme) ?? "") ?? .system }
        set { defaults.set(newValue.rawValue, forKey: Key.theme) }
    }

    static var isBriefEnabled: Bool {
        get { defaults.bool(forKey: Key.briefEnabled) }
        set { defaults.set(newValue, forKey: Key.briefEnabled) }
    }

    static var briefHour: Int {
        get { defaults.object(forKey: Key.briefHour) as? Int ?? 7 }
        set { defaults.set(newValue, forKey: Key.briefHour) }
    }

    static var briefMinute: Int {
        get { defaults.object(forKey: Key.briefMinute) as? Int ?? 30 }
        set { defaults.set(newValue, forKey: Key.briefMinute) }
    }

    /// 휴무·비번인 날에도 알릴지
    static var briefOnRestDays: Bool {
        get { defaults.object(forKey: Key.briefOnRestDays) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Key.briefOnRestDays) }
    }
}

/// 테마를 화면에 전달하는 통로. 고르는 즉시 다시 그려진다.
@Observable
final class ThemeStore {
    var mode: ThemeMode {
        didSet { Prefs.themeMode = mode }
    }

    init() {
        self.mode = Prefs.themeMode
    }
}
