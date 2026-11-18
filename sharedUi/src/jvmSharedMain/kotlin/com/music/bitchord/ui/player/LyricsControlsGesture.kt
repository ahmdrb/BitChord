package com.music.bitchord.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.music.bitchord.data.lyrics.LyricLine

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
 * A fully expanded ModalBottomSheet can consume the Initial down for its
 * short post-fling settle. In that one case a child clickable never receives
 * an eligible down, even though the listener tapped a lyric row. Observe that
 * consumed gesture at the list root and relay only a stationary tap to the
 * same row action; ordinary unconsumed taps stay owned by the row's clickable.
 */
@Composable
internal fun Modifier.seekLyricsWhenSheetConsumesTap(
    listState: LazyListState,
    lines: List<LyricLine>,
    enabled: Boolean,
    canPick: Boolean,
    picking: Boolean,
    onSeek: (Long) -> Unit,
    onPick: (Int) -> Unit,
    onTogglePick: (Int) -> Unit,
): Modifier {
    val currentSeek = rememberUpdatedState(onSeek)
    val currentPick = rememberUpdatedState(onPick)
    val currentTogglePick = rememberUpdatedState(onTogglePick)
    return pointerInput(listState, lines, enabled, canPick, picking) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            // Normal taps remain entirely with Modifier.clickable / combinedClickable.
            if (!down.isConsumed) return@awaitEachGesture

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
            if (moved) return@awaitEachGesture

            // LazyListItemInfo offsets are in the LazyColumn's local viewport
            // coordinates, the same space as this pointer's position.
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                down.position.y >= it.offset.toFloat() &&
                    down.position.y < (it.offset + it.size).toFloat()
            } ?: return@awaitEachGesture
            val index = item.index
            val line = lines.getOrNull(index) ?: return@awaitEachGesture
            if (line.isGap && picking) return@awaitEachGesture

            val heldFor = (up.uptimeMillis - down.uptimeMillis).coerceAtLeast(0L)
            if (canPick && heldFor >= longPressTimeout) {
                currentPick.value(index)
            } else if (picking) {
                currentTogglePick.value(index)
            } else {
                currentSeek.value(line.timeMs)
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
