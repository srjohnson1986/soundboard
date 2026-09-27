package com.example.soundboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget
import com.example.soundboard.data.UriPickedFile
import com.example.soundboard.data.UriSaveTarget

// Storage Access Framework pickers: no storage permission needed for any of them.

@Composable
actual fun rememberOpenFileLauncher(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(UriPickedFile(context, it)) }
    }
    return { launcher.launch(mimeTypes.toTypedArray()) }
}

@Composable
actual fun rememberPickImageLauncher(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked(UriPickedFile(context, it)) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberSaveFileLauncher(mimeType: String, onTarget: (SaveTarget) -> Unit): (suggestedName: String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let { onTarget(UriSaveTarget(context, it)) }
    }
    return { name -> launcher.launch(name) }
}

@Composable
actual fun rememberMicrophoneAccess(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onResult(true) else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // Cleared onDispose so leaving the screen (or turning the setting off) doesn't leave
    // the window flag stuck on.
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
actual fun BackHandler(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
}

@Composable
actual fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

actual val condensedFontFamily: FontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold)
)
