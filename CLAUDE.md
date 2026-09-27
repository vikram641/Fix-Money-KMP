# CLAUDE.md — Fix-Money-KMP

Context for working on this repo. `README.md` covers structure and build commands; this file covers what isn't obvious from the code — decisions, gotchas, and what's actually verified vs. assumed.

## Driving the simulator

`xcrun simctl io <udid> tap` does not exist, and AppleScript UI control is blocked (*"osascript is not allowed assistive access"*) unless the terminal is granted Accessibility in System Settings. So **Claude cannot tap through iOS screens** — it can build, install, launch and screenshot, but any interaction has to be done by hand. Reading back app state works well as a substitute: `xcrun simctl get_app_container <udid> com.vikram641.fixmoney data` then `plutil -p <container>/Library/Preferences/prefs_onboarding.plist`.

## Keep this file updated

**After every change to this project, update this file in the same session.** Not a summary of what you did — record what a future session would otherwise have to rediscover: decisions and *why*, gotchas that cost a build cycle, what was verified vs. assumed, and what's deliberately left undone. Delete or correct entries that stop being true rather than stacking contradictions.

## What this project is

FixMoney rebuilt as Kotlin Multiplatform + Compose Multiplatform, targeting Android and iOS from one codebase. Started 2026-09-27.

**The old app is reference material, not source.** `~/Downloads/Expense` (the current Play Store FixMoney — Fragments + XML, 23 Fragments / 46 layouts, Hilt/Retrofit/Room-kapt) stays completely untouched and keeps shipping on its own. Screens get **rebuilt** here in Compose by looking at what the old app does, not ported file by file.

This was an explicit user decision. An in-place migration was tried first — a `:shared` module added inside the Expense repo — and was rejected and fully reverted: the production project should not carry half-migrated KMP scaffolding. **Never add KMP/Compose modules or dependencies to `~/Downloads/Expense`.**

Migration plan being followed: `~/Downloads/Expense/docs/FixMoney_iOS_KMP_Compose_Migration_Plan.pdf` (sections 5–7 are the reference tables for library swaps and phase order).

## Build

- **JDK 17 required**: `JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home`. JDK 25 breaks the Gradle Kotlin DSL compiler — same constraint as the old project.
- Open the **repo root in Android Studio** (2025.3 installed, KMP-capable) for all Kotlin work. Xcode only ever opens `iosApp/` — it cannot open a Gradle project.
- Kotlin 2.2.0 · Compose Multiplatform 1.9.0 · AGP 8.7.3 · Gradle 8.9 · Ktor 3.2.3 · compileSdk 36 / minSdk 24 · iOS deployment target 15.0.

## Shipping constraint — this replaces the live Play Store app

The rebuild must land as an **update to the existing listing**, not a new app. Three things make that work, all already wired:

- `applicationId = "com.vikram641.expensetracker"` — same as the live listing. **Release builds use it exactly**; only `debug` gets an `applicationIdSuffix = ".kmp"` so a dev build installs *alongside* the real app on a test device instead of replacing it.
- **Signing**: `keystore.properties` in the repo root (gitignored, copy from `~/Downloads/Expense/keystore.properties`) points at `/Users/apple/keystores/expense-release.jks`. Play rejects an update signed with any other key. **Verified 2026-09-27**: the release APK's cert SHA-256 `8E:62:87:9F:6C:48:8F:F8:C4:E9:FD:76:67:A0:D4:F0:FB:E8:A0:F4:F9:6F:BE:EB:47:33:7A:3C:88:F3:F7:17` matches that keystore exactly.
- `versionCode = 11` — Play has 10 (1.4.1) live, so uploads must be higher. Bump before every release.

Without `keystore.properties` present, release builds come out unsigned and the Gradle config silently skips signing (the `if (keystoreProperties.containsKey("storeFile"))` guard) — it won't error, so check the APK's signature, don't assume.

## Gotchas that already cost a build cycle

- **Root `build.gradle.kts` must declare every plugin with `apply false`** before a module can use its version-catalog alias. Without it: *"plugin is already on the classpath with an unknown version, so compatibility cannot be checked"*. Bit us when `:shared` was first added.
- **AGP 8.7.3's bundled lint predates Kotlin 2.2.0** and its `androidx.lifecycle` detector crashes **every release build** with `Found class KaCallableMemberCall, but interface was expected`. Worked around with `lint { disable += "NullSafeMutableLiveData" }` in `composeApp/build.gradle.kts`. Remove that line once AGP is bumped — don't leave it disabled silently.
- **Kotlin 2.2.0's Kotlin/Native toolchain works against Xcode 26.5**, despite 2.2.0 officially listing Xcode 16.3. No version bump needed. First iOS compile downloads ~1GB of LLVM/sysroot into `~/.konan` (~2 min); later builds are fast.
- **Unused shared code is stripped from the iOS binary.** `GroqChatAssistant` compiles for iOS but no UI calls it yet, so Kotlin/Native dead-code-eliminates it — `nm` on the built `.app` shows zero Groq symbols. That's correct behaviour, not a linking bug. It'll appear once a screen actually calls it.
- **`iosApp.xcodeproj` is hand-written**, not generated by the KMP wizard. It parses and builds. If it ever needs structural edits, prefer editing through Xcode over hand-patching the pbxproj.
- **`-destination 'id=<simulator udid>'` is unreliable on this setup.** `SUPPORTED_PLATFORMS = "iphoneos iphonesimulator"` is set on both target configs, and it worked once, but `xcodebuild -showdestinations` intermittently drops the simulator entry and every `-destination` build then fails with *"Unable to find a destination matching the provided destination specifier"* — with the simulator booted and visible to `simctl`. **Use `-sdk iphonesimulator -target iosApp` instead**, which has never failed. Note: `-sdk iphonesimulator17.5` is rejected (the installed *SDK* is 26.5 even though the *runtime* is 17.5) — pass the bare `iphonesimulator`.
- **That build produces a fat x86_64 + arm64 binary and takes ~10 minutes.** Pass `ARCHS=x86_64 ONLY_ACTIVE_ARCH=NO` to halve it when only the Intel simulator matters.
- **Library klibs must be built with Kotlin ≤ 2.2.0.** Koin **4.2.2 does not work** — its klib is compiled with Kotlin 2.3.20 (abi 2.3.0) and the 2.2.0 resolver fails with a completely misleading `KLIB resolver: Could not find "<exact path>"` for a file that is sitting right there. Pinned to **Koin 4.1.1** (compiler 2.1.21). Check `unzip -p <artifact>.klib default/manifest | grep compiler_version` before adopting any new KMP library.
- **Do not trust Gradle module metadata for target support — compile instead.** Reading `*.module` files suggested navigation-compose and even Compose Multiplatform lacked `iosX64`; both actually compile for it fine. The metadata describes alias artifacts, not what resolution really pulls in.
- **This is an Intel i7 Mac** (i7-1068NG7, macOS 26.6.2), so the simulator runs **x86_64** — Kotlin target `iosX64`, not `iosSimulatorArm64`. `lipo -info` on the built app binary should say `x86_64`. Keep `iosX64()` in both modules' target lists; dropping it breaks simulator runs on this machine.

## Status

**Verified working 2026-09-27** (commands actually run, not assumed):

- `:composeApp:assembleDebug` — Android APK builds
- `:composeApp:assembleRelease` — signed APK, signature matches the live app's key
- `:composeApp:linkDebugFrameworkIosSimulatorArm64` — `ComposeApp.framework` links
- `xcodebuild -target iosApp -sdk iphonesimulator26.5` — **`FixMoney.app` builds end to end**, with the Gradle `embedAndSignAppleFrameworkForXcode` phase running from inside Xcode. 44,950 Compose symbols confirmed in the binary.

**Ran on iOS 2026-09-27** — the shared Compose UI rendered and navigated correctly on a simulator. The whole chain is proven: Kotlin → Compose Multiplatform → `ComposeApp.framework` → SwiftUI shell → simulator.

Simulator in use: **`FixMoney-SE-iOS17`** — iPhone SE (3rd gen), iOS 17.5, chosen deliberately for this Intel Mac (smallest screen, and 17.5 is lighter than iOS 26 while still shipping a Universal/x86_64 runtime). Recreate with:

```bash
xcrun simctl create "FixMoney-SE-iOS17" \
  com.apple.CoreSimulator.SimDeviceType.iPhone-SE-3rd-generation \
  com.apple.CoreSimulator.SimRuntime.iOS-17-5
```

The iOS 17.5 runtime is a 7.34 GB download (`xcodebuild -downloadPlatform iOS -buildVersion 17.5`; note the `-architectureVariant universal` flag is *rejected* — the default already is universal).

**Foundation layer, verified on iOS 2026-09-27**: Koin (DI, replacing Hilt), Navigation Compose (replacing `nav_graph.xml`), and multiplatform-settings (replacing SharedPreferences) are all wired and working.

`OnboardingPrefs` (`shared/onboarding/`) deliberately keeps **the old app's exact preference keys and file name** (`prefs_onboarding`, `onboarding_complete`, `user_name`, `monthly_budget`, `currency_code`), so an existing Android install keeps its profile when this build replaces the old app. Backed by SharedPreferences on Android, `NSUserDefaults(suiteName = "prefs_onboarding")` on iOS.

Koin starts from `FixMoneyApp.onCreate()` on Android and from `MainViewController()` on iOS (no Application class there), guarded by a flag since SwiftUI can recreate the representable.

**Screens built**: `WelcomeScreen` and `SetupNameScreen` (ports of `fragment_onboarding_welcome.xml` / `fragment_onboarding_setup_name.xml`, copy kept identical), plus `HomePlaceholderScreen`. End-to-end flow confirmed on the simulator: typing a name → Continue → the name persisted to NSUserDefaults → relaunch started at Home (not Welcome) and greeted the saved name. That exercises Compose input → Koin → shared prefs → platform storage → the start-destination gate.

**One feature ported**: `shared/src/commonMain/.../ai/GroqChatAssistant.kt` — the old app's Groq chat fallback, rewritten from OkHttp + `org.json` to Ktor + kotlinx.serialization. Same prompt text, same model (`openai/gpt-oss-20b`), same 20s timeout, same 429→`QuotaExceeded` handling. Takes `apiKey` as a parameter (shared code has no `BuildConfig`). Groq's live response shape was re-verified against the new `@Serializable` DTOs by curl. **Never run at runtime on either platform** — compile- and contract-verified only.

`expect`/`actual` is proven working via `shared/.../platform/Platform.kt` (`httpClient()` → OkHttp/Darwin, `logWarn()` → `android.util.Log`/`NSLog`). Same mechanism camera, OCR, ads and background sync will need.

## Blockers and not-done

- **Android has never been run.** (iOS is verified; this is the gap.) The debug APK builds (`com.vikram641.expensetracker.kmp`, 11 MB) but no device has been connected yet, so the Android side is build-verified only — unlike iOS, nothing has been seen running.
- **No Apple Developer account** ($99/yr). `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` is deliberately empty. Needed before device testing or TestFlight.
- No Room, no Firebase, no ads, no charts. Onboarding is only 1 of 3 steps (name); budget and currency screens are not built, so `saveProfile` currently hardcodes `monthlyBudget = 0` and `currencyCode = "INR"`.
- The real dashboard does not exist — `HomePlaceholderScreen` just greets the saved name.

## Repo

Private GitHub repo `vikram641/Fix-Money-KMP`, branch `main`. Commits use the global git identity (`vikram8575229@gmail.com`) — **do not override the author with `-c user.email=...`**; an early commit did and had to be rewritten so GitHub would link it to the profile.
