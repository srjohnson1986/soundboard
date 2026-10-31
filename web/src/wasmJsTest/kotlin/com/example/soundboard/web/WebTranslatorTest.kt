package com.example.soundboard.web

import com.example.soundboard.translate.TranslatorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * [ChromeTranslator] against a stand-in for Chrome's Translator API (#332), installed on the page for
 * each test: headless Chrome has no language model to download, and the point is the plumbing
 * between Kotlin and the browser (the promises, the download progress events, the failures), which
 * is the same whichever model is behind the API. Waits are in real time.
 */
class WebTranslatorTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    private suspend fun realDelay(millis: Long) = withContext(Dispatchers.Default) { delay(millis) }

    private suspend fun waitUntil(what: String, maxMillis: Long = 5_000, condition: () -> Boolean) {
        repeat((maxMillis / 50).toInt()) { if (!condition()) realDelay(50) }
        assertTrue(condition(), "timed out waiting for $what")
    }

    private fun translator(): ChromeTranslator = ChromeTranslator(scope)

    @Test
    fun `a browser without the Translator API says why it cannot translate`() = runTest {
        removeTranslatorApi()

        val translator = translator()

        val status = translator.status.value
        assertTrue(status is TranslatorStatus.Unavailable, "was $status")
        assertTrue("Chrome" in status.reason, status.reason)
        assertNull(translator.translate("Water", "es"))
    }

    @Test
    fun `a model that is still to download is offered and then downloads with progress`() = runTest {
        installTranslatorApi(availability = "downloadable", createFails = false)
        val translator = translator()
        waitUntil("the browser to answer") { translator.status.value != TranslatorStatus.Checking }
        assertEquals(TranslatorStatus.NeedsDownload(null), translator.status.value)

        val seen = mutableListOf<TranslatorStatus>()
        scope.launch { translator.status.collect { seen += it } }
        translator.prepare("es")

        assertEquals(TranslatorStatus.Ready, translator.status.value)
        // The browser's progress events (half, then all) reached the status as percentages.
        val percents = seen.filterIsInstance<TranslatorStatus.Downloading>().mapNotNull { it.percent }
        assertTrue(50 in percents && 100 in percents, "progress was $percents in $seen")
    }

    @Test
    fun `translating makes the translator if need be and returns the browser's translation`() = runTest {
        installTranslatorApi(availability = "downloadable", createFails = false)
        val translator = translator()
        waitUntil("the browser to answer") { translator.status.value != TranslatorStatus.Checking }

        assertEquals("ES Water", translator.translate("Water", "es"))
        assertEquals(TranslatorStatus.Ready, translator.status.value)
        assertEquals("ES Juice", translator.translate("Juice", "es"))
        assertEquals(listOf("Water", "Juice"), translated())
    }

    @Test
    fun `a model that is already here is ready at once`() = runTest {
        installTranslatorApi(availability = "available", createFails = false)

        val translator = translator()

        waitUntil("the browser to answer") { translator.status.value != TranslatorStatus.Checking }
        assertEquals(TranslatorStatus.Ready, translator.status.value)
    }

    @Test
    fun `a language pair the browser cannot do is unavailable with the reason`() = runTest {
        installTranslatorApi(availability = "unavailable", createFails = false)

        val translator = translator()

        waitUntil("the browser to answer") { translator.status.value != TranslatorStatus.Checking }
        val status = translator.status.value
        assertTrue(status is TranslatorStatus.Unavailable && "English to es" in status.reason, "was $status")
    }

    @Test
    fun `a failed download is a failure that says why and translating returns nothing`() = runTest {
        installTranslatorApi(availability = "downloadable", createFails = true)
        val translator = translator()
        waitUntil("the browser to answer") { translator.status.value != TranslatorStatus.Checking }

        assertNull(translator.translate("Water", "es"))

        val status = translator.status.value
        assertTrue(status is TranslatorStatus.Failed && "model download failed" in status.message, "was $status")
        assertTrue(status.canTranslate, "a failure can be tried again")
    }

    @Test
    fun `a browser that never answers is reported as a failure and not left checking`() = runTest {
        installTranslatorApi(availability = "never", createFails = false)

        val translator = translator()

        // The answer is cut off after 10 seconds, so this is a real wait.
        waitUntil("the timeout", maxMillis = 15_000) { translator.status.value != TranslatorStatus.Checking }
        val status = translator.status.value
        assertTrue(status is TranslatorStatus.Failed && "did not answer" in status.message, "was $status")
    }
}

/** Replaces the page's Translator API with one that answers [availability] and whose creating fails if [createFails]. */
private fun installTranslatorApi(availability: String, createFails: Boolean): Unit = js(
    """{
        window.soundboardTranslators = undefined;
        window.soundboardTranslatorProgress = 0;
        window.__translated = [];
        window.Translator = {
            availability: () => availability === 'never' ? new Promise(() => {}) : Promise.resolve(availability),
            create: async (options) => {
                if (createFails) throw new Error('model download failed');
                const monitor = new EventTarget();
                options.monitor(monitor);
                for (const loaded of [0.5, 1]) {
                    const event = new Event('downloadprogress');
                    event.loaded = loaded;
                    monitor.dispatchEvent(event);
                    await new Promise(resolve => setTimeout(resolve, 100));
                }
                return { translate: async (text) => { window.__translated.push(text); return 'ES ' + text; } };
            }
        };
    }"""
)

private fun removeTranslatorApi(): Unit = js("{ window.soundboardTranslators = undefined; delete window.Translator; }")

/** What the stand-in was asked to translate, in order. */
private fun translated(): List<String> {
    val joined = translatedJoined()
    return if (joined.isEmpty()) emptyList() else joined.split("\u0000")
}

private fun translatedJoined(): String = js("(window.__translated || []).join('\\u0000')")
