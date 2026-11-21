package com.music.bitchord

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.TopBarCircleIconButton
import com.music.bitchord.ui.components.menuAnchor
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Navigation actions for album, playlist, artist and device-folder detail pages.
 * Each action is a separate outlined circle, matching the Shuffle and Plus
 * controls in the detail-page action row.
 */
@Composable
internal fun DetailPageTopBarActions(
    isReleaseDetail: Boolean,
    isLocalDetail: Boolean,
    isArtistDetail: Boolean,
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
    val canShareArtist = browseId.startsWith("UC")

    if (songs.isNotEmpty()) {
        TopBarCircleIconButton(
            icon = if (detailSearching) Icons.Rounded.Close else BitChordIcons.Search,
            contentDescription = stringResource(
                if (detailSearching) R.string.close_search else R.string.search_this_list,
            ),
            onClick = onDetailSearchToggle,
        )
    }

    TopBarCircleIconButton(
        icon = Icons.Rounded.Sort,
        contentDescription = stringResource(
            if (isLocalDetail) R.string.sort_music else R.string.sort_songs,
        ),
        onClick = onSortClick,
    )

    if (isReleaseDetail && songs.isNotEmpty()) {
        TopBarCircleIconButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.more),
            onClick = onMoreClick,
            modifier = Modifier.menuAnchor(),
        )
    }

    if (isArtistDetail && (isPersonalArtist || canShareArtist)) {
        Box {
            TopBarCircleIconButton(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.more),
                onClick = { onArtistMoreMenuOpenChange(true) },
            )
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
