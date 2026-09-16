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

## Docs

- **[User guide](docs/USER_GUIDE.md)** — how to use the app: tiles, editing,
  resizing, drag-to-reorder, backup/restore.
- **[Architecture](docs/ARCHITECTURE.md)** — how the code is put together:
  layers, the data model, persistence, audio playback, the single write
  path, and known limitations. Read this before making changes.

Both are kept current as the app changes — if you add or change a feature,
update the relevant doc in the same change.
