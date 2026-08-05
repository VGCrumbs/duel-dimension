# EDOPro duel-interface parity

Inventory extracted from the EDOPro client source (`gframe/`: `game.cpp` widget construction, `event_handler.cpp` field commands, `drawing.cpp` render routines, `duelclient.cpp` message handlers; checkout at `C:\Users\Admin\Desktop\YGO\edopro`). Status: ✅ ported (Minecraft-native equivalent) · 🟡 partial · ❌ not ported.

## Field & board display

| EDOPro element (source) | What it is | Status |
| --- | --- | --- |
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

1. **3D perspective → 2D GUI.** EDOPro renders the field as textured quads in an Irrlicht 3D scene. The *coordinates* port exactly (`FieldLayout`), the perspective projection does not; we fit the same table orthographically into the Minecraft screen.
2. **Client-side rules state.** EDOPro's client keeps a full `ClientField` mirror and computes `cmdFlag` locally from the raw message stream. We compute the identical bitmask **server-side** and send only the resulting legal commands, because a Minecraft client is untrusted — see the note below.
3. **UI assets.** EDOPro's textures and skins are its own project's assets under its own licence; the mod already ships a card-image pipeline for the same cards. We match layout and behaviour, not texture files.

## Architecture note

EDOPro resolves everything client-side from the raw message stream. We deliberately do not: prompts are flattened server-side into labelled options and the client answers with indices (plus a card code for the announce search, which the server re-validates against the same opcode filter the core uses). Hidden information never reaches the client, so interface parity does not reintroduce the cheating surface EDOPro accepts.
