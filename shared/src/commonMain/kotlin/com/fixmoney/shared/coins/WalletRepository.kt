package com.fixmoney.shared.coins

import com.fixmoney.shared.ai.AiProxy
import com.fixmoney.shared.platform.logWarn
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val TAG = "WalletRepository"

/** One AI call's credentials: the device token plus a request id the Worker de-duplicates on. */
class AiTicket internal constructor(
    val feature: AiFeature,
    internal val token: String,
    val requestId: String
) {
    fun applyTo(builder: HttpRequestBuilder) {
        builder.header("Authorization", "Bearer $token")
        builder.header("X-Feature", feature.wire)
        builder.header("X-Request-Id", requestId)
    }
}

/**
 * The app's side of AI coins: registration, the cached balance, and reading the
 * coin outcome off every AI response.
 *
 * **There is no login.** On first use the app posts its [DeviceKeySource] key to
 * `/v1/register` and gets back a device token, kept in [settings] - which is the
 * `prefs_onboarding` SharedPreferences file on Android, so Android Auto Backup
 * (`allowBackup="true"`) carries it to a new phone and the wallet follows.
 *
 * **Everything degrades to the old uncharged path.** If registration fails -
 * offline, or a Worker that predates coins answers 404 - [ticket] returns null,
 * the caller sends no token, and the Worker treats the call exactly as before
 * coins existed (while its `REQUIRE_AUTH` is off). Coins are never the reason
 * an AI feature stops working outright.
 */
class WalletRepository(
    private val client: HttpClient,
    private val settings: Settings,
    private val deviceKeys: DeviceKeySource
) {
    private companion object {
        const val KEY_TOKEN = "coin_token"
        const val KEY_ACCOUNT = "coin_account_id"
        const val KEY_CACHE = "coin_wallet_cache"
        const val KEY_ACTIVITY = "coin_activity"
        const val ACTIVITY_KEEP = 30
        /** After a failed registration, don't hammer the Worker on every AI call. */
        const val REGISTER_BACKOFF_MS = 60_000L
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private var lastRegisterFailure = 0L

    private val _wallet = MutableStateFlow(readCache())
    /** Last balance seen, persisted so the chip shows a number before the first network call. */
    val wallet: StateFlow<WalletInfo?> = _wallet.asStateFlow()

    private val _events = MutableSharedFlow<WalletEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WalletEvent> = _events.asSharedFlow()

    private val _activity = MutableStateFlow(readActivity())
    /** Newest first. See [CoinActivity] for what it can and cannot contain. */
    val activity: StateFlow<List<CoinActivity>> = _activity.asStateFlow()

    /** The id AdMob must report back as the SSV `user_id`, or null before registration. */
    val accountId: String? get() = settings.getStringOrNull(KEY_ACCOUNT)

    /** The stored token, registering first if there is none. Null when the Worker is unreachable. */
    suspend fun ensureRegistered(): String? = lock.withLock {
        settings.getStringOrNull(KEY_TOKEN) ?: registerLocked(previousToken = null)
    }

    private suspend fun registerLocked(previousToken: String?): String? {
        val now = com.fixmoney.shared.platform.currentTimeMillis()
        if (now - lastRegisterFailure < REGISTER_BACKOFF_MS) return null
        return try {
            val response = client.post(AiProxy.REGISTER_URL) {
                contentType(ContentType.Application.Json)
                previousToken?.let { header("Authorization", "Bearer $it") }
                setBody(json.encodeToString(RegisterRequest.serializer(), RegisterRequest(deviceKeys.deviceKey(), platformName)))
            }
            val text = response.bodyAsText()
            if (response.status.value != 200) {
                logWarn(TAG, "register failed: ${response.status.value} $text")
                lastRegisterFailure = now
                return null
            }
            val body = json.decodeFromString(RegisterResponse.serializer(), text)
            settings.putString(KEY_TOKEN, body.token)
            settings.putString(KEY_ACCOUNT, body.accountId)
            publish(body.wallet)
            body.token
        } catch (e: Exception) {
            logWarn(TAG, "register failed: ${e.message}")
            lastRegisterFailure = now
            null
        }
    }

    /**
     * Re-reads the balance. Safe to call often; does nothing useful offline.
     *
     * A 401 means the Worker no longer accepts the token (its secret was
     * rotated, or the wallet is gone). The token is dropped and the app
     * re-registers straight away by device key - which lands on the same wallet
     * when there is one - instead of showing a stale balance until the next AI
     * call happens to register.
     */
    suspend fun refresh(): WalletInfo? = refresh(retryOn401 = true)

    private suspend fun refresh(retryOn401: Boolean): WalletInfo? {
        val token = ensureRegistered() ?: return null
        return try {
            val response = client.get(AiProxy.WALLET_URL) { header("Authorization", "Bearer $token") }
            when (response.status.value) {
                200 -> json.decodeFromString(WalletResponse.serializer(), response.bodyAsText()).wallet
                    .also { publish(it) }
                401 -> {
                    forgetToken()
                    if (retryOn401) refresh(retryOn401 = false) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            logWarn(TAG, "wallet refresh failed: ${e.message}")
            null
        }
    }

    /** Credentials for one AI call, or null to make it on the old uncharged path. */
    suspend fun ticket(feature: AiFeature): AiTicket? {
        val token = ensureRegistered() ?: return null
        return AiTicket(feature, token, newRequestId())
    }

    /**
     * Answers "will the Worker refuse this?" *before* the user puts effort in -
     * before the camera opens for a scan, before a question goes out.
     *
     * Only ever says no for lack of coins, and only after re-reading the wallet:
     * the cached balance can be behind (a video reward lands server-side while
     * the app is not looking), and a stale "0" must never block a user who has
     * coins. Offline or unregistered it says yes and lets the Worker decide, as
     * it always does. The trial's and Premium's daily caps are not checked here
     * - their counters reset at midnight, so a cached count is often stale.
     */
    suspend fun precheck(feature: AiFeature): AiGate {
        if (shortOfCoins(_wallet.value, feature) == AiGate.Passed) return AiGate.Passed
        val fresh = refresh() ?: return AiGate.Passed
        return shortOfCoins(fresh, feature)
    }

    private fun shortOfCoins(w: WalletInfo?, feature: AiFeature): AiGate = when {
        w == null -> AiGate.Passed
        w.premiumActive -> AiGate.Passed
        w.trialActive -> AiGate.Passed
        w.available < w.cost(feature) -> AiGate.NoCoins
        else -> AiGate.Passed
    }

    /**
     * Reads the coin outcome off an AI response and updates the balance from its
     * headers. Must be called before the caller treats a non-2xx as a generic
     * failure, since 402 / 429-trial / 503 each mean something specific.
     */
    suspend fun absorb(ticket: AiTicket?, response: HttpResponse): AiGate {
        if (ticket == null) return AiGate.Passed
        applyHeaders(response, ticket.feature.label)
        val status = response.status.value
        if (status in 200..299) return AiGate.Passed
        if (status == 401) {
            forgetToken()
            return AiGate.Passed // treated as an ordinary failure by the caller
        }
        val err = try {
            json.decodeFromString(WorkerError.serializer(), response.bodyAsText())
        } catch (_: Exception) {
            null
        }
        err?.wallet?.let { publish(it) }
        return when (err?.code) {
            "NO_COINS" -> AiGate.NoCoins
            "TRIAL_LIMIT" -> AiGate.TrialLimit
            "AI_BUSY" -> AiGate.Busy
            "PREMIUM_LIMIT" -> AiGate.PremiumLimit
            else -> AiGate.Passed
        }
    }

    /**
     * After a rewarded video: waits for AdMob's server-side callback to reach the
     * Worker and the balance to move past [coinsBefore]. The callback usually
     * lands within seconds; this gives up after about half a minute and the
     * coins simply appear on a later refresh.
     */
    suspend fun awaitReward(coinsBefore: Int): Boolean {
        for (waitMs in longArrayOf(1_500, 2_000, 3_000, 4_000, 6_000, 8_000, 10_000)) {
            delay(waitMs)
            val now = refresh() ?: continue
            if (now.coins > coinsBefore) return true
        }
        return false
    }

    /** Result of handing a Google Play purchase to the Worker. */
    sealed interface PremiumResult {
        data class Active(val wallet: WalletInfo) : PremiumResult
        /** Play says the subscription isn't currently paid for (expired, pending, refunded). */
        data object Inactive : PremiumResult
        data class Failed(val message: String) : PremiumResult
    }

    /**
     * Sends a Play purchase token to the Worker, which verifies it with Google,
     * acknowledges it and records the expiry. The app never grants premium
     * itself - this is the only path to it.
     */
    suspend fun verifyPremium(purchaseToken: String): PremiumResult {
        val token = ensureRegistered() ?: return PremiumResult.Failed("Couldn't reach FixMoney. Check your connection.")
        return try {
            val response = client.post(AiProxy.PREMIUM_VERIFY_URL) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(json.encodeToString(PremiumVerifyRequest.serializer(), PremiumVerifyRequest(purchaseToken)))
            }
            when (response.status.value) {
                200 -> PremiumResult.Active(
                    json.decodeFromString(WalletResponse.serializer(), response.bodyAsText()).wallet.also { publish(it) }
                )
                402 -> PremiumResult.Inactive
                401 -> {
                    forgetToken()
                    PremiumResult.Failed("Please try again.")
                }
                else -> PremiumResult.Failed("Couldn't confirm the purchase with Google Play. It will be retried automatically.")
            }
        } catch (e: Exception) {
            logWarn(TAG, "premium verify failed: ${e.message}")
            PremiumResult.Failed("Couldn't reach FixMoney. Your purchase is safe and will be confirmed on the next launch.")
        }
    }

    /** Charges a feature with no model call behind it (the Home insight - free by default). */
    suspend fun spend(feature: AiFeature): AiGate {
        val token = ensureRegistered() ?: return AiGate.Passed
        return try {
            val response = client.post(AiProxy.SPEND_URL) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody("""{"feature":"${feature.wire}","requestId":"${newRequestId()}"}""")
            }
            if (response.status.value == 200) {
                json.decodeFromString(WalletResponse.serializer(), response.bodyAsText()).wallet
                    .also { publish(it, spentOn = feature.label) }
                AiGate.Passed
            } else {
                absorb(AiTicket(feature, token, ""), response)
            }
        } catch (e: Exception) {
            AiGate.Passed
        }
    }

    private fun applyHeaders(response: HttpResponse, spentOn: String) {
        val coins = response.headers["X-Coins"]?.toIntOrNull() ?: return
        val current = _wallet.value ?: WalletInfo()
        publish(
            spentOn = spentOn,
            info = current.copy(
                coins = coins,
                available = response.headers["X-Coins-Available"]?.toIntOrNull() ?: coins,
                trialEndsAt = response.headers["X-Trial-Ends-At"]?.toLongOrNull() ?: current.trialEndsAt,
                trialUsesLeftToday = response.headers["X-Trial-Uses-Left"]?.toIntOrNull() ?: current.trialUsesLeftToday,
                trialActive = (response.headers["X-Trial-Ends-At"]?.toLongOrNull() ?: current.trialEndsAt) >
                    com.fixmoney.shared.platform.currentTimeMillis(),
                welcomeBonusGranted = null
            )
        )
    }

    /**
     * [spentOn] names the feature when this update is the answer to an AI call;
     * otherwise a rise can only be a video reward or the welcome bonus, since
     * nothing else creates coins.
     */
    private fun publish(info: WalletInfo, spentOn: String? = null) {
        info.welcomeBonusGranted?.let { _events.tryEmit(WalletEvent.WelcomeBonus(it)) }
        val clean = info.copy(welcomeBonusGranted = null)
        _wallet.value?.let { before -> recordChange(before.coins, info, spentOn) }
        _wallet.value = clean
        try {
            settings.putString(KEY_CACHE, json.encodeToString(WalletInfo.serializer(), clean))
        } catch (_: Exception) {
            // A cache that cannot be written must never break the wallet.
        }
    }

    private fun recordChange(before: Int, info: WalletInfo, spentOn: String?) {
        var delta = info.coins - before
        val entries = mutableListOf<Pair<Int, String>>()
        val bonus = info.welcomeBonusGranted ?: 0
        if (bonus > 0) {
            entries += bonus to "Welcome bonus"
            delta -= bonus
        }
        // A rise seen on an AI answer's headers is a bonus or a reward that
        // landed meanwhile - the headers do not say which.
        if (delta > 0) entries += delta to (if (spentOn == null) "Video reward" else "Coins added")
        if (delta < 0) entries += delta to (spentOn ?: "AI use")
        if (entries.isEmpty()) return

        val now = com.fixmoney.shared.platform.currentTimeMillis()
        val day = com.fixmoney.shared.platform.todayIso()
        val updated = (entries.map { (d, label) -> CoinActivity(now, day, d, label) } + _activity.value)
            .take(ACTIVITY_KEEP)
        _activity.value = updated
        try {
            settings.putString(KEY_ACTIVITY, json.encodeToString(ListSerializer(CoinActivity.serializer()), updated))
        } catch (_: Exception) {
            // Same rule as the balance cache: the list is a convenience.
        }
    }

    private fun readActivity(): List<CoinActivity> = try {
        settings.getStringOrNull(KEY_ACTIVITY)
            ?.let { json.decodeFromString(ListSerializer(CoinActivity.serializer()), it) }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun readCache(): WalletInfo? = try {
        settings.getStringOrNull(KEY_CACHE)?.let { json.decodeFromString(WalletInfo.serializer(), it) }
    } catch (_: Exception) {
        null
    }

    /**
     * A token the Worker rejected. The next call re-registers by device key,
     * which lands on the same wallet - so this costs one round trip, not coins.
     */
    private fun forgetToken() {
        settings.remove(KEY_TOKEN)
    }

    private fun newRequestId(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..24).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
