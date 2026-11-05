package com.fixmoney.shared.premium

import com.russhwolf.settings.Settings

/** How long a new install gets the premium features for nothing. */
const val TRIAL_DAYS = 5

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val TRIAL_MILLIS = TRIAL_DAYS * DAY_MILLIS

/**
 * A jump this far forward is a wrong clock, not elapsed time.
 *
 * Phones do land on absurd dates - a flat battery can reset one to 1970 or to a
 * year in the future. Taking such a reading at face value would end a paying-
 * soon user's trial instantly and permanently, so an implausible jump is ignored
 * rather than counted.
 */
private const val MAX_PLAUSIBLE_JUMP_MILLIS = 90L * DAY_MILLIS

/**
 * The 5-day free trial.
 *
 * **Counted from two values, not one.** The obvious implementation stores a
 * start time and compares it with `now`, which breaks three ways: the clock can
 * be moved back so the trial never ends, moved forward so an honest user's trial
 * ends early, or reset to nonsense by a flat battery.
 *
 * So a high-water mark is kept alongside the start: the largest time ever
 * observed. Progress is measured against that, which makes moving the clock
 * backwards useless - the watermark has already advanced - while an implausible
 * jump forward is discarded instead of ending the trial.
 *
 * **Prefer server time.** [touch] takes an optional trusted time, which the app
 * passes from the AI proxy's HTTP `Date` header when a call has just been made.
 * A user cannot move the server's clock. The device clock is the fallback for
 * when the app is offline, with the guards above.
 *
 * **The reinstall hole is deliberate.** Settings are wiped on uninstall, so a
 * reinstall restarts the trial. Closing that needs a real identity, and building
 * auth solely to protect a 5-day trial costs more than the trial leaks - people
 * who reinstall to farm trials were never going to subscribe. If it ever shows
 * up in the numbers, Play's own trial is tracked per Google account and the
 * paywall would not have to change, only this class.
 */
class TrialState(private val settings: Settings) {

    private companion object {
        const val STARTED_AT = "trial_started_at"
        const val HIGH_WATER = "trial_high_water"
    }

    /**
     * Records the current time and returns the trial's state.
     *
     * Call on launch and whenever a trusted time becomes available. [now] should
     * be server time when there is one; [deviceNow] is what the device believes,
     * used only to seed a first run and when offline.
     */
    fun touch(deviceNow: Long, trustedNow: Long? = null): Trial {
        val now = trustedNow ?: deviceNow
        val started = settings.getLong(STARTED_AT, 0L)

        if (started == 0L) {
            // First run. The watermark starts level with the start, so a trial
            // always begins with its full length ahead of it.
            settings.putLong(STARTED_AT, now)
            settings.putLong(HIGH_WATER, now)
            return Trial(active = true, remainingMillis = TRIAL_MILLIS)
        }

        val water = settings.getLong(HIGH_WATER, started)
        val advanced = when {
            // Clock behind the watermark: already seen a later time, so this
            // reading tells us nothing new. Hold, do not rewind.
            now <= water -> water
            // A trusted time is worth believing however big the jump - that is
            // the whole point of having one.
            trustedNow != null -> now
            // Device clock leaping forward by months is a broken clock, not
            // five days passing.
            now - water > MAX_PLAUSIBLE_JUMP_MILLIS -> water
            else -> now
        }
        if (advanced != water) settings.putLong(HIGH_WATER, advanced)

        val elapsed = advanced - started
        val remaining = (TRIAL_MILLIS - elapsed).coerceAtLeast(0L)
        return Trial(active = remaining > 0L, remainingMillis = remaining)
    }

    /** Reads the state without recording a new time. */
    fun peek(): Trial {
        val started = settings.getLong(STARTED_AT, 0L)
        if (started == 0L) return Trial(active = true, remainingMillis = TRIAL_MILLIS)
        val elapsed = settings.getLong(HIGH_WATER, started) - started
        val remaining = (TRIAL_MILLIS - elapsed).coerceAtLeast(0L)
        return Trial(active = remaining > 0L, remainingMillis = remaining)
    }
}

/** @property remainingMillis zero once the trial is over. */
data class Trial(val active: Boolean, val remainingMillis: Long) {
    /** Days left, rounded up - "1 day left" should not read as 0 all day. */
    val daysLeft: Int
        get() = ((remainingMillis + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
}
