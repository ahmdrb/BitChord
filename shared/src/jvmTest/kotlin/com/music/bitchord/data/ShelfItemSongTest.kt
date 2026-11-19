package com.music.bitchord.data

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShelfItemSongTest {

    @Test
    fun playableSongKeepsExplicitAndVideoFlagsFromItsShelfCard() {
        val song = ShelfItem(
            title = "Explicit track",
            subtitle = "Song • Example Artist",
            thumbnailUrl = "https://example.invalid/art.jpg",
            videoId = "explicit-track",
            browseId = null,
            isVideo = true,
            isExplicit = true,
        ).toPlayableSong()

        assertEquals("explicit-track", song?.videoId)
        assertEquals("Example Artist", song?.artist)
        assertEquals(true, song?.isVideo)
        assertEquals(true, song?.isExplicit)
    }

    @Test
    fun collectionCardWithoutVideoIdDoesNotBecomeAPlayableSong() {
        val item = ShelfItem(
            title = "Album",
            subtitle = "Artist",
            thumbnailUrl = null,
            videoId = null,
            browseId = "MPRE123",
        )

        assertNull(item.toPlayableSong())
    }

    @Test
    fun duplicateUnbadgedSongCannotEraseAnExplicitFlag() {
        val songs = listOf(
            Song(videoId = "same-track", title = "Track", artist = "Artist", thumbnailUrl = null, isExplicit = true),
            Song(videoId = "same-track", title = "Track", artist = "Artist", thumbnailUrl = null),
        )

        val result = songs.distinctByVideoIdKeepingExplicit()

        assertEquals(1, result.size)
        assertEquals(true, result.single().isExplicit)
    }

    @Test
    fun laterExplicitDuplicatePromotesAnEarlierUnbadgedSong() {
        val songs = listOf(
            Song(videoId = "same-track", title = "Track", artist = "Artist", thumbnailUrl = null),
            Song(videoId = "same-track", title = "Track", artist = "Artist", thumbnailUrl = null, isExplicit = true),
        )

        assertEquals(true, songs.distinctByVideoIdKeepingExplicit().single().isExplicit)
    }
}
