package com.music.bitchord.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How far a finger may wander inside a lower-half tap and still be a tap, as a
 * multiple of the platform's own slop.
 *
 * The list underneath starts scrolling at exactly one slop, and it consumes the
 * gesture when it does — so a tap that shifted by a hair more than that was not
 * merely ignored, it was taken by the list and became a browse. Every shortfall
 * here is a tap somebody meant and did not get.
 */
private const val TAP_SLOP_FACTOR = 2.5f

/**
 * Pause automatic lyric following for the duration of a touch without
 * consuming any pointer changes. The rows' own click handlers remain the sole
 * owners of line taps; this only prevents an auto-scroll from moving a row out
 * from under the finger between down and up.
 */
@Composable
internal fun Modifier.holdLyricsAutoFollowWhileTouched(
    onTouchChanged: (Boolean) -> Unit,
    onTouchStarted: suspend () -> Unit,
): Modifier {
    val currentOnTouchChanged = rememberUpdatedState(onTouchChanged)
    val currentOnTouchStarted = rememberUpdatedState(onTouchStarted)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            currentOnTouchChanged.value(true)
            try {
                // A fling or automatic scroll can still own the list when a
                // reader taps a distant line. Stop that motion on down without
                // consuming the pointer, so the row's click handler gets this
                // same first tap instead of only stopping the scroll.
                currentOnTouchStarted.value()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                } while (true)
            } finally {
                currentOnTouchChanged.value(false)
            }
        }
    }
}

/**
 * Reveal the player's controls from an otherwise unhandled tap in the lower
 * half of the lyrics panel.
 *
 * Observe at PointerEventPass.Final rather than consuming in Initial. The
 * previous initial-pass detector treated every lower-half tap as a control
 * reveal while controls were hidden, stealing taps from the lyric rows before
 * their seek click could run. Listening at Final lets a row handle its own tap;
 * only an unclaimed tap reveals the controls. Scroll gestures are observed but
 * never delayed or consumed by this helper.
 */
@Composable
internal fun Modifier.revealLyricsControlsOnTap(
    enabled: Boolean,
    onReveal: () -> Unit,
): Modifier {
    val currentOnReveal = rememberUpdatedState(onReveal)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop * TAP_SLOP_FACTOR
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Final,
            )
            if (down.position.y < size.height / 2f) return@awaitEachGesture
            var dragged = false
            var claimed = down.isConsumed
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                claimed = claimed || change.isConsumed
                if ((change.position - down.position).getDistance() > tapSlop ||
                    event.changes.size > 1
                ) {
                    dragged = true
                }
                if (!change.pressed) {
                    if (!dragged && !claimed) currentOnReveal.value()
                    break
                }
            } while (true)
        }
    }
}

/**
 * Toggle Spotify Canvas controls from an unhandled video tap.
 *
 * Unlike [revealLyricsControlsOnTap], this observes at the final pointer pass
 * and never consumes the gesture. The compact title/artist row remains on
 * screen while the rest of the player is hidden, so its heart, overflow menu,
 * album and artist targets must receive taps normally. Any empty video space
 * that no child claimed can show or hide the full control deck.
 */
@Composable
internal fun Modifier.toggleSpotifyCanvasControlsOnTap(
    enabled: Boolean,
    onToggle: () -> Unit,
): Modifier {
    val currentOnToggle = rememberUpdatedState(onToggle)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop * TAP_SLOP_FACTOR
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dragged = false
            var claimed = down.isConsumed
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                claimed = claimed || change.isConsumed
                if ((change.position - down.position).getDistance() > tapSlop ||
                    event.changes.size > 1
                ) {
                    dragged = true
                }
                if (!change.pressed) {
                    if (!dragged && !claimed) currentOnToggle.value()
                    break
                }
            } while (true)
        }
    }
}
