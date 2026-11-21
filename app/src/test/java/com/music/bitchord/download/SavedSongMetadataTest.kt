package com.music.bitchord.download

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSongMetadataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun explicitFlagSurvivesDownloadMetadataSerialization() {
        val metadata = SavedSongMetadata(
            videoId = "explicit-track",
            title = "Track",
            artist = "Artist",
            uri = "file:///music/track.flac",
            isExplicit = true,
        )

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<SavedSongMetadata>(encoded)

        assertEquals(true, decoded.isExplicit)
    }

    @Test
    fun olderDownloadRecordsWithoutExplicitFieldStillDecode() {
        val oldRecord = """
            {
              "videoId": "old-track",
              "title": "Old track",
              "artist": "Artist",
              "uri": "file:///music/old-track.flac"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<SavedSongMetadata>(oldRecord)

        assertNull(decoded.isExplicit)
    }
}
