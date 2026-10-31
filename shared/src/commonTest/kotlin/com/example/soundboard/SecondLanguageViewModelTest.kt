package com.example.soundboard

import com.example.soundboard.audio.FakeMediaVolume
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.audio.spokenOf
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.FakeBundledBoards
import com.example.soundboard.data.FakeZipCodec
import com.example.soundboard.data.InMemoryFileStore
import com.example.soundboard.data.MapKeyValueStore
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.SpeechSettings
import com.example.soundboard.model.Tile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Speaking Spanish (#331) through [BoardViewModel]: Spanish mode picks a tile's Spanish text, falls
 * back to English for a tile without any, uses the Spanish voice, and never says Spanish in the
 * wrong voice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecondLanguageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val files = InMemoryFileStore()
    private val zip = FakeZipCodec()
    private val prefsStore = MapKeyValueStore()
    private val repo = BoardRepository(files, zip, FakeBundledBoards())
    private val player = FakePlayer()
    private val speaker = FakeSpeaker()
    private val devicePrefs = DevicePreferences(prefsStore)

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun newViewModel() = BoardViewModel(
        repo, player, FakeRecorder(), SavedBoardRepository(files), speaker, devicePrefs, RecentBoardsRepository(files),
        ioDispatcher = dispatcher, now = { 1L }, mediaVolume = FakeMediaVolume()
    )

    private val spanishWater = Tile(id = "w", label = "Water", ttsScript = "A glass of water please", speakWhenNoSound = true, translations = mapOf("es" to "Un vaso de agua, por favor"))
    private val recorded = Tile(id = "r", label = "Hello", fileName = "hello.m4a", translations = mapOf("es" to "Hola"))
    private val untranslated = Tile(id = "u", label = "Thanks", fileName = "thanks.m4a")
    private val untranslatedSpoken = Tile(id = "s", label = "Sorry", speakWhenNoSound = true)

    @Test
    fun `in English a tile with Spanish text still plays in English`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.play(spanishWater)
        vm.play(recorded)

        assertEquals(spokenOf("A glass of water please" to null), speaker.spokenIn)
        assertEquals(listOf("hello.m4a"), player.playedKeys)
    }

    @Test
    fun `in Spanish mode a tile speaks its Spanish in the Spanish voice`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.play(spanishWater)

        assertEquals(spokenOf("Un vaso de agua, por favor" to "es"), speaker.spokenIn)
    }

    @Test
    fun `in Spanish mode the Spanish is spoken even over an English recording`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.play(recorded)

        assertEquals(spokenOf("Hola" to "es"), speaker.spokenIn)
        assertTrue(player.playedKeys.isEmpty())
    }

    @Test
    fun `in Spanish mode a tile with no Spanish plays as normal in English`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.play(untranslated)
        vm.play(untranslatedSpoken)

        assertEquals(listOf("thanks.m4a"), player.playedKeys)
        assertEquals(spokenOf("Sorry" to null), speaker.spokenIn)
    }

    @Test
    fun `a blank Spanish text counts as none`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.play(untranslated.copy(translations = mapOf("es" to "   ")))

        assertEquals(listOf("thanks.m4a"), player.playedKeys)
        assertTrue(speaker.spokenIn.isEmpty())
    }

    @Test
    fun `with no Spanish voice the tile plays in English and Spanish is never spoken`() = runTest(dispatcher) {
        speaker.spanishVoices.value = emptyList()
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.play(recorded)
        vm.play(spanishWater)

        assertEquals(listOf("hello.m4a"), player.playedKeys)
        assertEquals(spokenOf("A glass of water please" to null), speaker.spokenIn)
    }

    @Test
    fun `while the device is still finding its Spanish voices a tap speaks Spanish`() = runTest(dispatcher) {
        speaker.spanishVoices.value = null
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.play(recorded)

        assertEquals(spokenOf("Hola" to "es"), speaker.spokenIn)
    }

    @Test
    fun `turning the second language off on the board brings back English even with the switch on ES`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)
        vm.setSecondLanguageEnabled(false)

        vm.play(recorded)

        assertNull(vm.board.value.secondLanguage)
        assertNull(vm.activeLanguage())
        assertEquals(listOf("hello.m4a"), player.playedKeys)
    }

    @Test
    fun `the switch is saved on this device and not in the board`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertFalse(vm.secondLanguageOn.value)

        vm.setSecondLanguageOn(true)

        assertTrue(devicePrefs.secondLanguageActive)
        assertTrue(newViewModel().secondLanguageOn.value)
        assertEquals("es", newViewModel().activeLanguage())
        assertEquals("es", vm.board.value.secondLanguage)
    }

    // --- Show mode ---

    private fun showModeOn(vm: BoardViewModel) {
        vm.setShowModeEnabled(true)
    }

    @Test
    fun `Show mode in Spanish shows the Spanish with the English underneath`() = runTest(dispatcher) {
        val vm = newViewModel()
        showModeOn(vm)
        vm.setSecondLanguageOn(true)

        vm.activate(spanishWater)

        assertEquals("Un vaso de agua, por favor", vm.shownText.value)
        assertEquals("A glass of water please", vm.shownSubtext.value)
    }

    @Test
    fun `Show mode in Spanish for a tile with no Spanish shows its English with nothing under it`() = runTest(dispatcher) {
        val vm = newViewModel()
        showModeOn(vm)
        vm.setSecondLanguageOn(true)

        vm.activate(untranslated)

        assertEquals("Thanks", vm.shownText.value)
        assertEquals("", vm.shownSubtext.value)
    }

    @Test
    fun `Show mode in English shows English only and closing it clears the line under`() = runTest(dispatcher) {
        val vm = newViewModel()
        showModeOn(vm)

        vm.activate(spanishWater)
        assertEquals("A glass of water please", vm.shownText.value)
        assertEquals("", vm.shownSubtext.value)

        vm.setSecondLanguageOn(true)
        vm.activate(spanishWater)
        vm.dismissShownText()
        assertNull(vm.shownText.value)
        assertEquals("", vm.shownSubtext.value)
    }

    @Test
    fun `Show mode that mutes sounds in Spanish only shows the text`() = runTest(dispatcher) {
        devicePrefs.showMode = ShowModeSettings(enabled = true, muteSounds = true)
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.activate(recorded)

        assertEquals("Hola", vm.shownText.value)
        assertTrue(speaker.spokenIn.isEmpty())
        assertTrue(player.playedKeys.isEmpty())
    }

    // --- The Speak... box ---

    @Test
    fun `the Speak box speaks what is typed in the device language in English`() = runTest(dispatcher) {
        newViewModel().speakAdHoc("Hello there")

        assertEquals(spokenOf("Hello there" to null), speaker.spokenIn)
    }

    @Test
    fun `in Spanish mode the Speak box speaks what is typed in the Spanish voice`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.speakAdHoc("Buenos dias")

        assertEquals(spokenOf("Buenos dias" to "es"), speaker.spokenIn)
    }

    @Test
    fun `in Spanish mode with no Spanish voice the Speak box says so instead of using an English voice`() = runTest(dispatcher) {
        speaker.spanishVoices.value = emptyList()
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.speakAdHoc("Buenos dias")

        assertTrue(speaker.spokenIn.isEmpty())
        assertEquals(BoardViewModel.NO_SPANISH_VOICE_MESSAGE, vm.message.value)
    }

    @Test
    fun `hearing a tile's Spanish while editing works from English mode`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.speakSecondLanguage("Hola")

        assertEquals(spokenOf("Hola" to "es"), speaker.spokenIn)
    }

    // --- Settings ---

    @Test
    fun `the Spanish voice is chosen apart from the English one and reaches the speaker`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSpeechVoice("en-1")

        vm.setSpanishVoice("es-2")

        val expected = SpeechSettings(voiceId = "en-1", spanishVoiceId = "es-2")
        assertEquals(expected, speaker.configured)
        assertEquals(expected, newViewModel().speech.value)
    }

    @Test
    fun `the Spanish preview speaks in Spanish and says so when there is no voice`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.previewSpanishSpeech()
        assertEquals(spokenOf(BoardViewModel.SPANISH_SPEECH_PREVIEW to "es"), speaker.spokenIn)

        speaker.spanishVoices.value = emptyList()
        vm.previewSpanishSpeech()
        assertEquals(1, speaker.spokenIn.size)
        assertEquals(BoardViewModel.NO_SPANISH_VOICE_MESSAGE, vm.message.value)
    }

    // --- The tile editor ---

    private fun boardOf(vararg tiles: Tile) = Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList())))

    @Test
    fun `saving a tile keeps its Spanish trimmed and drops a blank one`() = runTest(dispatcher) {
        repo.save(boardOf(Tile(id = "a"), Tile(id = "b")))
        val vm = newViewModel()

        vm.saveTile("a", Tile(id = "a", label = "Hi", translations = mapOf("es" to "  Hola  ")))
        vm.saveTile("b", Tile(id = "b", label = "Bye", translations = mapOf("es" to "   ")))

        assertEquals(mapOf("es" to "Hola"), vm.board.value.findTile("a")?.translations)
        assertEquals(emptyMap(), vm.board.value.findTile("b")?.translations)
    }

    @Test
    fun `clearing a tile clears its Spanish too`() = runTest(dispatcher) {
        repo.save(boardOf(spanishWater))
        val vm = newViewModel()

        vm.clearTile("w")

        assertEquals(emptyMap(), vm.board.value.findTile("w")?.translations)
    }

    @Test
    fun `a tile's Spanish is saved with the board`() = runTest(dispatcher) {
        repo.save(boardOf(Tile(id = "a")))
        val vm = newViewModel()
        vm.saveTile("a", Tile(id = "a", label = "Hi", translations = mapOf("es" to "Hola")))

        assertEquals(mapOf("es" to "Hola"), repo.load().findTile("a")?.translations)
    }
}
