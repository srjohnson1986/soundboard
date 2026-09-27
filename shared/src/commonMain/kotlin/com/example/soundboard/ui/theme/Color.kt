package com.example.soundboard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tile/page color presets. Material 400-weight tones throughout (previously a mix of
 * 200-300 weights) so the swatches read as one cohesive set rather than an ad hoc grab bag.
 * `null` means "use the theme default" and must stay first — callers rely on that order
 * for the "no custom color" option.
 */
val presetColors: List<Color?> = listOf(
    null,
    Color(0xFFEF5350), // red 400
    Color(0xFFFFA726), // orange 400
    Color(0xFFFFCA28), // amber 300
    Color(0xFF66BB6A), // green 400
    Color(0xFF42A5F5), // blue 400
    Color(0xFFAB47BC), // purple 400
    Color(0xFF26A69A) // teal 400
)
