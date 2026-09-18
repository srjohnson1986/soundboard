# Architecture

MVVM over a single mutable `Board`, with one function (`BoardViewModel.commit()`)
as the sole path to disk. Everything else is Compose reacting to a `StateFlow`.

## Layers

| File | Responsibility |
|---|---|
| `model/Board.kt` | `Tile`, `Page`, and `Board` data classes; resize, visibility, reorder, and page-management logic. No Android dependencies. |
| `data/BoardRepository.kt` | Reads/writes `board.json`, copies picked audio into app storage, zips/unzips backups. All file I/O. |
| `audio/Player.kt` | Interface (`load`/`play`/`unload`/`clear`/`release`) that `BoardViewModel` depends on. The seam that lets tests substitute a fake instead of real audio. |
| `audio/SoundPlayer.kt` | Real `Player` implementation: owns the `SoundPool` and the current `MediaPlayer`. No knowledge of `Board` or `Tile`. |
| `audio/Recorder.kt` | Interface (`start`/`stop`/`cancel`) that `BoardViewModel` depends on for recording — same fake-in-tests seam as `Player`. |
| `audio/AudioRecorder.kt` | Real `Recorder` implementation: owns a `MediaRecorder`, encoding straight to a file `BoardRepository` hands it. |
| `BoardViewModel.kt` | Holds the `Board` as a `StateFlow`, wires the other four together, single write path. Takes `BoardRepository`/`Player`/`Recorder`/dispatcher as constructor params (see below) rather than constructing them. |
| `ui/BoardScreen.kt` | Compose UI: pinned row, per-page swipeable grid (`HorizontalPager`), drag-to-reorder, edit dialog, grid-size/save/page/color dialogs, top bar showing the active board's name, and a tab row for switching pages. |
| `MainActivity.kt` | Just sets content to `SoundboardTheme { BoardScreen() }`. |

Data flows one way: UI calls a `BoardViewModel` function → it updates
`_board` (a `MutableStateFlow<Board>`) → Compose recomposes because
`BoardScreen` collects `board` with `collectAsStateWithLifecycle()`. The UI
never touches `BoardRepository` or `SoundPlayer` directly.

## The data model

```kotlin
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val fileName: String? = null,   // null = empty tile
    val volume: Float = 1f,
    val colorArgb: Int? = null      // null = use the theme default
)

data class Page(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Page 1",
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = List(16) { Tile() },
    val tileAspectRatio: Float = 1f,  // width:height; e.g. 4f/3f for wider-than-tall
    val color: Int? = null            // page-identity accent; null = theme default
)

data class Board(
    val name: String = "New Board",
    val pages: List<Page> = listOf(Page()),
    val currentPageIndex: Int = 0,
    val pinnedTiles: List<Tile> = emptyList(),  // shown above every page, identical everywhere
    val homePageIndex: Int? = null              // auto-return target; null disables it
)
```

A board is one or more `Page`s, each an independent grid, switched via tabs
in the UI. `Board.name` identifies the whole board (shown in the title bar);
each `Page.name` identifies just that tab; `Page.color` is that tab's own
identity accent, distinct from `Tile.colorArgb` (a tile's own color always
wins). `name`, `currentPageIndex`, `pinnedTiles`, and `homePageIndex` are the
board-level state that isn't grid geometry or page tiles —
`BoardViewModel.renameBoard()`/`switchPage()`/pinned-tile mutators/`setHomePage()`
are their write paths, and none of them need a dedicated persistence concept
since they're just more fields in `board.json`. `Board.currentPage` resolves
the active `Page` (clamping `currentPageIndex` defensively);
`Board.updatingCurrentPage { transform }` is how every tile/grid mutation
reaches it without the caller handling the page list itself. `addPage()`,
`removePage()` (a no-op on the last remaining page; also clears or shifts
`homePageIndex` if it pointed at or past the removed page), `renamePage()`,
and `switchTo()` round out page management, all returning a new `Board` like
every other mutator here.

**`pinnedTiles` is deliberately on `Board`, not `Page`.** The whole point is
a row identical on every page — putting it on `Page` would mean N independent
copies to keep in sync by hand. `BoardViewModel` mirrors the five page-tile
mutators (`setLabel`/`setVolume`/`setColor`/`assignSound`/`clearTile`) as
pinned-scoped equivalents (`setPinnedLabel`, etc.) operating on
`Board.pinnedTiles` directly instead of `updatingCurrentPage`.
`BoardViewModel.resize()` grows `pinnedTiles` to match a page's new column
count whenever it's resized wider (never shrinks it — same
never-drop-a-tile rule as `Page.resized()`); `addPinnedRow()` is the one-time
action that materializes the row in the first place (a no-op once it exists).

**Invariant: `tiles.size` is always `>= rows * columns`, per page.**
`Page.resized()` only ever grows the list; shrinking the grid just lowers
`rows`/`columns`; the now-hidden tiles stay in `tiles`, unreachable except
through `visibleTiles = tiles.take(rows * columns)`. Growing back reveals
them again. This is why `BoardScreen` renders `board.currentPage.visibleTiles`,
not `.tiles` — rendering the full list would show hidden tiles that shrinking
was supposed to tuck away.

`Page.moved(fromIndex, toIndex)` is the same idea applied to drag-reorder: it
only ever touches the first `rows * columns` entries, leaving hidden tiles in
place at the end of the list.

Both `resized()` and `moved()` return `this` unchanged on invalid input
(out-of-range indices, no-op moves) rather than throwing — callers don't need
to pre-validate. `Board`'s own mutators (`addPage`, `removePage`, `renamePage`,
`switchTo`) follow the same rule for out-of-range indices.

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
  bundled fallback preset, itself old-format when this migration was added)
  never needed regenerating — the migration runs on every `load()`, so it
  applies the moment the asset is imported. By contrast, `pinnedTiles`,
  `homePageIndex`, `Page.color`, and
  `Page.tileAspectRatio` needed **no** new branching logic in `load()` at
  all — they're additive fields onto an already-`pages`-shaped `Board`, so
  the ordinary `ignoreUnknownKeys` + defaults path handles them exactly like
  `volume`/`colorArgb` did originally. The structural-migration branch above
  only exists for the one field (`pages` itself) that changed the JSON's
  *shape* rather than just adding to it.
- `filesDir/sounds/<uuid>.<ext>` — every picked audio file, copied in by
  `importSound()`. The UUID is generated at import time and has no relation
  to the tile's own `id`. Copying (instead of holding onto the picked
  content URI) is deliberate: content URIs can be revoked when the source
  app updates, uninstalls, or the user moves the file, silently breaking the
  tile. Copying also means the app never needs `READ_EXTERNAL_STORAGE`.

**Backup** (`exportTo/importFrom`) is just those two things zipped: `board.json`
at the zip root, every file under `sounds/` mirrored into a `sounds/` entry.
Both go through Storage Access Framework document pickers
(`CreateDocument`/`OpenDocument`), so no storage permission is needed here
either. `importFrom()` overwrites `board.json` and merges files into
`sounds/` — it does not clear `sounds/` first, so an import after manually
adding stray files there could leave orphans; in practice the app is the only
thing that ever writes there, so this hasn't mattered.

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
2. `vm.startRecording()` calls `repo.newRecordingFile()` for a fresh
   UUID-named `.m4a` path in `soundsDir` — the same directory `importSound()`
   writes into — then `recorder.start(file)`. The file is tracked as
   `pendingRecordingFile` and `_isRecording` flips true, which is what turns
   the dialog's button into `Stop (Ns)`.
3. Tapping **Stop** calls `vm.stopRecording(tileId)` (or `stopPinnedRecording`
   for the pinned row), which stops the recorder, `player.load()`s the
   resulting file, and writes `fileName` onto the tile through the normal
   `updateTiles`/`commit()` path — recording is assigned exactly as
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel()
```

`BoardViewModel.Factory(application)` builds the real `BoardRepository`,
`SoundPlayer`, and `AudioRecorder` and is what `BoardScreen` passes to
`viewModel(factory = ...)`. Tests construct `BoardViewModel` directly
instead, passing a real `BoardRepository` (against a Robolectric or
instrumented context — file I/O is cheap enough not to fake), a `FakePlayer`
in place of `SoundPlayer`, and a `FakeRecorder` in place of `AudioRecorder`.
`ioDispatcher` defaults to `Dispatchers.IO` in production; tests pass an
`UnconfinedTestDispatcher` so the persistence coroutine in `commit()` (below)
runs synchronously instead of racing a real background thread.

## The single write path

Every mutation — rename, assign sound, clear, resize, reorder, volume,
color — ends up calling `BoardViewModel.commit(board)`:

```kotlin
private fun allTiles(board: Board): List<Tile> = board.pages.flatMap { it.tiles } + board.pinnedTiles

private fun commit(board: Board) {
    val before = allTiles(_board.value).mapNotNull { it.fileName }.toSet()
    val after = allTiles(board).mapNotNull { it.fileName }.toSet()
    _board.value = board

    (before - after).forEach { player.unload(it) }

    viewModelScope.launch(ioDispatcher) {
        repo.save(board)
        repo.pruneUnused(after)
    }
}
```

It diffs referenced file names before/after **across every page plus the
pinned row, not just the current page** — a sound assigned on a page you're
not viewing (or on a pinned tile) must still survive pruning — unloads
anything that fell out of the referenced set, updates the in-memory state
immediately (so the UI never waits on disk I/O), then persists on
`ioDispatcher`. `loadSounds()` (called on initial load and after import) uses
the same `allTiles()` helper to preload everything for the same reason:
`SoundPool` needs a clip decoded before it can play regardless of which page
is visible when the app starts. Adding a new mutation to a single page means
building the next `Board` via `_board.value.updatingCurrentPage { ... }` and
calling `commit()`; a pinned-tile mutation does the same against
`_board.value.copy(pinnedTiles = ...)` — either way, not inventing a new
write path.

**Drag-reorder is the one deliberate exception.** Calling `commit()` on every
pointer-move frame during a drag would mean dozens of disk writes per
gesture, for a plain reordering where `before == after` every time (no file
names actually change). Instead:

- `previewMove(from, to)` just sets `_board.value = _board.value.moved(from,
  to)` — updates the UI instantly, touches nothing on disk.
- `commitOrder()` — called once, from the drag's `onDragEnd` — runs the
  current board through `commit()` as normal.

If you add another feature that fires rapidly (a color picker with
continuous drag, for instance), follow the same split: cheap live preview
in `_board.value`, one `commit()` at the end. The volume slider already does
this via Compose's own `onValueChangeFinished`, which only fires once per
gesture.

## The UI layer

`BoardScreen` is one `@Composable` function plus private helpers
(`PinnedRow`, `PageGrid`, `TileCard`, `EditTileDialog`, `ColorSwatch`,
`GridSizeDialog`, `PageColorDialog`, `TextInputDialog`, `Stepper`).
A few things worth knowing if you're touching it:

- **The active board's name lives in the title, not a separate label.**
  `TopAppBar`'s `title` is a two-line `Column`: "Soundboard" (the app) in
  `labelSmall`, then `board.name` in `titleLarge`. It's the only place the
  active board is identified, so a board with no name of its own reads as
  "New Board" rather than blank. The `☰` menu's **Save** item is unrelated
  to the auto-save every other mutation already gets — it exists solely to
  open a `TextInputDialog` and change this name via `vm.renameBoard()`.
  **Add page**/**Rename page** open the same `TextInputDialog` composable
  against `vm.addPage()`/`vm.renamePage()`; **Delete page** is hidden from
  the menu entirely when only one page remains (`Board.removePage()` is a
  no-op on the last page anyway, so hiding it just avoids a dead menu entry),
  and otherwise routes through `requestDeletePage()`, which checks
  `page.tiles.any { !it.isEmpty }` — a page with any sound assigned shows a
  confirm dialog naming how many tiles would be lost before calling
  `vm.deletePage()`, while an all-empty page deletes immediately. **Page
  color** opens `PageColorDialog` (the same swatch-picker `ColorSwatch` the
  tile-edit dialog uses) against `vm.setPageColor()`. **Add pinned row** only
  appears while `board.pinnedTiles` is empty, since `vm.addPinnedRow()` is a
  no-op afterward anyway. **Set as home page**/**Home page ✓** toggles
  `vm.setHomePage(board.currentPageIndex)`. **Load Jeremy test preset** is
  gated on `BuildConfig.DEBUG` (requires `buildFeatures.buildConfig = true`
  in `app/build.gradle.kts`) and calls `vm.importJeremyTestPreset()`, which
  imports `app/src/debug/assets/jeremy-care-board.zip` the same way the
  fallback board imports on a fresh install — it exists purely so testing
  in an emulator doesn't need `adb push` plus the file picker every time;
  it never appears, and the asset isn't even packaged, in a release build.
- **Pages are a `PrimaryScrollableTabRow` under the `TopAppBar`, shown only
  when there's more than one, plus a `HorizontalPager` driving the actual
  grid.** Both the app bar and tab row live inside one `Column` passed to
  `Scaffold`'s `topBar` slot; the pager fills the content area below the
  (optional) pinned row. Three navigation paths all have to agree on
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
  a gesture during a swipe transition can't mutate the wrong page.
- **The pinned row lives above the pager, rendered once — not once per
  page.** Since `Board.pinnedTiles` is the same list regardless of which page
  is showing, there is exactly one `PinnedRow` composable instance; it never
  needs to re-render on a page switch, only when the tiles themselves change.
  It's shown at `pinnedTiles.take(board.currentPage.columns)` width, the same
  "trailing entries hidden, not lost" idea `Page.visibleTiles` already uses.
- **`editingTarget: EditTarget?`** (a `PageTile(id)` or `PinnedTile(id)`
  sealed type) replaces a plain tile-id string precisely so the edit dialog
  knows which list to look the tile up in and which mutator group
  (`vm.setLabel`/... vs. `vm.setPinnedLabel`/...) to call. It's plain Compose
  state, not part of `Board`. A `LaunchedEffect(board.currentPageIndex)`
  resets it to `null` on every page switch, but **only when it's a
  `PageTile`** — a `PinnedTile` dialog left open survives a page switch
  cleanly, since pinned tiles don't belong to any one page in the first
  place.
- **Auto-return to home page.** `lastInteractionAt` is bumped by a local
  `touch()` call from every meaningful interaction (tile tap, tab tap, a
  settled swipe) rather than from a low-level raw-pointer listener,  so only
  real interactions with the board reset the countdown. A
  `LaunchedEffect(lastInteractionAt, board.homePageIndex)` `delay()`s
  `IDLE_TIMEOUT_MS` (5 minutes) and, if nothing has restarted it since and a
  home page is set, calls `vm.switchPage(homeIndex)` — restarting the effect
  is what "resets the timer," since a new key value cancels the previous
  coroutine before it can fire.
- **Tile shape and color are page properties, not global constants.**
  `TileCard` takes `aspectRatio`/`pageColor` as parameters instead of a
  hardcoded `1f` and a hardcoded `primaryContainer`; `GridSizeDialog` has a
  Square/Wide toggle next to the rows/columns steppers (`vm.setTileAspectRatio()`,
  4f/3f for "Wide"). A `Tile`'s own `colorArgb` always overrides `pageColor`,
  and `pageColor` only applies to **filled** tiles — an empty tile keeps the
  neutral "add a sound here" look regardless of the page's accent.
- **Tap vs. edit are different gestures on purpose.** A tile's `Card` uses
  `combinedClickable(onClick = onTap)` with no `onLongClick` — long-press is
  reserved entirely for drag-reorder (`detectDragGesturesAfterLongPress` in a
  separate `pointerInput`). The corner pencil isn't a separate tap target
  anymore (a permanent nested `clickable` there used to crowd small tiles);
  `BoardScreen` holds a top-level `editMode` boolean toggled from the `☰`
  menu, `TileCard` renders the pencil purely as a visual indicator whenever
  `editMode` is true, and `onTap` checks `tile.isEmpty || editMode` to decide
  whether a tap on the whole card edits or plays.
- **Drag math.** `cellStepPx` (cell size + spacing, in pixels) is computed
  from the grid's measured width (`Modifier.onSizeChanged`) divided by column
  count. During a drag, the accumulated offset is converted to a row/column
  delta by dividing by `cellStepPx` and rounding; crossing a full cell calls
  `vm.previewMove()` and then subtracts that cell's worth of offset back out,
  so the dragged tile keeps tracking the finger smoothly across multiple
  cell-crossings in one gesture. Non-dragged items get `Modifier.animateItem()`
  so they slide into their new slot instead of jump-cutting.
- **Arming a drag is announced, not just shown.** `onDragStart` fires
  `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`
  alongside the visual lift (scale + shadow) — a hesitant press that
  accidentally armed reorder is obvious immediately rather than only once the
  tile visibly moves. The page-tab long-press that opens `PageOptionsDialog`
  fires the same feedback constant when it arms, for the same reason.
- **Color and contrast.** A custom `colorArgb`, or failing that a filled
  tile's page color, overrides the card's container color. Text/icon color
  for either comes from `textColorFor()`, a local helper that picks black or
  white from the color's own luminance — not Material3's `contentColorFor()`,
  which only resolves a real color when the background exactly matches a
  theme role and otherwise silently falls back to the ambient theme text
  color (light in dark mode), producing light text on a light custom tile.
  `textColorFor()` sidesteps that by never depending on the current theme at
  all.

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
| `Page.resized()`/`.moved()` | `test/.../model/PageTest.kt` | plain JVM (JUnit) |
| `Board` page management (`addPage`/`removePage`/`renamePage`/`switchTo`/`withHomePage`) | `test/.../model/BoardTest.kt` | plain JVM (JUnit) |
| `BoardRepository`, incl. the legacy-schema migration and additive-field defaults in `load()` | `test/.../data/BoardRepositoryTest.kt` | Robolectric |
| `BoardViewModel`, incl. pinned-tile mutators, cross-page/pinned orphan pruning, and record/stop/cancel | `test/.../BoardViewModelTest.kt` | Robolectric, `MainDispatcherRule` + `FakePlayer` + `FakeRecorder` |
| `BoardScreen`, incl. the pinned row, swipe navigation, and idle-timeout auto-return | `androidTest/.../ui/BoardScreenTest.kt` | Compose UI test, real device/emulator |

`./gradlew test` runs the first three; `./gradlew connectedAndroidTest` runs
the Compose layer against a connected device or emulator.

- **`FakePlayer`** (a `Player`) exists twice — once under `test/`, once under
  `androidTest/` — since those source sets don't share code by default. Keep
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
- **Page reordering isn't supported** — `addPage()` always appends, so pages
  land in creation order with no way to move one later without deleting and
  re-adding it. Fine as long as pages are created in the order you want them
  to stay in.
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
