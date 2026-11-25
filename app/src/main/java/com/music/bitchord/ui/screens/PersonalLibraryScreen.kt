package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.PersonalLibraryStore
import com.music.bitchord.data.forArtistInAddedOrder
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.theme.rememberArtworkPalette
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.IosMenuShape
import com.music.bitchord.ui.components.iosMenuColors
import com.music.bitchord.ui.components.iosMenuPanel
import com.music.bitchord.ui.components.optimizedHazeEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.launch

internal enum class PersonalAddMode { ARTIST, SONG }

@Composable
internal fun PersonalArtistDetailDialog(
    artist: BrowseItem,
    songs: List<Song>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onAddSongs: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDelete: () -> Unit,
    onSongMenu: (Song) -> Unit,
    onPlayNextAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    playingVideoId: String?,
    isPlaying: Boolean,
    /** Search and menu triggers are owned by the app-wide FrostedTopBar. */
    searchOpen: Boolean,
    sortMenuOpen: Boolean,
    onSortMenuOpenChange: (Boolean) -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    PersonalCollectionDetailDialog(
        title = artist.title,
        artwork = artist.thumbnailUrl,
        songs = songs,
        hazeState = hazeState,
        collectionKind = "artist",
        onDismiss = onDismiss,
        onAddSongs = onAddSongs,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        onRemoveSong = onRemoveSong,
        onDelete = onDelete,
        onSongMenu = onSongMenu,
        onPlayNextAll = onPlayNextAll,
        onAddAllToQueue = onAddAllToQueue,
        onDownloadAll = onDownloadAll,
        playingVideoId = playingVideoId,
        isPlaying = isPlaying,
        searchOpen = searchOpen,
        sortMenuOpen = sortMenuOpen,
        onSortMenuOpenChange = onSortMenuOpenChange,
        menuOpen = menuOpen,
        onMenuOpenChange = onMenuOpenChange,
    )
}

@Composable
internal fun PersonalPlaylistCreateDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.iosMenuPanel(translucent = true, hazeState = hazeState),
        shape = IosMenuShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("New playlist", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Give your personal collection a name. You can add songs and customize its artwork anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Playlist name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
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
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onAddSongs: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDelete: () -> Unit,
    onCustomizeArtwork: () -> Unit,
    onSongMenu: (Song) -> Unit,
    onPlayNextAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    playingVideoId: String?,
    isPlaying: Boolean,
    /** Search and menu triggers are owned by the app-wide FrostedTopBar. */
    searchOpen: Boolean,
    sortMenuOpen: Boolean,
    onSortMenuOpenChange: (Boolean) -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    PersonalCollectionDetailDialog(
        title = playlist.title,
        artwork = playlist.artworkPath ?: playlist.songs.firstOrNull()?.thumbnailUrl,
        songs = playlist.songs,
        hazeState = hazeState,
        collectionKind = "playlist",
        onDismiss = onDismiss,
        onAddSongs = onAddSongs,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        onRemoveSong = onRemoveSong,
        onDelete = onDelete,
        onCustomizeArtwork = onCustomizeArtwork,
        onSongMenu = onSongMenu,
        onPlayNextAll = onPlayNextAll,
        onAddAllToQueue = onAddAllToQueue,
        onDownloadAll = onDownloadAll,
        playingVideoId = playingVideoId,
        isPlaying = isPlaying,
        searchOpen = searchOpen,
        sortMenuOpen = sortMenuOpen,
        onSortMenuOpenChange = onSortMenuOpenChange,
        menuOpen = menuOpen,
        onMenuOpenChange = onMenuOpenChange,
    )
}

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
private fun PersonalCollectionDetailDialog(
    title: String,
    artwork: Any?,
    songs: List<Song>,
    hazeState: HazeState,
    collectionKind: String,
    onDismiss: () -> Unit,
    onAddSongs: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDelete: () -> Unit,
    onCustomizeArtwork: (() -> Unit)? = null,
    onSongMenu: (Song) -> Unit,
    onPlayNextAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    playingVideoId: String?,
    isPlaying: Boolean,
    /** Search and menu triggers are owned by the app-wide FrostedTopBar. */
    searchOpen: Boolean,
    sortMenuOpen: Boolean,
    onSortMenuOpenChange: (Boolean) -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Default order") }
    var dateAddedDescending by remember { mutableStateOf(true) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            searchQuery = ""
        }
    }

    val artworkUrl = artwork?.toString()
    val palette = rememberArtworkPalette(artworkUrl)
    val menuColors = iosMenuColors()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val actionShape = CircleShape
    val actionSurface = Modifier
        .clip(actionShape)
        .then(
            if (reduceDynamicBlur) {
                Modifier.background(MaterialTheme.colorScheme.surface)
            } else {
                Modifier.optimizedHazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                )
            },
        )
        .border(0.5.dp, Color.White.copy(alpha = 0.10f), actionShape)
    val visibleSongs = remember(songs, searchQuery, sortMode, dateAddedDescending) {
        val filtered = songs.filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }
        when (sortMode) {
            "Date added" -> if (dateAddedDescending) filtered.reversed() else filtered
            "Alphabetical (A to Z)" -> filtered.sortedBy { it.title.lowercase() }
            "Alphabetical (Z to A)" -> filtered.sortedByDescending { it.title.lowercase() }
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
            Box(Modifier.fillMaxSize()) {
                // The actual controls live in MainActivity's shared FrostedTopBar.
                // These invisible anchors keep the existing popups attached to that
                // same top-right control area without drawing another toolbar here.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 24.dp, end = 20.dp)
                        .size(1.dp),
                ) {
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { onSortMenuOpenChange(false) },
                        modifier = Modifier.iosMenuPanel(translucent = true, hazeState = hazeState),
                        shape = IosMenuShape,
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        listOf(
                            "Date added",
                            "Default order",
                            "Alphabetical (A to Z)",
                            "Alphabetical (Z to A)",
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = menuColors.label,
                                    )
                                },
                                trailingIcon = {
                                    if (option == "Date added" && sortMode == option) {
                                        Text(
                                            if (dateAddedDescending) "↓" else "↑",
                                            color = menuColors.label,
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    } else if (sortMode == option) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = menuColors.label,
                                        )
                                    }
                                },
                                onClick = {
                                    if (option == "Date added" && sortMode == "Date added") {
                                        dateAddedDescending = !dateAddedDescending
                                    } else {
                                        sortMode = option
                                    }
                                    onSortMenuOpenChange(false)
                                },
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { onMenuOpenChange(false) },
                        modifier = Modifier.iosMenuPanel(translucent = true, hazeState = hazeState),
                        shape = IosMenuShape,
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play next", color = menuColors.label) },
                            onClick = { onMenuOpenChange(false); onPlayNextAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue", color = menuColors.label) },
                            onClick = { onMenuOpenChange(false); onAddAllToQueue() },
                        )
                        DropdownMenuItem(
                            text = { Text("Download all", color = menuColors.label) },
                            onClick = { onMenuOpenChange(false); onDownloadAll() },
                        )
                        if (onCustomizeArtwork != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Customize artwork",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = menuColors.label,
                                    )
                                },
                                onClick = { onMenuOpenChange(false); onCustomizeArtwork() },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (collectionKind == "playlist") "Delete playlist" else "Remove from collection",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { onMenuOpenChange(false); confirmDelete = true },
                        )
                    }
                }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 76.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        bottom = 28.dp,
                    ),
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
                                modifier = Modifier.size(56.dp).then(actionSurface),
                            ) {
                                Icon(BitChordIcons.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurface)
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
                                modifier = Modifier.size(56.dp).then(actionSurface),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add songs", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        AnimatedVisibility(
                            visible = searchOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .focusRequester(searchFocusRequester),
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.divider,
                                    unfocusedBorderColor = palette.divider,
                                    cursorColor = palette.accent,
                                    focusedLabelColor = palette.accent,
                                ),
                                placeholder = { Text("Search this list") },
                            )
                        }
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
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) {
                                    AsyncImage(
                                        model = song.thumbnailUrl.artworkAt(512),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                    if (playingVideoId == song.videoId) {
                                        Box(
                                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isPlaying) NowPlayingEqualizer(palette.accent)
                                            else Icon(Icons.Rounded.Pause, contentDescription = "Paused", tint = palette.accent)
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    ExplicitSongTitle(
                                        song = song,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.onBackground,
                                        badgeColor = palette.onBackgroundVariant,
                                        modifier = Modifier.fillMaxWidth(),
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
                                    IconButton(onClick = { onSongMenu(song) }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Song options", tint = palette.onBackground)
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
            modifier = Modifier.iosMenuPanel(translucent = true, hazeState = hazeState),
            shape = IosMenuShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    if (collectionKind == "playlist") "Delete playlist?" else "Remove artist?",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    if (collectionKind == "playlist") {
                        "This will remove this playlist from your BitChord personal collection."
                    } else {
                        "This will remove this artist from your BitChord personal collection."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun durationSeconds(durationText: String?): Long? {
    val parts = durationText?.split(':')?.mapNotNull { it.toLongOrNull() } ?: return null
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
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
        else collection.songs.forArtistInAddedOrder(selectedArtist!!.browseId)
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
                        ExplicitSongTitle(
                            song = entry.song,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
            hazeState = remember { HazeState() },
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
    hazeState: HazeState,
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
    val dialogMenuColors = iosMenuColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.iosMenuPanel(translucent = true, hazeState = hazeState),
        shape = IosMenuShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(if (mode == PersonalAddMode.ARTIST) "Add artist" else "Add song", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (mode == PersonalAddMode.ARTIST) "Find an artist and add them to your personal collection."
                    else "Search YouTube Music and add a track to this BitChord collection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        label = { Text(if (mode == PersonalAddMode.ARTIST) "Artist name" else "Song title or artist") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
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
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
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
                            modifier = Modifier.iosMenuPanel(translucent = true, hazeState = hazeState),
                            shape = IosMenuShape,
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            DropdownMenuItem(
                                text = { Text("No artist collection", color = dialogMenuColors.label) },
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
                                            Text(artist.title, color = dialogMenuColors.label)
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
                                    AsyncImage(artist.thumbnailUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
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
                                    AsyncImage(song.thumbnailUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        ExplicitSongTitle(
                                            song = song,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
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

@Composable
private fun NowPlayingEqualizer(color: Color) {
    val motion = rememberInfiniteTransition(label = "personalNowPlaying")
    val first by motion.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "equalizerOne",
    )
    val second by motion.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
        label = "equalizerTwo",
    )
    val third by motion.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(490), RepeatMode.Reverse),
        label = "equalizerThree",
    )
    Canvas(Modifier.size(width = 22.dp, height = 22.dp)) {
        val barWidth = size.width / 5f
        val heights = listOf(first, second, third)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * barWidth * 2f + barWidth * 0.5f,
                    y = (size.height - barHeight) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
            )
        }
    }
}
