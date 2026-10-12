package com.fixmoney.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.config.FeatureFlagRepository

/**
 * Whether a feature is switched on, for the composition.
 *
 * Provided once at the root from [FeatureFlagRepository]'s flow, so a refresh
 * that lands after launch re-composes whatever depends on it rather than
 * needing a restart.
 *
 * The default is **every flag on**. A screen reached before the provider exists
 * - or in a preview - behaves as if the feature is present, which is the same
 * fail-open rule the repository applies.
 */
val LocalFeatureFlags = staticCompositionLocalOf { FeatureFlag.entries.associateWith { true } }

/** `if (isEnabled(FeatureFlag.ExportData)) { … }` inside any composable. */
@Composable
fun isEnabled(flag: FeatureFlag): Boolean = LocalFeatureFlags.current[flag] ?: true

/** Renders [content] only while [flag] is on. */
@Composable
fun FeatureGate(flag: FeatureFlag, content: @Composable () -> Unit) {
    if (isEnabled(flag)) content()
}

/** Reads the repository's flow for [LocalFeatureFlags]. */
@Composable
fun rememberFeatureFlags(repository: FeatureFlagRepository): Map<FeatureFlag, Boolean> {
    val flags by repository.flags.collectAsStateWithLifecycle(repository.flags.value)
    return flags
}
