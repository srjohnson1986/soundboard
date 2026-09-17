# User Guide

## What board is loaded

The top bar always shows which board is active: "Soundboard" in small text,
with the board's own name underneath (e.g. "Steve Draft Care Board" for the
bundled fallback preset — see [presets/README.md](../presets/README.md)). A
fresh board with no name of its own is called "New Board" until you rename
it.

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

Turn on **Edit mode** from the **☰** menu in the top bar. While it's on, a
small pencil appears on every tile as a reminder, and tapping any filled
tile opens the edit dialog instead of playing its sound; turn it back off
to return to normal play mode. Empty tiles always open the edit dialog on
tap, edit mode or not.

With the edit dialog open:

- **Name** — the label shown on the tile. Blank is fine; filled tiles with
  no label show "Unnamed".
- **Choose sound / Replace sound** — opens the system file picker filtered to
  audio files. Whatever you pick is copied into the app's own storage, so
  moving or deleting the original file afterward won't break the tile.
- **Volume** — per-tile playback volume, 0 to full.
- **Color** — seven swatches (including "none", which uses the default
  theme color). Tap one to apply it immediately — no need to hit Save.
- **Clear tile** — removes the sound and label, turning it back into an
  empty **+** tile.

Tap **Save** to keep name/sound changes, or **Cancel** to discard them.
Volume and color changes apply as soon as you pick them, regardless of
Save/Cancel.

## Resizing the grid

Open the **☰** menu, tap **Grid size (N x M)**, adjust rows/columns with the
+/- steppers, pick a tile shape (see below), and tap **Apply**.

Shrinking the grid doesn't delete anything — it just hides the tiles that no
longer fit, starting from the end. Grow the grid back to the same size (or
larger) and they reappear exactly as you left them, sounds included. The
only way to actually lose a tile's sound is to clear it directly.

**Tile shape** — **Square** (the default) or **Wide**. A grid with more rows
than columns tends to need Wide so two-line labels fit without wrapping to a
cramped third line. This is a per-page setting, alongside rows/columns.

## Pages

A board can hold more than one page — each its own grid of tiles, with its
own name and color, switched by swiping left/right or tapping the tabs that
appear under the top bar once there's more than one. Use pages to group
sounds (e.g. "Requests", "Feelings", "People") instead of cramming everything
onto one grid.

- **Add page** (in the **☰** menu) — prompts for a name and switches to the
  new, empty page.
- **Rename page** — renames whichever page is currently showing.
- **Page color** — pick an accent for the page you're on; it tints that
  page's tab and its filled tiles (a tile's own color, if it has one, always
  wins). One glance at the tab bar tells you which page you're on.
- **Delete page** — removes whichever page is currently showing, sounds and
  all. Only appears in the menu when there's more than one page, since a
  board always needs at least one.
- **Grid size** resizes only the page you're currently on — other pages keep
  their own dimensions.

Switching pages doesn't affect anything else: Edit mode stays as you left it,
and any page-tile dialog you had open closes rather than applying to the
wrong page.

### A pinned row that stays on every page

**Add pinned row** (in the **☰** menu, shown until you've added one) creates
a row of tiles that sits above the grid and stays fixed while you scroll or
switch pages — the same tiles, in the same place, no matter which page
you're on. It's meant for anything you need reachable no matter where the
board happens to be — a call for help, "something's wrong," a way to summon
someone. Editing a pinned tile from any page updates it everywhere, since
it's really one shared row, not a per-page copy. It grows automatically to
match a page's column count if you widen the grid; it never shrinks or loses
a tile on its own.

### Returning to a home page automatically

**Set as home page** (in the **☰** menu) marks whichever page you're on as
the one the board should return to on its own. After five minutes with no
taps, swipes, or drags, the board snaps back to that page — so it's never
found parked somewhere else, like a page meant for occasional use. The menu
item reads **Home page ✓** when you're already looking at the current home
page.

## Naming a board

Tile edits, grid size, and reordering all save automatically — there's
nothing extra to do for those. **Save** in the **☰** menu is specifically
for the board's *name*, the one shown in the top bar: tap it, edit the
**Board name** field (prefilled with the current name), and tap **Save**.
An empty name falls back to "New Board" rather than saving blank.

## Backup

Use **Export** / **Import** in the **☰** menu to back up or restore your
whole board — its name, every tile, label, color, volume, and sound file —
as a single zip.

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
