package com.example.soundboard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget

// Placeholders until the web app lands (phase 4 of the web plan): enough for the shared UI
// to build for the browser. The file pickers, microphone and wake lock are real browser
// APIs to wire up then; until they are, each control simply does nothing.

@Composable
actual fun rememberOpenFileLauncher(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit = {}

@Composable
actual fun rememberPickImageLauncher(onPicked: (PickedFile) -> Unit): () -> Unit = {}

@Composable
actual fun rememberSaveFileLauncher(mimeType: String, onTarget: (SaveTarget) -> Unit): (suggestedName: String) -> Unit = {}

// The browser asks for the microphone itself when recording starts.
@Composable
actual fun rememberMicrophoneAccess(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

@Composable
actual fun KeepScreenOn(enabled: Boolean) {}

// Browsers have their own back button, which leaves the page; there's nothing to intercept.
@Composable
actual fun BackHandler(onBack: () -> Unit) {}

@Composable
actual fun isLandscape(): Boolean = LocalWindowInfo.current.containerSize.let { it.width > it.height }

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null

// Browsers have no stand-in for Android's built-in condensed font; phase 4 can bundle one.
actual val condensedFontFamily: FontFamily = FontFamily.SansSerif
