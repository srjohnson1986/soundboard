package com.example.soundboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.ui.platformColorScheme

@Composable
fun SoundboardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = platformColorScheme(darkTheme) ?: if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, shapes = SoundboardShapes, content = content)
}
