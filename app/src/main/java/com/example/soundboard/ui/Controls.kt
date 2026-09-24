package com.example.soundboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.soundboard.model.TileBorder
import com.example.soundboard.ui.theme.presetColors
import kotlin.math.roundToInt

/** A read-only dropdown picking one of [options]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> OptionDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** A percentage label + slider for a resolved [Float] opacity, shared by the tile, page, and global surfaces. */
@Composable
internal fun OpacityControls(opacity: Float, onChange: (Float?) -> Unit) {
    Text("Opacity: ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = opacity,
        onValueChange = { onChange(it) },
        valueRange = 0.1f..1f
    )
}

/** Enabled/color/width controls for a [TileBorder], shared by the tile, page, and global surfaces. */
@Composable
internal fun BorderControls(border: TileBorder, onChange: (TileBorder) -> Unit) {
    SwitchRow(
        label = "Show border",
        checked = border.enabled,
        onCheckedChange = { onChange(border.copy(enabled = it)) },
        labelStyle = MaterialTheme.typography.bodyMedium
    )
    Text("Color (first = Recommended)", style = MaterialTheme.typography.bodySmall)
    ColorPicker(selectedArgb = border.colorArgb, onSelect = { onChange(border.copy(colorArgb = it)) })
    Text("Width: ${"%.1f".format(border.widthDp)}dp", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = border.widthDp,
        onValueChange = { onChange(border.copy(widthDp = it)) },
        valueRange = 0.5f..8f
    )
}

/**
 * A scrollable row of [presetColors] swatches. The first swatch is null — "none" for a
 * tile or page color, "Recommended" for a border. [showSelection] false highlights none,
 * e.g. for the background color while a background image is set instead.
 */
@Composable
internal fun ColorPicker(selectedArgb: Int?, onSelect: (Int?) -> Unit, showSelection: Boolean = true) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presetColors.forEach { color ->
            ColorSwatch(
                color = color,
                selected = showSelection && color?.toArgb() == selectedArgb,
                onClick = { onSelect(color?.toArgb()) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    // Outer box is a full 48dp touch target (Material's minimum); the visible
    // circle stays 32dp so a full row of presets still fits without crowding.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
    }
}

@Composable
internal fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > 1) onChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { if (value < 50) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * A label, with optional [supportingText] under it, and a switch at the row's end. The
 * label column takes the remaining width, so a long label wraps rather than pushing the
 * switch off the edge. The whole row is the toggle — tapping the label flips it too, a
 * far bigger target than the switch alone, and screen readers announce it as one control.
 */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    labelStyle: TextStyle = LocalTextStyle.current
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = labelStyle)
            if (supportingText != null) HelperText(supportingText)
        }
        // Null: the row above handles the toggle, so the switch is just its visual.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Small secondary text explaining the control above or beside it. */
@Composable
internal fun HelperText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The divider between sections of a settings-style dialog. */
@Composable
internal fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

/** One connected segmented button per entry in [options], with [selected] highlighted. */
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) { Text(optionLabel(option)) }
        }
    }
}

/**
 * A switch that turns an override of an inherited setting on or off, plus [controls] for
 * the override while it's on. [value] null means "inherit"; switching on starts from
 * [initialOverride].
 */
@Composable
internal fun <T : Any> OverrideSection(
    label: String,
    value: T?,
    initialOverride: T,
    onChange: (T?) -> Unit,
    labelStyle: TextStyle = LocalTextStyle.current,
    controls: @Composable (T) -> Unit
) {
    Column {
        SwitchRow(
            label = label,
            checked = value != null,
            onCheckedChange = { on -> onChange(if (on) initialOverride else null) },
            labelStyle = labelStyle
        )
        value?.let { controls(it) }
    }
}
