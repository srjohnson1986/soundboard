package com.example.soundboard

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.AudioRecorder
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.audio.Speaker
import com.example.soundboard.audio.TtsSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.PresetRepository
import com.example.soundboard.data.RecentPresetEntry
import com.example.soundboard.data.RecentPresetsRepository
import com.example.soundboard.data.SavedPreset
import com.example.soundboard.model.Board
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoardViewModel(
    private val repo: BoardRepository,
    private val player: Player,
    private val recorder: Recorder,
    private val presetRepo: PresetRepository,
    private val speaker: Speaker,
    private val devicePrefs: DevicePreferences,
    private val recentPresetsRepo: RecentPresetsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    /** One-off status text for the UI to show (e.g. in a Snackbar), then clear. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences]. */
    private val _performanceModeEnabled = MutableStateFlow(devicePrefs.performanceModeEnabled)
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled.asStateFlow()

    private val _presets = MutableStateFlow<List<SavedPreset>>(emptyList())
    val presets: StateFlow<List<SavedPreset>> = _presets.asStateFlow()

    /** Sound files not referenced by any tile on the live board or any saved preset — see [refreshStrayClips]. */
    private val _strayClips = MutableStateFlow<List<StrayClip>>(emptyList())
    val strayClips: StateFlow<List<StrayClip>> = _strayClips.asStateFlow()

    /** Presets actually loaded/saved recently, newest first — see [RecentPresetsRepository]. */
    private val _recentPresets = MutableStateFlow<List<RecentPresetItem>>(emptyList())
    val recentPresets: StateFlow<List<RecentPresetItem>> = _recentPresets.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** The file the active recording is writing to; only meaningful while [isRecording] is true. */
    private var pendingRecordingFile: File? = null

    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (!repo.hasSavedBoard()) repo.importFromAsset(FALLBACK_PRESET_ASSET)
            }
            val loaded = withContext(ioDispatcher) { repo.load() }
            // Only overrides where the pager starts, not what's saved to disk — a plain
            // switchTo(), same as any other in-session page switch, see BoardViewModel.switchPage.
            val initial = if (loaded.openOnHomePage) {
                loaded.homePageIndex?.let { loaded.switchTo(it) } ?: loaded
            } else {
                loaded
            }
            _board.value = initial
            withContext(ioDispatcher) { loadSounds(initial) }
        }
    }

    fun play(tile: Tile) {
        val name = tile.fileName
        if (name != null) {
            player.play(name, tile.volume)
        } else if (tile.speechText.isNotBlank() && (tile.speakLabel || _board.value.speakUnrecordedTilesEnabled)) {
            speaker.speak(tile.speechText)
        }
    }

    /** Speaks arbitrary text not tied to any tile — for a one-off phrase no pad covers. */
    fun speakAdHoc(text: String) {
        speaker.speak(text)
    }

    fun setLabel(tileId: String, label: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = label) else it }
    }

    /** Sets the longer text spoken instead of the label; a blank value clears it back to null (falls back to the label). */
    fun setTtsScript(tileId: String, script: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(ttsScript = script.ifBlank { null }) else it }
    }

    fun setVolume(tileId: String, volume: Float) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(volume = volume.coerceIn(0f, 1f)) else it }
    }

    fun setColor(tileId: String, colorArgb: Int?) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(colorArgb = colorArgb) else it }
    }

    /** Per-tile border override; null inherits the page's, then the board's. */
    fun setBorder(tileId: String, border: TileBorder?) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(border = border) else it }
    }

    fun setSpeakLabel(tileId: String, value: Boolean) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(speakLabel = value) else it }
    }

    fun assignSound(tileId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { repo.importSound(uri) } ?: return@launch
            withContext(ioDispatcher) { player.load(name, repo.soundFile(name)) }
            updateTiles { tiles ->
                tiles.map { if (it.id == tileId) it.copy(fileName = name) else it }
            }
        }
    }

    fun clearTile(tileId: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = "", fileName = null, speakLabel = false) else it }
    }

    /** Detaches a tile's sound without touching its label or [Tile.speakLabel]. */
    fun removeSound(tileId: String) = updateTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(fileName = null) else it }
    }

    /** Starts recording into a fresh file; call [stopRecording] or [cancelRecording] to end it. */
    fun startRecording() {
        if (_isRecording.value) return
        viewModelScope.launch {
            val file = withContext(ioDispatcher) { repo.newRecordingFile() }
            val started = withContext(ioDispatcher) { recorder.start(file) }
            if (started) {
                pendingRecordingFile = file
                _isRecording.value = true
            } else {
                _message.value = "Couldn't start recording"
            }
        }
    }

    /** Stops the active recording and points [tileId] at the result. */
    fun stopRecording(tileId: String) {
        viewModelScope.launch {
            val name = finishRecording() ?: return@launch
            updateTiles { tiles -> tiles.map { if (it.id == tileId) it.copy(fileName = name) else it } }
        }
    }

    /** Abandons the active recording without assigning it to any tile. */
    fun cancelRecording() {
        if (!_isRecording.value) return
        viewModelScope.launch {
            withContext(ioDispatcher) { recorder.cancel() }
            _isRecording.value = false
            pendingRecordingFile?.delete()
            pendingRecordingFile = null
        }
    }

    /** Stops the recorder, loads the clip into the player, and returns its file name — or null on failure. */
    private suspend fun finishRecording(): String? {
        if (!_isRecording.value) return null
        val ok = withContext(ioDispatcher) { recorder.stop() }
        _isRecording.value = false
        val file = pendingRecordingFile
        pendingRecordingFile = null
        if (!ok || file == null) {
            file?.delete()
            _message.value = "Recording failed"
            return null
        }
        withContext(ioDispatcher) { player.load(file.name, file) }
        return file.name
    }

    /** Whether the home page's first row shows fixed above every other page. */
    fun setStickyHomeRowEnabled(value: Boolean) {
        commit(_board.value.copy(stickyHomeRowEnabled = value))
    }

    /** Global tile border; overridden per-page by [setPageBorder] and per-tile by [setBorder]. */
    fun setTileBorder(value: TileBorder) {
        commit(_board.value.copy(tileBorder = value))
    }

    /** Disables tile shadows to help scrolling stay smooth on slower devices. */
    fun setPerformanceModeEnabled(value: Boolean) {
        devicePrefs.performanceModeEnabled = value
        _performanceModeEnabled.value = value
    }

    // The sticky home row shown on other pages is just the home page's own first
    // row of tiles — these mutators edit it via Board.updatingPage(homeIndex, ...)
    // rather than updatingCurrentPage, since the home page usually isn't the page
    // being viewed when its sticky banner is tapped.

    fun setHomeRowLabel(tileId: String, label: String) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = label) else it }
    }

    /** Same as [setTtsScript], for a home-row tile edited via the sticky banner. */
    fun setHomeRowTtsScript(tileId: String, script: String) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(ttsScript = script.ifBlank { null }) else it }
    }

    fun setHomeRowVolume(tileId: String, volume: Float) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(volume = volume.coerceIn(0f, 1f)) else it }
    }

    fun setHomeRowColor(tileId: String, colorArgb: Int?) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(colorArgb = colorArgb) else it }
    }

    /** Same as [setBorder], for a home-row tile edited via the sticky banner. */
    fun setHomeRowBorder(tileId: String, border: TileBorder?) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(border = border) else it }
    }

    fun setHomeRowSpeakLabel(tileId: String, value: Boolean) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(speakLabel = value) else it }
    }

    fun assignHomeRowSound(tileId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { repo.importSound(uri) } ?: return@launch
            withContext(ioDispatcher) { player.load(name, repo.soundFile(name)) }
            updateHomeRowTiles { tiles ->
                tiles.map { if (it.id == tileId) it.copy(fileName = name) else it }
            }
        }
    }

    fun clearHomeRowTile(tileId: String) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(label = "", fileName = null, speakLabel = false) else it }
    }

    /** Same as [removeSound], for a home-row tile edited via the sticky banner. */
    fun removeHomeRowSound(tileId: String) = updateHomeRowTiles { tiles ->
        tiles.map { if (it.id == tileId) it.copy(fileName = null) else it }
    }

    /** Same as [stopRecording], for a home-row tile edited via the sticky banner. */
    fun stopHomeRowRecording(tileId: String) {
        viewModelScope.launch {
            val name = finishRecording() ?: return@launch
            updateHomeRowTiles { tiles -> tiles.map { if (it.id == tileId) it.copy(fileName = name) else it } }
        }
    }

    fun setDefaultPageRows(value: Int) {
        commit(_board.value.copy(defaultPageRows = value))
    }

    fun setDefaultPageColumns(value: Int) {
        commit(_board.value.copy(defaultPageColumns = value))
    }

    fun resize(index: Int, rows: Int, columns: Int) {
        commit(_board.value.updatingPage(index) { it.resized(rows, columns) })
    }

    fun setTileAspectRatio(index: Int, ratio: Float) {
        commit(_board.value.updatingPage(index) { it.copy(tileAspectRatio = ratio) })
    }

    fun setPageColor(index: Int, colorArgb: Int?) {
        commit(_board.value.updatingPage(index) { it.copy(color = colorArgb) })
    }

    /** Per-page border override; null inherits the board's global setting. */
    fun setPageBorder(index: Int, border: TileBorder?) {
        commit(_board.value.updatingPage(index) { it.copy(border = border) })
    }

    /** Whether a fresh launch jumps to the home page instead of resuming the last-viewed one. */
    fun setOpenOnHomePage(value: Boolean) {
        commit(_board.value.copy(openOnHomePage = value))
    }

    /** Minutes of inactivity before auto-return to home; 0 disables it. */
    fun setIdleTimeoutMinutes(value: Int) {
        commit(_board.value.copy(idleTimeoutMinutes = value))
    }

    /** How long a page-tab press must be held before it counts as a long-press, in milliseconds. */
    fun setLongPressDurationMillis(value: Int) {
        commit(_board.value.copy(longPressDurationMillis = value))
    }

    fun setThemeMode(mode: ThemeMode) {
        commit(_board.value.copy(themeMode = mode))
    }

    fun setKeepScreenAwake(value: Boolean) {
        commit(_board.value.copy(keepScreenAwake = value))
    }

    fun setHapticFeedbackEnabled(value: Boolean) {
        commit(_board.value.copy(hapticFeedbackEnabled = value))
    }

    /** Whether a tile with no sound file speaks its label via TTS on tap, even without its own speak-label switch on. */
    fun setSpeakUnrecordedTilesEnabled(value: Boolean) {
        commit(_board.value.copy(speakUnrecordedTilesEnabled = value))
    }

    /** Whether blank tiles are hidden (and untappable) outside of edit mode, to avoid a stray tap opening the editor. */
    fun setHideBlankTilesEnabled(value: Boolean) {
        commit(_board.value.copy(hideBlankTilesEnabled = value))
    }

    fun renameBoard(name: String) {
        commit(_board.value.copy(name = name.ifBlank { "New Board" }))
    }

    fun setHomePage(index: Int) {
        commit(_board.value.withHomePage(index))
    }

    fun clearHomePage() {
        commit(_board.value.clearingHomePage())
    }

    fun addPage(name: String) {
        commit(_board.value.addPage(name.ifBlank { "Page ${_board.value.pages.size + 1}" }))
    }

    fun renamePage(index: Int, name: String) {
        commit(_board.value.renamePage(index, name))
    }

    fun deletePage(index: Int) {
        commit(_board.value.removePage(index))
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        commit(_board.value.movedPage(fromIndex, toIndex))
    }

    /** Switches the active page without touching disk — nothing about the board changed. */
    fun switchPage(index: Int) {
        _board.value = _board.value.switchTo(index)
    }

    /** Live-reorders tiles during a drag without touching disk; see [commitOrder]. */
    fun previewMove(fromIndex: Int, toIndex: Int) {
        _board.value = _board.value.updatingCurrentPage { it.moved(fromIndex, toIndex) }
    }

    /** Persists whatever order a drag gesture has left the board in. */
    fun commitOrder() {
        commit(_board.value)
    }

    fun exportBoard(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { repo.exportTo(uri) }
            _message.value = if (ok) "Exported backup" else "Export failed"
        }
    }

    fun importBoard(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { repo.importFrom(uri) }
            replaceBoardAfterImport(ok, "Imported backup", "Import failed")
        }
    }

    /** Refreshes [presets] from disk; call before showing a preset picker. */
    fun refreshPresets() {
        viewModelScope.launch {
            _presets.value = withContext(ioDispatcher) { presetRepo.list() }
        }
    }

    /** Refreshes [strayClips] from disk; call before showing the stray-clip cleanup dialog. */
    fun refreshStrayClips() {
        viewModelScope.launch {
            _strayClips.value = withContext(ioDispatcher) {
                val keep = allTiles(_board.value).mapNotNull { it.fileName }.toSet() + presetRepo.allReferencedFileNames()
                repo.strayFiles(keep).map { StrayClip(it.name, it.length()) }
            }
        }
    }

    /** Zips [strayClips] to [uri] and, only if that succeeds, deletes them. */
    fun exportAndDeleteStrayClips(uri: Uri) {
        val fileNames = _strayClips.value.map { it.fileName }
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { repo.exportFiles(fileNames, uri) }
            if (ok) {
                withContext(ioDispatcher) { repo.deleteFiles(fileNames) }
                _strayClips.value = emptyList()
                _message.value = "Exported and deleted ${fileNames.size} unused clip(s)"
            } else {
                _message.value = "Export failed"
            }
        }
    }

    /** Deletes [strayClips] without exporting them first. */
    fun deleteStrayClips() {
        val fileNames = _strayClips.value.map { it.fileName }
        viewModelScope.launch {
            withContext(ioDispatcher) { repo.deleteFiles(fileNames) }
            _strayClips.value = emptyList()
            _message.value = "Deleted ${fileNames.size} unused clip(s)"
        }
    }

    /** Refreshes [recentPresets] from disk; call before showing the title bar's quick-switch dropdown. */
    fun refreshRecentPresets() {
        viewModelScope.launch {
            _recentPresets.value = withContext(ioDispatcher) { recentPresetsRepo.recent().mapNotNull { it.toItem() } }
        }
    }

    /**
     * Snapshots the current board as a brand-new saved preset and renames the
     * live board to match — same-device version history, not a portable
     * backup (see [PresetRepository]). [exportBoard] is still what carries a
     * board's audio off-device.
     */
    fun saveAsPreset(name: String) {
        val renamed = _board.value.copy(name = name.ifBlank { _board.value.name })
        viewModelScope.launch {
            val id = withContext(ioDispatcher) { presetRepo.save(renamed) }
            commit(renamed)
            refreshPresets()
            recordRecentlyUsed(RecentPresetEntry(kind = "saved", id = id, label = renamed.name, usedAt = System.currentTimeMillis()))
            _message.value = "Saved preset \"${renamed.name}\""
        }
    }

    /** Replaces the live board with [ref]'s content. Caller is responsible for confirming this is wanted first. */
    fun applyPreset(ref: PresetRef) {
        viewModelScope.launch {
            when (ref) {
                is PresetRef.Saved -> {
                    val loaded = withContext(ioDispatcher) { presetRepo.load(ref.id) }
                    if (loaded == null) {
                        _message.value = "Couldn't load preset"
                        return@launch
                    }
                    player.clear()
                    commit(loaded)
                    withContext(ioDispatcher) { loadSounds(loaded) }
                    recordRecentlyUsed(RecentPresetEntry(kind = "saved", id = ref.id, label = loaded.name, usedAt = System.currentTimeMillis()))
                    _message.value = "Loaded \"${loaded.name}\""
                }
                is PresetRef.Factory -> {
                    val ok = withContext(ioDispatcher) { repo.importFromAsset(ref.assetName) }
                    if (ok) {
                        recordRecentlyUsed(RecentPresetEntry(kind = "factory", assetName = ref.assetName, label = ref.label, usedAt = System.currentTimeMillis()))
                    }
                    replaceBoardAfterImport(ok, "Loaded \"${ref.label}\"", "Couldn't load preset")
                }
            }
        }
    }

    private suspend fun recordRecentlyUsed(entry: RecentPresetEntry) {
        _recentPresets.value = withContext(ioDispatcher) {
            recentPresetsRepo.recordUsed(entry)
            recentPresetsRepo.recent()
        }.mapNotNull { it.toItem() }
    }

    private fun RecentPresetEntry.toItem(): RecentPresetItem? {
        val ref = when (kind) {
            "saved" -> id?.let { PresetRef.Saved(it) }
            "factory" -> assetName?.let { PresetRef.Factory(it, label) }
            else -> null
        } ?: return null
        return RecentPresetItem(ref, label, usedAt)
    }

    /** Factory presets bundled with this build — Jeremy's, Sarah's, and the TTS-only board ship in every build; Steve's only where its asset is actually packaged (debug builds). */
    fun factoryPresets(context: Context): List<PresetRef.Factory> = buildList {
        add(PresetRef.Factory(JEREMY_PRESET_ASSET, "Jeremy Draft Care Board"))
        add(PresetRef.Factory(SARAH_PRESET_ASSET, "Sarah (ElevenLabs) Care Board"))
        add(PresetRef.Factory(TTS_PRESET_ASSET, "TTS Care Board"))
        if (runCatching { context.assets.open(STEVE_PRESET_ASSET).close() }.isSuccess) {
            add(PresetRef.Factory(STEVE_PRESET_ASSET, "Steve Draft Care Board"))
        }
    }

    private suspend fun replaceBoardAfterImport(imported: Boolean, successMessage: String, failureMessage: String) {
        if (imported) {
            player.clear()
            val loaded = withContext(ioDispatcher) { repo.load() }
            _board.value = loaded
            withContext(ioDispatcher) { loadSounds(loaded) }
            _message.value = successMessage
        } else {
            _message.value = failureMessage
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun loadSounds(board: Board) {
        allTiles(board).mapNotNull { it.fileName }.forEach { name ->
            player.load(name, repo.soundFile(name))
        }
    }

    private fun updateTiles(transform: (List<Tile>) -> List<Tile>) {
        commit(_board.value.updatingCurrentPage { it.copy(tiles = transform(it.tiles)) })
    }

    private fun updateHomeRowTiles(transform: (List<Tile>) -> List<Tile>) {
        val homeIndex = _board.value.homePageIndex ?: return
        commit(_board.value.updatingPage(homeIndex) { it.copy(tiles = transform(it.tiles)) })
    }

    /** Every tile a sound file can be referenced from: every page (the home row is just its first row). */
    private fun allTiles(board: Board): List<Tile> = board.pages.flatMap { it.tiles }

    /** Single write path: update state, drop orphaned audio, persist. */
    private fun commit(board: Board) {
        val grown = board.copy(pages = board.pages.map { it.withAutoGrownTrailingRow() })
        val before = allTiles(_board.value).mapNotNull { it.fileName }.toSet()
        val after = allTiles(grown).mapNotNull { it.fileName }.toSet()
        _board.value = grown

        (before - after).forEach { player.unload(it) }

        viewModelScope.launch(ioDispatcher) {
            repo.save(grown)
            // A saved preset references sound files by name without copying them (see
            // PresetRepository) — protect those from pruning too, or clearing/replacing a
            // live tile could delete audio a saved preset still points at.
            repo.pruneUnused(after + presetRepo.allReferencedFileNames())
        }
    }

    override fun onCleared() {
        recorder.cancel()
        pendingRecordingFile?.delete()
        player.release()
        speaker.shutdown()
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BoardViewModel(
                BoardRepository(app),
                SoundPlayer(),
                AudioRecorder(app),
                PresetRepository(app),
                TtsSpeaker(app),
                DevicePreferences(app),
                RecentPresetsRepository(app)
            ) as T
        }
    }

    companion object {
        /** Bundled in every build (src/main/assets/); see [BoardRepository.importFromAsset]. */
        private const val JEREMY_PRESET_ASSET = "jeremy-care-board.zip"

        /** Shipped as the default/fallback board for now. */
        private const val FALLBACK_PRESET_ASSET = JEREMY_PRESET_ASSET

        /** Same layout as Jeremy's board, but every tile speaks instead of playing audio — bundled in every build. */
        private const val TTS_PRESET_ASSET = "tts-care-board.zip"

        /** Same layout as Jeremy's board, recorded with ElevenLabs' Sarah voice — bundled in every build. */
        private const val SARAH_PRESET_ASSET = "sarah-care-board.zip"

        /** Debug-only (src/debug/assets/) — only actually available where that asset is packaged. */
        private const val STEVE_PRESET_ASSET = "steve-care-board.zip"
    }
}

/** A preset the "Load preset" picker can apply — either bundled with the app or saved on-device. */
sealed interface PresetRef {
    data class Saved(val id: String) : PresetRef
    data class Factory(val assetName: String, val label: String) : PresetRef
}

/** One entry in the title bar's quick-switch dropdown — see [BoardViewModel.recentPresets]. */
data class RecentPresetItem(val ref: PresetRef, val label: String, val usedAt: Long)

/** One unused sound file found by [BoardViewModel.refreshStrayClips]. */
data class StrayClip(val fileName: String, val sizeBytes: Long)
