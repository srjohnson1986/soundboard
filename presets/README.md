# Factory presets

The app has two kinds of preset (see `docs/ARCHITECTURE.md`'s "Presets"
section): lightweight, on-device saves made in-app via **Save as preset**
(just a small JSON snapshot, no audio of its own), and **factory presets** —
the zips in this folder. A factory preset is a full backup-shaped zip
(`board.json` at the root plus every referenced sound under `sounds/`, the
same format `BoardRepository.exportTo()`/`importFrom()` produce) bundled as
an app asset, so it's self-contained and works on a device that has never
recorded anything. All three zips here show up in the **☰** menu's **Load preset**
picker automatically, labeled "Factory," alongside whatever you've saved
yourself.

A factory preset (or any backup zip, via **Import backup**) doesn't need
every tile's clip actually present in `sounds/` — `BoardRepository.load()`
sanitizes any tile whose `fileName` has no backing file back to empty
(keeping its label), rather than leaving it "filled" with nothing playable.
That makes a layout-and-labels-only zip, with sparse or no recorded audio, a
legitimate way to build a generic starting template — see the "Presets"
section in `docs/USER_GUIDE.md`.

## `chimes/chime.wav`

A short (~2s) chime, kept here as the canonical source so it's easy to find
and swap later. Jeremy's, Sarah's, and Steve's boards all point their
Trouble page's "Chime" tile at a copy of this file (`sounds/chime.wav`
inside each preset zip) instead of shipping it as an unrecorded gap.
`tts-care-board.zip` is left as-is — it speaks the word "Chime" via
text-to-speech, same as every other tile there, and adding a real audio
file would break that preset's whole reason for existing (zero recording,
no `sounds/` directory at all).

## `steve-care-board.zip` — "Steve Draft Care Board"

Three pages — **Trouble**, **Needs** (home), **Talking** — plus a sticky
home row (Hey / Something's wrong / Call the doctor / 911, in red — the
home page's own first row) fixed above every other page's 6-row x 4-column
grid. Tiles are "Wide" (4:3) rather than square, and each page carries its
own color identity (Trouble amber, Needs blue, Talking neutral) so which
page is active is visible at a glance. This is the layout described in the
original board-layout planning doc, minus a fourth page, **Well Wishes**,
that shipped almost entirely unrecorded and was removed until it's properly
filled in.

Content came together in two passes: an initial pass reused 41 of the 50
recordings from an earlier flat draft, redistributed onto the new page
structure. A second pass pulled from the full ~92-clip recording batch (the
complete script, not just the 50 that had originally been tiled) and filled
in nearly everything that pass had to leave blank — **69 of the ~92
recorded clips are on a tile somewhere in this board.** Trouble and Needs
(including its sticky first row) are now **fully recorded** (every non-blank tile has real
audio); Talking is recorded except for "Not that", which has no matching
clip yet.

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

**Jeremy's board is the fallback for now instead (see below) — this one
doesn't auto-load on install.** Only one board can be the fallback, but it's
still bundled for convenience: a copy lives at
`app/src/debug/assets/steve-care-board.zip` (debug builds only).
`BoardViewModel.factoryPresets()` checks whether that asset actually opens,
so it appears in the **☰** menu's **Load preset** picker only where it's
packaged — debug builds — with no separate menu item or `BuildConfig` check
of its own needed. On a release build (or any build where the asset isn't
there), pushing the zip and using **Import backup** still works the same as
any other backup.

## `jeremy-care-board.zip` — "Jeremy Draft Care Board"

Same layout and tile-for-tile mapping as `steve-care-board.zip` above (same
three pages, same sticky home row, same colors, same "Wide" tiles, Needs as home)
built from a separate recording batch in Jeremy's own voice — same 92-clip
script as Steve's, filename-for-filename identical except for two extras
(see below).

**It's the fallback board on every fresh install, debug or release, for
now.** A copy lives at `app/src/main/assets/jeremy-care-board.zip` (keep it
in sync with this one — both the zip's bytes and its `board.json`'s `name`
field), and `BoardViewModel` imports it whenever `board.json` doesn't exist
yet, i.e. before the app has ever saved anything — release builds get this
too, not just debug. Once *any* board gets saved — including this
auto-import — it's never triggered again; uninstall (or clear app data) to
see it re-trigger.

Once it's the fallback, the normal way to get back to it on a board that's
already been saved is the **☰** menu's **Load preset**, which lists it as a
"Factory" entry ("Jeremy Draft Care Board") — no `adb push` or file picker
needed. Loading it over a board that has sounds asks for confirmation first.

Trouble and Needs (including its sticky first row) are fully recorded; Talking is missing
only "Not that", same gap as Steve's board. The same clips are deferred for
the same reasons (see the list above) — this batch is otherwise identical.

Three things differ from Steve's board:

- **No named contacts yet.** Steve's board reused real "Get Mom"/"Get Dad"
  recordings inherited from an earlier draft; Jeremy's batch only has the
  generic `get_NAME.wav`/`get_NAME2.wav` templates, so both "Get ___" tiles
  on Trouble use those as-is (literally placeholder audio) until real names
  get recorded.
- **`personal_message_jeremy.wav`** doesn't correspond to anything in the
  board-layout doc's tile list. It was tiled as "For you" on the since-removed
  Well Wishes page (see above); pruned along with that page for now.
- **`pain_worse_ALT_retake.wav`** is an alternate take of "Pain worse."
  The plain `pain_worse.wav` is what's on the tile; the alt take is left
  unused/spare, same treatment as Steve's ambiguous multi-take clips.

## `tts-care-board.zip` — "TTS Care Board"

Same three pages, layout, colors, and labels as `jeremy-care-board.zip`, but
built for zero recording: every tile with a label speaks it via on-device
text-to-speech (`speakLabel = true`, `fileName = null`) instead of playing a
clip. There's no `sounds/` directory in this zip at all — nothing to copy —
so it's a few KB instead of several megabytes.

Bundled in every build (not debug-only), so it always shows up in **Load
preset** as a way to try the full layout, or hand someone a working board,
without waiting on any recording.

Most tiles also carry a `Tile.ttsScript` — the actual sentence recorded for
that clip (e.g. "Water" speaks "Water, please.", "Meds not working" speaks
"The medicine isn't working."), pulled from the same recording script used
to record Jeremy's and Steve's audio, matched to each tile by the fileName
`jeremy-care-board.zip`'s corresponding tile uses. A few tiles have no real
sentence to assign and just speak their plain label instead: Trouble's
"Chime" (a physical sound, not a phrase), Trouble's two "Get ___" name
templates (the script leaves the name blank), and Talking's "Ha!" (a real
laugh, not a line). Because the script is matched by fileName rather than by
re-deriving from the label, it also carries over the one labeling quirk
noted above verbatim — Talking's "Thanks (quick)" speaks "Thank you." here
too, since that's what `thank_you_2.wav` actually says.

## `sarah-care-board.zip` — "Sarah (ElevenLabs) Care Board"

Same three pages, layout, colors, and sticky home row as `jeremy-care-board.zip`,
recorded with ElevenLabs' Sarah voice instead of a human. The recording
script for this batch (not checked into the repo) replaced the two generic
"Get ___" name templates with 5 specific contacts — nurse, husband, wife,
son, daughter — so Trouble's two old "Get ___" tiles plus its 3 already-blank
trailing tiles (it has 24 total, only 21 were otherwise used) hold those 5
named contacts instead; no resize needed. Bundled in every build, same as
Jeremy's and the TTS-only board — Jeremy's board remains the fallback that
auto-imports on a fresh install.

Every tile also carries a `ttsScript`, same treatment as `tts-care-board.zip`.
One known gap: the script's `no_2.wav` ("Mm-mm") isn't in this recording
batch, so that tile ships with no `fileName`, same as Talking's "Not that"
gap on Jeremy's board. 23 of the batch's 92 clips aren't tiled anywhere in
this layout — the same "recorded but no slot yet" phrases documented for
Jeremy's and Steve's boards above.
