package com.fixmoney.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Horizontal swipe-to-navigate, used by onboarding and the bottom-nav shell so
 * neither is button-only any more.
 *
 * Deliberately `pointerInput` + [detectHorizontalDragGestures] rather than an
 * anchored draggable: the gesture runs in the **Main** pass, which reaches a
 * parent only after its children, so a child that owns horizontal movement
 * itself - the budget `Slider`, every `LazyRow` of category chips - consumes the
 * drag first and keeps working. Only a swipe that nothing else wanted gets here.
 *
 * **Keyed on `Unit`, with the callbacks read through [rememberUpdatedState].**
 * Keying on the callbacks themselves looks right and is wrong: a caller passes a
 * lambda like `{ if (state.name.isNotBlank()) onContinue() }`, which is a fresh
 * object on every recomposition, so the pointer-input node was torn down and
 * restarted constantly - and a restart **cancels the gesture in flight**. The
 * symptom was exact and reproducible on the device: after typing a name on
 * SetupName, the *first* swipe did nothing and the second one worked.
 *
 * A null callback means that direction does nothing, so the first onboarding
 * screen cannot be swiped backwards off the flow.
 */
private val SWIPE_THRESHOLD = 56.dp

@Composable
fun Modifier.horizontalSwipe(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
): Modifier {
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)
    return this.pointerInput(Unit) {
        val threshold = SWIPE_THRESHOLD.toPx()
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragCancel = { travelled = 0f },
            onDragEnd = {
                when {
                    travelled <= -threshold -> left?.invoke()
                    travelled >= threshold -> right?.invoke()
                }
                travelled = 0f
            },
            onHorizontalDrag = { change, delta ->
                travelled += delta
                change.consume()
            }
        )
    }
}
