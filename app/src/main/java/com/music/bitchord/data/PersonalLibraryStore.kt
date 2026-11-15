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
        /** The artist card this song belongs to in BitChord; null means unassigned. */
        val artistBrowseId: String? = null,
    )

    data class Snapshot(
        val artists: List<BrowseItem> = emptyList(),
        val songs: List<PersonalSong> = emptyList(),
    )

    private const val PREFS = "bitchord_personal_library"
    private const val KEY_ARTISTS = "artists"
    private const val KEY_SONGS = "songs"

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

    fun addArtist(artist: BrowseItem) = update { current ->
        if (current.artists.any { it.browseId == artist.browseId }) current
        else current.copy(artists = current.artists + artist.copy(type = BrowseType.ARTIST))
    }

    fun removeArtist(browseId: String) = update { current ->
        current.copy(
            artists = current.artists.filterNot { it.browseId == browseId },
            songs = current.songs.map { entry ->
                if (entry.artistBrowseId == browseId) entry.copy(artistBrowseId = null) else entry
            },
        )
    }

    fun addSong(song: Song, artistBrowseId: String?) = update { current ->
        if (current.songs.any { it.song.videoId == song.videoId }) current
        else current.copy(songs = current.songs + PersonalSong(song, artistBrowseId))
    }

    fun removeSong(videoId: String) = update { current ->
        current.copy(songs = current.songs.filterNot { it.song.videoId == videoId })
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
                            put("personalArtistId", entry.artistBrowseId ?: JSONObject.NULL)
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
                    artistBrowseId = item.nullableString("personalArtistId"),
                )
            }
        }.getOrDefault(emptyList())
        return Snapshot(artists, songs)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
