package com.example.soundboard.translate

import com.example.soundboard.model.SPANISH
import com.example.soundboard.resources.Res
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The bundled English to Spanish phrasebook (#342): what every phrase on the built-in boards
 * says in Spanish, written by hand. It is looked up before any translator, so a built-in board
 * (or a person's own tile that says "I'm thirsty") gets its Spanish at once, offline, on every
 * platform and browser, with no model to download.
 *
 * Phrases are matched by [normalize]: trimmed, case-insensitive, trailing `.`, `!` and `?`
 * ignored, so "Thank you" and "Thank you." are one entry. The file is made by
 * `scripts/build-phrasebook.py` from the zips in `presets/`, which has the same normalising.
 */
class Phrasebook(private val language: String, entries: Map<String, String>) {

    // Normalised again here so a hand-written file (or a test's) needs no particular spelling.
    private val entries: Map<String, String> =
        entries.entries.mapNotNull { (english, translated) ->
            val key = normalize(english)
            translated.trim().takeIf { key.isNotEmpty() && it.isNotEmpty() }?.let { key to it }
        }.toMap()

    /** How many phrases it knows. */
    val size: Int get() = entries.size

    /** [english] in [target], or null if this phrasebook is for another language or doesn't have the phrase. */
    fun lookup(english: String, target: String): String? =
        if (target == language) entries[normalize(english)] else null

    companion object {
        /** A phrasebook that knows nothing, so a lookup falls through to the translator. */
        val Empty = Phrasebook(SPANISH, emptyMap())

        /** Where the Spanish phrasebook is, as a Compose resource. */
        const val SPANISH_FILE = "files/phrasebook-es.json"

        /**
         * [text] as the phrasebook compares it: trimmed, lower case, inner whitespace collapsed,
         * curly apostrophes straight, and trailing `.`, `!` and `?` dropped. Must stay the same as
         * `normalize()` in `scripts/build-phrasebook.py`.
         */
        fun normalize(text: String): String =
            text.replace('’', '\'').replace('‘', '\'')
                .trim().split(Regex("\\s+")).joinToString(" ").lowercase()
                .trimEnd('.', '!', '?', ' ')

        /** Reads a phrasebook file's text: a JSON object of English phrase to its translation. Anything else is an empty phrasebook. */
        fun parse(language: String, json: String): Phrasebook = try {
            val root = Json.parseToJsonElement(json) as? JsonObject
            Phrasebook(language, root.orEmpty().mapNotNull { (english, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { english to it } }.toMap())
        } catch (e: Exception) {
            Empty
        }

        /**
         * The Spanish phrasebook that ships in the app (`composeResources/files/phrasebook-es.json`),
         * read through Compose Resources so Android, the web and iOS each find it their own way. A file
         * that can't be read is an empty phrasebook: the translator is then all there is, as before.
         */
        suspend fun bundledSpanish(): Phrasebook = try {
            parse(SPANISH, Res.readBytes(SPANISH_FILE).decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Empty
        }
    }
}
