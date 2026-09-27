import UIKit
import SwiftUI
import ComposeApp

/// Bridges the Kotlin `MainViewController()` (composeApp/src/iosMain) into SwiftUI.
/// Everything visible inside it is shared Compose code - this file is the whole
/// iOS-native UI layer.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Full bleed: without this SwiftUI insets the Compose view by the
            // safe area, leaving black bands behind the status bar and home
            // indicator. Compose applies its own insets (statusBarsPadding /
            // navigationBarsPadding), so it must own the whole window.
            .ignoresSafeArea(.all)
    }
}
