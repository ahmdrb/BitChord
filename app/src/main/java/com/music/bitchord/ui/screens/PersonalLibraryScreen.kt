package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.PersonalLibraryStore
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.theme.rememberArtworkPalette
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.launch

internal enum class PersonalAddMode { ARTIST, SONG }

@Composable
internal fun PersonalArtistDetailDialog(
    artist: BrowseItem,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onAddSongs: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDelete: () -> Unit,
) {
    PersonalCollectionDetailDialog(
        title = artist.title,
        artwork = artist.thumbnailUrl,
        songs = songs,
        collectionKind = "artist",
        onDismiss = onDismiss,
        onAddSongs = onAddSongs,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        onRemoveSong = onRemoveSong,
        onDelete = onDelete,
    )
}

@Composable
internal fun PersonalPlaylistCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Playlist name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onCreate(title.trim())
                    onDismiss()
                },
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun PersonalPlaylistDetailDialog(
    playlist: PersonalLibraryStore.PersonalPlaylist,
    onDismiss: () -> Unit,
    onAddSongs: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDelete: () -> Unit,
    onCustomizeArtwork: () -> Unit,
) {
    PersonalCollectionDetailDialog(
        title = playlist.title,
        artwork = playlist.artworkPath ?: playlist.songs.firstOrNull()?.thumbnailUrl,
        songs = playlist.songs,
        collectionKind = "playlist",
        onDismiss = onDismiss,
        onAddSongs = onAddSongs,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        onRemoveSong = onRemoveSong,
        onDelete = onDelete,
        onCustomizeArtwork = onCustomizeArtwork,
    )
}

@Composable
private fun PersonalCollectionDetailDialog(
    title: String,
    artwork: Any?,
    songs: List<Song>,
    collectionKind: String,
    onDismiss: () -> Unit,
    onAddSongs: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDelete: () -> Unit,
    onCustomizeArtwork: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var songMenuVideoId by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Default") }

    val artworkUrl = artwork?.toString()
    val palette = rememberArtworkPalette(artworkUrl)
    val visibleSongs = remember(songs, searchQuery, sortMode) {
        val filtered = songs.filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }
        when (sortMode) {
            "Title" -> filtered.sortedBy { it.title.lowercase() }
            "Artist" -> filtered.sortedBy { it.artist.lowercase() }
            "Duration" -> filtered.sortedBy { it.durationText.orEmpty() }
            else -> filtered
        }
    }

    // This is a real Library detail page, not a modal window: keeping it in
    // the app content leaves the tab bar and mini-player in their original places.
    BackHandler { onDismiss() }
    Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(palette.background, palette.wash)),
            ),
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl.artworkAt(1200),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(42.dp).alpha(0.20f),
                )
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(52.dp).background(palette.elevated, RoundedCornerShape(50))
                            .border(1.dp, palette.divider, RoundedCornerShape(50)),
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = palette.onBackground)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.elevated.copy(alpha = 0.88f))
                            .border(1.dp, palette.divider, RoundedCornerShape(50)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search songs", tint = palette.onBackground)
                        }
                        Box {
                            IconButton(
                                onClick = { sortMenuOpen = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Rounded.FilterList, contentDescription = "Sort songs", tint = palette.onBackground)
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                listOf("Default", "Title", "Artist", "Duration").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(if (sortMode == option) "✓ $option" else option) },
                                        onClick = { sortMode = option; sortMenuOpen = false },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options", tint = palette.onBackground)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (onCustomizeArtwork != null) {
                                    DropdownMenuItem(
                                        text = { Text("Customize artwork") },
                                        onClick = { menuOpen = false; onCustomizeArtwork() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete $collectionKind") },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                }

                if (searchOpen) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        singleLine = true,
                        placeholder = { Text("Search songs") },
                    )
                }

                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 42.dp).aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.elevated),
                        ) {
                            if (artworkUrl != null) {
                                AsyncImage(
                                    model = artworkUrl.artworkAt(1200),
                                    contentDescription = "$title artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.onBackgroundVariant,
                        )
                        val trackCount = if (songs.size == 1) "1 Track" else "${songs.size} Tracks"
                        val duration = collectionDurationLabel(songs)
                        Text(
                            if (duration == null) trackCount else "$trackCount · $duration",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(22.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconButton(
                                onClick = onShuffle,
                                enabled = songs.isNotEmpty(),
                                modifier = Modifier.size(56.dp).background(palette.elevated, RoundedCornerShape(50))
                                    .border(1.dp, palette.divider, RoundedCornerShape(50)),
                            ) {
                                Icon(BitChordIcons.Shuffle, contentDescription = "Shuffle", tint = palette.accent)
                            }
                            androidx.compose.material3.Button(
                                onClick = onPlay,
                                enabled = songs.isNotEmpty(),
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(50),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color.White.copy(alpha = 0.45f),
                                    disabledContentColor = Color.Black.copy(alpha = 0.55f),
                                ),
                            ) {
                                Icon(BitChordIcons.Play, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            IconButton(
                                onClick = onAddSongs,
                                modifier = Modifier.size(56.dp).background(palette.elevated, RoundedCornerShape(50))
                                    .border(1.dp, palette.divider, RoundedCornerShape(50)),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add songs", tint = palette.accent)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    if (visibleSongs.isEmpty()) {
                        item {
                            Text(
                                if (songs.isEmpty()) {
                                    "Songs you add to this BitChord $collectionKind collection will appear here."
                                } else {
                                    "No matching songs."
                                },
                                color = palette.onBackgroundVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        items(visibleSongs, key = { it.videoId }) { song ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                                    .clickable { onSongClick(song) }
                                    .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl.artworkAt(512),
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.onBackgroundVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    song.durationText.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.onBackgroundVariant,
                                )
                                Box {
                                    IconButton(onClick = { songMenuVideoId = song.videoId }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Song options", tint = palette.onBackground)
                                    }
                                    DropdownMenu(
                                        expanded = songMenuVideoId == song.videoId,
                                        onDismissRequest = { songMenuVideoId = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Remove from collection") },
                                            onClick = {
                                                songMenuVideoId = null
                                                onRemoveSong(song.videoId)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $collectionKind?") },
            text = { Text("This will remove this BitChord $collectionKind collection.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun collectionDurationLabel(songs: List<Song>): String? {
    val totalSeconds = songs.sumOf { song ->
        val parts = song.durationText?.split(':')?.mapNotNull { it.toLongOrNull() }
            ?: return@sumOf 0L
        when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }
    if (totalSeconds <= 0L) return null
    val minutes = (totalSeconds + 30) / 60
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

/**
 * The Library tab is an intentionally curated, local collection. The YouTube
 * Music account library is not rendered here and is never modified by edits.
 */
@Composable
fun PersonalLibraryScreen(
    contentPadding: PaddingValues,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(context) { PersonalLibraryStore.initialize(context) }
    val collection by PersonalLibraryStore.state.collectAsStateWithLifecycle()
    var addMode by remember { mutableStateOf<PersonalAddMode?>(null) }
    var selectedArtist by remember { mutableStateOf<BrowseItem?>(null) }

    val visibleSongs = remember(collection.songs, selectedArtist) {
        if (selectedArtist == null) collection.songs
        else collection.songs.filter { entry -> selectedArtist?.browseId?.let(entry.artistBrowseIds::contains) == true }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PAGE_GUTTER,
            end = PAGE_GUTTER,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (selectedArtist == null) {
                Column {
                    Text(
                        "Your Library",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Your collection, your rules",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedArtist = null }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to artists")
                    }
                    Column {
                        Text(selectedArtist!!.title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Your artist collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (selectedArtist == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Artists", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { addMode = PersonalAddMode.ARTIST }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add artist")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Card(
                            modifier = Modifier.width(164.dp).height(210.dp).clickable {
                                addMode = PersonalAddMode.ARTIST
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Add artist", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    items(collection.artists, key = { it.browseId }) { artist ->
                        Card(
                            modifier = Modifier.width(164.dp).height(210.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Box {
                                Column(
                                    modifier = Modifier.fillMaxSize().clickable { selectedArtist = artist },
                                ) {
                                    AsyncImage(
                                        model = artist.thumbnailUrl,
                                        contentDescription = artist.title,
                                        modifier = Modifier.fillMaxWidth().height(154.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Text(
                                        artist.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
                                    )
                                    Text(
                                        artist.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { PersonalLibraryStore.removeArtist(artist.browseId) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(50)),
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove artist")
                                }
                            }
                        }
                    }
                }
                if (collection.artists.isEmpty()) {
                    Text(
                        "Add artists to build your own collection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (selectedArtist == null) "Songs" else "Songs by ${selectedArtist!!.title}",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = { addMode = PersonalAddMode.SONG }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add song")
                }
            }
        }

        if (visibleSongs.isEmpty()) {
            item {
                Text(
                    if (selectedArtist == null) "Songs you add will appear here."
                    else "Add songs to this artist collection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(visibleSongs, key = { it.song.videoId }) { entry ->
                val songsForQueue = visibleSongs.map { it.song }
                val index = visibleSongs.indexOfFirst { it.song.videoId == entry.song.videoId }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.song.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { onPlaySong(songsForQueue, index) },
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f).clickable { onPlaySong(songsForQueue, index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(entry.song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        val artistId = selectedArtist?.browseId
                        if (artistId == null) PersonalLibraryStore.removeSong(entry.song.videoId)
                        else PersonalLibraryStore.removeSongFromArtist(entry.song.videoId, artistId)
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove song")
                    }
                }
            }
        }
    }

    if (addMode != null) {
        PersonalLibraryAddDialog(
            mode = addMode!!,
            artists = collection.artists,
            initialArtistId = selectedArtist?.browseId,
            onDismiss = { addMode = null },
            onArtistAdded = { artist -> PersonalLibraryStore.addArtist(artist) },
            onSongAdded = { song, artistIds -> PersonalLibraryStore.addSong(song, artistIds) },
        )
    }
}

@Composable
internal fun PersonalLibraryAddDialog(
    mode: PersonalAddMode,
    artists: List<BrowseItem>,
    initialArtistId: String?,
    onDismiss: () -> Unit,
    onArtistAdded: (BrowseItem) -> Unit,
    onSongAdded: (Song, Set<String>) -> Unit,
) {
    var query by remember(mode) { mutableStateOf("") }
    var loading by remember(mode) { mutableStateOf(false) }
    var searched by remember(mode) { mutableStateOf(false) }
    var songs by remember(mode) { mutableStateOf(emptyList<Song>()) }
    var artistResults by remember(mode) { mutableStateOf(emptyList<BrowseItem>()) }
    var selectedArtistIds by remember(mode, initialArtistId) {
        mutableStateOf(initialArtistId?.let(::setOf).orEmpty())
    }
    var artistMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == PersonalAddMode.ARTIST) "Add artist" else "Add song") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(if (mode == PersonalAddMode.ARTIST) "Artist name" else "Song title or artist") },
                    )
                    IconButton(
                        enabled = query.isNotBlank() && !loading,
                        onClick = {
                            scope.launch {
                                loading = true
                                searched = true
                                songs = emptyList()
                                artistResults = emptyList()
                                val filter = if (mode == PersonalAddMode.ARTIST) SearchFilter.ARTISTS else SearchFilter.SONGS
                                val results = YtMusicRepository.search(query.trim(), filter).getOrNull().orEmpty()
                                if (mode == PersonalAddMode.ARTIST) {
                                    artistResults = results.filterIsInstance<SearchResult.Browse>()
                                        .map { it.item }
                                        .filter { it.type == BrowseType.ARTIST }
                                        .distinctBy { it.browseId }
                                } else {
                                    songs = results.mapNotNull { result ->
                                        when (result) {
                                            is SearchResult.TopTrack -> result.song
                                            is SearchResult.Track -> result.song
                                            is SearchResult.Browse -> null
                                        }
                                    }.distinctBy { it.videoId }
                                }
                                loading = false
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                }

                if (mode == PersonalAddMode.SONG && artists.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { artistMenuOpen = true }) {
                            val selectedNames = artists.filter { it.browseId in selectedArtistIds }.map { it.title }
                            Text("Add to: ${selectedNames.takeIf { it.isNotEmpty() }?.joinToString() ?: "No artist collection"}")
                        }
                        DropdownMenu(
                            expanded = artistMenuOpen,
                            onDismissRequest = { artistMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("No artist collection") },
                                onClick = {
                                    selectedArtistIds = emptySet()
                                    artistMenuOpen = false
                                },
                            )
                            artists.forEach { artist ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = artist.browseId in selectedArtistIds,
                                                onCheckedChange = null,
                                            )
                                            Text(artist.title)
                                        }
                                    },
                                    onClick = {
                                        selectedArtistIds = if (artist.browseId in selectedArtistIds) {
                                            selectedArtistIds - artist.browseId
                                        } else {
                                            selectedArtistIds + artist.browseId
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    searched && mode == PersonalAddMode.ARTIST && artistResults.isEmpty() ->
                        Text("No artists found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    searched && mode == PersonalAddMode.SONG && songs.isEmpty() ->
                        Text("No songs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        if (mode == PersonalAddMode.ARTIST) {
                            items(artistResults, key = { it.browseId }) { artist ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        onArtistAdded(artist)
                                        onDismiss()
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(artist.thumbnailUrl, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(artist.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(artist.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.Add, contentDescription = "Add artist")
                                }
                            }
                        } else {
                            items(songs, key = { it.videoId }) { song ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        onSongAdded(song, selectedArtistIds)
                                        onDismiss()
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(song.thumbnailUrl, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.Add, contentDescription = "Add song")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
