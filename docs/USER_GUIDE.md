# User Guide

## Finding your way around

The top bar shows the app's name ("Soundboard"), a row of page tabs, and the
**☰** menu. In landscape all three share one row to leave more room for tiles.

The board you're using is named in the **☰** menu's first item, **Rename
board (…)**. On a fresh install that's "Jeremy Draft Care Board", the
built-in board the app starts with (see [presets/README.md](../presets/README.md)).

The **☰** menu, top to bottom:

- **Boards:**
  - **Rename board**
  - **Recent boards**
  - **Save board as...**
  - **Open board...**
  - (see "Saving and opening boards")
- **Edit mode:** a switch (see "Editing a tile")
- **Show mode:** a switch (see "Show mode")
- **Speak...:** type any phrase and have it read aloud (see "Speaking")
- **Settings** (see "Settings")
- **Page options (…):** for the page you're on (see "Pages")
- **Backup:**
  - **Export backup**
  - **Import backup**
  - **Clean up unused clips**
  - (see "Backup")
- **App version:** the version number (see "App version")

## The grid

Each tile is one of three things:

- **Blank:** it shows a **+**.
- **Filled:** it has a sound, or is set to speak. It shows its label, or
  "Unnamed" if it doesn't have one.
- **Labeled but not recorded yet:** it has a small 🔇 in the corner. That's how
  a tile looks when a board arrives with labels but no audio for it (see
  "Saving and opening boards").

What your fingers do:

- **Tap a filled tile:** plays its sound or speaks its words. Tapping another
  tile while one is playing stops the first one; only one plays at a time.
- **Tap a labeled tile with no recording:** reads its label aloud. To have
  these open the editor instead, turn off **Settings → Tapping & speech →
  Speak label when there's no clip**.
- **Tap a blank tile:** opens the editor so you can give it a sound.
- **Press and hold a tile, then drag:** moves it. A small buzz and a slight
  lift show the tile is picked up. As you drag, the other tiles slide over to
  make room, and swiping between pages is paused so crossing the screen moves
  the tile instead of turning the page. Letting go where you started leaves
  everything as it was.
- **Press and hold without moving:** plays the tile as a preview (outside Edit
  mode).

To stop stray taps on empty spots from opening the editor, turn on **Settings →
Tapping & speech → Hide blank tiles**. Blank tiles then disappear, and can't be
tapped, until you turn on Edit mode.

## Editing a tile

Turn on **Edit mode** in the **☰** menu. While it's on, a small pencil shows
on every tile, and tapping any tile opens the editor instead of playing it.
Turn it off again to go back to normal use. Blank tiles open the editor
whether Edit mode is on or not.

In the editor:

- **Name:** the label shown on the tile.
- **Choose sound / Replace sound:** picks an audio file from the device. It's
  copied into the app, so moving or deleting the original later doesn't
  break the tile.
- **Remove sound:** takes the sound off the tile but keeps its name.
- **Speak the label instead:** makes the tile speak using the device's
  text-to-speech voice when it has no sound file.
- **What to say:** the words spoken aloud, if they should differ from the
  short name on the tile (e.g. the name "Water", saying "Could I have a
  glass of water, please?"). Leave it blank to speak the name.
- **Preview / Play clip:** hear the tile without closing the editor.
- **Record:** records from the microphone instead of picking a file:
  - The first time, the app asks for microphone permission.
  - While recording, the button reads **Stop (Ns)** and counts up.
  - Tapping **Stop** puts the new recording on the tile right away. Use
    **Play clip** to check it, and **Record** again to redo it.
  - Closing the editor while still recording throws that recording away.
- **Volume:** how loud this tile plays.
- **Color:** eight swatches. The first means "no color of its own".
- **Override opacity / Override border:** gives this one tile its own
  see-through level or outline, instead of the page's or board's.
- **Clear tile:** removes the sound, name and speech, turning it back into a
  blank **+** tile. Only shown on a tile that has a sound or speaks.

**Save** keeps the **Name** and **What to say** text; **Cancel** throws those
two away. Everything else (sound, recording, volume, color, speech switch,
overrides) takes effect the moment you change it.

## Speaking

**Speak...** in the **☰** menu opens a box where you can type any phrase and
tap **Speak** to hear it read aloud. It's for something no tile covers, and
it doesn't change the board.

## Show mode

Show mode puts a tile's words on screen for someone to read, instead of (or as
well as) hearing them. Turn it on or off with the **Show mode** switch in the
**☰** menu.

While it's on, tapping a tile, or holding one to preview it, fills the screen
with its words in large white text on black: its script if it has one,
otherwise its name. It doesn't apply to **Speak...** or while Edit mode is on.
Closing the text puts you back exactly where you were.

**Settings → Show mode** has its options:

- **Close the text after:** 3, 5, 10, 15 or 30 seconds, or Off.
- **Tap to close:** tapping anywhere on the text closes it early.
- **Mute sounds while showing:** shows the words without playing the tile's
  recording or speech. With this off, the sound plays too and finishes even if
  the text is closed early.

The timer and tap to close can't both be off, so the text can always be
closed. The **Back** button always closes it too.

## Pages

A board can have several pages, each its own grid with its own name. Use them
to group tiles (e.g. "Requests", "Feelings", "People"). Switch pages by
swiping left or right, or by tapping a tab. The home page's tab has a small
house and bold text.

- **Add a page:** tap the **+** tab at the end of the tab row and give it a
  name.
- **Page options:** press and hold a page's tab (or use **☰ → Page options**
  for the page you're on). It holds:
  - **Rename**
  - **Set as home page:** see "The home page" below.
  - **Grid size:** see "Grid size" below.
  - **Page appearance:**
    - **Page color:** tints the page's tab and its filled tiles. A tile's own
      color always wins.
    - **Tile opacity** and **Tile border:** optionally override the board-wide
      settings for just this page.
  - **Move left / Move right:** reorders the tabs.
  - **Delete page:** only there when the board has more than one page. If the
    page has any tiles with sounds, you'll be asked to confirm; an empty page
    is deleted right away.

How long you have to hold a tab is set in **Settings → Tapping & speech →
Long-press duration**.

Switching pages leaves Edit mode as it was. A tile editor you had open for a
tile on the page you left closes, so it can't change a tile you can no longer
see.

### Grid size

**Page options → Grid size** sets that page's size. Other pages keep their own.

- **Rows and columns:** the + and − buttons. Making a page smaller never
  deletes anything or hides a tile that has a sound or a label; rows stop
  shrinking at the last such tile, and only blank tiles past it are tucked
  away.
- **Custom landscape size:** see "Landscape" below.
- **Tile shape:** **Square** (the default) or **Wide**. Wide suits pages with
  more rows than columns, so two-line labels fit comfortably.
- **Max row height:** this page's own limit (see below), or the board's.

Tap **Apply** to save the changes.

**Max row height.** Rows never grow taller than a 4-column row of the same
tile shape (**Standard**), so a page with 1 or 2 columns gets wide bars
instead of giant squares. Set the limit for the whole board in **Settings →
Grid layout → Max row height**: **Short**, **Standard**, **Tall**, **Extra
tall**, or **No limit**. Pages with 4 or more columns are already under the
Standard limit, so only **Short** changes them.

**Landscape.** Turning the device sideways shows the same tiles in a wider
grid: twice the columns, and about half the rows plus one, so there are always
a few blank tiles ready to fill. Rows stay the same height as in portrait. To
choose a different landscape size for a page, turn on **Custom landscape
size** in its Grid size. A tile filled in landscape also shows up in portrait;
that page just gets another row. For the older behavior of adding columns until
4 rows fit on screen, set **Settings → Grid layout → Landscape layout** to
**Fit to screen**.

### The home page

**Page options → Set as home page** makes that page the board's home. It
gets a house on its tab, and turns on the features below. Settings for all
three are in **Settings → Home page**.

- **Auto-return:** after a few minutes with no taps or swipes (5 by default;
  1, 2 and 10 minutes are the other choices, or Off), the board goes back to
  the home page, so it's never found left on a page meant for occasional
  use.
- **Open on home page:** starts the app on the home page instead of wherever
  it was left.
- **Sticky home row:** shows the home page's first row of tiles fixed at the
  top of every other page. It's meant for what must always be within reach
  — a call for help, "something's wrong", a way to summon someone:
  - It's the same tiles, not copies, so editing one from any page changes
    the real tile.
  - It's hidden on the home page itself, where that row is already showing.
  - It's the same tiles in portrait and landscape; in landscape they stretch
    across the wider screen.
  - Turning it on or off never deletes anything; other pages just shift down
    a row.

## Settings

**Settings** (in the **☰** menu) is a short list of groups. Each shows a
one-line summary of its current values; tap one to change them, then **Back**
to return to the list or **Done** to close Settings.

- **Home page:** open on the home page, auto-return, and the sticky home row
  (see "The home page").
- **Look:**
  - Theme: system, light or dark
  - Background: a color or a picture
  - Board-wide tile opacity and border
- **Tile labels:** font, text size, bold and all caps (see "Label text").
- **Grid layout:** the grid size new pages start at, the max row height, and
  the landscape layout.
- **Tapping & speech:**
  - Speak a tile's name when it has no recording
  - Hide blank tiles outside Edit mode
  - How long to hold a page tab to open Page options
  - Haptic feedback (the small buzz)
- **Screen:**
  - Keep the screen from turning off while the board is open
  - Performance mode: turns off shadows, tap ripples and similar effects
    (and tile see-through and borders) to help older devices stay smooth
- **Show mode:** on or off, and how the text closes and whether sounds play
  (see "Show mode").

Everything except Performance mode and Show mode is saved with the board, so
switching boards switches these too. Those two belong to the device.

## Label text

**Settings → Tile labels** sets how every tile label looks on this board.

- **Font:**
  - Default (Roboto)
  - Roboto Condensed: fits longer phrases
  - Noto Serif
  - **Atkinson Hyperlegible** and **Lexend**: two fonts designed for easy
    reading. Both come with the app under the SIL Open Font License; their
    license texts are in `assets/licenses/`.
- **Text size:** a smallest and a largest size. All labels on a page share
  one size: the largest that still fits every tile on that page. Pages with
  fewer, bigger tiles get bigger text, and text never goes below the smallest
  size. A label too long even at the smallest size wraps onto up to 3 lines
  and ends in "…".
- **Bold** and **All caps:** for extra legibility.
- **Preview:** two sample tiles show your choices at both ends of the size
  range, a long phrase at the smallest size and a short word at the largest.
  They update as you change the settings.

## Saving and opening boards

Everything you change (tiles, pages, grid size, order, settings) saves by
itself; there's nothing to do for that. Saving a board *as* something is
different: it keeps a copy of the whole board so you can come back to it
later, or start a new board from a good one.

- **Save board as...** (☰ → Boards) asks for a name, then keeps a copy of the
  whole board, including every page, tile and setting. The board you're
  using takes that name too.
- **Open board...** lists the boards built into the app ("Built-in") and the
  ones you've saved, newest first. **Recent boards** lists the last five you
  opened or saved. Picking one replaces the board on screen; if the current
  board has any sounds on it, you're asked to confirm first. Export a backup first
  if you want to keep it.

**A saved board stays on this device.** It points at the sounds already on
the device instead of making its own copy of them. That keeps saving quick
and small, but a saved board can't survive uninstalling the app or clearing
its data, since that removes the sounds too. To keep a copy that survives
that, or to move a board to another device, use **Export backup**. Built-in
boards don't have this limit; their sounds come with the app.

**A layout with labels but little or no audio works fine.** Importing a
backup whose tiles have names but no recordings loads those tiles as
"labeled but not recorded yet" (the 🔇 tiles) rather than broken ones. That's
a handy way to share a starting layout: label every tile, record what you
can, and let whoever imports it record the rest.

## Backup

**Export backup** and **Import backup** (in the **☰** menu) save or restore
the whole board as a single zip file. The backup includes every page, tile,
setting, sound and background picture. Unlike a saved board, it carries its
own copy of every sound, so it survives reinstalling the app or moving to a
new device.

- **Export backup** asks where to save the file (the name starts as
  `soundboard-backup.zip`).
- **Import backup** asks you to pick a backup zip, then replaces the current
  board and its sounds with it.

**Heads up:** importing replaces everything. It doesn't merge, so whatever's
on the board now is gone once the backup loads. Export first if you want to
keep it.

**Clean up unused clips** finds sound files that no tile uses on the current
board or any saved board. For example, a recording replaced by a newer one
may still be taking up space. You can export them to a zip first and then
delete them, or just delete them.

A short message at the bottom of the screen confirms whether each of these
worked.

## App version

The bottom of the **☰** menu shows the app's version (e.g. `v0.3.0`). Tapping
it opens that version's release notes on GitHub. The newest version can
always be downloaded from
https://github.com/srjohnson1986/soundboard/releases/latest/download/soundboard.apk

## Long clips

There's no length limit on a tile's sound. Short clips are kept ready in
memory so they start instantly; longer ones are read from storage as they
play. Either way only one sound plays at a time, and you don't need to do
anything differently.
