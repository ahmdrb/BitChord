package com.music.bitchord.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.WeakHashMap

/**
 * Shuffle changes playback traversal, never the queue's displayed order.
 *
 * The player's timeline stays in the order the listener arranged it. An anchored
 * [ShuffleOrder] supplies a random next/previous path over those same timeline
 * entries, preserving the current track and keeping newly inserted queue items
 * in the unplayed portion of the path.
 */
internal class QueueShuffleOrderState(
    var indices: IntArray,
    var anchorIndex: Int,
)

internal class AnchoredQueueShuffleOrder(
    private val state: QueueShuffleOrderState,
) : ShuffleOrder {
    internal constructor(indices: IntArray, anchorIndex: Int) :
        this(QueueShuffleOrderState(indices.copyOf(), anchorIndex))

    internal fun advanceAnchor(index: Int) {
        state.anchorIndex = index
    }

    internal fun nextIndex(index: Int): Int {
        val position = state.indices.indexOf(index)
        return if (position >= 0) {
            state.indices.getOrNull(position + 1) ?: C.INDEX_UNSET
        } else {
            C.INDEX_UNSET
        }
    }

    override fun getLength(): Int = state.indices.size

    override fun getNextIndex(index: Int): Int = nextIndex(index)

    override fun getPreviousIndex(index: Int): Int {
        val position = state.indices.indexOf(index)
        return if (position > 0) state.indices[position - 1] else C.INDEX_UNSET
    }

    override fun getFirstIndex(): Int = state.indices.firstOrNull() ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = state.indices.lastOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (insertionCount <= 0) return AnchoredQueueShuffleOrder(state)

        val oldAnchor = state.anchorIndex
        val shifted = state.indices.mapTo(mutableListOf()) { index ->
            if (index >= insertionIndex) index + insertionCount else index
        }
        val newAnchor = if (oldAnchor >= insertionIndex) oldAnchor + insertionCount else oldAnchor
        val anchorPosition = if (newAnchor == C.INDEX_UNSET) -1 else shifted.indexOf(newAnchor)
        val insertedBeforeCurrent = oldAnchor != C.INDEX_UNSET && insertionIndex <= oldAnchor
        val minimum = if (insertedBeforeCurrent) 0 else anchorPosition + 1
        val maximum = if (insertedBeforeCurrent) anchorPosition else shifted.size
        val insertionAt = if (minimum <= maximum) {
            kotlin.random.Random.nextInt(minimum, maximum + 1)
        } else {
            shifted.size
        }
        shifted.addAll(insertionAt, (insertionIndex until insertionIndex + insertionCount).toList().shuffled())

        state.indices = shifted.toIntArray()
        state.anchorIndex = newAnchor
        return AnchoredQueueShuffleOrder(state)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val from = indexFrom.coerceAtLeast(0)
        val to = indexToExclusive.coerceAtMost(state.indices.size)
        if (to <= from) return AnchoredQueueShuffleOrder(state)

        val removed = to - from
        state.indices = state.indices
            .filterNot { it in from until to }
            .map { index -> if (index >= to) index - removed else index }
            .toIntArray()
        state.anchorIndex = when {
            state.anchorIndex < from -> state.anchorIndex
            state.anchorIndex >= to -> state.anchorIndex - removed
            else -> C.INDEX_UNSET
        }
        return AnchoredQueueShuffleOrder(state)
    }

    override fun cloneAndClear(): ShuffleOrder {
        state.indices = intArrayOf()
        state.anchorIndex = C.INDEX_UNSET
        return AnchoredQueueShuffleOrder(state)
    }
}

object QueueShuffle {

    private val _enabled = MutableStateFlow(false)
    private val shuffleStates = WeakHashMap<ExoPlayer, QueueShuffleOrderState>()

    /** Whether playback currently follows a random path through the queue. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) boundedUserQueueOriginal = emptyList()
    }

    /** Apply or remove playback-only shuffle without changing any media-item positions. */
    fun applyPlaybackMode(player: ExoPlayer, enabled: Boolean) {
        if (!enabled) {
            player.shuffleModeEnabled = false
            player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
            shuffleStates.remove(player)
            return
        }

        val state = shuffleStates[player]
            ?.takeIf { it.indices.size == player.mediaItemCount }
            ?: newShuffleState(player).also { shuffleStates[player] = it }
        val current = player.currentMediaItemIndex
        if (current in 0 until player.mediaItemCount) state.anchorIndex = current
        player.setShuffleOrder(AnchoredQueueShuffleOrder(state))
        player.shuffleModeEnabled = true
    }

    /** Start a fresh random traversal after a newly selected/current queue item. */
    fun resetPlaybackOrder(player: ExoPlayer) {
        if (!_enabled.value) {
            applyPlaybackMode(player, false)
            return
        }
        val state = newShuffleState(player)
        shuffleStates[player] = state
        player.setShuffleOrder(AnchoredQueueShuffleOrder(state))
        player.shuffleModeEnabled = true
    }

    /**
     * Retain the current permutation when the expected shuffled item becomes current.
     * A direct row selection or a newly restored queue starts a fresh path instead.
     */
    fun onMediaItemTransition(player: ExoPlayer) {
        if (!_enabled.value || player.mediaItemCount == 0) return
        val current = player.currentMediaItemIndex
        if (current !in 0 until player.mediaItemCount) return

        val state = shuffleStates[player]
        if (state == null || state.indices.size != player.mediaItemCount) {
            resetPlaybackOrder(player)
            return
        }

        val expected = AnchoredQueueShuffleOrder(state).nextIndex(state.anchorIndex)
        if (current == expected) {
            state.anchorIndex = current
        } else {
            resetPlaybackOrder(player)
        }
    }

    private fun newShuffleState(player: ExoPlayer): QueueShuffleOrderState {
        val count = player.mediaItemCount
        val current = player.currentMediaItemIndex.takeIf { it in 0 until count } ?: C.INDEX_UNSET
        val past = if (current == C.INDEX_UNSET) emptyList() else (0..current).toList()
        val upcomingStart = if (current == C.INDEX_UNSET) 0 else current + 1
        val upcoming = (upcomingStart until count).toList().shuffled()
        return QueueShuffleOrderState((past + upcoming).toIntArray(), current)
    }

    /** Original order for the USER_QUEUE block when the bounded QueueWindow owns CONTEXT. */
    @Volatile private var boundedUserQueueOriginal: List<String> = emptyList()

    /** Entry IDs in their pre-shuffle order. Empty while shuffle is off. */
    @Volatile private var original: List<String> = emptyList()

    /** Provenance and metadata for a canonical album/playlist queue. */
    internal class CanonicalCtx(
        val items: List<MediaItem>,
        val byEntry: Map<String, Int>,
        val byMediaId: Map<String, Int>,
    )

    @Volatile private var ctx: CanonicalCtx? = null

    /** Canonical context MediaItems for the active album/playlist. */
    internal var canonicalContext: List<MediaItem>
        get() = ctx?.items ?: emptyList()
        set(value) {
            if (value.isEmpty()) clearCanonicalContext() else setCanonicalContext(value)
        }

    /** Callback to check if CrossfadeController is currently in a transition (ARMING/FADING/etc.). */
    internal var isCrossfadeTransitioning: (() -> Boolean)? = null

    fun setCanonicalContext(items: List<MediaItem>) {
        val byEntry = HashMap<String, Int>(items.size)
        val byMediaId = HashMap<String, Int>(items.size)
        val stamped = items.mapIndexed { idx, item ->
            val entryId = item.queueEntryId ?: UUID.randomUUID().toString()
            val stampedItem = withQueueMetadata(item, newEntryId = entryId, newCanonicalIndex = idx)
            byEntry[entryId] = idx
            byMediaId.putIfAbsent(item.mediaId, idx)
            stampedItem
        }
        ctx = CanonicalCtx(stamped, byEntry, byMediaId)
    }

    fun clearCanonicalContext() {
        ctx = null
    }

    /**
     * Checks if the active [canonicalContext] matches the player's active queue.
     *
     * Only validates when [ctx] is non-empty and the currently playing item is
     * marked [QueueTier.CONTEXT]. If the current item is [QueueTier.USER_QUEUE]
     * or [QueueTier.AUTOPLAY], or if the queue is empty / in transition, the context
     * is left alone.
     */
    internal fun isCanonicalContextValid(player: Player, c: CanonicalCtx? = ctx): Boolean {
        val currentCtx = c ?: return false
        if (currentCtx.items.isEmpty()) return false
        val count = player.mediaItemCount
        val cur = player.currentMediaItemIndex
        if (cur !in 0 until count) return true

        val currentItem = player.getMediaItemAt(cur)
        if (currentItem.queueTier != QueueTier.CONTEXT) {
            return true
        }

        val entryId = currentItem.queueEntryId
        return if (entryId != null) {
            currentCtx.byEntry.containsKey(entryId)
        } else {
            currentCtx.byMediaId.containsKey(currentItem.mediaId)
        }
    }

    fun getCanonicalIndex(item: MediaItem): Int? = getCanonicalIndex(item, ctx)

    internal fun getCanonicalIndex(item: MediaItem, c: CanonicalCtx?): Int? {
        val extraIndex = item.canonicalIndex
        if (extraIndex != null && extraIndex >= 0) return extraIndex
        val currentCtx = c ?: return null
        val entryId = item.queueEntryId
        if (entryId != null) {
            val fromProv = currentCtx.byEntry[entryId]
            if (fromProv != null) return fromProv
        }
        return currentCtx.byMediaId[item.mediaId]
    }

    fun withQueueMetadata(
        item: MediaItem,
        newEntryId: String? = item.queueEntryId,
        newCanonicalIndex: Int? = item.canonicalIndex,
        newTier: QueueTier = item.queueTier,
        newContextCycleId: Long? = item.contextCycleId,
    ): MediaItem {
        val currentExtras = item.mediaMetadata.extras ?: Bundle()
        val newExtras = Bundle(currentExtras).apply {
            if (newEntryId != null) putString(EXTRA_QUEUE_ENTRY_ID, newEntryId)
            if (newCanonicalIndex != null) putInt(EXTRA_CANONICAL_INDEX, newCanonicalIndex)
            if (newContextCycleId != null) putLong(EXTRA_CONTEXT_CYCLE_ID, newContextCycleId)
            putString(EXTRA_QUEUE_TIER, newTier.name)
        }
        val newMetadata = item.mediaMetadata.buildUpon().setExtras(newExtras).build()
        val newItem = item.buildUpon().setMediaMetadata(newMetadata).build()
        if (newEntryId != null) mediaItemEntryIds[newItem] = newEntryId
        if (newCanonicalIndex != null) mediaItemCanonicalIndices[newItem] = newCanonicalIndex
        mediaItemTiers[newItem] = newTier
        return newItem
    }

    /**
     * Checks if a transition to the next item in the queue is imminent
     * (either actively arming/blending in CrossfadeController or within 5 seconds of track completion).
     */
    internal fun isNextItemImminent(player: Player): Boolean {
        if (isCrossfadeTransitioning?.invoke() == true) return true
        val duration = runCatching { player.duration }.getOrDefault(0L)
        val position = runCatching { player.currentPosition }.getOrDefault(0L)
        if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET && position >= 0) {
            val remaining = duration - position
            if (remaining <= 5000L) return true
        }
        return false
    }

    /**
     * Shuffle only the physical USER_QUEUE block while a bounded QueueWindow
     * owns the CONTEXT order. The selected next item is left alone when a
     * crossfade or track ending makes it imminent.
     */
    internal fun applyBoundedUserQueueShuffle(player: Player, enabled: Boolean) {
        val items = player.queueItems()
        val current = player.currentMediaItemIndex
        val start = current + 1
        if (start !in items.indices || items[start].queueTier != QueueTier.USER_QUEUE) {
            if (!enabled) boundedUserQueueOriginal = emptyList()
            return
        }
        var end = start
        while (end < items.size && items[end].queueTier == QueueTier.USER_QUEUE) end++
        val section = items.subList(start, end)
        val keys = section.map { it.key() }
        if (enabled) {
            if (boundedUserQueueOriginal.isEmpty() || boundedUserQueueOriginal.none { it in keys }) {
                boundedUserQueueOriginal = keys
            }
            val protectedCount = if (isNextItemImminent(player)) 1 else 0
            val fixed = section.take(protectedCount)
            val movable = section.drop(protectedCount)
            val shuffled = shuffledSection(movable.indices.toList()).map(movable::get)
            replaceChanged(player, start, end, fixed + shuffled)
        } else {
            val restored = restoreOrder(keys, boundedUserQueueOriginal).map(section::get)
            replaceChanged(player, start, end, restored)
            boundedUserQueueOriginal = emptyList()
        }
    }

    fun toggle(player: Player) {
        if (player !is ExoPlayer) {
            // Retain the in-memory Player adapter used by the legacy pure
            // queue-edit tests. The Android service always supplies ExoPlayer
            // and therefore takes the playback-only ShuffleOrder path below.
            val c = ctx
            if (c != null && !isCanonicalContextValid(player, c)) clearCanonicalContext()
            if (_enabled.value) restore(player) else shuffle(player)
            return
        }
        val target = !_enabled.value
        setEnabled(target)
        AppSettings.setShuffleEnabled(target)
        applyPlaybackMode(player, target)
    }

    /**
     * Turns shuffle on without touching the current queue — for the Shuffle
     * button on an album or playlist page, where the queue it applies to is the
     * one about to replace this one. [playSongs] builds that one shuffled.
     */
    fun enableForNextQueue() {
        original = emptyList()
        boundedUserQueueOriginal = emptyList()
        clearCanonicalContext()
        _enabled.value = true
        AppSettings.setShuffleEnabled(true)
    }

    /**
     * [enableForNextQueue]'s counterpart, for the Play button beside it: an
     * album or playlist played from the top plays in its own order, whatever
     * shuffle was left on from the last queue (#1148).
     */
    fun disableForNextQueue() {
        original = emptyList()
        boundedUserQueueOriginal = emptyList()
        clearCanonicalContext()
        _enabled.value = false
        AppSettings.setShuffleEnabled(false)
    }

    /**
     * The order a queue should go in when it is started while shuffle is on:
     * the track the user picked leads, the rest follow at random. The order it
     * arrived in is remembered, so turning shuffle off restores it.
     */
    /**
     * Legacy helper for in-memory queue tests. Android playback no longer calls
     * this: real queue submissions preserve their source order and use
     * [AnchoredQueueShuffleOrder] for playback-only random traversal.
     */
    fun startingOrder(songs: List<Song>, startIndex: Int): List<Song> {
        require(songs.isNotEmpty() && startIndex in songs.indices)
        original = songs.map { "${it.queueEntryId ?: it.videoId}_${it.queueTier.name}" }
        val contextSongs = songs.filter { it.queueTier == QueueTier.CONTEXT }
        if (contextSongs.isNotEmpty()) {
            setCanonicalContext(contextSongs.mapIndexed { idx, s ->
                val entryId = s.queueEntryId ?: UUID.randomUUID().toString()
                withQueueMetadata(s.toMediaItem(), newEntryId = entryId, newCanonicalIndex = idx)
            })
        }
        boundedUserQueueOriginal = emptyList()
        val rest = songs.filterIndexed { i, _ -> i != startIndex }
        val userQueue = rest.filter { it.queueTier == QueueTier.USER_QUEUE }
        val shuffledUserQueue = shuffledSection(userQueue.indices.toList()).map(userQueue::get)
        val context = rest.filter { it.queueTier == QueueTier.CONTEXT }
        val shuffledContext = if (context.size > 1) {
            val shuffled = context.shuffled()
            if (shuffled == context) shuffled.drop(1) + shuffled.first() else shuffled
        } else context
        val autoplay = rest.filter { it.queueTier == QueueTier.AUTOPLAY }
        val shuffledAutoplay = shuffledSection(autoplay.indices.toList()).map(autoplay::get)
        return listOf(songs[startIndex]) + shuffledUserQueue + shuffledContext + shuffledAutoplay
    }

    /**
     * Rearranges unconsumed upcoming tracks after the playing track into a shuffled session.
     *
     * Invariants:
     * - The currently playing occurrence and preceding history occurrences remain untouched.
     * - Each upcoming tier is shuffled internally: USER_QUEUE, CONTEXT, then AUTOPLAY.
     * - History tracks are NEVER reintroduced into upcoming, preventing phantom duplicates in the context queue.
     * - Tier priority remains intact, so manual queue tracks stay ahead of collection tracks and AutoPlay.
     * - If the next item is imminent (within 5s or crossfade arming/fading), cur + 1 is preserved in place
     *   and shuffle applies from cur + 2 onward.
     */
    private fun shuffle(player: Player) {
        val c = ctx
        if (c != null && !isCanonicalContextValid(player, c)) {
            clearCanonicalContext()
        }
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val imminent = isNextItemImminent(player)
        val from = if (imminent) currentIndex + 2 else currentIndex + 1
        original = items.map { it.key() }

        if (currentIndex !in items.indices || from >= items.size) {
            _enabled.value = true
            AppSettings.setShuffleEnabled(true)
            return
        }

        // If canonicalContext is empty, discover it from the player's context tracks
        if (ctx == null) {
            val contextItems = items.filter { it.queueTier == QueueTier.CONTEXT }
            if (contextItems.isNotEmpty()) {
                setCanonicalContext(contextItems)
            }
        }

        val upcoming = items.drop(from)
        val userQueueIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE }
        val contextIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.CONTEXT }
        val autoplayIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

        val shuffledUserQueue = shuffledSection(userQueueIndices)
        val shuffledContext = shuffledSection(contextIndices)
        val shuffledAutoplay = shuffledSection(autoplayIndices)

        val order = shuffledUserQueue + shuffledContext + shuffledAutoplay
        val ok = reorder(player, from, order.toIntArray())
        if (ok) {
            _enabled.value = true
            AppSettings.setShuffleEnabled(true)
        }
    }

    /**
     * Randomises one queue section but never returns its unchanged order when
     * at least two tracks can move.
     */
    internal fun avoidIdentityShuffleMediaItems(
        original: List<MediaItem>,
        shuffled: List<MediaItem>,
    ): List<MediaItem> {
        if (original.size <= 1 || shuffled != original) return shuffled
        return shuffled.drop(1) + shuffled.first()
    }

    private fun shuffledSection(indices: List<Int>): List<Int> =
        avoidIdentityShuffle(indices, indices.shuffled())

    internal fun avoidIdentityShuffle(original: List<Int>, shuffled: List<Int>): List<Int> {
        if (original.size <= 1 || shuffled != original) return shuffled
        return shuffled.drop(1) + shuffled.first()
    }

    /**
     * Restores the queue to the full canonical collection order around the active track.
     * Preceding canonical tracks are placed in history before current track.
     * User-queued tracks remain pinned immediately behind current track.
     * Following canonical tracks fill the upcoming context queue.
     *
     * Applied via two-segment [Player.replaceMediaItems] (tail first, then history)
     * skipping common prefixes and suffixes via [replaceChanged] so that the currently
     * playing item and preloaded next items are completely untouched: no gap, no rebuffer,
     * and no playback position reset.
     *
     * If the next item is imminent (within 5s or crossfade arming/fading), cur + 1 is preserved
     * in place and restore applies from cur + 2 onward.
     */
    private fun restore(player: Player) {
        var c = ctx
        if (c != null && !isCanonicalContextValid(player, c)) {
            clearCanonicalContext()
            c = null
        }
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val imminent = isNextItemImminent(player)
        val from = if (imminent) currentIndex + 2 else currentIndex + 1
        if (currentIndex !in items.indices || currentIndex + 1 >= items.size) {
            original = emptyList()
            _enabled.value = false
            AppSettings.setShuffleEnabled(false)
            return
        }

        val currentItem = items[currentIndex]
        val isCurrentContext = currentItem.queueTier == QueueTier.CONTEXT
        var currentCanonicalIndex: Int? = null

        // If currently playing track is CONTEXT, use its canonical index directly.
        // If USER_QUEUE or AUTOPLAY, bypass getCanonicalIndex so a queued copy of an album song
        // doesn't falsely resolve, and scan backward for the most recent CONTEXT item.
        if (isCurrentContext) {
            currentCanonicalIndex = getCanonicalIndex(currentItem, c)
        } else {
            for (i in (currentIndex - 1) downTo 0) {
                val itm = items[i]
                if (itm.queueTier == QueueTier.CONTEXT) {
                    val cIdx = getCanonicalIndex(itm, c)
                    if (cIdx != null) {
                        currentCanonicalIndex = cIdx
                        break
                    }
                }
            }
            if (currentCanonicalIndex == null) {
                // Forward scan if session began on USER_QUEUE before any CONTEXT played
                for (i in (currentIndex + 1) until items.size) {
                    val itm = items[i]
                    if (itm.queueTier == QueueTier.CONTEXT) {
                        val cIdx = getCanonicalIndex(itm, c)
                        if (cIdx != null) {
                            currentCanonicalIndex = cIdx - 1
                            break
                        }
                    }
                }
            }
        }

        val resolvedCurrentIndex = if (isCurrentContext) {
            currentCanonicalIndex ?: getCanonicalIndex(currentItem, c)
        } else {
            currentCanonicalIndex
        }

        val canonicalList = c?.items ?: emptyList()

        // Fallback for ad-hoc / radio queues without canonical context
        if (resolvedCurrentIndex == null || canonicalList.isEmpty()) {
            if (from < items.size) {
                val upcoming = items.drop(from)
                val currentId = if (imminent) items[currentIndex + 1].key() else currentItem.key()
                val restored = restoreOrder(
                    upcoming = upcoming.map { it.key() },
                    original = original,
                    currentId = currentId,
                )
                val order = sections(restored, upcoming)
                reorder(player, from, order.toIntArray())
            }
            original = emptyList()
            setEnabled(false)
            AppSettings.setShuffleEnabled(false)
            return
        }

        // Map all existing context MediaItems by canonicalIndex so we preserve existing instances & metadata
        val existingByCanonical = HashMap<Int, MediaItem>()
        // Context rows the canonical order can't place (no index, or a second
        // copy of one). They stay in the queue on the side of the current
        // track they were on, rather than being dropped.
        val unplacedHistory = ArrayList<MediaItem>()
        val unplacedUpcoming = ArrayList<MediaItem>()
        for (i in items.indices) {
            if (i == currentIndex) continue
            val item = items[i]
            if (item.queueTier == QueueTier.CONTEXT) {
                val idx = getCanonicalIndex(item, c)
                if (idx != null && idx in canonicalList.indices && !existingByCanonical.containsKey(idx)) {
                    existingByCanonical[idx] = item
                } else if (i < currentIndex) {
                    unplacedHistory += item
                } else {
                    unplacedUpcoming += item
                }
            }
        }

        val precedingRange = if (isCurrentContext) {
            0 until resolvedCurrentIndex
        } else {
            0 until (resolvedCurrentIndex + 1).coerceAtLeast(0)
        }
        val followingRange = ((resolvedCurrentIndex + 1).coerceAtLeast(0)) until canonicalList.size

        // Only rows still in the queue are reordered. Filling gaps from the
        // canonical list put back every track the listener had removed, and
        // re-grew history the service had already trimmed to its 25 rows.
        // Canonical context preceding the current track (history)
        val precedingContext = unplacedHistory + precedingRange.mapNotNull { existingByCanonical[it] }

        // Canonical context following the current track (upcoming)
        val followingContext = followingRange.mapNotNull { existingByCanonical[it] } + unplacedUpcoming

        // Played manual user queue tracks in history (before current track)
        val historyUserQueue = items.filterIndexed { idx, it -> idx < currentIndex && it.queueTier == QueueTier.USER_QUEUE }

        // Restore each non-canonical section to its pre-shuffle order. Tracks
        // added while shuffle was on are retained at the end of their section.
        val userQueue = restoreSectionOrder(
            items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.USER_QUEUE },
            original,
        )
        val autoplay = restoreSectionOrder(
            items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.AUTOPLAY },
            original,
        )

        val cur = player.currentMediaItemIndex

        if (imminent && currentIndex + 1 < items.size) {
            val nextItem = items[currentIndex + 1]
            val nextKey = nextItem.key()
            // nextItem stays in place at cur + 1. Exclude it from restored history and upcoming so it isn't duplicated.
            val cleanPreceding = precedingContext.filter { it.key() != nextKey }
            val cleanHistory = historyUserQueue + cleanPreceding

            val rawUpcoming = userQueue.filter { it.key() != nextKey } +
                followingContext.filter { it.key() != nextKey } +
                autoplay.filter { it.key() != nextKey }

            replaceChanged(player, cur + 2, player.mediaItemCount, rawUpcoming)
            if (cur > 0 || cleanHistory.isNotEmpty()) {
                replaceChanged(player, 0, cur, cleanHistory)
            }
        } else {
            val upcoming = userQueue + followingContext + autoplay
            val history = historyUserQueue + precedingContext

            replaceChanged(player, cur + 1, player.mediaItemCount, upcoming)
            if (cur > 0 || history.isNotEmpty()) {
                replaceChanged(player, 0, cur, history)
            }
        }

        original = emptyList()
        setEnabled(false)
        AppSettings.setShuffleEnabled(false)
    }

    /**
     * Where each of [upcoming] belongs once [original] is put back, as indices
     * into [upcoming].
     *
     * Each track still queued goes back to where it stood in the old order.
     * Whatever is left over was queued after the shuffle and was never part of
     * that order, so it keeps its place at the end. A track named by [original]
     * that has since been removed is simply skipped.
     *
     * When [currentId] is provided and found in [original], the restoration
     * begins strictly after the current track's position in [original] in
     * original forward positional order without wrapping around to the
     * beginning, preserving history and unplayed upcoming context items.
     */
    internal fun restoreOrder(
        upcoming: List<String>,
        original: List<String>,
        currentId: String? = null,
    ): List<Int> {
        val positions = HashMap<String, ArrayDeque<Int>>(upcoming.size)
        upcoming.forEachIndexed { index, id ->
            positions.getOrPut(id) { ArrayDeque() }.addLast(index)
        }
        val placed = BooleanArray(upcoming.size)
        val out = ArrayList<Int>(upcoming.size)

        val currentIndex = if (currentId != null) original.indexOf(currentId) else -1
        val searchOrder: Iterable<String> = if (currentIndex in 0 until original.size) {
            // Restore strictly forward after current track; do not wrap around to the beginning
            original.subList(currentIndex + 1, original.size)
        } else {
            original
        }

        for (id in searchOrder) {
            val index = positions[id]?.removeFirstOrNull() ?: continue
            placed[index] = true
            out += index
        }
        for (index in upcoming.indices) if (!placed[index]) out += index
        return out
    }

    /** Rebuild one non-canonical section in its original order, leaving newly added entries last. */
    private fun restoreSectionOrder(section: List<MediaItem>, original: List<String>): List<MediaItem> {
        if (section.size < 2) return section
        val keys = section.map { it.key() }
        val available = keys.toHashSet()
        val originalSection = original.filter { it in available }
        return restoreOrder(keys, originalSection).map(section::get)
    }

    /** [order] with sections strictly maintained: USER_QUEUE -> CONTEXT -> AUTOPLAY. */
    private fun sections(order: List<Int>, upcoming: List<MediaItem>): List<Int> =
        order.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE } +
            order.filter { upcoming[it].queueTier == QueueTier.CONTEXT } +
            order.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

    /**
     * Replaces items in [player] from [from] to [to] with [new], skipping any unchanged
     * common prefix and common suffix to avoid discarding preloaded buffers (e.g. for the next item)
     * and avoiding redundant timeline change events.
     *
     * Returns true if any items were replaced, false if the range was already identical.
     */
    internal fun replaceChanged(player: Player, from: Int, to: Int, new: List<MediaItem>): Boolean {
        if (from < 0 || to < from || to > player.mediaItemCount) return false
        val old = (from until to).map { player.getMediaItemAt(it).key() }
        val n = new.map { it.key() }
        var p = 0
        while (p < old.size && p < n.size && old[p] == n[p]) p++
        var s = 0
        while (s < old.size - p && s < n.size - p &&
            old[old.size - 1 - s] == n[n.size - 1 - s]
        ) s++
        if (p == old.size && p == n.size) return false
        player.replaceMediaItems(from + p, to - s, new.subList(p, n.size - s))
        return true
    }

    internal fun MediaItem.key(): String = "${queueEntryId ?: mediaId}_${queueTier.name}"

    internal val originalOrder: List<String> get() = original

    /**
     * Rearranges the live queue from [from] onwards, [order] naming where each
     * slot's new occupant is standing now.
     */
    internal fun reorder(player: Player, from: Int, order: IntArray): Boolean {
        if (order.isEmpty() || from < 0 || from + order.size > player.mediaItemCount) return false
        val target = List(order.size) { player.getMediaItemAt(from + order[it]) }
        replaceChanged(player, from, from + order.size, target)
        return true
    }

    private fun Player.queueItems(): List<MediaItem> =
        List(mediaItemCount) { getMediaItemAt(it) }
}

/** Repeat-all cycle identity for a bounded CONTEXT materialization. */
const val EXTRA_CONTEXT_CYCLE_ID = "bitchord.contextCycleId"

val MediaItem.contextCycleId: Long?
    get() = mediaMetadata.extras
        ?.takeIf { it.containsKey(EXTRA_CONTEXT_CYCLE_ID) }
        ?.getLong(EXTRA_CONTEXT_CYCLE_ID)
