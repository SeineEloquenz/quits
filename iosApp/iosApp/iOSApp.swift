import SwiftUI
import Foundation
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Starts Koin and registers the BGTaskScheduler launch handler (must run during launch).
        BootstrapKt.startApp()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                // Universal Link tapped from another app; the invite secret is in the fragment.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        BootstrapKt.handleDeepLink(url: url.absoluteString)
                    }
                }
                .onOpenURL { url in
                    BootstrapKt.handleDeepLink(url: url.absoluteString)
                }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                BootstrapKt.scheduleBackgroundSync()
            }
        }
    }
}
