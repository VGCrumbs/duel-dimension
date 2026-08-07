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
- `Item.Properties.setId(ResourceKey<Item>)` is now **required** — an item has
  to know its own registry id before it is constructed.
- `Item.Properties.tab(...)` is gone and so is `fillItemCategory`. A tab
  supplies its own contents; an item says nothing about tabs.
- `appendHoverText` takes `(stack, TooltipContext, TooltipDisplay,
  Consumer<Component>, TooltipFlag)` — lines are fed to a consumer, not added
  to a list.
- `Item.use` returns `InteractionResult`; `InteractionResultHolder` is gone.
- `Level.isClientSide` is a method.
- **Fabric API dropped `FabricItemGroup`** for 26.2 — the module is not even a
  dependency of the umbrella any more. Use vanilla's
  `CreativeModeTab.builder(Row, int)`.
- `Material`/`MaterialColor` were removed. A block states its map colour,
  sound and strength separately; blocks need `setId` like items.
- `new SoundEvent(location)` became `SoundEvent.createVariableRangeEvent(id)`.
- Play packets travel on `RegistryFriendlyByteBuf`, not `FriendlyByteBuf`.
  Codecs that touch no registry (`ByteBufCodecs.VAR_INT` and friends) are
  typed on plain `ByteBuf` and need `.cast()` to widen.
- `PayloadTypeRegistry` methods are `serverboundPlay()`/`clientboundPlay()`.
- `CommandSourceStack.sendSuccess` takes a `Supplier<Component>` — the message
  is only built if someone is listening.
- Permission levels became named checks:
  `.requires(source -> source.hasPermission(2))` is
  `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`.
- `ClickEvent`/`HoverEvent` are sealed interfaces with a record per action, so
  the action is the *type*: `new ClickEvent.RunCommand(cmd)`. An event that
  cannot carry the right payload for its action is now unrepresentable.
- Entities: `defineSynchedData(SynchedEntityData.Builder)`; save/load use
  `ValueOutput`/`ValueInput`, not `CompoundTag`; `hurt` is `hurtServer(level,
  source, amount)`; `canBeLeashed()` takes no player;
  `InteractionResult.sidedSuccess(isClient)` is gone — `SUCCESS` is side-aware.

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
2. **In progress — the card data layer and the item foundation are across.** `DdDatabase`, `Properties`
   and the whole `card/properties` package, `CardHolder`, `CardSet`, the
   rarity and set data, `DNCList`, the task workers, `ShopStock`, the sided
   proxy, and a JSON-file `CommonConfig` replacing `ForgeConfigSpec` — which
   kept the `Value.get()` shape so three dozen call sites did not have to
   change. **128 tests green.**

   Registration now works end to end: `DdComponents` (the card component),
   `DdItems` (registry + `setId`), `DdItemGroup` (two creative tabs),
   `CardItem`, `CosmeticItem`, `ItemStackCardHolder`. **128 tests green, 121
   classes.**

   The interesting piece is `DdComponents`. `ItemStack` has no NBT at all, so
   the four values the Forge build wrote into a stack's tag — card id, artwork
   index, rarity, print code — became a typed component with a Codec for disk
   and a stream codec for the wire. `ItemStackCardHolder` reads and writes that
   instead, and since a component is immutable it has to put a fresh one back
   rather than mutate in place.

   `DdSounds` is across too — all seventeen duel effects.

   Still to do in this phase: `CardSleevesItem`/`Type`,
   `CardSetItem`/`BaseItem`/`Opened`, the three `CardPuller`s and `PullType`,
   `YDMItemHandler`, `ICooldownHolder`, `DdUtil`'s two command-executing
   methods, and **the block layer** — `DuelBlock`, `CardShopBlock`,
   `CardSupplyBlock` and their block entities. The blocks are parked as one
   unit with the containers below, because each of them exists to open a menu
   and a block that cannot open one is not worth registering. Blocks now also
   need a `MapCodec` (`codec()`), which is new since 1.19.

3. **Registries & content — mostly done.** Items, components, creative tabs,
   sounds and entities all register; `DdEntityTypes` includes the duelist's
   attributes, which Fabric asks for directly rather than through an event.

   What is left here is one cluster, and it is blocked on a **decision, not on
   drift** — see below.

4. **Registries & content (was 3)** — items, blocks, entities (`DuelistEntity`),
   sounds; `DeferredRegister` → direct `Registry.register`, creative tabs,
   `FabricEntityTypeBuilder`, upstream YgoDuelingMod content.
4. **Networking — started; the pattern is proved end to end.** `DdNetwork`
   plus `ProfilePayloads`: all ten profile messages, registered by direction,
   with the server handlers and a join hook that syncs a player's profile.
   **124 classes, 128 tests green, jar builds.**

   Forge gave every mod a `SimpleChannel` with a hand-assigned integer per
   message. Minecraft grew its own version and it is stricter in the ways that
   matter: a message is a `CustomPacketPayload` with a `Type` naming it, a
   `StreamCodec`, and a registration that says which *direction* it may travel
   — no ids to keep in step, and a message registered the wrong way round is
   refused rather than mis-parsed.

   One improvement fell out for free: the Forge `Sync` sent a profile as a raw
   `CompoundTag` and trusted both ends to agree what was in it. A profile has a
   Codec now, so the message carries a `DuelProfile` and the wire format is the
   one thing neither side can get wrong.

   `OutfitMessages` and `LobbyMessages` followed, with the outfit and match
   layers under them (`Outfits`, `WornOutfits`, `MatchConfig`, `DuelLobby`).
   **130 classes, 128 tests green.**

   Those two were nearly free, and the reason is worth writing down.
   `tools/port_payloads.py` converts a Forge message record in place, because
   the Forge records were already the right shape and nobody planned it that
   way: each carries `static void encode(T, FriendlyByteBuf)` and
   `static T decode(FriendlyByteBuf)`, which are exactly
   `StreamMemberEncoder.encode(T, B)` and `StreamDecoder.decode(B)`. So
   `CustomPacketPayload.codec(X::encode, X::decode)` reuses the existing
   methods verbatim — the wire format is never retyped, which is how a port
   quietly changes one. Each record gains a `Type`, that codec, and `type()`.

   What the tool does **not** do is handlers: `handle(msg, Supplier<Context>)`
   has no equivalent, since Fabric hands the payload and the sender to a
   receiver registered by direction. `tools/park_handlers.py` comments each one
   out so the body survives as the record of what the message is for, and the
   logic moves to `DdNetwork.registerServerHandlers` by hand.

   `ShopMessages`, `PromptMessages` and `PackMessages` followed, and with them
   the duel-session layer: `DuelistDuels`, `DuelistEntity`, `DuelInvites`, the
   commands, `ShopStock` purchases. **140 classes, 128 tests green.**

   Remaining: `CardSupplyMessages` and the older `duel/network` messages —
   both tied to containers, so they go with that phase.
5. **Server events & commands** — login/logout/respawn hooks →
   `ServerPlayConnectionEvents` etc.; command registration; `FreeMode`
   SavedData; duel lifecycle driving (`DuelistDuels`).
6. **Client — started. Read this before continuing.**

   `GuiGraphics` **does not exist in 26.2.** The GUI is retained-mode: a screen
   no longer draws itself, it *describes* itself to a `GuiGraphicsExtractor`
   and the game draws everything afterwards in one pass. So the client is not
   a `PoseStack` → `GuiGraphics` translation (the 1.20 change) — it is two
   generations of rework, and the second one removed immediate-mode drawing
   altogether.

   What that costs, measured across the 22,295 lines of client code:
   **59 files use `PoseStack`, 37 use `RenderSystem.setShader`, 17 use
   `DdBlitUtil`.** Everything in that last group builds its own quads, and
   there is nothing to build them with any more.

   Across so far: `HubTextures`, `NineSlice`, `HubWidgets`, `HubKeybinds`, and
   a `DuelHubScreen` frame. **Y opens the hub; the panel, tab strip and buttons
   are the Forge build's own art, drawn through the new API.** Each tab says
   what it is waiting for rather than showing an empty body.

   The client also has a mixin config now (`dueldimension.mixins.json`), which
   the Forge tree never needed. `PlayerSkins` is why: a dev client is offline,
   so the game has nowhere to fetch a skin from and every dev player is Steve.
   On Forge that took two hooks and a compromise — `RenderPlayerEvent.Pre` to
   hide the real body and a render layer to draw a replacement over it, because
   the skin itself could not be changed. Fabric has neither event, so a mixin
   patches `AbstractClientPlayer.getSkin` at the source instead, and the proper
   fix is the smaller one: every part of the game that draws a player, the
   3D-skin-layers mod included, picks it up without knowing anything happened.
   `PlayerSkin.Patch` replaces only the fields named, so capes and elytra
   textures survive.

   Two facts worth having up front:

   - A **screen** implements `extractRenderState` (`Renderable`'s single
     method); a **widget** implements `extractContents`. Swapping them compiles
     as a new method and silently draws nothing.
   - Retained mode draws in the order described, so a screen's background must
     be described **before** `super.extractRenderState`, not after.

   Encouragingly, `NineSlice` came out *shorter* than the Forge version: a
   nine-slice is a UV window and the extractor's blit takes one directly, so
   the hand-rolled quad building simply went away. The parts that will not go
   that way are `FieldQuad`, `BoardRenderer` and `DuelAnimations`, which
   project card quads onto a duel field in 3D — that is genuine geometry and
   needs redesign, not translation.

7. **Client, the rest** — the big one: every screen (hub, editor, shop, pack opening,
   card info, lobby, `EngineDuelScreen`), `FieldQuad`/`BoardRenderer`,
   `DuelAnimations`, textures/mipmapping, `OutfitLayer`/`PlayerSkins`
   (entity render state refactor landed since 1.21.2; no
   `RenderPlayerEvent`/`RenderArmEvent` on Fabric — mixins into the player
   renderer replace them), `HitchWatch`, keybinds. Expect
   `PoseStack`-era GUI code → `GuiGraphics`-era plus the newer render
   pipeline work.
8. **Parity audit** against the Forge branch; only then does this branch
   take over.

## Menus with extra data — resolved

Four of this mod's containers were opened with Forge's `IContainerFactory`,
which hands the menu a `FriendlyByteBuf` of extra data written at the moment
it opens — which duel, which card set, which supply block.

**There is no equivalent to port them onto.** Vanilla's `MenuType` offers only
`create(int, Inventory)`, and `ServerPlayer.openMenu` takes a bare
`MenuProvider`; neither carries extra data. Fabric API used to fill that gap
with `ExtendedScreenHandlerType`, and **that module is not in Fabric API
0.156.0+26.2** — the same disappearance as `FabricItemGroup`.

So this is a design choice rather than a translation, and there are two
honest options:

- **Send the data as its own payload** immediately after opening the menu, and
  have the screen wait for it. Keeps the menus as they are; adds a frame where
  the screen is open and empty.
- **Derive the data server-side** from what the menu already knows (the block
  position, the held stack), so nothing needs sending. Cleaner where it works,
  and it does not work for everything — the duel menu genuinely needs to be
  told which duel.

**Decided: send the data as its own payload, one packet *ahead* of the menu.**
`net/MenuData` implements it, with four tests.

The deciding fact is that `DuelBlockContainer` looks up a block entity from its
position *while constructing* — so anything arriving after construction
(`ContainerData`, a second payload, a slot sync) is too late by construction
rather than merely by timing. Deriving the data server side was the other
candidate; it covers the two block menus and cannot cover the duel menu, which
genuinely has to be told which duel. One mechanism for all four beats two that
each cover some.

Sending it *before* rather than after is what makes it safe: a play connection
is a single Netty channel, so packets arrive in the order they were sent. The
data is already there when the constructor runs — no race to lose, and no frame
where the screen is open and empty. It also leans on nothing but vanilla and
this mod's own payload registration, which matters after watching two Fabric
helpers vanish (`FabricItemGroup`, `ExtendedScreenHandlerType`) in this very
port.

Still parked as one unit, but on work rather than on a question:
`DdContainerTypes`, the six containers, the blocks (`DuelBlock`,
`CardShopBlock`, `CardSupplyBlock`), the block entities, `CardSupplyMessages`,
the older `duel/network` messages, and the item classes that open them.

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
