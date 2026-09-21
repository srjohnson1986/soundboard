# User Guide

## What board is loaded

The top bar always shows which board is active: "Soundboard" in small text,
with the board's own name underneath (e.g. "Jeremy Draft Care Board" for the
bundled fallback preset — see [presets/README.md](../presets/README.md)). A
fresh board with no name of its own is called "New Board" until you save it
as a preset (see "Presets" below), which is also how you rename one.

## The grid

Each tile is either empty (shows a **+**), filled (shows its label, or
"Unnamed" if you haven't given it one), or labeled but without a sound yet —
shown with a small 🔇 in the corner. That last state is what a preset built
from labels alone (see "Presets" below) leaves a tile in before you've
recorded anything for it; tapping it opens the edit dialog just like any
other empty tile.

- **Tap an empty tile** — opens the edit dialog so you can give it a sound.
- **Tap a filled tile** — plays its sound. Tapping any other tile while one
  is playing stops the first and starts the new one; only one clip plays at
  a time.
- **Long-press and drag a tile** — reorders the grid. A haptic tick and a
  slight lift confirm the moment the drag arms, so a hesitant press that
  didn't quite mean to grab a tile is obvious right away, and dropping it
  back where it started leaves the board unchanged. Swiping between pages is
  disabled for as long as a tile is lifted, so crossing the screen mid-drag
  moves the tile instead of turning the page. Drop it over another tile to
  swap positions; everything else slides out of the way as you drag.

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
- **Play clip** — plays whatever sound is currently on the tile, so you can
  check it without leaving the edit dialog. Disabled on an empty tile, and
  while a recording is in progress.
- **Record** — records straight from the microphone instead of picking a
  file. The first tap asks for microphone permission if the app doesn't
  have it yet; after that, tapping **Record** starts capturing and the
  button turns into **Stop (Ns)**, counting up while it listens. Tapping
  **Stop** finishes the clip and assigns it to the tile immediately, the
  same as picking a file — use **Play clip** afterward to check it, and
  **Record** again to redo it if it's not right. Closing the dialog (Save,
  Cancel, or dismissing it) while still recording discards that in-progress
  clip rather than keeping a half-finished one.
- **Volume** — per-tile playback volume, 0 to full.
- **Color** — seven swatches (including "none", which uses the default
  theme color). Tap one to apply it immediately — no need to hit Save.
- **Clear tile** — removes the sound and label, turning it back into an
  empty **+** tile.

Tap **Save** to keep the name, or **Cancel** to discard it. Sound (whether
picked or recorded), volume, and color changes all apply immediately,
regardless of Save/Cancel — only the name is staged until you tap Save.

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
- **Delete page** — removes whichever page is currently showing. Only appears
  in the menu when there's more than one page, since a board always needs at
  least one. If the page has any tiles with sounds assigned, you'll be asked
  to confirm before it's gone for good; an all-empty page deletes right away.
- **Grid size** resizes only the page you're currently on — other pages keep
  their own dimensions.

Switching pages doesn't affect anything else: Edit mode stays as you left it,
and any page-tile dialog you had open closes rather than applying to the
wrong page.

### A sticky row that stays on every other page

**Sticky home row** (a switch in **Settings**) shows your home page's own
first row of tiles fixed above whichever other page you're looking at — the
same tiles, in the same place, no matter which page you're on. It's meant
for anything you need reachable no matter where the board happens to be — a
call for help, "something's wrong," a way to summon someone. There's nothing
extra to set up: it's just the top row of tiles you already have on your
home page, so editing one of those tiles from any page updates the real
tile, not a separate copy. It's hidden while you're actually on the home
page, since that row is already right there.

Turning it on or off never deletes anything — pages just show (or stop
showing) that row above their own content.

### Returning to a home page automatically

**Set as home page** (in the **☰** menu) marks whichever page you're on as
the one the board should return to on its own. After five minutes with no
taps, swipes, or drags, the board snaps back to that page — so it's never
found parked somewhere else, like a page meant for occasional use. The menu
item reads **Home page ✓** when you're already looking at the current home
page.

## Presets

Tile edits, grid size, and reordering all save automatically — there's
nothing extra to do for those. Presets are for something different:
snapshotting a whole board layout so you can come back to it, or start a new
board from a known-good one, without a file picker or leaving the app.

- **Save as preset** (in the **☰** menu) prompts for a name, then saves
  everything about the current board — its pages, their names and order,
  which page is home, grid size, and every tile's label, sound, volume, and
  color — as a new preset. The name you type also becomes the board's own
  name (the one shown in the top bar), so this replaces the old
  name-only-and-nothing-else "Save."
- **Load preset** shows every available preset: the bundled ones ("Factory")
  plus anything you've saved yourself, newest first. Picking one replaces
  your current board with it. If your current board has any sounds on it,
  you'll be asked to confirm first — export a backup beforehand if you want
  to keep what's there.

**A saved preset is same-device only.** It's a small file that points at the
sound files already on your device — it doesn't make its own copy of the
audio. That keeps saving fast and keeps you from running out of space no
matter how many you save, but it also means a saved preset can't survive
uninstalling the app or clearing its data (that wipes the sounds it points
at too). If you want a copy that survives that — or that you can move to
another device — use **Export** below instead. The two bundled factory
presets don't have this limitation, since their sounds ship inside the app
itself.

**A generic, mostly-unrecorded layout works fine too.** If you build (or are
handed) a backup zip whose board.json has real pages, names, and tile labels
but little or no actual audio, importing it (**Import backup**, below) works
exactly as well as importing a fully-recorded one — any tile whose sound
doesn't actually exist in the zip just loads as labeled-but-empty (the 🔇
tiles described above) instead of a broken "filled" tile that plays nothing.
That's the practical way to get a shareable starting layout: label every
tile up front, record what you have, and let whoever imports it record the
rest.

## Backup

Use **Export backup** / **Import backup** in the **☰** menu to back up or
restore your whole board — its name, every tile, label, color, volume, and
sound file — as a single, self-contained zip. Unlike a saved preset, a
backup carries its own copy of every sound file, so it survives moving to a
new device or reinstalling the app.

- **Export backup** opens the system "save file" picker. Pick a location and
  name (it defaults to `soundboard-backup.zip`) and confirm.
- **Import backup** opens the system file picker filtered to zip files. Pick
  a backup made by Export, and it replaces your current board and sounds
  entirely.

A Snackbar confirms success or failure at the bottom of the screen.

**Heads up:** Import is a full replace, not a merge — whatever's on your
board now is gone once the new one loads. Export first if you want to keep
it.

## App version

The bottom of the **☰** menu shows the app's version (e.g. `v0.1.0`). Tapping
it opens that version's own release notes on GitHub in your browser.

## Long clips

There's no length limit on what you can assign to a tile. Clips play
instantly either way; very short clips (under ~300 KB) may overlap more
cleanly if you tap rapidly, but this is an internal detail and not something
you need to manage — pick whatever file you want and it'll work.
