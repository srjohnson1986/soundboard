package com.example.soundboard

import com.example.soundboard.audio.FakeMediaVolume
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.audio.FakeTranslator
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
import com.example.soundboard.model.Tile
import com.example.soundboard.translate.TranslatorStatus
import com.example.soundboard.translate.UnavailableTranslator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Translating on the device (#332) through [BoardViewModel], with a fake [com.example.soundboard.translate.Translator]:
 * suggesting a tile's Spanish, translating every tile, and the Speak... preview. A translation only ever
 * lands in a tile as text the user could edit; nothing here is used at playback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val files = InMemoryFileStore()
    private val repo = BoardRepository(files, FakeZipCodec(), FakeBundledBoards())
    private val speaker = FakeSpeaker()
    private val translator = FakeTranslator()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun newViewModel(translator: com.example.soundboard.translate.Translator = this.translator) = BoardViewModel(
        repo, FakePlayer(), FakeRecorder(), SavedBoardRepository(files), speaker, DevicePreferences(MapKeyValueStore()),
        RecentBoardsRepository(files), ioDispatcher = dispatcher, now = { 1L }, mediaVolume = FakeMediaVolume(),
        translator = translator
    )

    private suspend fun boardOf(vararg tiles: Tile) = repo.save(Board(pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList()))))

    /** Runs a callback-style call to its result. */
    private fun suggest(vm: BoardViewModel, english: String): String? {
        var result: String? = "not called"
        vm.suggestTranslation(english) { result = it }
        return result
    }

    // --- Suggest ---

    @Test
    fun `suggest hands back the translation and changes nothing on the board`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()
        translator.translations["Water, please"] = "Agua, por favor"

        assertEquals("Agua, por favor", suggest(vm, "  Water, please "))

        assertEquals(listOf("Water, please"), translator.translated)
        assertEquals(emptyMap(), vm.board.value.findTile("a")?.translations)
    }

    @Test
    fun `suggest with nothing to translate is null and asks the translator nothing`() = runTest(dispatcher) {
        val vm = newViewModel()

        assertNull(suggest(vm, "   "))
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `suggest on a board without a second language is null`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSecondLanguageEnabled(false)

        assertNull(suggest(vm, "Water"))
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `suggest downloads the model first when it is not here yet`() = runTest(dispatcher) {
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        val vm = newViewModel()

        assertEquals("[es] Water", suggest(vm, "Water"))

        assertEquals(1, translator.prepared)
        assertEquals(TranslatorStatus.Ready, vm.translatorStatus.value)
    }

    @Test
    fun `suggest says why when the download fails and can be tried again`() = runTest(dispatcher) {
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        translator.downloadFails = "No internet."
        val vm = newViewModel()

        assertNull(suggest(vm, "Water"))
        assertEquals(TranslatorStatus.Failed("No internet."), vm.translatorStatus.value)
        assertTrue(vm.translatorStatus.value.canTranslate)

        translator.downloadFails = null
        assertEquals("[es] Water", suggest(vm, "Water"))
    }

    @Test
    fun `an unavailable translator suggests nothing and keeps saying why`() = runTest(dispatcher) {
        val vm = newViewModel(UnavailableTranslator("no translator here."))

        assertNull(suggest(vm, "Water"))
        assertEquals(TranslatorStatus.Unavailable("no translator here."), vm.translatorStatus.value)
        assertEquals("Translating isn't available here: no translator here.", vm.translatorStatus.value.explanation)
    }

    // --- Translate all tiles ---

    @Test
    fun `translate all fills in every tile without Spanish and keeps the rest`() = runTest(dispatcher) {
        boardOf(
            Tile(id = "a", label = "Water"),
            Tile(id = "b", label = "Yes", ttsScript = "Yes please", translations = mapOf("es" to "Si, por favor")),
            Tile(id = "c", label = "Hello", fileName = "hello.m4a", ttsScript = "Hello there"),
            Tile(id = "d")
        )
        val vm = newViewModel()

        vm.translateAllTiles()

        val board = vm.board.value
        assertEquals("[es] Water", board.findTile("a")?.translations?.get("es"))
        assertEquals("Si, por favor", board.findTile("b")?.translations?.get("es"))
        // It translates what the tile says, not its short name.
        assertEquals("[es] Hello there", board.findTile("c")?.translations?.get("es"))
        assertEquals(emptyMap(), board.findTile("d")?.translations)
        assertEquals(listOf("Water", "Hello there"), translator.translated)
        assertEquals("Added Spanish to 2 tiles.", vm.message.value)
        assertNull(vm.translateProgress.value)
    }

    @Test
    fun `translate all is saved with the board`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()

        vm.translateAllTiles()

        assertEquals("[es] Water", repo.load().findTile("a")?.translations?.get("es"))
    }

    @Test
    fun `translate all says so when every tile has Spanish already`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water", translations = mapOf("es" to "Agua")), Tile(id = "b"))
        val vm = newViewModel()

        vm.translateAllTiles()

        assertEquals("Every tile already has Spanish.", vm.message.value)
        assertTrue(translator.translated.isEmpty())
    }

    @Test
    fun `translate all reports one tile in the singular`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()

        vm.translateAllTiles()

        assertEquals("Added Spanish to 1 tile.", vm.message.value)
    }

    @Test
    fun `translate all stops and says why when translating fails`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Juice"), Tile(id = "c", label = "Milk"))
        translator.failWith = "The translator broke."
        val vm = newViewModel()

        vm.translateAllTiles()

        // It doesn't ask three more times of a translator that has just failed.
        assertEquals(listOf("Water"), translator.translated)
        assertEquals(emptyMap(), vm.board.value.findTile("a")?.translations)
        val message = vm.message.value.orEmpty()
        assertTrue("No tiles were translated." in message, message)
        assertTrue("The translator broke." in message, message)
        assertNull(vm.translateProgress.value)
    }

    @Test
    fun `translate all when the model will not download says so and changes nothing`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        translator.downloadFails = "No internet."
        val vm = newViewModel()

        vm.translateAllTiles()

        assertEquals(emptyMap(), vm.board.value.findTile("a")?.translations)
        assertTrue("No internet." in vm.message.value.orEmpty())
    }

    @Test
    fun `translate all on an unavailable translator changes nothing and says why`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel(UnavailableTranslator("not on this device."))

        vm.translateAllTiles()

        assertEquals(emptyMap(), vm.board.value.findTile("a")?.translations)
        assertTrue("not on this device." in vm.message.value.orEmpty())
    }

    @Test
    fun `translate all does nothing on a board without a second language`() = runTest(dispatcher) {
        boardOf(Tile(id = "a", label = "Water"))
        val vm = newViewModel()
        vm.setSecondLanguageEnabled(false)

        vm.translateAllTiles()

        assertTrue(translator.translated.isEmpty())
    }

    // --- The Speak... preview ---

    @Test
    fun `the preview translates only when the model is ready and never starts a download`() = runTest(dispatcher) {
        val vm = newViewModel()
        assertEquals("[es] Water", vm.translateForPreview(" Water "))

        translator.status.value = TranslatorStatus.NeedsDownload(30)
        assertNull(vm.translateForPreview("Water"))
        assertEquals(0, translator.prepared)

        assertNull(vm.translateForPreview("  "))
    }

    @Test
    fun `preparing the translator downloads the model`() = runTest(dispatcher) {
        translator.status.value = TranslatorStatus.NeedsDownload(30)
        val vm = newViewModel()

        vm.prepareTranslator()

        assertEquals(TranslatorStatus.Ready, vm.translatorStatus.value)
    }

    // --- The model ---

    @Test
    fun `untranslated tiles are the ones with words and no Spanish on any page`() {
        val board = Board(
            pages = listOf(
                Page(rows = 1, columns = 2, tiles = listOf(Tile(id = "a", label = "Water"), Tile(id = "b", label = "Yes", translations = mapOf("es" to "Si")))),
                Page(rows = 1, columns = 2, tiles = listOf(Tile(id = "c", fileName = "x.m4a"), Tile(id = "d", label = "Hi", translations = mapOf("es" to "  "))))
            )
        )

        assertEquals(listOf("a", "d"), board.untranslatedTiles("es").map { it.id })
    }

    @Test
    fun `every status explains itself except ready`() {
        assertNull(TranslatorStatus.Ready.explanation)
        assertTrue("about 30 MB" in TranslatorStatus.NeedsDownload(30).explanation.orEmpty())
        assertTrue("(40%)" in TranslatorStatus.Downloading(40).explanation.orEmpty())
        assertTrue(TranslatorStatus.Downloading(null).explanation.orEmpty().endsWith("..."))
        assertTrue("went wrong" in TranslatorStatus.Failed("went wrong").explanation.orEmpty())
        assertTrue(TranslatorStatus.Checking.explanation != null)
        assertTrue(!TranslatorStatus.Unavailable("x").canTranslate && !TranslatorStatus.Checking.canTranslate)
        assertTrue(TranslatorStatus.NeedsDownload().canTranslate && TranslatorStatus.Failed("x").canTranslate)
    }
}
