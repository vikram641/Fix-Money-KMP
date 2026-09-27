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
            .ignoresSafeArea(.keyboard)
    }
}
