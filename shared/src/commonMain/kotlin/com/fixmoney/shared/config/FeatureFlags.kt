package com.fixmoney.shared.config

import com.fixmoney.shared.data.FirebaseConfig
import com.fixmoney.shared.platform.currentTimeMillis
import com.fixmoney.shared.platform.logWarn
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "FeatureFlags"

/**
 * A feature that can be switched off remotely.
 *
 * [key] is the **field name in the Firestore document** - change it and the
 * flag silently reverts to its default, so treat these as a published contract.
 *
 * Everything defaults to **on**: a flag that has never been published, a failed
 * fetch and a first launch must all behave like the feature simply exists. The
 * remote document is how a feature gets *taken away*, never how it gets turned
 * on - that way a Firestore outage can never blank out the app.
 */
enum class FeatureFlag(val key: String) {
    /** The Android floating voice bubble, and its Settings row. */
    VoiceQuickAdd("voice_quick_add"),

    /** Scan Receipt: the header button on Add Expense and the scanner route. */
    ReceiptScan("receipt_scan"),

    /** Export Data: the Settings row and the Expenses footer link. */
    ExportData("export_data"),

    /** The SIP calculator screen and its Settings row. */
    SipCalculator("sip_calculator"),

    /** Fixoo: the Home AI button, Ask Fixoo links and the chat route. */
    FixooChat("fixoo_chat"),

    /** The computed insight cards on Home and Analytics. */
    AiInsights("ai_insights"),

    /** Budget alerts and its Settings row. */
    BudgetAlerts("budget_alerts"),

    /** The whole Analytics tab. */
    AnalyticsTab("analytics_tab"),

    /** The whole Budget tab. */
    BudgetTab("budget_tab"),

    /**
     * AI coins *UI*: the balance chip, the wallet screen and its Settings row.
     * Only hides the display - charging is decided by the Worker, so turning
     * coins off for real means setting the costs to 0 in `worker/wrangler.jsonc`.
     */
    CoinWallet("coin_wallet"),

    /**
     * The "Fixoo Premium - coming soon" card on the trial-ended sheet. Display
     * only; there is no billing yet (that is `feat/premium-subscription`).
     */
    PremiumPlan("premium_plan"),

    /* --------------------------------------------------- Settings sections
     * A section also disappears on its own once every row inside it is
     * hidden, so these are only needed to drop a whole group in one switch
     * while its rows stay individually available.
     */

    /** The AI & Intelligence section of Settings. */
    SectionAi("section_ai"),

    /** The Connected Accounts section of Settings. */
    SectionConnectedAccounts("section_connected_accounts"),

    /** The Preferences & Currency section of Settings. */
    SectionPreferences("section_preferences"),

    /** The Appearance & Haptics section of Settings. */
    SectionAppearance("section_appearance"),

    /** The Tools & Exports section of Settings. */
    SectionTools("section_tools"),

    /* ------------------------------------------------------- Settings rows */

    /** Fixoo Autonomous Guard. */
    AutonomousGuard("autonomous_guard"),

    /** Insight Frequency. */
    InsightFrequency("insight_frequency"),

    /** Neural Categorization. */
    NeuralCategorization("neural_categorization"),

    /** Base Currency. Hiding it does not change the currency, only the row. */
    BaseCurrency("base_currency"),

    /** Fiscal Cycle Start. */
    FiscalCycleStart("fiscal_cycle_start"),

    /** Smart Cap Rollover. */
    SmartCapRollover("smart_cap_rollover"),

    /** Biometric Shield. */
    BiometricShield("biometric_shield"),

    /**
     * The Obsidian Pulse row - the dark-theme switch, not the theme. Hiding it
     * leaves the app on whatever theme was last chosen.
     */
    DarkThemeToggle("dark_theme_toggle"),

    /** Frosted Glass & Ambient Glows. */
    FrostedGlass("frosted_glass"),

    /** Tactile Haptics. */
    TactileHaptics("tactile_haptics")
}

/**
 * Remote feature switches, read from a single Firestore document over the REST
 * API - the same transport `CategoryRepository` uses, for the same reasons: no
 * SDK, identical on both platforms, and no `iosX64` / Kotlin-version gate.
 *
 * **Firebase Remote Config would be the obvious product for this and is not
 * usable here**: its SDK is Android/iOS-native with no KMP build, which is the
 * same wall every Firebase library hits in this project. A plain document gives
 * the one thing actually needed - a value that can be changed without a release.
 *
 * Document: `feature_flags/app`. Each flag is one field, `0`/`false` to hide and
 * `1`/`true` to show. A field that is absent means **on**.
 *
 * ```
 * feature_flags (collection)
 *   └── app (document)
 *         voice_quick_add: 1
 *         export_data: 1
 *         …
 * ```
 *
 * **The rule has to allow it.** `categories` is world-readable by an explicit
 * rule; this document needs its own:
 * ```
 * match /feature_flags/{doc} { allow read: if true; allow write: if false; }
 * ```
 * Without it the fetch 403s, every flag falls back to its last known value, and
 * failing that to on - so the app still works, it just stops being steerable.
 *
 * The last successful fetch is persisted, so a cold start offline keeps whatever
 * was last published rather than snapping everything back on.
 */
class FeatureFlagRepository(
    private val client: HttpClient,
    private val settings: Settings
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _flags = MutableStateFlow(loadPersisted())

    /** Current values. Emits again once a refresh lands, so UI can react. */
    val flags: StateFlow<Map<FeatureFlag, Boolean>> = _flags.asStateFlow()

    fun isEnabled(flag: FeatureFlag): Boolean = _flags.value[flag] ?: true

    /**
     * Fetches once. Failure is not an error state: the previous values stay,
     * and the caller does not need to care.
     */
    /**
     * [force] skips the interval - for a pull-to-refresh or a debug action.
     *
     * Called once per launch, this was one Firestore read every time the app
     * cold-started. The free plan allows 50,000 reads a day across the whole
     * project, and flags change rarely, so an interval trades a few hours of
     * staleness for roughly an order of magnitude more launches before the
     * project's quota is the thing that stops working. The persisted values
     * carry the app between fetches, so skipping one costs nothing.
     */
    suspend fun refresh(force: Boolean = false) {
        if (!force) {
            val age = currentTimeMillis() - settings.getLong(FETCHED_AT, 0L)
            // A clock moved backwards leaves a negative age; refetch rather than
            // trust it until the clock catches up.
            if (age in 0 until REFRESH_INTERVAL_MILLIS) return
        }
        val fetched = fetch() ?: return
        _flags.value = fetched
        persist(fetched)
        settings.putLong(FETCHED_AT, currentTimeMillis())
    }

    private suspend fun fetch(): Map<FeatureFlag, Boolean>? = try {
        val url = "https://firestore.googleapis.com/v1/projects/" +
            "${FirebaseConfig.PROJECT_ID}/databases/(default)/documents/" +
            "$COLLECTION/$DOCUMENT?key=${FirebaseConfig.API_KEY}"

        val response = client.get(url)
        val body = response.bodyAsText()

        if (!response.status.isSuccess()) {
            logWarn(TAG, "Firestore returned ${response.status.value}: $body")
            null
        } else {
            val fields = json.decodeFromString<FlagDocument>(body).fields.orEmpty()
            FeatureFlag.entries.associateWith { flag ->
                fields[flag.key]?.toBoolean() ?: true
            }
        }
    } catch (e: Exception) {
        logWarn(TAG, "Feature flag fetch failed: ${e.message}")
        null
    }

    private fun loadPersisted(): Map<FeatureFlag, Boolean> =
        FeatureFlag.entries.associateWith { flag ->
            settings.getBoolean(storageKey(flag), true)
        }

    private fun persist(values: Map<FeatureFlag, Boolean>) {
        values.forEach { (flag, enabled) ->
            settings.putBoolean(storageKey(flag), enabled)
        }
    }

    private fun storageKey(flag: FeatureFlag) = "flag_${flag.key}"

    private companion object {
        const val COLLECTION = "feature_flags"
        const val DOCUMENT = "app"
        const val FETCHED_AT = "feature_flags_fetched_at"

        /** Flags are a kill switch, not live config; hours of lag is fine. */
        const val REFRESH_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
    }
}

/* ------------------------------------------------------- Firestore shapes */

@Serializable
private data class FlagDocument(val fields: Map<String, FlagValue>? = null)

/**
 * Firestore wraps every field in its type. A flag may reasonably be published
 * as a number (`1`), a boolean (`true`) or - if someone edits it by hand in the
 * console - a string, so all three are accepted.
 */
@Serializable
private data class FlagValue(
    val integerValue: String? = null,
    val booleanValue: Boolean? = null,
    val stringValue: String? = null
) {
    fun toBoolean(): Boolean = when {
        booleanValue != null -> booleanValue
        integerValue != null -> integerValue != "0"
        stringValue != null -> stringValue.lowercase() !in OFF
        else -> true
    }

    private companion object {
        val OFF = setOf("0", "false", "off", "no")
    }
}
