# CLAUDE.md — Fix-Money-KMP

Context for working on this repo. `README.md` covers structure and build commands; this file covers what isn't obvious from the code — decisions, gotchas, and what's actually verified vs. assumed.

## Driving the simulator

`xcrun simctl io <udid> tap` does not exist, and AppleScript UI control is blocked (*"osascript is not allowed assistive access"*) unless the terminal is granted Accessibility in System Settings. So **Claude cannot tap through iOS screens** — it can build, install, launch and screenshot, but any interaction has to be done by hand. Reading back app state works well as a substitute: `xcrun simctl get_app_container <udid> com.vikram641.fixmoney data` then `plutil -p <container>/Library/Preferences/prefs_onboarding.plist`.

## Before every commit

```bash
export JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
./gradlew checkAll
```

`checkAll` (defined in the root `build.gradle.kts`) builds the Android APK and compiles both modules for `iosX64`. Android is what gets run day to day; this keeps the iOS check cheap enough (~20s warm) that it never gets skipped. Compiling for iOS every time and *running* it only at milestones is the intended workflow — an Android-only pass proves nothing about `expect`/`actual` code, library availability, or Kotlin/Native behaviour.

## Keep this file updated

**After every change to this project, update this file in the same session.** Not a summary of what you did — record what a future session would otherwise have to rediscover: decisions and *why*, gotchas that cost a build cycle, what was verified vs. assumed, and what's deliberately left undone. Delete or correct entries that stop being true rather than stacking contradictions.

## What this project is

FixMoney rebuilt as Kotlin Multiplatform + Compose Multiplatform, targeting Android and iOS from one codebase. Started 2026-09-27.

**The old app is reference material, not source.** `~/Downloads/Expense` (the current Play Store FixMoney — Fragments + XML, 23 Fragments / 46 layouts, Hilt/Retrofit/Room-kapt) stays completely untouched and keeps shipping on its own. Screens get **rebuilt** here in Compose by looking at what the old app does, not ported file by file.

This was an explicit user decision. An in-place migration was tried first — a `:shared` module added inside the Expense repo — and was rejected and fully reverted: the production project should not carry half-migrated KMP scaffolding. **Never add KMP/Compose modules or dependencies to `~/Downloads/Expense`.**

Migration plan being followed: `~/Downloads/Expense/docs/FixMoney_iOS_KMP_Compose_Migration_Plan.pdf` (sections 5–7 are the reference tables for library swaps and phase order).

## Assets (copied from the old project 2026-09-27)

The old app's assets were copied once; `~/Downloads/Expense` is **not** read at build time. To change an asset, change it here.

| What | Where it went | Count |
|---|---|---|
| PNG / WebP | `composeApp/src/commonMain/composeResources/drawable/` | 26 |
| Vector XML drawables | same | 24 |
| Lottie JSON | `composeApp/src/commonMain/composeResources/files/lottie/` | 6 |
| Launcher icons (mipmaps) | `composeApp/src/androidMain/res/` — Android-only | 6 folders |

**57 XML drawables were deliberately NOT copied.** They are `<shape>`, `<selector>` and `<layer-list>` backgrounds (`bg_*.xml`, ripples, chips), which compose-resources cannot read. Each becomes a Compose modifier at the point of use — `Modifier.background(color, RoundedCornerShape(...))`, `Modifier.border(...)` — so there is nothing to port ahead of time. Only `<vector>` XML travels.

Adaptive launcher icons reference `@drawable/ic_launcher_background`, which lives in `androidMain/res/drawable/`, **not** in composeResources — putting it only in composeResources fails the Android build with `AAPT: error: resource drawable/ic_launcher_background not found`.

`compose.resources { packageOfResClass = "com.fixmoney.resources" }` is set in `composeApp/build.gradle.kts`. Without it the generated class lands in `fix_money_kmp.composeapp.generated.resources`, derived from the root project name.

### Colours

`composeApp/.../ui/Theme.kt` is **generated** from the old `values/colors.xml` + `values-night/colors.xml` — 59 tokens, 20 with real dark variants. Material3's scheme only covers a handful of roles, so the full set lives in an `AppColors` holder reached via `AppTheme.colors.textSecondary`, with the Material scheme mapped onto the closest tokens so stock components still look right.

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
- **Two independent constraints pin library versions, and both bite regularly:**
  1. **klibs must be built with Kotlin ≤ 2.2.0** (our compiler). A newer one fails with a completely misleading `KLIB resolver: Could not find "<exact path>"` for a file that is sitting right there.
  2. **The library must publish an `iosX64` target** (Intel simulator). Missing it gives `No matching variant ... org.jetbrains.kotlin.native.target with value 'ios_x64'`.

  Already hit: **Koin pinned to 4.1.1** (4.2.2 → Kotlin 2.3.20) and **Compottie pinned to 2.0.2** (2.0.3+ → Kotlin 2.3.0; 2.1.0 is the last release with `iosX64` at all). Before adopting any KMP library, check both:

  ```bash
  curl -s "https://repo1.maven.org/maven2/<group-path>/<artifact>-iosx64/maven-metadata.xml"   # iosX64 published?
  unzip -p <artifact>.klib default/manifest | grep compiler_version                            # built with which Kotlin?
  ```

  **This is a shrinking window.** The ecosystem is steadily dropping Intel-simulator support, so expect more libraries to be unusable. The real escape hatch is to stop testing iOS on this Intel Mac's simulator and test on a **physical iPhone** (`iosArm64`) instead — a free Apple ID grants 7-day development provisioning, no paid account needed. Worth doing before the library ceiling blocks something that actually matters.
- **Compose Multiplatform refuses to start without `CADisableMinimumFrameDurationOnPhone` in `Info.plist`.** It throws `IllegalStateException` at launch, which reads as an instant crash back to the home screen.
- **Vector drawables must use literal hex colours.** compose-resources' vector parser cannot resolve `@color/...` or `@android:color/...` references and throws `IllegalArgumentException: Invalid color value @android:color/transparent` at *render* time — the app launches to a black screen with no build error. All 19 affected drawables were converted on import; convert any new one the same way.
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

## Feature 1 — Onboarding (complete 2026-09-27)

All 8 screens of the old onboarding flow, ported from their XML layouts and cross-checked against screenshots of the running old app (`~/Downloads/ss_for_fixmoney/`):

`WelcomeScreen` → `FeatureScreen` ×3 → `SetupNameScreen` → `SetupBudgetScreen` → `SetupCurrencyScreen` → `AllSetScreen` → Home.

- **`FeatureScreen` is one composable for all three pages**, with content in a `FeaturePage` enum — the direct equivalent of the old app reusing one Fragment with nav-graph `<argument>` defaults. Copy, button labels and the per-page animation flag are carried over verbatim.
- `OnboardingState` replaces the activity-scoped `OnboardingViewModel`. Defaults match: ₹25,000, INR. Only `AllSetScreen` commits the profile, exactly as `AllSetFragment` was the sole caller of `completeOnboarding()`.
- Poppins is now applied app-wide through `poppinsTypography()`, matching the old theme's `android:fontFamily`.
- `IconBadge`, `StepHeader` and `PrimaryButton` (`ui/components/Common.kt`) rebuild the old `bg_icon_badge_gradient.xml`, back-circle + progress row, and pill `MaterialButton` as composables.

### Deliberate differences from the old app

- **`SetupBudgetScreen` scrolls.** The old `ConstraintLayout` had no ScrollView — it only ever fitted because the reference device is 20:9. On a 16:9 screen the category row and Continue button fell off the bottom. The body now scrolls with the button pinned; on a tall device it looks identical.
- **Category chips come from Firestore**, like the old app — but over the **REST API via Ktor**, not an SDK. See "Categories" below.
- **`OnboardingState.complete()` does not write `BudgetEntity` rows.** The old `completeOnboarding()` also created per-category budget rows for the current month so the number reached the Budget screen and Home's card. That half needs Room.
- Category icon names are mapped to drawables explicitly in `SetupBudgetScreen.categoryIcon()`. The old app used `resources.getIdentifier(...)`, which has no compose-resources equivalent — accessors are generated, so the mapping must be static.

### Categories (Firestore over REST)

`shared/data/CategoryRepository.kt` fetches the public `categories` collection straight from Firestore's REST API with the Ktor client already in the project:

```
GET https://firestore.googleapis.com/v1/projects/expenseapp-7b500/databases/(default)/documents/categories?key=<web api key>
```

**Why REST and not a Firebase SDK**: the official SDK is Android/iOS-native, and the community KMP wrapper (GitLive) would have to clear the same two hurdles every other library here does — `iosX64` and a Kotlin ≤ 2.2.0 klib. REST needs neither, works identically on both platforms, and is the pattern the migration plan already recommends for Gemini in section 6.2. Firestore's REST shape wraps each field in its type (`{"name": {"stringValue": "Food"}}`), which is what the `Firestore*` DTOs in that file decode.

The collection is world-readable by design (`allow read: if true`; everything else in the project stays locked), so the only credential is the Firebase **Web API key** in `shared/data/FirebaseConfig.kt`. That key is not a secret — it ships inside every client binary, and access is controlled by security rules rather than by hiding it.

Retries 3× with a 1.5s delay, matching the old `OnboardingViewModel.loadCategoriesWithRetry()` — a single cold call raced app startup and left the chip picker permanently empty for that session. Unlike the old app, total failure falls back to `DefaultCategories` (an identical copy of the 7 seeded documents) so an offline first run still shows chips. Results are cached in memory; Room will replace that.

### Verified

Categories confirmed arriving from Firestore on iOS, not the fallback — the simulator's network log shows the real request to `firestore.googleapis.com`. Both targets build (`checkAll`), and Welcome / Feature / SetupName / SetupBudget / AllSet were each rendered on the iPhone SE iOS 17.5 simulator in dark mode and compared against the old app's screenshots. Interaction (tapping through) has not been machine-verified — see "Driving the simulator".

**One feature ported**: `shared/src/commonMain/.../ai/GroqChatAssistant.kt` — the old app's Groq chat fallback, rewritten from OkHttp + `org.json` to Ktor + kotlinx.serialization. Same prompt text, same model (`openai/gpt-oss-20b`), same 20s timeout, same 429→`QuotaExceeded` handling. Takes `apiKey` as a parameter (shared code has no `BuildConfig`). Groq's live response shape was re-verified against the new `@Serializable` DTOs by curl. **Never run at runtime on either platform** — compile- and contract-verified only.

`expect`/`actual` is proven working via `shared/.../platform/Platform.kt` (`httpClient()` → OkHttp/Darwin, `logWarn()` → `android.util.Log`/`NSLog`). Same mechanism camera, OCR, ads and background sync will need.

## Feature 2 — Home (in progress)

`ui/home/DashboardScreen.kt` is the five-tab shell from the old `bottom_nav_menu.xml`; only the Home tab is built, the other four say "Not migrated yet".

`ui/home/HomeScreen.kt` ports `fragment_home.xml` in its **empty state**: greeting + month + avatar initials, the budget card (spent / progress / "n% to X" / This Week / Remaining), the "No expenses yet" card, and the FAB. Greeting buckets and the initials logic match `HomeViewModel` exactly.

There is no expense data source yet (Room), so spend is 0 and the empty state always shows — which is what a fresh install saw in the old app too. **Not yet ported**: the AI insight card, pie chart + legend, the "Recent" list, offline banner, pull-to-refresh, month picker, AdMob banner.

Date handling is `expect`/`actual` (`shared/platform/DateTime.kt`) rather than kotlinx-datetime — only the current hour and `yyyy-MM` are needed, and every third-party KMP library so far has had to clear an `iosX64` + Kotlin-version gate for no benefit here.

**Window insets: use the specific one, never `safeDrawingPadding()` on a partial-screen element.** `safeDrawingPadding()` applies *every* inset, so putting it on the bottom nav bar added the **status bar's** height as padding above the icons - a large empty gap on Android. The nav bar takes `navigationBarsPadding()`; the scrolling content above it takes `statusBarsPadding()`. The FAB, sitting inside the content area, needs neither.

**`bottomNavBg` is the wrong token for the nav bar** — it is `#FFFFFF` in `colors.xml` with no `values-night` override, so it stayed white in dark mode. Use `bgCard`. Other tokens without a night variant will have the same trap.

**The Android manifest needs `android:icon` / `android:roundIcon` explicitly** - without them the app shows the default green robot in the launcher even though the mipmaps are present in `androidMain/res`.

## iCloud breaks builds

`~/Desktop` is synced by iCloud Drive, and iCloud periodically duplicates files inside `build/` as `<name> 2.<ext>`. Gradle then fails with D8 `Type ... is defined multiple times` (seen for `ModulesKt 2.class` and `AppKt 2.dex`). It recurs; it is not a one-off.

Workaround when it happens:

```bash
find . -name "* 2.*" -delete
rm -rf composeApp/build/intermediates shared/build/intermediates
```

The real fix is to keep build output out of iCloud - either move the project off the Desktop, or point Gradle's build directory elsewhere. Not decided yet.

## Testing on Android

**The Android emulator is not usable on this machine.** `Pixel_7_Pro_API_35` boots but quits within minutes (it logs a clean `Saving snapshot 'default_boot'` and exits), repeatedly, across hardware and software GPU. With `-gpu swiftshader_indirect` it survives longer but is too slow to accept input — taps are dropped and SystemUI ANRs.

While it was briefly alive, real taps confirmed: forward navigation works, and **the in-app back circle on the setup screens works** (SetupName → Alerts). One observation suggested the *system* back button moved forward rather than back, but that could not be reproduced before the emulator died, and dropped input makes it unreliable evidence. Treat it as unconfirmed.

Until this is sorted, Android verification means installing the APK on the real device.

## Blockers and not-done

- **Android has never been run.** (iOS is verified; this is the gap.) The debug APK builds (`com.vikram641.expensetracker.kmp`, 11 MB) but no device has been connected yet, so the Android side is build-verified only — unlike iOS, nothing has been seen running.
- **No Apple Developer account** ($99/yr). `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` is deliberately empty. Needed before device testing or TestFlight.
- No Room, no Firebase, no ads, no charts. Onboarding is only 1 of 3 steps (name); budget and currency screens are not built, so `saveProfile` currently hardcodes `monthlyBudget = 0` and `currencyCode = "INR"`.
- The real dashboard does not exist — `HomePlaceholderScreen` just greets the saved name.

## Repo

Private GitHub repo `vikram641/Fix-Money-KMP`, branch `main`. Commits use the global git identity (`vikram8575229@gmail.com`) — **do not override the author with `-c user.email=...`**; an early commit did and had to be rewritten so GitHub would link it to the profile.
