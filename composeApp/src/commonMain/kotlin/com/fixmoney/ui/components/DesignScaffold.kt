package com.fixmoney.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_fixmoney_logo
import com.fixmoney.resources.nd_bell
import com.fixmoney.resources.nd_person
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The one-pixel rule under the collapsed header. */
val HAIRLINE = 1.dp

/** How far a header-only screen scrolls before its rule is fully opaque. */
private const val HAIRLINE_FADE_PX = 24f

/**
 * A stroke-drawn vector tinted at the call site. The `nd_*` icons are authored
 * white so `Icon`'s tint controls them completely - a vector with baked colours
 * cannot be recoloured, and these are used in a dozen tints.
 */
@Composable
fun LineIcon(res: DrawableResource, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

/**
 * The global app bar: app mark, screen title, an inert bell, and the avatar.
 *
 * It is **just the row**. The status-bar spacer and the hairline belong to
 * [CollapsingScreen], because this row's measured height is the distance the
 * header collapses by - anything else inside it would make the sticky section
 * stop in the wrong place.
 */
/**
 * The height of the header strip directly under the status bar.
 *
 * Home spends it on [DesignAppBar]; every other screen spends it on its own
 * title row via [ScreenTitleBand]. Both read this one value so a title lands at
 * the same height on every tab and switching tabs does not nudge the header up
 * or down.
 */
val HEADER_BAND = 64.dp

/**
 * A screen's own title row, occupying exactly the band [DesignAppBar] occupies
 * on Home.
 *
 * Screens without an app bar used to start their title 2dp under the status
 * bar, which put it roughly 18dp higher than Home's - small enough to look like
 * a glitch rather than a design, and visible as a jump on every tab change.
 * Centring the row in the same band fixes that for any title size, with no
 * per-screen offset to keep in sync.
 */
@Composable
fun ScreenTitleBand(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(HEADER_BAND).padding(horizontal = 20.dp),
        content = content
    )
}

@Composable
fun DesignAppBar(title: String, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(HEADER_BAND).padding(horizontal = 20.dp)
    ) {
        // The app mark sits on a **white** plate in both themes: the artwork is
        // itself two shades of purple, so a tinted plate swallows it. Same
        // reason the splash and the Welcome header use one.
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(Color.White)
                .border(1.dp, colors.stroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_fixmoney_logo),
                contentDescription = "FixMoney",
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = t.headlineSm, color = colors.textPrimary)
        Spacer(Modifier.weight(1f))
        // Inert: there is no notification feed to open.
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            LineIcon(Res.drawable.nd_bell, colors.textMuted, 22.dp)
        }
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(colors.accent)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(Res.drawable.nd_person, colors.surface, 18.dp)
        }
    }
}

/**
 * The redesign's page shell: a scrolling body under a header that collapses.
 *
 * [DesignAppBar] and [sticky] are one pinned stack laid over the content. A
 * null [title] drops the app bar entirely: [sticky] is then simply the screen's
 * header, pinned, with nothing to collapse.
 *
 * Scrolling slides the stack up by exactly the scroll offset until the app bar
 * has gone, then stops - [sticky] is left sitting where the app bar was and the
 * body passes underneath it. Scrolling back down unwinds identically.
 *
 * The offset is **derived** from `scroll.value`, never animated towards a
 * target. That is what makes the two directions match for free and leaves
 * nothing to get out of sync when the direction changes mid-fling.
 *
 * Three things here are easy to get wrong, and each one was a real bug first:
 *
 * - **The body's top spacer reserves the stack's *uncollapsed* height**, hairline
 *   included, and the stack shrinks by the same pixels the body scrolls.
 *   Shrinking the spacer too moves the body at twice the speed of the finger;
 *   leaving the hairline out of the sum shaves the first card's top corner.
 * - **Constraining the stack's height from its own measured size deadlocks.** On
 *   the first frame the measured height is 0, so the box is 0dp, so the content
 *   measures 0, and the header never appears at all. It stays unconstrained
 *   until measured once; collapse is 0 then, so it looks identical.
 * - **The inner column takes `wrapContentHeight(unbounded = true)`**, so the size
 *   reported is its natural height rather than whatever the box has shrunk to.
 *   Without it the collapse distance chases its own tail.
 */
@Composable
fun CollapsingScreen(
    title: String?,
    scroll: ScrollState,
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
    sticky: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    val colors = Design.colors
    val density = LocalDensity.current
    var appBarPx by remember { mutableIntStateOf(0) }
    var headerBodyPx by remember { mutableIntStateOf(0) }

    val collapsePx = scroll.value.coerceIn(0, appBarPx)

    // With an app bar, the rule tracks the collapse. Without one there is
    // nothing to collapse - the sticky section *is* the header - so it fades in
    // over the first few pixels of scroll instead, which is the only thing the
    // rule is actually for: separating a pinned header from content sliding
    // under it.
    val hairlineProgress =
        if (appBarPx > 0) collapsePx.toFloat() / appBarPx
        else (scroll.value / HAIRLINE_FADE_PX).coerceIn(0f, 1f)

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerBodyDp = with(density) { headerBodyPx.toDp() }

    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(statusBarHeight + headerBodyDp + HAIRLINE))
            body()
        }

        Column(Modifier.fillMaxWidth().background(colors.surface)) {
            // Outside the clipped region, so the status bar never has content
            // sliding behind it.
            Spacer(Modifier.height(statusBarHeight))
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (headerBodyPx == 0) Modifier
                        else Modifier.height(
                            (headerBodyDp - with(density) { collapsePx.toDp() })
                                .coerceAtLeast(0.dp)
                        )
                    )
                    .clipToBounds()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .offset { IntOffset(0, -collapsePx) }
                        .onSizeChanged { headerBodyPx = it.height }
                ) {
                    if (title != null) {
                        DesignAppBar(
                            title = title,
                            onOpenProfile = onOpenProfile,
                            modifier = Modifier.onSizeChanged { appBarPx = it.height }
                        )
                    }
                    sticky()
                }
            }
            // Fades in only once the sticky section is actually acting as the
            // header. **Scaled, not replaced**: `stroke` is already translucent,
            // so `copy(alpha = progress)` would overwrite its 8% and paint a
            // solid white bar across the screen at full collapse.
            Box(
                Modifier.fillMaxWidth().height(HAIRLINE)
                    .background(colors.stroke.copy(alpha = colors.stroke.alpha * hairlineProgress))
            )
        }
    }
}
