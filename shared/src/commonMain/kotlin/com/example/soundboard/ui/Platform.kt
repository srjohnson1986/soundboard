package com.example.soundboard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget

// Everything the UI needs from the platform it runs on, each implemented once per
// platform (androidMain, wasmJsMain). The rest of ui/ is plain Compose and runs anywhere.

/** Returns a function that opens the file picker for [mimeTypes]; [onPicked] gets the chosen file. Cancelling calls nothing. */
@Composable
expect fun rememberOpenFileLauncher(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit

/** Returns a function that opens the image picker; [onPicked] gets the chosen image. */
@Composable
expect fun rememberPickImageLauncher(onPicked: (PickedFile) -> Unit): () -> Unit

/** Returns a function that asks where to save a file of [mimeType], suggesting the name it's called with; [onTarget] gets the chosen place. */
@Composable
expect fun rememberSaveFileLauncher(mimeType: String, onTarget: (SaveTarget) -> Unit): (suggestedName: String) -> Unit

/**
 * Returns a function that makes sure the app may use the microphone, asking the user if it
 * has to, then calls [onResult] with whether it may.
 */
@Composable
expect fun rememberMicrophoneAccess(onResult: (granted: Boolean) -> Unit): () -> Unit

/** Keeps the screen from locking while [enabled] and this is in the composition. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** Calls [onBack] on the platform's back gesture or button, where there is one. */
@Composable
expect fun BackHandler(onBack: () -> Unit)

/** Whether the app's window is in landscape. */
@Composable
expect fun isLandscape(): Boolean

/** A color scheme taken from the device (Android 12's dynamic color); null to use the default one. */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?

/** The narrower sans-serif offered as the "Roboto Condensed" label font. */
expect val condensedFontFamily: FontFamily
