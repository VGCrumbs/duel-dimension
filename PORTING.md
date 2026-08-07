# Fabric fork — porting status

This branch is the Fabric fork of Duel Dimension, targeting the current
Fabric stack. The Forge tree it forks from is kept in-tree at `forge-src/`
(moved, so git history follows it) and stays the canonical, playable mod
until this fork reaches parity.

## Toolchain

| | |
|---|---|
| Minecraft | **26.2** (date-versioned; requires Java 25 per its launcher manifest) |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.156.0+26.2 |
| Loom | 1.17.18, plugin id `net.fabricmc.fabric-loom` |
| Gradle | 9.7.0 (`gradlew25.cmd` runs it on the local JDK 25) |
| Mappings | **none** — Minecraft stopped shipping obfuscated with the move to date versions, so the jar's own names are Mojang's names. That is the same vocabulary the Forge tree was written against (ForgeGradle's parchment channel layers docs over official names), which is why ported files keep their vanilla references untouched. There is no remapping step anywhere: published mods are plain `implementation` dependencies now. |

Build and test:

    .\gradlew25.cmd test

## What is across (phase 0 — done)

The loader-agnostic core: **74 main classes (72 ported + 2 entrypoints), 26 test
classes, 86 tests green**, including the native-core duels — `native/ocgcore.dll` is tracked,
so `BotDisciplineTest` and friends run the real ygopro-core engine inside
the Fabric workspace.

- `ocg/**` — the whole engine layer: JNA binding, message decoding, session,
  prompts (`EnginePrompt`/`BoardSnapshot` use `FriendlyByteBuf`, which is
  unchanged in 26.2 and compiled as-is), board queries, bots, deck loading.
- `duel/match` — banlists, match config/state.
- Pure data/utility classes that happened to be loader-clean.

Selection was mechanical, then honest: `tools/port_scan.py` copied every
file with no Minecraft/Forge/Mojang imports, and `tools/port_prune.py`
compiled and removed whatever referenced a class that stayed behind. The
removals are the phase lists below (`build/prune-*.txt` has the raw lists).

## What is not, and where it goes

Ordered so each phase unblocks the next. The version gap (1.19.2 → 26.2)
is usually the larger half of the work, not Forge-vs-Fabric.

1. **Profile & shop data** — `DeckList`, `Trunk`, `DeckLimits`, `DeckEdits`,
   `DuelProfile(s)`, `DuelPoints`, and their five test classes. NBT-based;
   check `CompoundTag`'s API drift. Forge's `PERSISTED_NBT_TAG` becomes the
   Fabric Data Attachment API (persistent, `copyOnDeath`).
2. **Registries & content** — items, blocks, entities (`DuelistEntity`),
   sounds; `DeferredRegister` → direct `Registry.register`, creative tabs,
   `FabricEntityTypeBuilder`, upstream YgoDuelingMod content.
3. **Networking** — the ~30 message records in `ProfileMessages`,
   `LobbyMessages`, `OutfitMessages`, `ShopMessages`, duel messages.
   SimpleChannel → `CustomPacketPayload` + `StreamCodec` +
   `PayloadTypeRegistry`/`ServerPlayNetworking`.
4. **Server events & commands** — login/logout/respawn hooks →
   `ServerPlayConnectionEvents` etc.; command registration; `FreeMode`
   SavedData; duel lifecycle driving (`DuelistDuels`).
5. **Client** — the big one: every screen (hub, editor, shop, pack opening,
   card info, lobby, `EngineDuelScreen`), `FieldQuad`/`BoardRenderer`,
   `DuelAnimations`, textures/mipmapping, `OutfitLayer`/`PlayerSkins`
   (entity render state refactor landed since 1.21.2; no
   `RenderPlayerEvent`/`RenderArmEvent` on Fabric — mixins into the player
   renderer replace them), `HitchWatch`, keybinds. Expect
   `PoseStack`-era GUI code → `GuiGraphics`-era plus the newer render
   pipeline work.
6. **Parity audit** against the Forge branch; only then does this branch
   take over.

## Decisions

- **One repo, `fabric` branch**, not a second repository: the fork shares
  its history with `crumby`, so fixes to the engine core can cherry-pick
  across in either direction.
- **Assets stay in `forge-src` until their feature ports**, so nothing is
  duplicated; each phase `git mv`s what it needs (the starter deck `.ydk`
  lists came across with phase 0 because the deck tests read them).
- `OutfitSkinFormatTest` is parked with phase 5 — it validates outfit PNGs
  that live with the unported client feature.
