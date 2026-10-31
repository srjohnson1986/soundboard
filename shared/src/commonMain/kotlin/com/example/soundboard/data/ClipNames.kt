package com.example.soundboard.data

import com.example.soundboard.model.EmojiNames
import com.example.soundboard.model.Tile

/**
 * Readable file names for sounds that are stored under random IDs: what a backup zip calls them
 * ([BoardRepository.exportTo]) and what the unused-clips export does. The names only ever exist
 * inside a zip; files on the device keep their IDs, and a restore gives every clip a fresh ID.
 *
 * A namer hands out names one at a time, so the order tiles are given in decides who keeps the
 * plain name: the first "Yes" is `Yes.m4a`, the next `Yes (Food).m4a` (its page's name), then
 * `Yes (Food) 2.m4a`. Names are compared ignoring case, since Windows and macOS would otherwise
 * merge two files that differ only by it.
 *
 * @param taken names that are already spoken for (compared ignoring case, extension included),
 *   such as the ID names of clips that keep them.
 */
internal class ClipNamer(taken: Collection<String> = emptyList()) {

    private val used = taken.mapTo(mutableSetOf()) { it.lowercase() }

    /** A name for a clip stored as [originalName] and used by [tile], which is on page [pageName]. */
    fun nameFor(tile: Tile, pageName: String, originalName: String): String =
        nameFor(tile.label, tile.emoji, pageName, originalName)

    /**
     * A name for a clip stored as [originalName] (its extension is kept), from the tile's [label],
     * else its [emoji]'s name, else `Sound`; [pageName] tells apart two tiles with the same name.
     */
    fun nameFor(label: String, emoji: String?, pageName: String, originalName: String): String {
        val extension = ClipNames.extensionOf(originalName)
        val stem = ClipNames.stemFor(label, emoji)
        val page = ClipNames.clean(pageName, ClipNames.MAX_PAGE_LENGTH)

        fun claim(stemCandidate: String): String? {
            val candidate = stemCandidate + extension
            return if (used.add(candidate.lowercase())) candidate else null
        }

        claim(stem)?.let { return it }
        val withPage = if (page.isEmpty()) stem else "$stem ($page)"
        if (page.isNotEmpty()) claim(withPage)?.let { return it }
        var number = 2
        while (true) {
            claim("$withPage $number")?.let { return it }
            number++
        }
    }
}

/** The rules for a clip's readable name; see [ClipNamer]. */
internal object ClipNames {
    /** A tile's name is cut to about this many characters. */
    const val MAX_LENGTH = 60

    /** A page's name, added in brackets to tell apart two clips, is cut to this many. */
    const val MAX_PAGE_LENGTH = 30

    const val FALLBACK_STEM = "Sound"

    private const val FORBIDDEN = "\\/:*?\"<>|"

    /** Names Windows won't let a file have, with or without an extension. */
    private val RESERVED = setOf("con", "prn", "aux", "nul") +
        (1..9).flatMap { listOf("com$it", "lpt$it") }

    /**
     * [text] as a name that's safe on Windows, macOS and Linux: `\ / : * ? " < > |` and control
     * characters taken out (a tab or newline counts as a space), runs of spaces made one, spaces
     * and dots trimmed from both ends, and cut to [maxLength] without splitting a character. It
     * keeps letters from any language. Empty when nothing is left, and not checked against the
     * reserved names ([stemFor] does that).
     */
    fun clean(text: String, maxLength: Int = MAX_LENGTH): String {
        val kept = buildString {
            for (c in text) {
                when {
                    c == '\t' || c == '\n' || c == '\r' -> append(' ')
                    c in FORBIDDEN || c.isISOControl() -> Unit
                    else -> append(c)
                }
            }
        }
        val collapsed = kept.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        val cut = collapsed.cutTo(maxLength)
        return cut.trim(' ', '.')
    }

    /** The name, without its extension, for a tile with [label] and [emoji]: the label, else the emoji's name, else `Sound`. */
    fun stemFor(label: String, emoji: String?): String {
        val fromLabel = clean(label)
        val stem = fromLabel.ifEmpty { clean(emoji?.let(EmojiNames::suggest).orEmpty()) }.ifEmpty { FALLBACK_STEM }
        return avoidReserved(stem)
    }

    /** [stem] with an underscore on the end if Windows reserves it (`CON`, `NUL`, `COM1`...), which it does even with an extension after it. */
    private fun avoidReserved(stem: String): String =
        if (stem.substringBefore('.').trimEnd().lowercase() in RESERVED) "${stem}_" else stem

    /** The extension of [fileName] with its dot (`.m4a`), only if it's a short run of letters and digits; otherwise empty. */
    fun extensionOf(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= 8 && ext.all { it.isLetterOrDigit() && it.code < 128 }) ".$ext" else ""
    }

    /** The first [max] characters, or one fewer if that would split a surrogate pair. */
    private fun String.cutTo(max: Int): String {
        if (length <= max) return this
        val end = if (this[max - 1].isHighSurrogate()) max - 1 else max
        return substring(0, end)
    }
}
