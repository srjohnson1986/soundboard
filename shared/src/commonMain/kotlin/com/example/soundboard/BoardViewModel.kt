package com.example.soundboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundboard.audio.MediaVolume
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.audio.SpeechVoice
import com.example.soundboard.audio.UnknownMediaVolume
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.PickedFile
import com.example.soundboard.data.RecentBoardEntry
import com.example.soundboard.data.RecentBoardKind
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SaveTarget
import com.example.soundboard.data.SavedBoard
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.SPANISH
import com.example.soundboard.model.languageName
import com.example.soundboard.translate.Phrasebook
import com.example.soundboard.translate.Translator
import com.example.soundboard.translate.TranslatorStatus
import com.example.soundboard.translate.UnavailableTranslator
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.SpeechSettings
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class BoardViewModel(
    private val boardRepo: BoardRepository,
    private val player: Player,
    private val recorder: Recorder,
    private val savedBoardRepo: SavedBoardRepository,
    private val speaker: Speaker,
    private val devicePrefs: DevicePreferences,
    private val recentBoardsRepo: RecentBoardsRepository,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
    /** Milliseconds since the epoch, for when a board was last used; a parameter so tests can pin it. */
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val mediaVolume: MediaVolume = UnknownMediaVolume,
    private val translator: Translator = UnavailableTranslator("this build has no translator."),
    /**
     * Where the bundled English to Spanish phrasebook comes from (#342): read once, the first time a
     * tile needs Spanish. Each platform passes [Phrasebook.bundledSpanish]; the default knows nothing,
     * so a test (or a build without the file) falls through to [translator].
     */
    private val phrasebookSource: suspend () -> Phrasebook = { Phrasebook.Empty }
) : ViewModel(), BoardSettingsActions {

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    /** One-off status text for the UI to show (e.g. in a Snackbar), then clear. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences]. */
    private val _performanceModeEnabled = MutableStateFlow(devicePrefs.performanceModeEnabled)
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences]. */
    private val _showMode = MutableStateFlow(devicePrefs.showMode)
    val showMode: StateFlow<ShowModeSettings> = _showMode.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences.recordingTrimEndMillis]. */
    private val _recordingTrimEndMillis = MutableStateFlow(devicePrefs.recordingTrimEndMillis)
    val recordingTrimEndMillis: StateFlow<Int> = _recordingTrimEndMillis.asStateFlow()

    /** Device-local, not part of [Board] — see [DevicePreferences.speech]. */
    private val _speech = MutableStateFlow(devicePrefs.speech)
    val speech: StateFlow<SpeechSettings> = _speech.asStateFlow()

    /** The voices Settings can offer. See [Speaker.voices]. */
    val speechVoices: StateFlow<List<SpeechVoice>> get() = speaker.voices

    /** The Spanish voices Settings can offer; null until the device has said, empty if it has none. See [Speaker.spanishVoices]. */
    val spanishVoices: StateFlow<List<SpeechVoice>?> get() = speaker.spanishVoices

    /** Whether the device can translate, for the screen to explain; see [Translator.status]. */
    val translatorStatus: StateFlow<TranslatorStatus> get() = translator.status

    /**
     * How far filling in Spanish has got (#342, #332): [TranslateProgress.done] of [TranslateProgress.total]
     * tiles, or null when it isn't running. The board's banner and Settings' "Translate all tiles"
     * both read it. A [TranslateProgress.total] of 0 means only the translator's language model is
     * being got ready.
     */
    private val _translateProgress = MutableStateFlow<TranslateProgress?>(null)
    val translateProgress: StateFlow<TranslateProgress?> = _translateProgress.asStateFlow()

    /**
     * A note about Spanish that couldn't be filled in automatically, for the board's banner to show
     * until it is dismissed (#342): why some tiles still say "no Spanish", and what to do about it.
     */
    private val _spanishNote = MutableStateFlow<String?>(null)
    val spanishNote: StateFlow<String?> = _spanishNote.asStateFlow()

    /** The bundled phrasebook, read the first time it is needed. */
    private var phrasebook: Phrasebook? = null

    private suspend fun phrasebook(): Phrasebook = phrasebook ?: phrasebookSource().also { phrasebook = it }

    /** The automatic fill that is running, so a newer one (or a switch back to English) can replace it. */
    private var fillJob: Job? = null

    /**
     * Suggests the Spanish for [english] (the tile editor's Suggest, #332), handing the result to
     * [onResult], or null if it couldn't ([translatorStatus] says why). It only fills the editor's
     * Spanish field, for the user to edit; the saved text is what plays. The bundled phrasebook
     * answers first, as it does when Spanish is filled in automatically.
     */
    fun suggestTranslation(english: String, onResult: (String?) -> Unit) {
        val language = _board.value.secondLanguage
        if (language == null || english.isBlank()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val spanish = phrasebook().lookup(english, language) ?: translator.translate(english.trim(), language)
            onResult(spanish?.trim()?.ifBlank { null })
        }
    }

    /**
     * Translates what the Speak... box holds, for its live Spanish preview. Null if translating
     * isn't ready (it never starts a download by itself) or fails.
     */
    suspend fun translateForPreview(text: String): String? {
        val language = _board.value.secondLanguage ?: return null
        if (text.isBlank() || translator.status.value != TranslatorStatus.Ready) return null
        return translator.translate(text.trim(), language)?.trim()?.ifBlank { null }
    }

    override fun prepareTranslator() {
        val language = _board.value.secondLanguage ?: return
        viewModelScope.launch { translator.prepare(language) }
    }

    /**
     * Settings' "Translate all tiles", the manual retry for the automatic fill (#332, #342): fills in
     * the Spanish of every tile without any, from the phrasebook and then the translator, downloading
     * its model if it must, and says how many it did and why it stopped if it did.
     */
    override fun translateAllTiles() {
        if (_board.value.secondLanguage == null || _translateProgress.value != null) return
        startFill(allowDownload = true, manual = true)
    }

    /** "Cancel" on the banner while Spanish is being added (it keeps what was filled in so far). */
    fun cancelSpanishFill() {
        if (fillJob?.isActive != true) return
        fillJob?.cancel()
        val language = _board.value.secondLanguage ?: return
        val left = _board.value.untranslatedTiles(language).size
        if (left > 0) {
            _spanishNote.value = "${tilesStill(left)} no ${languageName(language)} yet. " +
                "Settings > Voice > Translate all tiles adds ${languageName(language)} when you want it."
        }
    }

    /** "OK" on the banner's note. */
    fun dismissSpanishNote() {
        _spanishNote.value = null
    }

    /**
     * Fills in Spanish for the tiles that have none (#342): the bundled phrasebook first (instant,
     * offline), then the translator for whatever it didn't know. Typed Spanish is never touched, and
     * what is filled in is marked [Tile.autoTranslated] so it can be refreshed when the English
     * changes. It runs on switching the board to ES, on opening or restoring a board while ES is on,
     * and from Settings; it replaces an earlier run still going.
     *
     * [allowDownload] says the translator may fetch its language model (the switch to ES and
     * Settings, which a person just asked for), which opening a board doesn't do. [manual] is Settings:
     * it says what it did in the snackbar instead of the banner.
     */
    private fun startFill(allowDownload: Boolean, manual: Boolean = false) {
        val language = _board.value.secondLanguage ?: return
        if (!manual && activeLanguage() == null) return
        fillJob?.cancel()
        _spanishNote.value = null
        fillJob = viewModelScope.launch { runFill(language, allowDownload, manual) }
    }

    private suspend fun runFill(language: String, allowDownload: Boolean, manual: Boolean) {
        val targets = _board.value.untranslatedTiles(language)
        if (targets.isEmpty() && manual) {
            _message.value = "Every tile already has ${languageName(language)}."
            return
        }
        // What each tile said when this started: a translation is only kept if it still says so.
        val sources = targets.associate { it.id to it.speechText.trim() }

        val book = phrasebook()
        val fromBook = targets.mapNotNull { tile -> book.lookup(sources.getValue(tile.id), language)?.let { tile.id to it } }.toMap()
        applyAutoTranslations(language, fromBook, sources)
        val remaining = targets.filter { it.id !in fromBook }

        var translated = 0
        var failed = 0
        var stopped: TranslatorStatus? = null
        var finished = false
        try {
            val status = if (remaining.isNotEmpty() || (allowDownload && !manual)) settledTranslatorStatus() else null
            when {
                status == null -> Unit
                status is TranslatorStatus.Unavailable -> stopped = status
                // Not for now: still checking, or its model would have to be downloaded and nobody asked.
                status == TranslatorStatus.Checking || (status != TranslatorStatus.Ready && !allowDownload) -> Unit
                // The phrasebook knew every tile and the model is here: nothing for the translator to do.
                remaining.isEmpty() && status == TranslatorStatus.Ready -> Unit
                else -> {
                    // Nothing to count when it is only the model being got ready.
                    _translateProgress.value = if (remaining.isEmpty()) TranslateProgress(0, 0) else TranslateProgress(fromBook.size, targets.size)
                    if (remaining.isEmpty()) {
                        // Switching to ES gets the translator ready even if the phrasebook knew every
                        // tile, so a tile typed later gets its Spanish too.
                        translator.prepare(language)
                    }
                    val pending = mutableMapOf<String, String>()
                    try {
                        for (tile in remaining) {
                            val spanish = translator.translate(sources.getValue(tile.id), language)?.trim()
                            if (spanish.isNullOrBlank()) {
                                failed++
                                // A model that won't download or a translator that's gone fails every tile the same way.
                                val now = translator.status.value
                                if (now is TranslatorStatus.Failed || now is TranslatorStatus.Unavailable) {
                                    stopped = now
                                    break
                                }
                            } else {
                                pending[tile.id] = spanish
                                translated++
                            }
                            _translateProgress.value = TranslateProgress(fromBook.size + translated + failed, targets.size)
                            // In batches, so a long fill isn't one save per tile and isn't lost if the app is closed.
                            if (pending.size >= FILL_BATCH_SIZE) {
                                applyAutoTranslations(language, pending, sources)
                                pending.clear()
                            }
                        }
                    } finally {
                        // Also when cancelled: what was translated is kept.
                        applyAutoTranslations(language, pending, sources)
                    }
                }
            }
            finished = true
        } finally {
            _translateProgress.value = null
        }
        if (!finished) return

        val filled = fromBook.size + translated
        val left = _board.value.untranslatedTiles(language).size
        if (manual) {
            reportTranslateAll(language, filled, failed, targets.size - filled - failed, stopped)
        } else if (left > 0) {
            noteLeftovers(language, left, stopped)
        }
    }

    /** What Settings' "Translate all tiles" says when it's done. */
    private fun reportTranslateAll(language: String, filled: Int, failed: Int, skipped: Int, stopped: TranslatorStatus?) {
        _message.value = buildString {
            append(
                when (filled) {
                    0 -> "No tiles were translated."
                    1 -> "Added ${languageName(language)} to 1 tile."
                    else -> "Added ${languageName(language)} to $filled tiles."
                }
            )
            if (failed > 0) append(" $failed couldn't be translated.")
            if (skipped > 0) append(" Stopped with $skipped left.")
            (stopped as? TranslatorStatus.Failed)?.let { append(" ${it.message}") }
            (stopped as? TranslatorStatus.Unavailable)?.let { append(" ${it.reason}") }
        }
    }

    /**
     * Says on the banner why [left] tiles still say "no Spanish" after an automatic fill, when the
     * translator was the reason: unavailable here (said once, since nothing about it will change) or
     * failed (said each time, since trying again may work).
     */
    private fun noteLeftovers(language: String, left: Int, stopped: TranslatorStatus?) {
        val name = languageName(language)
        _spanishNote.value = when (stopped) {
            is TranslatorStatus.Unavailable -> {
                if (devicePrefs.translatorUnavailableNoteSeen) return
                devicePrefs.translatorUnavailableNoteSeen = true
                "${tilesStill(left)} no $name yet. Translating isn't available here: ${stopped.reason.trimEnd('.')}. " +
                    "To add $name to a tile, open its editor and type it in the $name field."
            }
            is TranslatorStatus.Failed ->
                "Couldn't add $name to ${tilesCount(left)}. ${stopped.message} " +
                    "Settings > Voice > Translate all tiles tries again."
            else -> return
        }
    }

    private fun tilesCount(count: Int) = if (count == 1) "1 tile" else "$count tiles"

    private fun tilesStill(count: Int) = if (count == 1) "1 tile has" else "$count tiles have"

    /** The translator's status once it has said what it can do, giving it a few seconds to finish checking. */
    private suspend fun settledTranslatorStatus(): TranslatorStatus =
        withTimeoutOrNull(TRANSLATOR_CHECK_TIMEOUT_MILLIS) { translator.status.first { it != TranslatorStatus.Checking } }
            ?: translator.status.value

    /**
     * Puts [translations] (tile id to Spanish) on their tiles in one edit, marked as automatic, where
     * the tile still has no Spanish and still says what it said when [sources] was taken: typed
     * Spanish is never replaced, and a tile whose English changed meanwhile is left for the next fill.
     */
    private fun applyAutoTranslations(language: String, translations: Map<String, String>, sources: Map<String, String>) {
        if (translations.isEmpty()) return
        var changed = false
        val board = _board.value
        val next = board.copy(
            pages = board.pages.map { page ->
                page.copy(
                    tiles = page.tiles.map { tile ->
                        val spanish = translations[tile.id]
                        if (spanish != null && tile.translationFor(language) == null && tile.speechText.trim() == sources[tile.id]) {
                            changed = true
                            tile.withAutoTranslation(language, spanish)
                        } else {
                            tile
                        }
                    }
                )
            }
        )
        if (changed) commit(next)
    }

    /** Device-local, not part of [Board] — see [DevicePreferences.secondLanguageActive]. */
    private val _secondLanguageOn = MutableStateFlow(devicePrefs.secondLanguageActive)

    /** Whether the EN / ES switch is on ES. It only has an effect while the board has a second language ([activeLanguage]). */
    val secondLanguageOn: StateFlow<Boolean> = _secondLanguageOn.asStateFlow()

    /** Turns the board's second language on (the switch on ES) or off (EN), on this device. Allowed while locked. */
    fun setSecondLanguageOn(on: Boolean) {
        devicePrefs.secondLanguageActive = on
        _secondLanguageOn.value = on
        if (on) {
            // Nothing for the person to do (#342): every tile without Spanish gets it now.
            startFill(allowDownload = true)
        } else {
            fillJob?.cancel()
            _spanishNote.value = null
        }
    }

    /** The language the board is speaking right now besides English: the second language if the switch is on ES, else null. */
    fun activeLanguage(): String? = _board.value.secondLanguage.takeIf { _secondLanguageOn.value }

    /** True when the board is to speak [language] but the device is known to have no voice for it. */
    private fun voiceMissingFor(language: String): Boolean =
        language == SPANISH && speaker.spanishVoices.value?.isEmpty() == true

    init {
        speaker.configure(_speech.value)
    }

    /**
     * Whether to remind about a backup (#249): the board has had changes that aren't in one
     * for [BACKUP_REMINDER_AFTER], and "Later" isn't holding the reminder off.
     */
    private val _backupReminderDue = MutableStateFlow(false)
    val backupReminderDue: StateFlow<Boolean> = _backupReminderDue.asStateFlow()

    init {
        refreshBackupReminder()
    }

    /** Whether the caregiver lock is on for this device (#251). See [DevicePreferences.editingLockEnabled]. */
    private val _editingLockEnabled = MutableStateFlow(devicePrefs.editingLockEnabled)
    val editingLockEnabled: StateFlow<Boolean> = _editingLockEnabled.asStateFlow()

    /**
     * Whether editing is locked away right now: the lock is on and nobody has held Unlock since
     * the app started or the board last locked itself again ([relockEditing]).
     */
    private val _editingLocked = MutableStateFlow(devicePrefs.editingLockEnabled)
    val editingLocked: StateFlow<Boolean> = _editingLocked.asStateFlow()

    /** Turns the caregiver lock on, which locks at once, or off. */
    fun setEditingLockEnabled(enabled: Boolean) {
        devicePrefs.editingLockEnabled = enabled
        _editingLockEnabled.value = enabled
        _editingLocked.value = enabled
    }

    /** Held Unlock: editing is available until [relockEditing] or the app restarts. */
    fun unlockEditing() {
        _editingLocked.value = false
    }

    /** Locks editing away again, if the lock is on. */
    fun relockEditing() {
        _editingLocked.value = _editingLockEnabled.value
    }

    /** Whether speech works on this device; null while it's starting. See [Speaker.available]. */
    val speechAvailable: StateFlow<Boolean?> get() = speaker.available

    /** Whether the media volume is off, which silences every tile. See [MediaVolume]. */
    val mediaMuted: StateFlow<Boolean> get() = mediaVolume.muted

    /** The words Show mode has on screen right now; null when the text screen is closed. */
    private val _shownText = MutableStateFlow<String?>(null)
    val shownText: StateFlow<String?> = _shownText.asStateFlow()

    /** The English words under the Spanish ones on the Show mode screen (#331); empty when there is nothing to put under them. */
    private val _shownSubtext = MutableStateFlow("")
    val shownSubtext: StateFlow<String> = _shownSubtext.asStateFlow()

    /** The tile whose picture or emoji Show mode has on screen with [shownText] (which is empty if it has no words); null if it has none. */
    private val _shownIcon = MutableStateFlow<Tile?>(null)
    val shownIcon: StateFlow<Tile?> = _shownIcon.asStateFlow()

    private val _savedBoards = MutableStateFlow<List<SavedBoard>>(emptyList())
    val savedBoards: StateFlow<List<SavedBoard>> = _savedBoards.asStateFlow()

    /** Sound files not referenced by any tile on the live board or any saved board — see [refreshStrayClips]. */
    private val _strayClips = MutableStateFlow<List<StrayClip>>(emptyList())
    val strayClips: StateFlow<List<StrayClip>> = _strayClips.asStateFlow()

    /** Boards actually opened/saved recently, newest first — see [RecentBoardsRepository]. */
    private val _recentBoards = MutableStateFlow<List<RecentBoardItem>>(emptyList())
    val recentBoards: StateFlow<List<RecentBoardItem>> = _recentBoards.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Where the active recording is writing to; only meaningful while [isRecording] is true. */
    private var pendingRecordingPath: String? = null

    fun play(tile: Tile) {
        if (!tile.isPlayable(_board.value.speakUnrecordedTilesEnabled)) return
        mediaVolume.refresh()
        // In Spanish mode a tile with Spanish text speaks it, even over an English recording
        // (#331). With no Spanish voice it plays as in English, and the board says so on screen
        // (SilentBoardWarning); the Spanish is never spoken in an English voice.
        val language = activeLanguage()
        val translation = tile.translationFor(language)
        if (language != null && translation != null && !voiceMissingFor(language)) {
            speaker.speak(translation, language)
            return
        }
        val name = tile.fileName
        if (name != null) player.play(name, tile.volume) else speaker.speak(tile.speechText)
    }

    /**
     * A tile used for real, by a tap or a long-press preview on the board: puts its words on
     * the Show mode screen when that's on, and plays it unless Show mode mutes sounds. The tile
     * editor's own Play button calls [play] directly, so it never opens the text screen.
     */
    fun activate(tile: Tile) {
        // A board left open for days gets its reminder without waiting for an edit.
        refreshBackupReminder()
        if (!tile.isPlayable(_board.value.speakUnrecordedTilesEnabled)) return
        val showMode = _showMode.value
        // In Spanish mode the Spanish is the big text and the English sits small underneath (#331).
        val english = tile.shownWords
        val translation = tile.translationFor(activeLanguage())?.trim()
        val text = translation ?: english
        if (showMode.enabled && (text.isNotEmpty() || tile.hasIcon)) {
            _shownText.value = text
            _shownSubtext.value = if (translation != null && english != translation) english else ""
            _shownIcon.value = tile.takeIf { it.hasIcon }
            if (showMode.muteSounds) return
        }
        play(tile)
    }

    /** Closes the Show mode text screen. Whatever it was playing carries on to the end. */
    fun dismissShownText() {
        _shownText.value = null
        _shownSubtext.value = ""
        _shownIcon.value = null
    }

    /** Speaks arbitrary text not tied to any tile — for a one-off phrase no pad covers. */
    fun speakAdHoc(text: String) {
        // In Spanish mode what is typed is Spanish (#331); translating it is up to whoever types.
        val language = activeLanguage()
        if (language != null) speakIn(text, language) else speaker.speak(text)
    }

    /** Speaks [text] in the board's second language whatever the switch says, to hear a tile's Spanish while editing it. */
    fun speakSecondLanguage(text: String) {
        val language = _board.value.secondLanguage ?: return
        speakIn(text, language)
    }

    private fun speakIn(text: String, language: String) {
        if (voiceMissingFor(language)) {
            _message.value = NO_SPANISH_VOICE_MESSAGE
        } else {
            speaker.speak(text, language)
        }
    }

    // The tile editors below find their tile by id on whichever page holds it (see
    // Board.updatingTile), so the same call edits a tile on the current page or a
    // home-row tile tapped from the sticky row on another page.

    fun setLabel(tileId: String, label: String) = updateTile(tileId) { it.copy(label = label) }

    /** Sets the longer text spoken instead of the label; a blank value clears it back to null (falls back to the label). */
    fun setTtsScript(tileId: String, script: String) = updateTile(tileId) { it.copy(ttsScript = script.ifBlank { null }) }

    /** Per-tile opacity override; null inherits the page's, then the board's. */
    fun setOpacity(tileId: String, opacity: Float?) = updateTile(tileId) { it.copy(opacity = opacity?.coerceIn(0f, 1f)) }

    /** Per-tile border override; null inherits the page's, then the board's. */
    fun setBorder(tileId: String, border: TileBorder?) = updateTile(tileId) { it.copy(border = border) }

    fun setSpeakWhenNoSound(tileId: String, value: Boolean) = updateTile(tileId) { it.copy(speakWhenNoSound = value) }

    // The tile editor works on a draft of the tile: nothing reaches the board until Save
    // ([saveTile]), and Cancel ([discardTileEdits]) throws the draft away (#207). A sound
    // picked or recorded while editing is a real file from the start, so the editor can
    // play it; until the edit ends it's "pending": kept safe from pruning, and deleted
    // again if the edit is cancelled.

    /** Sound files added during the current tile edit, not yet saved onto any tile. */
    private val pendingSounds = mutableSetOf<String>()

    /** Tile pictures added during the current tile edit, not yet saved onto any tile; deleted again if the edit is cancelled. */
    private val pendingIcons = mutableSetOf<String>()

    /** Bumped whenever a tile edit ends, so a pick or recording that finishes after it knows it's too late. */
    private var editSession = 0

    /** The edit the active recording was started in; its clip belongs to that edit or to none. */
    private var recordingSession = 0

    /**
     * Copies a picked sound into app storage and loads it for the editor to play, then hands
     * its file name to [onImported] for the draft. Nothing changes on the board until [saveTile].
     */
    fun importSound(file: PickedFile, onImported: (fileName: String) -> Unit) {
        val session = editSession
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { boardRepo.importSound(file) } ?: return@launch
            addPendingSound(name, session, onImported)
        }
    }

    /**
     * Copies a picked picture into app storage and hands its file name to [onImported] for the
     * draft. Like a sound, it's pending until [saveTile] and deleted if the edit is cancelled.
     */
    fun importTileImage(file: PickedFile, onImported: (fileName: String) -> Unit) {
        val session = editSession
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { boardRepo.importIcon(file) } ?: return@launch
            if (session != editSession) {
                withContext(ioDispatcher) { boardRepo.deleteIcons(listOf(name)) }
            } else {
                pendingIcons += name
                onImported(name)
            }
        }
    }

    /** The picture behind [Tile.imageFileName], for the UI to decode and draw; null if it's gone. */
    suspend fun tileImage(name: String): ByteArray? = withContext(ioDispatcher) { boardRepo.readIcon(name) }

    /**
     * Saves the tile editor's draft onto tile [tileId]: every field at once, as one board edit.
     * Pending sounds the draft didn't keep are unloaded here and pruned with the commit.
     */
    fun saveTile(tileId: String, edited: Tile) {
        val language = _board.value.secondLanguage
        var refillFor: String? = null
        updateTile(tileId) { current ->
            val translations = edited.translations.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
            val saved = edited.copy(
                id = current.id,
                label = edited.label.trim(),
                emoji = edited.emoji?.trim()?.ifBlank { null },
                translations = translations,
                // Only text that is there can be automatic; typing in the editor already took a language out.
                autoTranslated = edited.autoTranslated.filter { it in translations }.toSet(),
                ttsScript = edited.ttsScript?.trim()?.ifBlank { null },
                volume = edited.volume.coerceIn(0f, 1f),
                opacity = edited.opacity?.coerceIn(0f, 1f)
            )
            val english = saved.speechText.trim()
            if (language == null || english == current.speechText.trim()) return@updateTile saved
            // The words changed (#342). Automatic Spanish no longer says what they do, so it goes and is
            // refreshed below; Spanish a person typed is never touched.
            val stale = saved.isAutoTranslated(language)
            val cleared = if (stale) saved.copy(translations = saved.translations - language, autoTranslated = saved.autoTranslated - language) else saved
            if (english.isNotEmpty() && cleared.translationFor(language) == null) refillFor = english
            cleared
        }
        endTileEdit(keep = setOfNotNull(edited.fileName), keepIcons = setOfNotNull(edited.imageFileName))
        refillFor?.let { english -> if (language != null) viewModelScope.launch { fillSavedTile(tileId, language, english) } }
    }

    /**
     * Gives a tile just saved with new words its Spanish (#342): from the phrasebook, else from the
     * translator if it is already ready. A save never starts a model download.
     */
    private suspend fun fillSavedTile(tileId: String, language: String, english: String) {
        val spanish = phrasebook().lookup(english, language)
            ?: if (translator.status.value == TranslatorStatus.Ready) translator.translate(english, language) else null
        spanish?.trim()?.ifBlank { null }?.let { applyAutoTranslations(language, mapOf(tileId to it), mapOf(tileId to english)) }
    }

    /**
     * Looks up the tile named [label] on the built-in board this board came from (see
     * [originalSourceFor]) and hands [onResult] its original sound, for the tile editor's draft
     * (#208): its clip, copied into app storage as a pending sound, and its volume and speech
     * settings. Like any other change in the editor, it only reaches the tile on [saveTile].
     */
    fun restoreOriginalSound(label: String, onResult: (OriginalSound) -> Unit) {
        val session = editSession
        val source = originalSourceFor(_board.value)
        if (source == null || label.isBlank()) {
            onResult(OriginalSound.NotFound(source?.label))
            return
        }
        viewModelScope.launch {
            val original = withContext(ioDispatcher) { boardRepo.originalTile(source.assetName, label) }
            val sound = original?.sound
            when {
                original == null -> onResult(OriginalSound.NotFound(source.label))
                sound == null -> onResult(OriginalSound.Restored(original.tile, source.label))
                else -> {
                    val extension = original.tile.fileName.orEmpty().substringAfterLast('.', "")
                    val name = withContext(ioDispatcher) { boardRepo.importSoundBytes(sound, extension) }
                    if (name == null) {
                        onResult(OriginalSound.NotFound(source.label))
                    } else {
                        addPendingSound(name, session) { onResult(OriginalSound.Restored(original.tile.copy(fileName = it), source.label)) }
                    }
                }
            }
        }
    }

    /**
     * The built-in board [board] came from: the one it was opened from, else (for a board from
     * before that was recorded) the built-in board with its name, else the one a fresh install opens.
     */
    private fun originalSourceFor(board: Board): BoardRef.BuiltIn? {
        val builtIns = builtInBoards()
        return builtIns.firstOrNull { it.assetName == board.builtInSource }
            ?: builtIns.firstOrNull { it.label == board.name }
            ?: FALLBACK_BOARD_ASSETS.firstNotNullOfOrNull { asset -> builtIns.firstOrNull { it.assetName == asset } }
    }

    /** Throws the tile editor's draft away: stops any recording and deletes the sounds added while editing. */
    fun discardTileEdits() {
        cancelRecording()
        endTileEdit(keep = emptySet(), keepIcons = emptySet())
    }

    private fun endTileEdit(keep: Set<String>, keepIcons: Set<String>) {
        editSession++
        val discarded = pendingSounds - keep
        pendingSounds.clear()
        discarded.forEach { player.unload(it) }
        // Anything the board doesn't reference is deleted here; a saved sound is on the board by now.
        val unused = discarded - _board.value.soundFileNames
        if (unused.isNotEmpty()) viewModelScope.launch(ioDispatcher) { boardRepo.deleteFiles(unused) }
        val unusedIcons = (pendingIcons - keepIcons) - _board.value.iconFileNames
        pendingIcons.clear()
        if (unusedIcons.isNotEmpty()) viewModelScope.launch(ioDispatcher) { boardRepo.deleteIcons(unusedIcons) }
    }

    /** Records [name] as pending for edit [session], or deletes it if that edit has already ended. */
    private suspend fun addPendingSound(name: String, session: Int, onAdded: (String) -> Unit) {
        if (session != editSession) {
            withContext(ioDispatcher) { boardRepo.deleteFiles(listOf(name)) }
            return
        }
        pendingSounds += name
        withContext(ioDispatcher) { player.load(name, boardRepo.soundPath(name)) }
        onAdded(name)
    }

    fun clearTile(tileId: String) = updateTile(tileId) {
        it.copy(label = "", fileName = null, speakWhenNoSound = false, emoji = null, imageFileName = null, showLabel = true, translations = emptyMap(), autoTranslated = emptySet())
    }

    /** Detaches a tile's sound without touching its label or [Tile.speakWhenNoSound]. */
    fun removeSound(tileId: String) = updateTile(tileId) { it.copy(fileName = null) }

    /** Starts recording into a fresh file; call [stopRecording] or [cancelRecording] to end it. */
    fun startRecording() {
        if (_isRecording.value) return
        recordingSession = editSession
        viewModelScope.launch {
            val path = boardRepo.newRecordingPath(recorder.fileExtension)
            val started = withContext(ioDispatcher) { recorder.start(path) }
            if (started) {
                pendingRecordingPath = path
                _isRecording.value = true
            } else {
                _message.value = "Couldn't start recording"
            }
        }
    }

    /**
     * Stops the active recording and hands its file name to [onRecorded] for the tile editor's
     * draft; like [importSound], it only reaches a tile on [saveTile].
     */
    fun stopRecording(onRecorded: (fileName: String) -> Unit) {
        val session = recordingSession
        viewModelScope.launch {
            val name = finishRecording() ?: return@launch
            addPendingSound(name, session, onRecorded)
        }
    }

    /** Abandons the active recording without assigning it to any tile. */
    fun cancelRecording() {
        if (!_isRecording.value) return
        viewModelScope.launch {
            withContext(ioDispatcher) { recorder.cancel() }
            _isRecording.value = false
            val path = pendingRecordingPath
            pendingRecordingPath = null
            if (path != null) withContext(ioDispatcher) { boardRepo.deleteRecording(path) }
        }
    }

    /** Stops the recorder and returns the clip's file name, or null on failure. */
    private suspend fun finishRecording(): String? {
        if (!_isRecording.value) return null
        val trim = _recordingTrimEndMillis.value
        val ok = withContext(ioDispatcher) { recorder.stop(trimEndMillis = trim) }
        _isRecording.value = false
        val path = pendingRecordingPath
        pendingRecordingPath = null
        if (!ok || path == null) {
            if (path != null) withContext(ioDispatcher) { boardRepo.deleteRecording(path) }
            _message.value = "Recording failed"
            return null
        }
        return path.substringAfterLast('/')
    }

    /** Whether the home page's first row shows fixed above every other page. */
    override fun setStickyHomeRowEnabled(value: Boolean) {
        commit(_board.value.copy(stickyHomeRowEnabled = value))
    }

    /** Global tile opacity; overridden per-page by [setPageOpacity] and per-tile by [setOpacity]. */
    override fun setBoardTileOpacity(value: Float) {
        commit(_board.value.copy(tileOpacity = value.coerceIn(0f, 1f)))
    }

    /** Global tile border; overridden per-page by [setPageBorder] and per-tile by [setBorder]. */
    override fun setBoardTileBorder(value: TileBorder) {
        commit(_board.value.copy(tileBorder = value))
    }

    /** Disables tile shadows to help scrolling stay smooth on slower devices. */
    override fun setPerformanceModeEnabled(value: Boolean) {
        devicePrefs.performanceModeEnabled = value
        _performanceModeEnabled.value = value
    }

    override fun setShowModeEnabled(value: Boolean) = updateShowMode { it.copy(enabled = value) }

    /** 0 turns the timer off; ignored if tap to close is off too (see [ShowModeSettings.withTimerSeconds]). */
    override fun setShowModeTimerSeconds(value: Int) = updateShowMode { it.withTimerSeconds(value) }

    /** Ignored when turning it off would leave no timer either (see [ShowModeSettings.withTapToClose]). */
    override fun setShowModeTapToClose(value: Boolean) = updateShowMode { it.withTapToClose(value) }

    override fun setShowModeMuteSounds(value: Boolean) = updateShowMode { it.copy(muteSounds = value) }

    override fun setShowModeFlipped(value: Boolean) = updateShowMode { it.copy(flipped = value) }

    /** How much of the end of each new recording to leave off; see [DevicePreferences.recordingTrimEndMillis]. */
    override fun setRecordingTrimEndMillis(value: Int) {
        devicePrefs.recordingTrimEndMillis = value
        _recordingTrimEndMillis.value = devicePrefs.recordingTrimEndMillis
    }

    override fun setSpeechVoice(voiceId: String?) = updateSpeech { it.copy(voiceId = voiceId) }

    override fun setSpeechRatePercent(value: Int) = updateSpeech { it.copy(ratePercent = value) }

    override fun setSpeechPitchPercent(value: Int) = updateSpeech { it.copy(pitchPercent = value) }

    override fun previewSpeech() = speaker.speak(SPEECH_PREVIEW)

    override fun setSpanishVoice(voiceId: String?) = updateSpeech { it.copy(spanishVoiceId = voiceId) }

    override fun previewSpanishSpeech() = speakIn(SPANISH_SPEECH_PREVIEW, SPANISH)

    /** Turns the second language on for the board, or off, which hides the switch and the Spanish fields. */
    override fun setSecondLanguageEnabled(value: Boolean) {
        commit(_board.value.copy(secondLanguage = if (value) SPANISH else null))
        if (value) startFill(allowDownload = true) else fillJob?.cancel()
    }

    private fun updateSpeech(transform: (SpeechSettings) -> SpeechSettings) {
        devicePrefs.speech = transform(_speech.value)
        _speech.value = devicePrefs.speech
        speaker.configure(_speech.value)
    }

    private fun updateShowMode(transform: (ShowModeSettings) -> ShowModeSettings) {
        val updated = transform(_showMode.value)
        if (updated == _showMode.value) return
        devicePrefs.showMode = updated
        _showMode.value = updated
        // Turning Show mode off shouldn't leave its text screen stranded on top of the board.
        if (!updated.enabled) dismissShownText()
    }

    override fun setDefaultPageRows(value: Int) {
        commit(_board.value.copy(defaultPageRows = value))
    }

    override fun setDefaultPageColumns(value: Int) {
        commit(_board.value.copy(defaultPageColumns = value))
    }

    fun resize(index: Int, rows: Int, columns: Int) {
        commit(_board.value.updatingPage(index) { it.withGridSize(rows, columns) })
    }

    fun setTileAspectRatio(index: Int, ratio: Float) {
        commit(_board.value.updatingPage(index) { it.copy(tileAspectRatio = ratio) })
    }

    /** Per-page landscape grid; null for either dimension derives it from the portrait grid. */
    fun setLandscapeGrid(index: Int, rows: Int?, columns: Int?) {
        commit(_board.value.updatingPage(index) { it.copy(landscapeRows = rows, landscapeColumns = columns) })
    }

    override fun setLandscapeLayout(layout: LandscapeLayout) {
        commit(_board.value.copy(landscapeLayout = layout))
    }

    /** Tile label font/size/weight/case; the size range is kept in order (min never above max). */
    override fun setLabelStyle(style: LabelStyle) {
        val min = style.minSizeSp.coerceIn(LabelStyle.SIZE_RANGE_SP)
        val max = style.maxSizeSp.coerceIn(LabelStyle.SIZE_RANGE_SP).coerceAtLeast(min)
        commit(_board.value.copy(labelStyle = style.copy(minSizeSp = min, maxSizeSp = max)))
    }

    override fun setRowHeight(rowHeight: RowHeight) {
        commit(_board.value.copy(rowHeight = rowHeight))
    }

    /** Per-page row height override; null inherits the board's global setting. */
    fun setPageRowHeight(index: Int, rowHeight: RowHeight?) {
        commit(_board.value.updatingPage(index) { it.copy(rowHeight = rowHeight) })
    }

    fun setPageColor(index: Int, colorArgb: Int?) {
        commit(_board.value.updatingPage(index) { it.copy(color = colorArgb) })
    }

    /** Per-page opacity override; null inherits the board's global setting. */
    fun setPageOpacity(index: Int, opacity: Float?) {
        commit(_board.value.updatingPage(index) { it.copy(opacity = opacity?.coerceIn(0f, 1f)) })
    }

    /** Per-page border override; null inherits the board's global setting. */
    fun setPageBorder(index: Int, border: TileBorder?) {
        commit(_board.value.updatingPage(index) { it.copy(border = border) })
    }

    /** Whether a fresh launch jumps to the home page instead of resuming the last-viewed one. */
    override fun setOpenOnHomePage(value: Boolean) {
        commit(_board.value.copy(openOnHomePage = value))
    }

    /** Minutes of inactivity before auto-return to home; 0 disables it. */
    override fun setIdleTimeoutMinutes(value: Int) {
        commit(_board.value.copy(idleTimeoutMinutes = value))
    }

    /** How long a page-tab press must be held before it counts as a long-press, in milliseconds. */
    override fun setLongPressDurationMillis(value: Int) {
        commit(_board.value.copy(longPressDurationMillis = value))
    }

    override fun setThemeMode(mode: ThemeMode) {
        commit(_board.value.copy(themeMode = mode))
    }

    override fun setKeepScreenAwake(value: Boolean) {
        commit(_board.value.copy(keepScreenAwake = value))
    }

    override fun setHapticFeedbackEnabled(value: Boolean) {
        commit(_board.value.copy(hapticFeedbackEnabled = value))
    }

    /** Whether a tile with no sound file speaks its label via TTS on tap, even without its own speak-label switch on. */
    override fun setSpeakUnrecordedTilesEnabled(value: Boolean) {
        commit(_board.value.copy(speakUnrecordedTilesEnabled = value))
    }

    /** Whether blank tiles are hidden (and untappable) outside of edit mode, to avoid a stray tap opening the editor. */
    override fun setHideBlankTilesEnabled(value: Boolean) {
        commit(_board.value.copy(hideBlankTilesEnabled = value))
    }

    /** Solid background color; setting one clears any background image. */
    override fun setBackgroundColor(argb: Int?) = replaceBackground(colorArgb = argb, imageFileName = null)

    /** Background image picked from the gallery; replaces any solid background color. */
    fun setBackgroundImage(file: PickedFile) {
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { boardRepo.importBackgroundImage(file) } ?: return@launch
            replaceBackground(colorArgb = null, imageFileName = name)
        }
    }

    /** Clears the background back to the plain theme surface. */
    override fun clearBackground() = replaceBackground(colorArgb = null, imageFileName = null)

    /**
     * Single write path for the background: at most one of color or image is ever set, and
     * the image file it replaces is deleted — nothing else points at it (backgrounds aren't
     * shared with saved boards the way sounds are).
     */
    private fun replaceBackground(colorArgb: Int?, imageFileName: String?) {
        val previousImage = _board.value.backgroundImageFileName
        commit(_board.value.copy(backgroundColorArgb = colorArgb, backgroundImageFileName = imageFileName))
        if (previousImage != null && previousImage != imageFileName) {
            viewModelScope.launch(ioDispatcher) { boardRepo.deleteBackground(previousImage) }
        }
    }

    /** The image behind [Board.backgroundImageFileName], for the UI to decode and render; null if it's gone. */
    suspend fun backgroundImage(name: String): ByteArray? = withContext(ioDispatcher) { boardRepo.readBackground(name) }

    fun renameBoard(name: String) {
        commit(_board.value.copy(name = name.ifBlank { "New Board" }))
    }

    fun setHomePage(index: Int) {
        commit(_board.value.withHomePage(index))
    }

    fun clearHomePage() {
        commit(_board.value.withoutHomePage())
    }

    fun addPage(name: String) {
        commit(_board.value.withPageAdded(name.ifBlank { "Page ${_board.value.pages.size + 1}" }))
    }

    fun renamePage(index: Int, name: String) {
        commit(_board.value.withPageRenamed(index, name))
    }

    fun deletePage(index: Int) {
        commit(_board.value.withPageRemoved(index))
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        commit(_board.value.withPageMoved(fromIndex, toIndex))
    }

    /** Switches the active page without touching disk — nothing about the board changed. */
    fun switchPage(index: Int) {
        _board.value = _board.value.withCurrentPage(index)
    }

    /** Live-reorders tiles during a drag without touching disk; see [commitOrder]. */
    fun previewMove(fromIndex: Int, toIndex: Int) {
        _board.value = _board.value.updatingCurrentPage { it.withTileMoved(fromIndex, toIndex) }
    }

    /** Persists whatever order a drag gesture has left the board in. */
    fun commitOrder() {
        commit(_board.value)
    }

    fun exportBoard(target: SaveTarget) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { boardRepo.exportTo(target) }
            _message.value = if (ok) "Exported backup" else "Export failed"
            if (ok) markBackedUp()
        }
    }

    fun importBoard(file: PickedFile) {
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { boardRepo.importFrom(file) }
            replaceBoardAfterImport(ok, "Imported backup", "Import failed")
        }
    }

    /** Refreshes [savedBoards] from disk; call before showing the Open board picker. */
    fun refreshSavedBoards() {
        viewModelScope.launch {
            _savedBoards.value = withContext(ioDispatcher) { savedBoardRepo.list() }
        }
    }

    /** Refreshes [strayClips] from disk; call before showing the stray-clip cleanup dialog. */
    fun refreshStrayClips() {
        viewModelScope.launch {
            _strayClips.value = withContext(ioDispatcher) {
                val keep = _board.value.soundFileNames + savedBoardRepo.allReferencedFileNames()
                // In a fixed order, so the numbering of clips nothing is remembered about is the
                // same here and in the export zip.
                val strays = boardRepo.strayFiles(keep).sortedWith(compareBy({ it.lastModifiedMillis }, { it.name }))
                val names = boardRepo.unusedClipNames(strays.map { it.name })
                strays.zip(names) { file, name -> StrayClip(file.name, file.sizeBytes, name.description) }
            }
        }
    }

    /** Zips [strayClips] to [target] and, only if that succeeds, deletes them. */
    fun exportAndDeleteStrayClips(target: SaveTarget) {
        val fileNames = _strayClips.value.map { it.fileName }
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { boardRepo.exportFiles(fileNames, target) }
            if (ok) {
                withContext(ioDispatcher) { boardRepo.deleteFiles(fileNames) }
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
            withContext(ioDispatcher) { boardRepo.deleteFiles(fileNames) }
            _strayClips.value = emptyList()
            _message.value = "Deleted ${fileNames.size} unused clip(s)"
        }
    }

    /** Refreshes [recentBoards] from disk; call before showing the title bar's quick-switch dropdown. */
    fun refreshRecentBoards() {
        viewModelScope.launch {
            _recentBoards.value = withContext(ioDispatcher) { recentBoardsRepo.recent().mapNotNull { it.toItem() } }
        }
    }

    /**
     * Snapshots the current board as a brand-new saved board and renames the
     * live board to match — same-device version history, not a portable
     * backup (see [SavedBoardRepository]). [exportBoard] is still what carries a
     * board's audio off-device.
     */
    fun saveBoardAs(name: String) {
        val renamed = _board.value.copy(name = name.ifBlank { _board.value.name })
        viewModelScope.launch {
            val id = withContext(ioDispatcher) { savedBoardRepo.save(renamed) }
            commit(renamed)
            refreshSavedBoards()
            recordRecentlyUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = id, label = renamed.name, usedAt = now()))
            _message.value = "Saved board \"${renamed.name}\""
        }
    }

    /** Replaces the live board with [ref]'s content. Caller is responsible for confirming this is wanted first. */
    fun openBoard(ref: BoardRef) {
        viewModelScope.launch {
            when (ref) {
                is BoardRef.Saved -> {
                    val loaded = withContext(ioDispatcher) { savedBoardRepo.load(ref.id) }
                    if (loaded == null) {
                        _message.value = "Couldn't open board"
                        return@launch
                    }
                    player.clear()
                    commit(loaded)
                    withContext(ioDispatcher) { loadSounds(loaded) }
                    recordRecentlyUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = ref.id, label = loaded.name, usedAt = now()))
                    _message.value = "Loaded \"${loaded.name}\""
                    startFill(allowDownload = false)
                }
                is BoardRef.BuiltIn -> {
                    val ok = withContext(ioDispatcher) { boardRepo.importFromAsset(ref.assetName) }
                    if (ok) {
                        recordRecentlyUsed(RecentBoardEntry(kind = RecentBoardKind.BUILT_IN, assetName = ref.assetName, label = ref.label, usedAt = now()))
                    }
                    replaceBoardAfterImport(ok, "Loaded \"${ref.label}\"", "Couldn't open board")
                }
            }
        }
    }

    private suspend fun recordRecentlyUsed(entry: RecentBoardEntry) {
        _recentBoards.value = withContext(ioDispatcher) {
            recentBoardsRepo.recordUsed(entry)
            recentBoardsRepo.recent()
        }.mapNotNull { it.toItem() }
    }

    private fun RecentBoardEntry.toItem(): RecentBoardItem? {
        val ref = when (kind) {
            RecentBoardKind.SAVED -> id?.let { BoardRef.Saved(it) }
            RecentBoardKind.BUILT_IN -> assetName?.let { BoardRef.BuiltIn(it, label) }
        } ?: return null
        return RecentBoardItem(ref, label, usedAt)
    }

    /**
     * Built-in boards packaged with this build. The Android app ships Jeremy's, Sarah's and the
     * TTS-only board in every build, and Steve's only in debug builds; the web version ships just
     * the TTS-only board, which has no recorded voices in it.
     */
    fun builtInBoards(): List<BoardRef.BuiltIn> = listOf(
        BoardRef.BuiltIn(JEREMY_BOARD_ASSET, "Jeremy Draft Care Board"),
        BoardRef.BuiltIn(SARAH_BOARD_ASSET, "Sarah (ElevenLabs) Care Board"),
        BoardRef.BuiltIn(TTS_BOARD_ASSET, "TTS Care Board"),
        BoardRef.BuiltIn(STEVE_BOARD_ASSET, "Steve Draft Care Board")
    ).filter { boardRepo.hasAsset(it.assetName) }

    private suspend fun replaceBoardAfterImport(imported: Boolean, successMessage: String, failureMessage: String) {
        if (imported) {
            player.clear()
            val loaded = withContext(ioDispatcher) { boardRepo.load() }
            // Its sounds just came in under new names, so they have no record yet.
            withContext(ioDispatcher) { boardRepo.recordClipLabels(loaded) }
            _board.value = loaded
            withContext(ioDispatcher) { loadSounds(loaded) }
            // Nothing on a board just opened or restored needs backing up yet.
            markBackedUp()
            _message.value = successMessage
            // Opened or restored while ES is on: fill in its Spanish as switching to ES does.
            startFill(allowDownload = false)
        } else {
            _message.value = failureMessage
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun loadSounds(board: Board) {
        board.soundFileNames.forEach { name ->
            player.load(name, boardRepo.soundPath(name))
        }
    }

    private fun updateTile(tileId: String, transform: (Tile) -> Tile) {
        commit(_board.value.updatingTile(tileId, transform))
    }

    /** Single write path: update state, drop orphaned audio, persist. */
    private fun commit(board: Board) {
        val next = board.normalized()
        val removedSounds = _board.value.soundFileNames - next.soundFileNames
        _board.value = next
        if (devicePrefs.unbackedChangesSince == null) devicePrefs.unbackedChangesSince = now()
        refreshBackupReminder()

        removedSounds.forEach { player.unload(it) }

        // Taken now, on the main thread that changes them, not on the I/O thread below.
        val inProgress = pendingSounds.toSet() + listOfNotNull(pendingRecordingPath?.substringAfterLast('/'))
        val iconsInProgress = pendingIcons.toSet()
        viewModelScope.launch(ioDispatcher) {
            // Back-to-back commits (e.g. Grid size's Apply) each launch a save on a pool
            // thread; without the lock they can finish out of order and leave an older
            // board on disk. Each save writes whatever is current, so the last one to run
            // always persists the latest board — and prunes against its files, not a
            // stale commit's that might not know about a sound added since.
            saveMutex.withLock {
                val latest = _board.value
                boardRepo.save(latest)
                // Before pruning, so a sound's last tile is on record even if it goes right away.
                boardRepo.recordClipLabels(latest)
                // A saved board references sound files by name without copying them (see
                // SavedBoardRepository) — protect those from pruning too, or clearing/replacing a
                // live tile could delete audio a saved board still points at. Likewise a sound
                // added in the tile editor but not saved yet, and a recording in progress.
                boardRepo.pruneUnused(latest.soundFileNames + savedBoardRepo.allReferencedFileNames() + inProgress)
                // Same for tile pictures: the live board's, any saved board's, and ones picked in the open editor.
                boardRepo.pruneUnusedIcons(latest.iconFileNames + savedBoardRepo.allReferencedIconNames() + iconsInProgress)
            }
        }
    }

    private val saveMutex = Mutex()

    /** "Later" on the backup reminder: puts it away for [BACKUP_REMINDER_SNOOZE]. */
    fun snoozeBackupReminder() {
        devicePrefs.backupReminderSnoozedUntil = now() + BACKUP_REMINDER_SNOOZE.inWholeMilliseconds
        refreshBackupReminder()
    }

    private fun markBackedUp() {
        devicePrefs.unbackedChangesSince = null
        devicePrefs.backupReminderSnoozedUntil = null
        refreshBackupReminder()
    }

    private fun refreshBackupReminder() {
        val since = devicePrefs.unbackedChangesSince
        val snoozedUntil = devicePrefs.backupReminderSnoozedUntil
        val time = now()
        _backupReminderDue.value = since != null &&
            time - since >= BACKUP_REMINDER_AFTER.inWholeMilliseconds &&
            (snoozedUntil == null || time >= snoozedUntil)
    }

    override fun onCleared() {
        recorder.cancel()
        // viewModelScope is already cancelled here, so the abandoned file is deleted on a
        // scope of its own. Missing it would only leave a stray clip for the next prune.
        pendingRecordingPath?.let { path ->
            CoroutineScope(ioDispatcher + NonCancellable).launch { boardRepo.deleteRecording(path) }
        }
        player.release()
        speaker.shutdown()
        mediaVolume.release()
        super.onCleared()
    }

    // Last in the class, so every property the first fill touches (commit's, say) exists by the time it runs
    // (a test's dispatcher runs this at once, before construction has finished).
    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (!boardRepo.hasSavedBoard()) {
                    FALLBACK_BOARD_ASSETS.firstOrNull(boardRepo::hasAsset)?.let { boardRepo.importFromAsset(it) }
                }
            }
            val loaded = withContext(ioDispatcher) { boardRepo.load() }
            // Only overrides where the pager starts, not what's saved to disk — a plain
            // switchTo(), same as any other in-session page switch, see BoardViewModel.switchPage.
            val initial = if (loaded.openOnHomePage) {
                loaded.homePageIndex?.let { loaded.withCurrentPage(it) } ?: loaded
            } else {
                loaded
            }
            _board.value = initial
            withContext(ioDispatcher) { loadSounds(initial) }
            // A board left on ES: anything still without Spanish gets it.
            startFill(allowDownload = false)
        }
    }

    companion object {
        /** How long changes can go without a backup before the board reminds about one (#249). */
        val BACKUP_REMINDER_AFTER = 7.days

        /** How long "Later" puts the reminder away for. */
        val BACKUP_REMINDER_SNOOZE = 7.days

        /** How many translated tiles an automatic fill saves at once (#342), so it is never one save per tile. */
        const val FILL_BATCH_SIZE = 10

        /** How long a fill waits for the translator to finish checking what the device can do. */
        const val TRANSLATOR_CHECK_TIMEOUT_MILLIS = 12_000L

        /** What Settings' Preview button says in the chosen voice. */
        const val SPEECH_PREVIEW = "Hello. This is how the board sounds."

        /** What Settings' Spanish Preview button says in the Spanish voice. */
        const val SPANISH_SPEECH_PREVIEW = "Hola. As\u00ed suena el tablero."

        /** Shown when something is to be said in Spanish and the device has no Spanish voice. */
        const val NO_SPANISH_VOICE_MESSAGE = "This device has no Spanish voice. Install one in the text-to-speech settings."

        /** Bundled in every build (src/main/assets/); see [BoardRepository.importFromAsset]. */
        private const val JEREMY_BOARD_ASSET = "jeremy-care-board.zip"


        /** Same layout as Jeremy's board, but every tile speaks instead of playing audio — bundled in every build. */
        private const val TTS_BOARD_ASSET = "tts-care-board.zip"

        /** Same layout as Jeremy's board, recorded with ElevenLabs' Sarah voice — bundled in every build. */
        private const val SARAH_BOARD_ASSET = "sarah-care-board.zip"

        /** Debug-only (src/debug/assets/) — only actually available where that asset is packaged. */
        private const val STEVE_BOARD_ASSET = "steve-care-board.zip"

        /** What a fresh install opens: the first of these this build packages (Jeremy's on Android, the TTS board on the web). */
        private val FALLBACK_BOARD_ASSETS = listOf(JEREMY_BOARD_ASSET, TTS_BOARD_ASSET)
    }
}

/** A board the "Open board" picker can open — either built into the app or saved on this device. */
sealed interface BoardRef {
    data class Saved(val id: String) : BoardRef
    data class BuiltIn(val assetName: String, val label: String) : BoardRef
}

/** What [BoardViewModel.restoreOriginalSound] found. */
sealed interface OriginalSound {
    /** The tile as built-in board [boardLabel] has it; its `fileName`, if any, is a pending sound in app storage. */
    class Restored(val tile: Tile, val boardLabel: String) : OriginalSound

    /** No tile by that name plays anything on built-in board [boardLabel] (null: no built-in board to look in). */
    class NotFound(val boardLabel: String?) : OriginalSound
}

/** One entry in the title bar's quick-switch dropdown — see [BoardViewModel.recentBoards]. */
data class RecentBoardItem(val ref: BoardRef, val label: String, val usedAt: Long)

/** How far [BoardViewModel.translateAllTiles] has got: [done] of [total] tiles. */
data class TranslateProgress(val done: Int, val total: Int)

/** One unused sound file found by [BoardViewModel.refreshStrayClips]. */
data class StrayClip(
    val fileName: String,
    val sizeBytes: Long,
    /** What it was: "Yes, Food page" (the last tile it was on), or "Unused clip 1" if that isn't known. */
    val description: String = fileName
)
