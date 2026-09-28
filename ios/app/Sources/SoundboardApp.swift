import SwiftUI
import SoundboardKit

// The iOS app (#266): one window showing the Kotlin board screen (MainViewController.kt).
@main
struct SoundboardApp: App {
    var body: some Scene {
        WindowGroup {
            // Compose lays out around the status bar and home indicator itself, so the board's
            // background can reach the edges.
            BoardView().ignoresSafeArea()
        }
    }
}

struct BoardView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
