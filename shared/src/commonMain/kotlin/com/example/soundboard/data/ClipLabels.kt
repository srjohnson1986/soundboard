package com.example.soundboard.data

import com.example.soundboard.model.EmojiNames
import kotlinx.serialization.Serializable

/**
 * The last tile a sound was on, as `clip-labels.json` remembers it (see
 * [BoardRepository.recordClipLabels]): enough to tell what an unused clip was before deciding to
 * keep or delete it, and to name it in the unused-clips export.
 */
@Serializable
data class ClipLabel(
    val label: String = "",
    val emoji: String? = null,
    /** The name of the page the tile was on. */
    val page: String = ""
) {
    /** How the tile is called in the cleanup list: its label, else its emoji's name, else "Unnamed tile". */
    val tileName: String
        get() = label.trim().ifEmpty { emoji?.let(EmojiNames::suggest).orEmpty() }.ifEmpty { UNNAMED_TILE }

    /** "Yes, Food page" (just "Yes" when the page has no name). */
    val description: String
        get() = page.trim().let { if (it.isEmpty()) tileName else "$tileName, $it page" }
}

private const val UNNAMED_TILE = "Unnamed tile"

/** An unused sound as the cleanup dialog lists it and its export zip names it. */
class UnusedClipName(
    /** The name it's stored under in `sounds/`. */
    val fileName: String,
    /** "Yes, Food page", or "Unused clip 1" when nothing is remembered about it. */
    val description: String,
    /** Its path in the export zip: `Food/Yes.m4a`, or `Unused clip 1.m4a` with no folder. */
    val zipPath: String
)

/**
 * Names for [fileNames] (in this order), given what [labels] remembers about each. A clip with a
 * record goes in a folder named after its page, as the tile's name (the rules in [ClipNamer], so
 * `Food/Yes.m4a`, then `Food/Yes (Food).m4a` for a second "Yes" on that page); one without is
 * `Unused clip N`, numbered from 1 in order. Pages whose names differ only by case share a folder.
 */
internal fun nameUnusedClips(fileNames: List<String>, labels: Map<String, ClipLabel>): List<UnusedClipName> {
    val folders = mutableMapOf<String, Pair<String, ClipNamer>>()
    var unlabeled = 0
    return fileNames.map { fileName ->
        val label = labels[fileName]
        if (label == null) {
            unlabeled++
            val description = "$UNUSED_CLIP $unlabeled"
            UnusedClipName(fileName, description, description + ClipNames.extensionOf(fileName))
        } else {
            val folderName = ClipNames.clean(label.page)
            val (folder, namer) = folders.getOrPut(folderName.lowercase()) { folderName to ClipNamer() }
            val name = namer.nameFor(label.label, label.emoji, folder, fileName)
            UnusedClipName(fileName, label.description, if (folder.isEmpty()) name else "$folder/$name")
        }
    }
}

private const val UNUSED_CLIP = "Unused clip"
