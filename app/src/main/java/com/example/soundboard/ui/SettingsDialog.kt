package com.example.soundboard.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.TileBorder
import com.example.soundboard.ui.theme.presetColors
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
    Text(
        "Each page's labels share the largest size that still fits all of its tiles, " +
            "so labels grow on pages with fewer columns and never go below the smaller number.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Bold")
        Switch(checked = labelStyle.bold, onCheckedChange = { onChange(labelStyle.copy(bold = it)) })
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("All caps")
        Switch(checked = labelStyle.allCaps, onCheckedChange = { onChange(labelStyle.copy(allCaps = it)) })
    }
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

/** App-level preferences that aren't page content — see [com.example.soundboard.data.SettingsRepository]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDialog(
    openOnHomePage: Boolean,
    onOpenOnHomePageChange: (Boolean) -> Unit,
    idleTimeoutMinutes: Int,
    onIdleTimeoutMinutesChange: (Int) -> Unit,
    longPressDurationMillis: Int,
    onLongPressDurationMillisChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    keepScreenAwake: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    hapticFeedbackEnabled: Boolean,
    onHapticFeedbackEnabledChange: (Boolean) -> Unit,
    speakUnrecordedTilesEnabled: Boolean,
    onSpeakUnrecordedTilesEnabledChange: (Boolean) -> Unit,
    performanceModeEnabled: Boolean,
    onPerformanceModeEnabledChange: (Boolean) -> Unit,
    stickyHomeRowEnabled: Boolean,
    hasHomePage: Boolean,
    onStickyHomeRowEnabledChange: (Boolean) -> Unit,
    hideBlankTilesEnabled: Boolean,
    onHideBlankTilesEnabledChange: (Boolean) -> Unit,
    backgroundColorArgb: Int?,
    hasBackgroundImage: Boolean,
    onBackgroundColorChange: (Int?) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackground: () -> Unit,
    tileOpacity: Float,
    onTileOpacityChange: (Float) -> Unit,
    tileBorder: TileBorder,
    onTileBorderChange: (TileBorder) -> Unit,
    defaultPageRows: Int,
    onDefaultPageRowsChange: (Int) -> Unit,
    defaultPageColumns: Int,
    onDefaultPageColumnsChange: (Int) -> Unit,
    landscapeLayout: LandscapeLayout,
    onLandscapeLayoutChange: (LandscapeLayout) -> Unit,
    rowHeight: RowHeight,
    onRowHeightChange: (RowHeight) -> Unit,
    labelStyle: LabelStyle,
    onLabelStyleChange: (LabelStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open on home page")
                    Switch(checked = openOnHomePage, onCheckedChange = onOpenOnHomePageChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                var idleTimeoutExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = idleTimeoutExpanded,
                    onExpandedChange = { idleTimeoutExpanded = it }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        value = idleTimeoutLabel(idleTimeoutMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Auto-return to home page after") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = idleTimeoutExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = idleTimeoutExpanded,
                        onDismissRequest = { idleTimeoutExpanded = false }
                    ) {
                        IDLE_TIMEOUT_OPTIONS_MINUTES.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(idleTimeoutLabel(minutes)) },
                                onClick = {
                                    onIdleTimeoutMinutesChange(minutes)
                                    idleTimeoutExpanded = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                var longPressExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = longPressExpanded,
                    onExpandedChange = { longPressExpanded = it }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        value = longPressDurationLabel(longPressDurationMillis),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Long-press duration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = longPressExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = longPressExpanded,
                        onDismissRequest = { longPressExpanded = false }
                    ) {
                        LONG_PRESS_DURATION_OPTIONS_MILLIS.forEach { millis ->
                            DropdownMenuItem(
                                text = { Text(longPressDurationLabel(millis)) },
                                onClick = {
                                    onLongPressDurationMillisChange(millis)
                                    longPressExpanded = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == themeMode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size)
                        ) {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep screen awake")
                    Switch(checked = keepScreenAwake, onCheckedChange = onKeepScreenAwakeChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic feedback")
                    Switch(checked = hapticFeedbackEnabled, onCheckedChange = onHapticFeedbackEnabledChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Speak label when there's no clip", modifier = Modifier.weight(1f))
                    Switch(checked = speakUnrecordedTilesEnabled, onCheckedChange = onSpeakUnrecordedTilesEnabledChange)
                }
                Text(
                    "Reads a tile's name aloud if it has no recording or upload yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Performance mode", modifier = Modifier.weight(1f))
                    Switch(checked = performanceModeEnabled, onCheckedChange = onPerformanceModeEnabledChange)
                }
                Text(
                    "Turns off tile shadows, tap ripples, and the drag-reorder scale " +
                        "effect to help the board stay smooth on older devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sticky home row", modifier = Modifier.weight(1f))
                    Switch(
                        checked = stickyHomeRowEnabled,
                        onCheckedChange = onStickyHomeRowEnabledChange,
                        enabled = hasHomePage
                    )
                }
                Text(
                    if (hasHomePage) {
                        "Shows your home page's first row fixed at the top of every other " +
                            "page. Nothing is deleted — other pages' content just moves down a row."
                    } else {
                        "Set a home page (Page options) to use this."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hide blank tiles", modifier = Modifier.weight(1f))
                    Switch(checked = hideBlankTilesEnabled, onCheckedChange = onHideBlankTilesEnabledChange)
                }
                Text(
                    "Only shown while editing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Background", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { color ->
                        ColorSwatch(
                            color = color,
                            selected = !hasBackgroundImage && color?.toArgb() == backgroundColorArgb,
                            onClick = { onBackgroundColorChange(color?.toArgb()) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onPickBackgroundImage, modifier = Modifier.weight(1f)) {
                        Text("Set image")
                    }
                    OutlinedButton(
                        onClick = onClearBackground,
                        enabled = hasBackgroundImage || backgroundColorArgb != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Tile opacity", style = MaterialTheme.typography.bodyMedium)
                OpacityControls(tileOpacity) { it?.let(onTileOpacityChange) }
                Text(
                    "Overridable per page (Page options) and per tile (Edit tile). " +
                        "Set to 100% automatically in Performance mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Tile border", style = MaterialTheme.typography.bodyMedium)
                BorderControls(tileBorder, onTileBorderChange)
                Text(
                    "Overridable per page (Page options) and per tile (Edit tile). " +
                        "Turned off automatically in Performance mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Default grid size for new pages", style = MaterialTheme.typography.bodyMedium)
                Stepper("Rows", defaultPageRows, onDefaultPageRowsChange)
                Stepper("Columns", defaultPageColumns, onDefaultPageColumnsChange)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OptionDropdown(
                    label = "Max row height",
                    selected = rowHeight,
                    options = RowHeight.entries,
                    optionLabel = ::rowHeightLabel,
                    onSelect = onRowHeightChange
                )
                Text(
                    "Standard is the height of a 4-column row, so pages with fewer columns " +
                        "get wide bars instead of huge squares. Overridable per page (Grid size).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LabelStyleControls(labelStyle, onLabelStyleChange)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Landscape layout", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LandscapeLayout.entries.forEachIndexed { index, layout ->
                        SegmentedButton(
                            selected = layout == landscapeLayout,
                            onClick = { onLandscapeLayoutChange(layout) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = LandscapeLayout.entries.size)
                        ) {
                            Text(
                                when (layout) {
                                    LandscapeLayout.PAGE_GRID -> "Page grid"
                                    LandscapeLayout.FIT_TO_SCREEN -> "Fit to screen"
                                }
                            )
                        }
                    }
                }
                Text(
                    when (landscapeLayout) {
                        LandscapeLayout.PAGE_GRID ->
                            "Each page's own landscape grid — twice its columns unless set in Grid size. " +
                                "Rows keep their portrait height."
                        LandscapeLayout.FIT_TO_SCREEN ->
                            "Adds columns until 4 rows fit on screen."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
