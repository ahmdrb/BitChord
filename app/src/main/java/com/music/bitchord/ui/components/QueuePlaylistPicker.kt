package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.PersonalLibraryStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.MainViewModel

/**
 * A multi-select queue transaction over BitChord's own playlists.
 *
 * Remote songs go into PersonalLibraryStore, not YouTube Music's account
 * library. Device files can additionally be filed into OfflinePlaylists.
 * The selected queue snapshot stays frozen while the picker is open.
 */
@Composable
fun QueuePlaylistPicker(
    songs: List<Song>,
    @Suppress("UNUSED_PARAMETER") viewModel: MainViewModel,
    onNotice: (String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember(songs) { songs.toList() }
    val deviceSongs = remember(selected) { selected.filter(::usesDevicePlaylist) }
    var submitted by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val personalLibrary by PersonalLibraryStore.state.collectAsStateWithLifecycle()
    val offlinePlaylists by OfflinePlaylists.playlists.collectAsState()

    val cancel: () -> Unit = {
        if (!submitted && !finished) {
            finished = true
            onDismiss()
        }
    }
    val complete: (Int, Int, Int) -> Unit = { added, alreadyThere, failed ->
        if (!finished) {
            finished = true
            val base = context.getString(R.string.queue_playlist_added, added, selected.size)
            onNotice(
                if (alreadyThere == 0 && failed == 0) base else context.getString(
                    R.string.queue_playlist_partial, base, 0, alreadyThere, failed,
                ),
            )
            onComplete()
        }
    }

    LaunchedEffect(context) {
        PersonalLibraryStore.initialize(context)
        if (selected.isEmpty()) cancel()
    }

    if (submitted || finished || selected.isEmpty()) return

    val choices = remember(personalLibrary.playlists, offlinePlaylists, deviceSongs.size) {
        val personal = personalLibrary.playlists.map { item ->
            UserPlaylist(
                playlistId = "personal:" + item.id,
                title = item.title,
                subtitle = context.resources.getQuantityString(
                    R.plurals.song_count_plural, item.songs.size, item.songs.size,
                ),
                thumbnailUrl = item.artworkPath ?: item.songs.firstNotNullOfOrNull { it.thumbnailUrl },
            )
        }
        val device = if (deviceSongs.isNotEmpty()) {
            offlinePlaylists.map { item ->
                UserPlaylist(
                    playlistId = "device:" + item.id,
                    title = item.title,
                    subtitle = context.resources.getQuantityString(
                        R.plurals.song_count_plural, item.songs.size, item.songs.size,
                    ),
                    thumbnailUrl = item.songs.firstNotNullOfOrNull { it.thumbnailUrl }
                        .takeUnless { item.isLiked },
                )
            }
        } else {
            emptyList()
        }
        personal + device
    }

    PlaylistPickerSheet(
        playlists = choices,
        loading = false,
        songs = selected,
        personal = true,
        onDismiss = cancel,
        onAdd = { picked ->
            if (!submitted && !finished) {
                submitted = true
                val personalIds = picked.filter { it.playlistId.startsWith("personal:") }
                    .map { it.playlistId.removePrefix("personal:") }
                val deviceIds = picked.filter { it.playlistId.startsWith("device:") }
                    .map { it.playlistId.removePrefix("device:") }

                val personalBefore = PersonalLibraryStore.state.value.playlists
                    .filter { it.id in personalIds }
                    .associate { item -> item.id to item.songs.mapTo(HashSet()) { it.videoId } }
                val newPersonalIds = mutableSetOf<String>()
                val existingPersonalIds = mutableSetOf<String>()

                personalIds.forEach { id ->
                    selected.forEach { song ->
                        if (song.videoId in personalBefore[id].orEmpty()) {
                            existingPersonalIds += song.videoId
                        } else if (PersonalLibraryStore.addSongToPlaylist(id, song)) {
                            newPersonalIds += song.videoId
                        } else {
                            existingPersonalIds += song.videoId
                        }
                    }
                }

                val deviceBefore = OfflinePlaylists.playlists.value
                    .filter { it.id in deviceIds }
                    .associate { item -> item.id to item.songs.mapNotNullTo(HashSet()) { it.localUri } }
                if (deviceIds.isNotEmpty() && deviceSongs.isNotEmpty()) {
                    OfflinePlaylists.add(deviceIds, deviceSongs)
                }
                val deviceAfter = OfflinePlaylists.playlists.value
                    .filter { it.id in deviceIds }
                    .associate { item -> item.id to item.songs.mapNotNullTo(HashSet()) { it.localUri } }
                val newDeviceIds = mutableSetOf<String>()
                val existingDeviceIds = mutableSetOf<String>()
                deviceSongs.forEach { song ->
                    val uri = song.localUri ?: return@forEach
                    val existed = deviceIds.any { uri in deviceBefore[it].orEmpty() }
                    val present = deviceIds.any { uri in deviceAfter[it].orEmpty() }
                    if (present && existed) existingDeviceIds += song.videoId
                    else if (present) newDeviceIds += song.videoId
                }

                var added = 0
                var alreadyThere = 0
                var failed = 0
                selected.forEach { song ->
                    when {
                        song.videoId in newPersonalIds || song.videoId in newDeviceIds -> added++
                        song.videoId in existingPersonalIds || song.videoId in existingDeviceIds -> alreadyThere++
                        else -> failed++
                    }
                }
                complete(added, alreadyThere, failed)
            }
        },
        onCreate = { title, _ ->
            if (!submitted && !finished) {
                submitted = true
                val created = runCatching {
                    PersonalLibraryStore.addPlaylistWithSongs(title, selected)
                }.isSuccess
                if (created) complete(selected.size, 0, 0)
                else complete(0, 0, selected.size)
            }
        },
    )
}

/** Match the main library's dispatcher: a localUri alone is not enough. */
private fun usesDevicePlaylist(song: Song): Boolean =
    song.localUri != null && (song.videoId.startsWith("content://") || song.videoId.startsWith("file://"))
