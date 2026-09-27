package com.example.soundboard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import com.example.soundboard.data.BrowserPickedFile
import com.example.soundboard.data.DownloadSaveTarget
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget
import com.example.soundboard.data.openFileChooser

@Composable
actual fun rememberOpenFileLauncher(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return { openFileChooser(mimeTypes.joinToString(",")) { currentOnPicked(BrowserPickedFile(it)) } }
}

@Composable
actual fun rememberPickImageLauncher(onPicked: (PickedFile) -> Unit): () -> Unit =
    rememberOpenFileLauncher(listOf("image/*"), onPicked)

// A browser can't ask where to save: the file goes to its downloads, under the suggested name.
@Composable
actual fun rememberSaveFileLauncher(mimeType: String, onTarget: (SaveTarget) -> Unit): (suggestedName: String) -> Unit {
    val currentOnTarget by rememberUpdatedState(onTarget)
    return { name -> currentOnTarget(DownloadSaveTarget(name, mimeType)) }
}

// The browser asks for the microphone itself when recording starts; if the user says no,
// recording fails to start and the app says so.
@Composable
actual fun rememberMicrophoneAccess(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return { currentOnResult(true) }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        val lock = if (enabled) acquireWakeLock() else null
        onDispose { lock?.let(::releaseWakeLock) }
    }
}

// Browsers have their own back button, which leaves the page; there's nothing to intercept.
@Composable
actual fun BackHandler(onBack: () -> Unit) {}

@Composable
actual fun isLandscape(): Boolean = LocalWindowInfo.current.containerSize.let { it.width > it.height }

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null

// Browsers have no stand-in for Android's built-in condensed font.
actual val condensedFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Holds a screen wake lock (where the browser supports one) until [releaseWakeLock]. The
 * browser drops the lock whenever the tab is hidden, so it's taken again each time the tab
 * comes back.
 */
private fun acquireWakeLock(): JsAny = js(
    """(() => {
        const holder = { sentinel: null, released: false };
        const acquire = () => {
            if (holder.released || !('wakeLock' in navigator) || document.visibilityState !== 'visible') return;
            navigator.wakeLock.request('screen')
                .then(sentinel => { if (holder.released) sentinel.release(); else holder.sentinel = sentinel; })
                .catch(() => {});
        };
        holder.onVisibilityChange = acquire;
        document.addEventListener('visibilitychange', acquire);
        acquire();
        return holder;
    })()"""
)

private fun releaseWakeLock(holder: JsAny): Unit = js(
    """{
        holder.released = true;
        document.removeEventListener('visibilitychange', holder.onVisibilityChange);
        if (holder.sentinel) holder.sentinel.release();
    }"""
)
