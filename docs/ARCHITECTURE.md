# Architecture

MVVM over a single mutable `Board`, with one function (`BoardViewModel.commit()`)
as the sole path to disk. Everything else is Compose reacting to a `StateFlow`.

## Modules

| Module | What's in it |
|---|---|
| `shared/` | Code that doesn't depend on Android, built for both Android and WebAssembly (Kotlin Multiplatform): the board model (`model/`) and the repositories (`data/`). More of the app moves here as the web version comes together (#193). |
| `app/` | The Android app: everything else below, which uses `shared` like any other library. |

`shared`'s common code has no Android or Compose dependencies. Keep it that way:
anything that needs a platform goes behind one of the small interfaces in
`shared/.../data/Storage.kt`, with an implementation per platform:

| Interface | What it is | Android implementation |
|---|---|---|
| `FileStore` | App-private files by relative path (`board.json`, `sounds/<name>`) | `FileSystemStore(filesDir)` (`shared/src/androidMain`) |
| `ZipCodec` | Reads/writes zip archives (backups, built-in boards) | `JavaZipCodec`, on `java.util.zip` (`shared/src/androidMain`) |
| `PickedFile` / `SaveTarget` | A file the user picked to open / somewhere they picked to save | `UriPickedFile` / `UriSaveTarget`, on content URIs (`app/.../data/AndroidStorage.kt`) |
| `BundledBoards` | The built-in board zips packaged with the build | `AssetBundledBoards`, on APK assets (same file) |
| `KeyValueStore` | Small device-local settings (`DevicePreferences`) | `SharedPreferencesStore` (same file) |

`AndroidStorage.kt` also has factory functions named after the repositories
(`BoardRepository(context)` and so on) that wire them to those implementations,
which is what the app and the Android tests call. `FileStore` is suspending
because the web's storage is asynchronous. A new platform capability follows the
same pattern: an interface in `shared`, and an implementation for each platform
in the same change. Moved code keeps its package (`com.example.soundboard.model`,
`...data`), so moving a file there changes no imports. Its tests run twice, on the JVM and compiled to WebAssembly
(`./gradlew :shared:allTests`), which is what keeps it building for the web.
One side effect of the module boundary: Kotlin won't smart-cast a model
property from `app/` (`if (tile.fileName != null) use(tile.fileName)` fails
to compile); read it into a local `val` first.

## Layers

| File | Responsibility |
|---|---|
| `shared/.../model/Board.kt` | `Tile`, `Page`, and `Board` data classes; resize, visibility, reorder, and page-management logic. No Android dependencies. |
| `shared/.../data/BoardRepository.kt` | Reads/writes `board.json`, copies picked audio into app storage, zips/unzips backups. All file I/O, through `FileStore`/`ZipCodec`. |
| `shared/.../data/SavedBoardRepository.kt` | Reads/writes small `Board`-snapshot JSON files under `presets/` — same-device version history, deliberately not carrying its own copy of audio (see "Saved boards" below). |
| `audio/Player.kt` | Interface (`load`/`play`/`unload`/`clear`/`release`) that `BoardViewModel` depends on. The seam that lets tests substitute a fake instead of real audio. |
| `audio/SoundPlayer.kt` | Real `Player` implementation: owns the `SoundPool` and the current `MediaPlayer`. No knowledge of `Board` or `Tile`. |
| `audio/Recorder.kt` | Interface (`start`/`stop`/`cancel`) that `BoardViewModel` depends on for recording — same fake-in-tests seam as `Player`. |
| `audio/AudioRecorder.kt` | Real `Recorder` implementation: owns a `MediaRecorder`, encoding straight to a file `BoardRepository` hands it. |
| `BoardViewModel.kt` | Holds the `Board` as a `StateFlow`, wires the other layers together, single write path. Takes `BoardRepository`/`Player`/`Recorder`/`SavedBoardRepository`/dispatcher as constructor params (see below) rather than constructing them. |
| `BoardSettingsActions.kt` | Interface listing every change the Settings dialog can make; `BoardViewModel` implements it, so the dialog takes one object instead of a callback per setting. |
| `ui/BoardScreen.kt` | The screen: its state (edit mode, the one open `BoardDialog`, the idle timer), the pager, the sticky home row, and the dialog host. |
| `ui/BoardTopBar.kt` | Title, page tabs (with the long-press gesture) and the hamburger menu. |
| `ui/PageGrid.kt` | `PageGrid`, `PinnedRow` and `TileCard`: grid layout, drag-to-reorder, tile colors. |
| `ui/EditTileDialog.kt`, `ui/SettingsDialog.kt`, `ui/PageDialogs.kt`, `ui/BoardManagementDialogs.kt`, `ui/SpeakDialog.kt` | The dialogs, grouped by what they edit. |
| `ui/Controls.kt` | Shared building blocks: `SwitchRow`, `HelperText`, `ColorPicker`, `SegmentedChoice`, `OverrideSection`, `OptionDropdown`, `Stepper`, opacity/border controls. |
| `MainActivity.kt` | Just sets content to `SoundboardTheme { BoardScreen() }`. |

Data flows one way: UI calls a `BoardViewModel` function → it updates
`_board` (a `MutableStateFlow<Board>`) → Compose recomposes because
`BoardScreen` collects `board` with `collectAsStateWithLifecycle()`. The UI
never touches `BoardRepository` or `SoundPlayer` directly.

## The data model

```kotlin
data class Tile(
    val id: String = Uuid.random().toString(),
    val label: String = "",
    val fileName: String? = null,   // null = empty tile
    val volume: Float = 1f,
    val colorArgb: Int? = null      // null = use the theme default
)

data class Page(
    val id: String = Uuid.random().toString(),
    val name: String = "Page 1",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = List(16) { Tile() },
    val tileAspectRatio: Float = 1f,  // width:height; e.g. 4f/3f for wider-than-tall
    val color: Int? = null,           // page-identity accent; null = theme default
    val isHome: Boolean = false       // auto-return target; at most one page should have this set
)

data class Board(
    val name: String = "New Board",
    val pages: List<Page> = listOf(Page()),
    val currentPageIndex: Int = 0,
    val stickyHomeRowEnabled: Boolean = false  // shows the home page's first row above every OTHER page
) {
    val homePageIndex: Int?   // computed — derived from pages, not stored; see "Why isHome is per-page" below
    val homePage: Page?       // computed — pages[homePageIndex], or null
    val hasAnySound: Boolean  // computed — any tile on any page has a sound
}
```

A board is one or more `Page`s, each an independent grid, switched via tabs
in the UI. `Board.name` identifies the whole board (shown in the title bar);
each `Page.name` identifies just that tab; `Page.color` is that tab's own
identity accent, distinct from `Tile.colorArgb` (a tile's own color always
wins). `name`, `currentPageIndex`, and `stickyHomeRowEnabled` are the
board-level state that isn't grid geometry or page tiles —
`BoardViewModel.renameBoard()`/`switchPage()`/`setStickyHomeRowEnabled()` are
their write paths, and none of them need a dedicated persistence concept
since they're just more fields in `board.json`. `Board.currentPage` resolves
the active `Page` (clamping `currentPageIndex` defensively);
`Board.updatingCurrentPage { transform }` is how a grid mutation reaches it
without the caller handling the page list itself, and
`Board.updatingTile(tileId) { transform }` edits one tile on whichever page
holds it. `withPageAdded()`, `withPageRemoved()` (a no-op on the last
remaining page), `withPageRenamed()`, and `withCurrentPage()` round out page
management, all returning a new `Board` like every other mutator here. The
naming is consistent across `Board` and `Page`: `with…` returns a copy with
something set, `updating…` applies a caller's transform.

**Why `isHome` is per-page, not a board-level index.** It used to be a single
`Board.homePageIndex: Int?`, which meant `withPageRemoved()`/`withPageMoved()` both had
to carry index-remapping logic (shift it down, clear it, or follow a move) any
time the page list changed shape. Moving the flag onto `Page` itself made that
bookkeeping unnecessary — deleting a page deletes its `isHome` flag with it,
and reordering pages doesn't touch `isHome` at all, since it's intrinsic to
the `Page` object rather than a position in a list. `Board.homePageIndex` is
kept as a computed property (`pages.indexOfFirst { it.isHome }`) purely so the
handful of read sites (the auto-return effect, the Home-page toggle) didn't
need to change at all. `withHomePage(index)`/`withoutHomePage()` are still
the write paths, just implemented as `pages.map { it.copy(isHome = ...) }`
now instead of setting a single field.

**The sticky home row isn't its own data — it's just the home page's first
row, read a second time.** An earlier version had a wholly separate
`Board.pinnedTiles`/`pinnedRowSize`, an independently-edited tile list shown
above every page (see git history / older revisions of this doc if you need
the details). That meant keeping two copies of "the tiles you use most" in
sync by hand — one in the pinned row, one wherever they'd naturally live on a
page. `stickyHomeRowEnabled` replaced it: when on, `BoardScreen` renders
`board.homePage.tiles.take(homePage.columns)` fixed above whichever page
isn't the home page (hidden on the home page itself, since it's already
showing there as ordinary content). Editing a tile from that banner writes
straight into the home page's tile list through the same `BoardViewModel`
tile editors as any other tile (`setLabel`, `assignSound`, `stopRecording`,
…): they go through `Board.updatingTile(tileId)`, which finds the tile on
whichever page holds it. That works because tile ids are unique across a
board — the banner shows the home page's own tiles, never copies (#155).
There's no separate width setting either — the banner is simply as wide as
the home page's own `columns`.

**Each orientation shows a prefix of one shared `tiles` list.** Portrait
shows `visibleTiles = tiles.take(rows * columns)`. Landscape (under
`Board.landscapeLayout == PAGE_GRID`) shows `landscapeTiles`, a prefix
`effectiveLandscapeColumns` wide and `shownLandscapeRows` tall. Unset
`Page.landscapeColumns`/`landscapeRows` default to `columns * 2` and
`ceil(rows / 2) + 1`. Landscape rows keep their portrait height
(`portraitRowHeightPx`, measured against the window's shorter side), so a
landscape tile is a different shape from its portrait self. Under
`FIT_TO_SCREEN` landscape instead reflows `visibleTiles` into however many
columns fit 4 rows (`landscapeColumnCount`), the original behavior.

**Invariant: no tile with content is ever hidden, in either orientation.**
Every write goes through `Board.normalized()` (`BoardViewModel.commit` and
`BoardRepository.load`), which for each page:
1. grows `rows` to reach the last tile with a sound or label (`Tile.hasContent`),
   e.g. one filled in a landscape-only slot;
2. adds a blank portrait row once the last one is full (`withAutoGrownTrailingRow`),
   and does the same for an overridden `landscapeRows`;
3. pads `tiles` with blanks to cover every landscape slot.

`shownLandscapeRows` also extends past a too-small override at read time.
`Page.withGridSize()` never drops tiles. Shrinking therefore only hides trailing
*blank* tiles; rows stop at the last tile with content.

`Page.withTileMoved(fromIndex, toIndex)` indexes into the full `tiles` list, since
portrait and landscape each show a different-length prefix of it.

Both `withGridSize()` and `withTileMoved()` return `this` unchanged on invalid input
(out-of-range indices, no-op moves) rather than throwing — callers don't need
to pre-validate. `Board`'s own mutators (`withPageRemoved`, `withPageRenamed`,
`withCurrentPage`, `withPageMoved`) follow the same rule for out-of-range indices.

## Persistence

`BoardRepository` treats app-private storage as the source of truth:

- `filesDir/board.json` — the serialized `Board`, via `kotlinx.serialization`
  with `ignoreUnknownKeys = true`. That flag is what lets old boards (saved
  before `volume`/`colorArgb` existed) keep loading after a schema change —
  new fields just take their default. Anytime you add a field to `Tile`,
  `Page`, or `Board`, give it a default for the same reason.
  **This only covers additive changes.** Introducing `pages` required an
  actual structural migration — boards saved before pages existed are a flat
  `name`/`rows`/`columns`/`tiles` object with no `pages` key, and decoding
  that straight as the new `Board` would silently succeed with an *empty
  default* board (`pages` has a default too) instead of failing loudly.
  `BoardRepository.load()` guards against this by parsing to a `JsonObject`
  first and checking for a `"pages"` key: present, decode normally; absent,
  decode the legacy shape (`LegacyBoard`, private to `BoardRepository.kt`) and
  wrap it into a single `Page`. This is why `steve-care-board.zip` (the
  bundled fallback board, itself old-format when this migration was added)
  never needed regenerating — the migration runs on every `load()`, so it
  applies the moment the asset is imported. By contrast,
  `stickyHomeRowEnabled`, `Page.color`, and `Page.tileAspectRatio` needed **no** new branching logic
  in `load()` at all — they're additive fields onto an already-`pages`-shaped
  `Board`, so the ordinary `ignoreUnknownKeys` + defaults path handles them
  exactly like `volume`/`colorArgb` did originally.
  **`homePageIndex` is the one exception, and a second, different kind of
  migration.** It used to be a single board-level field; now it's derived
  from each page's own `isHome` (see the data-model section above).
  `ignoreUnknownKeys` only helps with fields that were *added* — a field that
  *moved* would just be silently dropped, since today's `Board` has nowhere
  to put a stray top-level `homePageIndex` key. `load()`'s
  `migrateHomePageIndex(board, root)` reads that raw key straight off the
  parsed `JsonObject` (the same one already extracted to check for `"pages"`)
  and calls `board.withHomePage(index)` if no page already claims to be home.
  It runs after *both* branches above — the pages-shaped decode and the
  `LegacyBoard` one — so an old board doesn't need to clear both migrations
  to get its home page back, just this one.
  **`sanitizeMissingSounds(board)` runs last, on every `load()`.** A tile's
  `fileName` is just a claim — nothing enforces that the file it names
  actually exists. A generic built-in board can legitimately ship with some or all
  clips unrecorded, and any zip import in general could be missing a file for
  other reasons. Rather than let a tile render as "filled" while silently
  doing nothing when tapped, `sanitizeMissingSounds` walks every page's tiles
  (the home page's, sticky-row-eligible or not, included — there's no
  separate list anymore) and resets `fileName` to `null` wherever `sounds/<fileName>`
  doesn't exist — same label, now honestly without a sound (`!hasSound`). This runs unconditionally
  on every load, not just right after an import, so it also self-heals a
  board whose sound file went missing some other way. It does not rewrite
  `board.json` — the fix-up is in-memory only, and gets persisted naturally
  the next time any ordinary mutation calls `commit()`.
- `filesDir/sounds/<uuid>.<ext>` — every picked audio file, copied in by
  `importSound()`. The UUID is generated at import time and has no relation
  to the tile's own `id`. Copying (instead of holding onto the picked
  content URI) is deliberate: content URIs can be revoked when the source
  app updates, uninstalls, or the user moves the file, silently breaking the
  tile. Copying also means the app never needs `READ_EXTERNAL_STORAGE`.

**`DevicePreferences` holds the settings deliberately *not* on `Board`.**
Everything else in Settings travels with the board on purpose (loading a
different board switches those too — see the settings-on-board comment atop
`Board`'s fields). Performance mode (disables tile shadows, tap ripples, and
the drag-reorder scale effect, see "The UI layer" below) describes the
device the app happens to be running on, not the board's content, so it
lives in ordinary `SharedPreferences` (`DevicePreferences`, a small
wrapper over a `KeyValueStore`) instead — opening a different board must not
silently turn it back off. `BoardViewModel` reads it once at construction
into its own `performanceModeEnabled: StateFlow<Boolean>`, separate from
`board`. Show mode (`ShowModeSettings`: on/off, display timer, tap to close,
mute, flip upside down) is there for the same reason — it's about where the board is being used —
and is exposed the same way as `showMode: StateFlow<ShowModeSettings>`.
`ShowModeSettings.withTimerSeconds`/`withTapToClose` refuse a change that
would leave neither a timer nor tap to close, and `DevicePreferences` repairs
such a combination on read, so the text screen can never strand anyone.

**Backup** (`exportTo/importFrom`) is just those two things zipped: `board.json`
at the zip root, every file under `sounds/` mirrored into a `sounds/` entry.
Both go through Storage Access Framework document pickers
(`CreateDocument`/`OpenDocument`), so no storage permission is needed here
either. A backup is built and read whole, in memory, rather than streamed, since
that's what the web can do too; boards with a few hundred short clips are a few
MB. `importFrom()` only takes files that land directly in their own folder (an
entry like `sounds/../board.json` is skipped), and fails on a file that isn't a
zip rather than seeming to work. It overwrites `board.json` and merges files into
`sounds/` — it does not clear `sounds/` first, so an import after manually
adding stray files there could leave orphans; in practice the app is the only
thing that ever writes there, so this hasn't mattered.

## Saved boards

**Saved boards and Backup solve different problems and deliberately don't share a
mechanism**, even though a saved board's content is exactly a `Board`. Backup is
the *portable, self-contained* one — a zip carrying its own audio, meant to
survive a reinstall or move to another device. A **saved board** is *same-device
version history* — you're saving a snapshot of your own board's layout to
come back to later, on this device, while its sound files are still sitting
in the shared `sounds/` directory you already have.

`SavedBoardRepository` stores each saved board as its own small file,
`filesDir/presets/<uuid>.json` — literally just `Board` JSON, no new schema.
(The folder keeps its name from when saved boards were called presets, so
existing ones still show up; `recent_presets.json` likewise.)
Once `isHome` moved onto `Page` (above), `Board` already was exactly the
shape a saved board needs: pages, names, order, home page, dimensions, and every
tile's label/sound-reference/volume/color. Critically, **a saved board does
not copy any audio** — `Tile.fileName` stays a bare filename resolved against
whatever `sounds/` directory it's loaded into, so a saved board just
references the same files the live board already uses. This keeps a save
cheap (a few KB of JSON, not a copy of every recorded clip) but means a saved
board **cannot survive a reinstall or cleared app data on its own** — that
wipes `sounds/` too, leaving the saved board's `fileName`s pointing at nothing.
Backup is still what you'd use for that. `SavedBoardRepository.list()` scans
`presets/*.json` directly rather than keeping a separate index file, so the
list can never drift from what's actually on disk, and skips (rather than
crashes on) a file that fails to decode.

**This creates one sharp edge `BoardViewModel.commit()` has to guard
against.** `commit()`'s existing job is pruning `sounds/` files no longer
referenced by the live board (`repo.pruneUnused(after)`). Once a saved board
can reference a file the *live* board no longer does, that same prune would
silently delete audio a saved board still needs — clearing a tile today,
opening that saved board tomorrow, and finding it silently plays nothing.
`commit()` folds `savedBoardRepo.allReferencedFileNames()` (every `fileName`
across every saved board) into the keep-set before pruning:
`repo.pruneUnused(after + savedBoardRepo.allReferencedFileNames())`. This is the
one correctness-critical piece of the whole feature.

`BoardViewModel.saveBoardAs(name)` also renames the live board to `name` —
the save dialog already prompts for a name defaulting to the current board
name, so this absorbed what used to be a separate "Save" (rename-only) menu
action rather than keeping both. `openBoard(ref)` takes a `BoardRef`,
which is either `Saved(id)` (loaded via `SavedBoardRepository.load()`, then
`commit()`ed like any other board mutation) or `BuiltIn(assetName, label)`
(the two bundled zips, still going through `BoardRepository.importFromAsset()`
exactly as before — they're self-contained zips, not lightweight JSON, so
they don't need `SavedBoardRepository` at all). `builtInBoards()` builds
that built-in list by asking `BoardRepository.hasAsset(assetName)` — Steve's
entry simply doesn't appear when its asset isn't packaged (debug builds
only), the same practical availability the old `BuildConfig.DEBUG`-gated menu
item had, without importing `BuildConfig` (or a `Context`) into the ViewModel.

`Board.hasAnySound` (any tile on any page with a `fileName`)
gates the UI's confirm dialog before opening another board over a board that has
real content — same reasoning, and same shape, as the page-delete confirm
(`requestDeletePage`) already uses.

## Audio playback

`SoundPlayer` hides two different Android playback APIs behind one
`load(key, file)` / `play(key, volume)` interface:

- **SoundPool** for files at or under `longClipThresholdBytes` (300,000 bytes
  by default). Decoded fully into memory on `load()`; playback is instant with
  no per-tap decode cost. `load()` is async — a clip won't play until
  SoundPool's load-complete callback marks it `ready`, which is normally
  imperceptible but means a clip can briefly be a no-op right after import.
- **MediaPlayer** for anything bigger — backing tracks, long recordings.
  Nothing is pre-decoded; a fresh `MediaPlayer` is created per play, prepared
  asynchronously, and released either on completion or when superseded.

`play()` picks the path by checking which map (`soundIds` vs. `longClips`)
the key is in — that decision was made once, in `load()`, based on file size.

**Playback is exclusive across both paths.** `stopActive()` runs at the top
of every `play()` call: it stops the tracked SoundPool stream (if any) and
stops+releases the tracked `MediaPlayer` (if any), before starting the new
clip. So tapping a tile always cuts off whatever was playing, whether that
was a short clip or a long one.

`clear()` vs. `release()`: `clear()` unloads every clip without releasing the
underlying `SoundPool` object, so the player can keep being used —
`BoardViewModel.importBoard()` calls this before reloading from a freshly
imported board, so stale keys from the previous state can't linger.
`release()` is the real teardown, called once from `onCleared()`.

## Audio recording

`AudioRecorder` wraps `MediaRecorder`, encoding to AAC in an MPEG-4 (`.m4a`)
container — `SoundPlayer` branches on file size, not extension, so a
recorded clip plays back through the exact same `SoundPool`/`MediaPlayer`
path as an imported one, no conversion needed.

The flow, split between `EditTileDialog` (permission + button state) and
`BoardViewModel` (the actual recording):

1. Tapping **Record** checks `RECORD_AUDIO` via `ContextCompat.checkSelfPermission`
   first; if it's not granted, a `rememberLauncherForActivityResult(RequestPermission())`
   asks for it and only starts recording once granted, showing an inline
   error otherwise. This lives in the UI layer because permission requests
   need an `Activity` context a `ViewModel` shouldn't hold.
2. `vm.startRecording()` calls `repo.newRecordingPath()` for a fresh
   UUID-named `.m4a` path in `sounds/` — the same directory `importSound()`
   writes into — then `recorder.start(path)`, which resolves it to a real file
   through `FileSystemStore.file()`. The path is tracked as
   `pendingRecordingPath` and `_isRecording` flips true, which is what turns
   the dialog's button into `Stop (Ns)`.
3. Tapping **Stop** calls `vm.stopRecording(tileId)` (the same call for a
   tile edited via the sticky home row banner), which stops the recorder, `player.load()`s the
   resulting file, and writes `fileName` onto the tile through the normal
   `updateTile`/`commit()` path — recording is assigned exactly as
   immediately as picking a file is, not gated behind the dialog's Save
   button.
4. A failed `MediaRecorder.stop()` (thrown when too little audio was
   captured to finalize the file — e.g. tapping Stop instantly after Record)
   is treated as failure: the partial file is deleted and the tile is left
   untouched, with a "Recording failed" message.
5. **Nothing commits a recording that isn't explicitly stopped.**
   `vm.cancelRecording()` — called from the dialog's `onDismiss` and from the
   page-switch effect that closes a `PageTile` dialog (auto-return can fire
   mid-recording) — stops the recorder without touching the tile and deletes
   the abandoned file. It's a no-op when nothing is recording, so it's safe
   to call unconditionally on every dialog exit path (Save, Cancel, or
   dismiss).

## Dependency injection

`BoardViewModel` takes its collaborators as constructor parameters instead of
building them:

```kotlin
class BoardViewModel(
    private val repo: BoardRepository,
    private val player: Player,
    private val recorder: Recorder,
    private val savedBoardRepo: SavedBoardRepository,
    private val speaker: Speaker,
    private val devicePrefs: DevicePreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel()
```

`BoardViewModel.Factory(application)` builds the real `BoardRepository`,
`SoundPlayer`, `AudioRecorder`, `SavedBoardRepository`, `TtsSpeaker`, and
`DevicePreferences` and is what `BoardScreen` passes to
`viewModel(factory = ...)`. Tests construct `BoardViewModel` directly
instead, passing a real `BoardRepository`, `SavedBoardRepository`, and
`DevicePreferences` (against a Robolectric or instrumented context — file I/O
and `SharedPreferences` are cheap enough not to fake), a `FakePlayer` in
place of `SoundPlayer`, a `FakeRecorder` in place of `AudioRecorder`, and a
`FakeSpeaker` in place of `TtsSpeaker`. `ioDispatcher` defaults to
`Dispatchers.IO` in production; tests pass an `UnconfinedTestDispatcher` so
the persistence coroutine in `commit()` (below) runs synchronously instead of
racing a real background thread.

## The single write path

Every mutation — rename, assign sound, clear, resize, reorder, volume,
color — ends up calling `BoardViewModel.commit(board)`:

```kotlin
private fun allTiles(board: Board): List<Tile> = board.pages.flatMap { it.tiles }

private fun commit(board: Board) {
    val before = allTiles(_board.value).mapNotNull { it.fileName }.toSet()
    val after = allTiles(board).mapNotNull { it.fileName }.toSet()
    _board.value = board

    (before - after).forEach { player.unload(it) }

    viewModelScope.launch(ioDispatcher) {
        repo.save(board)
        repo.pruneUnused(after + savedBoardRepo.allReferencedFileNames())
    }
}
```

It diffs referenced file names before/after **across every page, not just the
current page** — a sound assigned on a page you're not viewing (including the
home page's sticky-row-eligible first row) must still survive pruning —
unloads anything that fell out of the referenced set, updates the in-memory
state immediately (so the UI never waits on disk I/O), then persists on
`ioDispatcher`. The keep-set folds in `savedBoardRepo.allReferencedFileNames()`
too — see "Saved boards" above for why a saved board's audio needs the same
protection a live tile's does. `loadSounds()` (called on initial load and
after import) uses
the same `allTiles()` helper to preload everything for the same reason:
`SoundPool` needs a clip decoded before it can play regardless of which page
is visible when the app starts. Adding a new mutation to a single page means
building the next `Board` via `_board.value.updatingCurrentPage { ... }` and
calling `commit()`; a sticky-home-row-banner edit does the same against
`_board.value.updatingPage(homePageIndex) { ... }` — either way, not inventing
a new write path.

**Drag-reorder is the one deliberate exception.** Calling `commit()` on every
pointer-move frame during a drag would mean dozens of disk writes per
gesture, for a plain reordering where `before == after` every time (no file
names actually change). Instead:

- `previewMove(from, to)` just sets `_board.value` to the current page
  `withTileMoved(from, to)` — updates the UI instantly, touches nothing on disk.
- `commitOrder()` — called once, from the drag's `onDragEnd` — runs the
  current board through `commit()` as normal.

If you add another feature that fires rapidly (a color picker with
continuous drag, for instance), follow the same split: cheap live preview
in `_board.value`, one `commit()` at the end. The volume slider already does
this via Compose's own `onValueChangeFinished`, which only fires once per
gesture.

## The UI layer

`BoardScreen` is the screen-level `@Composable`; the top bar, grid and each
group of dialogs live in their own files next to it (see "Layers" above), and
repeated pieces (a label with a switch, helper text, a color swatch row, an
"override the board's setting" switch) come from `ui/Controls.kt` rather than
being rebuilt in each dialog. A few things worth knowing if you're touching it:

- **Only one dialog is ever open, so it's one piece of state.** Every dialog is
  modal, so `BoardScreen` keeps a single `openDialog: BoardDialog?` instead of
  a show-flag or page index per dialog. The page-scoped variants carry the
  index of the page they were opened for. `showDialog()` is also where a dialog
  that lists something from disk (the Open board picker, unused-clip cleanup)
  refreshes that list first.

- **The active board's name lives in the title, not a separate label.**
  `TopAppBar`'s `title` is a two-line `Column`: "Soundboard" (the app) in
  `labelSmall`, then `board.name` in `titleLarge`. It's the only place the
  active board is identified, so a board with no name of its own reads as
  "New Board" rather than blank. There's no dedicated rename-only menu item —
  **Save board as...** (below) prompts for a name and renames the board to
  match as a side effect, which absorbed what used to be a separate **Save**
  action.
- **The hamburger menu is settings and same-device history now, not page
  management.** Its old emoji-style items were replaced with Material icons:
  every `DropdownMenuItem` gets a `leadingIcon` with `contentDescription =
  null`, since the adjacent `Text` already labels it, while an icon-only
  control with no adjacent label — the menu button itself, or the tab row's
  Add-page tab below — carries a real `contentDescription` instead. Top to
  bottom, the menu holds: **Edit mode** and **Show mode**, each a
  `MenuSwitchRow` (`Icon` + label + `Switch`) rather than a `DropdownMenuItem`
  since a switch doesn't fit that composable's trailing-content slot cleanly; **Settings**, which opens
  `SettingsDialog` (below), including the **Sticky home row** switch, rather
  than exposing its contents as more menu rows; **Boards**
  (**Rename board**, **Recent boards**, **Save board as...**/**Open board...**); **Backup** (**Export backup**/
  **Import backup**); and the app-version link at the bottom. Page-level
  actions — add, rename, delete, grid size, page color, and home-page
  selection — live in the tab row and `PageOptionsDialog` instead; see below.
- **Settings is a list of groups, each its own dialog.** `SettingsDialog` shows
  one row per `SettingsGroup` (Home page, Look, Tile labels, Grid layout,
  Tapping & speech, Screen, Show mode) with a summary of its current values; picking one
  opens `SettingsGroupDialog` via `BoardDialog.SettingsGroupDetail(group)`,
  whose Back (and the system back gesture) reopens the list and whose Done
  closes Settings (#169). This keeps any one screen short instead of one long
  scroll of unrelated controls.
- **Settings holds board-wide preferences that aren't page content**
  — open on home page, auto-return timeout (`IDLE_TIMEOUT_OPTIONS_MINUTES`,
  `0` means "Off"), long-press duration, theme, background, default tile
  opacity/border, row height, label style, landscape layout and so on. They
  are fields on `Board` itself, so they travel with the board and persist
  through `commit()` like any other board edit. The exceptions are
  **Performance mode** and **Show mode**, which describe the device rather than
  the board and live in `DevicePreferences` (`SharedPreferences`). The group dialog takes the
  `Board` plus a `BoardSettingsActions` (the ViewModel) rather than a value
  and a callback per setting. Unlike `editMode`, which lives entirely in
  Compose state, all of these need to survive process death.
- **Save board as...**/**Open board...** are the same-device version-history
  actions (see "Saved boards" above), kept visually grouped and separate from
  **Export backup**/**Import backup**. **Save board as...** opens
  `TextInputDialog` against `vm.saveBoardAs()`. **Open board...** calls
  `vm.refreshSavedBoards()` then opens `OpenBoardDialog`, listing
  `vm.builtInBoards()` (bundled zips — Steve's only appears where
  its asset actually opens, i.e. debug builds) above `vm.savedBoards` (on-device
  saved snapshots, newest first). Picking one routes through
  `requestOpenBoard()`, mirroring `requestDeletePage()`'s shape: a confirm
  `AlertDialog` only when `board.hasAnySound`, otherwise `vm.openBoard()`
  runs immediately.
- **The last menu item is the app version, `APP_VERSION`** — built from
  `BuildConfig.VERSION_NAME`, so bumping `versionName` in
  `app/build.gradle.kts` is the only change a release needs (see
  `docs/RELEASING.md` for the full cut-a-release checklist). Tapping it fires
  an `ACTION_VIEW` intent at `RELEASE_URL`, opening a page in the browser;
  `RELEASE_URL` is `"$RELEASES_BASE_URL/tag/$APP_VERSION"`, landing on that
  specific version's own release notes rather than the bare `/releases`
  list. All three constants live at the top of `BoardTopBar.kt`.
- **Pages are a `PrimaryScrollableTabRow` under the `TopAppBar`, always
  shown even for a single page, plus a `HorizontalPager` driving the actual
  grid.** Both the app bar and tab row live inside one `Column` passed to
  `Scaffold`'s `topBar` slot; the pager fills the content area below the
  (optional) sticky home row banner. Three navigation paths all have to agree on
  `board.currentPageIndex`: tapping a `Tab` calls `vm.switchPage(index)`
  directly; swiping the pager is picked up by a
  `LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { vm.switchPage(it) } }`;
  and a separate `LaunchedEffect(board.currentPageIndex) { pagerState.animateScrollToPage(...) }`
  runs the sync the other direction, so a tab tap or the idle-timeout
  auto-return (below) animates the pager to match. `vm.switchPage()` —
  unlike every other `BoardViewModel` mutator — updates `_board.value`
  directly without going through `commit()`, since switching pages changes
  nothing that needs to reach disk. Each pager page renders its own
  `PageGrid(page, ..., isActive = pageIndex == board.currentPageIndex)`;
  drag-to-reorder's `pointerInput` is skipped entirely (`if (!isActive)
  return@pointerInput`) on any page that isn't the settled, on-screen one, so
  a gesture during a swipe transition can't mutate the wrong page. The row's
  permanent last entry is an icon-only `Tab` (`Icons.Filled.Add`,
  `contentDescription = "Add page"`) that opens the same `TextInputDialog`
  used elsewhere, against `vm.addPage()` — which is why the row can't just
  hide itself down to zero tabs. The home page's tab also renders
  differently from the rest — bold `Text` plus a small leading
  `Icons.Filled.Home` — so it reads as "home" without opening anything.
- **Long-pressing a page's tab opens `PageOptionsDialog`, independent of
  `editMode`.** The tab row's own `pointerInput` (see the Initial-pass note
  above) opens `BoardDialog.PageOptions(index)` on a long-press regardless of
  whether edit mode is on, unlike tile drag-reorder. `PageOptionsDialog` is
  where per-page management actually lives now: **Rename** (`TextInputDialog`
  → `vm.renamePage()`); **Set as home page** (`vm.setHomePage()`, `enabled =
  !isHome` — once a page is already home there's no "remove home" action,
  only setting a different page as the new home); **Grid size**, opening
  `GridSizeDialog` against `vm.resize()`/`vm.setTileAspectRatio()`; **Page
  appearance**, one `PageAppearanceDialog` for the page color plus its
  optional tile opacity and border overrides (`vm.setPageColor()`/
  `setPageOpacity()`/`setPageBorder()`); **Move
  left**/**Move right** (`vm.movePage()`, each disabled at its respective end
  of the page list); and **Delete page**, hidden entirely — not just
  disabled — when `board.pages.size == 1` (matching `Board.withPageRemoved()`'s
  own no-op-on-last-page behavior), and otherwise routing through
  `requestDeletePage()`, which shows a confirm dialog naming how many tiles
  have sounds before calling `vm.deletePage()`, or deletes immediately if the
  page is all-empty. Because the long-pressed page (the dialog's `pageIndex`) isn't
  necessarily the one on screen (`board.currentPageIndex`), `resize()`,
  `setTileAspectRatio()`, and `setPageColor()` all take an explicit page
  `index` and go through `Board.updatingPage(index)` rather than
  `updatingCurrentPage()`.
- **The sticky home row lives above the pager, rendered once — not once per
  page.** It only shows while `board.stickyHomeRowEnabled` is true, a home
  page exists, and the current page isn't the home page itself. It renders
  `homePage.tiles.take(homePage.columns)` — the home page's own first row —
  via the same `PinnedRow` composable the old board-wide pinned row used, the
  same "trailing entries hidden, not lost" idea `Page.visibleTiles` already
  uses for width. That's the first **portrait** row in both orientations (#153),
  so rotating never changes what's pinned: in landscape the same tiles stretch
  across the width at the page's standard row height, and the home page's own
  pinned row (`PageGrid`'s `pinFirstRow`) does the same, with the landscape
  grid continuing below from tile `columns`. Drags across that boundary map
  columns proportionally (`dragTargetIndex`), since the pinned row can hold
  fewer, wider tiles than the rows under it.
- **The tile editor is `BoardDialog.EditTile(tileId, fromStickyRow)`.** The
  tile is found by id on whichever page holds it (`Board.findTile`), and
  edited through the same `vm.setLabel`/... calls either way. It's plain
  Compose state, not part of `Board`. A `LaunchedEffect(board.currentPageIndex)`
  closes it on every page switch, but **only when `fromStickyRow` is false** —
  a page tile's editor would otherwise keep editing a tile no longer on
  screen, while a sticky-row tile stays visible whichever page is current.
- **Auto-return to home page.** `lastInteractionAt` is bumped by a local
  `recordInteraction()` call from every meaningful interaction (tile tap, tab tap, a
  settled swipe) rather than from a low-level raw-pointer listener,  so only
  real interactions with the board reset the countdown. A
  `LaunchedEffect(lastInteractionAt, board.homePageIndex, idleTimeoutMinutes)`
  returns immediately if `idleTimeoutMinutes` (from `SettingsDialog`, default
  5) is `0`, otherwise `delay()`s that many minutes and, if nothing has
  restarted it since and a home page is set, calls `vm.switchPage(homeIndex)`
  — restarting the effect is what "resets the timer," since a new key value
  cancels the previous coroutine before it can fire. Keying the effect on
  `idleTimeoutMinutes` too means changing the setting mid-session restarts
  the countdown under the new duration rather than waiting for the next
  interaction.
- **Tile shape and color are page properties, not global constants.**
  `TileCard` takes `aspectRatio`/`pageColor` as parameters instead of a
  hardcoded `1f` and a hardcoded `primaryContainer`; `GridSizeDialog` has a
  Square/Wide toggle next to the rows/columns steppers (`vm.setTileAspectRatio()`,
  `Page.WIDE_TILE_ASPECT_RATIO` — 4:3 — for "Wide"). A `Tile`'s own `colorArgb` always overrides `pageColor`,
  and `pageColor` only applies to **filled** tiles — an empty tile keeps the
  neutral "add a sound here" look regardless of the page's accent.
- **Tap vs. edit are different gestures on purpose.** A tile's `Card` uses
  `combinedClickable(onClick = onTap)` with no `onLongClick` — long-press is
  reserved entirely for drag-reorder (`detectDragGesturesAfterLongPress` in a
  separate `pointerInput`). The corner pencil isn't a separate tap target
  anymore (a permanent nested `clickable` there used to crowd small tiles);
  `BoardScreen` holds a top-level `editMode` boolean toggled from the `☰`
  menu, `TileCard` renders the pencil purely as a visual indicator whenever
  `editMode` is true, and `onTap` checks `!tile.isPlayable(...) || editMode`
  to decide whether a tap on the whole card edits or plays.
- **A labeled-but-empty tile is visually distinct from a plain blank one.**
  `TileCard`'s `needsRecording = !tile.hasSound && tile.label.isNotBlank()`
  renders a small 🔇 glyph in the opposite corner from the edit-mode pencil.
  This is the state a generic built-in board leaves a tile in after
  `BoardRepository.sanitizeMissingSounds()` (see "Persistence") clears a
  `fileName` with nothing behind it — the label still shows (rather than a
  bare "+"), so the board reads as "labeled, still needs a recording"
  instead of either "filled" or "totally empty." Tapping it opens the edit
  dialog exactly like any other empty tile (unless the board's
  speak-unrecorded-tiles setting makes it speak its label instead).
- **Drag math.** `cellStepPx` (cell size + spacing, in pixels) is computed
  from the grid's measured width (`Modifier.onSizeChanged`) divided by column
  count. During a drag, the accumulated offset is converted to a row/column
  delta by dividing by `cellStepPx` and rounding; crossing a full cell calls
  `vm.previewMove()` and then subtracts that cell's worth of offset back out,
  so the dragged tile keeps tracking the finger smoothly across multiple
  cell-crossings in one gesture. Non-dragged items get `Modifier.animateItem()`
  so they slide into their new slot instead of jump-cutting.
- **The home page's own first row can render split from the rest of its
  grid.** When `pinFirstRow` (`page.isHome && board.stickyHomeRowEnabled`)
  and the page has more than one row, `PageGrid` renders a plain `Row` for
  `visibleTiles.take(columns)` above a `LazyVerticalGrid` for the rest,
  instead of one grid for everything — so the first row stays fixed while the
  remainder scrolls beneath it, matching what the sticky banner already shows
  on every other page (#83). This needed no change to reordering itself:
  `draggedIndex`/`dragOffset` are shared closure state at the top of
  `PageGrid`, and the drag math above is pure index arithmetic with no idea
  which visual container a tile is in — a tile is "pinned" purely by
  occupying one of the first `columns` slots in `visibleTiles`, so dragging
  one across the row/grid boundary just works. The one seam:
  `Modifier.animateItem()` only exists inside a `LazyGridItemScope`, so it
  can't apply to the fixed `Row` — a reorder crossing that boundary pops
  instead of sliding, while one that stays within either zone still animates.
- **Arming a drag is announced, not just shown.** `onDragStart` fires
  `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`
  alongside the visual lift (scale + shadow, skipped when Performance mode is
  on) — a hesitant press that accidentally armed reorder is obvious
  immediately rather than only once the tile visibly moves. The page-tab
  long-press that opens `PageOptionsDialog` fires the same feedback constant
  when it arms, for the same reason.
- **Color and contrast.** A custom `colorArgb`, or failing that a filled
  tile's page color, overrides the card's container color. Text/icon color
  for either comes from `textColorFor()`, a local helper that picks black or
  white from the color's own luminance — not Material3's `contentColorFor()`,
  which only resolves a real color when the background exactly matches a
  theme role and otherwise silently falls back to the ambient theme text
  color (light in dark mode), producing light text on a light custom tile.
  `textColorFor()` sidesteps that by never depending on the current theme at
  all.
- **Show mode is an overlay, not a screen.** Board taps and long-press
  previews call `vm.activate(tile)`, which sets `shownText` (the tile's
  `speechText`) when Show mode is on and calls `play()` unless it mutes sounds;
  the tile editor's own Play button still calls `play()` directly, so it never
  opens the text screen. `BoardScreen` draws `ShowTextOverlay` over the whole
  `Scaffold` while `shownText` is set, so closing it lands on the same page
  with nothing to navigate back to. The overlay always installs a
  `pointerInput`, even with tap to close off, since an overlay without one
  lets touches fall through to the tiles underneath. The auto-return idle timer
  is held off while it's up. Text size comes from the same
  `largestFittingSize`/`breaksInsideWord` search tile labels use, not
  Compose's `TextAutoSize`, which only checks height and will happily split
  "doctor" across two lines.

## Threading

- File I/O (`repo.save`, `repo.pruneUnused`, `repo.load`, export/import) runs
  on `ioDispatcher` (`Dispatchers.IO` in production), launched from
  `viewModelScope`.
- `SoundPlayer` calls (`load`, `play`, `unload`, `clear`) are cheap enough to
  call directly from the main thread — `pool.load()`/`pool.play()` are
  non-blocking native calls, and `MediaPlayer.prepareAsync()` is async by
  name. None of `SoundPlayer`'s public methods should be made to block; if a
  future change needs synchronous prep, dispatch it explicitly rather than
  changing that contract.
- `_board` is a `MutableStateFlow`, always written from the main thread in
  this codebase (ViewModel functions aren't marked `suspend` except where
  they explicitly `launch`), so there's no cross-thread mutation to guard
  against today.

## Testing

| Layer | File(s) | Runs on |
|---|---|---|
| `Page.withGridSize()`/`.withTileMoved()`/`.normalized()`, tile opacity/border fallback, and the landscape grid defaults | `shared/src/commonTest/.../model/PageTest.kt` | JVM and WebAssembly (`kotlin.test`) |
| Landscape column/row-height math, label size search | `test/.../ui/GridLayoutTest.kt`, `test/.../ui/LabelTextTest.kt` | plain JVM (JUnit) |
| `Board` page management (`withPageAdded`/`withPageRemoved`/`withPageRenamed`/`withCurrentPage`/`withHomePage`), `updatingTile`/`findTile`, `soundFileNames`, `Tile.isPlayable`, and `hasAnySound` | `shared/src/commonTest/.../model/BoardTest.kt` | JVM and WebAssembly (`kotlin.test`) |
| `BoardRepository`'s own logic — save/load, missing-sound sanitizing, backup round-trip, the zip path guard, bundled boards — plus `SavedBoardRepository` basics, against in-memory storage | `shared/src/commonTest/.../data/BoardRepositoryCommonTest.kt` | JVM and WebAssembly (`kotlin.test`) |
| The `FileStore` contract every implementation must meet; `JavaZipCodec` reading every zip in `presets/` | `shared/src/commonTest/.../data/FileStoreContractTest.kt` (in-memory store), `shared/src/androidHostTest/.../data/AndroidStorageTest.kt` (`FileSystemStore`, `JavaZipCodec`) | JVM and WebAssembly / JVM |
| `BoardRepository` wired to real files, zips and assets, incl. the legacy-schema and `homePageIndex` migrations, additive-field defaults in `load()`, and every shipped built-in board | `test/.../data/BoardRepositoryTest.kt` | Robolectric |
| `SavedBoardRepository` — save/list/load round-trip, `allReferencedFileNames()`, corrupt-file resilience | `test/.../data/SavedBoardRepositoryTest.kt` | Robolectric |
| `BoardViewModel`, incl. editing a home-row tile from another page, cross-page/saved-board orphan pruning, record/stop/cancel, and saveBoardAs/openBoard | `test/.../BoardViewModelTest.kt` | Robolectric, `MainDispatcherRule` + `FakePlayer` + `FakeRecorder` |
| `BoardScreen`, incl. the sticky home row, swipe navigation, and idle-timeout auto-return | `androidTest/.../ui/BoardScreenTest.kt` | Compose UI test, real device/emulator |
| Landscape grid, row height cap, drag steps, label size/font/caps, large font scale — real rotation and real layout | `androidTest/.../ui/LayoutAndLabelTest.kt` | Compose UI test, real device/emulator |

`./gradlew test :shared:allTests` runs everything except the two `androidTest/`
classes (`test` alone skips `shared`, which has no task by that name);
`./gradlew connectedAndroidTest` runs those against a connected device or
emulator. In CI, the **CI** workflow runs the former and gates merging. The
separate **UI tests** workflow (`.github/workflows/ui-tests.yml`) runs the
latter on an API 35 emulator for every PR, and on demand via **Run workflow**.
It's advisory, not a merge gate, because emulators on shared runners
occasionally flake on timing-sensitive gestures. Its HTML report is uploaded as
the `ui-test-report` artifact. `LayoutAndLabelTest` rotates the device itself
(`UiAutomation.setRotation`) and restores portrait afterwards.

- **`FakePlayer`** (a `Player`) exists twice — once under `test/`, once under
  `androidTest/` (`UiTestFakes.kt`) — since those source sets don't share code by default. Keep
  both in sync if `Player`'s contract changes. **`FakeRecorder`** (a
  `Recorder`) follows the same split, though the `androidTest/` copy is a
  bare stub (`BoardScreenTest` doesn't exercise recording — see the known
  limitations below) rather than a full call-recording fake.
- **`MainDispatcherRule`** sets `Dispatchers.Main` to an
  `UnconfinedTestDispatcher` for the duration of a test, since
  `viewModelScope` has no `Main` dispatcher on a plain JVM. Every
  `BoardViewModelTest` needs it (`@get:Rule`).
- **Robolectric needs a version that supports the project's `targetSdk`.**
  `testOptions.unitTests.isIncludeAndroidResources = true` is also required —
  without it, Robolectric silently fails to find app resources.
- **`BoardRepository.save()` swallows failures** (`runCatching`). A
  persistence test that passes suspiciously easily is worth double-checking
  by asserting the write actually happened, not just that no exception was
  thrown.
- **Long-press in `BoardScreenTest` opens nothing** — long-press drives
  drag-to-reorder, not the edit dialog (see the UI layer section above); to
  open the edit dialog on a filled tile in a test, toggle "Edit mode" from
  the `☰` menu first, same as a real user would.
- **Testing drag-to-reorder itself needs raw pointer events, not `adb shell
  input`.** `adb`'s synthetic `swipe` interpolates movement from the very
  first frame, tripping touch-slop cancellation before the long-press timeout
  fires. `BoardScreenTest.longPressDragReordersTiles` instead drives
  `performTouchInput { down(...); advanceEventTime(600); moveBy(...); up() }`
  directly — holding position past the long-press timeout before moving,
  same as a real long-press-then-drag — and measures the actual on-screen
  cell width from tile semantics bounds rather than hardcoding a pixel
  offset.
- **`Tile`/`Board` default constructor args generate random UUIDs.** Two
  freshly-constructed default `Board`s are never equal; compare on labels/file
  names or pass explicit `id`s in fixtures.

## Known limitations / things to check before extending

- **`longClipThresholdBytes` (300 KB) is a constant, not user-configurable.**
  A very "hot" short clip just over the line will use MediaPlayer's slightly
  higher per-tap latency; there's no UI to override this per-tile.
- **Backup import doesn't clear `sounds/` first**, so importing over a board
  that has files an old export didn't include won't remove them. Not
  currently reachable through normal use (the app is the only writer to that
  directory), but worth knowing if you build a "manage backups" feature that
  juggles multiple exports.
- **Pager swipe vs. long-press drag-to-reorder is resolved via an explicit
  lock, not gesture-priority alone.** `PageGrid` reports drag state up
  through `onDragActiveChanged`, and `BoardScreen` feeds it into
  `HorizontalPager(userScrollEnabled = !isDragActive)` — so once a tile is
  armed for reorder, the pager simply can't consume horizontal movement
  until the drag ends or cancels. This sidesteps relying on Compose's
  child-before-ancestor gesture consumption for a case (dragging a tile
  across the full page width) where that alone wasn't verified to hold up.
- **There's no "Delete saved board" action.** Every `saveBoardAs()` call writes a
  new file under `presets/` and nothing ever removes one. This is low-risk in
  practice — each saved board is a few KB of JSON, not a copy of any audio —
  but old, no-longer-wanted saves will accumulate indefinitely until a delete
  action is added.
- **Recording is untested against a real microphone.** `BoardViewModelTest`
  covers the start/stop/cancel state machine against `FakeRecorder`, but
  nothing exercises `AudioRecorder` against actual `MediaRecorder` I/O —
  emulators' virtual mic is often silent by default, so even a passing
  instrumented test wouldn't confirm real voice quality or latency. Verify
  on a physical device before relying on this for an actual care board.
- **A permanently denied `RECORD_AUDIO` permission has no recovery path.**
  `EditTileDialog` shows an inline "permission is needed" message and lets
  the user try again, but Android stops showing its own permission dialog
  after a second denial — there's no "open Settings" deep link, so a user
  who denies twice can't record without leaving the app manually.
