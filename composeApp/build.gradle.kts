import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

/**
 * All UI, as Compose Multiplatform composables shared between Android and iOS,
 * plus each platform's thin entry point (MainActivity / MainViewController).
 * The iOS framework produced here is what iosApp/ links against.
 */
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.compottie)
            implementation(libs.compottie.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            // The in-app receipt camera. Android only - iOS uses the system
            // image picker, see ReceiptCapture.ios.kt.
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
            // AI coins: rewarded videos are how coins are earned.
            implementation(libs.play.services.ads)
            implementation(libs.user.messaging.platform)
            // See libs.versions.toml: keeps CameraX's ListenableFuture visible
            // once play-services-ads has stubbed it out.
            implementation(libs.guava)
            // Fixoo Premium subscription - see libs.versions.toml.
            implementation(libs.play.billing)
        }
    }
}

// Same keystore the current Play Store app is signed with
// (~/Downloads/Expense/keystore.properties -> /Users/apple/keystores/expense-release.jks).
// Play rejects an update signed with any other key, so this is not optional.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Without this the generated class lands in `fix_money_kmp.composeapp.
// generated.resources`, derived from the root project name.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.fixmoney.resources"
}

android {
    namespace = "com.fixmoney"
    compileSdk = libs.versions.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    // Debug-only overlay: cleartext to a local `wrangler dev` Worker over
    // `adb reverse`. See src/androidDebug/AndroidManifest.xml.
    sourceSets["debug"].manifest.srcFile("src/androidDebug/AndroidManifest.xml")
    sourceSets["debug"].res.srcDirs("src/androidDebug/res")

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        // Same applicationId as the live Play Store listing, so this rewrite
        // ships as an update to the existing app rather than a new one.
        applicationId = "com.vikram641.expensetracker"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Play has versionCode 10 (1.4.1) live. 11 was uploaded to a draft
        // release and then superseded when the foreground service moved from
        // `specialUse` to `microphone`; Play will not take a version code twice,
        // even from a discarded draft. 12 is on Play too (installed from Play
        // 2026-10-06); 13 adds Fixoo Coins, real rewarded ads and Premium.
        versionCode = 13
        versionName = "2.1"
    }

    buildTypes {
        debug {
            // Installs alongside the real FixMoney app on a test device instead
            // of replacing it, so old and new can be compared side by side.
            // Release keeps the exact applicationId - only debug is suffixed.
            applicationIdSuffix = ".kmp"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // AGP 8.7.3 ships a lint build older than Kotlin 2.2.0, and the
        // androidx.lifecycle detector crashes on 2.2.0 metadata with
        // "Found class KaCallableMemberCall, but interface was expected",
        // failing every release build. Drop this once AGP is bumped.
        disable += "NullSafeMutableLiveData"
    }
}
