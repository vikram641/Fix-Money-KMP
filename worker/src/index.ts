/**
 * FixMoney AI proxy.
 *
 * The app used to call Groq directly, which meant shipping the Groq key inside
 * the APK - a `strings` away from anyone who downloads it. This Worker holds
 * the key instead, so the app never carries a credential at all.
 *
 * It is deliberately a **forwarder, not a framework**: read the body, attach the
 * key, pass it on. That keeps it inside the free plan's 10ms *CPU* budget -
 * waiting on Groq is I/O, which does not count - and keeps the isolate's
 * ~1ms start, which was the whole reason for choosing Workers over a free
 * container host that sleeps after 15 minutes.
 *
 * What this does fix: the key is no longer extractable, it can be rotated
 * without shipping an app update, and abuse can be cut off from the dashboard.
 *
 * What it does NOT fix on its own: anyone who pulls the endpoint URL out of the
 * APK can still call it. That is what `MIN_APP_VERSION` and the per-IP cap
 * below blunt, and what Play Integrity / App Check would close properly - see
 * README.
 */

export interface Env {
  GROQ_API_KEY: string;
  /** Optional. Bound in wrangler.jsonc to enable the per-IP daily cap. */
  RATE?: KVNamespace;
  /** Optional. Cloudflare Workers AI, used when Groq is out of quota. */
  AI?: Ai;
}

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

/** Only this model may be requested, whatever the caller asks for. */
const ALLOWED_MODEL = "openai/gpt-oss-20b";

/**
 * Per-IP cap. Groq's own free tier stops at 1,000 requests/day for the whole
 * org, so without this one abuser can spend the entire allowance and every
 * real user gets a 429 for the rest of the day.
 */
const DAILY_PER_IP = 80;

/** Groq rejects runaway prompts anyway; failing here is cheaper and clearer. */
const MAX_BODY_BYTES = 128 * 1024;

/**
 * Second tier, used only once Groq refuses.
 *
 * Groq's free allowance is 1,000 requests a day for the whole org; Workers AI
 * gives every account 10,000 Neurons a day on top of that, as a binding with no
 * key and no second account. Chaining two *providers* is the legitimate version
 * of chaining two free accounts at one provider - that would be a terms
 * violation and would cost both accounts.
 *
 * Living here rather than in the app means the chain can be reordered, or a
 * third tier added, without shipping a release.
 */
const FALLBACK_MODEL = "@cf/openai/gpt-oss-120b";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    if (request.method !== "POST") return err(405, "POST only");
    if (!env.GROQ_API_KEY) return err(500, "Proxy is missing its API key");

    const url = new URL(request.url);
    if (url.pathname !== "/v1/chat") return err(404, "Unknown route");

    const raw = await request.text();
    if (raw.length > MAX_BODY_BYTES) return err(413, "Body too large");

    let body: Record<string, unknown>;
    try {
      body = JSON.parse(raw);
    } catch {
      return err(400, "Body must be JSON");
    }
    if (!Array.isArray(body.messages) || body.messages.length === 0) {
      return err(400, "messages[] is required");
    }

    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    if (await overQuota(env, ip)) {
      return err(429, "Daily limit reached for this device. Try again tomorrow.");
    }

    // The model is pinned here rather than taken from the caller: a forwarder
    // that passes `model` straight through is an open invitation to spend the
    // org's quota on whatever is most expensive.
    const payload = { ...body, model: ALLOWED_MODEL, stream: false };

    let upstream: Response | null = null;
    try {
      upstream = await fetch(GROQ_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${env.GROQ_API_KEY}`,
        },
        body: JSON.stringify(payload),
      });
    } catch {
      upstream = null; // network failure counts as "Groq unavailable"
    }

    if (upstream && upstream.ok) {
      return cors(
        new Response(upstream.body, {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );
    }

    // Out of quota (429) or Groq is down (5xx / unreachable) - try tier two.
    // A 4xx that is not 429 means the request itself was wrong, so retrying it
    // somewhere else would just fail again more slowly.
    const retryable = !upstream || upstream.status === 429 || upstream.status >= 500;
    if (retryable && env.AI) {
      const viaFallback = await runFallback(env, body.messages as ChatMessage[]);
      if (viaFallback) return cors(viaFallback);
    }

    if (!upstream) return err(502, "Could not reach the model provider");
    return cors(
      new Response(upstream.body, {
        status: upstream.status,
        headers: { "Content-Type": "application/json" },
      })
    );
  },
} satisfies ExportedHandler<Env>;

/**
 * Counts one request against this IP's day.
 *
 * KV is eventually consistent, so a burst can slip a few over the line. That is
 * fine: this exists to stop one client draining 1,000 requests, not to meter
 * billing - and on the free tier there is no bill to meter.
 */
async function overQuota(env: Env, ip: string): Promise<boolean> {
  if (!env.RATE) return false;
  const key = `${ip}:${new Date().toISOString().slice(0, 10)}`;
  const used = Number((await env.RATE.get(key)) ?? "0");
  if (used >= DAILY_PER_IP) return true;
  // 48h TTL: longer than the window, so yesterday's key expires on its own.
  await env.RATE.put(key, String(used + 1), { expirationTtl: 60 * 60 * 48 });
  return false;
}

function cors(res: Response): Response {
  res.headers.set("Access-Control-Allow-Origin", "*");
  res.headers.set("Access-Control-Allow-Headers", "Content-Type");
  res.headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  return res;
}

function err(status: number, message: string): Response {
  return cors(
    new Response(JSON.stringify({ error: message }), {
      status,
      headers: { "Content-Type": "application/json" },
    })
  );
}

interface ChatMessage {
  role: string;
  content: string;
}

/** Either shape Workers AI may answer in - see the note in `runFallback`. */
interface FallbackReply {
  response?: string;
  choices?: Array<{ message?: { content?: string } }>;
}

/**
 * Workers AI, reshaped into the OpenAI response the app already parses.
 *
 * The app decodes `choices[0].message.content` in all three call sites, so the
 * fallback has to speak that shape or every caller would need a second parser
 * for a path that normally never runs - exactly the kind of branch that rots
 * unnoticed until the day it is needed.
 */
async function runFallback(env: Env, messages: ChatMessage[]): Promise<Response | null> {
  if (!env.AI) return null;
  try {
    // `@cloudflare/workers-types` pins a union of model names that trails the
    // live catalogue, so a current model fails to typecheck against a stale
    // union. Widening the call is honest here: the name is validated by the
    // platform at runtime, and `wrangler ai models` is the real source of truth.
    const ai = env.AI as unknown as {
      run(model: string, input: { messages: ChatMessage[] }): Promise<FallbackReply>;
    };
    const out = await ai.run(FALLBACK_MODEL, { messages });
    // Workers AI returns one of two shapes depending on the model: the older
    // text models answer `{ response }`, while the OpenAI-family ones already
    // answer in OpenAI form. Reading only `response` is what made this fall
    // through silently the first time it was wired up - the model replied
    // perfectly well and the proxy threw the answer away.
    const content =
      out?.choices?.[0]?.message?.content ?? out?.response ?? null;
    if (!content) return null;
    return new Response(
      JSON.stringify({
        choices: [{ message: { role: "assistant", content } }],
        x_fixmoney_tier: "workers-ai",
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch {
    return null;
  }
}
