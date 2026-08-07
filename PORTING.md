# Fabric fork — porting status

This branch is the Fabric fork of Duel Dimension, targeting the current
Fabric stack. The Forge project stays in its own folder next door at
`../CrumbyDueling` and remains the canonical, playable mod until this fork
reaches parity. Nothing here writes to it; the porting tools read from it.

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

## API drift worth knowing before you port anything

Measured off the 26.2 jars with `javap`, not guessed. These bite every phase:

- **`ResourceLocation` is `Identifier` again** (`net.minecraft.resources.Identifier`),
  built with `Identifier.fromNamespaceAndPath(namespace, path)`.
- **`CompoundTag` getters return `Optional`.** `getString(k)` is
  `Optional<String>`; the old behaviour is `getStringOr(k, "")`. Likewise
  `getIntOr`, `getBooleanOr`, `getCompoundOrEmpty`, `getListOrEmpty`.
  `contains(k)` no longer takes a type. `ListTag` reads went the same way.
- **`SavedData` is Codec-driven.** No `save(CompoundTag)` override; you
  declare a `SavedDataType<>(Identifier, constructor, Codec, DataFixTypes)`
  and ask the storage to `computeIfAbsent(TYPE)`.
- **Fabric attachments persist via Codec too**, not NBT.
- `CompoundTag.putUUID`/`getUUID`/`hasUUID` are gone; use
  `tag.store(key, UUIDUtil.CODEC, uuid)` and `tag.read(key, UUIDUtil.CODEC)`.
- `Entity.level` is private — `level()`.
- `Mth.createInsecureUUID()` needs a `RandomSource`.
- `ItemStack` has **no NBT at all**: `getOrCreateTag()` and friends were
  replaced by the data-component system. This is the single biggest reason the
  item layer is a rewrite rather than a port.
- Forge's capability system has no equivalent; `ItemStackHandler`,
  `INBTSerializable` and `getCapability` all need replacing with attachments
  or plain fields.

Which is why phase 1 replaced the hand-written `save()`/`load()` pairs with
Codecs rather than translating them: both places a profile now persists ask
for a Codec, and a class that has one *and* hand-writes its own NBT has two
descriptions of its own shape to keep in step. The field names are unchanged,
so a world written by the Forge build reads here.

One trap worth stating: **register attachments from a nested holder class,
not a static field of the class itself.** A static `AttachmentType` means
merely loading the class initialises Fabric's registry, which needs a running
game — and a unit test asking a pure question (what does a win pay?) dies on
`NoClassDefFoundError` before it gets there. `DuelPoints.Storage` and
`DuelProfiles.Storage` show the shape.

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

1. ~~**Profile & shop data**~~ — **done.** `DeckList`, `Trunk`, `DeckLimits`,
   `DeckEdits`, `DuelProfile`, `DuelProfiles`, `FreeMode`, `DuelPoints` and
   five test classes; **121 tests green**. Forge's `PERSISTED_NBT_TAG` became
   persistent, `copyOnDeath` data attachments; `FreeMode` became a
   `SavedDataType`. `DuelProfiles` lost its `Map<UUID, DuelProfile>` cache —
   the attachment *is* the storage, so a read gives the live object and there
   is no flush to remember. Two assertions are parked here that measure a
   reward against `ShopStock.BASE_PRICE`; restore them with phase 2.
2. **In progress — the card data layer is across.** `DdDatabase`, `Properties`
   and the whole `card/properties` package, `CardHolder`, `CardSet`, the
   rarity and set data, `DNCList`, the task workers, `ShopStock`, the sided
   proxy, and a JSON-file `CommonConfig` replacing `ForgeConfigSpec` — which
   kept the `Value.get()` shape so three dozen call sites did not have to
   change. **128 tests green.**

   Still to do in this phase, and all of it blocked on the same two things —
   there are no items and no registries yet:
   `CardItem`, `CardSleevesItem`/`Type`, `CardSetItem`/`BaseItem`/`Opened`,
   `ItemStackCardHolder`, the three `CardPuller`s and `PullType`,
   `CardSetContainer`(`Contents`), `YDMItemHandler`, `ICooldownHolder`, and
   `DdUtil`'s two command-executing methods. Each is parked with a comment
   saying so rather than deleted.

3. **Registries & content** — items, blocks, entities (`DuelistEntity`),
   sounds; `DeferredRegister` → direct `Registry.register`, creative tabs,
   `FabricEntityTypeBuilder`, upstream YgoDuelingMod content.
4. **Networking** — the ~30 message records in `ProfileMessages`,
   `LobbyMessages`, `OutfitMessages`, `ShopMessages`, duel messages.
   SimpleChannel → `CustomPacketPayload` + `StreamCodec` +
   `PayloadTypeRegistry`/`ServerPlayNetworking`.
5. **Server events & commands** — login/logout/respawn hooks →
   `ServerPlayConnectionEvents` etc.; command registration; `FreeMode`
   SavedData; duel lifecycle driving (`DuelistDuels`).
6. **Client** — the big one: every screen (hub, editor, shop, pack opening,
   card info, lobby, `EngineDuelScreen`), `FieldQuad`/`BoardRenderer`,
   `DuelAnimations`, textures/mipmapping, `OutfitLayer`/`PlayerSkins`
   (entity render state refactor landed since 1.21.2; no
   `RenderPlayerEvent`/`RenderArmEvent` on Fabric — mixins into the player
   renderer replace them), `HitchWatch`, keybinds. Expect
   `PoseStack`-era GUI code → `GuiGraphics`-era plus the newer render
   pipeline work.
7. **Parity audit** against the Forge branch; only then does this branch
   take over.

## A bug the port found

`DdUtil` executes configured commands when a duel ends. Two of the four
branches in each of its two command blocks called
`ConfigValue.getPath()` instead of `.get()` — which returns the setting's
*path in the config file*, not the commands. It compiled because both are
`List<String>`, so it has presumably always run the strings
`["common", "commandCooldowns", ...]` as commands in those two cases.

Fixed here. **The Forge tree still has it**, at
`util/DdUtil.java` — four call sites, `grep -n "CDCommands.getPath()"`.

## Decisions

- **One repo, `fabric` branch**, not a second repository: the fork shares
  its history with `crumby`, so fixes to the engine core can cherry-pick
  across in either direction.
- **Assets stay in `forge-src` until their feature ports**, so nothing is
  duplicated; each phase `git mv`s what it needs (the starter deck `.ydk`
  lists came across with phase 0 because the deck tests read them).
- `OutfitSkinFormatTest` is parked with phase 5 — it validates outfit PNGs
  that live with the unported client feature.
