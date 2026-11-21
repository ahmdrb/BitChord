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
): Modifier {
    val currentOnTouchChanged = rememberUpdatedState(onTouchChanged)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            currentOnTouchChanged.value(true)
            try {
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
 * Relay a stationary tap to this exact lyric row when a fully expanded
 * ModalBottomSheet consumes the Initial down during its short post-fling
 * settle.
 *
 * This lives on the row, not at the LazyColumn root. Compose has already
 * performed hit-testing against the row's real bounds (including transforms),
 * so no arithmetic over LazyListItemInfo offsets can accidentally choose the
 * line above or below it when content padding or sheet motion is involved.
 * Ordinary taps stay with the row's clickable/combinedClickable.
 */
@Composable
internal fun Modifier.relayLyricsTapWhenSheetConsumes(
    enabled: Boolean,
    canPick: Boolean,
    picking: Boolean,
    onSeek: () -> Unit,
    onPick: () -> Unit,
    onTogglePick: () -> Unit,
): Modifier {
    val currentSeek = rememberUpdatedState(onSeek)
    val currentPick = rememberUpdatedState(onPick)
    val currentTogglePick = rememberUpdatedState(onTogglePick)
    return pointerInput(enabled, canPick, picking) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val sheetConsumedDown = down.isConsumed
            var moved = false
            var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.size > 1) moved = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > tapSlop) moved = true
                if (!change.pressed) {
                    released = change
                    break
                }
            } while (true)

            val up = released ?: return@awaitEachGesture
            // The normal row action owns a down it can claim. Relay only
            // when the sheet consumed that down or no child claimed the tap;
            // this row's identity comes from Compose hit-testing, not offsets.
            if (moved || (!sheetConsumedDown && up.isConsumed)) {
                return@awaitEachGesture
            }

            val heldFor = (up.uptimeMillis - down.uptimeMillis).coerceAtLeast(0L)
            when {
                canPick && heldFor >= longPressTimeout -> currentPick.value()
                picking -> currentTogglePick.value()
                else -> currentSeek.value()
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
