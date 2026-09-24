package com.example.soundboard.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundboard.BoardSettingsActions
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ThemeMode
import kotlin.math.roundToInt

/** Choices offered for the auto-return-to-home idle timeout in [SettingsDialog]; 0 means "Off". */
private val IDLE_TIMEOUT_OPTIONS_MINUTES = listOf(0, 1, 2, 5, 10)

private fun idleTimeoutLabel(minutes: Int) = if (minutes == 0) "Off" else "$minutes min"

internal fun rowHeightLabel(rowHeight: RowHeight) = when (rowHeight) {
    RowHeight.SHORT -> "Short"
    RowHeight.STANDARD -> "Standard"
    RowHeight.TALL -> "Tall"
    RowHeight.EXTRA_TALL -> "Extra tall"
    RowHeight.UNLIMITED -> "No limit"
}

/** Settings' "Tile labels" section: font, size range, bold and all caps, with a live sample. */
@Composable
private fun LabelStyleControls(labelStyle: LabelStyle, onChange: (LabelStyle) -> Unit) {
    Text("Tile labels", style = MaterialTheme.typography.bodyMedium)
    OptionDropdown(
        label = "Font",
        selected = labelStyle.font,
        options = LabelFont.entries,
        optionLabel = { it.displayName() },
        onSelect = { onChange(labelStyle.copy(font = it)) }
    )
    // Dragging only moves the slider; the board (and disk) update once, on release.
    var sizeRange by remember(labelStyle.minSizeSp, labelStyle.maxSizeSp) {
        mutableStateOf(labelStyle.minSizeSp..labelStyle.maxSizeSp)
    }
    Text(
        "Text size: ${sizeRange.start.roundToInt()}–${sizeRange.endInclusive.roundToInt()} sp",
        style = MaterialTheme.typography.bodySmall
    )
    RangeSlider(
        value = sizeRange,
        onValueChange = { sizeRange = it },
        valueRange = LabelStyle.SIZE_RANGE_SP,
        onValueChangeFinished = {
            onChange(
                labelStyle.copy(
                    minSizeSp = sizeRange.start.roundToInt().toFloat(),
                    maxSizeSp = sizeRange.endInclusive.roundToInt().toFloat()
                )
            )
        }
    )
    HelperText(
        "Each page's labels share the largest size that still fits all of its tiles, " +
            "so labels grow on pages with fewer columns and never go below the smaller number."
    )
    SwitchRow("Bold", labelStyle.bold, { onChange(labelStyle.copy(bold = it)) })
    SwitchRow("All caps", labelStyle.allCaps, { onChange(labelStyle.copy(allCaps = it)) })
    Text(
        "Preview",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    // Both ends of the size range, each on a tile-shaped sample: a long phrase at the
    // smallest size and a short word at the largest, the way they'd actually meet it.
    val base = baseLabelTextStyle(labelStyle)
    val smallest = sizeRange.start.roundToInt()
    val largest = sizeRange.endInclusive.roundToInt()
    // The two tiles share a height (the taller one's), so they read as tiles on one row.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)
    ) {
        LabelPreviewTile("Call the doctor", base.copy(fontSize = smallest.sp), labelStyle.allCaps, Modifier.weight(1f))
        LabelPreviewTile("Yes", base.copy(fontSize = largest.sp), labelStyle.allCaps, Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)
    ) {
        listOf("Smallest ($smallest sp)", "Largest ($largest sp)").forEach { caption ->
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A sample tile in Settings' label preview, drawn like a filled tile. */
@Composable
private fun LabelPreviewTile(text: String, style: TextStyle, allCaps: Boolean, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(TILE_CONTENT_PADDING),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (allCaps) text.uppercase() else text,
                style = style,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Choices offered for the page-tab long-press duration in [SettingsDialog]. 500ms is the
 * Android platform default (ViewConfiguration.longPressTimeoutMillis); the longer options
 * are for anyone who needs more of a deliberate hold before it fires, e.g. on a shared or
 * care board someone else operates.
 */
private val LONG_PRESS_DURATION_OPTIONS_MILLIS = listOf(500, 800, 1200)

private fun longPressDurationLabel(millis: Int) = when (millis) {
    500 -> "Default"
    800 -> "Long"
    else -> "Longer"
}

/**
 * Board-wide settings. Everything here except Performance mode lives on the board itself,
 * so it travels with the board when it's saved or opened; Performance mode is per-device (see
 * [com.example.soundboard.data.DevicePreferences]).
 */
@Composable
internal fun SettingsDialog(
    board: Board,
    performanceModeEnabled: Boolean,
    actions: BoardSettingsActions,
    onPickBackgroundImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasHomePage = board.homePageIndex != null
    val hasBackgroundImage = board.backgroundImageFileName != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SwitchRow("Open on home page", board.openOnHomePage, actions::setOpenOnHomePage)
                SectionDivider()
                OptionDropdown(
                    label = "Auto-return to home page after",
                    selected = board.idleTimeoutMinutes,
                    options = IDLE_TIMEOUT_OPTIONS_MINUTES,
                    optionLabel = ::idleTimeoutLabel,
                    onSelect = actions::setIdleTimeoutMinutes
                )
                SectionDivider()
                OptionDropdown(
                    label = "Long-press duration",
                    selected = board.longPressDurationMillis,
                    options = LONG_PRESS_DURATION_OPTIONS_MILLIS,
                    optionLabel = ::longPressDurationLabel,
                    onSelect = actions::setLongPressDurationMillis
                )
                SectionDivider()
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                SegmentedChoice(
                    options = ThemeMode.entries,
                    selected = board.themeMode,
                    optionLabel = { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                    },
                    onSelect = actions::setThemeMode,
                    modifier = Modifier.fillMaxWidth()
                )
                SectionDivider()
                SwitchRow("Keep screen awake", board.keepScreenAwake, actions::setKeepScreenAwake)
                SectionDivider()
                SwitchRow("Haptic feedback", board.hapticFeedbackEnabled, actions::setHapticFeedbackEnabled)
                SectionDivider()
                SwitchRow(
                    label = "Speak label when there's no clip",
                    checked = board.speakUnrecordedTilesEnabled,
                    onCheckedChange = actions::setSpeakUnrecordedTilesEnabled,
                    supportingText = "Reads a tile's name aloud if it has no recording or upload yet."
                )
                SectionDivider()
                SwitchRow(
                    label = "Performance mode",
                    checked = performanceModeEnabled,
                    onCheckedChange = actions::setPerformanceModeEnabled,
                    supportingText = "Turns off tile shadows, tap ripples, and the drag-reorder scale " +
                        "effect to help the board stay smooth on older devices."
                )
                SectionDivider()
                SwitchRow(
                    label = "Sticky home row",
                    checked = board.stickyHomeRowEnabled,
                    onCheckedChange = actions::setStickyHomeRowEnabled,
                    enabled = hasHomePage,
                    supportingText = if (hasHomePage) {
                        "Shows your home page's first row fixed at the top of every other " +
                            "page. Nothing is deleted — other pages' content just moves down a row."
                    } else {
                        "Set a home page (Page options) to use this."
                    }
                )
                SectionDivider()
                SwitchRow(
                    label = "Hide blank tiles",
                    checked = board.hideBlankTilesEnabled,
                    onCheckedChange = actions::setHideBlankTilesEnabled,
                    supportingText = "Only shown while editing."
                )
                SectionDivider()
                Text("Background", style = MaterialTheme.typography.bodyMedium)
                ColorPicker(
                    selectedArgb = board.backgroundColorArgb,
                    onSelect = actions::setBackgroundColor,
                    showSelection = !hasBackgroundImage
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onPickBackgroundImage, modifier = Modifier.weight(1f)) {
                        Text("Set image")
                    }
                    OutlinedButton(
                        onClick = actions::clearBackground,
                        enabled = hasBackgroundImage || board.backgroundColorArgb != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }
                SectionDivider()
                Text("Tile opacity", style = MaterialTheme.typography.bodyMedium)
                OpacityControls(board.tileOpacity) { it?.let(actions::setBoardTileOpacity) }
                HelperText(
                    "Overridable per page (Page appearance) and per tile (Edit tile). " +
                        "Set to 100% automatically in Performance mode."
                )
                SectionDivider()
                Text("Tile border", style = MaterialTheme.typography.bodyMedium)
                BorderControls(board.tileBorder, actions::setBoardTileBorder)
                HelperText(
                    "Overridable per page (Page appearance) and per tile (Edit tile). " +
                        "Turned off automatically in Performance mode."
                )
                SectionDivider()
                Text("Default grid size for new pages", style = MaterialTheme.typography.bodyMedium)
                Stepper("Rows", board.defaultPageRows, actions::setDefaultPageRows)
                Stepper("Columns", board.defaultPageColumns, actions::setDefaultPageColumns)
                SectionDivider()
                OptionDropdown(
                    label = "Max row height",
                    selected = board.rowHeight,
                    options = RowHeight.entries,
                    optionLabel = ::rowHeightLabel,
                    onSelect = actions::setRowHeight
                )
                HelperText(
                    "Standard is the height of a 4-column row, so pages with fewer columns " +
                        "get wide bars instead of huge squares. Overridable per page (Grid size)."
                )
                SectionDivider()
                LabelStyleControls(board.labelStyle, actions::setLabelStyle)
                SectionDivider()
                Text("Landscape layout", style = MaterialTheme.typography.bodyMedium)
                SegmentedChoice(
                    options = LandscapeLayout.entries,
                    selected = board.landscapeLayout,
                    optionLabel = { layout ->
                        when (layout) {
                            LandscapeLayout.PAGE_GRID -> "Page grid"
                            LandscapeLayout.FIT_TO_SCREEN -> "Fit to screen"
                        }
                    },
                    onSelect = actions::setLandscapeLayout,
                    modifier = Modifier.fillMaxWidth()
                )
                HelperText(
                    when (board.landscapeLayout) {
                        LandscapeLayout.PAGE_GRID ->
                            "Each page's own landscape grid — twice its columns unless set in Grid size. " +
                                "Rows keep their portrait height."
                        LandscapeLayout.FIT_TO_SCREEN ->
                            "Adds columns until 4 rows fit on screen."
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
