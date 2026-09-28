package com.example.soundboard.ui.theme

import androidx.compose.ui.graphics.Color

/** A color the color pickers offer, with the [name] a screen reader announces for it. */
data class PresetColor(val name: String, val color: Color)

/**
 * Tile/page color presets. Material 400-weight tones throughout (previously a mix of
 * 200-300 weights) so the swatches read as one cohesive set rather than an ad hoc grab bag.
 * The pickers put a "none" swatch before these, named for what none means where it's used.
 */
val presetColors: List<PresetColor> = listOf(
    PresetColor("Red", Color(0xFFEF5350)), // red 400
    PresetColor("Orange", Color(0xFFFFA726)), // orange 400
    PresetColor("Amber", Color(0xFFFFCA28)), // amber 300
    PresetColor("Green", Color(0xFF66BB6A)), // green 400
    PresetColor("Blue", Color(0xFF42A5F5)), // blue 400
    PresetColor("Purple", Color(0xFFAB47BC)), // purple 400
    PresetColor("Teal", Color(0xFF26A69A)) // teal 400
)
