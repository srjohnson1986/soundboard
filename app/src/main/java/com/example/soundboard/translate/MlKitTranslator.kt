package com.example.soundboard.translate

import com.example.soundboard.model.SPANISH
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [Translator] on Google's ML Kit on-device translation (#332). The Spanish model (about 30 MB)
 * downloads once, over any connection since someone asked for it, and after that translating
 * works offline. English is built in, so only the target's model is downloaded.
 *
 * ML Kit reports no download progress, only done or failed, so [TranslatorStatus.Downloading]
 * has no percentage here and the screen shows a bar that moves instead of one that fills.
 */
class MlKitTranslator : Translator {

    private val _status = MutableStateFlow<TranslatorStatus>(TranslatorStatus.Checking)
    override val status: StateFlow<TranslatorStatus> = _status.asStateFlow()

    private val clients = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()

    /** One download or translation at a time, as the translators themselves work. */
    private val mutex = Mutex()

    init {
        // Whether the model is here already decides what the screen says before anyone asks.
        val model = remoteModel(SPANISH)
        if (model == null) {
            _status.value = TranslatorStatus.Unavailable("ML Kit has no model for this language.")
        } else {
            RemoteModelManager.getInstance().isModelDownloaded(model)
                .addOnSuccessListener { downloaded ->
                    if (_status.value == TranslatorStatus.Checking) {
                        _status.value = if (downloaded) TranslatorStatus.Ready else TranslatorStatus.NeedsDownload(MODEL_MEGABYTES)
                    }
                }
                .addOnFailureListener { _status.value = TranslatorStatus.NeedsDownload(MODEL_MEGABYTES) }
        }
    }

    override suspend fun prepare(target: String) {
        mutex.withLock { ensureModel(target) }
    }

    override suspend fun translate(text: String, target: String, from: String): String? = mutex.withLock {
        if (!ensureModel(target, from)) return@withLock null
        val client = client(from, target) ?: return@withLock null
        try {
            client.translate(text).await().also { _status.value = TranslatorStatus.Ready }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = TranslatorStatus.Failed(describe(e))
            null
        }
    }

    /** Downloads [target]'s model if it isn't here, setting [status] as it goes; false if that failed. */
    private suspend fun ensureModel(target: String, from: String = "en"): Boolean {
        if (_status.value == TranslatorStatus.Ready) return true
        val client = client(from, target) ?: return false
        _status.value = TranslatorStatus.Downloading(percent = null)
        return try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            _status.value = TranslatorStatus.Ready
            true
        } catch (e: CancellationException) {
            // Cancel on the banner while the model downloads (#342): not a failure, so it can be asked for again.
            // ML Kit carries on downloading in the background, and the next call then finds the model here.
            _status.value = TranslatorStatus.NeedsDownload(MODEL_MEGABYTES)
            throw e
        } catch (e: Exception) {
            _status.value = TranslatorStatus.Failed(describe(e))
            false
        }
    }

    private fun client(from: String, target: String): com.google.mlkit.nl.translate.Translator? {
        val source = TranslateLanguage.fromLanguageTag(from)
        val destination = TranslateLanguage.fromLanguageTag(target)
        if (source == null || destination == null) {
            _status.value = TranslatorStatus.Unavailable("ML Kit can't translate from $from to $target.")
            return null
        }
        return clients.getOrPut("$source-$destination") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(destination).build())
        }
    }

    private fun remoteModel(language: String): TranslateRemoteModel? =
        TranslateLanguage.fromLanguageTag(language)?.let { TranslateRemoteModel.Builder(it).build() }

    /** What went wrong, in words for the screen. */
    private fun describe(e: Exception): String = when ((e as? MlKitException)?.errorCode) {
        MlKitException.NETWORK_ISSUE -> "The language model download needs an internet connection. Connect and try again."
        MlKitException.NOT_ENOUGH_SPACE -> "There isn't enough storage space for the language model (about $MODEL_MEGABYTES MB)."
        else -> "The language model couldn't be downloaded or used" + (e.message?.let { " ($it)" } ?: "") + ". Try again."
    }

    private companion object {
        /** ML Kit says its language models are "around 30 MB". */
        const val MODEL_MEGABYTES = 30
    }
}

/** Bridges a Google Play services [Task] into a suspending call, so this module needs no extra library for it. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
