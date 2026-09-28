@file:OptIn(ExperimentalForeignApi::class)

package com.example.soundboard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import com.example.soundboard.data.IosPickedFile
import com.example.soundboard.data.IosSaveTarget
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.SaveTarget
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// The iOS side of Platform.kt (#266): the Files picker opens and saves files, as a browser's
// file chooser and downloads do on the web.

/**
 * Whether the shared UI is running inside the iOS app, which sets this as it starts. The
 * shared tests run on the simulator without one, where there's no UIApplication to use.
 */
object IosHost {
    var inApp = false
}

@Composable
actual fun rememberOpenFileLauncher(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { currentOnPicked(IosPickedFile(it)) } }
    return remember(mimeTypes) {
        { present(UIDocumentPickerViewController(forOpeningContentTypes = contentTypes(mimeTypes), asCopy = true), delegate) }
    }
}

@Composable
actual fun rememberPickImageLauncher(onPicked: (PickedFile) -> Unit): () -> Unit =
    rememberOpenFileLauncher(listOf("image/*"), onPicked)

// Like a browser download: the file is written first, then the Files picker asks where it goes.
@Composable
actual fun rememberSaveFileLauncher(mimeType: String, onTarget: (SaveTarget) -> Unit): (suggestedName: String) -> Unit {
    val currentOnTarget by rememberUpdatedState(onTarget)
    val delegate = remember { PickerDelegate {} }
    return { name ->
        currentOnTarget(
            IosSaveTarget(name) { url ->
                dispatch_async(dispatch_get_main_queue()) {
                    present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true), delegate)
                }
            }
        )
    }
}

// iOS asks the first time; the answer comes back on another thread.
@Composable
actual fun rememberMicrophoneAccess(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            dispatch_async(dispatch_get_main_queue()) { currentOnResult(granted) }
        }
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (IosHost.inApp) UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { if (IosHost.inApp) UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

// iOS has no back button; going back is a swipe the app's own screens would handle.
@Composable
actual fun BackHandler(onBack: () -> Unit) {}

@Composable
actual fun isLandscape(): Boolean = LocalWindowInfo.current.containerSize.let { it.width > it.height }

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null

// No built-in condensed font to stand in, as on the web.
actual val condensedFontFamily: FontFamily = FontFamily.SansSerif

/** Hands the first picked file's URL to [onPicked]. Kept by the caller: a picker only holds its delegate weakly. */
private class PickerDelegate(private val onPicked: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }
}

private fun present(picker: UIDocumentPickerViewController, delegate: PickerDelegate) {
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

@Suppress("DEPRECATION") // keyWindow: the app has the one window
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

private fun contentTypes(mimeTypes: List<String>): List<UTType> = mimeTypes.map { mime ->
    when (mime) {
        "audio/*" -> UTTypeAudio
        "image/*" -> UTTypeImage
        else -> UTType.typeWithMIMEType(mime) ?: UTTypeData
    }
}
