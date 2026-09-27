# Fix-Money-KMP

FixMoney rebuilt as a Kotlin Multiplatform + Compose Multiplatform app, targeting **Android and iOS from one codebase**.

The existing Android app at `~/Downloads/Expense` (Fragments + XML, 23 Fragments / 46 layouts) is the **reference** for this rebuild, not its source — screens get rebuilt here in Compose, using the old app to decide what each screen looks like and does. That project stays untouched and keeps shipping to the Play Store on its own.

Plan this follows: `~/Downloads/Expense/docs/FixMoney_iOS_KMP_Compose_Migration_Plan.pdf`

## Structure

```
shared/        Business logic only - no UI. ViewModels, repositories,
               networking, and (later) Room. Compiles for Android + iOS.
composeApp/    All UI as Compose Multiplatform composables, plus each
               platform's entry point. Produces the iOS framework.
  commonMain/    shared screens + theme
  androidMain/   MainActivity, manifest, Android resources
  iosMain/       MainViewController
iosApp/        Xcode project - a SwiftUI shell that hosts the Compose UI.
```

## Build

JDK 17 is required (JDK 25 breaks the Gradle Kotlin DSL compiler):

```bash
export JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
```

| What | Command |
|---|---|
| Android APK | `./gradlew :composeApp:assembleDebug` |
| iOS framework | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` |
| iOS app | open `iosApp/iosApp.xcodeproj` in Xcode and run |

Xcode runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automatically as a build phase, so the Kotlin side rebuilds on every iOS build.

Open the **repo root** in Android Studio (not Xcode) for Kotlin work. Xcode is only for `iosApp/`.

## Versions

Kotlin 2.2.0 · Compose Multiplatform 1.9.0 · AGP 8.7.3 · Gradle 8.9 · Ktor 3.2.3 · compileSdk 36 / minSdk 24 · iOS deployment target 15.0

Kotlin 2.2.0's Kotlin/Native toolchain is verified working against **Xcode 26.5** on this machine, even though 2.2.0 officially lists Xcode 16.3.

## Status

Verified working on 2026-09-27:

- `:composeApp:assembleDebug` - Android APK builds
- `:composeApp:linkDebugFrameworkIosSimulatorArm64` - `ComposeApp.framework` links
- `xcodebuild -sdk iphonesimulator26.5` - **`FixMoney.app` builds end to end**, Gradle framework phase included

What's actually in it: a placeholder Compose screen shared by both platforms (shows which OS it's running on, proving the shared-UI path works), and one ported feature in `shared/` - `com.fixmoney.shared.ai.GroqChatAssistant`, the old app's Groq chat fallback rewritten from OkHttp + org.json to Ktor + kotlinx.serialization. It compiles for both targets but no UI calls it yet, so Kotlin/Native currently strips it from the iOS binary. That's expected until the chat screen exists.

### Not done yet

- **No iOS simulator runtime installed.** Xcode → Settings → Components → download an iOS runtime. Without it the app builds but can't be *launched* (`xcodebuild` reports "iOS 26.5 is not installed").
- No Apple Developer account, so no device testing or App Store work yet. `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` is deliberately empty — fill it in once there is one.
- No DI (Koin), no Room, no navigation, no real screens. Those come as each feature gets rebuilt.

## IDs

- Android `applicationId`: `com.vikram641.expensetracker` — same as the live Play Store listing, so this rebuild can eventually ship as an *update* rather than a separate app. Debug builds get a `.kmp` suffix so they install alongside the current app on a test device.
- iOS bundle id: `com.vikram641.fixmoney` (set in `Config.xcconfig`).
