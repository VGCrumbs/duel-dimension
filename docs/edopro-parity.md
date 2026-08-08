# EDOPro duel-interface parity

Inventory extracted from the EDOPro client source (`gframe/`: `game.cpp` widget construction, `event_handler.cpp` field commands, `drawing.cpp` render routines, `duelclient.cpp` message handlers; checkout at `C:\Users\Admin\Desktop\YGO\edopro`). Status: ✅ ported (Minecraft-native equivalent) · 🟡 partial · ❌ not ported.

## Field & board display

| EDOPro element (source) | What it is | Status |
| --- | --- | --- |
| Field mat (`matManager.vField`) | One quad, x −1..9, y −4..4, u=(x+1)/10, v=(y+4)/8, drawn once for both halves | ✅ **1:1** — rectangle and UVs verified against the shipped PNG by inverse-mapping its printed slots |
| Duel camera (`game.h` FIELD_*/CAMERA_*, `game.cpp` getPosition/getTarget) | Off-centre LH perspective, eye (4.2,8,7.8) | ✅ **1:1 port** |
| Field zones (`materials.cpp` vertex table) | 5 monster + 2 extra monster zones, 5 spell/trap, field spell, pendulum zones, side columns | ✅ **1:1 port** — `FieldLayout` keeps EDOPro's own field-unit coordinates (1.1 pitch, side columns, EMZ on the centre line); only the 3D→2D projection is ours |
| Deck/extra/grave/banished piles | Pile stacks with counts | ✅ counts, viewers, and **activation from a pile** (grave/banished/deck/extra), which the option list previously made unreachable |
| `act.png` on piles (`deck_act`/`grave_act`/`remove_act`/`extra_act`) | Indicator that something in that pile can be activated | ✅ EDOPro's own texture, same trigger |
| Both hands | Own face-up, opponent's as backs | ✅ |
| Card art on field (`DrawCard`) | Real art, face-downs as card backs | ✅ via the mod's own image pipeline |
| Defence rotation | Sideways cards | ✅ |
| `DrawStackIndicator` | Xyz material count badge | ✅ overlay count badge |
| `DrawMisc` LP + names | LP per player, damage flash | 🟡 LP shown; no flash animation |
| Turn/phase banner | Turn count + phase name | ✅ |
| `DrawPendScale` | Pendulum scales on card | ❌ (scales not in query snapshot yet) |
| Counters on cards | Counter overlays | ❌ display (prompt handling ✅) |
| Equip/target lines, attack arrow | Relation lines | ❌ |
| Card motion/flip animations | Movement animation | ❌ (log narrates instead) |
| Chain number bubbles | Chain stack display | ❌ (chain prompts carry text) |
| Selectable-zone flashing | Highlights for place selection | ✅ zone highlighting + click |
| Hover: info panel (`wCardImg`, `stInfo…`) | Art, name, type line, ATK/DEF, effect text | ✅ right-hand info panel from local DB |
| `lstLog` log tab | Scrolling event log, click to inspect | 🟡 log lines shown; no click-to-inspect |
| Chat (`wChat`) | In-duel chat | ✅ Minecraft chat |

## Prompt dialogs (all reachable ones must be answerable)

| EDOPro widget | Engine message | Status |
| --- | --- | --- |
| `wCmdMenu` (Summon/SP/MSet/SSet/Repos/Attack/Activate) | `SELECT_IDLECMD` / `SELECT_BATTLECMD` | ✅ **1:1 port** — `CardCommands` mirrors game.h COMMAND_* bits, duelclient.cpp's cmdFlag population and ShowMenu's order/captions |
| Phase buttons `btnBP/btnM2/btnEP/btnShuffle` | phase commands in idle/battle | ✅ as options |
| `wCardSelect` + `btnCancelOrFinish` | `SELECT_CARD` (min/max, cancel) | ✅ multi-select + confirm/cancel |
| `wCardSelect` unselect loop | `SELECT_UNSELECT_CARD` | ✅ one-at-a-time + finish |
| `wQuery` yes/no | `SELECT_YESNO` / `SELECT_EFFECTYN` | ✅ |
| `wOptions` | `SELECT_OPTION` | ✅ |
| `wPosSelect` (4 position buttons) | `SELECT_POSITION` | ✅ |
| Zone click + flash | `SELECT_PLACE` / `SELECT_DISFIELD` | ✅ highlighted zones, click to choose |
| Chain respond + `btnChainAlways/Ignore/WhenAvail` | `SELECT_CHAIN` | ✅ **1:1 port** — `ChainPreference` reproduces duelclient.cpp's auto-respond formula incl. the `spe_count == 0x7f` select-trigger sentinel; cycled from a toggle beside Surrender |
| Tribute selection | `SELECT_TRIBUTE` | ✅ (release_param shown) |
| Material sums | `SELECT_SUM` | ✅ |
| `wANCard` (text search + list) | `ANNOUNCE_CARD` | ✅ client-side name search over local DB, server validates with the opcode filter |
| `wANNumber` | `ANNOUNCE_NUMBER` | ✅ |
| `wANRace` / `wANAttribute` checkboxes | `ANNOUNCE_RACE` / `ANNOUNCE_ATTRIB` | ✅ multi-pick of exactly N |
| Sort dialog (`wCardSelect` reorder) | `SORT_CARD` / `SORT_CHAIN` | ✅ click-in-order UI + decline |
| Counter distribution | `SELECT_COUNTER` | ✅ +/- per card up to stock |
| `wHand` (rock/paper/scissors) | `ROCK_PAPER_SCISSORS` | ✅ |
| `btnFirst/btnSecond` | first-turn choice | ❌ host-protocol, not a core message; we seat the challenger first (future: challenge screen) |
| `wCardDisplay` | `MSG_CONFIRM_CARDS` reveal viewer | 🟡 revealed cards go to the log |
| `stHintMsg` | `HINT_SELECTMSG` banner | ✅ prompt titles |
| `wACMessage` popup | auto-close hints | 🟡 folded into log |
| `btnLeaveGame` | surrender | ✅ Surrender button |
| Timers (`time_limit`) | per-player clocks | ❌ (10-min prompt timeout instead) |
| Replay controls / spectating | `wReplay`, swap views | ❌ in-game (replay format exists engine-side) |

## Deviations, stated explicitly

Three places where byte-for-byte copying is impossible or unwise, and what we do instead:

1. **3D perspective → 2D GUI.** ~~Not portable.~~ **Now ported.** EDOPro's duel camera (eye `(4.2, 8, 7.8)`, target `(4.2, 0, 0)`, up `+Z`, frustum `l=-0.90 r=0.45 b=-0.42 t=0.42 n=1`) reduces to a closed form for points on the table plane, so `FieldLayout.Projection` reproduces the exact perspective — including the asymmetric frustum whose `M[8] = 1/3` shifts the table right and creates the space EDOPro puts its card-info column in. Only the final fit to a Minecraft-shaped window is ours.
2. **Client-side rules state.** EDOPro's client keeps a full `ClientField` mirror and computes `cmdFlag` locally from the raw message stream. We compute the identical bitmask **server-side** and send only the resulting legal commands, because a Minecraft client is untrusted — see the note below.
3. **UI assets.** EDOPro's textures and skins are its own project's assets under its own licence; the mod already ships a card-image pipeline for the same cards. We match layout and behaviour, not texture files.

## Bot behaviour, verified against Windbot

The duelists run a port of Windbot (`.wbsrc/windbot-master`), and its rules are
followed rather than tuned. One that gets questioned often, recorded here so it
is not re-investigated:

**Monsters are summoned face-up even when badly outmatched.** That is Windbot's
own rule, not a port defect. `DefaultExecutor.OnSelectMonsterSummonOrSet`
(`ExecutorBase/Game/AI/DefaultExecutor.cs:563`) is

```csharp
return card.Level <= 4 && Bot.GetMonsters().Count(m => m.IsFaceup()) == 0
    && Util.IsAllEnemyBetterThanValue(card.Attack, true);
```

which our `DefaultExecutor.onSelectMonsterSummonOrSet` reproduces exactly, as
does `AIUtil.isAllEnemyBetterThanValue` (`AIUtil.cs:50`) and the main-phase loop
that consults it (`GameAI.cs:524-541` → `ExecutorBot.idle`). Being outmatched is
necessary but not sufficient; setting also needs

* the card to be **Level 4 or lower**;
* the bot to control **no face-up monster at all** — once it has one it never
  sets again, however outclassed the next one is;
* **every** enemy monster to out-power it *and be in attack position*, because
  the `onlyATK` clause sits inside the `All(...)`. A single monster of yours in
  defence makes the whole test false.

`ExecutorType.MonsterSet` is likewise not missing: upstream, only per-deck AIs
register it (`ABCExecutor`, `AlbazExecutor`, `BlackwingExecutor`, …), never
`DefaultExecutor`. The sanctioned way to make a particular duelist set a
particular card is a per-card `MonsterSet`/`SummonOrSet` rule in `Duelists`,
which is exactly how Windbot's own deck AIs do it — not a change to the default.

### The one deviation: free triggers

Windbot activates only what a per-card rule names. `GameAI.OnSelectEffectYn`
(`GameAI.cs:462-470`) returns false the moment the executor list runs out, and
`DefaultExecutor` registers **four** `Activate` rules in total (Chicken Game,
two Vaylantz, Santa Claws). A card it has never heard of is declined — Mystic
Tomato is not mentioned anywhere in Windbot, decks included.

The duelists differ in exactly one case: **a trigger on a card that is already
in the graveyard is taken, listed or not.** The card is spent either way, so
there is no copy being held back and no later moment being preferred;
declining cannot be the better play.

It is deliberately not "activate anything offered" — that would have the bot
chain every trap at the first opportunity, which is worse than the behaviour it
replaces. The condition is the location, which is the part that can actually be
checked.

`Executor.activateUnlistedOptional` defaults to Windbot's answer and is
overridden only in `DuelistExecutor`, so **`DefaultExecutor` stays a faithful
port** — `FreeTriggerRuleTest` pins that, along with the rule firing in the
graveyard and nowhere else.

## Architecture note

EDOPro resolves everything client-side from the raw message stream. We deliberately do not: prompts are flattened server-side into labelled options and the client answers with indices (plus a card code for the announce search, which the server re-validates against the same opcode filter the core uses). Hidden information never reaches the client, so interface parity does not reintroduce the cheating surface EDOPro accepts.
