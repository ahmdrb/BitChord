package com.music.bitchord.data

import android.content.Context
import android.net.Uri
import java.io.File
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * BitChord's independent, on-device Library. Editing this collection never
 * calls YouTube Music's add/remove-library endpoints or changes the Google account.
 */
object PersonalLibraryStore {
    data class PersonalSong(
        val song: Song,
        /** Artist cards this song belongs to in BitChord; empty means unassigned. */
        val artistBrowseIds: Set<String> = emptySet(),
        /**
         * Position at which this song was added to each artist collection.
         * A song may already exist in the global Songs list long before it is
         * assigned to an artist, so the global list position is not its
         * membership order for that artist.
         */
        val artistOrder: Map<String, Long> = emptyMap(),
    )

    data class PersonalPlaylist(
        val id: String,
        val title: String,
        val songs: List<Song> = emptyList(),
        val artworkPath: String? = null,
    )

    data class Snapshot(
        val artists: List<BrowseItem> = emptyList(),
        val songs: List<PersonalSong> = emptyList(),
        val playlists: List<PersonalPlaylist> = emptyList(),
    )

    private const val PREFS = "bitchord_personal_library"
    private const val KEY_ARTISTS = "artists"
    private const val KEY_SONGS = "songs"
    private const val KEY_PLAYLISTS = "playlists"

    private val lock = Any()
    private var initialized = false
    private lateinit var preferences: android.content.SharedPreferences
    private val mutableState = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = mutableState.asStateFlow()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mutableState.value = readSnapshot()
            initialized = true
        }
    }

    fun addPlaylist(title: String, song: Song? = null) = update { current ->
        val name = title.trim()
        if (name.isBlank()) current
        else current.copy(
            playlists = current.playlists + PersonalPlaylist(
                id = java.util.UUID.randomUUID().toString(),
                title = name,
                songs = listOfNotNull(song),
            ),
        )
    }

    /** Create a BitChord playlist populated from an external source, without editing the account library. */
    fun addPlaylistWithSongs(title: String, songs: List<Song>, artworkUrl: String? = null) = update { current ->
        val name = title.trim()
        if (name.isBlank()) current
        else current.copy(
            playlists = current.playlists + PersonalPlaylist(
                id = java.util.UUID.randomUUID().toString(),
                title = name,
                songs = songs.distinctByVideoIdKeepingExplicit(),
                artworkPath = artworkUrl?.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun removePlaylist(id: String) = update { current ->
        current.copy(playlists = current.playlists.filterNot { it.id == id })
    }

    /** Copy a gallery selection into app-private storage so it survives restarts. */
    fun savePlaylistArtwork(context: Context, id: String, uri: Uri) {
        val mimeExtension = context.contentResolver.getType(uri)
            ?.substringAfter('/', "jpg")
            ?.filter { it.isLetterOrDigit() }
            ?.take(8)
            ?.ifBlank { "jpg" } ?: "jpg"
        val directory = File(context.filesDir, "personal-playlist-artwork")
        if (!directory.exists() && !directory.mkdirs()) return
        val target = File(directory, "$id-${System.currentTimeMillis()}.$mimeExtension")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return
            target.toURI().toString()
        }.getOrNull() ?: run {
            target.delete()
            return
        }
        update { current ->
            current.copy(playlists = current.playlists.map { playlist ->
                if (playlist.id == id) playlist.copy(artworkPath = copied) else playlist
            })
        }
    }

    /** Returns true only when this call added a new track to the playlist. */
    fun addSongToPlaylist(id: String, song: Song): Boolean {
        var added = false
        update { current ->
            current.copy(playlists = current.playlists.map { playlist ->
                if (playlist.id != id) {
                    playlist
                } else {
                    val existing = playlist.songs.firstOrNull { it.videoId == song.videoId }
                    if (existing == null) {
                        added = true
                        playlist.copy(songs = playlist.songs + song)
                    } else if (existing.isExplicit != true && song.isExplicit == true) {
                        playlist.copy(
                            songs = playlist.songs.map { currentSong ->
                                if (currentSong.videoId == song.videoId) {
                                    currentSong.copy(isExplicit = true)
                                } else currentSong
                            },
                        )
                    } else {
                        playlist
                    }
                }
            })
        }
        return added
    }

    fun removeSongFromPlaylist(id: String, videoId: String) = update { current ->
        current.copy(playlists = current.playlists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(songs = playlist.songs.filterNot { it.videoId == videoId })
            } else playlist
        })
    }

    fun addArtist(artist: BrowseItem) = update { current ->
        if (current.artists.any { it.browseId == artist.browseId }) current
        else current.copy(artists = current.artists + artist.copy(type = BrowseType.ARTIST))
    }

    fun removeArtist(browseId: String) = update { current ->
        current.copy(
            artists = current.artists.filterNot { it.browseId == browseId },
            songs = current.songs.map { entry ->
                entry.copy(
                    artistBrowseIds = entry.artistBrowseIds - browseId,
                    artistOrder = entry.artistOrder - browseId,
                )
            },
        )
    }

    /** Add a song once, merging any selected artist memberships for collaborations. */
    fun addSong(song: Song, artistBrowseIds: Set<String>) = update { current ->
        current.copy(songs = current.songs.addingSongToArtists(song, artistBrowseIds))
    }

    fun removeSong(videoId: String) = update { current ->
        current.copy(songs = current.songs.filterNot { it.song.videoId == videoId })
    }

    /** Remove a song from one artist's page without deleting it from Songs. */
    fun removeSongFromArtist(videoId: String, artistBrowseId: String) = update { current ->
        current.copy(songs = current.songs.map { entry ->
            if (entry.song.videoId == videoId && artistBrowseId in entry.artistBrowseIds) {
                entry.copy(
                    artistBrowseIds = entry.artistBrowseIds - artistBrowseId,
                    artistOrder = entry.artistOrder - artistBrowseId,
                )
            } else entry
        })
    }

    private fun update(transform: (Snapshot) -> Snapshot) {
        synchronized(lock) {
            if (!initialized) return
            val next = transform(mutableState.value)
            mutableState.value = next
            preferences.edit()
                .putString(KEY_ARTISTS, JSONArray().apply {
                    next.artists.forEach { artist ->
                        put(JSONObject().apply {
                            put("browseId", artist.browseId)
                            put("title", artist.title)
                            put("subtitle", artist.subtitle)
                            put("thumbnailUrl", artist.thumbnailUrl ?: JSONObject.NULL)
                        })
                    }
                }.toString())
                .putString(KEY_SONGS, JSONArray().apply {
                    next.songs.forEach { entry ->
                        val song = entry.song
                        put(JSONObject().apply {
                            put("videoId", song.videoId)
                            put("title", song.title)
                            put("artist", song.artist)
                            put("thumbnailUrl", song.thumbnailUrl ?: JSONObject.NULL)
                            put("durationText", song.durationText ?: JSONObject.NULL)
                            put("artistId", song.artistId ?: JSONObject.NULL)
                            put("isExplicit", song.isExplicit ?: JSONObject.NULL)
                            put("personalArtistIds", JSONArray().apply {
                                entry.artistBrowseIds.forEach { artistId -> put(artistId) }
                            })
                            put("artistOrder", JSONObject().apply {
                                entry.artistOrder.forEach { (artistId, order) -> put(artistId, order) }
                            })
                        })
                    }
                }.toString())
                .putString(KEY_PLAYLISTS, JSONArray().apply {
                    next.playlists.forEach { playlist ->
                        put(JSONObject().apply {
                            put("id", playlist.id)
                            put("title", playlist.title)
                            put("artworkPath", playlist.artworkPath ?: JSONObject.NULL)
                            put("songs", JSONArray().apply {
                                playlist.songs.forEach { song ->
                                    put(JSONObject().apply {
                                        put("videoId", song.videoId)
                                        put("title", song.title)
                                        put("artist", song.artist)
                                        put("thumbnailUrl", song.thumbnailUrl ?: JSONObject.NULL)
                                        put("durationText", song.durationText ?: JSONObject.NULL)
                                        put("artistId", song.artistId ?: JSONObject.NULL)
                                        put("isExplicit", song.isExplicit ?: JSONObject.NULL)
                                    })
                                }
                            })
                        })
                    }
                }.toString())
                .apply()
        }
    }

    private fun readSnapshot(): Snapshot {
        val artists = runCatching {
            val array = JSONArray(preferences.getString(KEY_ARTISTS, "[]"))
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("browseId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                BrowseItem(
                    browseId = id,
                    title = item.optString("title"),
                    subtitle = item.optString("subtitle"),
                    thumbnailUrl = item.nullableString("thumbnailUrl"),
                    type = BrowseType.ARTIST,
                )
            }
        }.getOrDefault(emptyList())

        val songs = runCatching {
            val array = JSONArray(preferences.getString(KEY_SONGS, "[]"))
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("videoId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PersonalSong(
                    song = Song(
                        videoId = id,
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        thumbnailUrl = item.nullableString("thumbnailUrl"),
                        durationText = item.nullableString("durationText"),
                        artistId = item.nullableString("artistId"),
                        isExplicit = item.nullableBoolean("isExplicit"),
                    ),
                    artistBrowseIds = item.optJSONArray("personalArtistIds")?.let { ids ->
                        (0 until ids.length()).mapNotNull { ids.optString(it).takeIf { artistId -> artistId.isNotBlank() } }.toSet()
                    } ?: item.nullableString("personalArtistId")?.let { setOf(it) }.orEmpty(),
                    artistOrder = item.optJSONObject("artistOrder")?.let { savedOrder ->
                        buildMap {
                            val keys = savedOrder.keys()
                            while (keys.hasNext()) {
                                val artistId = keys.next()
                                val order = savedOrder.optLong(artistId, -1L)
                                if (artistId.isNotBlank() && order >= 0L) put(artistId, order)
                            }
                        }
                    }.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
        // Upgrade older personal-library records without changing their
        // already-visible artist order. New memberships added after this
        // migration are assigned their own per-artist position.
        val orderedSongs = normalizePersonalArtistOrder(songs)
        val playlists = runCatching {
            val array = JSONArray(preferences.getString(KEY_PLAYLISTS, "[]"))
            (0 until array.length()).mapNotNull playlistEntry@{ index ->
                val item = array.optJSONObject(index) ?: return@playlistEntry null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@playlistEntry null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@playlistEntry null
                val playlistSongs = item.optJSONArray("songs")?.let { entries ->
                    (0 until entries.length()).mapNotNull songEntry@{ songIndex ->
                        val song = entries.optJSONObject(songIndex) ?: return@songEntry null
                        val videoId = song.optString("videoId").takeIf { it.isNotBlank() }
                            ?: return@songEntry null
                        Song(
                            videoId = videoId,
                            title = song.optString("title"),
                            artist = song.optString("artist"),
                            thumbnailUrl = song.nullableString("thumbnailUrl"),
                            durationText = song.nullableString("durationText"),
                            artistId = song.nullableString("artistId"),
                            isExplicit = song.nullableBoolean("isExplicit"),
                        )
                    }
                }.orEmpty()
                PersonalPlaylist(id = id, title = title, songs = playlistSongs, artworkPath = item.nullableString("artworkPath"))
            }
        }.getOrDefault(emptyList())
        return Snapshot(artists, orderedSongs, playlists)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)

    private fun List<Song>.distinctByVideoIdKeepingExplicit(): List<Song> {
        val unique = LinkedHashMap<String, Song>()
        for (song in this) {
            val previous = unique[song.videoId]
            unique[song.videoId] = if (
                previous?.isExplicit == true && song.isExplicit != true
            ) {
                song.copy(isExplicit = true)
            } else if (previous?.isExplicit != true && song.isExplicit == true) {
                song.copy(isExplicit = true)
            } else {
                song
            }
        }
        return unique.values.toList()
    }
}


/**
 * Migrates older personal-library entries that only stored artist membership.
 * For those entries, the old global insertion order is the best available
 * record of their previous artist order. Once assigned, positions are stored
 * independently per artist.
 */
internal fun normalizePersonalArtistOrder(
    songs: List<PersonalLibraryStore.PersonalSong>,
): List<PersonalLibraryStore.PersonalSong> {
    val nextByArtist = mutableMapOf<String, Long>()
    return songs.map { entry ->
        val order = entry.artistOrder.toMutableMap()
        entry.artistBrowseIds.forEach { artistId ->
            val position = order[artistId] ?: nextByArtist[artistId] ?: 0L
            order[artistId] = position
            nextByArtist[artistId] = maxOf(nextByArtist[artistId] ?: 0L, position + 1L)
        }
        entry.copy(artistOrder = order.filterKeys { it in entry.artistBrowseIds })
    }
}

/** The next append position for a newly assigned song in one artist collection. */
internal fun List<PersonalLibraryStore.PersonalSong>.nextArtistMembershipOrder(
    artistBrowseId: String,
): Long {
    val nextStoredPosition = mapNotNull { it.artistOrder[artistBrowseId] }
        .maxOrNull()
        ?.plus(1L)
        ?: 0L
    // Count memberships as a second guard: a partially migrated entry with
    // no saved position must not let the next addition reuse an older slot.
    val membershipCount = count { artistBrowseId in it.artistBrowseIds }.toLong()
    return maxOf(nextStoredPosition, membershipCount)
}

/**
 * Add a song to one or more personal artist collections. Normalize existing
 * memberships first, before allocating the next position. This matters when a
 * globally saved song already belongs to an artist but its older record has
 * no per-artist position yet: that membership must be accounted for before
 * the new song receives its append position.
 */
internal fun List<PersonalLibraryStore.PersonalSong>.addingSongToArtists(
    song: Song,
    artistBrowseIds: Set<String>,
): List<PersonalLibraryStore.PersonalSong> {
    val orderedSongs = normalizePersonalArtistOrder(this)
    val existingIndex = orderedSongs.indexOfFirst { it.song.videoId == song.videoId }
    if (existingIndex < 0) {
        val artistOrder = artistBrowseIds.associateWith { artistId ->
            orderedSongs.nextArtistMembershipOrder(artistId)
        }
        return orderedSongs + PersonalLibraryStore.PersonalSong(song, artistBrowseIds, artistOrder)
    }

    val updated = orderedSongs.toMutableList()
    val existing = updated[existingIndex]
    val explicit = when {
        existing.song.isExplicit == true || song.isExplicit == true -> true
        else -> existing.song.isExplicit ?: song.isExplicit
    }
    // A globally saved song can be assigned to this artist much later.
    // Give each newly added membership its own position at the end of
    // that artist's collection; never inherit the song's global index.
    val newlyAddedArtists = artistBrowseIds - existing.artistBrowseIds
    val newArtistOrder = newlyAddedArtists.associateWith { artistId ->
        orderedSongs.nextArtistMembershipOrder(artistId)
    }
    updated[existingIndex] = existing.copy(
        song = existing.song.copy(isExplicit = explicit),
        artistBrowseIds = existing.artistBrowseIds + artistBrowseIds,
        artistOrder = existing.artistOrder + newArtistOrder,
    )
    return updated
}

/** Songs for one artist in the order they were assigned to that artist. */
internal fun List<PersonalLibraryStore.PersonalSong>.forArtistInAddedOrder(
    artistBrowseId: String,
): List<PersonalLibraryStore.PersonalSong> =
    normalizePersonalArtistOrder(this)
        .withIndex()
        .filter { (_, entry) -> artistBrowseId in entry.artistBrowseIds }
        .sortedWith(
            compareBy<IndexedValue<PersonalLibraryStore.PersonalSong>> {
                it.value.artistOrder[artistBrowseId] ?: Long.MAX_VALUE
            }.thenBy { it.index },
        )
        .map { it.value }
