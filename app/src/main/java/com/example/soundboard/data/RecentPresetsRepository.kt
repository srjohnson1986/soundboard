package com.example.soundboard.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName

/** Where a recent preset came from. Serialized as the lowercase strings older builds wrote. */
@Serializable
enum class RecentPresetKind {
    /** A preset saved on this device; [RecentPresetEntry.id] names it. */
    @SerialName("saved") SAVED,

    /** A preset bundled with the app; [RecentPresetEntry.assetName] names it. */
    @SerialName("factory") FACTORY
}

/** One preset that was actually loaded or saved, for the title bar's quick-switch dropdown. */
@Serializable
data class RecentPresetEntry(
    val kind: RecentPresetKind,
    val id: String? = null,
    val assetName: String? = null,
    val label: String,
    val usedAt: Long
)

/**
 * Small on-device history of which presets have actually been used recently —
 * deliberately separate from [PresetRepository]'s saved-preset files, since a
 * factory preset (never "saved") needs to show up here too. Kept in the `data`
 * package with no dependency on `PresetRef` (defined alongside `BoardViewModel`),
 * matching the rest of this package's layering.
 */
class RecentPresetsRepository(context: Context) {

    private val file = File(context.filesDir, "recent_presets.json")

    private val json = BoardJson

    /** Most recently used first, capped at [MAX_ENTRIES]. */
    fun recent(limit: Int = MAX_ENTRIES): List<RecentPresetEntry> = readAll().take(limit)

    /** Records [entry] as just-used, moving it to the front and dropping any earlier entry for the same preset. */
    fun recordUsed(entry: RecentPresetEntry) {
        val deduped = readAll().filterNot { it.kind == entry.kind && it.id == entry.id && it.assetName == entry.assetName }
        val next = (listOf(entry) + deduped).take(MAX_ENTRIES)
        runCatching { file.writeText(json.encodeToString(next)) }
    }

    private fun readAll(): List<RecentPresetEntry> =
        runCatching { json.decodeFromString<List<RecentPresetEntry>>(file.readText()) }.getOrElse { emptyList() }

    private companion object {
        const val MAX_ENTRIES = 5
    }
}
