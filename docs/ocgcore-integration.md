# ocgcore integration (automated card effects)

Duel Dimension embeds the **EDOPro-core** rules engine ([edo9300/ygopro-core](https://github.com/edo9300/ygopro-core)) in-process via JNA to get real, automated card-effect resolution — the same engine EDOPro uses, covering effectively every card. This replaces nothing: the upstream mod's manual duel simulator stays as-is; the engine is a second, "ruled" duel mode.

## Architecture (path A: in-process)

```
Minecraft server (JVM)
 └─ de.cas_ual_ty.dueldimension.ocg          Java binding layer (no MC dependencies)
     ├─ OcgApi                     JNA mapping of ocgapi.h (API v11.0)
     ├─ OcgStructs                 structs + callbacks of ocgapi_types.h
     ├─ OcgConstants               ocgapi_constants.h (MSG_*, LOCATION_*, DUEL_MODE_*, ...)
     ├─ OcgCard / OcgDuel          high-level wrapper, owns callback + buffer lifetimes
     └─ OcgSpike                   standalone smoke test (main(), no Minecraft)
          │ JNA
          ▼
 ocgcore native library (C++)  ⇄  Lua card scripts (CardScripts repo)
```

- **Server-authoritative:** the engine runs only on the (logical) server. Clients receive prompts/updates through the existing `duel/network` packet layer; hidden information never leaves the server.
- **Single-threaded per duel:** all core calls and the callbacks the core makes back into Java happen on the calling thread. Drive each duel from one thread (a dedicated engine thread, not the server tick thread — some core calls can take a while).
- **Crash risk:** a native crash takes down the JVM. That is the accepted trade-off of path A; if it becomes a problem in practice, the binding layer is process-agnostic and could be moved behind IPC later.

## What the engine needs at runtime

**All four of these now ship inside the jar** and are unpacked to
`<gamedir>/dueldimension_engine/` on first run — see "The bundled engine" below.
What follows is what each one *is* and where it comes from; a player no longer
has to obtain any of it by hand.

1. **The native library** (`ocgcore.dll` / `libocgcore.so` / `libocgcore.dylib`), built from [edo9300/ygopro-core](https://github.com/edo9300/ygopro-core). Local path: `native/` (**tracked in git**, despite what this line used to say — `git ls-files native/` returns it and `.gitignore` has no `native` entry). Sources:
   - extract from an [EDOPro release](https://github.com/edo9300/edopro/releases) (Windows x64),
   - or an existing ProjectIgnis/EDOPro installation,
   - or build from source (premake5 + MSVC/GCC, needs a Lua).
2. **Card scripts:** a checkout of [ProjectIgnis/CardScripts](https://github.com/ProjectIgnis/CardScripts). `constant.lua` + `utility.lua` are loaded once per duel; per-card scripts (`official/c<code>.lua`) are requested on demand through the script-reader callback.
3. **Card static data:** the engine asks for it by passcode through the card-reader callback (`OcgCard`). Source of truth should be [ProjectIgnis/BabelCDB](https://github.com/ProjectIgnis/BabelCDB) (`cards.cdb`, SQLite `datas` table) — **not** the YDM JSON DB, which lacks `setcodes` and the numeric bitfields the engine needs. Passcodes match YDM card ids, so joining engine data to YDM display data is a direct id match. Reading `.cdb` needs a bundled SQLite JDBC driver (e.g. `org.xerial:sqlite-jdbc` via jarJar) — not yet added.

## The bundled engine

The jar carries a copy of all four and unpacks whatever the machine is missing
into `<gamedir>/dueldimension_engine/`. `EngineBundle` does the work;
`EngineRuntime.Paths` decides what gets used.

**Preference order, per piece:** an explicit `-Docg.lib` / `-Docg.scripts` /
`-Docg.cdb` / `-Docg.strings`, then a discovered EDOPro install, then the
bundle. That order is not an accident — EDOPro updates several times a week and
its scripts and database were cut from the same commit as each other, so a
player who has one is better off on it. The bundle is what makes a machine with
nothing work.

**It is per piece, not all-or-nothing.** The common Windows case is an EDOPro
install whose core is 32-bit while Minecraft 26.2 needs 64-bit Java: that
machine takes the core from the bundle (1.5 MB) and the scripts, database and
strings from EDOPro, and 28 MB of scripts are never written.

**Versioning.** Neither the scripts nor `cards.cdb` carries a version of its own
(`PRAGMA user_version` is 0; there is no `.git` under `script/`), so the build
stamps one: `engineBundleStamp` hashes the content of everything shipped and
writes `dueldimension_engine/bundle.properties`. The unpacked tree keeps the
same value in `.bundle`, written **last** so an interrupted unpack is never
mistaken for a finished one. Same value plus the pieces already present means
nothing is done; a mod update changes the hash and the pieces are rewritten.
`bundle/provenance.properties` records which upstream snapshot was taken, and
ships alongside.

**Why it must be unpacked at all:** `HeadlessDuelRunner.cardScriptsDirectory` is
`Files.readAllBytes` over a `Path`, `Sqlite.open` builds a `jdbc:sqlite:` URL
that the driver opens as a real file, and JNA is handed an absolute path. None
of the three can see a classpath resource.

**Not bundled: the banlists.** [ProjectIgnis/LFLists](https://github.com/ProjectIgnis/LFLists)
carries no licence file and no distribution repository supplies one, so
`Banlists` still discovers them from EDOPro or `<gamedir>/lflists`. See
`NOTICE.md`.

## The duel loop

```
OCG_CreateDuel(options)        options: seed (non-zero!), flags (DUEL_MODE_MR5 etc.),
                               per-team LP/draw counts, card/script/log callbacks
load constant.lua, utility.lua
OCG_DuelNewCard(...)           per card, LOCATION_DECK / LOCATION_EXTRA
OCG_StartDuel
loop:
  status = OCG_DuelProcess
  messages = OCG_DuelGetMessage      [uint32 len][uint8 msgType + payload]...
  render messages to players (MSG_MOVE, MSG_DRAW, MSG_SELECT_CARD, ...)
  if status == AWAITING: OCG_DuelSetResponse(answer from prompted player)
  if status == END: done
```

The ~95 `MSG_*` payload formats are not documented in the core headers; the reference implementations are EDOPro's client (`gframe/duelclient.cpp`, `CoreUtils`) and the core's `playerop.cpp`. Building the Minecraft-side UI for the interactive subset (`MSG_SELECT_*`) is the bulk of the remaining work.

## JNA pitfalls handled in `OcgDuel` (do not "simplify" these away)

- **Callback GC:** the core stores raw function pointers; the wrapper keeps strong Java references to all four callbacks for the duel's lifetime.
- **`setcodes` lifetime:** the card reader hands the core a 0-terminated `uint16*`; that `Memory` must stay alive until the matching `cardReaderDone`. Tracked in a map keyed by native address.
- **Volatile buffers:** `OCG_DuelGetMessage` / `OCG_DuelQuery*` return core-owned buffers only valid until the next call — always copied out immediately.
- **Version check:** `OCG_GetVersion` major must equal 11, else struct layouts differ and everything breaks subtly.

## Build/toolchain notes

- JNA is not bundled: Minecraft 1.19.2 itself ships JNA 5.10.0 (via oshi), on both client and dedicated server. The explicit `implementation` dep in `build.gradle` pins the same version for compilation and the spike.
- Smoke test (no Minecraft): `gradlew ocgSpike -PocgLib=native/ocgcore.dll [-PocgScripts=<CardScripts checkout>]`
- Gradle 7.3.3 (this repo's wrapper) requires a JDK ≤ 17 to run.

## Licensing

- EDOPro-core and CardScripts are **AGPL-3.0-or-later**; this mod is **GPL-3.0** (inherited from YgoDuelingMod). GPLv3 §13 permits combining/linking GPLv3 work with AGPLv3 work; the combined work must satisfy both licenses (effectively AGPL terms govern the combination). Practical consequences: keep the mod open source, keep all license notices, and if the mod is ever run as a hosted network service, the AGPL source-offer obligation applies to the core.
- Card names/text/art remain Konami IP — same fan-project footing as upstream YDM and EDOPro themselves. Non-commercial, no Konami branding.
