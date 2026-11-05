package com.fixmoney.shared.ai

/**
 * Where the app sends model requests.
 *
 * It used to send them straight to `api.groq.com` with the Groq key attached,
 * which meant shipping that key inside the APK - `Secrets.GROQ_API_KEY` was a
 * compile-time constant, so it landed in the dex and came out with `strings` in
 * seconds. Verified on a real build before this changed.
 *
 * Now a Cloudflare Worker holds the key and the app carries no credential at
 * all. The Worker also pins the model, caps abuse per IP, and falls back to a
 * second provider when Groq is out of quota - all of which can change without
 * shipping an app update.
 *
 * The URL is **not** a secret: it is a public endpoint, and treating it as one
 * would be the same mistake in a different place. What protects it is that it
 * can only reach one model, and that the Worker can be rate-limited or taken
 * down from a dashboard. See `worker/README.md`.
 *
 * The request body is unchanged OpenAI chat-completions, so the callers that
 * used to post to Groq post the same shape here.
 */
internal object AiProxy {
    const val DEFAULT_BASE_URL = "https://fixmoney-ai-proxy.vikram8575229.workers.dev"

    /** Set only by debug builds, via [AiEndpoint.useDebugWorker]. */
    internal var baseOverride: String? = null

    private val base: String get() = baseOverride ?: DEFAULT_BASE_URL

    val CHAT_URL: String get() = "$base/v1/chat"

    // AI coins - see worker/README.md. A Worker that predates coins answers 404
    // here, which WalletRepository treats as "no wallet" and the app carries on
    // on the old uncharged path.
    val REGISTER_URL: String get() = "$base/v1/register"
    val WALLET_URL: String get() = "$base/v1/wallet"
    val SPEND_URL: String get() = "$base/v1/spend"
    val PREMIUM_VERIFY_URL: String get() = "$base/v1/premium/verify"
}

/**
 * Points the app at a different Worker - **debug builds only**, to test against
 * `wrangler dev` on the developer's machine (`adb reverse tcp:8787 tcp:8787`,
 * then launch with `--es worker_url http://127.0.0.1:8787`). The Android entry
 * point refuses to call this unless the build is debuggable, so a release build
 * always talks to the real Worker.
 */
object AiEndpoint {
    fun useDebugWorker(url: String?) {
        AiProxy.baseOverride = url?.trimEnd('/')?.takeIf { it.isNotBlank() }
    }

    val current: String get() = AiProxy.baseOverride ?: AiProxy.DEFAULT_BASE_URL

    /** The production Worker. */
    const val DEFAULT: String = AiProxy.DEFAULT_BASE_URL
}
