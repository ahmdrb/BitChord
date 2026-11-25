package com.music.bitchord.ui.player

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHistoryDisplayTest {
    private fun song(id: String, tier: QueueTier = QueueTier.CONTEXT) =
        Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null, queueTier = tier)

    @Test
    fun `history visibility follows the user preference without changing upcoming sections`() {
        val queue = listOf(song("old"), song("current"), song("manual", QueueTier.USER_QUEUE),
            song("album"), song("radio", QueueTier.AUTOPLAY))
        val visible = splitQueue(queue, 1, showHistory = true)
        val hidden = splitQueue(queue, 1, showHistory = false)

        assertEquals(listOf(0), visible.history.map { it.timelineIndex })
        assertTrue(hidden.history.isEmpty())
        assertEquals(visible.nowPlaying, hidden.nowPlaying)
        assertEquals(visible.user, hidden.user)
        assertEquals(visible.context, hidden.context)
        assertEquals(visible.autoplay, hidden.autoplay)
    }

    @Test
    fun `advancing adds the old current song and going back removes future history`() {
        val queue = listOf(song("a"), song("b"), song("c"))
        assertEquals(listOf("a", "b"), splitQueue(queue, 2, true).history.map { it.song.videoId })
        val back = splitQueue(queue, 1, true)
        assertEquals(listOf("a"), back.history.map { it.song.videoId })
        assertEquals("b", back.nowPlaying?.song?.videoId)
        assertEquals(listOf(2), back.context.map { it.timelineIndex })
    }

    @Test
    fun `duplicates have distinct keys and retain their playback indices`() {
        val queue = List(4) { song("same") }
        val tracks = splitQueue(queue, 2, true)
        val rows = tracks.history + listOfNotNull(tracks.nowPlaying) + tracks.context
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.timelineIndex })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `trimmed history uses the new timeline indices`() {
        val queue = List(28) { song("$it") }
        val tracks = splitQueue(queue.drop(2), 25, true)
        assertEquals(25, tracks.history.size)
        assertEquals("2", tracks.history.first().song.videoId)
        assertEquals(0, tracks.history.first().timelineIndex)
        assertEquals("27", tracks.nowPlaying?.song?.videoId)
    }

    @Test
    fun `empty invalid and first-song queues have no history`() {
        assertTrue(splitQueue(emptyList(), 0, true).history.isEmpty())
        assertTrue(splitQueue(listOf(song("a")), -1, true).history.isEmpty())
        assertTrue(splitQueue(listOf(song("a")), 1, true).history.isEmpty())
        assertTrue(splitQueue(listOf(song("a")), 0, true).history.isEmpty())
    }

    @Test
    fun `selectable rows exclude current and preserve section order and timeline indices`() {
        val queue = listOf(song("old"), song("current"), song("manual", QueueTier.USER_QUEUE),
            song("album"), song("radio", QueueTier.AUTOPLAY))

        assertEquals(listOf(0, 2, 3, 4), splitQueue(queue, 1, true).rows.map { it.timelineIndex })
        assertEquals(listOf(2, 3, 4), splitQueue(queue, 1).rows.map { it.timelineIndex })
    }

    @Test
    fun `each upcoming section can be the first selectable section`() {
        val current = song("current")
        assertEquals(listOf(1), splitQueue(listOf(current, song("manual", QueueTier.USER_QUEUE)), 0).rows.map { it.timelineIndex })
        assertEquals(listOf(1), splitQueue(listOf(current, song("album")), 0).rows.map { it.timelineIndex })
        assertEquals(listOf(1), splitQueue(listOf(current, song("radio", QueueTier.AUTOPLAY)), 0).rows.map { it.timelineIndex })
    }

    @Test
    fun `last current song leaves only enabled history selectable`() {
        val queue = listOf(song("old"), song("current"))
        assertEquals(listOf(0), splitQueue(queue, 1, true).rows.map { it.timelineIndex })
        assertTrue(splitQueue(queue, 1).rows.isEmpty())
        assertTrue(splitQueue(listOf(song("current")), 0).rows.isEmpty())
        assertTrue(splitQueue(emptyList(), 0).rows.isEmpty())
    }
    @Test
    fun `shuffle history uses played entry IDs and removes them from upcoming sections`() {
        val queue = listOf("a", "b", "c", "d", "e").map { id ->
            song(id).copy(queueEntryId = id)
        }

        val tracks = splitQueue(
            queue = queue,
            currentIndex = 1,
            showHistory = true,
            shuffleEnabled = true,
            queueHistoryEntryIds = listOf("d", "a"),
        )

        assertEquals(listOf("d", "a"), tracks.history.map { it.song.videoId })
        assertEquals(listOf(3, 0), tracks.history.map { it.timelineIndex })
        assertEquals(listOf("c", "e"), tracks.context.map { it.song.videoId })
        assertEquals("b", tracks.nowPlaying?.song?.videoId)
    }

    @Test
    fun `shuffle keeps every non-current row in original timeline order after a backwards jump`() {
        val queue = listOf(song("a"), song("b"), song("c"), song("d"), song("e"))

        val currentAtThree = splitQueue(queue, 3, showHistory = true, shuffleEnabled = true)
        assertEquals(listOf(0, 1, 2, 4), currentAtThree.context.map { it.timelineIndex })
        assertTrue(currentAtThree.history.isEmpty())

        // A shuffle transition can select an item at an earlier physical index.
        // That does not mean the rows before it were played or should disappear.
        val currentAtOne = splitQueue(queue, 1, showHistory = true, shuffleEnabled = true)
        assertEquals(listOf(0, 2, 3, 4), currentAtOne.context.map { it.timelineIndex })
        assertTrue(currentAtOne.history.isEmpty())
        assertEquals("b", currentAtOne.nowPlaying?.song?.videoId)
    }

}
