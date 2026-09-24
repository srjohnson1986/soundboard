package com.example.soundboard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.TileBorder

@Composable
internal fun PageOptionsDialog(
    pageName: String,
    isHome: Boolean,
    gridSize: String,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onSetHome: () -> Unit,
    onGridSize: () -> Unit,
    onPageAppearance: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (isHome) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Home page",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(pageName)
                }
            } else {
                Text(pageName)
            }
        },
        text = {
            Column {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    onClick = onRename
                )
                DropdownMenuItem(
                    text = { Text("Set as home page") },
                    leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    enabled = !isHome,
                    onClick = onSetHome
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Grid size ($gridSize)") },
                    leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                    onClick = onGridSize
                )
                DropdownMenuItem(
                    text = { Text("Page appearance") },
                    leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    onClick = onPageAppearance
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onMoveLeft,
                        enabled = canMoveLeft,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Move left")
                    }
                    TextButton(
                        onClick = onMoveRight,
                        enabled = canMoveRight,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Move right")
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (canDelete) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete page", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = onDelete
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun GridSizeDialog(
    rows: Int,
    columns: Int,
    aspectRatio: Float,
    landscapeRows: Int?,
    landscapeColumns: Int?,
    // Whether Settings' landscape layout is the per-page grid, i.e. whether the
    // landscape values here actually apply — they're still editable either way.
    pageGridActive: Boolean,
    rowHeight: RowHeight?,
    boardRowHeight: RowHeight,
    onConfirm: (
        rows: Int,
        columns: Int,
        aspectRatio: Float,
        landscapeRows: Int?,
        landscapeColumns: Int?,
        rowHeight: RowHeight?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var r by remember { mutableIntStateOf(rows) }
    var c by remember { mutableIntStateOf(columns) }
    var wide by remember { mutableStateOf(aspectRatio != 1f) }
    var customLandscape by remember { mutableStateOf(landscapeRows != null || landscapeColumns != null) }
    var pageRowHeight by remember { mutableStateOf(rowHeight) }
    // The derived defaults track the portrait steppers live, so switching to custom
    // starts from exactly what landscape was about to show.
    val defaultLandscapeRows = Page.defaultLandscapeRows(r)
    val defaultLandscapeColumns = Page.defaultLandscapeColumns(c)
    var lr by remember { mutableIntStateOf(landscapeRows ?: defaultLandscapeRows) }
    var lc by remember { mutableIntStateOf(landscapeColumns ?: defaultLandscapeColumns) }
    val shrinking = r * c < rows * columns

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid size") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Portrait", style = MaterialTheme.typography.labelMedium)
                Stepper("Rows", r) { r = it }
                Stepper("Columns", c) { c = it }
                if (shrinking) {
                    HelperText(
                        "Shrinking only hides blank tiles — rows stop at the last " +
                            "tile with a sound or label."
                    )
                }
                SectionDivider()
                SwitchRow(
                    label = "Custom landscape size",
                    checked = customLandscape,
                    onCheckedChange = { on ->
                        if (on) {
                            lr = defaultLandscapeRows
                            lc = defaultLandscapeColumns
                        }
                        customLandscape = on
                    }
                )
                if (customLandscape) {
                    Stepper("Landscape rows", lr) { lr = it }
                    Stepper("Landscape columns", lc) { lc = it }
                } else {
                    HelperText("Landscape: $defaultLandscapeRows rows x $defaultLandscapeColumns columns (twice the columns).")
                }
                HelperText(
                    if (pageGridActive) {
                        "Rows keep their portrait height in landscape, and grow to show every tile with a sound or label."
                    } else {
                        "Settings has landscape set to Fit to screen, so this only applies once it's switched to Page grid."
                    }
                )
                SectionDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tile shape", modifier = Modifier.weight(1f))
                    SegmentedChoice(
                        options = listOf(false, true),
                        selected = wide,
                        optionLabel = { isWide -> if (isWide) "Wide" else "Square" },
                        onSelect = { wide = it }
                    )
                }
                OptionDropdown(
                    label = "Max row height",
                    selected = pageRowHeight,
                    options = listOf(null) + RowHeight.entries,
                    optionLabel = { it?.let(::rowHeightLabel) ?: "Board default (${rowHeightLabel(boardRowHeight)})" },
                    onSelect = { pageRowHeight = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    r,
                    c,
                    if (wide) 4f / 3f else 1f,
                    lr.takeIf { customLandscape },
                    lc.takeIf { customLandscape },
                    pageRowHeight
                )
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * A page's color, plus its optional overrides of the board's tile opacity and border — one
 * dialog for everything about how the page's tiles look. Each change applies immediately.
 */
@Composable
internal fun PageAppearanceDialog(
    page: Page,
    onColorChange: (Int?) -> Unit,
    onOpacityChange: (Float?) -> Unit,
    onBorderChange: (TileBorder?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page appearance") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Page color", style = MaterialTheme.typography.bodyMedium)
                ColorPicker(selectedArgb = page.color, onSelect = onColorChange)
                HelperText("Tints this page's tab and its filled tiles. A tile's own color always wins.")
                SectionDivider()
                OverrideSection(
                    label = "Override the board's tile opacity",
                    value = page.opacity,
                    initialOverride = 1f,
                    onChange = onOpacityChange
                ) { opacity -> OpacityControls(opacity, onOpacityChange) }
                SectionDivider()
                OverrideSection(
                    label = "Override the board's tile border",
                    value = page.border,
                    initialOverride = TileBorder(enabled = true),
                    onChange = onBorderChange
                ) { border -> BorderControls(border, onBorderChange) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** Confirms deleting a page that has [soundCount] tiles with sound or speech set up. */
@Composable
internal fun ConfirmDeletePageDialog(
    pageName: String,
    soundCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$pageName\"?") },
        text = {
            Text(
                "This page has $soundCount tile${if (soundCount == 1) "" else "s"} " +
                    "with sound or speech set up. Deleting it can't be undone."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
