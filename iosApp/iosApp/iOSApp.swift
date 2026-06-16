import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Starts Koin and registers the BGTaskScheduler launch handler (must run during launch).
        BootstrapKt.initApp()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                BootstrapKt.scheduleBackgroundSync()
            }
        }
    }
}
