/**
 * The model call itself: Groq first, Workers AI when Groq refuses.
 *
 * Moved out of index.ts unchanged in behaviour, except that it now reports
 * *what happened* instead of returning a Response directly - the coin layer
 * needs to know whether the model answered (commit the charge), was busy
 * (release it, tell the user they were not charged) or rejected the request
 * itself (release it, pass the error on).
 */

import type { Env } from "./env";

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

/** Only this model may be requested, whatever the caller asks for. */
const ALLOWED_MODEL = "openai/gpt-oss-20b";

/**
 * Second tier, used only once Groq refuses.
 *
 * Groq's free allowance is shared by the whole org (about 30 requests and 8k
 * tokens a minute, 1,000 requests a day); Workers AI adds 10,000 Neurons a day
 * as a binding with no key and no second account. Chaining two *providers* is
 * the legitimate version of chaining two free accounts at one provider - that
 * would be a terms violation and would cost both accounts.
 */
const FALLBACK_MODEL = "@cf/openai/gpt-oss-120b";

export interface ChatMessage {
  role: string;
  content: string;
}

export type AiOutcome =
  /** The model answered; `body` is the OpenAI-shaped JSON the app parses. */
  | { kind: "ok"; body: ReadableStream | string; tier: "groq" | "workers-ai" }
  /** Every provider is out of capacity or down. Nothing should be charged. */
  | { kind: "busy" }
  /** The request itself was rejected (a non-429 4xx). Retrying elsewhere would fail too. */
  | { kind: "rejected"; status: number; body: ReadableStream | null };

export async function runChat(env: Env, body: Record<string, unknown>): Promise<AiOutcome> {
  // The model is pinned here rather than taken from the caller: a forwarder
  // that passes `model` straight through is an open invitation to spend the
  // org's quota on whatever is most expensive.
  const payload = { ...body, model: ALLOWED_MODEL, stream: false };
  const url = env.DEV_MODE === "true" && env.GROQ_URL ? env.GROQ_URL : GROQ_URL;

  let upstream: Response | null = null;
  try {
    upstream = await fetch(url, {
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

  if (upstream && upstream.ok && upstream.body) {
    return { kind: "ok", body: upstream.body, tier: "groq" };
  }

  // Out of quota (429) or Groq is down (5xx / unreachable) - try tier two.
  const retryable = !upstream || upstream.status === 429 || upstream.status >= 500;
  if (!retryable && upstream) {
    return { kind: "rejected", status: upstream.status, body: upstream.body };
  }

  // Not consumed - free it so the connection is not held open.
  await upstream?.body?.cancel();

  const fallbackOff = env.DEV_MODE === "true" && env.DEV_NO_FALLBACK === "true";
  if (env.AI && !fallbackOff) {
    const content = await runFallback(env, body.messages as ChatMessage[]);
    if (content) {
      return {
        kind: "ok",
        body: JSON.stringify({
          choices: [{ message: { role: "assistant", content } }],
          x_fixmoney_tier: "workers-ai",
        }),
        tier: "workers-ai",
      };
    }
  }
  return { kind: "busy" };
}

/** Either shape Workers AI may answer in - see the note below. */
interface FallbackReply {
  response?: string;
  choices?: Array<{ message?: { content?: string } }>;
}

/**
 * Workers AI, reshaped into the OpenAI response the app already parses.
 *
 * The app decodes `choices[0].message.content` in all three call sites, so the
 * fallback has to speak that shape or every caller would need a second parser
 * for a path that normally never runs.
 */
async function runFallback(env: Env, messages: ChatMessage[]): Promise<string | null> {
  if (!env.AI) return null;
  try {
    // `@cloudflare/workers-types` pins a union of model names that trails the
    // live catalogue, so a current model fails to typecheck against a stale
    // union. The name is validated by the platform at runtime.
    const ai = env.AI as unknown as {
      run(model: string, input: { messages: ChatMessage[] }): Promise<FallbackReply>;
    };
    const out = await ai.run(FALLBACK_MODEL, { messages });
    // Older text models answer `{ response }`, the OpenAI-family ones answer in
    // OpenAI form. Reading only `response` is what made this fall through
    // silently the first time it was wired up.
    return out?.choices?.[0]?.message?.content ?? out?.response ?? null;
  } catch {
    return null;
  }
}
