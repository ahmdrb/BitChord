package com.music.bitchord

import com.music.bitchord.data.PersonalLibraryStore
import com.music.bitchord.data.forArtistInAddedOrder
import com.music.bitchord.data.normalizePersonalArtistOrder
import com.music.bitchord.data.addingSongToArtists
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.ui.screens.sortedForDetail
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

        // Exercise the same helper used by PersonalLibraryStore.addSong,
        // rather than manually assigning the expected position in this test.
        val afterAdding = globalSongs.addingSongToArtists(
            song = globallyEarlierSaturn.song,
            artistBrowseIds = setOf(sza),
        )

        val artistSongs = afterAdding.forArtistInAddedOrder(sza).map { it.song }
        val insertionOrder = listOf("open-arms", "nobody-gets-me", "snooze", "kill-bill", "saturn")
        assertEquals(insertionOrder, artistSongs.map { it.videoId })
        assertEquals(insertionOrder, artistSongs.sortedForDetail(SongSort.DEFAULT).map { it.videoId })
        assertEquals(insertionOrder, artistSongs.sortedForDetail(SongSort.DATE_ADDED_ASC).map { it.videoId })
        assertEquals(insertionOrder.reversed(), artistSongs.sortedForDetail(SongSort.DATE_ADDED_DESC).map { it.videoId })
    }

    @Test
    fun globallyEarlierSongAppendsAfterFrankOceanMembershipsWithMissingPosition() {
        val frankOcean = "artist-frank-ocean"
        // Thinkin Bout You already exists globally, before the other tracks.
        val globallyEarlierThinkin = PersonalLibraryStore.PersonalSong(
            song = Song(
                videoId = "thinkin-bout-you",
                title = "Thinkin Bout You",
                artist = "Frank Ocean",
                thumbnailUrl = null,
            ),
        )
        val ivy = PersonalLibraryStore.PersonalSong(
            song = Song("ivy", "Ivy", "Frank Ocean", null),
            artistBrowseIds = setOf(frankOcean),
            artistOrder = mapOf(frankOcean to 0L),
        )
        val whiteFerrari = PersonalLibraryStore.PersonalSong(
            song = Song("white-ferrari", "White Ferrari", "Frank Ocean", null),
            artistBrowseIds = setOf(frankOcean),
            artistOrder = mapOf(frankOcean to 1L),
        )
        // Simulate an older/partially migrated membership whose order map is absent.
        val pinkAndWhite = PersonalLibraryStore.PersonalSong(
            song = Song("pink-and-white", "Pink + White", "Frank Ocean", null),
            artistBrowseIds = setOf(frankOcean),
        )

        val afterAdding = listOf(
            globallyEarlierThinkin,
            ivy,
            whiteFerrari,
            pinkAndWhite,
        ).addingSongToArtists(
            song = globallyEarlierThinkin.song,
            artistBrowseIds = setOf(frankOcean),
        )

        assertEquals(
            listOf("ivy", "white-ferrari", "pink-and-white", "thinkin-bout-you"),
            afterAdding.forArtistInAddedOrder(frankOcean).map { it.song.videoId },
        )
        assertEquals(2L, afterAdding.first { it.song.videoId == "pink-and-white" }.artistOrder[frankOcean])
        assertEquals(3L, afterAdding.first { it.song.videoId == "thinkin-bout-you" }.artistOrder[frankOcean])
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
