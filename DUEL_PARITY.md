# The two duels

A duel is played in one of two places: the **screen** (`EngineDuelScreen`, the
flat board) or the **world board** (`OverworldBoardRenderer` and the pointer,
crosshair and chooser around it). They are two presentations of one duel, and
the standing rule is that the second should not be a lesser version of the
first — the same question should be answerable, the same facts legible, and the
same click should mean the same thing.

This is where they still differ. It exists because "it worked in 2D" turned out
to be three separate bug reports in one evening, and each of them was a
difference nobody had written down.

**Every line here was read out of the code, not remembered.** Each claim was
then handed to a second reader whose job was to refute it; eight of the
forty-four candidates died there, most because a comment in the code said the
difference was on purpose. Those are recorded at the bottom rather than
deleted, so the same eight are not rediscovered as faults later.

The file:line references drift. Treat them as where to start looking.

---

## What has been closed

Everything the first pass of this document listed as a gap has been worked
through. The reasoning is kept — a gap that was closed once is a gap that can
reopen, and the argument for why each mattered is the thing worth having.

### The two blockers

**Multi-select on a pile** was blind and could wedge. A prompt wanting several
cards reaches the board whenever one candidate is outside a stack, and a click
on a graveyard toggled an option chosen by engine order with nothing drawn to
say which card it was. `DuelSelection.pick` returns the first *unchosen* option
and `toggle` refuses additions at the maximum, so with three candidates and a
maximum of two there was no way to reach `{2,3}` from `{1,2}` — a third click
did nothing and the taken card could not be given back by clicking the stack.
Cancelling was the only way out, and the pile-browse escape was gated on the
prompt having *no* options, so the one stack you could not read was the one
being asked about. Piles now go through the card list on every click, not only
the ones that are the whole answer. `answersOutright` already refused every
pile, so single-select is unchanged.

**The shared Extra Monster Zone** resolved to a different zone in each view. One
physical square carries both controllers' zone, crossed over by the mirror:
controller 0's sequence 5 is controller 1's sequence 6. Occupancy cannot break
that tie — the square is empty in both readings until somebody summons into it
— and falling back to absolute controller order meant a duellist could point at
a square and have the summon land in the other half of the engine's numbering.
`BoardPicker.at` now takes the same weighting the screen hands `hitAt`: offered
outranks occupied outranks bare. Tested in `BoardPickerZoneTest`, which is
possible because `BoardPicker` takes a landing point rather than a camera.

### The disagreements

| | what it was | what it is |
|---|---|---|
| **A running multi-selection** | the screen's own `selected` set | one `DuelSelection`, read by both |
| **Which snapshot answers a click** | hit-tested `boardToDraw()`, read `DuelClientState.board` | one board, the one being drawn |
| **A chain window on one click** | the screen fired immediately | both refuse and ask, matching EDOPro |
| **A pile picker's heading** | the first card's name | the stack's name |
| **A PLACES prompt** | the board lit at most the square under the crosshair | every offered zone, green over blue for one already named |

The chain window was the one row where the *screen* was wrong, so the screen
changed: chain options carry no command, and its `actions.size() == 1` shortcut
set off a trap on the first click with no confirmation.

### Facts a duellist needs

All four draw on the board (`OverworldBoardRenderer.drawRow`, `drawPile`,
`drawNumber`), out of `DuelTextures.DIGITS` rather than the font — everything a
duellist reads off this board is a PNG, and world-space glyphs would be the one
thing on the mat that had to turn to face somebody.

- **Negated / disabled marker**, over the zone, untinted and unturned. A negated monster looking exactly like a working one is the most expensive thing a board can be wrong about.
- **Xyz material counts**, from the digit atlas.
- **Pendulum scales**, blue on the left and red on the right, each zone's own — the two differ only when an effect has moved one, which is exactly when it matters.
- **Deck / graveyard / banished counts.** Thickness saturates at 45 cards (`PileMesh:41`), so 45 and 60 stood equally tall.

### Things that happened without being shown

The animation queue is shared and paced by one loop; the board simply consumed
two of its kinds. `DuelAnimations` now exposes the rest in the shape
`ShatterView` and `AttackView` established — a view record and an `inFlight`
list, because the board's geometry has nothing in common with that class's
projected quads and only the timing is shared.

- **Cards move between zones**, arcing as a carried thing does, and coming in over their owner's edge when they start off the mat.
- **Face-down cards turn over**, narrowing to nothing and opening again with the face swapped at the midpoint. `CardRenderer.submitAt` is the split that lets the quad narrow about its own centre while a settled card is drawn by exactly the same code.
- **Chain and become-target markers** stand over the cards they concern, the way `drawing.cpp` lays `tChain` and `tChainTarget`. Both textures had been sitting unreferenced under `overworld/`.
- **Coin flips and dice rolls** are announced in the HUD — the coin out of the two-frame coin sheet, a die as its number out of the digit sheet. The board used to play the *sound* and show nothing, and an effect that turns on a flip cannot be followed by somebody who never saw how it landed.
- **Reveals** likewise, as a row under the instruments.
- **Empty legal placement zones are lit**, which is the same fix as the PLACES row above: `drawRow` skips empty squares before any highlight is considered, so the lighting had to happen outside it.

Tosses and reveals are drawn by `DuelHud` rather than on the mat because they
are announcements rather than objects, and drawing them there means they appear
whether the cursor is up or the camera is the player's — the same reason the
outcome banner is drawn from there.

### Actions

- **"View Deck"** sent its packet and closed the pointer, and the only reader of the server's answer was the duel screen's tick. The row cost a duellist their cursor and showed them nothing. It waits now, and opens the list in the pile viewer a graveyard opens in.
- **Hold-to-pass** works, which the caption had been promising. Only windows the core will accept an empty answer to, on the screen's rule.
- **The pile viewer scrolls.** `CardChooser.layout` derived rows from the count with no ceiling, so a forty-card graveyard laid five rows out in the room for two. Rows are capped, the wheel moves the band, and the header says which part of the pile is on screen.
- **Multi-zone placement** is answered at the board. Every option is a square on the mat, which is the one thing a world board is better at; `PLACES` passes `boardCanAnswer` at any size and `wantsSeveral` counts it alongside `MULTI`, as the screen has always done.
- **Chain preference, mute and volume** are rows on the deck's menu, next to Surrender, which is where EDOPro keeps the chain toggles. The preference also stopped being an instance field of the screen — it reset to `DEFAULT` on every rebuild while the server went on holding what had last been sent.
- **The opponent's Extra Deck** opens. Not because it is public: `BoardState.playerBoard` already conceals it, sending backs for everything except the cards the core marks public — the face-up Pendulum monsters, which are exactly the ones an opponent has to play around.
- **Cancel and Confirm** are drawn after the panels. The grid dims the window on its way in, and the HUD used to draw them first, so the only way out of a picker sat behind the dim that opening it had put there.

---

## Missing in both

- **Counters on cards.** Not a presentation gap: `BoardSnapshot.Slot` has no counter field and `QUERY_COUNTERS` (`OcgConstants:164`) is never requested, so the data does not exist client-side. The only counter UI anywhere is the removal prompt's badge.
- **The duel log** is disabled in the screen (`renderLog` returns immediately) and absent from the board.

---

## The board is ahead here

Worth knowing before "parity" is read as "make the board match".

- **Monster holograms** standing on every face-up monster (`OverworldBoardRenderer:1021`), with per-side opacity.
- **Surrender asks twice** (`BoardPointerScreen:713`); the screen concedes on the first click.
- **Long card names scroll** rather than being cut (`CardChooser:293`); the screen truncates at the cell width.

---

## Deliberate, and not to be "fixed"

Each of these was proposed as a gap and rejected because the code says
otherwise. They are listed so the argument is not had twice.

- **Card text and ATK/DEF are Shift-gated on the board.** A board is looked at from inside the world; permanent overlays would cover it.
- **No hint banner for board-answered selections** — two of its three contents have board equivalents already.
- **No "waiting for opponent" panel on the board**, which has other ways of showing it.
- **Picker header and progress** differ in form, not in information.
- **No phase transition while the camera is held** — documented, and about the camera rather than the presentation.
- **Sort, counter amounts and declare-a-card have no board UI** and delegate to the screen deliberately, with a working path.
- **The duel log is off in both** by an explicit early return.
- **`DuelAnimations.clear()` does not empty `reveals`** — real, and now reachable from the board as well, so `revealsInFlight` prunes the queue itself rather than relying on the one presentation that used to read it.

---

## What is left

Two things, both listed above as missing in **both** presentations rather than
in one: counters on cards, which needs `QUERY_COUNTERS` requested and a field on
`BoardSnapshot.Slot` before either view can draw anything, and the duel log,
which is switched off in the screen by an explicit early return.

Neither is a parity gap. They are the same in both places, which is the point.
