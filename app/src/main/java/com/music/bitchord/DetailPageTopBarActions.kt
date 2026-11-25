package com.music.bitchord

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.glassContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.menuAnchor
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Navigation actions for album, playlist, artist and device-folder detail pages.
 *
 * Kept outside BitChordApp so Compose does not generate one oversized method
 * for the app's entire navigation hierarchy. All controls remain children of
 * the shared FrostedTopBar action pill.
 */
@Composable
internal fun DetailPageTopBarActions(
    isReleaseDetail: Boolean,
    isLocalDetail: Boolean,
    isArtistDetail: Boolean,
    showSearchAction: Boolean = true,
    browseId: String,
    title: String,
    songs: List<Song>,
    isPersonalArtist: Boolean,
    detailSearching: Boolean,
    onDetailSearchToggle: () -> Unit,
    onSortClick: () -> Unit,
    onMoreClick: () -> Unit,
    artistMoreMenuOpen: Boolean,
    onArtistMoreMenuOpenChange: (Boolean) -> Unit,
    onDeleteArtist: () -> Unit,
) {
    val context = LocalContext.current
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassControls = LocalLiquidGlassEnabled.current && isGlassSupported() && !reduceDynamicBlur
    val controlTint = if (glassControls) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val canShareArtist = browseId.startsWith("UC")

    if (showSearchAction && songs.isNotEmpty()) {
        IconButton(onClick = onDetailSearchToggle) {
            Icon(
                if (detailSearching) Icons.Rounded.Close else BitChordIcons.Search,
                contentDescription = stringResource(
                    if (detailSearching) R.string.close_search else R.string.search_this_list,
                ),
                tint = controlTint,
            )
        }
    }

    // The menu itself is FrostedSortMenu, composed with the app's other
    // frosted overlays in the main hierarchy where Haze can sample the page.
    IconButton(onClick = onSortClick) {
        Icon(
            Icons.Rounded.Sort,
            contentDescription = stringResource(
                if (isLocalDetail) R.string.sort_music else R.string.sort_songs,
            ),
            tint = controlTint,
        )
    }

    if (isReleaseDetail && songs.isNotEmpty()) {
        IconButton(
            modifier = Modifier.menuAnchor(),
            onClick = onMoreClick,
        ) {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.more),
                tint = controlTint,
            )
        }
    }

    if (isArtistDetail && (isPersonalArtist || canShareArtist)) {
        Box {
            IconButton(onClick = { onArtistMoreMenuOpenChange(true) }) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.more),
                    tint = controlTint,
                )
            }
            DropdownMenu(
                expanded = artistMoreMenuOpen,
                onDismissRequest = { onArtistMoreMenuOpenChange(false) },
            ) {
                if (canShareArtist) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = {
                            onArtistMoreMenuOpenChange(false)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/channel/$browseId",
                                )
                            }
                            context.startActivity(Intent.createChooser(sendIntent, title))
                        },
                    )
                }
                if (isPersonalArtist) {
                    DropdownMenuItem(
                        text = { Text("Delete artist") },
                        onClick = {
                            onArtistMoreMenuOpenChange(false)
                            onDeleteArtist()
                        },
                    )
                }
            }
        }
    }
}
