# Test presets

The app doesn't have a separate "preset" concept — a preset is just a backup
zip in the format `BoardRepository.exportTo()`/`importFrom()` produces:
`board.json` at the zip root plus every referenced sound under `sounds/`.
Anything built to that shape can be loaded with the app's **Import** button.

## `care-board.zip` — "Steve Draft Care Board"

Four pages — **Trouble**, **Needs** (home), **Talking**, **Well Wishes** —
plus a pinned row (Hey / Something's wrong / Call the doctor / 911, in red)
fixed above every page's own 6-row x 4-column grid. Tiles are "Wide"
(4:3) rather than square, and each page carries its own color identity
(Trouble amber, Needs blue, Talking neutral, Well Wishes green) so which
page is active is visible at a glance. This is the layout described in the
original board-layout planning doc.

Content came together in two passes: an initial pass reused 41 of the 50
recordings from an earlier flat draft, redistributed onto the new page
structure. A second pass pulled from the full ~92-clip recording batch (the
complete script, not just the 50 that had originally been tiled) and filled
in nearly everything that pass had to leave blank — **69 of the ~92
recorded clips are on a tile somewhere in this board.** Trouble, Needs, and
the pinned row are now **fully recorded** (every non-blank tile has real
audio); Talking is recorded except for "Not that", which has no matching
clip yet.

**Well Wishes still intentionally ships with only two labeled tiles** ("Mom",
"Dad") and no audio on either — those are placeholders for messages *Mom
and Dad themselves* record for the patient, not anything in the recording
batch (which is all patient-voice utility phrases). The "Get Mom"/"Get Dad"
summoning clips are a different thing entirely and live on Trouble.

**Recorded but with no slot in this layout** (matches the planning doc's own
"not tiled yet" list almost exactly): warmth (`good_morning`,
`thank_you_really`, `sorry_and_thanks`, `im_okay_2` as "I'm okay (calm)"),
humour (`laugh_short` as "Ha (short)", `very_funny`, `rude`, `you_wish`,
`i_heard_that`, `stop_it`, `okay_okay`, `wonderful_sarcastic`), ordinary life
(`how_was_your_day`, `tell_me_about_it`, `whats_going_on`, `come_watch` as
"Watch with me", `music` as "Put music on", `smells_good`, `better_today`,
`much_better`), and two with no obvious slot at all (`not_what_i_meant`,
`put_something_on`). `get_NAME.wav`/`get_NAME2.wav` are also unused — they're
generic templates superseded by the already-recorded, already-named "Get
Mom"/"Get Dad" clips. None of these are deleted; they're candidates for a
fifth page if they turn out to matter, not swapped in over what's here.
One judgment call worth flagging: `thank_you_2.wav`/`thank_you_3.wav` aren't
labeled by take, so "Thanks (quick)" got `thank_you_2` and `thank_you_3`
was left deferred as "Thanks (warm)" — swap the tile's fileName if that's
backwards.

Its `board.json` carries the name **"Steve Draft Care Board"** (the
`Board.name` field), so once loaded it's clearly labeled — not mistaken for
a finished or generic board — wherever the app shows the active board's
name (currently the top bar's title; see
[docs/USER_GUIDE.md](../docs/USER_GUIDE.md#what-board-is-loaded)).

**It auto-loads in debug builds.** A copy lives at
`app/src/debug/assets/care-board.zip` (keep it in sync with this one — both
the zip's bytes and its `board.json`'s `name` field), and `BoardViewModel`
imports it on a fresh install — whenever `board.json` doesn't exist yet,
i.e. before the app has ever saved anything. Release builds carry no such
asset, so `repo.importFromAsset()` silently no-ops there and the app starts
with the normal empty, unnamed 4x4 board. Once *any* board gets saved —
including this auto-import — it's never triggered again; uninstall (or
clear app data) to see it re-trigger.

To load it by hand instead (e.g. onto a build that already has a saved
board):

1. Get the zip onto the device/emulator, e.g. `adb push presets/care-board.zip /sdcard/Download/`.
2. In the app, tap **Import** in the top bar and pick the file.

**Heads up:** Import fully replaces the current board — export first if you
want to keep what's there.
