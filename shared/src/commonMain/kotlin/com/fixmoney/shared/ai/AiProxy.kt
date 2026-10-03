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
    const val CHAT_URL = "https://fixmoney-ai-proxy.vikram8575229.workers.dev/v1/chat"
}
