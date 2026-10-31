package com.example.soundboard.web

import com.example.soundboard.translate.Translator
import com.example.soundboard.translate.TranslatorStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [Translator] on Chrome's built-in Translator API (#332), which runs a small language model
 * the browser downloads once and then translates with offline. As of Chrome 138 it is in
 * Chrome (and Edge) on a computer only: no Firefox or Safari, no phone browser, and not in
 * workers. Anywhere else this reports [TranslatorStatus.Unavailable] with that reason, so the
 * controls say why they can't, rather than vanishing.
 *
 * Chrome reports every language pair as "downloadable" until a page has created a translator
 * for it, so [TranslatorStatus.NeedsDownload] can mean the model is in fact already here; making
 * the translator then finishes at once. Creating one that has to download needs a tap, which the
 * button that asks for it is.
 */
class ChromeTranslator(private val scope: CoroutineScope = MainScope()) : Translator {

    private val _status = MutableStateFlow<TranslatorStatus>(TranslatorStatus.Checking)
    override val status: StateFlow<TranslatorStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    init {
        if (!translatorSupported()) {
            _status.value = TranslatorStatus.Unavailable(NOT_SUPPORTED)
        } else {
            scope.launch { refresh("es") }
        }
    }

    /** Asks the browser what it can do for English to [target], and shows it. */
    private suspend fun refresh(target: String) {
        val availability = translatorAvailability("en", target).await<JsString>().toString()
        if (_status.value is TranslatorStatus.Downloading || _status.value == TranslatorStatus.Ready) return
        _status.value = when {
            availability == "available" -> TranslatorStatus.Ready
            availability == "downloadable" -> TranslatorStatus.NeedsDownload(approximateMegabytes = null)
            availability == "downloading" -> TranslatorStatus.Downloading(percent = null)
            availability == "unavailable" -> TranslatorStatus.Unavailable("this browser can't translate from English to $target.")
            else -> TranslatorStatus.Failed("the browser wouldn't say whether it can translate (${availability.removePrefix("error:")}).")
        }
    }

    override suspend fun prepare(target: String) {
        mutex.withLock { ensureTranslator("en", target) }
    }

    override suspend fun translate(text: String, target: String, from: String): String? = mutex.withLock {
        if (!ensureTranslator(from, target)) return@withLock null
        val result = translateWith(from, target, text).await<JsString>().toString()
        if (result.startsWith("ok:")) {
            _status.value = TranslatorStatus.Ready
            result.removePrefix("ok:")
        } else {
            _status.value = TranslatorStatus.Failed("Chrome couldn't translate that (${result.removePrefix("error:")}).")
            null
        }
    }

    /** Makes the browser's translator for [from] to [target], downloading its model if need be; false if that failed. */
    private suspend fun ensureTranslator(from: String, target: String): Boolean {
        if (!translatorSupported()) return false
        if (_status.value == TranslatorStatus.Ready) return true
        _status.value = TranslatorStatus.Downloading(percent = null)
        val result = try {
            createTranslator(from, target) {
                _status.value = TranslatorStatus.Downloading(percent = (downloadProgress() * 100).toInt().coerceIn(0, 100))
            }.await<JsString>().toString()
        } catch (e: CancellationException) {
            // Cancel on the banner while the model downloads (#342): not a failure, so it can be asked for again.
            // The browser carries on with the download (the API has no way to stop it here), and the next call finds the model.
            _status.value = TranslatorStatus.NeedsDownload(approximateMegabytes = null)
            throw e
        }
        return if (result == "ok") {
            _status.value = TranslatorStatus.Ready
            true
        } else {
            _status.value = TranslatorStatus.Failed("Chrome couldn't get the language model (${result.removePrefix("error:")}). Try again.")
            false
        }
    }

    private companion object {
        const val NOT_SUPPORTED = "this browser has no built-in translator. Chrome or Edge on a computer (version 138 or later) does; " +
            "Firefox, Safari and phone browsers don't yet."
    }
}

private fun translatorSupported(): Boolean = js("typeof Translator !== 'undefined' && typeof Translator.create === 'function'")

private fun translatorAvailability(from: String, target: String): Promise<JsString> = js(
    """(async () => {
        try {
            // Some browsers (an embedded one, a profile with the translation component missing) never answer.
            const answer = await Promise.race([
                Translator.availability({ sourceLanguage: from, targetLanguage: target }),
                new Promise(resolve => setTimeout(() => resolve('error:the browser did not answer'), 10000))
            ]);
            return answer;
        } catch (e) {
            return 'error:' + (e && e.message ? e.message : e);
        }
    })()"""
)

/** Creates (once per pair) the browser's translator, reporting its model download through [onProgress]; resolves to "ok" or "error:...". */
private fun createTranslator(from: String, target: String, onProgress: () -> Unit): Promise<JsString> = js(
    """(async () => {
        try {
            const translators = window.soundboardTranslators || (window.soundboardTranslators = {});
            const key = from + '-' + target;
            if (!translators[key]) {
                translators[key] = await Translator.create({
                    sourceLanguage: from,
                    targetLanguage: target,
                    monitor(m) {
                        m.addEventListener('downloadprogress', e => { window.soundboardTranslatorProgress = e.loaded; onProgress(); });
                    }
                });
            }
            return 'ok';
        } catch (e) {
            return 'error:' + (e && e.message ? e.message : e);
        }
    })()"""
)

/** How much of the model has downloaded, 0 to 1. */
private fun downloadProgress(): Double = js("window.soundboardTranslatorProgress || 0")

/** Resolves to "ok:" and the translation, or "error:" and why. */
private fun translateWith(from: String, target: String, text: String): Promise<JsString> = js(
    """(async () => {
        try {
            const translator = window.soundboardTranslators[from + '-' + target];
            return 'ok:' + await translator.translate(text);
        } catch (e) {
            return 'error:' + (e && e.message ? e.message : e);
        }
    })()"""
)
