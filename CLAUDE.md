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
| Lottie JSON | `composeApp/src/commonMain/composeResources/files/lottie/` | 7 |
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

**Play App Signing is on.** Builds users get from Play are re-signed by Google (seen 2026-10-07 on the OnePlus: the Play-installed v12 carried certificate `d9169e77...` / v3.2 `f6729a82...`, not the upload key `8E:62:...`). So a locally built release APK **cannot be installed over a Play install** - `adb install -r` would fail on the signature - and the only way is to uninstall first, which wipes that install's local data (release builds are not debuggable, and `adb backup` is off for targetSdk 31+, so there is no backup). The user chose that on 2026-10-07 and uninstalled both apps themselves. Also learned: **versionCode 12 is already on Play** (installed from Play on 2026-10-06), so the next upload must be 13+.

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

## System bars follow the in-app switch, not the system (Android)

`MainActivity` calls `enableEdgeToEdge()` with no arguments for the first frame,
and `SystemBarsAppearance(darkTheme)` (expect/actual, `ui/SystemBars.kt`) calls it
again from the composition whenever the theme flips.

The bare call was the bug. Its default `SystemBarStyle.auto` decides light-vs-dark
icons from the **system's** `uiMode`, and the two disagree constantly: Settings'
Dark Theme switch can put the app in dark mode on a phone that is in light mode.
When that happened the status bar drew **dark icons on the dark navy background**
- the clock and battery were invisible - and the navigation bar got a **white
contrast scrim** under the dark bottom nav.

`SystemBarStyle.auto(lightScrim, darkScrim, detectDarkMode)` takes the dark-mode
test as its third argument, so passing `{ darkTheme }` drives it from the app's own
flag. Re-calling `enableEdgeToEdge` is deliberate rather than poking
`WindowInsetsController.isAppearanceLight*Bars` directly: only the former also
clears `isNavigationBarContrastEnforced`, which is what painted that bar white.

Both scrims are `TRANSPARENT` because the app already draws behind both bars - the
bottom nav's `bgCard` background sits *before* `navigationBarsPadding()` in its
modifier chain, so it extends under the navigation bar, and each screen's
background runs up behind the status bar.

The iOS `actual` is an empty no-op: Compose Multiplatform drives the status bar
style from the composition's own colours there, and there is no navigation bar.

**Verified on the device 2026-10-02** in both directions - dark theme gives white
status-bar icons and a dark navigation bar, light theme gives dark icons and a
light one.

## iOS must run full-bleed

`iosApp/iosApp/ContentView.swift` uses `.ignoresSafeArea(.all)`. With the template's `.ignoresSafeArea(.keyboard)` instead, SwiftUI insets the Compose view by the safe area and the app renders with black bands behind the status bar and home indicator. Compose applies its own insets, so it has to own the whole window.

## Feature 2 — Home (in progress)

`ui/home/DashboardScreen.kt` is the five-tab shell from the old `bottom_nav_menu.xml`; only the Home tab is built, the other four say "Not migrated yet".

`ui/home/HomeScreen.kt` ports `fragment_home.xml` in its **empty state**: greeting + month + avatar initials, the budget card (spent / progress / "n% to X" / This Week / Remaining), the "No expenses yet" card, and the FAB. Greeting buckets and the initials logic match `HomeViewModel` exactly.

There is no expense data source yet (Room), so spend is 0 and the empty state always shows — which is what a fresh install saw in the old app too. **Not yet ported**: the AI insight card, pie chart + legend, the "Recent" list, offline banner, pull-to-refresh, month picker, AdMob banner.

Date handling is `expect`/`actual` (`shared/platform/DateTime.kt`) rather than kotlinx-datetime — only the current hour and `yyyy-MM` are needed, and every third-party KMP library so far has had to clear an `iosX64` + Kotlin-version gate for no benefit here.

**Window insets: use the specific one, never `safeDrawingPadding()` on a partial-screen element.** `safeDrawingPadding()` applies *every* inset, so putting it on the bottom nav bar added the **status bar's** height as padding above the icons - a large empty gap on Android. The nav bar takes `navigationBarsPadding()`; the scrolling content above it takes `statusBarsPadding()`. The FAB, sitting inside the content area, needs neither.

The bottom bar has a rounded pill behind the active tab, matching the old app. **`bottomNavBg` is the wrong token for it** — it is `#FFFFFF` in `colors.xml` with no `values-night` override, so it stayed white in dark mode. It takes `bgPrimary` in dark mode and `bgCard` in light; see "UI fixes pass" below. Other tokens without a night variant will have the same trap.

**The Android manifest needs `android:icon` / `android:roundIcon` explicitly** - without them the app shows the default green robot in the launcher even though the mipmaps are present in `androidMain/res`.

## Location — do not move this back to the Desktop

The project lives at **`~/Developer/Fix-Money-KMP`**. It started on `~/Desktop`, which is synced by iCloud Drive, and that broke builds repeatedly: iCloud duplicates files inside `build/` as `<name> 2.<ext>`, and Gradle then fails with D8 `Type ... is defined multiple times` (hit for `ModulesKt 2.class` and `AppKt 2.dex`).

Moving it out was not straightforward - a plain `mv` of the 1.1 GB tree hung for over ten minutes with zero bytes written, blocked by iCloud's file provider. What worked: delete the `build/` directories first (1.1 GB to 8 MB), then `rsync -a --remove-source-files`.

Only `~/Desktop` and `~/Documents` are synced, so `~/Developer` is safe.

## Testing on Android

**The Android emulator is not usable on this machine.** `Pixel_7_Pro_API_35` boots but quits within minutes (it logs a clean `Saving snapshot 'default_boot'` and exits), repeatedly, across hardware and software GPU. With `-gpu swiftshader_indirect` it survives longer but is too slow to accept input — taps are dropped and SystemUI ANRs.

While it was briefly alive, real taps confirmed: forward navigation works, and **the in-app back circle on the setup screens works** (SetupName → Alerts). One observation suggested the *system* back button moved forward rather than back, but that could not be reproduced before the emulator died, and dropped input makes it unreliable evidence. Treat it as unconfirmed.

Until this is sorted, Android verification means installing the APK on the real device.

### Verified on a real Android device 2026-10-02

**OnePlus Nord (AC2001), Android 12, `adb` over USB.** The debug APK
(`com.vikram641.expensetracker.kmp`, 26 MB) installed and ran. Home, Expenses,
Analytics, Budget and Settings were each opened and screenshotted; data came from
Room (an existing install's rows, ₹10 in October, ₹2400 on 28 Sep), the donut,
the six-month bar chart and the budget roll-up all rendered, and the bottom nav
switched tabs. **No crash and no exception in logcat** — the only app-tagged
warnings are SLF4J's "no providers" notice (Ktor's logger, harmless) and an OEM
`FeatureFlagsImplExport` `NoClassDefFoundError` from the platform, not from us.

So both platforms have now been seen running. Notes from that session:

- **The device must be awake before anything is screenshotted.** A locked/dozing
  phone screencaps as a pure black PNG with the app still `mFocusedApp` — which
  reads exactly like the vector-drawable black-screen bug. Check
  `dumpsys power | grep mWakefulness` before believing a black screenshot, and
  `input keyevent KEYCODE_WAKEUP` first.
- **The floating voice bubble overlaps content at its default position.** It parks
  on the left edge vertically centred in the upper third, where it covers the start
  of the Home budget card's "TOTAL SPENT THIS MONTH" label and the Budget screen's
  "Overall Budget". It is draggable, so this is a default-position choice, not a
  layout bug — but the old app's bubble did not land on top of a label either.
- `This Week ₹0` on Home is visible on the device too, as expected — still the
  `platform/DateTime.kt` gap below.

## Feature 3 — Add Expense + Room (2026-09-27)

**Room is in**, shared across both platforms. Two things had to line up, as always:

- **AndroidX artifacts live on Google Maven, not Maven Central.** Checking `repo1.maven.org` said Room had no `iosX64` at all, which was wrong - `dl.google.com/dl/android/maven2` has it up to 2.8.5. Verify androidx availability there.
- **Room 2.7.2 / sqlite-bundled 2.5.2** are built with Kotlin 2.1.10, so they clear the ≤ 2.2.0 klib gate (Room 2.8.x is 2.2.0 and would also work). KSP is pinned to `2.2.0-2.0.2` to match the Kotlin version.

`shared/db/` holds `FixMoneyDatabase`, `ExpenseEntity` and `ExpenseDao`. The entity keeps the old app's **exact column names and types** so an existing install's `expenses` table is readable - this ships as an update to the same listing. Only `expenses` so far; budgets, categories and the two pending-sync queues come with their screens.

The database file is the only platform-specific part (`DatabaseBuilderFactory`, expect/actual): app database dir on Android, Documents on iOS. Room's KSP processor has to be added for **every** target, not just Android - see the `kspAndroid`/`kspIosX64`/… loop in `shared/build.gradle.kts`.

`ExpenseRepository` is local-only; the old app also queued a server sync row, but there is no backend in this build (the old one had been running fully offline too). Ids are `local-<millis>` - there is no UUID in common Kotlin, and the server assigned real ids before.

`ui/components/MonthYearDialog.kt` is one composable serving both of the old app's pickers - `MonthYearPickerDialog` on Home (`showDays = false`) and `SelectDateDialog` on Add Expense (`showDays = true`). Home's month is selectable, and switching it re-queries Room for that month.

**`BasicTextField` fills the available width by default**, which left-aligned the whole amount row instead of centring it. Onboarding's budget field still uses `Modifier.width(IntrinsicSize.Min)` for that; **Add Expense's does not any more** - `IntrinsicSize.Min` clipped long amounts, so it measures instead. See "UI fixes pass" below.

`AddExpenseScreen` ports `fragment_add_expense.xml`. Amount is whole numbers only, matching the old `amount.toInt()` validation. **Scan Receipt and Attach Photo are inert** - both need camera/photo access, the biggest expect/actual chunk left in the plan.

`ui/home/SpendingDonut.kt` replaces the old Home's MPAndroidChart pie + legend, drawn with `Canvas` arcs rather than a chart library - Vico (the plan's suggestion) is another dependency to clear the iosX64 + Kotlin-version gate for, and a donut is a handful of arcs. Slice colours are the category colours from Firestore. Integer percentages are nudged so they sum to 100; otherwise 87 + 12 leaves a visible gap in the ring.

The "Ask AI" button is `ai.json` played through Compottie, as the old Home did with a `LottieAnimationView`.

Home now reads spend from Room reactively, so a new expense appears without a refresh. `weekSpent` is still 0: the week window needs real date arithmetic, which the minimal `platform/DateTime.kt` does not do. That is where kotlinx-datetime would finally earn its place.

## Feature 4 — the remaining tabs (2026-09-27)

All four bottom-nav tabs and the AI chat are now real screens, ported from their layouts and the screenshots in `~/Downloads/ss_for_fixmoney/`.

- **Expenses** (`ui/expense/ExpensesScreen.kt`) - category filter chips built from whatever categories the data actually contains, search over note and category, rows grouped by date with a per-day total.
- **Budget** (`ui/budget/BudgetScreen.kt`) - month stepper, overall roll-up, per-category progress with delete. A `budgets` table was added to Room (same columns as the old `BudgetEntity`). **`spentAmount` is computed from the expenses table rather than stored**, unlike the old schema: keeping a stored copy in sync needs a write on every expense change and can drift. "+ Set Budget" splits the profile figure across categories, as onboarding's budget step did; the old per-category editor screen is not migrated.
- **Settings** (`ui/settings/SettingsScreen.kt`) - profile row and the toggles. `AppPrefs` keeps them in the old app's separate `"settings"` store. Dark Theme takes effect immediately (the preference is lifted into state in `App.kt`). **Export Data and SIP Calculator are shown but inert** - separate screens, not migrated. **Floating Voice Quick-Add is wired on Android** and hidden on iOS - see "Feature 5" below.
- **Fixoo chat** (`ui/chat/ChatScreen.kt`) - the shared `GroqChatAssistant` ported on day one finally has a UI. `ChatEngine` builds the DATA summary from Room that the old `ChatViewModel` assembled. **Only the Groq tier is migrated**: the old app tried Gemini first through the Firebase AI SDK, which is Android-only. Plan section 6.2 has the REST route when it is wanted.
- **Analytics** (`ui/analytics/AnalyticsScreen.kt`) - six months of spend as bars plus the month's categories ranked, both drawn as plain Boxes for the same reason as Home's donut. **The month label is the picker**, as `tvMonth` was in `AnalyticsFragment`: it opens the shared `MonthYearDialog` and everything below re-reads for the month chosen. The bar chart is the six months *ending* at that month, so picking an older one scrolls the whole window back rather than only re-filtering the categories. The old label gave no hint it was tappable, so a small chevron was added - the one deliberate difference.
- **Set Budget dialog**, **Export Data dialog** and the **SIP Calculator** screen are ported too. Export itself is **not** wired: writing a CSV and handing it to a share sheet is platform work (SAF on Android, `UIActivityViewController` on iOS) with no shared implementation.

## Feature 5 - Voice quick-add (2026-09-28)

The old app's floating voice bubble, ported. Tap a draggable bubble that sits over
every other app, say "150 on coffee", and the expense is in Room before you've put
the phone down - no screen in between.

The feature is split so the Android-only part is as small as possible:

| Piece | Where | Platform |
|---|---|---|
| Groq parse of one spoken sentence | `shared/ai/VoiceExpenseParser.kt` | both |
| parse -> match category -> save | `shared/voice/VoiceQuickAddEngine.kt` | both |
| overlay bubble + `SpeechRecognizer` | `composeApp/androidMain/voice/FloatingBubbleService.kt` | Android |
| the Settings toggle's permission flow | `ui/settings/VoiceQuickAdd.android.kt` | Android |

**iOS gets no bubble and never will** - an iOS app cannot draw over another app,
and there is no entitlement that changes that. `VoiceQuickAddHandle.supported` is
false there and the Settings row is **hidden rather than shown inert**, which is a
different call from Export Data and SIP (shown, not wired) because those will
eventually work on iOS and this will not. iOS reaches the same place through Siri
instead - see "Feature 5b" below.

**Only the Groq tier is migrated**, same as the chat assistant: the old app tried
`GeminiVoiceExpenseParser` first, through the Android-only Firebase AI SDK. The
prompt, model and JSON contract are carried over verbatim from
`GroqVoiceExpenseParser`.

### Differences from the old service, and why

- **The bubble is built in code**, not inflated from `overlay_voice_bubble.xml` -
  composeApp has no Android layout resources. Same reason its listening state is a
  `ValueAnimator` pulse rather than Lottie: Compottie is a *Compose* renderer and
  cannot draw into a plain `ImageView`.
- **The bubble's two colours are hardcoded ints** in the service (`COLOR_IDLE`,
  `COLOR_LISTENING` = `accent` / `expense_red`). It lives in a window the Compose
  theme never reaches, so `AppTheme.colors` is unavailable - change them alongside
  `Theme.kt`.
- **`ic_mic.xml` is duplicated** into `androidMain/res/drawable/`. compose-resources
  drawables are generated accessors and unreachable from View code - the same trap
  `ic_launcher_background` hit.
- **Hilt -> Koin**: the service is a `KoinComponent` with `by inject()`. A Service
  is not a Compose scope, so `koinInject()` does not apply there.
- **An unmatched category now falls back to the real "Other" row.** The old app
  saved an empty `categoryId`, which silently kept those expenses out of the donut,
  Analytics and Budget roll-ups.
- **The currency comes from the saved profile**, not a hardcoded `"INR"`.
- **`isBusy` guards the parse/save window.** The old service only guarded
  `isListening`, so a second tap while the Groq round trip was still running
  started a fresh recognition on top of it.
- **Returning from the overlay-permission screen continues the flow.** The old app
  made you come back and flip the switch a second time. The intent is launched for
  a result here, so granting it goes straight on to the microphone prompt. The
  result code is useless (always `RESULT_CANCELED`), so the permission is re-read.
- **The toggle reflects permissions actually held, not just the stored preference.**
  Revoking "Display over other apps" used to leave the switch showing ON with no
  bubble anywhere; `refresh()` now clears the preference in that case.

`FixMoneyApp.onCreate()` restarts the service when the preference says on and both
permissions are still granted - the toggle's listener is the only other thing that
ever starts it, so without this a force-stop or an OS kill left it gone for good.
The old `ExpenseApp` did the same thing for the same reason.

Manifest additions: `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, the `<queries>` entry that
makes the on-device recognizer visible under Android 11+ package visibility, and
the `specialUse` service declaration. `androidx.core:core-ktx` was added to
`androidMain` for `NotificationCompat` rather than left to arrive transitively.

**Verified on iOS 2026-09-28**: the app builds, launches and renders Settings on
the iPhone SE simulator with the voice row correctly absent and no crash - which is
what the `expect`/`actual` could have broken there.

**The Android half has never been run.** Both targets compile and the debug APK
carries the right permissions and the `specialUse` service (checked with `aapt2
dump`), but the bubble has never been tapped: no Android device has been connected
to this project yet (see "Testing on Android"). The Groq call reuses a parser shape
already verified live for the chat assistant, but this prompt's response has not
been seen.

**To see a screen you cannot tap to**, temporarily change `DashboardScreen`'s
initial `tab` and rebuild - tapping the simulator is impossible (see "Driving the
simulator"), and `osascript` is still refused assistive access as of 2026-09-28.
Reverting needs a third build, so batch whatever you want to look at into one pass.

### Known preference-store mismatch (pre-existing, not introduced here)

`AppPrefs` resolves the *same* `Settings` instance as `OnboardingPrefs`, i.e. the
`prefs_onboarding` file - despite its own comment claiming the old app's separate
`"settings"` store. The old app kept `dark_mode` in `settings` and the voice flag
in `app_prefs` under `voice_bubble_enabled`. So on an update, an existing install's
theme, alerts and voice-bubble states all read as unset and fall back to defaults.
Only the onboarding profile carries over. Fixing it means binding named `Settings`
instances in `platformModule` and pointing `AppPrefs` at them.

### Bubble behaviour, revised 2026-10-02

Three changes, all in `FloatingBubbleService`, after seeing the bubble on a real
device sitting on top of the Home card's heading:

- **It goes dormant.** Four seconds untouched and it fades to 40% and animates
  45% of its width past whichever edge it is resting on. A tap brings it back,
  and **that tap only wakes it** - it does not also open the microphone, or any
  accidental brush would start a recording. Dormancy is suspended while
  listening or saving: a half-hidden bubble mid-recording reads as a crash.
- **Vertical position is clamped, inside the system bars.** The window carries
  `FLAG_LAYOUT_NO_LIMITS` so it *can* tuck horizontally past the edge - but that
  same flag let a drag throw it off the top or bottom of the screen, where it
  could not be retrieved at all. `clampY` keeps it between 12dp margins **and
  subtracts the status / navigation bar insets**: `screenHeightPx` reports the
  whole display (`maximumWindowMetrics` includes the bars by design), so
  clamping against it alone still let the bubble settle under the navigation
  bar, half hidden with the nav buttons swallowing its touches. The insets are
  read with `getInsetsIgnoringVisibility` - a temporarily hidden bar must not
  widen the range, or the bubble is stranded when it returns. The initial
  position is a third of the way down rather than a bare `y = 300`, which on a
  tall screen parked it over whatever card was at the top.
- **Long press shows "Hide bubble".** 450ms, then a chip appears beside it,
  auto-hiding after 3s; tapping the chip stops the service **and clears the
  stored preference**, so Settings doesn't go on claiming the bubble is on with
  nothing on screen - the same trap `AndroidVoiceQuickAdd.refresh()` exists to
  close. Moving past the tap slop cancels the pending long press, so dragging
  never pops the chip up mid-gesture.

The chip is a **second overlay window**, not a badge inside the bubble's own:
the bubble window is sized exactly to the circle, so anything drawn beside it
would be clipped. It is positioned on the inward side so it can't land off the
edge, and it must be added to the window manager *before* anything calls
`updateViewLayout` on it.

## Feature 5b - Siri App Intent, the iOS answer to the bubble (2026-09-28)

What the bubble is *for* - speak from anywhere, expense filed, app never opened - is
achievable on iOS; only the floating window isn't. `AddExpenseIntent`
(`iosApp/iosApp/AddExpenseIntent.swift`) does it through Siri: *"Hey Siri, add an
expense to FixMoney"* -> Siri asks "What did you spend?" -> the answer is parsed and
saved, and Siri speaks the result back.

- **Siri does the speech-to-text.** The intent declares a `String` parameter and
  receives a finished sentence, so there is no `SFSpeechRecognizer`, no
  `AVAudioEngine` and **no microphone permission** on iOS - unlike the Android
  service, which runs its own recognition.
- **`openAppWhenRun = false`** is the point: the intent runs in the app's process
  with no UI. That is why `VoiceQuickAddBridge` calls `ensureKoinStarted()` itself
  (`IosKoin.kt`) instead of relying on `MainViewController()` having run - a Siri
  invocation can be the first thing that ever touches Koin in that launch.
- **The bridge is callback-based, not `suspend`.** An exported suspend function is
  awkward to call from a `perform()` that is already `async`, and the callback keeps
  the threading explicit; Swift wraps it in `withCheckedContinuation`. It dispatches
  to `Dispatchers.Main` so `ensureKoinStarted()` only ever runs on one thread.
- **The bridge must live in `composeApp`, not `shared`.** composeApp depends on
  shared with `implementation`, so shared's declarations are not exported into the
  `ComposeApp` framework header - only composeApp's own are visible to Swift.
- **Deployment target went 15.0 -> 16.4.** App Intents need 16.0, and the
  `AppShortcut(intent:phrases:shortTitle:systemImageName:)` initialiser needs 16.4.
  Costs nothing today: the iOS build has never shipped, so it has no users to drop.
- Every App Shortcut phrase **must** contain `\(.applicationName)` - Siri rejects a
  phrase that doesn't name the app, so plain "add an expense" will not work.
- `iosApp.xcodeproj` was hand-patched to add the Swift file (a `PBXBuildFile`, a
  `PBXFileReference`, a group entry and a `Sources` entry). `plutil -lint` on the
  pbxproj afterwards catches a mangled edit before xcodebuild does.

### Verified end to end on iOS 2026-09-28

Not just compiled. A temporary probe called `VoiceQuickAddBridge.addFromSpeech("spent
150 on coffee")` on launch, and the row landed in Room as `150 / INR / Coffee /
Food`, with `categoryId` matched to the **real Firestore category**
(`69dab76ce89baeef7e6c86e5`), not a fallback. Home then showed ₹272 and a Food donut.
The probe and the test row were removed afterwards. So Groq parsing, category
matching, Room writes and the Koin-from-no-UI path are all proven on iOS; **Siri's
own plumbing is not** - the simulator has no Siri, so the phrase-to-intent hop needs
a real device.

`Metadata.appintents` is generated into `FixMoney.app` at build time and contains
`AddExpenseIntent` plus its phrases - that much is checkable without a device:

```bash
python3 -c "import json;print(json.load(open('build/Debug-iphonesimulator/FixMoney.app/Metadata.appintents/extract.actionsdata')))"
```

### Kotlin string literals are UTF-16 in the binary

`strings FixMoney | grep Something` finds **nothing** for a Kotlin string, and macOS
`strings` has no `-el` flag to fix it. This wasted a debugging round confirming
whether an install was stale. Search the bytes instead:

```python
open(binary,'rb').read().count("my literal".encode('utf-16-le'))
```

### `simctl install` can silently leave the old build in place

The same round: the app kept running old code although `xcodebuild` had succeeded,
because an earlier `install` had not taken. After installing, confirm the bytes are
what you just built (the UTF-16 search above) before concluding a code path is
broken. `simctl install` also hands the app a **new data container path**, so any
saved `.db` path from before the install is stale - re-run `get_app_container`.

## Add Expense layout, revised 2026-10-02

- **The title is centred with a `Box`, not a `Row`.** Close (46dp) and SAVE are
  different widths, so a `weight(1f)` title between them centres in the *gap*,
  which sat visibly left of the screen's centre.
- **The Scan Receipt / Attach Photo row is pinned below the scrolling body**
  rather than trailing inside it. Everything used to crowd into the top two
  thirds with the last quarter blank. Same pattern as `SetupBudgetScreen`.
- **Horizontal padding is per section, not on the scroll column.** With it on
  the column the category `LazyRow` was clipped mid-label against a hard 18dp
  margin instead of scrolling off the screen; it now takes `contentPadding` and
  runs edge to edge.
- The amount placeholder and field are centre-aligned inside their minimum-width
  box - start-aligned, the whole group sat a few dp left of centre. **Superseded
  2026-10-03**: the placeholder is in a `decorationBox` and the width is
  measured, not intrinsic.

## Feature 6 - Scan Receipt (2026-10-02)

Point the camera at a receipt, and the amount, date, note and category land in
Add Expense already filled in. Ported from the old app's `feature/scanner/`.

| Piece | Where | Platform |
|---|---|---|
| on-device OCR | `shared/receipt/ReceiptOcr.kt` (expect/actual) | both |
| OCR text -> fields, via Groq | `shared/ai/ReceiptTextParser.kt` | both |
| photo bytes -> `ExtractedReceipt` | `shared/receipt/ReceiptScanEngine.kt` | both |
| capture -> review -> scan UI | `ui/scanner/ReceiptScannerScreen.kt` | both |
| camera + alignment frame + crop | `ui/scanner/ReceiptCapture.android.kt` | Android |
| system camera picker | `ui/scanner/ReceiptCapture.ios.kt` | iOS |

**The old app's own docs are wrong about this feature - do not trust them.**
`~/Downloads/Expense/CLAUDE.md` still describes one multimodal Gemini call with
"no local OCR, no regex heuristics". That architecture was replaced and never
re-documented: the live path there is ML Kit OCR -> Groq text model, and
`GeminiReceiptExtractor` is dead code that nothing calls (it only still owns the
`ExtractedReceipt`/`ReceiptImageStatus` types). The port follows the *code*.
Also worth knowing: that repo's `GroqReceiptExtractor.kt` is **untracked in
git**, so a `git clean` over there would delete the engine this port was read
from.

### Two stages, not one multimodal call

1. **On-device OCR** - ML Kit Text Recognition v2 on Android, the **Vision
   framework** on iOS. Free, offline, instant; the photo itself never leaves the
   phone. Only the recognised *text* is sent anywhere.
2. **Groq's text model** (`openai/gpt-oss-20b`) turns that messy text into
   fields.

The old app's note on why there is no vision call is still the reason: Groq has
no vision-capable model on this project's key - `meta-llama/llama-4-scout-*`
now 404s `model_not_found`. Re-check the models endpoint before assuming
otherwise.

Vision is the one dependency-free win in this project: it ships with iOS, so
unlike every other library here it has no `iosX64` or Kotlin-version klib gate
to clear. ML Kit and CameraX are Android-only, so they have none either.

### The category-index trick is load-bearing

Groq's `json_object` mode cannot constrain a string to the app's real category
names, so the prompt asks for an **index** into a numbered list instead. Carried
over verbatim. A free-text category field lets the model paraphrase or invent a
name that then silently matches nothing; an int can only be bounds-checked.

### Differences from the old app, and why

- **The engine fetches the category list itself** instead of having it passed
  down. The old `AddExpenseFragment` put a `String[]` in a Bundle *and* enabled
  the scan button even when categories had failed to load, which produced a
  prompt with an empty numbered list and silently broke category autofill. A
  repository call can't get out of sync with itself.
- **Camera denial keeps the user on the screen** with a tappable explanation.
  The old fragment showed a toast and navigated away, with no rationale, no
  permanent-denial handling and no route to App Settings - a second denial just
  bounced you out with a toast you might miss.
- **iOS uses the system camera** (`UIImagePickerController`, `allowsEditing =
  true`) rather than a custom AVFoundation preview with the Android side's
  alignment frame. That code is writable but **not testable from here at all** -
  no Apple Developer account for device testing, and the simulator has no
  camera - and an untested hand-written camera is a worse bet than the system
  one. The crop step in the editor stands in for the frame. Revisit if a device
  ever becomes available.
- **Only the Groq tier is migrated**, as with the chat assistant and voice
  quick-add. Nothing was lost: the Gemini extractor was already dead code.

### Things that cost time, or would have

- **`UIImagePickerController.delegate` is a weak reference.** Without the
  `retainedDelegate` strong reference in `ReceiptCapture.ios.kt`, the delegate
  is collected before the shutter is tapped and no callback ever fires.
- **`BlendMode.Clear` needs an offscreen layer.** The overlay's cut-out is
  drawn with `graphicsLayer { compositingStrategy = Offscreen }`; without it the
  "hole" paints solid black over the preview.
- **Don't write state from inside a draw scope.** The capture surface's size is
  taken with `Modifier.onSizeChanged`, not assigned inside `Canvas {}` - setting
  state while drawing schedules another draw just to read it back.
- The skia decoder for the review image is `Image.makeFromEncoded(bytes)
  .toComposeImageBitmap()`, **not** `asComposeImageBitmap` - that one exists but
  takes a different receiver and the error only says "receiver type mismatch".

### The capture -> review -> scan flow is deliberate

OCR and the Groq call fire only after the user looks at the photo and taps Scan
Receipt. A shot they would have retaken anyway never costs an API call. Every
failure (`null`, `BLURRY`, `NOT_A_RECEIPT`, or a result with nothing in it) is
recoverable in place with the old app's exact wording, and Retake comes back
rather than dropping the user out of the screen.

### Handing the result back

The old app wrote to the nav back stack's `savedStateHandle` and consumed once.
The Compose equivalent is a `pendingScan` holder in `App.kt`: the scanner writes
it, `AddExpenseScreen` applies it and calls `onScanConsumed()`. Same
write-then-remove contract, so re-entering Add Expense never re-applies a stale
scan, and the whole hop stays in common code.

`AddExpenseScreen`'s apply effect is keyed on **both** the result and the
category list, because a scan can land before the categories finish loading -
the old app needed a `pendingScanResult` field for that case, and re-running the
effect when the list arrives covers it without the extra state. Ownership is
only released once there is nothing left to wait for.

### Attach Photo is still inert - and that is faithful

`btnAttachPhoto` existed in the old `fragment_add_expense.xml` with **no click
listener anywhere**, no picker code, and no attachment field on the expense
model. There is nothing to port; wiring it would be new work, not a migration.

### Status

Both targets compile (`checkAll`). **Not yet run on a device** - the phone was
disconnected before the scanner could be exercised, so the camera, the crop, ML
Kit and the Groq round-trip are all build-verified only on Android, and iOS is
compile-verified as usual.

## Sizing across devices

`ui/Responsive.kt` wraps the whole app and scales **`LocalDensity`** from the window width against a 430dp design baseline, clamped to 0.82–1.0. Every screen was laid out against the large simulator, so on a narrower phone the same dp values ate more of the screen and read as oversized; scaling density once at the root shrinks `dp` and `sp` together and keeps every screen's proportions, instead of needing size variants in each composable. `fontScale` is left alone so the user's own text-size setting still applies.

Because of this, **do not "fix" an oversized screen by hand-tuning its dp values** - check what the scale is doing first.

The bottom bar carries **icons with labels**. An earlier pass removed the labels to match the reference screenshots; the labels were wanted, so they are back.

**The Groq API key is generated into shared source from `local.properties`** by the `generateSecrets` Gradle task - KMP has no `BuildConfig`, and the key should not be committed. Copy `GROQ_API_KEY` into `local.properties` on any new machine or the chat silently fails.

## UI fixes pass, 2026-10-03

Eight reported bugs, all in `composeApp`. What is worth knowing afterwards:

### Navigation now animates and responds to swipes

- **Transitions are set on the `NavHost`, not per destination** (`ui/App.kt`).
  Every hop in this app is lateral, so one set of four
  `slideIntoContainer`/`slideOutOfContainer` specs covers the whole graph;
  adding them per `composable` would be four lines each and drift apart.
- **`Modifier.horizontalSwipe` (`ui/components/Swipe.kt`)** is the shared
  gesture. It is `pointerInput` + `detectHorizontalDragGestures` **on purpose**:
  that runs in the *Main* pass, which reaches a parent only after its children,
  so the budget `Slider` and every category `LazyRow` consume their own
  horizontal drags first and keep working. An anchored `draggable` or an
  Initial-pass interceptor would have eaten both.
- **A swipe forward honours the same gate the Continue button does.** Without
  that, swiping walked straight past an empty name on SetupName and a zero
  budget on SetupBudget. `AllSetScreen` has a back swipe but deliberately **no**
  forward swipe: "Start Tracking" commits the profile, and a commit takes a tap.
- The five bottom-nav tabs are an `AnimatedContent` whose direction is read from
  `targetState.ordinal > initialState.ordinal`, so a swipe and a tab tap produce
  the same motion. It **clamps at both ends rather than wrapping** - Settings
  wrapping round to Home reads as a mis-tap. `SizeTransform(clip = false)` is
  required: without it the container animates between the two screens' heights
  and every tab change jolts vertically.

### Splash

`ui/SplashScreen.kt` is the start destination; it reads `isOnboardingComplete()`
**in its `onDone` callback**, not as a remembered start destination, so finishing
onboarding in the same session is seen. `LaunchedEffect(Unit)` fires it once, so
a theme flip mid-animation cannot navigate twice.

### The FixMoney mark needs a white plate, never `accent`

The artwork in `ic_fixmoney_logo.webp` is itself two shades of purple, so the
Welcome header's `accent` circle swallowed it. Both the splash and that header
now use a white circle with a `cardStroke` ring - the ring matters because
`bgSplash` is pure white in light mode and the plate would otherwise vanish.

### `AppColors.isDark`

Added as the last field (defaulted `false`, `true` in `DarkColors`). A few places
need the *screen* colour rather than a token: the bottom nav bar takes
`bgPrimary` in dark mode so it sits on the same navy as the content above it -
`bgCard` is a lighter navy and drew the bar as a visibly paler slab. Reading
`isSystemInDarkTheme()` there would be wrong, because Settings' own switch can
put the app in dark mode on a light phone (same trap as the system bars).

### Onboarding's budget reaches the Budget screen

`OnboardingState.complete()` is now **suspend** and writes the per-category
`BudgetEntity` rows the old `completeOnboarding()` wrote, via
`BudgetRepository.setSplitBudget`. Home always worked because it reads the raw
preference; the Budget screen only ever reads budget rows, so it stayed empty.
The split covers whichever categories were ticked on the budget step, or **all**
of them when none were - that row is labelled optional, so leaving it untouched
must not mean "no budget anywhere". `setSplitBudget` now hands the integer-
division remainder to the first row, or 25,000 over 7 categories rolled up to
24,997 on Budget while Home still said 25,000.

`OnboardingState`'s constructor takes `BudgetRepository` and `CategoryRepository`
now, both injected in `App.kt`.

**This one is not verified.** It needs tapping through onboarding, which neither
the iOS simulator (see "Driving the simulator") nor an absent Android device
allows.

### Add Expense's amount field

Three separate problems, all in the one `BasicTextField`:

- **`IntrinsicSize.Min` does not mean "as wide as the text".** A text field's
  minimum intrinsic width is not its content's, so the field stopped growing and
  a 6th digit scrolled the first one out of sight. It is now measured with
  `rememberTextMeasurer` against the same `TextStyle` it renders with, +6dp for
  the caret.
- **The value is a `TextFieldValue` with an explicit `TextRange`.** With a plain
  `String`, filtering the input on every keystroke reseated the caret at index 0,
  so editing mid-number produced reversed digits.
- The placeholder moved into `decorationBox` (it was a sibling in a `Box`), and
  the whole card focuses the field - the field is only as wide as its digits, so
  on an empty form there was a ~40dp target floating in a 300dp card.

### Home's month was not discoverably tappable

It opened a picker but rendered as a plain label showing the raw API form
(`2026-10`). It is now an accent-tinted chip with a `\u25BE` caret showing
`prettyMonthLabel(...)` - the same hint Analytics' month label already carried.
The "Ask AI" Lottie went 52dp -> 40dp with a 6dp top pad, which centres it
against the 52dp avatar in that Top-aligned row.

### Verified on the iPhone SE simulator 2026-10-03

Screenshotted and compared: the splash's circular mark, Welcome's white logo
plate, Home's month chip + smaller AI icon, and the bottom bar sitting on the
same navy as Home in dark mode. **The gesture work and the onboarding budget
rows are build-verified only** - nothing on this machine can tap or swipe either
platform.

## Second fixes pass + four ported screens, 2026-10-03

### The system navigation bar had a 40%-black scrim on it

The bottom bar read `#0B1A2B` and the strip below it `#07101A` in dark mode -
the same colour under Android's navigation-bar contrast scrim.
**`enableEdgeToEdge` turns `isNavigationBarContrastEnforced` on when the style's
night mode is dark**, which is exactly what this app asks for, so the fix is to
clear it *after* that call (it sets it itself) and set `window.navigationBarColor`
explicitly. `navigationBarColor` is ignored from API 35 on, where the bar is
always transparent and the app's own background - which already extends under it
- shows through instead, so the explicit colour is belt and braces for ≤34.

The colour itself is now one token, `AppColors.bottomBar`, read by both the
bottom bar and `SystemBarsAppearance`. They must not be able to drift.

### Home's budget comes from Room, not the preference

Setting a category budget moved the Budget screen's roll-up and left Home's
"Remaining" untouched, because Home read `prefs.getMonthlyBudget()` directly.
`HomeStateFactory.observe` is now a `combine` over the expenses **and** budgets
tables, and `totalBudget` is the sum of the month's budget rows, falling back to
the onboarding preference when a month has none.

It observes **all** expense rows and filters the month in memory rather than
running the month query. Two reasons: the insight engine's baseline is the three
weeks before last, which in the first days of a month sits entirely in the
previous one (a month-filtered query silently compared this week against
nothing), and deriving both from one list means the card and the figures above it
cannot disagree.

### `platform/DateMath.kt` - date arithmetic without kotlinx-datetime

`DateTime.kt` said to reach for kotlinx-datetime if real arithmetic was ever
needed. It is now (the week window, the insight baselines, the export range),
and the answer is still not a dependency: Howard Hinnant's `days_from_civil` /
`civil_from_days` are ~30 lines of **common** Kotlin, exact, with no `expect`
/`actual` and no klib to clear the `iosX64` + Kotlin-version gate.

**Verified, not assumed**: the two functions were transliterated to Python -
writing every division explicitly, because Kotlin's integer division truncates
toward zero while Python's `//` floors - and checked against `datetime` for every
day from 1970-01-01 to 2075-12-31. Zero mismatches in both directions, leap days
and year boundaries included. Redo that if the arithmetic is ever touched.

**`weekSpent` is no longer 0.** It was 0 only because there was no way to express
"the last 7 days"; it now reads 0 deliberately when Home is showing a month other
than the current one, where "this week" has no meaning.

### Settings' Dark Theme switch was lying on a first run

It derived its own state as `appPrefs.darkMode() ?: true` while `App.kt` resolves
`appPrefs.darkMode() ?: isSystemInDarkTheme()`. On a phone in light mode with no
stored preference, the app was light and the switch read ON - and the first tap
"turned dark mode off" while visibly turning it on. **The screen now takes the
applied theme as a parameter** rather than re-deriving it. Any future toggle that
feeds something outside Settings has to do the same.

Name and currency are also read per-composition instead of `remember`d, so Edit
Profile's changes show immediately rather than on the next visit to the tab.

### Export Data is wired

Ported from the old `ExpenseCsvExporter`. The **CSV is shared** - row filtering
and string building are identical on both platforms - and only `ExportFileWriter`
is `expect`/`actual`:

- **Android** writes into the shared Downloads collection. API 29+ is MediaStore
  and needs no permission; API 24-28 has no Downloads collection there, so it
  falls back to a legacy file write and `WRITE_EXTERNAL_STORAGE` is declared with
  `maxSdkVersion="28"`.
- **iOS** has no shared Downloads folder, so the file goes to the app's own
  Documents directory. **`UIFileSharingEnabled` and
  `LSSupportsOpeningDocumentsInPlace` are not set in `Info.plist` yet**, so the
  file is written but the user cannot browse to it in the Files app. Add both, or
  a share sheet (which needs a `UIViewController`, so it belongs in composeApp),
  before calling the iOS half done.
- `writeToURL`'s error-out parameter is `ExperimentalForeignApi` in Kotlin/Native
  2.2. Passing `null` is the documented "no NSError" form, so the `@OptIn` does
  not actually depend on unstable surface.

### Four screens ported from `~/Downloads/ui`

- **AI Insight card** (`ui/home/InsightCard.kt` + `shared/home/HomeInsight.kt`).
  Despite the name **nothing here is generated** - it is arithmetic over local
  rows, as it was in the old app, so it costs no Groq quota and cannot hallucinate
  a figure. Both thresholds are carried over (a 3-week baseline, a 20% minimum
  increase), as is returning null outright rather than filling the card with
  something vacuous. Unlike the old engine it takes the rows rather than a
  repository, so it cannot disagree with what is already on screen.
- **Expense Detail** (`ui/expense/ExpenseDetailScreen.kt`), reached by tapping a
  row on Expenses. **Edit is deliberately not offered**: the old screen's Edit
  routed to Add Expense with an `expenseId`, and that screen's edit mode is not
  migrated - it only ever inserts, so the button would have silently created a
  duplicate. Delete keeps the old confirmation dialog; it is the one
  unrecoverable action in the app.
- **Edit Profile** (`ui/settings/EditProfileScreen.kt`). Writes to
  `OnboardingPrefs`, not a server - there is no backend, so the local profile *is*
  the profile. **No avatar picker**: the old `AvatarPickerBottomSheet` /
  `AvatarManager` pair is not migrated, so the avatar stays the initial. "Your
  Activity" comes from the rows themselves, with "Member since" being the month
  of the earliest expense.
- **Chat category bars.** `ChatAnswer.categories` has been carried through the
  shared module since day one and nothing rendered it - the prompt asks for a
  breakdown whenever the question calls for one and the numbers were being thrown
  away. Bars are scaled against the largest value, not the budget, and coloured
  from the four category tokens cycled: the assistant returns names only, with no
  id to look a real colour up by.

### A route argument is Android-only

`NavBackStackEntry.arguments` is an Android `Bundle`, and its accessors do not
exist in common code - `expense_detail/{expenseId}` compiled on Android and
failed `compileKotlinIosX64` with *Unresolved reference 'getString'*. The
selected id is held in `App.kt` state instead, the same pattern (and the same
reason) as `pendingScan`. **Do not add a path argument to a route in this
project** without checking iOS.

### The chat was unusable with the keyboard open

Two bugs, both found only by typing into it on the device:

- **`imePadding()` is required on an edge-to-edge screen, not optional.**
  `android:windowSoftInputMode="adjustResize"` is in the manifest, but
  `enableEdgeToEdge` means the window does **not** resize - so the composer and
  the suggestion chips sat underneath the keyboard, and the user could neither
  see what they were typing nor reach Send. Any screen that pins something to
  the bottom needs it; `ChatScreen` was the one that did.
- **The keyboard's action key did nothing.** The field had no `ImeAction`, so the
  IME offered a newline on a conceptually single-line input and pressing it was a
  no-op. It is `ImeAction.Send` with a matching `KeyboardActions` now.

### Verified on the OnePlus Nord, 2026-10-03

Screenshotted and checked against real data, not assumed:

- **Navigation bar** measures `(11,26,43)` - byte-identical to the bottom bar,
  where it read `(7,16,26)` before. The seam is gone.
- **Home** shows `17% to 33000` and `Remaining ₹27,210` against `₹5,790` spent,
  i.e. the budget **rows** (33,000), not the onboarding preference (25,000).
  5,790 + 27,210 = 33,000 exactly.
- **`This Week` reads ₹5,790**, not ₹0.
- **AI Insight card** renders with a real forecast - "exceed budget by Oct 17 at
  this pace" - and the two-tone bar.
- **Expense Detail** opens from an Expenses row and matches the reference
  screenshot.
- **Edit Profile** shows 4 Expenses / ₹6,010 Tracked, which is the table summed
  by hand.
- **Settings** toggles match the stored preferences exactly (dark on, alerts off,
  voice on).
- **Export** wrote `Download/FixMoney Exports/FixMoney-2026-10.csv`, 183 bytes.
  Its contents were read back off the device: header plus the three October rows
  summing to 5,790, with the February 2020 row correctly excluded.
- **Chat bars** render: Shopping 5290, Transport 720 - both correct against the
  rows, scaled to the largest.

The date arithmetic is checked exhaustively (see above). **iOS is still
compile-only** for everything in this pass.

### Driving this device: do not chain adb taps

Two separate mishaps in one session, both from issuing taps without
screenshotting in between. A tap landed in the notification shade and opened
WhatsApp; later, taps sent before the app had finished launching went to the
Messages app. Once another app is in the task stack the symptoms get confusing -
`input` starts failing with *"Injecting to another application requires
INJECT_EVENTS permission"*, and taps appear to hit the wrong screen of FixMoney
when they are in fact hitting a different app.

**One tap, one screenshot, confirm, then the next.** To recover, never tap your
way back: `input keyevent KEYCODE_HOME`, then `am force-stop`, then `am start`.

### Fixoo: auto-scroll and New chat (2026-10-03)

- **The list follows the conversation.** A `rememberLazyListState` plus a
  `LaunchedEffect(messages.size, thinking)` that animates to the last index.
  Keyed on `thinking` as well as the size, so the spinner that appears under the
  question scrolls into view too - without that, asking from a full screen left
  the user looking at old messages with no sign anything had happened.
- **"New chat"** in the header clears the thread back to the greeting, which is
  now built by a `greeting()` factory rather than inlined in the initial state.
  It is **disabled while a request is in flight**: `send` holds a reference to
  the same list, so clearing mid-request would drop the reply into a
  conversation that no longer exists. It is also disabled when the thread is
  already just the greeting.

**The thread does not survive leaving the screen.** `messages` is `remember`ed
in the composable, so navigating back to Home and returning already gives a
fresh chat - "New chat" only matters within one visit. That matches the old
app, where the fragment was recreated. Persisting it would need the list lifted
into `App.kt` or a ViewModel.

### The export folder name changed

The old app wrote to `Downloads/Expense Exports/`; this one writes to
`Downloads/FixMoney Exports/`. Both directories now exist on the test device.
Deliberate - the app is FixMoney - but it means an updating user's old exports
stay in the old folder.

## The 2026 redesign — Home first (2026-10-03)

Source of truth: `~/Downloads/stitch_fixmoney_fintech_app_design/`. One folder
per screen, each with a `screen.png` and a `code.html`. **Read the HTML, not
just the PNG** — its Tailwind config carries the exact palette, type scale and
radii, which is how the tokens below were derived rather than eyedropped.

Home is done. The other eight folders are not started.

### Two colour holders on purpose

`AppColors` (`Theme.kt`) is *generated* from the old app's `colors.xml` and is
still what every un-redesigned screen reads. `DesignColors`
(`DesignTokens.kt`) is the new palette. Both are provided by `FixMoneyTheme`,
and a screen opts in by reading `Design.colors` instead of `AppTheme.colors`.

Folding the new surfaces into `AppColors` would have meant rewriting all nine
screens in one go, or leaving tokens in it that mean different things depending
on which screen you are looking at. **Delete `AppColors` once every screen has
moved**, not before.

The design is dark-first — every mockup is dark. `DesignLight` exists so the
light theme is not broken, but its values are invented, not design-sourced.
Check them against a light mockup before trusting them.

### Plus Jakarta Sans, cut from the variable font

The design specifies Plus Jakarta Sans. Google Fonts publishes **only the
variable TTF** for this family — the `static/` directory 404s, and the
jsDelivr/fontsource copies are `woff2`, which Compose cannot load. The four
weights in `composeResources/font/` were produced with
`fontTools.varLib.instancer` from the official variable file:

```python
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
f = TTFont("PlusJakartaSans[wght].ttf")
instancer.instantiateVariableFont(f, {"wght": 600}, inplace=True, updateFontNames=True)
f.save("plus_jakarta_semibold.ttf")
```

Poppins stays, and stays the Material theme default, so un-redesigned screens
keep the typeface they were laid out against. `DesignType` is opt-in per screen.

### Icons are generated stroke vectors

`nd_*.xml` in `composeResources/drawable` — 21 Feather/Material-style line icons
generated by a script rather than hand-written. They are authored **white** so
`Icon`'s tint controls them completely; a vector with baked colours cannot be
recoloured, and these appear in a dozen tints. The old full-colour PNG tab icons
are gone from the nav bar for exactly that reason — they could not show
selection at all except through the label.

### Layout gotchas worth keeping

- **Two Texts in a Row starve the weighted one.** The donut legend was a
  `Row { dot; Text(name, weight(1f)); Text(percent) }`. Compose measures
  *unweighted* children against the full row width first, so the percent took
  its natural width and the name got what was left — about two characters in
  half a card on a compact phone (`"Sh…"`). Fixed by making name + percent a
  single `buildAnnotatedString` with a muted span, measured and ellipsised as
  one unit. At 12sp "Transit 10%" still did not fit in ~74dp, so the legend is
  11sp with tracking removed.
- **Round stroke caps overhang.** `NewDonut` shortens each sweep by 6° and
  starts it 3° late; drawing the raw sweeps makes neighbouring slices overlap
  because a round cap extends half a stroke width past each end. A single 100%
  slice is special-cased to a full circle, or the only arc gets a visible notch.
- The bottom bar paints its background **before** `navigationBarsPadding()`, so
  it extends under the system bar instead of leaving a strip of page showing.

## Remote feature flags (2026-10-03)

`shared/config/FeatureFlags.kt` - one Firestore document switches features off
without a release.

**Firebase Remote Config is the obvious product for this and is not usable
here**: its SDK is Android/iOS-native with no KMP build, the same wall every
Firebase library hits in this project. So the flags ride the **Firestore REST
transport `CategoryRepository` already uses** - no SDK, identical on both
platforms, no `iosX64` / Kotlin-version gate.

Document `feature_flags/app`, one field per flag, `0`/`false` hides and
`1`/`true` shows. `FeatureFlag.key` is the field name, so **treat those strings
as a published contract** - renaming one silently reverts that flag to its
default.

**Everything fails open.** A missing field, a failed fetch, a first launch and a
403 all mean *on*. The document is how a feature gets taken **away**, never how
it gets turned on - otherwise a Firestore outage would blank the app. The last
successful fetch is persisted, so a cold start offline keeps what was last
published rather than snapping back to all-on.

**Live as of 2026-10-03**: `feature_flags/app` exists in the `expenseapp-7b500`
project with all 25 fields as int64 `1`, and the read rule below is published.
Every `FeatureFlag.key` has a matching field - worth re-checking after adding a
flag, because a missing field is indistinguishable from the feature being on.

The rule is what makes the fetch work at all - without it Firestore 403s the
request. It degrades silently, so a missing rule looks like the flags simply
having no effect:

```
match /feature_flags/{doc} { allow read: if true; allow write: if false; }
```

Writes stay denied, so the only way to flip a flag is the console (or a trusted
server). The document holds nine integers and no user data, which is why a
public read is fine here - the same reasoning as the `categories` rule above it.

Values are accepted as number, boolean *or* string, because someone editing in
the console by hand will produce any of the three.

`App.kt` refreshes once per launch and publishes the result through
`LocalFeatureFlags`, so a fetch landing after launch re-composes rather than
needing a restart. Screens read it with `isEnabled(FeatureFlag.X)`.

Gated today: voice quick-add, receipt scan, export (Settings row *and* the
Expenses footer link), SIP, Fixoo (Home's AI button, Ask Fixoo links), AI
insights (Home bento + Analytics copilot), budget alerts, and the Analytics and
Budget **tabs**.

**Settings is gated row by row** (2026-10-03). Every row has its own flag, and
each of the five groups has a `section_*` flag that drops the whole group at
once - 25 flags in total, all published at `1`.

Making nearly every row conditional broke the old `Section(content = { ... })`
shape, where callers placed their own `RowDivider()` between rows. A hidden row
left its divider behind, so a group could open or close on a stray hairline, and
a group whose rows were all hidden still drew its header over an empty card.

So `Section` now takes `rows: List<SettingsRow>` instead of a content lambda.
Building the list first is what makes the two fixes possible: the section
**places the dividers itself**, between surviving rows only, and **returns early
when the list is empty** so the header goes with it. Each `SettingsRow` carries a
stable `id` used as a `key()`, so hiding one row cannot hand its remembered
state to the row that slides up into its place.

Add a row by adding to the list, never by emitting a divider.

Hiding a tab needed two fixes beyond the `if`: the swipe step indexes into the
*visible* list rather than the enum (with a tab hidden the two stop lining up),
and a `LaunchedEffect` falls back to Home if the selected tab disappears under
the user mid-session.

### Splash uses the redesign's surface

`bgSplash` is the old generated token - `#FFFFFF` light, `#070E31` dark - and
matched neither new surface, so the splash flashed a different colour than the
screen that replaced it. It reads `Design.colors.surface` now.

### Edit Profile redesigned; the FAB is Home-only (2026-10-03)

**Edit Profile** from `stitch_fixmoney_fintech_app_design (5)` (which also ships
a `DESIGN.md` - its palette matches `DesignColors` exactly, no surprises).
Avatar, name, currency, Save, and an activity card counted from the rows.

Dropped, all data this app does not have: the **email address** with a
"Verified" tick (there is no account and no email anywhere), **"ID: FX-84920"**
(an invented identifier - the same kind the user had removed from Expense
Detail), **"256-Bit Hardware Enclave"** and the tick beside the name,
**"Active Sync"** / **"Cloud ledger synchronized"** (nothing syncs), the
**camera badge** on the avatar (`AvatarManager` / `AvatarPickerBottomSheet` are
not migrated, so there is nowhere to put a photo and no picker), and the header
**overflow menu**.

**The add-expense FAB is now Home-only.** It was removed from Expenses and
Budget at the user's request; Analytics and Settings never had one. The
scroll-aware show/hide machinery went with it on those two screens, along with
the imports it needed - `BudgetScreen` lost its `onAddExpense` parameter
entirely, since its empty state opens the limit sheet rather than Add Expense.
**Expenses keeps the inline "Add expense" button in its empty state**: that is
an empty-state action, not a floating one.

The bottom spacer on those screens dropped from 92dp to 28dp - 92 existed to
clear the FAB.

### Expense Detail redesigned, and Add Expense gained an edit mode (2026-10-03)

From `design 5/fixmoney_expense_detail_obsidian_settings_reference`.

**Add Expense now edits.** `AddExpenseScreen(editingId = ...)` loads the row,
prefills, retitles to "Edit Expense" and saves through the new
`ExpenseRepository.updateExpense`. That method re-inserts on the same primary
key - the DAO is `OnConflictStrategy.REPLACE`, so there is no separate `@Update`
to keep in step - and **carries `createdAt` / `localCreatedAt` over from the
original**: those are when the expense was *recorded*, and editing an amount
does not change that. Only `updatedAt` moves.

Two things are suppressed in edit mode: the **scan button** (a scan replaces
every field, which would silently rewrite the row being edited from a photo) and
the **pending-scan effect** that applies a returning scan result.

This closes a gap the first port left open - the old Expense Detail deliberately
had no Edit button because `AddExpenseScreen` only ever inserted, and a button
that duplicated the row would have been worse than none.

`platform/timeLabelOf(millis)` was added so "Created … 9:26 AM" is the row's
real `localCreatedAt`, not a fabricated time.

**Dropped from the mockup**, all claims about data that does not exist:
**"LEDGER SYNC"** and the green dot on the icon (nothing syncs), the **blue
verified tick** beside the note (nothing verifies an expense), **"Payment
Account — HDFC Bank ●●●● 4092 / Active AA Sync"** (no bank integration, no
payment-method field, invented account number), and **"ENCLAVE VAULT
VERIFICATION • SHA-256 Validated • Zero-Knowledge Store"**.

The **REF line is kept because it can be real** - it is derived from the row's
own primary key (`local-<millis>` becomes `FX-<yyyymmdd>-<last 4>`), not a
format invented for the screen.

### Four dialogs/screens redesigned (2026-10-03)

From the **"design 4"** drop, which is the only one with working screenshots for
these: `fixmoney_select_date_picker_obsidian_theme`,
`fixmoney_export_data_modal_obsidian_theme`,
`fixmoney_sip_calculator_obsidian_theme`,
`fixmoney_scan_receipt_viewfinder_obsidian_theme`.

**Date picker** (`components/MonthYearDialog.kt`) - still one composable for
both uses (`showDays` adds the day grid). Quick "This month" / "Last month"
chips, a year stepper, month and day grids. A cell that is *today* is ringed
rather than filled, which is the only way to tell it from the selection once the
user moves away. The mockup's **"FISCAL '26" badge is not here**: a fiscal year
is a real concept this app does not model, and a badge asserting one is
decoration standing in for a feature.

**Export modal** - after a successful write it offers **"Open with…"**, which
hands the CSV to the system chooser (`ACTION_VIEW` in `createChooser`, not
`ACTION_SEND`: the ask was for apps that *open* a CSV, which is viewing).
`FLAG_GRANT_READ_URI_PERMISSION` is load-bearing - without it the chooser
appears and every app it lists fails with a permission error.

`ExportFileWriter.write` now returns an `ExportLocation(display, handle)` rather
than a string: the handle is a `content://` URI on Android (MediaStore's own on
API 29+, a **FileProvider** URI on 24-28, where the export is a plain public file
and a `file://` URI would throw `FileUriExposedException`). The provider is
declared in the manifest with `export_paths.xml` scoped to the one Downloads
folder - a provider exposing more than the file it must is a way to leak private
storage.

`open()` returns **false on iOS** and the button is simply not offered there:
handing a file to another app needs a `UIActivityViewController`, which needs a
`UIViewController`, and `shared` has no view hierarchy to present from. Move it
into `composeApp` when iOS sharing is built.

**Only CSV is selectable**. Excel and PDF are shown with a
SOON pill and swallow taps. Unlike the Settings toggles (stored preferences,
which the user asked to make live), a *format* is a promise about the artefact:
picking one that does not exist either writes the wrong file or fails at the end
of the flow. The mockup's "Zero-Knowledge Export", "SHA-256 Validated" and
"audited" are **not** reproduced - nothing here is any of those.

**SIP calculator** - all of it is the standard SIP future value with monthly
compounding, so every figure is real. **"Start SIP Plan" is gone**: there is no
broker, no account and no money movement, and a primary button that looks like
it places an investment is the worst kind to ship inert. A disclaimer line
replaces it. The invented branding ("Sovereign Wealth Modeler", "WEALTH MATRIX
V2.4", "FixMoney Enclave Computation") is dropped; "compounded monthly" stays
because it is what the formula does and it changes how the number reads.

**Scan receipt viewfinder** - corner brackets on the cut-out, a status strip,
and a ring-and-disc shutter. **The brackets and shutter live in
`ReceiptCapture.android.kt`, not the shared screen**, because that is where the
frame's geometry is known and because the capture surface owns its capture
control by design (iOS uses the system camera and draws nothing). A first
attempt put a shutter in the shared screen; it would have been a second,
non-functional one. Four mockup items are absent: **"AI AUTO-DETECT • 99.4%"**
(there is no live detection and no confidence score - OCR runs *after* the
shutter), **"Targeting Merchant, GSTIN & Tax"** (the parser reads amount, date,
note, category - not a GSTIN), **flash** (the surface exposes no torch control)
and **gallery** (no photo picker exists).

### Fixoo chat, redesigned (2026-10-03)

Sixth screen, from `fixmoney_fixoo_copilot_chat`. **Neither drop of that mockup
has a usable `screen.png`** - both are 28-byte `<FIFE Image failed to fetch>`
placeholders - so it was built from the HTML alone and verified by rendering,
not by comparison. Check it against a real render before calling the layout
final.

Carried over: the header with a live **month pulse strip** (`Oct pulse: ₹4,220
spent • 16% of cap`, real), timestamped bubbles with sender badges, the
assistant's category bars, an insight card on the opening message, icon'd
suggestion chips with Shuffle, and the composer.

**The mockup's centrepiece is not here, deliberately.** It is a pair of
"Scenario" cards - *Reallocate Runway*, *Expand Food Cap* - with invented
figures ("projected deficit ~₹1,850", "AI Confidence: 99.4%") and buttons that
move money between envelopes. Three problems at once: the model returns text
plus category totals and nothing like that structure, the actions do not exist,
and the numbers would have to be fabricated. **Fabricated financial analysis
with an Apply button is not shippable at any fidelity.** The card that *is*
there is `HomeInsightEngine`'s, every figure of which is arithmetic over the
user's own rows.

Also dropped: the overflow menu and thumb up/down (nothing behind either),
attach-file, and "Analyzed via HDFC Verified Feed" (no bank feed).

**Two footer/status lines were rewritten because the mockup's are false:**

- It says **"End-to-end encrypted"**. It is not - the question and a summary of
  local spending go to Groq over HTTPS. The footer says that instead. Do not
  restore the original wording.
- The header says **"Online & Synced"**. Nothing syncs; there is no backend. It
  reads "Reads your local data".

`platform/currentTimeLabel()` was added (expect/actual) for message stamps -
turning epoch millis into a wall clock needs the zone offset and the locale's
12/24-hour preference, and both platforms already have a formatter that knows
them. iOS uses `setLocalizedDateFormatFromTemplate("jmm")` rather than a fixed
pattern for the same reason.

Chip labels are short (`"Am I saving?"`) while `Suggestion.prompt` holds the
real question - the full text pushed the second chip off a compact screen.

### Settings, redesigned (2026-10-03)

Fifth screen, from `fixmoney_settings`. **This mockup is almost entirely
fiction** - of its 17 rows, four map to features that exist (the Android voice
bubble, base currency, dark theme, export). The rest name an autonomous spend
guard, insight scheduling, neural categorisation, a fiscal cycle start, budget
sweeping, a biometric lock, a blur compositor, haptics, a zero-knowledge
keyphrase and an account logout - none of which this build has.

**The user chose the full layout with live switches** (asked explicitly, two
options offered). So every switch flips and persists through
`AppPrefs.preview(PreviewToggle.…)`, and **nothing reads those values back**.
Wire each one as its feature lands.

Rows in that state carry a small **PREVIEW** pill. That pill is not decoration:
"Smart Cap Rollover - sweep unspent budget into next month's envelope" with a
live switch and no pill reads as a promise that money is being moved, which is
the one class of misunderstanding this app must not create. **Remove a row's
pill only when its feature actually works.**

**Connected Accounts is an empty state**, also the user's choice. The mockup
lists HDFC and ICICI accounts with balances; those figures are invented, and
rendering invented numbers as the user's money is not shippable at any fidelity.
The section shows "No accounts linked" and a link row marked PREVIEW.

Two more lines that would otherwise assert something false: the profile card
says "Local profile on this device" where the mockup has an email address (there
is no account), and the sync strip says "Offline" where the mockup says
"Synchronized" (there is no backend).

**The PREVIEW pill belongs on the subtitle line, not the title's.** Sharing the
title's row cost about 90dp and truncated every long name - "Fixoo Autonomous
Gu…", "Insight Freque…". The title is the row's identity and has to survive;
the subtitle can ellipsise.

### Budget + Edit Budget Limit, redesigned (2026-10-03)

Fourth screen, from two mockups: `fixmoney_budget_limits` (the board) and
`fixmoney_set_budget_limit` (the sheet over it).

`shared/expense/BudgetState.kt` derives the overall cap, pace delta, daily burn,
runway, envelope health bands and the spending guard from the budget rows plus
the month's expenses. `BudgetProgress` gained `categoryId` (the sheet needs it)
and **`rawPercent`** - the existing `percent` is clamped to 0..100 for the
progress bar, which cannot tell "exactly at the cap" from "double it", and the
status word has to.

**Six pieces of the two mockups are deliberately absent.** Each is a control
with nothing behind it, and a switch that silently does nothing is worse than no
switch:

- The **Category Envelopes / Smart Automation / Rollover** tabs - two of three
  lead nowhere.
- The **Budget Automation** card ("Rollover Unspent Balances", "Velocity
  Shield"). Neither rule exists.
- The guard card's **Dismiss** button: nowhere to persist a dismissal, so it
  would return on the next recomposition.
- The sheet's **Cadence Period (Monthly / Weekly)** - budget rows are keyed
  `yyyy-MM`; there is no weekly cap in the schema.
- The sheet's **Smart Pace Guard** and **Rollover Excess** switches.
- **Precision Mode** and the sheet's history/reset buttons - no edit history is
  kept.

Put them back with the features, not before.

Other decisions worth knowing:

- The sheet is a `Dialog` pinned to the bottom, **not** a `ModalBottomSheet` -
  that component is Material3 and its Compose Multiplatform support lags, while
  a full-width dialog gives the same shape with no platform risk.
- **Removing a limit takes two taps on the same row**, with the label changing
  to say so and the arm lapsing after 4s. A confirm dialog stacked on a dialog
  is awkward on both platforms.
- Editing **locks the category**; adding offers only categories without a cap,
  so saving can never silently overwrite an envelope the user is not looking at.
- The guard card appears only once an envelope passes 75%, and the "Match
  spend" chip only when there is spend to match - a shortcut that is a no-op is
  just a dead control.
- `SetBudgetDialog` is gone, superseded by `EditBudgetSheet`. Its file now holds
  only `DialogButton`, which `ExportDataDialog` and `ExpenseDetailScreen` still
  use; that moves or goes when those two are redesigned.

### Analytics, redesigned (2026-10-03)

Third screen. Mockup: `stitch_fixmoney_fintech_app_design/fixmoney_analytics/`.

`shared/analytics/AnalyticsStateFactory` computes the whole screen from the
expense and budget tables - total, daily burn, peak day, projection, weekly
velocity, per-category allocation with a previous-period comparison, and payee
concentration. No API call and nothing cached, so it cannot disagree with
Expenses or Home. Scope is Month / Quarter / Year, each **ending** at the
anchor month.

**The velocity chart must not scale to the budget.** First version used
`max(tallestBar, budgetLimit)` as the axis so the limit was always on screen.
On the device that destroyed the chart: the budget is the *sum of every
category's limit*, so a ₹5.1L budget against ₹34k of spend squashed every real
bar to a 2% sliver. It now scales to the bars, and the dashed budget rule is
drawn **only within 1.25x of the tallest bar** - the range where "am I near it?"
is the question. Beyond that the caption still states the limit and the bars
stay readable.

Projected bars are drawn as an **outline, never a solid** - an extrapolation
must not read as money already spent.

Deliberate differences, all missing data:

- **"Where it went" groups by the note**, and is titled that rather than the
  mockup's "Merchant Concentration". There is no merchant field in this schema;
  the note is what the user typed. Rows without one are excluded and the card
  hides entirely when none have one.
- The Copilot card's text comes from `HomeInsightEngine`. The mockup's "weekend
  dining accounted for 64% of your Food outflow" needs a weekday/weekend split
  per category that the engine does not compute. The card is **absent** when
  the engine has no signal, rather than padded with filler.
- Category badges compare against the **previous period**, not a rolling
  average, and are absent when there is no previous period.
- **Year is four quarterly bars, not twelve monthly ones** - twelve do not fit
  across a phone.

### Expenses, redesigned (2026-10-03)

Second screen onto the new design. Mockup:
`stitch_fixmoney_fintech_app_design/fixmoney_expenses/`.

The **collapsing header is now shared**: `ui/components/DesignScaffold.kt` holds
`DesignAppBar`, `LineIcon` and `CollapsingScreen`, and both Home and Expenses
pass a `sticky` section and a `body`. Category colour/glyph/short-label mapping
moved to `ui/CategoryVisuals.kt` for the same reason. Adding a third screen
should mean neither is touched.

**The mockups' app bars say the wrong thing.** `fixmoney_expenses` and
`fixmoney_settings` both title theirs "Home", while analytics says
"Fixmoney — Analytics" and budget says "Budget & Limits" - template leftovers,
not a design intent. The bar takes the screen's own name here.

Deliberate differences, all missing data rather than taste:

- Row subtitles are "Shopping • Today", never "Linen shirt & socks • 7:40 PM" -
  the schema has a date and no clock time.
- The search field's trailing "tune" button is **inert**. The mockup implies an
  advanced-filter sheet that does not exist; the category pills are the filter.
- The footer reads "7 transactions in this period", not "All September
  transactions reconciled". Nothing in this build reconciles anything, and
  claiming it did would be a lie about the user's money. The export link under
  it is real - it opens the working `ExportDataDialog`.
- **"Custom" means all time**, not a range picker. There is no range state yet;
  the label is the mockup's, the behaviour is the honest subset.
- The trend badge is **omitted when the previous month has no rows** rather than
  shown as 0% or 100%. No baseline, no claim.

### The header collapses: app bar scrolls off, greeting takes its place

`AppBar` + `GreetingRow` are **one pinned stack** laid over the scrolling
content. Scrolling slides the stack up by exactly the scroll offset until the
app bar has gone, then stops - the greeting is left sitting where "Home" was and
the cards pass underneath it. Scrolling back down unwinds identically.

The offset is **derived** from `scroll.value`, not animated towards a target.
That is what makes the two directions match for free and leaves nothing to get
out of sync or to catch up when the direction changes mid-fling.

Three things this needs that are easy to get wrong:

- **The content's top spacer reserves the stack's *uncollapsed* height**, and
  the stack shrinks by the same pixels the content scrolls. Shrinking the spacer
  as well moves the content twice as fast as the finger.
- **Constraining the stack's height from its own measured size deadlocks.** On
  the first frame `headerBodyPx` is 0, so the box is 0dp, so the content
  measures 0, so `headerBodyPx` stays 0 — and the header never appears at all
  (seen on the device: the hero card sat under the status bar). The box is left
  unconstrained until it has been measured once; collapse is 0 then anyway, so
  it looks identical.
- **The inner column takes `wrapContentHeight(unbounded = true)`**, so the size
  reported to `onSizeChanged` is its natural height rather than whatever the box
  has shrunk to. Without it the collapse distance chases its own tail.

The status-bar spacer lives *outside* the clipped region, or content slides
behind the status bar.

**`Color.copy(alpha = x)` replaces the alpha, it does not scale it.** The
hairline fades in with the collapse, and writing
`colors.stroke.copy(alpha = progress)` overwrote `stroke`'s own 8% and painted a
**solid white bar** across the screen at full collapse (solid black in light
mode). Multiply instead: `copy(alpha = colors.stroke.alpha * progress)`. Every
token in `DesignColors` that carries alpha - `stroke`, `strokeAccent` - has the
same trap.

**The hairline occupies a pixel, so the content spacer has to pay for it.**
Reserving only `statusBar + headerBody` put the rule on top of the hero card's
first row and shaved its top rounded corner - small, but visible as the card
"being cut" at rest.

### The FAB yields while reading

It hides on scroll-down and returns on the first upward drag, with a 14px dead
zone so fling wobble does not flap it, and is always visible at rest at the top.
The direction comes from `snapshotFlow { scroll.value }` inside a single
`LaunchedEffect(scroll)` — **keying the effect on `scroll.value` instead makes
Compose cancel and restart it on every scrolled pixel.**

### Superseded: the app bar used to be pinned alone

`AppBar` sits **outside** the scrolling column, which takes `weight(1f)`; the
greeting row is inside it. That matches the mockup, where the header is the only
`position: fixed` element on the page. The header is opaque rather than the
mockup's translucent blur - Compose Multiplatform has no backdrop blur in common
code, and content scrolling under an opaque bar clips cleanly at its edge.

**Verifying a scroll position without being able to scroll**: the iOS simulator
takes no input (see "Driving the simulator"), so the pinned header was proved by
temporarily passing `rememberScrollState(initial = 700)`, screenshotting, and
reverting. Cheap, and it beats reasoning about a layout from the source.

### Where the data was not there

Three deliberate departures from the mockup, all for missing data rather than
taste — do not "fix" them without adding the data first:

- Recent rows read `Food · Today`, not `Food · Today, 4:15 PM`. The `expenses`
  table stores a `yyyy-MM-dd` date and no clock time (it is the old app's
  schema, kept so an update can read an existing install).
- The AI card's copy comes from `HomeInsightEngine`. The mockup's "Swiggy orders
  on weekends are driving the spike" implies merchant-level analysis the app has
  no data for.
- The bell is inert; there is no notification feed.

`HomeUiState` gained `daysLeft`, `dailyAverage`, `recent` and `paceLabel` for
this screen. **`paceLabel` allows a 10-point grace** against the month's
elapsed percentage: a strict comparison calls any month "running high" after one
ordinary purchase on the 2nd, and the mockup itself shows "On track" at 50%
spent with 18 of 30 days left.

### Verified on the iPhone SE simulator 2026-10-03

Both targets build. The screen was rendered and compared against `screen.png`:
app bar, greeting, hero card (figure, days left, progress, pace, three stats),
the breakdown donut with its legend, the AI card, Recent, the FAB and the new
nav bar all match. **Android has not seen it** — the phone was disconnected.

To get a seeded simulator rather than the onboarding gate: write a typed
`prefs_onboarding.plist` into `<container>/Library/Preferences` (a plist written
with `plutil` from a shell heredoc comes out as *strings* and `getBoolean` then
reads false — use explicit `<true/>`/`<integer>` XML), launch once so Room
creates `Documents/fixmoney.db`, then `sqlite3` rows straight in.

## Blockers and not-done

- ~~Android has never been run.~~ **Cleared 2026-10-02** — see "Verified on a real Android device" under "Testing on Android".
- **No Apple Developer account** ($99/yr). `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` is deliberately empty. Needed before device testing or TestFlight.
- **Scan Receipt is built but unrun.** See "Feature 6" - it compiles for both targets and has never been exercised on hardware. **Attach Photo** stays inert, which matches the old app.
- **No avatar picker** - Edit Profile shows the initial. The old `AvatarManager` / `AvatarPickerBottomSheet` are not migrated, so the mockup's camera badge has nowhere to go.
- **Every screen is on the new design**, splash included.
- **Settings has six stored-but-inert switches**, each marked PREVIEW - see "Settings, redesigned". They are preferences with no reader; drop the pill as each feature is wired. Mockups for all of them are in `~/Downloads/stitch_fixmoney_fintech_app_design/`.
- **Only Home keeps the app bar.** Expenses and Analytics pass `title = null` to `CollapsingScreen`, so their own title row is the header - the logo/bell/avatar strip would just repeat what Home shows.
- ~~**Export Data writes nothing.**~~ **Wired 2026-10-03** on both platforms; the iOS file is not yet reachable from the Files app - see "Second fixes pass".
- **No AdMob**, and no Gemini tier for either the chat or voice parsing (Android-only SDK; plan section 6.2 has the REST route).
- ~~**`weekSpent` is still 0** on Home.~~ **Fixed 2026-10-03** - see `platform/DateMath.kt`.
- **Settings' preference stores do not match the old app's** - see "Known preference-store mismatch" above.
- **Changing the budget still does not rewrite the budget rows.** Onboarding writes them, and Home now reads them, so the two agree - but there is no way to edit the overall monthly figure after onboarding except per-category on the Budget screen.
- **An install that onboarded before 2026-10-03 has no budget rows** and still opens Budget empty. There is no backfill; "+ Set Budget" is the way out.

## AI coins - built on `feat/ai-coins-wallet`, run on a device against the old Worker only (2026-10-07)

Branch `feat/ai-coins-wallet` was cut from `feat/premium-subscription` (2 commits ahead of `main`: the legacy-DB import and the microphone foreground-service fix). Design PDF + Stitch prompts: `docs/FixMoney_Analytics_Siri_Coins_System_Design.pdf`, `docs/Coin_Wallet_Stitch_Prompts.md`.

**User-facing name is "Fixoo Coins"** (renamed from "AI Coins" at the user's request); code identifiers still say coins/wallet.

**Coin config is live from Firestore** (user's request: change the trial length etc. without a deploy). The front Worker reads document `coin_config/app` (fields `trial_days`, `trial_daily_cap`, `reward_per_ad`, `daily_ad_cap`, `welcome_bonus`, `cost_fixoo`, `cost_voice`, `cost_scan`, `cost_insight`) over REST, caches it 5 minutes, clamps typos to sane maxima, and passes it into every wallet call; `wrangler.jsonc` vars are only the defaults. **The trial end is derived from `createdAt` + the live `trial_days`**, so a change applies to existing users too. **Live since 2026-10-07**: the rule `match /coin_config/{doc} { allow read: if true; allow write: if false; }` is published (added through the Firebase Rules REST API with the `gcloud` token of gomechg@gmail.com - the existing `categories` / `feature_flags` rules were read first and kept verbatim) and `coin_config/app` exists with all ten fields as int64 at the `wrangler.jsonc` defaults. So a value changed in the console now applies to production within 5 minutes - **and to any local `wrangler dev` too, unless `.dev.vars` sets `FIREBASE_PROJECT_ID=` (empty)**: without that line the production `trial_days: 5` beats `TRIAL_DAYS=0` and the paid tests silently run in trial. The repo's `.dev.vars` has the line.

Rules: tracking is free; Fixoo chat, voice quick-add and receipt scan cost 1 coin each; 5-day free trial (30 AI uses/day cap); a rewarded video gives 5 coins, max 10 videos/day; 10-coin welcome bonus when the trial ends; the Home insight is free (it is local arithmetic, no AI call). **All of these are `vars` in `worker/wrangler.jsonc`**, not code - the open product decisions in the design PDF's section 12 only need a var change.

**No login and no Firebase Auth - the user's explicit decision.** Do not add a sign-in step.

### Production Worker - deployed 2026-10-07 (user's call: "jitna kaam ho chuka hai wo deploy kar do")

- `fixmoney-ai-proxy`, version `916f899e-1260-4232-a760-2425b0cf205b`, at `https://fixmoney-ai-proxy.vikram8575229.workers.dev`. Durable Object migration `v1` (WalletDO, DeviceDO) applied.
- Secrets: `GROQ_API_KEY` (from before) and `TOKEN_SECRET` (random, set by Claude, **not recorded anywhere** - rotating it only makes devices re-register by device key; wallets survive). **Not yet set:** `PLAY_SERVICE_ACCOUNT` - premium verify answers 502 `PLAY_UNAVAILABLE` until it is.
- `DEV_MODE` is **not** set in production, and the smoke test proved both test bypasses are closed there: a `dev-premium-*` token went to Play (502) instead of being accepted, and an SSV callback with `key_id=test` got "unknown key_id".
- Smoke test passed: unknown route 404, wallet without token 401, register 200 (trial 5 days - Firestore `coin_config` still 403, defaults apply), wallet with token 200, **legacy token-less chat 200** (older builds unaffected; `REQUIRE_AUTH` stays "false"). One junk account was created by the smoke test (`deviceKey` "smoke-test-claude-2026-10-07").
- **Seeing the data:** Cloudflare dashboard -> Durable Objects -> `fixmoney-ai-proxy_WalletDO` -> **Data Studio**, then enter an **account id** (the object name; the app keeps it in `prefs_onboarding` as `coin_account_id`). The wallet is stored with the KV API (key `s`), which Data Studio shows read-only. There is **no "list all wallets"** - one object per user is the design; totals would need a separate store (e.g. Workers Analytics Engine) if wanted. Per-event history is in **Workers Logs** (observability is on): search `[coins]`.
- **Still pending before relying on it** (all need the user's consoles): real rewarded ad unit + SSV pointed at `/v1/admob/ssv` (then its id in `REAL_REWARDED_UNIT` and `REWARDED_AD_UNITS`); Play Console subscription + `PLAY_SERVICE_ACCOUNT` + a real test purchase; then flip `REQUIRE_AUTH` to "true" once every shipped build registers. Done 2026-10-07: Firestore `coin_config`, per-IP new-account limit, proactive gate - see "Coin pending work, second pass" below.

### Worker (`worker/src/`) - routes, status codes, deploy and test recipe in `worker/README.md`

- Identity: `/v1/register` takes a device key and returns an HMAC-signed device token. The account is found by **token first** (so a backup-restored phone keeps its wallet), device key second (so a reinstall keeps wallet *and* trial).
- **One Durable Object per wallet** (`WalletDO`), plus a `DeviceDO` per phone. Not D1: a D1 database runs one query at a time and answers "overloaded" under a launch-sized burst; Durable Objects give per-user serialisation with no shared queue. SQLite-backed, the only kind the free plan allows.
- **Reserve -> commit**: a coin is held before the model call and taken only if the model answered. Busy/failed calls cost nothing; there is no refund path to get wrong.
- Coins are created **only** by `GET /v1/admob/ssv`, AdMob's ECDSA-signed server callback. The app never reports a reward.
- Token-less requests still take the old uncharged path while `REQUIRE_AUTH` is `"false"`, so nothing breaks before every build registers.
- Verified locally with `wrangler dev` + mock Groq + a local SSV key: 11 trial checks, 16 paid checks, and 5 parallel calls on the last coin -> exactly one 200; re-run after `dailyAdCap` was added to the wallet view (all pass). `test/e2e.py` finds `ssvkeys.mjs` next to itself - an earlier version looked in the scratch dir and broke the README recipe. **Deployed to production 2026-10-07 - see "Production Worker" below.** Gotchas that cost a cycle: Workers AI is always *remote* under `wrangler dev`, so a mocked Groq 429 gets a real answer unless `DEV_NO_FALLBACK=true`; and a `.dev.vars` with `GROQ_URL` silently points local runs at the mock.

### App

- `shared/coins/WalletRepository` - registration, cached balance (`StateFlow`, persisted so the chip shows a number offline), `ticket()` for each AI call, `absorb()` to read the coin outcome and the `X-Coins*` headers off every AI response, `awaitReward()` to poll after a video. **If registration fails (offline, or the currently deployed pre-coins Worker answering 404) every AI call goes out without a token and works exactly as before** - that is deliberate, and it is why the app is safe to run against the old Worker.
- The three AI callers (`GroqChatAssistant`, `VoiceExpenseParser`, `ReceiptTextParser`) take the wallet; refusals surface as `ChatAskResult.NoCoins/TrialLimit/Busy`, `VoiceAddResult.Blocked`, and `AiBlockedException` from the scanner. `AiGate.shortMessage()` is the one-line text for toasts, Siri and the scanner hint; every one says nothing was charged.
- Device key: Android = SHA-256 of `ANDROID_ID` (`AndroidDeviceKeySource`); iOS = Keychain UUID (`IosDeviceKeySource`, falls back to NSUserDefaults if the Keychain refuses, e.g. unsigned simulator builds). The token lives in the `prefs_onboarding` Settings file, which Android Auto Backup carries to a new phone.
- UI (`composeApp/.../ui/coins/`): `CoinsController` + `LocalCoins` (one per app, raises sheets from any screen), `CoinChip` in Home's **app bar** between the bell and the avatar (via a new optional `actions` slot on `DesignAppBar` / `appBarActions` on `CollapsingScreen`), an "AI Coins" row at the top of Settings' AI section, `WalletScreen` (route `wallet`), and `CoinSheetHost` for out-of-coins / trial-limit / trial-ended / rewarded-video states. Flag `coin_wallet` hides the UI only; it does not stop charging.
- **Existing screens were not restyled** - the user asked for that explicitly. Only the chip, the Settings row, and new chat/scanner messages were added.
- Coin colour is the Stitch system's `tertiary` amber `#FFBA4B`, defined in `CoinVisuals.kt`, not in the theme.
- Mockups: `~/Downloads/stitch_fixmoney_fintech_app_design 8/` - four of six `screen.png` are 28-byte placeholders, so those were read from `code.html`. **Most of their content was invented and was not ported**: daily quests, streaks, referrals, a ₹799/yr plan, coins for exports, 5-15 coin prices, a 14-day trial, "Queue #248", "Latency 840ms", "Credited Instantly". Do not "restore" any of it.

**Why the chip is not where the Stitch mockup put it:** in the greeting row it squeezed "Good Morning, Vikram" to "Vikr…" on the 1080px OnePlus - a change to existing, approved UI. The app bar has free space and leaves the greeting untouched.

### Verified on the OnePlus Nord (Android 12), 2026-10-07 - against the *old* deployed Worker

- Debug APK installed over the existing `.kmp` build (data kept); no crash.
- `register failed: 404` in logcat as expected, and **Fixoo still answered** ("Top expenses" -> bars) on the token-less path - the fallback works.
- Chip shows "–", Wallet shows "Couldn't reach FixMoney…" with the video button disabled, Settings shows the "AI Coins" row. Back from Wallet returns to Home.
- The Google test rewarded ad **loaded** ("request sent from a test device"). UMP logged "no form(s) configured for the input app ID" - no GDPR message exists in the AdMob console yet; the app carries on, which is the intended behaviour, but configure one before any EEA/UK release.
- Two "anomalies" in that session (Back from Wallet leaving the app; one tap opening the wrong screen, later a video starting on its own) were **the user tapping the phone at the same time** - not app bugs. When driving the device over adb, ask whether the user is also using it before concluding anything from an unexpected screen.

### Testing coins on the device against a local Worker (verified 2026-10-07)

No deploy needed. Debug builds only:

```bash
# worker/.dev.vars: GROQ_API_KEY (from local.properties), TOKEN_SECRET, DEV_MODE=true, SSV_TEST_KEY (node worker/test/ssvkeys.mjs <dir> gen)
cd worker && npx wrangler dev --port 8787 --ip 127.0.0.1
adb reverse tcp:8787 tcp:8787
adb shell am start -n com.vikram641.expensetracker.kmp/com.fixmoney.MainActivity --es worker_url http://127.0.0.1:8787
```

`DebugWorker.kt` remembers the URL; to return to the real Worker run `adb shell "am start -n com.vikram641.expensetracker.kmp/com.fixmoney.MainActivity --es worker_url ''"` - the empty value must be quoted inside the device shell, or adb drops it and `am` fails with "Argument expected after worker_url" and refuses to act unless the build is debuggable; cleartext to 127.0.0.1/localhost is allowed only by `src/androidDebug/` (a debug-only manifest + network security config wired in `composeApp/build.gradle.kts`). **Checked with aapt2: the release APK has no `networkSecurityConfig`.**

Since the debug ad unit sends no SSV, credit a watched video by hand: read `coin_account_id` with `adb shell run-as <pkg> cat shared_prefs/prefs_onboarding.xml`, sign with `node worker/test/ssvkeys.mjs <dir> sign <accountId> <txId>`, and `curl` the result to `/v1/admob/ssv`.

Verified that way on the OnePlus: register -> 200 and chip "Trial · 5d"; test video played to "Reward granted"; the app polled `/v1/wallet`; a signed callback credited +5 and the wallet showed **5 coins, 1 of 10 watched**.

Then, with `TRIAL_DAYS=0` and (test only) `COST_FIXOO=10` in `.dev.vars`: the existing wallet's trial ended on the next call and the 10-coin welcome bonus landed (trial end is derived live, as designed); "Reward unlocked! 20 -> 25, 3 of 10 watched" after a video + signed callback; two Fixoo questions logged `reserved` -> `charged` (25 -> 15 -> 5); the third logged `refused NO_COINS`, returned 402 and raised the out-of-coins sheet with nothing charged. Worker logs carry one `[coins] <event> {json}` line per register / reserved / charged / released / refused / ad_reward / trial_ended.

**Trial-ended sheet always appears once**, even when `welcome_bonus` is 0: the Worker marks the moment the trial is first seen ended and sends `welcomeBonusGranted` (possibly 0) on that one response; the sheet hides its bonus row at 0. Before this it only appeared when a bonus was paid, so a 0 bonus meant the user was never told. Verified on device with a fresh wallet (`TRIAL_DAYS=0`, `WELCOME_BONUS=0`): sheet once, chip 0, not shown again after a relaunch, and Fixoo at 0 coins -> 402 + out-of-coins sheet.

### Fixoo Premium - Rs 99/month Google Play subscription (built 2026-10-07; Worker side deployed, no Play product yet)

The user asked for "Google Pay" payments. **Play policy requires Google Play Billing for digital subscriptions in a Play app** - a direct GPay/UPI link would get the app rejected - and Play's own checkout offers UPI / Google Pay / cards in India, so that is what is built. Do not add a direct payment link.

- **What it does:** while premium is active the Worker charges no coins for any AI feature (`paidBy: "premium"` in the logs). Not "unlimited": `premium_daily_cap` (default 100/day, Firestore-configurable) protects the shared Groq quota, and the copy says "fair use", never "unlimited" or "ad-free".
- **App** (`ui/coins/PremiumStore*.kt`): Play Billing **9.1.0** (plain Java artifact, so no Kotlin-metadata clash; v8+ is mandatory for updates from 31 Aug 2026). Product id `fixoo_premium_monthly` (must match worker `PREMIUM_PRODUCT_ID` and Play Console). The account id goes in as `obfuscatedAccountId`. The price shown is Play's localized `formattedPrice`, never hardcoded. On launch every owned subscription is re-sent to the Worker (restore after reinstall / new phone / lost confirmation). The app never acknowledges - the Worker does, after verifying.
- **Worker** (`src/play.ts`, `POST /v1/premium/verify`): verifies with `purchases.subscriptionsv2.get` using a service-account JWT signed with Web Crypto (no Google SDK), checks state ACTIVE / IN_GRACE_PERIOD / CANCELED-but-unexpired for our product, stores Play's expiry in the wallet, then `purchases.subscriptions.acknowledge` (Google refunds anything unacknowledged for 3 days). Renewals/cancellations are noticed by re-verifying on `/v1/wallet` once the stored expiry passes - no Pub/Sub RTDN yet.
- **Billing 9 gotcha that cost three build cycles:** with `enableAutoServiceReconnection()` the client reports `isReady == true` before `startConnection()` was ever called, so an `if (isReady) return` guard silently skipped connecting - no product, no restore, no error. Guard with our own `started` flag.
- **Debug builds simulate the purchase** (`PremiumStore.simulated`): the `.kmp` package is not in Play Console, so the product never loads; a debug build pointed at a **local** Worker then offers "Subscribe (test purchase)" which sends a `dev-premium-*` token that **only a DEV_MODE Worker** accepts (against the production Worker the card says "coming soon" instead, since prod would reject the token). Release builds and the real Worker cannot be fooled by it.
- **Verified on the OnePlus against the local Worker:** card "Rs 99 / month" -> test purchase -> `premium_verify active` -> "Welcome to Fixoo Premium", chip "Premium", Wallet "ACTIVE · Renews on November 6, 2026 · Manage subscription", coin earning hidden; then Fixoo at **0 coins answered** with nothing charged. Worker e2e has 4 premium checks (20/20 in paid mode).
- **Not verified - needs the user's Play Console work:** a real Play purchase, real verification/acknowledgement, renewal. Steps are in `worker/README.md` ("Fixoo Premium"). A real purchase can only be tested on a build installed from a Play testing track (license tester), never on the `.kmp` debug build.
- iOS: `available = false` (StoreKit needs the Apple account); the card says "coming soon" there.
- The earlier "coming soon only" premium card is gone; `premium_plan` flag still hides all premium UI.

**A 401 on wallet refresh re-registers immediately** (once) instead of just dropping the token - verified when the local Worker's data was wiped: `GET /v1/wallet 401` -> `register via device` -> `200`.

`worker/test/e2e.py` takes `FIXMONEY_WORKER=http://127.0.0.1:8788` so the suite can run on a second `wrangler dev` (with `--persist-to` and `--var` overrides) while the device's Worker keeps 8787.

**Fixed on device: two MainActivity instances.** Launching from the launcher while a task started by `am start` was running stacked a second MainActivity (seen in `dumpsys activity activities` as Hist #1 and #0), so Back surfaced the hidden instance's screen or left the app. `android:launchMode="singleTask"` + `onNewIntent` fixed it; re-checked with the same sequence - one instance, Back from Wallet returns to Home.

### Rewarded ads (Android only)

- play-services-ads 23.6.0 + UMP 3.1.0 (old app's versions); AdMob App ID in the manifest is the old app's. **Adding play-services-ads broke CameraX's compile** (`Cannot access class 'ListenableFuture'`): it pins `listenablefuture` to the empty `9999.0` stub that assumes Guava is present. Fixed by declaring `guava` in `composeApp` androidMain.
- **Debug builds always use Google's test rewarded unit** - the old app saw the OnePlus Nord's test-device hash change between sessions and a real ad served to the developer's phone (invalid traffic risks the AdMob account). Consequence: in debug the video plays but **no coins arrive** (the test unit has no SSV callback). Test crediting server-side.
- `REAL_REWARDED_UNIT` in `RewardedAds.android.kt` is **empty** until the unit is created in AdMob (with SSV pointed at the Worker); until then release builds report videos unsupported rather than serve test ads.
- iOS: `supported = false` - needs the Google Mobile Ads Swift package, an iOS AdMob app and an ATT decision, all blocked on the Apple account. iOS users after the trial therefore cannot earn coins yet.

### Coin pending work, second pass (2026-10-07)

Built and checked (`checkAll` green, Worker e2e trial + paid all PASS, new unit tests PASS). **Not run on a device** yet. **Worker deployed 2026-10-07 as version `54422fa4-9ebc-4667-bcd6-8fddd83f84d5`** (migration v2 / `IpGateDO`, SSV decode fix, `REWARDED_AD_UNITS`); prod smoke test: 404 / wallet 401 / register 200 / wallet 200 / legacy chat 200 / premium verify 502 `PLAY_UNAVAILABLE` (no service account yet, expected) / bad SSV rejected. Roll back to `916f899e` from the dashboard if needed.

- **Per-IP limit on new wallets** (`worker/src/ipgate.ts`, `IpGateDO`, migration `v2`). One Durable Object per `<network>:<UTC day>`; IPv6 counted per /64 after expanding `::`. Only creating a wallet counts - checked with `DeviceDO.accountId()` *before* `claim()`, so a known device or a valid token never touches it. Over the limit `/v1/register` answers `429 IP_LIMIT` and the app carries on token-less (the existing register-failure path). `NEW_ACCOUNTS_PER_IP` default **20** - loose because of carrier CGNAT; `"0"` disables. Not KV: KV is eventually consistent and its 1,000 free writes/day would be the real cap. Verified locally with `--var NEW_ACCOUNTS_PER_IP:3`: three new devices 200, the fourth 429, the first device again 200.
- **Proactive gate** - `WalletRepository.precheck(feature)`. Says no **only for lack of coins**, and only after a fresh `refresh()` confirms it (a cached 0 can be behind a reward that landed server-side); offline or unregistered it says yes and the Worker decides. Trial / Premium daily caps are deliberately not prechecked - their counters reset at midnight, so a cached count is usually stale. Used in two places: inside all three AI callers before `ticket()` (saves the round trip and the DATA-summary build) and, through `CoinsController.proceedIf`, **before the receipt camera opens** - the out-of-coins sheet comes up instead of after the user has framed a receipt. Voice is *not* gated before listening, because the local parser below makes simple phrases free even at 0 coins.
- **Local voice parsing** (`shared/voice/LocalVoiceParser.kt`, unit-tested in `shared/src/commonTest` - the project's first tests; run `./gradlew :shared:testDebugUnitTest`). One number + words that name a category (real names first, then a keyword list incl. Hinglish: chai, sabzi, auto, dawai...) is saved with **no AI call and no coin**. No number in any form -> `NoAmount` locally, which used to cost a coin for the model to say 0. Anything else -> the model as before: two numbers, decimals, number words ("one fifty", "do sau"), "and"/"split"/"yesterday", two categories, more than 4 words. A keyword maps only to a category that exists by that name. Bias is deliberate: a wrong local guess is a wrong ledger row, a needless model call is one coin.
- **Bubble opens the app on the out-of-coins sheet** (video +5 and "Or get Fixoo Premium") when refused for coins, and on the trial-limit sheet for `TRIAL_LIMIT` (`MainActivity` extra `open=out_of_coins` / `open=trial_limit`, `LaunchTarget`, common code). First built as "open the wallet"; the user asked for the subscription/earn options instead, so the sheet comes up over the current screen. Busy and Premium's own cap stay a toast. Every refusal sheet (incl. no-videos-left, videos unsupported, trial limit) now offers Premium when Play can sell it. Starting an activity from the background is allowed because the app holds "display over other apps". `App` waits until the route is past splash and onboarding is complete, navigates once and clears it; a restored activity (`savedInstanceState != null`) does not replay it.
- **Recent activity on the Wallet screen** - a local list (`coin_activity` in `prefs_onboarding`, newest 30, 10 shown) built from balance changes the app sees in `publish()`: a drop on an AI answer is labelled with the feature, a rise is "Video reward" (nothing else creates coins) or "Welcome bonus" when `welcomeBonusGranted` rides along, and a rise seen on an AI answer's headers is "Coins added" (headers cannot say which). Trial uses cost nothing and do not appear - the empty state says so. It is this phone's record, not an audit.

### Real rewarded unit + an SSV signature bug (2026-10-07)

- AdMob unit **"Fixoo Coins Rewarded"** = `ca-app-pub-7178541936408370/8131398118` (created through Chrome), reward 5 **`FixooCoins`**, SSV callback `https://fixmoney-ai-proxy.vikram8575229.workers.dev/v1/admob/ssv` verified in the console. Wired into `REAL_REWARDED_UNIT` (release builds only; debug still uses Google's test unit) and `REWARDED_AD_UNITS: "8131398118"`.
- **Bug found by the console's "Verify URL": Google signs the URL-*decoded* query** (its verifier uses `java.net.URI.getQuery()`), and `ssv.ts` checked the raw one. With reward item "Fixoo Coins" the ping arrived as `reward_item=Fixoo%20Coins` and failed with `bad signature` -> 400 (seen in `wrangler tail`; tail redacts the `signature` param). `ssv.ts` now verifies the decoded content and falls back to the raw one; tested locally by signing decoded content and sending it encoded (accepted) plus a tampered amount (rejected), and both e2e suites still pass. The fix was deployed in `54422fa4`; the reward item had already been set to `FixooCoins` (no space) - with nothing to decode, the production Worker verifies it. Keep the item free of characters that need encoding until the fix is live.

### Play setup for the first real purchase (2026-10-07, in progress)

- `versionCode` 13 / `versionName` 2.1; signed AAB `composeApp/build/outputs/bundle/release/composeApp-release.aab` (~40 MB), cert SHA-256 `8E:62:...:F7:17` = upload key, real rewarded unit in the dex. Too big for the Chrome tool's 10 MB upload limit - the user uploads it by hand.
- Play Console (developer `vikram8785`, account 7662596088003090473, app id 4974371973808517048): new email list **"Owner internal"** = vikram8575229@gmail.com + swanijai742@gmail.com (added at the user's request), selected as the **only** internal-testing tester list and as the **licence testers** (purchases by it are not charged). The other `tester*` lists belong to closed-testing people and were left alone.
- Google Cloud `expenseapp-7b500`: service account **`fixmoney-play-verify@expenseapp-7b500.iam.gserviceaccount.com`** created. **Not done (blocked for Claude, the user's to do):** accept the Google Play Android Developer API terms + enable it (`UREQ_TOS_NOT_ACCEPTED`), create its JSON key and `wrangler secret put PLAY_SERVICE_ACCOUNT`, invite it in Play Console Users and permissions, upload the AAB to internal testing, create `fixoo_premium_monthly`.

- **Internal testing release 13 (2.1) published 2026-10-07** ("Available to internal testers", opt-in link https://play.google.com/apps/internaltest/4701503854424410968). Release notes mention Coins, free simple voice add and Premium.
- `PLAY_SERVICE_ACCOUNT` secret set by the user (key created with gcloud, local copy deleted); API enabled and the SA invited by the user. First probe from the Worker: `subscriptionsv2 401 permissionDenied` - auth works, Play permission not yet propagated (can take up to 24h).
- **Premium is live in Play (2026-10-07):** merchant profile set up by the user (existing "Vikram Singh" payments profile, support email + statement name `FIXMONEY`); subscription `fixoo_premium_monthly` "Fixoo Premium", base plan **`monthly`**, auto-renewing, 7-day grace, **Active**, 175 countries priced from INR 99 by Play's conversion, **India set back to exactly INR 99** (Play charms 99 to 100 on bulk set - fix per country with the pencil). Worker probe after that: `subscriptionsv2 400 Invalid Value` for a fake token = **permission works**. Note: the Worker maps any Play error (incl. this 400 for a bad token) to 502 `PLAY_UNAVAILABLE`.
- **Production: 13 (2.1) sent for review at 100% full rollout on 2026-10-08 ~00:30 IST** (promoted from internal testing; 0 devices dropped; release notes carried over). Blocker fixed on the way: "Incomplete advertising ID declaration" - the AAB carries `com.google.android.gms.permission.AD_ID` (AdMob); the user declared Yes / Advertising or marketing. Google says reviews take up to 7 days. Also live: internal app sharing link for 2.1 (expires 6 Dec), "Owner internal" now also has swanijai742@gmail.com. Worker at that point: `dc40d63e`.
- **13 (2.1) is LIVE in production at 100% (approved 2026-10-08, 178 countries).** Play's release dashboard flags "DEX code optimisation below threshold - Obfuscation 1%, fix by Feb 2027": release builds have R8/minify off. Turning it on needs keep rules for Ktor, kotlinx.serialization, Room, Koin, Play Billing and AdMob, and a full device pass - do it as its own change, not in a hotfix.
- Store listing: first phone screenshot is now the voice banner (`~/Downloads/fixmoney_playstore_banner_1080x1920_fixed.jpg`, "100% private" replaced with "Works offline" - the original claim was false, AI calls send data to Groq), labelled AI-generated; old `00-cover.png` removed from the listing (still in the asset library). Submitted for review 2026-10-08.
- (was) **Blocker for Premium: the developer account has no Google Payments merchant account**, so the Subscriptions page will not open ("Missing requirements... set up a merchant account"). The user must set it up (legal/bank/tax details - not something Claude enters); only then can `fixoo_premium_monthly` be created.

### Known gaps

- `TrialState` (`shared/premium/`, from `feat/premium-subscription`) is a separate client-side trial that nothing uses; the coin trial is the Worker's. Left untouched.
- If the first response after the trial ends is an AI answer (not `/v1/wallet` or `/v1/register`), the welcome bonus arrives only as a header change, so the trial-ended sheet does not show and the history says "Coins added". The app refreshes the wallet on every launch, which normally gets there first.

## Planned, not built: Analytics, Siri quick-add (designed 2026-10-06)

Design doc: `docs/FixMoney_Analytics_Siri_Coins_System_Design.pdf` (Hinglish, the user's language). Its coin section predates the Durable Object / reserve-commit / no-login decisions above - where they differ, this file is current.

- **Analytics**: native Firebase SDKs (Android BOM, iOS via SPM in Xcode) behind a common `AnalyticsSink` interface that lives in composeApp - Kotlin never links Firebase, so the iosX64/klib gate does not apply. The old app had `firebase-analytics` but **zero custom `logEvent` calls**; the taxonomy is new.
- **Siri**: Apple requires the app name in every App Shortcut phrase and allows no free-text parameter inside one, so "just say 150 coffee" is impossible *through Siri*. The plan is shorter phrases plus a Back Tap / Action Button Shortcut (Dictate Text -> a new text intent), and a Settings toggle with a one-time in-app YouTube how-to. Siri capability / `requestSiriAuthorization` needs the paid Apple account.
- The real free-tier ceiling is Groq: ~30 requests and ~8k tokens a minute, 1,000 a day for the whole org (third-party sources - check console.groq.com).

## Repo

Private GitHub repo `vikram641/Fix-Money-KMP`, branch `main`. Commits use the global git identity (`vikram8575229@gmail.com`) — **do not override the author with `-c user.email=...`**; an early commit did and had to be rewritten so GitHub would link it to the profile.

## Theme wipe (2026-10-03)

`ui/ThemeReveal.kt` - the dark/light switch spreads out from the toggle instead
of snapping, like colour poured on at that point.

A theme change repaints everything at once, so there is **nothing left to
animate from**. The old screen is therefore photographed first: the live UI is
drawn through a `GraphicsLayer`, the last frame is read back on tap (still the
old theme, because the capture happens *before* the switch is applied), the
theme flips, and for the length of the animation that photograph is painted on
top with a growing circular hole at the tap point.

Nothing is re-laid-out, so content underneath cannot jump mid-wipe - the only
thing changing is how much of the old photo is left.

**Recording is off by default.** Routing the whole app through an offscreen
layer every frame to serve one switch inside Settings would tax scrolling
everywhere else, so `ArmThemeReveal()` turns it on only while Settings is on
screen. `LocalThemeReveal` is nullable and every failure path - not armed,
already mid-wipe, capture returned null - just applies the theme with no
animation. **A theme switch must never be lost to a missing snapshot.**

The radius grows to the farthest corner from the tap, not the nearest, or the
far corner finishes last and the wipe looks lopsided.

## One header height for every tab (2026-10-03)

`HEADER_BAND` in `DesignScaffold.kt` is the strip under the status bar. Home
spends it on `DesignAppBar`; Expenses, Analytics, Budget and Settings spend it
on their own title row via `ScreenTitleBand`. Both read the same value.

Those four screens used to start their title 2dp under the status bar, about
18dp higher than Home's app-bar title - too small to read as a design, big
enough to show as a jump on every tab change. Centring each title row in the
shared band fixes it **for any title size**, with no per-screen offset to drift.

A new screen's title goes in a `ScreenTitleBand`, never in a hand-padded Column.

## Onboarding and setup, rebuilt (2026-10-03)

Eight screens to the Stitch "obsidian dark" mockups: Welcome, three feature
slides, then name / budget / currency / all-set.

`ui/onboarding/OnboardingParts.kt` holds what they share - `GlowTile`,
`TagPill`, `StepDots`, `GlowButton`, `SetupHeader`, `SetupIconTile`.

**Glows are radial gradients, never `Modifier.blur`.** The first attempt stacked
concentric rounded rectangles of falling alpha and it banded visibly on device:
each ring drew its own hard edge, so the halo read as nested outlines rather
than light. `blur` would also work and is the one thing in these screens that
does not render identically on Android and iOS - not a trade worth making on the
first screen a user ever sees.

`GlowTile` is a solid purple slab with a white glyph, for when the icon is the
subject. `SetupIconTile` is a tinted plate with an accent glyph, for when it is
a label above a question. They are not interchangeable.

`formatIndian` lives in `OnboardingParts.kt`. It used to sit on the budget step
and moved when that screen was rebuilt - Edit Profile and the SIP calculator
import it, so it must stay in `com.fixmoney.ui.onboarding`.

There is **no `nd_chevron_left`** in the icon set; `SetupHeader` mirrors
`nd_chevron_right` with `scale(scaleX = -1f)`.

### What the mockups claim that the app does not do

Checked before changing anything: no bill, due-date or recurrence model in
`shared/`, no notification code anywhere, no encryption, no sync.
`budgetAlerts()` is read by its own Settings switch and by nothing else.

| Mockup said | Shipped instead | Why |
|---|---|---|
| Slide 2: recurring bills, auto-debit, calendar sync | Per-category budgets | No such model exists |
| Slide 3: push alerts before a bill is due | Ask Fixoo about your spending | Nothing notifies |
| Welcome: "Encrypted", "Zero-Knowledge Vault" | "On this device", "Works Offline" | Plain local Room DB |
| Welcome: "Auto Budget Caps" | "Category Budgets" | Nothing is automatic |
| Welcome: "ACTIVE PULSE" status dot | "EVERY RUPEE, TRACKED" | Implies an account and a connection |
| All set: "256-BIT ENCLAVE READY" / "SYNCED" | "STORED ON THIS DEVICE" / "OFFLINE" | Encrypts nothing, syncs nowhere |
| All set: "Automatic cycle" | "Resets each calendar month" | Fiscal-cycle start is still PREVIEW |

These are the first screens a new user sees - the worst place to promise
something absent. Both replacement slides are real on Android *and* iOS, which
ruled out voice quick-add (Android only). Restore a page the moment its feature
lands; `FeaturePage` takes any three.

The currency step keeps **all 14** currencies. The mockup crops to five because
that is what fits on one screen, not because the rest should go.

### Seeing these screens again

They only appear before onboarding completes. To re-check them, temporarily
force `Routes.WELCOME` in App.kt's splash hop, and **revert it before
installing again** - left in, the app opens on Welcome every launch. Walking the
flow is safe up to All Set; only "Start Tracking" calls `complete()` and writes
the profile. Back up first with
`adb shell run-as <pkg> tar -cf - shared_prefs databases`.

## Scale work before launch (2026-10-03)

Measured, not guessed. The app is offline-first with no per-user server, so
download count costs nothing - the ceilings were elsewhere.

### Firestore was the first wall, at roughly 3,000 DAU

Every cold start spent **8 document reads**: seven for `categories` (billing is
per document returned, and there are seven) plus one for `feature_flags`. The
free plan allows 50,000/day across the project, so ~6,250 cold starts - about
3,000 daily users at two launches each.

Two fixes, both about caching rather than quota:

- `CategoryRepository` cached **in memory only**, so the cache died with the
  process and every cold start refetched. It now persists to `Settings` with a
  24h TTL, and falls back to a *stale* copy before the seeded defaults - a stale
  real list still carries edits the defaults never had.

  `Category` had to gain `@Serializable` for this. It compiled without it,
  because `encodeToString` resolves the serializer at **runtime** - the write
  threw `SerializationException` on device and the catch swallowed it, so the
  cache silently never existed while everything appeared to work. Worth
  remembering: a green build says nothing about a serializer being present.
- `FeatureFlagRepository.refresh()` ran on every launch. It now skips the fetch
  if the last one was under 6h ago; `refresh(force = true)` bypasses that. The
  persisted values carry the app between fetches, so a skipped fetch costs
  nothing.

Together these cut steady-state reads to near zero and move the wall out by
roughly an order of magnitude - on the free plan.

Verified on device: a second cold start leaves `categories_cached_at` unchanged,
i.e. the list is served from disk and Firestore is never called.

**The earlier "~3,000 DAU" figure was too pessimistic.** It assumed 8 reads per
cold start, but `getCategories()` runs only from Add Expense, Budget, onboarding
and the scan/voice engines - never from Home. A launch that only opens Home was
always 1 read, not 8.

### Destructive migration was a loaded gun

`buildDatabase` used `fallbackToDestructiveMigration(dropAllTables = true)` with
a comment saying to revisit it before the first upload. Adding an index bumps
the schema, which would have **deleted every expense on every install** - and
this app holds the only copy, there is no backend to restore from.

Replaced with real migrations (`Migrations.kt`, `ALL_MIGRATIONS`). A missing
migration now throws instead: a crash in development is a bug report, a wiped
ledger is a one-star review.

**Every schema change from here needs a migration.** Index names must match
Room's generated `index_<table>_<column>` or validation fails on open.

### Home read the whole table on every change

`observeExpenses()` was `SELECT * FROM expenses` with no index anywhere, re-read
and re-derived on every insert. Fine at a few hundred rows; a multi-year user
with tens of thousands paid for all of them on every edit.

Now `observeExpensesBetween(from, to)` over a `LOOKBACK_DAYS = 45` window before
the selected month - far enough back for the insight baseline, which ends a week
ago and runs three weeks back, so on the 1st it reaches 27 days into the
previous month. That was the original reason for reading the table whole; the
window keeps the fix without the cost.

The query carries **no `ORDER BY`**: `build()` re-sorts for its Recent list and
otherwise only sums, groups and range-filters, so ordering forced a temp B-tree
for nothing. Verified against a copy of a real device database:

```
before: SCAN expenses
after : SEARCH expenses USING INDEX index_expenses_date (date>? AND date<?)
```

### Where the ceilings are now

| Layer | Limit |
|---|---|
| App core (offline) | no per-user cost; downloads are free to serve |
| Worker proxy | 100k req/day free, then ~$5/mo per 10M |
| Firestore | was ~3,000 DAU; now bounded by cache TTLs, not launches |
| AI calls | scales with *actions*, not users; paid Groq is the cost driver |
| Workers KV | 1,000 writes/day free - if the per-IP limiter is enabled it caps there; use Durable Objects or the rate-limiting binding at scale |
| Per-device data | indexed and windowed; no longer tied to ledger lifetime |


## QA pass + fixes for the next release (2026-10-11)

Full report: `~/Downloads/FixMoney_Bug_Report_2026-10-11.md`. The fixes are **uncommitted on `feat/ai-coins-wallet`**, and the versionCode is still 13, so bump it to 14 before uploading.

**C1, the one that matters most: every v10 → 2.1 updater crash-looped.** `LegacyImport` copied `expense.db` with the old app's `room_master_table` hash (`ac07dbd5…`), and Room 2.7 `checkIdentity` threw on every launch. Reproduced on the device with a DB built from the old `ExpenseDatabase_Impl` SQL. Fixed in two places:
- The import drops `room_master_table`, so Room validates the schema and writes its own hash.
- `repairLegacyIdentity()` drops it from an already-imported file that still carries the legacy hash. That rescues users who are stuck right now.

Both paths are verified on the device. `pending_expenses` (the old offline queue) is imported too. **Ship this as a hotfix.** Check Play vitals for "cannot verify the data integrity".

Decisions worth knowing:
- `parseStoredAmount()` / `ExpenseEntity.amountValue` (`shared/db/ExpenseAmount.kt`) is the only way to read an amount. Never use `amount.toIntOrNull()`: old rows hold "12.50" and "₹1,250".
- `MoneyFormat.currencyCode` (OnboardingParts.kt), set in App.kt, drives `formatIndian` and `compactAmount`. Lakh/crore only for INR, PKR, BDT, NPR and LKR.
- AI JSON is decoded through `LenientAiJson` + `extractJsonObject` + `LenientIntSerializer`. The coin is already charged at that point, so a strict decode wasted it.
- Fixoo's DATA block is scoped by month (this month plus last month, per currency). History is paired in order and capped at 6 turns.
- The four tabs use `rememberToday()` / `rememberFollowingMonth()` (`ui/components/Today.kt`), so a midnight rollover refreshes them.
- Navigation goes through `go(route)` (`launchSingleTop`), and `goBack` only pops when there is a previous entry.
- Worker `premiumToRecheck` is throttled with `checkedAt`. **Not deployed.**

Second pass, 2026-10-12, all fixed:
- **Totals count only the profile's currency.** Use `ExpenseEntity.isIn()` / `inCurrency()`. Never sum rows of different currencies.
- **Money formatting moved to `shared/model/MoneyFormat.kt`.** composeApp's `formatIndian` delegates to it.
- **SIP maths moved to `shared/calc/SipMath.kt`.**
- **CSV builder is top-level `buildExpenseCsv` / `csvField`.**
- **The state factories take `todayOverride`** so tests can use a fixed date.
- **Worker `PremiumTokenDO` (migration v3)** binds one account per Play token. **Not deployed.**
- **Analytics scope tabs are in the pinned `sticky` header.**

**UI tweaks (2026-10-12):** the app-bar bell is hidden behind `SHOW_NOTIFICATION_BELL = false` in `DesignScaffold.kt` (kept for future notifications). "FIXOO INSIGHT" (not Copilot) on Analytics. The scanner chip says "FIXOO RECEIPT SCAN". The Settings footer says "FixMoney v<version>". The scanner shutter now sits at bottom 22dp and `FRAME_HEIGHT_FRACTION` is 0.48, so the hint card no longer overlaps the shutter or frame.

**Profile avatars (2026-10-12):** 9 claymation avatars from Stitch `stitch_fixmoney_fintech_app_design 10`, cropped to 256px WebP as `drawable/avatar_1..9`. `ui/settings/Avatars.kt` holds:
- `ProfileAvatarOption`: the ids are stored, so **never reorder them**.
- `CurrentAvatar`: app-wide state, so Home updates the moment an avatar is applied.
- `ProfileAvatar`: falls back to the initial when none is picked.
- `AvatarPickerSheet`: a simplified version of the `(20)` mockup. Preview, 3-column grid, Apply / Cancel; the "Claymation Studio" and verified-badge extras are dropped at the user's request.

The choice is saved in `AppPrefs.avatarId()` (`profile_avatar`; 0 = initial). It shows in the Home app bar, the Settings card and Edit Profile (tap the avatar or its pencil badge). The dashboard tab is now `rememberSaveable`, so Back from Edit Profile / SIP returns to Settings instead of Home.

**Portrait only (2026-10-12):** `MainActivity` has `android:screenOrientation="portrait"`; iOS Info.plist was already portrait-only. Checked on the device: the activity reports `SCREEN_ORIENTATION_PORTRAIT` with auto-rotate on.

**Calculation tests:** 43 in `shared/src/commonTest` (fakes in `testutil/Fakes.kt`). Run with `./gradlew :shared:testDebugUnitTest`.

## Known bugs in live 2.1: fix in the next release (found 2026-10-11 while capturing store screenshots)

Plan agreed with the user: finish the store screenshots first, then a proper test pass, fix everything below, then release.

1. **Fixoo answers "this month" questions with all-time data.** `shared/.../ai/ChatEngine.kt:21` builds the prompt from `expenses.getExpensesOnce()`: every row ever recorded, totals and per-category sums with no month scoping or labels. Seen: with Sep + Oct demo data, "How much did I spend on food this month?" → 12460 (Sep+Oct) instead of 5600 (Oct). Fix: scope to the current month (and say so in the prompt), and pass earlier months as labelled context if comparisons are needed.
2. **Receipt scanner ignores the receipt's currency.** An Indian petrol bill reading "AMOUNT: 2598.61 INR", scanned with the app currency set to USD, became `$2,599`. Fix: detect the currency on the receipt; if it differs from the app currency, warn or convert rather than silently relabel.
3. **Voice amount parsing, possible "for" → "4" merge.** "T-shirt" voice adds produced 5004 three times across two sessions (₹5004 twice, then $5004; 500 expected), while other attempts gave the correct 500. Likely "500 for T-shirt" transcribed as "500 4". Reproducible, but the exact spoken phrase is still unconfirmed. Reproduce before fixing (check how `VoiceExpenseParser` / the Groq prompt treats split number tokens).
4. **Settings footer says "FixMoney • v2.0 preview"** while the shipped versionName is 2.1. Read the version from the build instead of hardcoding it.
5. **Fixoo repeats itself.** When a second question was asked, the answer reused the previous answer's opening sentence ("You spent 5600 on food this month.") above an unrelated breakdown. Check how history is passed in `ChatEngine.ask`.

## Fixoo orb logo (2026-10-12)
- The Fixoo AI logo is the animated orb `files/lottie/fixoo_orb.json` in both places:
  - Home "Ask Fixoo" button (HomeScreen.kt, `FeatureFlag.FixooChat`): 40dp tap area with a 32dp orb, clipped to a circle, opens `onAskAi`. It replaces the old `nd_sparkle_duo` icon, tinted circle and live dot.
  - Chat header avatar (ChatScreen.kt): 42dp, clipped to a circle. It replaces `ai.json`, which is now unused.
- Source: Stitch export folder (22), a WebGL glass-orb shader. Rendered in headless Chrome with the shader's outer **aura removed** (alpha only on the orb body), because the aura showed as a faint grey halo or square on the light theme.
- 90 frames are crossfaded into a seamless 72-frame loop and cropped to the orb's bounds. They are packed as an image-sequence Lottie: 160px WebP data URIs, 24fps, about 440 KB. Compottie renders the embedded WebP fine (verified on the device).
- Build script: `scratchpad/build_orb.py`, which is temporary. To regenerate, re-render the shader: a vector Lottie can't reproduce it.


## Release 14 / 2.2 (built 2026-10-12, not yet submitted)
- `versionCode` 14, `versionName` 2.2. It is the hotfix for the v10 → 2.1 Room identity crash loop, plus the whole QA bug pass, per-currency totals, avatars, the Fixoo orb and portrait lock.
- Signed AAB: `composeApp/build/outputs/bundle/release/composeApp-release.aab` (~40 MB). Cert SHA-256 is `8E:62:...:F7:17`, the upload key. A copy plus the 21-language "What's new" text is in `~/Downloads/FixMoney_v2.2_release/`.
- The user uploads the AAB by hand (it's over the Chrome tool's 10 MB limit) and submits it together with the pending listing and screenshot changes.
- The release build was **not smoke-tested on the device**. The Play-signed install on the phone blocks `adb install` of a locally signed build. After rollout, check the Play-served build ([[verify against distributed artifact]]): first launch after the update from v10, Home orb animation (Compottie WebP under R8), voice add, receipt scan.
- The Worker changes (premium recheck throttle, `PremiumTokenDO` migration v3) are committed but **not deployed**. The app doesn't depend on them.
