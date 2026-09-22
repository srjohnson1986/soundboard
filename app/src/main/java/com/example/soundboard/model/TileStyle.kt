package com.example.soundboard.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Resolves [TileBorder.colorArgb]'s "Recommended" (null) to the current theme's outline color. */
@Composable
fun TileBorder.resolvedColor(): Color = colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant
