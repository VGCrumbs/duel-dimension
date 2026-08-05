---
aliases: [Duel Dimension, Project Hub]
tags: [project, minecraft, yugioh, ocgcore]
created: 2026-08-04
status: active
---

# Duel Dimension — Project Hub

Fork of [YgoDuelingMod](https://github.com/CAS-ual-TY/YgoDuelingMod) (Forge 1.19.2, GPL-3.0) that adds **real, automated card-effect resolution** by embedding the EDOPro rules engine — plus Tag Force-style NPC duelists, historical rulesets, and the original mod's card-collecting loop as the economy underneath it all.

> [!NOTE]
> Deep technical reference for the engine embedding lives in [[ocgcore-integration]]. The card-database JSON formats are documented in [[ydm2-db-wiki/README|the YDM2-DB wiki mirror]]. This note is the hub: vision, decisions, roadmap, and how each feature works.

## Goals

1. **Ruled duels** — full rules enforcement for effectively every card, via the same engine EDOPro uses (ocgcore, in-process over JNA).
2. **NPC duelists** — Tag Force / World Championship-style opponents: legal play + per-duelist strategy, personality knobs, dialogue, difficulty via deliberate misplay rate. Duelists are **JSON content, not code**.
3. **Rulesets as a database** — composable era presets (mechanics × banlist × card pool × house rules). Flagship: **Goat Format (2005)**. Always available: **Unrestricted** (no banlist) for playing the game the way the schoolyard remembers it.
4. **Collection stays the soul** — decks for ruled duels are built from cards you own (packs, pull ratios, trading — upstream's core loop). NPCs can offer loaner decks to bootstrap.
5. **Keep casual mode** — the upstream manual/tabletop simulator remains as a second mode for house rules and unscripted customs.

### Non-goals (for now)

- Public release (build stays private-but-portable: clean licensing, Windows x64 natives only, no CI)
- Rush/Speed duels, Master Duel-style alternate formats
- Porting WindBot or a full combo-line AI (revisit if an "elite boss" NPC ever needs it)

## Decisions log

| Date | Decision |
|---|---|
| 2026-08-04 | **Path A**: ocgcore embedded in-process via JNA (not IPC, not a Java engine rewrite) |
| 2026-08-04 | Engine data comes from **BabelCDB** (`cards.cdb`) + **CardScripts** Lua; YDM JSON stays the display/collection DB; join key = passcode/id |
| 2026-08-04 | NPC AI = **layered Java bot** (legality → generic heuristics → JSON duelist profiles), not a WindBot bridge |
| 2026-08-04 | **Rulesets are composable** fields (mechanics, banlist, pool cutoff, house rules) with named presets on top |
| 2026-08-04 | Seed & test format: **Goat 2005**; Unrestricted preset ships trivially alongside |
| 2026-08-04 | **Strict deck validation** — freedom comes from permissive rulesets, not skipped checks |
| 2026-08-04 | First playable milestone: **human vs NPC** (PvP reuses the same GUI later) |
| 2026-08-04 | Distribution: **private, keep public possible** (licenses clean, assets referenced not committed) |
| 2026-08-04 | Ruled-duel decks are **collection-bound** (+ creative/dev bypass) |
| 2026-08-04 | **Rebrand now**, before worlds exist (mod id change breaks saves — do it while cheap) |
| 2026-08-04 | Bots must **observe all messages** (not just prompts) and see only a **filtered, non-omniscient board state** — cheating is an explicit profile knob, never an accident |
| 2026-08-04 | Named **Duel Dimension**: mod id `dueldimension`, packages `de.cas_ual_ty.dueldimension`, `YDM`/`Ydm*` classes to `DuelDimension`/`Dd*` (an interim "Duel Monsters" naming was replaced the same day) |
| 2026-08-04 | Starter decks are the real SDY/SDK/SDJ lists, generated from the card DB's set data (verified ids, all cards pack-obtainable) |
| 2026-08-04 | Launch NPC roster: **Joey** (Red-Eyes gambler aggro, high misplay, loaner-deck friend) and **Kaiba** (Blue-Eyes control-beatdown, zero misplay, boss) — anime decks as **real-card builds** (anime-only variants lack engine scripts), dueling under an Unrestricted-classic ruleset |

## Open decisions

- [x] **Name**: **Duel Dimension** — mod id `dueldimension`, packages `de.cas_ual_ty.dueldimension`, class prefixes `Dd*`. Mod-id changes break existing saves, so settle any further rename before serious worlds exist.
- [x] **Division of labor**: Claude handles machinery *and* content; user reviews/playtests (decided 2026-08-04)
- [ ] *(Parked)* Long-term hosting for the card-DB fork (`db.json` auto-update source). Dev rides upstream's 2023 snapshot — covers the Goat pool completely.

---

# Roadmap

> [!TIP]
> Working rule: **every step has a machine-checkable gate** — and Minecraft never boots until Phase 5. All engine/bot work runs headless where an iteration costs seconds.

## Phase 0 — Harness

- [x] JNA binding for ocgcore API v11 (`de.cas_ual_ty.dueldimension.ocg`)
- [x] x64 `ocgcore.dll` built from pinned ygopro-core source (`ocgcore-build/`, CMake + MSVC)
- [x] Spike: create duel → load scripts → pump messages → reach `AWAITING` (verified 2026-08-04)
- [x] `CdbCardProvider` — BabelCDB `.cdb` → card-reader callback (compiled; not yet exercised in a duel)
- [x] **Rebrand commit** — mod id, `mods.toml`, `assets/`+`data/` namespaces, lang keys, Java packages, class prefixes, Gradle refs (2026-08-04)
- [x] Extract spike → `HeadlessDuelRunner` + `ResponseSource` interface (`observe()` + lifecycle; per-bot memory arrives with the bots in Phase 3)
- [x] JUnit wired up (5 green; engine tests skip when native/scripts absent); `gradlew17` wrapper committed
- **Gate:** ✅ `test` green in ~18s cold, incremental faster (2026-08-04)

## Phase 1 — Protocol precision layer

- [x] Decoders for the ~18 core-duel messages (`ocg.msg`: sealed `DuelMessage`, 9 prompts + 9 informational + `Unknown`; strict exact-consume decoding). Layout details verified from source: 10-byte loc_info; repositionable/attackable lists use u8 sequences; `SELECT_PLACE` mask flags *forbidden* zones
- [x] Decoder unit tests: hand-built fixtures to the writers' layouts + live-stream decode of real duels
- [x] Response encoders for all 9 prompt types (`Responses`, from `playerop.cpp` — the parser is the spec)
- **Gate:** ✅ zero `MSG_RETRY` — live test plays multi-turn end-turn/decline-chain sequences accepted by the core; any RETRY in any test auto-fails (2026-08-04)

## Phase 2 — Legality + fuzzing

- [x] Legal-move logic for **all 19 prompt types**, including the hard ones: `SELECT_SUM` (`SumSolver` — port of `select_sum_check1` + the max==0 minimality rule), `SELECT_UNSELECT_CARD` (one index per prompt, re-prompted), `SELECT_TRIBUTE` (release_param totals, not card counts), `ANNOUNCE_CARD` (`DeclarableFilter` — RPN opcode VM port of `is_declarable`, scans the card index)
- [x] `RandomBot` (seeded) — random legal answers to all 9 decoded prompt types, aborts loudly on unknown prompts. **First full real-deck bot-vs-bot duel passed 2026-08-04**: vanilla decks from the real cdb via `CdbCardProvider`, decisive `MSG_WIN`, zero RETRY. Discovery: the core never returns `DUEL_STATUS_END` after a win — it plays zombie turns; stopping at `MSG_WIN` is the host's job (`stopOnWin`)
- [x] `gradlew17 duelFuzz -Pcount=N -Pthreads=T` — failures dump `(flags, seed, decks, responses)` as `.replay` files; per-worker in-flight marker files mean even a native crash names its seed
- [x] Replay format frozen (`Replay`, v1, line-based text) — verified by replaying a recorded duel to an identical message stream and winner
- [x] Parallel-duels stress → **independent duel handles are safe to run concurrently**; the MC server can host parallel duels without serialising them
- **Gate:** ✅ 2026-08-04 — RandomBots finish real duels incl. Ritual/Synchro (`MSG_SELECT_SUM` exercised, 8 prompt types seen); **1000 duels/4 threads and 3000 duels/8 threads: NO FAILURES**, 12–16 ms/duel

## Phase 2.5 — Data foundations *(parallelizable with Phase 3)*

- [ ] Pin the **engine bundle**: (core commit, CardScripts snapshot, cdb snapshot) vendored as a tested triple — stop live-referencing `C:\ProjectIgnis`
- [ ] `dbAudit` task: every pack-obtainable card ↔ cdb row ↔ `c<id>.lua` exists; alias/alt-art reconciliation; flags unduelable cards; Goat pool pullability check
- [ ] Banlist importer (lflist → `ydm_db/banlists/*.json`), incl. April 2005 TCG
- **Gate:** audit runs clean or every failure is a listed, understood exception

## Phase 3 — Generic competence (Layer 2)

- [x] Query API binding → `BoardState` snapshots (`QueryParser` over the core's TLV query buffers)
- [x] **Honest view first** — `BoardState.observe(duel, viewer)` strips what the viewer may not know; `observeOmnisciently()` is the explicit opt-in reserved for a "this boss cheats" profile knob
- [x] `HeuristicBot`: tiered idle scoring, trade-aware attacking, ownership-aware selections, cheapest-material solving
- [x] `gradlew17 botArena` — seeded win rates with alternating seats (the permanent regression metric)
- **Gate:** ✅ 2026-08-04 — **99.2%** (vanilla) and **93.6%** (ritual/synchro) vs RandomBot over 500 duels each

## Phase 4 — Duelists + rulesets (Layer 3)

- [ ] Card role auto-tagging from cdb `category` bits; profile `card_hints` override
- [ ] `DuelistProfile` JSON (schema-versioned): personality weights, `misplay_rate`, dialogue hooks, ruleset ref, deck ref
- [x] Player starter decks: **Starter Deck: Yugi / Kaiba / Joey** (`data/dueldimension/decks/*.ydk`, `StarterDecks` roster) — verified against the engine, full duels played, wired into fuzz + arena
- [ ] Launch NPC roster: `joey.json` + `kaiba.json` profiles (anime decks as real-card builds; original-flavored dialogue, not verbatim anime lines)
- [ ] Ruleset JSON + loader: `mechanics` / `banlist` / `pool` / `house`; presets `goat_2005`, `unrestricted`, `modern`
- [ ] Deck validation: ruleset legality × collection ownership (strict)
- [ ] `.ydk` loader; `duelists/` + `decks/` roster layout
- [ ] Scenario fixtures via core Debug/puzzle Lua → per-duelist characterization tests ("activates Mirror Force at the right time")
- **Gates:** opposed-aggression profiles differ measurably in arena stats; winrate falls monotonically with `misplay_rate`; new duelist = zero Java changes

## Phase 5 — Minecraft integration

- [ ] Engine thread + `DuelSession` manager (server side)
- [ ] `DuelistEntity` NPC: profile id, right-click challenge, loaner-deck offer
- [ ] Human `ResponseSource` over existing `duel/network` packets
- [ ] Prompt GUI for the interactive `MSG_SELECT_*` set (shares playfield rendering with casual mode)
- [ ] Challenge screen: Casual vs Ruled (+ ruleset picker for PvP later)
- [ ] Rewards hook: win → packs (existing distribution system)
- **Gate:** you beat (or lose to) a Goat-deck NPC in-game, start to finish, no manual intervention

---

# Feature documentation

## Working today

### Card collection & packs *(upstream)*

The mod's original core: cards exist as items with rarity overlays; **sets** define contents and **distributions** define pull ratios (weighted "pulls" of rarity slots). All of it is data-driven JSON downloaded as a versioned DB (`db.json` triggers updates; see [[ydm2-db-wiki/Home|DB update rules]]). Formats: [[ydm2-db-wiki/Card-Template|cards]], [[ydm2-db-wiki/Set-Template|sets]], [[ydm2-db-wiki/Distribution-Template|distributions]]. Card images are fetched and cached locally (`ydm_db_images/`).

### Manual dueling *(upstream — stays as "Casual mode")*

A DuelingBook-style tabletop: a duel-mat entity + GUI where players move cards freely (`MoveAction`, `ChangeLPAction`, dice/coin, phase buttons — `duel/action/*`). No rules enforcement; trust-based. Persists as the house-rules / custom-cards mode alongside engine duels.

### ocgcore embedding (the engine)

EDOPro's rules engine loaded **in-process** via JNA. The binding (`de.cas_ual_ty.dueldimension.ocg`) is deliberately Minecraft-free so everything below Phase 5 runs headless.

- `OcgApi` / `OcgStructs` / `OcgConstants` — raw mapping of `ocgapi.h` v11: functions, structs, callbacks, `MSG_*`/`LOCATION_*`/`DUEL_MODE_*` constants.
- `OcgDuel` / `OcgCard` — safe wrapper owning the hairy lifetimes: callback GC pinning, `setcodes` buffer lifetime, copying core-owned volatile buffers, version check. **Do not "simplify" these** — details in [[ocgcore-integration]].
- The core is script-driven: card behavior is per-card Lua (CardScripts repo), requested through our script-reader callback; static card data (atk/type/setcodes bitfields) through the card-reader callback.
- Duel loop: `CreateDuel(seed, flags, LP…) → NewCard×N → StartDuel → loop { Process → GetMessage* → render/dispatch; if AWAITING → SetResponse }`.
- **Server-authoritative**, one dedicated engine thread per duel (core calls + callbacks are same-thread). Native crash kills the JVM — accepted Path A trade-off; binding is process-agnostic if IPC is ever needed.

### Native build (`ocgcore-build/`)

CMake + MSVC build of `ocgcore.dll` (x64) from a local pinned [ygopro-core](https://github.com/edo9300/ygopro-core) checkout, output to gitignored `native/`.
**Why we build it:** EDOPro ships a *32-bit* dll — unloadable in a 64-bit JVM. Building from source also pins the exact core commit to our binding's struct layouts.

### CdbCardProvider

Loads the `datas` table of BabelCDB SQLite files (`cards.cdb` + expansion cdbs) into memory and answers the engine's card-reader callback: passcode → `OcgCard` (type/attribute/race bitfields, packed setcodes decoded to the 0-terminated `uint16` list, pendulum scales, link markers). Passcodes are the same ids YDM's JSON uses, so engine data joins display data by direct id match. SQLite via `org.xerial:sqlite-jdbc`.

### Spike harness (`OcgSpike`)

Standalone `main()` smoke test — no Minecraft. Loads the dll, verifies API v11, creates a duel, loads `constant.lua`/`utility.lua`, pumps messages to the first `AWAITING`. Run:

```
JAVA_HOME=<jdk17> ./gradlew ocgSpike -PocgLib=native/ocgcore.dll -PocgScripts=C:/ProjectIgnis/script
```

Verified end-to-end 2026-08-04 (empty decks → instant deck-out `MSG_WIN`, then `MSG_SELECT_IDLECMD` await — exactly right). Grows into `HeadlessDuelRunner` in Phase 0.

## Designed — not yet built

### Starter decks *(built)*

Three decks a new player picks from — **Starter Deck: Yugi** (SDY, 46 cards), **Kaiba** (SDK, 46) and **Joey** (SDJ, 50) — shipped as `.ydk` files in `data/dueldimension/decks/`. The lists are generated from the card database's own set data rather than transcribed, so every passcode is verified and, because collection-bound play is the rule, every card is genuinely obtainable from packs in game. `YdkDeck` reads the community `.ydk` format (so decks interchange with EDOPro and friends) and `StarterDecks` is the selectable roster, which doubles as the source of NPC loaner decks.

`StarterDeckTest` guards the whole chain: legal construction, every card known to the engine, every card scripted — following alt-art aliases, since a 2002 reprint's script lives under the original's id and the core resolves that itself — and a complete duel played between each pair. HeuristicBot wins 99.5% / 98.5% / 95.0% with them against RandomBot.

### Board state & the honesty rule *(built — Phase 3)*

`BoardState.observe(duel, viewer)` is the only view bots get. Because the engine runs in-process, the query API would happily hand over the opponent's hand and face-down cards, so the honest view strips the identity of anything the viewer isn't entitled to (keeping the fact that *a* card sits there, since that much is public) and leaves `observeOmnisciently()` as a deliberate opt-in for a future cheating-boss profile. The core helps here: it always emits `QUERY_IS_PUBLIC` whether or not you asked for it, so "may this player see this card" is the engine's answer, not our guess. `BoardStateTest` asserts both halves — that the snapshot really sees the field, and that the opponent's hand is genuinely hidden but readable when cheating.

### HeuristicBot & the arena *(built — Phase 3)*

Deck-agnostic scoring: special summon > normal summon > setting a wall > activating > setting backrow > entering battle > repositioning, with attacks taken only on winning trades and materials/tributes paid in the cheapest bodies. `gradlew17 botArena -Pduels=500` reports seeded win rates with seats alternating each duel, so going first can't flatter a bot.

The arena immediately earned its keep by catching three bugs that reading the code would not have:

1. Setting outclassed monsters face-down left no face-up attackers, so the bot never entered battle — it lost **100%** of ritual-deck duels while looking reasonable in review.
2. A naive reposition score dominated every turn, flipping the bot's own attackers into defence 16–32 times per duel.
3. Attacking face-down monsters with weak attackers bled life points into 2000-DEF walls; fixing just that was worth **+5.2 points** of win rate.

### Fuzzing & replays *(built — Phase 2)*

`gradlew17 duelFuzz -Pcount=1000 -Pthreads=4` plays that many bot-vs-bot duels, alternating a vanilla beatdown deck and a Ritual/Synchro deck so both the easy and hard prompt paths get hit. Anything that isn't a clean finish — a rejected response, an undecodable message, a prompt no bot can answer, a hang — is written to `build/fuzz/failure-<seed>.replay`. Each worker also keeps an `in-flight-<n>.txt` marker, so a native crash (which kills the JVM outright) still leaves the responsible seed on disk.

A `Replay` is `(flags, seed, decks, responses)` in line-based text — that quadruple fully determines a duel, so one format covers fuzz repro, regression fixtures, and later in-game replay/spectating. `ReplayTest` proves it by re-running a recorded duel and asserting an identical message stream and winner.

### Message layer (Phase 1)

Typed Java decoders for the core's length-prefixed message stream, and response encoders for the interactive subset. Formats come from reading the core source (the code that writes/parses the bytes), never from memory. Everything is fixture-tested against captured real streams, and `MSG_RETRY` — the core rejecting a response — is treated as a hard test failure, which makes the engine itself our protocol validator.

### ResponseSource & bots (Phases 0–3)

One interface for "a thing that plays": lifecycle callbacks (duel start/end), `observe(message)` for the full informational stream (fuels memory like "their trap is spent" and dialogue triggers), and `respond(prompt)` for decisions, backed by a per-duel `BotMemory` blackboard. Human GUI, `RandomBot`, `HeuristicBot`, and any future WindBot bridge are all just implementations — the engine loop can't tell them apart.

- **Layer 1 (legality):** `LegalMoveEnumerator` turns each decoded prompt into the complete set of legal responses. Mostly reading option lists out of the prompt; the real work is `SELECT_SUM` (subset-sum with "at least" semantics) and the `SELECT_UNSELECT` incremental loop.
- **Layer 2 (competence):** deck-agnostic scoring over `FilteredBoardState` — summon strongest, attack winning trades only, hold removal for threats, set backrow, detect lethal.
- **Information honesty:** the in-process query API sees *everything*, so heuristics are written only against `FilteredBoardState` (what a legal player knows). NPCs never accidentally read your set cards; `"cheats": true` exists as a deliberate boss knob.

### Duelist profiles (Phase 4)

An NPC = one JSON file + a `.ydk` deck. Personality weights bias Layer-2 scoring (aggression, risk), `misplay_rate` is the difficulty dial (epsilon-greedy 2nd-best moves), `card_hints` override auto-derived roles for signature cards, `dialog` hooks fire off observed events, plus ruleset ref and loaner-deck flag. Roles are ~80% auto-tagged from cdb `category` bits so most cards need no annotation. Making a new duelist requires zero Java.

### Rulesets & banlists (Phase 4)

Composable ruleset JSON in the card DB:

```json
{
  "schema": 1,
  "id": "goat_2005",
  "name": "Goat Format",
  "mechanics": { "master_rule": 1, "legacy_ignition_priority": true },
  "banlist": "tcg_2005_04",
  "pool": { "cutoff_date": "2005-06-01", "region": "TCG", "include": [], "exclude": [] },
  "house": { "lp": 8000, "match": true }
}
```

- **Mechanics** map to ocgcore per-duel creation flags (MR1–MR5 natively supported; Goat's legacy ignition priority included — exact flag combo to be confirmed from the core header).
- **Banlists** are shared files imported from historical lflist data; "no banlist" is an empty list — the `unrestricted` preset is ~4 lines.
- **Pool cutoffs** derive from YDM set release dates (TCG region), with explicit `include`/`exclude` for convention variants (the Exarion debate is a preset file, not an argument).
- **Enforcement is strict**: deck must pass ruleset legality × collection ownership to start. The engine never validates decks — this layer is ours.
- Custom cards: excluded from validated rulesets unless `"allow_custom": true`; always fine in Casual mode.

### Data integrity — `dbAudit` (Phase 2.5)

The mod spans two databases (YDM JSON for collection/display, cdb + Lua for the engine). The audit cross-checks the join: every obtainable card has a cdb row and a script file, aliases reconcile alt-arts, every ruleset's pool is pullable from available sets, and cards with missing/ambiguous release dates are listed for manual override instead of silently guessed. A card you can pull but not duel is a bug the build catches, not a player report.

### Testing & tooling (Phases 1–4)

- **Fuzzing:** N seeded duels in forked JVM batches; any crash/hang/RETRY reproduces exactly from `(seed, decks, trace)` — which doubles as the **replay format** (later: spectating, bug reports, NPC "watch the replay" flavor).
- **Arena:** seeded head-to-head winrate tables — every AI change is a number, not a vibe.
- **Scenario tests:** exact board states built via the core's puzzle-mode Debug Lua, asserting specific choices ("Mirror Force now, not earlier"). Winrate says a change helped; scenarios say the duelist *behaves in character*.

### Minecraft integration (Phase 5)

`DuelSession` manager on the server owns engine threads and routes prompts: NPC prompts → bot in-process; human prompts → network packets → prompt GUI (shared playfield rendering with Casual mode). `DuelistEntity` carries a profile id, offers challenges/loaner decks, speaks its dialogue lines, pays out packs through the existing distribution system on a win.

---

# Reference

| Thing | Where |
|---|---|
| Repo | `C:\Users\Admin\Desktop\YGO\CrumbyDueling` — folder still carries the old working name (branch `crumby`, remote `upstream` = CAS-ual-TY) |
| JDK for Gradle | Temurin 17 — `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot"` (system Java 25 breaks Gradle 7.3.3) |
| Native dll | `native/ocgcore.dll` (gitignored; rebuild via `ocgcore-build/`) |
| Core source | `C:\Users\Admin\Desktop\YGO\ygopro-core` (pinned checkout) |
| Scripts / cdb / lflists | `C:\ProjectIgnis` — `script/`, `expansions/*.cdb`, `lflists/` (until Phase 2.5 pins a bundle) |
| WindBot (reference only) | `C:\ProjectIgnis\WindBot` — `Executors/`, `Decks/`, `Dialogs/`, `bots.json` |
| Smoke test | `gradlew ocgSpike -PocgLib=native/ocgcore.dll -PocgScripts=C:/ProjectIgnis/script` |
| Licensing | Mod GPL-3.0 · core+scripts AGPL-3.0 (combination effectively AGPL-governed) · card IP Konami — details in [[ocgcore-integration]] |
