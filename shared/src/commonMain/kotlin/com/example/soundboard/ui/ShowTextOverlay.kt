package com.example.soundboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.Tile
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

const val SHOW_TEXT_OVERLAY_TAG = "show_text_overlay"

/**
 * Show mode's text screen: [text] as large as it fits without splitting a word, white on
 * black whatever the theme, in the board's label font (upside down if
 * [ShowModeSettings.flipped]). Drawn over the whole board (top bar
 * included) and swallowing every touch, so nothing underneath can be tapped by accident. Closes via [onDismiss] when
 * the timer runs out, on a tap if [ShowModeSettings.tapToClose], and always on Back.
 */
@Composable
internal fun ShowTextOverlay(
    text: String,
    icon: Tile?,
    showMode: ShowModeSettings,
    labelStyle: LabelStyle,
    onDismiss: () -> Unit
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    BackHandler(onBack = onDismiss)
    // Keyed on the text too, so a new tile's words get a fresh countdown.
    LaunchedEffect(text, icon?.id, showMode.timerSeconds) {
        if (showMode.hasTimer) {
            delay(showMode.timerSeconds.seconds)
            currentOnDismiss()
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(SHOW_TEXT_OVERLAY_TAG)
            // Always installed, even without tap to close: an overlay with no pointer input
            // lets touches fall through to the tiles underneath.
            .pointerInput(showMode.tapToClose) {
                detectTapGestures(onTap = { if (showMode.tapToClose) currentOnDismiss() })
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth
        // With a picture or emoji, it gets the top part of the screen and the words what's left;
        // on its own it gets the lot.
        val hasText = text.isNotEmpty()
        val heightPx = if (icon != null && hasText) (constraints.maxHeight * TEXT_SHARE_WITH_ICON).toInt() else constraints.maxHeight
        val base = baseLabelTextStyle(labelStyle).copy(color = Color.White, textAlign = TextAlign.Center)
        val measurer = rememberTextMeasurer()
        val style = remember(text, widthPx, heightPx, base) {
            val size = largestFittingSize(MIN_SIZE_SP, MAX_SIZE_SP) { sp ->
                measurer.fitsScreen(text, base.copy(fontSize = sp.sp), widthPx, heightPx)
            }
            base.copy(fontSize = size.sp)
        }
        // Only a script too long to fit even at the smallest size needs this; taps still
        // reach the tap-to-close handler, since scrolling only claims drags.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().rotate(if (showMode.flipped) 180f else 0f)
        ) {
            if (icon != null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    TileIcon(icon, Color.White, maxSidePx = SHOW_IMAGE_MAX_SIDE_PX)
                }
            }
            if (hasText) {
                BasicText(
                    text = text,
                    style = style,
                    modifier = Modifier
                        .then(if (icon != null) Modifier else Modifier.weight(1f, fill = false))
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

/** The share of the screen's height the words get when there's a picture or emoji above them. */
private const val TEXT_SHARE_WITH_ICON = 0.3f

private const val MIN_SIZE_SP = 24
private const val MAX_SIZE_SP = 240

/**
 * Whether [text] fits a [widthPx] x [heightPx] screen at [style] without a word being split
 * across lines — Compose's own auto-size only checks the height, and happily breaks
 * "doctor" into "docto" / "r" to get there.
 */
private fun TextMeasurer.fitsScreen(text: String, style: TextStyle, widthPx: Int, heightPx: Int): Boolean {
    val result = measure(text = text, style = style, constraints = Constraints(maxWidth = widthPx))
    if (result.size.height > heightPx) return false
    val lineEnds = (0 until result.lineCount - 1).map { result.getLineEnd(it) }
    return !breaksInsideWord(text, lineEnds)
}
