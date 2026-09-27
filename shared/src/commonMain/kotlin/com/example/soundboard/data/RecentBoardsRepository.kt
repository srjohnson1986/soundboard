package com.example.soundboard.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Where a recent board came from. Serialized as the lowercase strings older builds wrote. */
@Serializable
enum class RecentBoardKind {
    /** A board saved on this device; [RecentBoardEntry.id] names it. */
    @SerialName("saved") SAVED,

    /** A board built into the app; [RecentBoardEntry.assetName] names it. */
    @SerialName("factory") BUILT_IN
}

/** One board that was actually opened or saved, for the title bar's quick-switch dropdown. */
@Serializable
data class RecentBoardEntry(
    val kind: RecentBoardKind,
    val id: String? = null,
    val assetName: String? = null,
    val label: String,
    val usedAt: Long
)

/**
 * Small on-device history of which boards have actually been used recently —
 * deliberately separate from [SavedBoardRepository]'s saved-board files, since a
 * built-in board (never "saved") needs to show up here too. Kept in the `data`
 * package with no dependency on `BoardRef` (defined alongside `BoardViewModel`),
 * matching the rest of this package's layering.
 */
class RecentBoardsRepository(private val files: FileStore) {

    private val json = BoardJson

    /** Most recently used first, capped at [MAX_ENTRIES]. */
    suspend fun recent(limit: Int = MAX_ENTRIES): List<RecentBoardEntry> = readAll().take(limit)

    /** Records [entry] as just-used, moving it to the front and dropping any earlier entry for the same board. */
    suspend fun recordUsed(entry: RecentBoardEntry) {
        val deduped = readAll().filterNot { it.kind == entry.kind && it.id == entry.id && it.assetName == entry.assetName }
        val next = (listOf(entry) + deduped).take(MAX_ENTRIES)
        runCatching { files.writeText(FILE, json.encodeToString(next)) }
    }

    private suspend fun readAll(): List<RecentBoardEntry> =
        runCatching { json.decodeFromString<List<RecentBoardEntry>>(files.readText(FILE)!!) }.getOrElse { emptyList() }

    private companion object {
        const val MAX_ENTRIES = 5

        // Named from when saved boards were called presets; kept so the history survives updates.
        const val FILE = "recent_presets.json"
    }
}
