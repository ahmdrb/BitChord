package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.music.bitchord.ui.components.PAGE_GUTTER
import kotlinx.coroutines.launch

private enum class PersonalAddMode { ARTIST, SONG }

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
        else collection.songs.filter { selectedArtist?.browseId in it.artistBrowseIds }
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
private fun PersonalLibraryAddDialog(
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
