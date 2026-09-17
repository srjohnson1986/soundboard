# Architecture

MVVM over a single mutable `Board`, with one function (`BoardViewModel.commit()`)
as the sole path to disk. Everything else is Compose reacting to a `StateFlow`.

## Layers

| File | Responsibility |
|---|---|
| `model/Board.kt` | `Tile` and `Board` data classes; resize, visibility, and reorder logic. No Android dependencies. |
| `data/BoardRepository.kt` | Reads/writes `board.json`, copies picked audio into app storage, zips/unzips backups. All file I/O. |
| `audio/Player.kt` | Interface (`load`/`play`/`unload`/`clear`/`release`) that `BoardViewModel` depends on. The seam that lets tests substitute a fake instead of real audio. |
| `audio/SoundPlayer.kt` | Real `Player` implementation: owns the `SoundPool` and the current `MediaPlayer`. No knowledge of `Board` or `Tile`. |
| `BoardViewModel.kt` | Holds the `Board` as a `StateFlow`, wires the other three together, single write path. Takes `BoardRepository`/`Player`/dispatcher as constructor params (see below) rather than constructing them. |
| `ui/BoardScreen.kt` | Compose UI: grid, drag-to-reorder, edit dialog, grid-size dialog, top bar. |
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

data class Board(
    val rows: Int = 4,
    val columns: Int = 4,
    val tiles: List<Tile> = List(16) { Tile() }
)
```

**Invariant: `tiles.size` is always `>= rows * columns`.** `Board.resized()`
only ever grows the list; shrinking the grid just lowers `rows`/`columns`; the
now-hidden tiles stay in `tiles`, unreachable except through `visibleTiles =
tiles.take(rows * columns)`. Growing back reveals them again. This is why
`BoardScreen` renders `board.visibleTiles`, not `board.tiles` — rendering the
full list would show hidden tiles that shrinking was supposed to tuck away.

`Board.moved(fromIndex, toIndex)` is the same idea applied to drag-reorder:
it only ever touches the first `rows * columns` entries, leaving hidden tiles
in place at the end of the list.

Both `resized()` and `moved()` return `this` unchanged on invalid input
(out-of-range indices, no-op moves) rather than throwing — callers don't need
to pre-validate.

## Persistence

`BoardRepository` treats app-private storage as the source of truth:

- `filesDir/board.json` — the serialized `Board`, via `kotlinx.serialization`
  with `ignoreUnknownKeys = true`. That flag is what lets old boards (saved
  before `volume`/`colorArgb` existed) keep loading after a schema change —
  new fields just take their default. Anytime you add a field to `Tile` or
  `Board`, give it a default for the same reason.
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

## Dependency injection

`BoardViewModel` takes its collaborators as constructor parameters instead of
building them:

```kotlin
class BoardViewModel(
    private val repo: BoardRepository,
    private val player: Player,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel()
```

`BoardViewModel.Factory(application)` builds the real `BoardRepository` and
`SoundPlayer` and is what `BoardScreen` passes to `viewModel(factory = ...)`.
Tests construct `BoardViewModel` directly instead, passing a real
`BoardRepository` (against a Robolectric or instrumented context — file I/O
is cheap enough not to fake) and a `FakePlayer` in place of `SoundPlayer`.
`ioDispatcher` defaults to `Dispatchers.IO` in production; tests pass an
`UnconfinedTestDispatcher` so the persistence coroutine in `commit()` (below)
runs synchronously instead of racing a real background thread.

## The single write path

Every mutation — rename, assign sound, clear, resize, reorder, volume,
colour — ends up calling `BoardViewModel.commit(board)`:

```kotlin
private fun commit(board: Board) {
    val before = _board.value.tiles.mapNotNull { it.fileName }.toSet()
    val after = board.tiles.mapNotNull { it.fileName }.toSet()
    _board.value = board

    (before - after).forEach { player.unload(it) }

    viewModelScope.launch(ioDispatcher) {
        repo.save(board)
        repo.pruneUnused(after)
    }
}
```

It diffs referenced file names before/after, unloads anything that fell out
of the referenced set, updates the in-memory state immediately (so the UI
never waits on disk I/O), then persists on `ioDispatcher`. Adding a new
mutation means adding a function that builds the next `Board` and calls
`commit()` — not inventing a new write path.

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
(`TileCard`, `EditTileDialog`, `ColorSwatch`, `GridSizeDialog`, `Stepper`).
A few things worth knowing if you're touching it:

- **Tap vs. edit are different gestures on purpose.** A tile's `Card` uses
  `combinedClickable(onClick = onTap)` with no `onLongClick` — long-press is
  reserved entirely for drag-reorder (`detectDragGesturesAfterLongPress` in a
  separate `pointerInput`). There's no per-tile edit affordance (it used to be
  a corner pencil icon, but that crowded small tiles); instead `BoardScreen`
  holds a top-level `editMode` boolean toggled from the `☰` menu, and `onTap`
  checks `tile.isEmpty || editMode` to decide whether a tap edits or plays.
- **Drag math.** `cellStepPx` (cell size + spacing, in pixels) is computed
  from the grid's measured width (`Modifier.onSizeChanged`) divided by column
  count. During a drag, the accumulated offset is converted to a row/column
  delta by dividing by `cellStepPx` and rounding; crossing a full cell calls
  `vm.previewMove()` and then subtracts that cell's worth of offset back out,
  so the dragged tile keeps tracking the finger smoothly across multiple
  cell-crossings in one gesture. Non-dragged items get `Modifier.animateItem()`
  so they slide into their new slot instead of jump-cutting.
- **Colour and contrast.** A custom `colorArgb` overrides the card's
  container color; `contentColorFor()` (Material3) picks a readable
  text/icon color against it automatically, so custom colours never need a
  matching text-color field.

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
| `Board.resized()`/`.moved()` | `test/.../model/BoardTest.kt` | plain JVM (JUnit) |
| `BoardRepository` | `test/.../data/BoardRepositoryTest.kt` | Robolectric |
| `BoardViewModel` | `test/.../BoardViewModelTest.kt` | Robolectric, `MainDispatcherRule` + `FakePlayer` |
| `BoardScreen` | `androidTest/.../ui/BoardScreenTest.kt` | Compose UI test, real device/emulator |

`./gradlew test` runs the first three; `./gradlew connectedAndroidTest` runs
the Compose layer against a connected device or emulator.

- **`FakePlayer`** (a `Player`) exists twice — once under `test/`, once under
  `androidTest/` — since those source sets don't share code by default. Keep
  both in sync if `Player`'s contract changes.
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
