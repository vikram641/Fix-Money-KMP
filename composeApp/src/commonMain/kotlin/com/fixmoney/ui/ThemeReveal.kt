package com.fixmoney.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.math.hypot

/**
 * How long the new theme takes to flood the screen. Long enough to read as a
 * deliberate wipe rather than a dropped frame; short enough that nobody waiting
 * to carry on in Settings is held up by it.
 */
private const val REVEAL_MILLIS = 620

/**
 * The dark/light switch, animated as a circle of the **new** theme spreading
 * out from the switch itself - as if the colour had been poured on at that
 * point and run outwards.
 *
 * The trick is that a theme change repaints everything at once, so there is
 * nothing left to animate *from*. So the old screen is photographed first:
 *
 *  1. the live UI is drawn through a [androidx.compose.ui.graphics.layer.GraphicsLayer],
 *     so the last frame can be read back as a bitmap,
 *  2. on tap that bitmap is captured - still the old theme, because the capture
 *     happens before the switch is applied,
 *  3. the theme flips and the whole app recomposes in the new colours,
 *  4. for the length of the animation the **photograph is painted on top**,
 *     with a growing circular hole punched in it at the tap point, through
 *     which the new colours show.
 *
 * Nothing moves or is re-laid-out, so the expense list underneath cannot jump
 * mid-wipe: the only thing changing is how much of the old photo is left.
 *
 * **Recording is off until a screen asks for it** via [ArmThemeReveal]. Routing
 * the whole app through an offscreen layer on every frame to serve one switch
 * buried in Settings would tax scrolling everywhere else, so only Settings
 * turns it on, and only while it is on screen.
 *
 * Every failure here falls back to switching the theme with no animation, which
 * is the pre-existing behaviour: a theme switch must never be lost to a missing
 * snapshot.
 */
class ThemeRevealState internal constructor() {

    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal var origin by mutableStateOf(Offset.Zero)
    internal var armed by mutableStateOf(false)
    internal val progress = Animatable(0f)
    internal var capture: (suspend () -> ImageBitmap?)? = null

    private var running = false

    /**
     * Applies [apply] - the actual theme change - and wipes it in from [from],
     * a point in root coordinates.
     *
     * [apply] runs exactly once whatever happens, including when there is no
     * snapshot to animate or the animation is interrupted.
     */
    suspend fun reveal(from: Offset, apply: () -> Unit) {
        // Mid-wipe, or recording never started: just switch. Queueing a second
        // animation behind the first would leave the toggle lagging the tap.
        if (running || !armed) {
            apply()
            return
        }
        val shot = capture?.invoke()
        if (shot == null) {
            apply()
            return
        }
        running = true
        origin = from
        snapshot = shot
        try {
            progress.snapTo(0f)
            apply()
            progress.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
        } finally {
            // Dropping the photograph is what makes the result permanent, so it
            // has to happen even if the animation is cancelled - otherwise a
            // stale screenshot would be left covering the app.
            snapshot = null
            running = false
        }
    }
}

@Composable
fun rememberThemeRevealState(): ThemeRevealState = remember { ThemeRevealState() }

/** Null when no [ThemeReveal] is hosting - callers then switch theme directly. */
val LocalThemeReveal = staticCompositionLocalOf<ThemeRevealState?> { null }

/**
 * Starts frame recording while the calling screen is on screen, so a snapshot
 * is available the moment the switch is tapped. See [ThemeRevealState].
 */
@Composable
fun ArmThemeReveal() {
    val state = LocalThemeReveal.current ?: return
    DisposableEffect(state) {
        state.armed = true
        onDispose { state.armed = false }
    }
}

/** Hosts the reveal. Wrap the themed content of the app in this. */
@Composable
fun ThemeReveal(state: ThemeRevealState, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()

    LaunchedEffect(layer) {
        state.capture = { runCatching { layer.toImageBitmap() }.getOrNull() }
    }

    Box(
        Modifier.fillMaxSize().drawWithContent {
            val shot = state.snapshot
            when {
                // Mid-wipe: old photo on top, new theme through a growing hole.
                shot != null -> {
                    drawImage(shot)
                    val radius = state.progress.value * farthestCorner(state.origin, size)
                    if (radius > 0f) {
                        clipPath(Path().apply { addOval(Rect(state.origin, radius)) }) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                // Settings is open: keep a readable copy of the last frame.
                state.armed -> {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                // Everywhere else: draw straight to the screen, no layer.
                else -> drawContent()
            }
        }
    ) {
        content()
    }
}

/**
 * The radius at which a circle centred on [origin] covers the whole screen -
 * the distance to the farthest corner, so the wipe finishes everywhere at once
 * rather than leaving the far corner behind.
 */
private fun farthestCorner(origin: Offset, size: Size): Float = hypot(
    maxOf(origin.x, size.width - origin.x),
    maxOf(origin.y, size.height - origin.y)
)
