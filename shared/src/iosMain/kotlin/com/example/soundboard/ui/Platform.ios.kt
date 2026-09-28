package com.example.soundboard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget

// The iOS side of Platform.kt (#265): what the shared module needs to build and run its tests
// on iOS. Picking and saving files, the microphone and keeping the screen awake need the iOS
// app around them, and come with it (#266); until then they do nothing.

@Composable
actual fun rememberOpenFileLauncher(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit = {}

@Composable
actual fun rememberPickImageLauncher(onPicked: (PickedFile) -> Unit): () -> Unit = {}

@Composable
actual fun rememberSaveFileLauncher(mimeType: String, onTarget: (SaveTarget) -> Unit): (suggestedName: String) -> Unit = { _ -> }

// Reports no access, so recording says it couldn't start rather than recording nothing.
@Composable
actual fun rememberMicrophoneAccess(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return { currentOnResult(false) }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {}

// iOS has no back button; going back is a swipe the app's own screens would handle.
@Composable
actual fun BackHandler(onBack: () -> Unit) {}

@Composable
actual fun isLandscape(): Boolean = LocalWindowInfo.current.containerSize.let { it.width > it.height }

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null

// No built-in condensed font to stand in, as on the web.
actual val condensedFontFamily: FontFamily = FontFamily.SansSerif
