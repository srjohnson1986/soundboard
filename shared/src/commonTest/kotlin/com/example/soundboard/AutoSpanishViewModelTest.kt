package com.example.soundboard

import com.example.soundboard.audio.FakeMediaVolume
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.audio.FakeTranslator
import com.example.soundboard.audio.GateTranslator
import com.example.soundboard.data.BoardJson
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.FakeBundledBoards
import com.example.soundboard.data.FakeZipCodec
import com.example.soundboard.data.FileStore
import com.example.soundboard.data.InMemoryFileStore
import com.example.soundboard.data.MapKeyValueStore
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.StoredFileInfo
import com.example.soundboard.data.ZipEntryData
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.Tile
import com.example.soundboard.translate.Phrasebook
import com.example.soundboard.translate.Translator
import com.example.soundboard.translate.TranslatorStatus
import com.example.soundboard.translate.UnavailableTranslator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Spanish that fills itself in (#342): switching to ES, saving a tile and opening a board fill in
 * the Spanish of tiles that have none, from the bundled phrasebook and then the translator, and
 * never replace what a person typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSpanishViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val files = CountingFileStore(InMemoryFileStore())
    private val zip = FakeZipCodec()
    private val translator = FakeTranslator()
    private val prefs = DevicePreferences(MapKeyValueStore())
    private val phrasebook = Phrasebook(
        "es",
        mapOf("Water" to "Agua", "Thank you." to "Gracias", "Hug?" to "¿Un abrazo?", "Yes" to "Sí")
    )

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun repo(bundled: Map<String, ByteArray> = emptyMap()) = BoardRepository(files, zip, FakeBundledBoards(bundled))

    private fun newViewModel(
        translator: Translator = this.translator,
        phrasebook: Phrasebook = this.phrasebook,
        bundled: Map<String, ByteArray> = emptyMap()
    ) = BoardViewModel(
        repo(bundled), FakePlayer(), FakeRecorder(), SavedBoardRepository(files), FakeSpeaker(), prefs,
        RecentBoardsRepository(files), ioDispatcher = dispatcher, now = { 1L }, mediaVolume = FakeMediaVolume(),
        translator = translator, phrasebookSource = { phrasebook }
    )

    private suspend fun boardOf(vararg tiles: Tile) = repo().save(Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList()))))

    private fun BoardViewModel.es(id: String): String? = board.value.findTile(id)?.translations?.get("es")

    private fun BoardViewModel.auto(id: String): Boolean = board.value.findTile(id)?.isAutoTranslated("es") == true

    // --- The phrasebook ---

    @Test
    fun `the phrasebook ignores case spaces and trailing punctuation`() {
        assertEquals("Gracias", phrasebook.lookup("  THANK YOU  ", "es"))
        assertEquals("Gracias", phrasebook.lookup("Thank you!", "es"))
        assertEquals("Gracias", phrasebook.lookup("thank   you...", "es"))
        assertEquals("¿Un abrazo?", phrasebook.lookup("Hug", "es"))
        assertEquals("Agua", phrasebook.lookup("Water?!", "es"))
    }

    @Test
    fun `the phrasebook matches curly apostrophes and keeps inner punctuation`() {
        val book = Phrasebook("es", mapOf("I'm cold." to "Tengo frío", "Yes, please." to "Sí, por favor", "Yes please" to "Sí, gracias"))

        assertEquals("Tengo frío", book.lookup("I’m cold", "es"))
        assertEquals("Sí, por favor", book.lookup("yes, please", "es"))
        assertEquals("Sí, gracias", book.lookup("Yes please!", "es"))
    }

    @Test
    fun `the phrasebook knows only its own language and its own phrases`() {
        assertNull(phrasebook.lookup("Water", "fr"))
        assertNull(phrasebook.lookup("Where is the nurse", "es"))
        assertNull(phrasebook.lookup("   ", "es"))
        assertNull(Phrasebook.Empty.lookup("Water", "es"))
    }

    @Test
    fun `a phrasebook file that is not a phrasebook is empty`() {
        assertEquals(0, Phrasebook.parse("es", "not json").size)
        assertEquals(0, Phrasebook.parse("es", "[1, 2]").size)
        assertEquals(2, Phrasebook.parse("es", """{"Water": "Agua", "Yes": "Sí", "No": 4, "": "x"}""").size)
    }

    // --- Switching to ES ---

    @Test
    fun `switching to ES fills from the phrasebook with no translator at all`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Thank you", ttsScript = "Thank you."))
        val vm = newViewModel(UnavailableTranslator("none here."))

        vm.setSecondLanguageOn(true)

        assertEquals("Agua", vm.es("a"))
        assertEquals("Gracias", vm.es("b"))
        assertTrue(vm.auto("a") && vm.auto("b"))
        assertNull(vm.spanishNote.value)
        assertNull(vm.translateProgress.value)
    }

    @Test
    fun `the phrasebook is asked first and the translator only gets the rest`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Juice"), Tile(id = "c", label = "Yes"))
        translator.translations["Juice"] = "Jugo"
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        assertEquals(listOf("Juice"), translator.translated)
        assertEquals(listOf("Agua", "Jugo", "Sí"), listOf("a", "b", "c").map { vm.es(it) })
        assertTrue(vm.auto("b"))
    }

    @Test
    fun `tiles with no words or Spanish already are left alone`() = runTest(dispatcher) {
        boardOf(Tile(id = "a"), Tile(id = "b", label = "Water", translations = mapOf("es" to "Agüita")), Tile(id = "c", fileName = "x.m4a"))
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        assertEquals("Agüita", vm.es("b"))
        assertFalse(vm.auto("b"))
        assertNull(vm.es("a"))
        assertNull(vm.es("c"))
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `it fills tiles on every page and translates what they say not their names`() = runTest(dispatcher) {
        repo().save(
            Board(
                pages = listOf(
                    Page(id = "p1", rows = 1, columns = 1, tiles = listOf(Tile(id = "a", label = "Water"))),
                    Page(id = "p2", rows = 1, columns = 1, tiles = listOf(Tile(id = "b", label = "Pain", ttsScript = "My pain is bad")))
                )
            )
        )
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        assertEquals("Agua", vm.es("a"))
        assertEquals("[es] My pain is bad", vm.es("b"))
    }

    @Test
    fun `typed Spanish survives a fill and stays typed`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agüita")), Tile(id = "b", label = "Juice"))
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)
        vm.translateAllTiles()

        assertEquals("Agüita", vm.es("a"))
        assertFalse(vm.auto("a"))
        assertEquals("[es] Juice", vm.es("b"))
    }

    @Test
    fun `Spanish typed while the translator works is not replaced`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"), Tile(id = "b", label = "Milk"))
        val gate = GateTranslator()
        val vm = newViewModel(gate)

        vm.setSecondLanguageOn(true)
        // Held on "Juice"; the person types its Spanish meanwhile.
        val juice = vm.board.value.findTile("a")!!
        vm.saveTile("a", juice.withTranslation("es", "Zumo"))
        gate.release("Jugo")
        gate.release("Leche")

        assertEquals("Zumo", vm.es("a"))
        assertFalse(vm.auto("a"))
        assertEquals("Leche", vm.es("b"))
    }

    @Test
    fun `a fill is saved in batches and never one save per tile`() = runTest(dispatcher) {
        boardOf(*Array(25) { Tile(id = "t$it", label = "Thing $it") })
        val vm = newViewModel()
        files.boardWrites = 0

        vm.setSecondLanguageOn(true)

        assertEquals(25, vm.board.value.pages.single().tiles.count { it.translationFor("es") != null })
        assertEquals(3, files.boardWrites)
    }

    @Test
    fun `the fill is on the saved board`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        val saved = repo().load().findTile("a")
        assertEquals("Agua", saved?.translations?.get("es"))
        assertEquals(setOf("es"), saved?.autoTranslated)
    }

    @Test
    fun `switching to English does nothing and stops a fill`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"), Tile(id = "b", label = "Milk"))
        val gate = GateTranslator()
        val vm = newViewModel(gate)

        vm.setSecondLanguageOn(true)
        assertEquals(TranslateProgress(0, 2), vm.translateProgress.value)
        vm.setSecondLanguageOn(false)

        assertNull(vm.translateProgress.value)
        assertNull(vm.es("a"))
        assertNull(vm.spanishNote.value)
    }

    @Test
    fun `a board without a second language fills nothing`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()
        vm.setSecondLanguageEnabled(false)

        vm.setSecondLanguageOn(true)

        assertNull(vm.es("a"))
    }

    @Test
    fun `turning the second language on while ES is showing fills it in`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()
        vm.setSecondLanguageEnabled(false)
        vm.setSecondLanguageOn(true)

        vm.setSecondLanguageEnabled(true)

        assertEquals("Agua", vm.es("a"))
    }

    // --- The progress ---

    @Test
    fun `the banner counts the tiles as they are translated`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Juice"), Tile(id = "c", label = "Milk"))
        val gate = GateTranslator()
        val vm = newViewModel(gate)

        vm.setSecondLanguageOn(true)
        // The phrasebook did Water; the translator is on Juice.
        assertEquals(TranslateProgress(1, 3), vm.translateProgress.value)
        gate.release("Jugo")
        assertEquals(TranslateProgress(2, 3), vm.translateProgress.value)
        gate.release("Leche")

        assertNull(vm.translateProgress.value)
        assertEquals(listOf("Agua", "Jugo", "Leche"), listOf("a", "b", "c").map { vm.es(it) })
    }

    @Test
    fun `the board is usable while Spanish is being added`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice", speakWhenNoSound = true))
        val gate = GateTranslator()
        val speaker = FakeSpeaker()
        val vm = BoardViewModel(
            repo(), FakePlayer(), FakeRecorder(), SavedBoardRepository(files), speaker, prefs,
            RecentBoardsRepository(files), ioDispatcher = dispatcher, now = { 1L }, mediaVolume = FakeMediaVolume(),
            translator = gate, phrasebookSource = { phrasebook }
        )

        vm.setSecondLanguageOn(true)
        vm.play(vm.board.value.findTile("a")!!)

        // No Spanish yet, so it speaks English, as a tile without Spanish always does.
        assertEquals("Juice", speaker.spoken.last())
    }

    @Test
    fun `Cancel stops it and keeps what was done`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"), Tile(id = "b", label = "Milk"), Tile(id = "c", label = "Bread"))
        val gate = GateTranslator()
        val vm = newViewModel(gate)

        vm.setSecondLanguageOn(true)
        gate.release("Jugo")
        vm.cancelSpanishFill()

        assertNull(vm.translateProgress.value)
        assertEquals("Jugo", vm.es("a"))
        assertNull(vm.es("b"))
        val note = vm.spanishNote.value.orEmpty()
        assertTrue("2 tiles have no Spanish yet" in note, note)
        assertTrue("Translate all tiles" in note, note)
        // The same button, in Settings, picks it up again.
        vm.dismissSpanishNote()
        assertNull(vm.spanishNote.value)
    }

    // --- Downloading the model ---

    @Test
    fun `the first switch to ES downloads the model and uses it`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"))
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        assertEquals(1, translator.prepared)
        assertEquals("[es] Juice", vm.es("a"))
        assertEquals(TranslatorStatus.Ready, vm.translatorStatus.value)
    }

    @Test
    fun `the model is fetched on the switch even when the phrasebook knew every tile`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        assertEquals("Agua", vm.es("a"))
        assertEquals(1, translator.prepared)
        assertEquals(TranslatorStatus.Ready, vm.translatorStatus.value)
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `a failed download says why and keeps the tiles marked`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"), Tile(id = "b", label = "Water"))
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        translator.downloadFails = "No internet."
        val vm = newViewModel()

        vm.setSecondLanguageOn(true)

        assertEquals("Agua", vm.es("b"))
        assertNull(vm.es("a"))
        val note = vm.spanishNote.value.orEmpty()
        assertTrue("Couldn't add Spanish to 1 tile" in note, note)
        assertTrue("No internet." in note, note)
        assertNull(vm.translateProgress.value)

        // The manual button tries again.
        translator.downloadFails = null
        vm.translateAllTiles()
        assertEquals("[es] Juice", vm.es("a"))
        assertNull(vm.spanishNote.value)
    }

    // --- Where nothing can translate ---

    @Test
    fun `where translating is unavailable the leftover tiles keep the marker and a note says why once`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Juice"), Tile(id = "c", label = "Milk"))
        val vm = newViewModel(UnavailableTranslator("this browser has no translator."))

        vm.setSecondLanguageOn(true)

        assertEquals("Agua", vm.es("a"))
        assertTrue(vm.board.value.findTile("b")!!.lacksTranslation("es"))
        val note = vm.spanishNote.value.orEmpty()
        assertTrue("2 tiles have no Spanish yet" in note, note)
        assertTrue("this browser has no translator." in note, note)
        assertTrue("editor" in note, note)

        // Said once: the next switch to ES finds the note already shown.
        vm.dismissSpanishNote()
        vm.setSecondLanguageOn(false)
        vm.setSecondLanguageOn(true)
        assertNull(vm.spanishNote.value)
    }

    @Test
    fun `no note when the phrasebook covered every tile`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Yes"))
        val vm = newViewModel(UnavailableTranslator("none here."))

        vm.setSecondLanguageOn(true)

        assertNull(vm.spanishNote.value)
        assertFalse(prefs.translatorUnavailableNoteSeen)
    }

    @Test
    fun `Translate all tiles still says why when it can't translate`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"))
        val vm = newViewModel(UnavailableTranslator("none here."))

        vm.translateAllTiles()

        assertTrue("none here." in vm.message.value.orEmpty())
    }

    // --- Saving a tile ---

    @Test
    fun `saving a tile with new words fills its Spanish from the phrasebook`() = runTest(dispatcher) {
        boardOf(Tile(id = "a"))
        val vm = newViewModel(UnavailableTranslator("none here."))

        vm.saveTile("a", Tile(id = "a", label = "Thank you"))

        assertEquals("Gracias", vm.es("a"))
        assertTrue(vm.auto("a"))
    }

    @Test
    fun `saving fills Spanish even while the switch is on English`() = runTest(dispatcher) {
        boardOf(Tile(id = "a"))
        val vm = newViewModel()
        assertFalse(vm.secondLanguageOn.value)

        vm.saveTile("a", Tile(id = "a", label = "Water"))

        assertEquals("Agua", vm.es("a"))
    }

    @Test
    fun `saving uses the translator when it is ready`() = runTest(dispatcher) {
        boardOf(Tile(id = "a"))
        val vm = newViewModel()

        vm.saveTile("a", Tile(id = "a", label = "Juice"))

        assertEquals("[es] Juice", vm.es("a"))
        assertTrue(vm.auto("a"))
    }

    @Test
    fun `saving never starts a model download`() = runTest(dispatcher) {
        boardOf(Tile(id = "a"))
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        val vm = newViewModel()

        vm.saveTile("a", Tile(id = "a", label = "Juice"))

        assertNull(vm.es("a"))
        assertEquals(0, translator.prepared)
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `saving a tile without a second language on the board adds nothing`() = runTest(dispatcher) {
        boardOf(Tile(id = "a"))
        val vm = newViewModel()
        vm.setSecondLanguageEnabled(false)

        vm.saveTile("a", Tile(id = "a", label = "Water"))

        assertNull(vm.es("a"))
    }

    @Test
    fun `saving a tile without changing its words does not translate it again`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"))
        val vm = newViewModel()

        vm.saveTile("a", vm.board.value.findTile("a")!!.copy(volume = 0.5f))

        assertNull(vm.es("a"))
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `typed Spanish survives a change to the English`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice", translations = mapOf("es" to "Zumo")))
        val vm = newViewModel()

        vm.saveTile("a", vm.board.value.findTile("a")!!.copy(label = "Orange juice"))

        assertEquals("Zumo", vm.es("a"))
        assertFalse(vm.auto("a"))
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `automatic Spanish is refreshed when the English changes`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua"), autoTranslated = setOf("es")))
        val vm = newViewModel()

        vm.saveTile("a", vm.board.value.findTile("a")!!.copy(label = "Thank you"))

        assertEquals("Gracias", vm.es("a"))
        assertTrue(vm.auto("a"))
    }

    @Test
    fun `automatic Spanish goes when the English changes and nothing can refresh it`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua"), autoTranslated = setOf("es")))
        val vm = newViewModel(UnavailableTranslator("none here."))

        vm.saveTile("a", vm.board.value.findTile("a")!!.copy(label = "Something nobody wrote"))

        // Old Spanish saying something else would be worse than the marker.
        assertNull(vm.es("a"))
        assertFalse(vm.auto("a"))
        assertTrue(vm.board.value.findTile("a")!!.lacksTranslation("es"))
    }

    @Test
    fun `editing the Spanish by hand takes it out of the automatic ones for good`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua"), autoTranslated = setOf("es")))
        val vm = newViewModel()

        // The editor takes the language out of the draft's set when its field is typed in.
        vm.saveTile("a", vm.board.value.findTile("a")!!.withTranslation("es", "Agüita"))
        assertEquals("Agüita", vm.es("a"))
        assertFalse(vm.auto("a"))

        vm.saveTile("a", vm.board.value.findTile("a")!!.copy(label = "Thank you"))
        vm.setSecondLanguageOn(true)
        vm.translateAllTiles()

        assertEquals("Agüita", vm.es("a"))
        assertFalse(vm.auto("a"))
    }

    @Test
    fun `an emptied Spanish field is not automatic`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice", translations = mapOf("es" to "Zumo"), autoTranslated = setOf("es")))
        val vm = newViewModel()

        // The draft keeps the marker, but its text was cleared.
        vm.saveTile("a", vm.board.value.findTile("a")!!.copy(translations = mapOf("es" to "  ")))

        assertTrue(vm.board.value.findTile("a")!!.autoTranslated.isEmpty())
    }

    @Test
    fun `clearing a tile clears its automatic marks`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua"), autoTranslated = setOf("es")))
        val vm = newViewModel()

        vm.clearTile("a")

        assertEquals(emptyMap(), vm.board.value.findTile("a")?.translations)
        assertTrue(vm.board.value.findTile("a")!!.autoTranslated.isEmpty())
    }

    @Test
    fun `suggesting asks the phrasebook first`() = runTest(dispatcher) {
        val vm = newViewModel()
        var result: String? = null

        vm.suggestTranslation("Thank you.") { result = it }

        assertEquals("Gracias", result)
        assertTrue(translator.translated.isEmpty())
    }

    // --- Opening and restoring a board ---

    @Test
    fun `opening a board while ES is on fills it in`() = runTest(dispatcher) {
        val other = Board(name = "Other", pages = listOf(Page(rows = 1, columns = 2, tiles = listOf(Tile(id = "x", label = "Water"), Tile(id = "y", label = "Juice")))))
        val bundled = mapOf(
            "other.zip" to zip.write(listOf(ZipEntryData("board.json", BoardJson.encodeToString(other).encodeToByteArray())))
        )
        boardOf(Tile(id = "a", label = "Yes"))
        val vm = newViewModel(bundled = bundled)
        vm.setSecondLanguageOn(true)

        vm.openBoard(BoardRef.BuiltIn("other.zip", "Other"))

        assertEquals("Agua", vm.es("x"))
        assertEquals("[es] Juice", vm.es("y"))
    }

    @Test
    fun `opening a saved board while ES is on fills it in`() = runTest(dispatcher) {
        val saved = SavedBoardRepository(files).save(Board(name = "Mine", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "x", label = "Water"))))))
        boardOf(Tile(id = "a", label = "Yes"))
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.openBoard(BoardRef.Saved(saved))

        assertEquals("Agua", vm.es("x"))
    }

    @Test
    fun `restoring a backup while ES is on fills it in`() = runTest(dispatcher) {
        val backup = Board(name = "Backup", pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(Tile(id = "x", label = "Thank you")))))
        val pickedZip = zip.write(listOf(ZipEntryData("board.json", BoardJson.encodeToString(backup).encodeToByteArray())))
        boardOf(Tile(id = "a", label = "Yes"))
        val vm = newViewModel()
        vm.setSecondLanguageOn(true)

        vm.importBoard(com.example.soundboard.data.FakePickedFile(pickedZip, "zip"))

        assertEquals("Gracias", vm.es("x"))
    }

    @Test
    fun `starting the app with ES on fills in whatever has no Spanish`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Juice"))
        prefs.secondLanguageActive = true

        val vm = newViewModel()

        assertEquals("Agua", vm.es("a"))
        assertEquals("[es] Juice", vm.es("b"))
    }

    @Test
    fun `opening a board never starts a download`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Juice"))
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        prefs.secondLanguageActive = true

        val vm = newViewModel()

        assertNull(vm.es("a"))
        assertEquals(0, translator.prepared)
    }

    // --- The model ---

    @Test
    fun `a board saved before automatic Spanish loads with none marked`() = runTest(dispatcher) {
        files.put(
            "board.json",
            """{"name":"Old","pages":[{"id":"p","name":"One","rows":1,"columns":1,"tiles":[{"id":"a","label":"Water","translations":{"es":"Agua"}}]}]}""".encodeToByteArray()
        )

        val tile = repo().load().findTile("a")

        assertNotNull(tile)
        assertEquals(mapOf("es" to "Agua"), tile.translations)
        assertTrue(tile.autoTranslated.isEmpty())
        assertFalse(tile.isAutoTranslated("es"))
    }

    @Test
    fun `automatic marks travel in the saved board`() {
        val tile = Tile(id = "a", label = "Water").withAutoTranslation("es", "Agua")

        val again = BoardJson.decodeFromString<Board>(BoardJson.encodeToString(Board(pages = listOf(Page(rows = 1, columns = 1, tiles = listOf(tile))))))

        assertEquals(setOf("es"), again.pages.single().tiles.single().autoTranslated)
    }

    @Test
    fun `typed text replaces automatic text and takes the mark with it`() {
        val auto = Tile(id = "a", label = "Water").withAutoTranslation("es", "Agua")

        assertTrue(auto.isAutoTranslated("es"))
        assertFalse(auto.withTranslation("es", "Agüita").isAutoTranslated("es"))
        assertFalse(auto.withTranslation("es", " ").isAutoTranslated("es"))
        assertFalse(auto.copy(translations = emptyMap()).isAutoTranslated("es"))
    }
}

/** A [FileStore] that counts how often the board is written. */
private class CountingFileStore(private val inner: InMemoryFileStore) : FileStore {
    var boardWrites = 0

    fun put(path: String, bytes: ByteArray) = inner.put(path, bytes)

    override suspend fun exists(path: String) = inner.exists(path)
    override suspend fun read(path: String) = inner.read(path)
    override suspend fun write(path: String, bytes: ByteArray) {
        if (path == "board.json") boardWrites++
        inner.write(path, bytes)
    }
    override suspend fun delete(path: String) = inner.delete(path)
    override suspend fun list(directory: String): List<StoredFileInfo> = inner.list(directory)
}
