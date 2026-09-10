import SwiftData
import SwiftUI
import UserNotifications

@main
struct SocSchedulerApp: App {

    @State private var theme = ThemeStore()
    @State private var showOnboarding = !Prefs.isSetupDone

    private let notificationDelegate = NotificationDelegate()

    init() {
        ShiftAlarms.registerCategories()
        UNUserNotificationCenter.current().delegate = notificationDelegate
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(theme)
                .preferredColorScheme(theme.mode.colorScheme)
                .fullScreenCover(isPresented: $showOnboarding) {
                    OnboardingView(isFirstRun: true) {
                        Prefs.isSetupDone = true
                        showOnboarding = false
                    }
                    .environment(theme)
                }
        }
        .modelContainer(Store.container)
    }
}
