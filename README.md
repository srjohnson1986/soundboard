# Soundboard

A configurable grid of tiles. Tap a tile to play its sound, long-press to rename it,
replace its sound, or clear it. No runtime permissions required.

## Open it

1. Android Studio → **Open**, point at this folder.
2. Let Gradle sync. If it complains about plugin versions, bump them in the root
   `build.gradle.kts` and `compose-bom` in `app/build.gradle.kts` to whatever
   Studio suggests — the code itself doesn't depend on those exact numbers.
3. Run on a device or emulator.

## How it fits together

| File | Job |
|---|---|
| `model/Board.kt` | `Tile` and `Board` data classes, plus grid resize logic |
| `data/BoardRepository.kt` | Copies picked audio into app storage, reads/writes `board.json` |
| `audio/SoundPlayer.kt` | SoundPool wrapper: load, play, unload, release |
| `BoardViewModel.kt` | Holds board state, single `commit()` write path |
| `ui/BoardScreen.kt` | Grid, tile cards, edit dialog, grid-size dialog |

## Design notes

**Audio is copied, not referenced.** When you pick a file, it's copied to
`filesDir/sounds/<uuid>.<ext>` and the tile stores only that name. Content URIs
get revoked when the source app updates or the file moves, which would leave you
with tiles that silently stop working. Copying also means no storage permission.

**SoundPool, not MediaPlayer.** Clips decode into memory on load, so taps fire
instantly and overlap cleanly. `maxStreams` is 8. `load()` is async, so a clip
won't play until its load-complete callback has fired — normally imperceptible,
but worth knowing if a tile seems dead for the first split second after import.

**One write path.** Every mutation goes through `commit()`, which diffs the
referenced file names, unloads anything orphaned, saves the JSON, and deletes
unused audio. Adding a feature means adding a function that calls `commit()`.

## Worth adding next

- **Long clips.** SoundPool holds everything in memory. If you want backing
  tracks, add a `MediaPlayer` path for files over a few hundred KB and pick
  between them in `SoundPlayer.play()`.
- **Stop-all button.** Keep the stream IDs returned by `pool.play()` and call
  `pool.stop()` on each.
- **Drag to rearrange.** `LazyVerticalGrid` has no built-in reorder; the usual
  route is `Modifier.pointerInput` tracking drag offsets against item bounds.
- **Backup.** `board.json` plus the sounds folder is the whole state — zip both
  to an exported file and you have import/export.
- **Per-tile volume or colour.** Both are just extra fields on `Tile`;
  `ignoreUnknownKeys` means old saved boards still load.
