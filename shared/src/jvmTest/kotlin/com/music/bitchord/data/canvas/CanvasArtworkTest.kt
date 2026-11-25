package com.music.bitchord.data.canvas

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasArtworkTest {
    @Test
    fun identifiedTrackRequiresBothTitleAndArtist() {
        val exact = CanvasArtwork(
            url = "https://example.test/canvas.mp4",
            title = "Song",
            artist = "Artist",
        )
        val missingTitle = exact.copy(title = null)
        val missingArtist = exact.copy(artist = null)

        assertTrue(exact.matchesIdentifiedTrack("Song", "Artist", null))
        assertFalse(missingTitle.matchesIdentifiedTrack("Song", "Artist", null))
        assertFalse(missingArtist.matchesIdentifiedTrack("Song", "Artist", null))
    }

    @Test
    fun identifiedTrackStillRejectsMismatchedMetadata() {
        val candidate = CanvasArtwork(
            url = "https://example.test/canvas.mp4",
            title = "Different Song",
            artist = "Artist",
        )

        assertFalse(candidate.matchesIdentifiedTrack("Song", "Artist", null))
    }
}
