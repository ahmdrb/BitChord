package com.music.bitchord

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.components.TopFadeBlur
import dev.chrisbanes.haze.HazeState

/**
 * Draw the progressive top blur only over scrolling page content. Playlist and
 * Artist navigation use the app-wide FrostedTopBar and must remain outside this
 * layer. A bottom-edge scrim is not a valid fallback at the top of the screen.
 */
@Composable
internal fun DetailAwareTopFadeLayer(
    hazeState: HazeState,
    pageColor: Color,
    useBlur: Boolean,
    protectNavigationChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    if (useBlur && !protectNavigationChrome) {
        TopFadeBlur(
            hazeState = hazeState,
            pageColor = pageColor,
            modifier = modifier,
        )
    }
}
