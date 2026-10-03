import AppIntents
import ComposeApp

/// The iOS answer to Android's floating voice bubble.
///
/// Android puts a mic over every other app; iOS forbids that outright, so this
/// reaches the same place through Siri instead - "Hey Siri, add an expense to
/// FixMoney", answer the question, done, with the app closed.
///
/// `openAppWhenRun = false` is the whole point: the intent runs in the app's
/// process with no UI at all, which is why `VoiceQuickAddBridge` starts Koin
/// itself rather than relying on `MainViewController()` having run.
///
/// Siri performs the speech-to-text and hands over a finished sentence, so this
/// needs no microphone permission and no `SFSpeechRecognizer` - unlike the
/// Android service, which does its own recognition.
struct AddExpenseIntent: AppIntent {

    static var title: LocalizedStringResource = "Add an expense"

    static var description = IntentDescription(
        "Say what you spent and FixMoney files it under the right category."
    )

    static var openAppWhenRun: Bool = false

    @Parameter(
        title: "What did you spend?",
        requestValueDialog: "What did you spend?"
    )
    var phrase: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let spoken = phrase

        let outcome: VoiceAddOutcome = await withCheckedContinuation { continuation in
            VoiceQuickAddBridge.shared.addFromSpeech(spokenText: spoken) { result in
                continuation.resume(returning: result)
            }
        }

        return .result(dialog: IntentDialog(stringLiteral: outcome.message))
    }
}

/// Registers the phrases Siri listens for. `\(.applicationName)` is required in
/// every phrase - App Shortcuts must name the app, so "add an expense" on its own
/// is not something Siri will accept.
struct FixMoneyShortcuts: AppShortcutsProvider {

    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddExpenseIntent(),
            phrases: [
                "Add an expense to \(.applicationName)",
                "Add expense to \(.applicationName)",
                "Log an expense in \(.applicationName)",
                "Track a spend in \(.applicationName)"
            ],
            shortTitle: "Add an expense",
            systemImageName: "mic.fill"
        )
    }
}
