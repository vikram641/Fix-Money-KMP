plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

/**
 * One command that proves a change still works on both platforms.
 *
 * Android is what gets run day to day; this makes the iOS check cheap enough
 * (~20s when warm) that it never gets skipped. iosX64 is the target that
 * matters here - this is an Intel Mac, so that is what the simulator runs.
 */
tasks.register("checkAll") {
    group = "verification"
    description = "Compile for Android and iOS (x64) - run before every commit"
    dependsOn(
        ":composeApp:assembleDebug",
        ":composeApp:compileKotlinIosX64",
        ":shared:compileKotlinIosX64"
    )
}
