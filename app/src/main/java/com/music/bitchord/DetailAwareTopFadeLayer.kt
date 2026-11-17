package com.music.bitchord

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.components.BottomFadeScrim
import com.music.bitchord.ui.components.TopFadeBlur
import dev.chrisbanes.haze.HazeState

/**
 * Draw the page's top edge treatment outside BitChordApp's large composition
 * method. Keeping this branch in its own composable avoids the JVM method-size
 * limit while preserving the existing reduced-blur fallback.
 */
@Composable
internal fun DetailAwareTopFadeLayer(
    hazeState: HazeState,
    pageColor: Color,
    useBlur: Boolean,
    protectNavigationChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    if (useBlur) {
        TopFadeBlur(
            hazeState = hazeState,
            pageColor = pageColor,
            protectNavigationChrome = protectNavigationChrome,
            modifier = modifier,
        )
    } else {
        BottomFadeScrim(
            pageColor = pageColor,
            modifier = modifier,
        )
    }
}
