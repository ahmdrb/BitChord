package com.music.bitchord.data

import android.content.Context
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
    )

    data class PersonalPlaylist(
        val id: String,
        val title: String,
        val songs: List<Song> = emptyList(),
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

    fun addPlaylist(title: String) = update { current ->
        val name = title.trim()
        if (name.isBlank()) current
        else current.copy(
            playlists = current.playlists + PersonalPlaylist(
                id = java.util.UUID.randomUUID().toString(),
                title = name,
            ),
        )
    }

    fun removePlaylist(id: String) = update { current ->
        current.copy(playlists = current.playlists.filterNot { it.id == id })
    }

    fun addSongToPlaylist(id: String, song: Song) = update { current ->
        current.copy(playlists = current.playlists.map { playlist ->
            if (playlist.id == id && playlist.songs.none { it.videoId == song.videoId }) {
                playlist.copy(songs = playlist.songs + song)
            } else playlist
        })
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
                entry.copy(artistBrowseIds = entry.artistBrowseIds - browseId)
            },
        )
    }

    /** Add a song once, merging any selected artist memberships for collaborations. */
    fun addSong(song: Song, artistBrowseIds: Set<String>) = update { current ->
        val existingIndex = current.songs.indexOfFirst { it.song.videoId == song.videoId }
        if (existingIndex < 0) {
            current.copy(songs = current.songs + PersonalSong(song, artistBrowseIds))
        } else {
            val updated = current.songs.toMutableList()
            val existing = updated[existingIndex]
            updated[existingIndex] = existing.copy(
                artistBrowseIds = existing.artistBrowseIds + artistBrowseIds,
            )
            current.copy(songs = updated)
        }
    }

    fun removeSong(videoId: String) = update { current ->
        current.copy(songs = current.songs.filterNot { it.song.videoId == videoId })
    }

    /** Remove a song from one artist's page without deleting it from Songs. */
    fun removeSongFromArtist(videoId: String, artistBrowseId: String) = update { current ->
        current.copy(songs = current.songs.map { entry ->
            if (entry.song.videoId == videoId && artistBrowseId in entry.artistBrowseIds) {
                entry.copy(artistBrowseIds = entry.artistBrowseIds - artistBrowseId)
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
                            put("personalArtistIds", JSONArray().apply {
                                entry.artistBrowseIds.forEach { artistId -> put(artistId) }
                            })
                        })
                    }
                }.toString())
                .putString(KEY_PLAYLISTS, JSONArray().apply {
                    next.playlists.forEach { playlist ->
                        put(JSONObject().apply {
                            put("id", playlist.id)
                            put("title", playlist.title)
                            put("songs", JSONArray().apply {
                                playlist.songs.forEach { song ->
                                    put(JSONObject().apply {
                                        put("videoId", song.videoId)
                                        put("title", song.title)
                                        put("artist", song.artist)
                                        put("thumbnailUrl", song.thumbnailUrl ?: JSONObject.NULL)
                                        put("durationText", song.durationText ?: JSONObject.NULL)
                                        put("artistId", song.artistId ?: JSONObject.NULL)
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
                    ),
                    artistBrowseIds = item.optJSONArray("personalArtistIds")?.let { ids ->
                        (0 until ids.length()).mapNotNull { ids.optString(it).takeIf { artistId -> artistId.isNotBlank() } }.toSet()
                    } ?: item.nullableString("personalArtistId")?.let { setOf(it) }.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
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
                        )
                    }
                }.orEmpty()
                PersonalPlaylist(id = id, title = title, songs = playlistSongs)
            }
        }.getOrDefault(emptyList())
        return Snapshot(artists, songs, playlists)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
