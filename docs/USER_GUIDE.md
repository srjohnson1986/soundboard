# User Guide

## The grid

Each tile is either empty (shows a **+**) or filled (shows its label, or
"Unnamed" if you haven't given it one).

- **Tap an empty tile** — opens the edit dialog so you can give it a sound.
- **Tap a filled tile** — plays its sound. Tapping any other tile while one
  is playing stops the first and starts the new one; only one clip plays at
  a time.
- **Long-press and drag a tile** — reorders the grid. Drop it over another
  tile to swap positions; everything else slides out of the way as you drag.

## Editing a tile

Turn on **Edit mode** from the **☰** menu in the top bar. While it's on,
tapping any filled tile opens the edit dialog instead of playing its sound;
turn it back off to return to normal play mode. Empty tiles always open the
edit dialog on tap, edit mode or not.

With the edit dialog open:

- **Name** — the label shown on the tile. Blank is fine; filled tiles with
  no label show "Unnamed".
- **Choose sound / Replace sound** — opens the system file picker filtered to
  audio files. Whatever you pick is copied into the app's own storage, so
  moving or deleting the original file afterward won't break the tile.
- **Volume** — per-tile playback volume, 0 to full.
- **Colour** — seven swatches (including "none", which uses the default
  theme colour). Tap one to apply it immediately — no need to hit Save.
- **Clear tile** — removes the sound and label, turning it back into an
  empty **+** tile.

Tap **Save** to keep name/sound changes, or **Cancel** to discard them.
Volume and colour changes apply as soon as you pick them, regardless of
Save/Cancel.

## Resizing the grid

Tap the **N x M** button in the top bar, adjust rows/columns with the +/-
steppers, and tap **Apply**.

Shrinking the grid doesn't delete anything — it just hides the tiles that no
longer fit, starting from the end. Grow the grid back to the same size (or
larger) and they reappear exactly as you left them, sounds included. The
only way to actually lose a tile's sound is to clear it directly.

## Backup

Use **Export** / **Import** in the top bar to back up or restore your whole
board — every tile, label, colour, volume, and sound file — as a single zip.

- **Export** opens the system "save file" picker. Pick a location and name
  (it defaults to `soundboard-backup.zip`) and confirm.
- **Import** opens the system file picker filtered to zip files. Pick a
  backup made by Export, and it replaces your current board and sounds
  entirely.

A Snackbar confirms success or failure at the bottom of the screen.

**Heads up:** Import is a full replace, not a merge — whatever's on your
board now is gone once the new one loads. Export first if you want to keep
it.

## Long clips

There's no length limit on what you can assign to a tile. Clips play
instantly either way; very short clips (under ~300 KB) may overlap more
cleanly if you tap rapidly, but this is an internal detail and not something
you need to manage — pick whatever file you want and it'll work.
