# EDOPro duel-interface parity

Inventory extracted from the EDOPro client source (`gframe/`: `game.cpp` widget construction, `event_handler.cpp` field commands, `drawing.cpp` render routines, `duelclient.cpp` message handlers; checkout at `C:\Users\Admin\Desktop\YGO\edopro`). Status: ✅ ported (Minecraft-native equivalent) · 🟡 partial · ❌ not ported.

## Field & board display

| EDOPro element (source) | What it is | Status |
| --- | --- | --- |
| Field zones (`client_field.cpp`) | 5 monster + 2 extra monster zones, 5 spell/trap, field spell, 2 pendulum zones, per player | ✅ full zone grid incl. EMZ/field/pendulum slots |
| Deck/extra/grave/banished piles | Pile stacks with counts | ✅ counts + clickable grave/banished viewers |
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
| `wCmdMenu` (Summon/SP/MSet/SSet/Repos/Attack/Activate) | `SELECT_IDLECMD` / `SELECT_BATTLECMD` | ✅ option list + click-on-card shortcut |
| Phase buttons `btnBP/btnM2/btnEP/btnShuffle` | phase commands in idle/battle | ✅ as options |
| `wCardSelect` + `btnCancelOrFinish` | `SELECT_CARD` (min/max, cancel) | ✅ multi-select + confirm/cancel |
| `wCardSelect` unselect loop | `SELECT_UNSELECT_CARD` | ✅ one-at-a-time + finish |
| `wQuery` yes/no | `SELECT_YESNO` / `SELECT_EFFECTYN` | ✅ |
| `wOptions` | `SELECT_OPTION` | ✅ |
| `wPosSelect` (4 position buttons) | `SELECT_POSITION` | ✅ |
| Zone click + flash | `SELECT_PLACE` / `SELECT_DISFIELD` | ✅ highlighted zones, click to choose |
| Chain respond + `btnChainAlways/Ignore/WhenAvail` | `SELECT_CHAIN` | 🟡 respond/decline ✅; the three auto-respond preference toggles ❌ |
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

## Architecture note

EDOPro resolves everything client-side from the raw message stream. We deliberately do not: prompts are flattened server-side into labelled options and the client answers with indices (plus a card code for the announce search, which the server re-validates against the same opcode filter the core uses). Hidden information never reaches the client, so interface parity does not reintroduce the cheating surface EDOPro accepts.
