import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

/*
 * The Groq key used to be generated into a source file here and read as
 * `Secrets.GROQ_API_KEY`. A compile-time constant lands in the dex, so the key
 * shipped inside every APK and `strings` pulled it straight back out - checked
 * on a real build.
 *
 * It now lives on the Cloudflare Worker in `worker/`, which the app calls
 * instead. Nothing in the app carries a credential, so there is nothing to
 * generate. Do not reintroduce this pattern for the next key: put it behind the
 * Worker.
 */

/**
 * Business logic only - no UI. ViewModels, repositories, networking, and
 * (later) the Room database live here. Compiles for Android and iOS.
 */
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iosX64 = Intel simulator; kept so the module still builds on an Intel Mac.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.multiplatform.settings)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Receipt OCR. The iOS actual uses the Vision framework, which is
            // part of the OS and needs no dependency at all.
            implementation(libs.mlkit.text.recognition)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("com.russhwolf:multiplatform-settings-test:1.3.0")
        }
    }
}

// Room's compiler has to run for every target, not just Android.
dependencies {
    listOf(
        "kspAndroid",
        "kspIosX64",
        "kspIosArm64",
        "kspIosSimulatorArm64"
    ).forEach { add(it, libs.room.compiler) }
}

android {
    namespace = "com.fixmoney.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
