import java.util.Properties
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

/**
 * KMP has no BuildConfig, so the Groq key is generated into a source file from
 * local.properties (gitignored) instead of being committed.
 */
val generateSecrets by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/secrets")
    outputs.dir(outputDir)
    val key = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }.getProperty("GROQ_API_KEY", "")
    doLast {
        val dir = outputDir.get().asFile.resolve("com/fixmoney/shared/ai")
        dir.mkdirs()
        dir.resolve("Secrets.kt").writeText(
            """
            package com.fixmoney.shared.ai

            /** GENERATED from local.properties - do not edit. */
            internal object Secrets {
                const val GROQ_API_KEY: String = "$key"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateSecrets)
}

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
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
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
