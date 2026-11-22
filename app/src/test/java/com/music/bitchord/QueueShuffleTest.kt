package com.music.bitchord

import androidx.media3.common.C
import com.music.bitchord.playback.AnchoredQueueShuffleOrder
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.queueStartIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reordering behind the shuffle toggle. Playing the queue is ExoPlayer's
 * job; getting the queue into the order it should play in is this one's.
 */
class QueueShuffleTest {

    /** The upcoming stretch of the queue as turning shuffle off leaves it. */
    private fun restored(upcoming: List<String>, original: List<String>): List<String> =
        QueueShuffle.restoreOrder(upcoming, original).map { upcoming[it] }

    @Test
    fun `shuffle keeps the tapped track at its visible queue position`() {
        assertEquals(3, queueStartIndex(requestedIndex = 3, itemCount = 5, shuffled = true))
    }

    @Test
    fun `shuffle off also keeps the tapped track at its visible queue position`() {
        assertEquals(3, queueStartIndex(requestedIndex = 3, itemCount = 5, shuffled = false))
    }

    @Test
    fun `playback shuffle follows a random traversal without changing queue indices`() {
        val order = AnchoredQueueShuffleOrder(
            indices = intArrayOf(2, 0, 3, 1),
            anchorIndex = 2,
        )

        val traversal = buildList {
            var index = order.getFirstIndex()
            while (index != C.INDEX_UNSET) {
                add(index)
                index = order.getNextIndex(index)
            }
        }

        assertEquals(listOf(2, 0, 3, 1), traversal)
        assertEquals(0, order.getPreviousIndex(3))
        assertEquals(3, traversal.distinct().size)
        assertEquals(setOf(0, 1, 2, 3), traversal.toSet())
    }

    @Test
    fun `playback shuffle returns unset for indices outside its queue`() {
        val order = AnchoredQueueShuffleOrder(indices = intArrayOf(0, 2, 1), anchorIndex = 0)

        assertEquals(C.INDEX_UNSET, order.getNextIndex(99))
        assertEquals(C.INDEX_UNSET, order.getPreviousIndex(99))
        assertEquals(C.INDEX_UNSET, order.getNextIndex(1))
    }

    @Test
    fun `inserted queue entries join the future random traversal without losing existing entries`() {
        val original = AnchoredQueueShuffleOrder(indices = intArrayOf(0, 3, 1, 2), anchorIndex = 0)
        val inserted = original.cloneAndInsert(2, 2) as AnchoredQueueShuffleOrder

        val traversal = buildList {
            var index = inserted.getFirstIndex()
            while (index != C.INDEX_UNSET) {
                add(index)
                index = inserted.getNextIndex(index)
            }
        }

        assertEquals(6, inserted.getLength())
        assertEquals(6, traversal.size)
        assertEquals((0..5).toSet(), traversal.toSet())
        assertEquals(0, traversal.first())
    }

    @Test
    fun `a random identity is rotated so the first shuffle always moves a track`() {
        assertEquals(
            listOf(1, 2, 0),
            QueueShuffle.avoidIdentityShuffle(
                original = listOf(0, 1, 2),
                shuffled = listOf(0, 1, 2),
            ),
        )
    }

    @Test
    fun `a genuinely shuffled order is kept`() {
        assertEquals(
            listOf(2, 0, 1),
            QueueShuffle.avoidIdentityShuffle(
                original = listOf(0, 1, 2),
                shuffled = listOf(2, 0, 1),
            ),
        )
    }

    @Test
    fun `a one track section remains unchanged`() {
        assertEquals(
            listOf(0),
            QueueShuffle.avoidIdentityShuffle(original = listOf(0), shuffled = listOf(0)),
        )
    }

    @Test
    fun `the queue goes back into the order it was queued in`() {
        assertEquals(
            listOf("b", "c", "d"),
            restored(upcoming = listOf("d", "b", "c"), original = listOf("a", "b", "c", "d")),
        )
    }

    @Test
    fun `an order already in place is left as it is`() {
        assertEquals(
            listOf("b", "c", "d"),
            restored(upcoming = listOf("b", "c", "d"), original = listOf("a", "b", "c", "d")),
        )
    }

    @Test
    fun `a queue holding the same track twice keeps both copies`() {
        assertEquals(
            listOf("b", "b", "c"),
            restored(upcoming = listOf("b", "c", "b"), original = listOf("a", "b", "b", "c")),
        )
    }

    @Test
    fun `tracks the old order does not name trail behind the ones it does`() {
        // "e" was queued after the shuffle, so the restored order says nothing
        // about it — it stays at the end rather than displacing anything.
        assertEquals(
            listOf("b", "c", "d", "e"),
            restored(upcoming = listOf("e", "d", "b", "c"), original = listOf("a", "b", "c", "d")),
        )
    }

    @Test
    fun `a track that has since been removed is skipped`() {
        assertEquals(
            listOf("b", "d"),
            restored(upcoming = listOf("d", "b"), original = listOf("a", "b", "c", "d")),
        )
    }

    @Test
    fun `shuffling then restoring returns the original running order`() {
        val original = ('a'..'j').map { it.toString() }
        repeat(50) {
            val upcoming = original.drop(1).shuffled()
            assertEquals(original.drop(1), restored(upcoming, original))
        }
    }

    /**
     * The queues this runs on are playlists, and a per-track linear search over
     * one is quadratic — the shape that used to hang the app. Ten thousand
     * tracks is a fraction of a second here and minutes if that ever comes back.
     */
    @Test
    fun `a very long queue is restored without a per-track search`() {
        val original = (0 until 10_000).map { it.toString() }
        val upcoming = original.shuffled()
        assertEquals(original, restored(upcoming, original))
    }
}
