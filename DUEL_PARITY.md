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

## Blocks play

Two, and both can strand a player who has done nothing wrong.

### Multi-select on a pile is blind, and can wedge

A prompt that wants several cards reaches the board whenever **one** candidate
is outside a pile — the escape hatch is `allHidden`, which needs *every* option
to be in one. On the board, clicking a graveyard toggles an option chosen by
engine order with nothing drawn to say which card it was.

It can also strand you. `DuelSelection.pick` returns the first *unchosen*
option and `toggle` refuses additions once the maximum is reached, so with
three candidates and a maximum of two, a third click is a silent no-op and an
already-chosen pile card cannot be deselected by clicking that pile. There is
no way to reach `{2,3}` from `{1,2}` except by cancelling — and the pile-browse
escape at `BoardPointerScreen:566` is gated on the prompt having *no* options,
so the one pile you may not read is the one being asked about.

- screen: `EngineDuelScreen:1137`, `:1249`, `:1365` — every pile click goes through the picker
- board: `BoardPointerScreen:584`, `CrosshairAction:96`, `DuelSelection:94`

### The shared Extra Monster Zone resolves to a different zone in each view

The two logical EMZ entries overlap on one square. The screen breaks the tie by
asking the prompt — `priority = optionsFor(candidate).isEmpty() ? 0 : 4`, so
the hit the engine is actually offering wins at either seat. `BoardPicker.at`
breaks it by occupancy and then by **absolute controller order**, never
consulting the prompt. Same square, same click, different zone.

- screen: `EngineDuelScreen:1682`
- board: `BoardPicker:91`, `FieldLayout:126`

---

## Where the two disagree

Not missing features — the same input understood differently. This is the
class that produces "it worked in 2D".

| | screen | board |
|---|---|---|
| **A running multi-selection** | its own `selected` set (`EngineDuelScreen:1365`) | the static `DuelSelection` (`BoardPointerScreen:584`) |
| **Which snapshot answers a click** | one source for drawing and contents (`EngineDuelScreen:1797`) | hit-tests `boardToDraw()` (`:428`), reads contents from `DuelClientState.board` (`:1157`) |
| **A chain window on one click** | fires immediately (`EngineDuelScreen:1712`) | refuses, and asks (`PromptOptions:122`) |
| **A pile picker's heading** | the stack's name (`EngineDuelScreen:1161`) | the first card's name (`BoardPointerScreen:1119`, `:517`) |
| **A PLACES prompt** | lights every legal zone (`BoardRenderer:1252`) | lights at most the one under the crosshair (`OverworldBoardRenderer:106`) |

Two notes on that table.

**Switching views mid-prompt loses your selection**, because the two keep
separate running answers and neither reads the other.

**The chain window is the one row where the screen is the wrong one.** Chain
options carry no command, so the screen's `actions.size() == 1` shortcut fires
on the first click — which sets off a trap with no confirmation. The board
refuses, matching EDOPro. Fixing that means changing the screen, not the board.

---

## Missing on the board

### Facts a duellist needs

**Done** — all four now draw on the board (`OverworldBoardRenderer.drawRow`,
`drawPile`, `drawNumber`). Kept here because the reasoning is worth having.

- **Negated / disabled marker.** ~~Nothing draws it on the board.~~ Now over the zone, untinted and unturned, as on the screen (`BoardRenderer:1286`). A negated monster looks exactly like a working one, which is the most expensive thing a board can be wrong about.
- **Xyz material counts.** ~~`slot.overlays()` had no reader.~~ Now a number on the card, from the digit atlas.
- **Pendulum scales.** ~~`hasScale()` had no caller.~~ Now in the zone, blue on the left and red on the right, showing that zone's own scale — the two differ only when an effect has moved one, which is exactly when it matters.
- **Deck / graveyard / banished counts.** ~~Stack thickness only, and thickness saturates at 45 cards (`PileMesh:41`), so 45 and 60 looked identical.~~ Now a number on top of the stack.

The board spells numbers out of `DuelTextures.DIGITS` rather than the font: everything a
duellist reads off this board is a PNG, and world-space glyphs would be the one
thing on the mat that had to turn to face somebody.

### Things that happen without being shown

The animation queue is shared; the board simply does not consume most of it.
`DuelAnimations` exposes only `shattersInFlight` and `attacksInFlight`, so
everything else is queued, waited on, and never drawn.

- **Cards never move between zones.** `renderMoves` (`DuelAnimations:1036`) has one caller, the screen. On the board a card is in its old zone and then in its new one.
- **Face-down cards do not turn over.** `renderFlips` (`:858`) likewise. The board holds the old face for the event's duration and then swaps the texture instantly.
- **Reveal.** `renderReveals` (`:211`) — a revealed hand is shown to the screen and to nobody at a board.
- **Coin flips and dice rolls.** `renderTosses` (`:907`). The board plays the *sound* (`:440`) and never shows the result, which is game information rather than decoration.
- **Chain and become-target markers.** `renderOverlays` (`:1009`); `DuelTextures.CHAIN` and `TARGET` are unreferenced under `overworld/`.
- **Empty legal placement zones are not lit.** `drawRow` skips empty squares before any highlight is considered (`OverworldBoardRenderer:880`).

### Actions

- **"View Deck" is a dead row.** The board sends the packet and closes itself (`BoardPointerScreen:703`) on the reasoning that the list is "a screen of its own" — but only `EngineDuelScreen:285` reads `deckView`, so nothing opens.
- **Hold-to-pass does not work.** The screen polls the held button each tick (`:235`) and waves a whole chain through. The board passes one window per press — *and its caption says `[hold right-click to pass]` anyway*, which is a promise it does not keep.
- **The pile viewer cannot scroll**, and the card grid has no row cap: `CardChooser.layout:123` derives rows from the count with no ceiling and nothing under `overworld/` defines `mouseScrolled`. A 40-card graveyard runs off the bottom of the screen, unreachable.
- **Multi-zone placement** is refused (`PromptOptions:169` accepts PLACES only at `maxSelect <= 1`) and pulls the screen over the board.
- **Chain preference** and **music volume / mute** have no control on the board; both live in the screen's sidebar (`EngineDuelScreen:379`, `:391`).
- **The opponent's Extra Deck** opens on the screen and not the board (`CardChooser:51` allows EXTRA for controller 0 only).
- **Cancel and Confirm are drawn under the chooser's dim** — `CardChooser.draw` fills the window at `:230` after `DuelHud` has drawn them.

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
- **`DuelAnimations.clear()` does not empty `reveals`** — real, but it can only affect the screen, since the board never draws them.

---

## If these are worked through

The two blockers first: they can strand a player mid-duel, which is worse than
anything missing. Then the disagreements, because that is the class that
produces a bug report which says only "it worked in 2D" — the reporter cannot
see the divergence, only its result. The missing facts (negated, overlays,
scales, pile counts) are next: they are small, and each is a thing a player is
expected to play around and cannot currently see. The animation gaps are the
largest body of work and the least urgent, since nothing is hidden by them,
only unexplained.
