package com.music.bitchord

import com.music.bitchord.data.PersonalLibraryStore
import com.music.bitchord.data.forArtistInAddedOrder
import com.music.bitchord.data.nextArtistMembershipOrder
import com.music.bitchord.data.normalizePersonalArtistOrder
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalArtistOrderTest {
    private val sza = "artist-sza"

    private fun entry(
        id: String,
        title: String,
        artists: Set<String> = emptySet(),
        order: Long? = null,
    ) = PersonalLibraryStore.PersonalSong(
        song = Song(videoId = id, title = title, artist = "SZA", thumbnailUrl = null),
        artistBrowseIds = artists,
        artistOrder = order?.let { mapOf(sza to it) }.orEmpty(),
    )

    @Test
    fun addingPreviouslySavedSongAppendsToArtistMembershipOrder() {
        // Saturn was already in the global Songs collection, so its global
        // position precedes the four tracks that were assigned to SZA first.
        val globallyEarlierSaturn = entry("saturn", "Saturn")
        val existingArtistSongs = listOf(
            entry("open-arms", "Open Arms", setOf(sza), 0),
            entry("nobody-gets-me", "Nobody Gets Me", setOf(sza), 1),
            entry("snooze", "Snooze", setOf(sza), 2),
            entry("kill-bill", "Kill Bill", setOf(sza), 3),
        )
        val globalSongs = listOf(globallyEarlierSaturn) + existingArtistSongs

        val newPosition = globalSongs.nextArtistMembershipOrder(sza)
        assertEquals(4L, newPosition)

        val assignedSaturn = globallyEarlierSaturn.copy(
            artistBrowseIds = setOf(sza),
            artistOrder = mapOf(sza to newPosition),
        )
        val afterAdding = globalSongs.map {
            if (it.song.videoId == "saturn") assignedSaturn else it
        }

        assertEquals(
            listOf("open-arms", "nobody-gets-me", "snooze", "kill-bill", "saturn"),
            afterAdding.forArtistInAddedOrder(sza).map { it.song.videoId },
        )
    }

    @Test
    fun oldMembershipsMigrateWithoutChangingTheirExistingOrder() {
        val legacy = listOf(
            entry("open-arms", "Open Arms", setOf(sza)),
            entry("nobody-gets-me", "Nobody Gets Me", setOf(sza)),
            entry("kill-bill", "Kill Bill", setOf(sza)),
        )

        val migrated = normalizePersonalArtistOrder(legacy)

        assertEquals(listOf(0L, 1L, 2L), migrated.map { it.artistOrder[sza] })
        assertEquals(legacy.map { it.song.videoId }, migrated.forArtistInAddedOrder(sza).map { it.song.videoId })
    }

    @Test
    fun sameSongCanKeepDifferentPositionsInDifferentArtistCollections() {
        val collaboration = PersonalLibraryStore.PersonalSong(
            song = Song(videoId = "collab", title = "Collab", artist = "A, B", thumbnailUrl = null),
            artistBrowseIds = setOf("artist-a", "artist-b"),
            artistOrder = mapOf("artist-a" to 5L, "artist-b" to 1L),
        )
        assertEquals(5L, collaboration.artistOrder["artist-a"])
        assertEquals(1L, collaboration.artistOrder["artist-b"])
    }
}
