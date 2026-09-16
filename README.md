# Soundboard

A configurable grid of tiles. Tap a tile to play its sound, or to add one if
it's empty. Tap the pencil to rename it, replace its sound, adjust its volume
or colour, or clear it. Long-press and drag a tile to reorder the grid. No
runtime permissions required.

## Open it

1. Android Studio → **Open**, point at this folder.
2. Let Gradle sync. If it complains about plugin versions, bump them in the root
   `build.gradle.kts` and `compose-bom` in `app/build.gradle.kts` to whatever
   Studio suggests — the code itself doesn't depend on those exact numbers.
3. Run on a device or emulator.

## How it fits together

| File | Job |
|---|---|
| `model/Board.kt` | `Tile` and `Board` data classes, plus grid resize and reorder logic |
| `data/BoardRepository.kt` | Copies picked audio into app storage, reads/writes `board.json`, zips/unzips backups |
| `audio/SoundPlayer.kt` | SoundPool + MediaPlayer wrapper: load, play, unload, release |
| `BoardViewModel.kt` | Holds board state, single `commit()` write path |
| `ui/BoardScreen.kt` | Grid with drag-to-reorder, tile cards, edit dialog, grid-size dialog |

## Design notes

**Audio is copied, not referenced.** When you pick a file, it's copied to
`filesDir/sounds/<uuid>.<ext>` and the tile stores only that name. Content URIs
get revoked when the source app updates or the file moves, which would leave you
with tiles that silently stop working. Copying also means no storage permission.

**SoundPool for short clips, MediaPlayer for long ones.** Clips at or under
300 KB decode into memory via SoundPool, so taps fire instantly. Bigger files
(backing tracks, longer recordings) stream through MediaPlayer instead —
`SoundPlayer.load()` picks the path per file, `play()` doesn't care which one
a key uses.

**Playback is exclusive.** `SoundPlayer.play()` stops whatever was previously
playing — on either the SoundPool or MediaPlayer path — before starting the
new one. Tapping a tile always cuts off the last one instead of layering
sounds.

**One write path.** Every mutation goes through `commit()`, which diffs the
referenced file names, unloads anything orphaned, saves the JSON, and deletes
unused audio. Adding a feature means adding a function that calls `commit()`.
Dragging is the one exception: `previewMove()` reorders the in-memory board on
every frame without touching disk, and `commitOrder()` — called once, on
release — is what actually persists it through `commit()`.

**Shrinking hides, it doesn't delete.** `Board.tiles` only ever grows — the
grid size just controls how many of those tiles `visibleTiles` shows. Shrink
to 2x2 then back to 4x4 and the other 12 tiles, sounds included, are exactly
as you left them. `commit()` only unloads/deletes audio for a tile once it's
explicitly cleared, since that's the only way a file name drops out of the
full `tiles` list.

**Backup is just the same two things, zipped.** `BoardRepository.exportTo()`
zips `board.json` plus everything in `sounds/` via SAF's `CreateDocument`;
`importFrom()` reverses it via `OpenDocument`, overwriting both. After an
import the view model calls `SoundPlayer.clear()` (unloads everything without
releasing the underlying `SoundPool`) and reloads from the fresh board, so
stale keys from the old state can't linger.

**Volume and colour are just fields on `Tile`.** `volume` feeds straight into
`SoundPool.play()`/`MediaPlayer.setVolume()`; `colorArgb` overrides the card's
container color when set, with `contentColorFor()` picking readable text on
top of it. `ignoreUnknownKeys` means boards saved before these fields existed
still load fine.

## Testing notes

Everything above was exercised on an emulator except drag-to-reorder: the
long-press-then-drag gesture isn't something `adb shell input` can reproduce
faithfully (its `swipe` interpolates motion from the first frame, which trips
touch-slop cancellation before the long-press timeout ever fires), so that
one's only had a code review and a crash/no-crash smoke test — give it a real
touch test before relying on it.
