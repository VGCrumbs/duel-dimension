# Fabric fork — porting status

This branch is the Fabric fork of Duel Dimension, targeting the current
Fabric stack. The Forge project stays in its own folder next door at
`../CrumbyDueling` and remains the canonical, playable mod until this fork
reaches parity. Nothing here writes to it; the porting tools read from it.

A duel is played in two places, on a flat screen or on a board in the world,
and the two are meant to be one duel presented twice. Where they are not,
**`DUEL_PARITY.md`** is the list -- what each shows, what each can answer, and
the handful of places the same click means different things. Read it before
changing either presentation; add to it when the two drift.

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

### Found while porting the outfit system and the card pages

| Forge / older | 26.2 | why it matters |
| --- | --- | --- |
| `net.minecraft.Util` | `net.minecraft.util.Util` | Moved out of the root package. The simple name is unchanged, so only a fully qualified use breaks — and it breaks at compile time, so it is safe. In `port_rewrite.py`'s `RENAMES`. |
| `new ClientAsset.ResourceTexture(path)` | `new ClientAsset.ResourceTexture(assetId, path)` | **The one-argument form takes an ASSET ID and derives the file: `ns:foo` becomes `ns:textures/foo.png`.** Handing it a path that is already complete wraps it twice and the result does not exist. This is silent — no warning, no exception, just a player rendered in missing-texture magenta. `OutfitSkins.asset` works the id back out of the path so the pair stays consistent. |
| `CreativeModeTab.builder(Row, column)` | `FabricCreativeModeTab.builder()` | Vanilla's own fourteen tabs fill both rows of the strip; there is no free slot to ask for, and a mod tab given one lands **on top of** a vanilla tab. Fabric's builder puts mod tabs on pages of their own. The module is `fabric-creative-tab-api-v1` — `FabricItemGroup` from `fabric-item-group-api-v1` is the old name and is not what 26.2 ships. |
| `RenderLayer.render(PoseStack, MultiBufferSource, int, T entity, ...)` | `RenderLayer.submit(PoseStack, SubmitNodeCollector, int, S state, float, float)` | A layer is given a render **state**, never the entity. Anything it needs about *who* the entity is has to be put on the state during extraction. |
| `PlayerRenderer` / `PlayerModel<AbstractClientPlayer>` | `AvatarRenderer` / `net.minecraft.client.model.player.PlayerModel` | `PlayerModel` is no longer generic and lives in `.model.player`. `AvatarRenderState` carries a `PlayerSkin` and the cape fields but **no identity**. |
| `model.setupAnim(...)` before drawing | `collector.submitModel(model, state, ...)` | The pose is applied at draw time from the state that was submitted alongside the model, which is why vanilla's own layers never call `setupAnim`. Layer models can therefore be shared across every entity on screen. |
| `RenderType.entityCutoutNoCull` | `RenderTypes.entityCutout` | The names swapped round: `entityCutout` is the no-cull one and `entityCutoutCull` is the culled one. Getting this backwards makes a jacket invisible from the inside. |
| `ItemStackHandler` (Forge capability) | `net.minecraft.world.Container` | See the container decision below. |
| `handler.serializeNBT()` | `serializeNBT(HolderLookup.Provider)` | An `ItemStack` is written through its codec now, and that codec resolves the item against the registry. Saving a slot is no longer something an object can do alone. |
| `Screen.renderBackground(pose)` | `Screen.extractBackground(graphics, mouseX, mouseY, partialTick)` | |
| `screen.renderTooltip(pose, text, x, y)` | `graphics.setTooltipForNextFrame(font, text, x, y)` | A tooltip belongs to the frame, not to the widget, so a **screen** sets it — and it has to be set after the widgets are extracted or something described later covers it. |
| `Screen.renderables` | `Screen.children()` | `renderables` is private now. |
| `minecraft.setScreen` | `minecraft.setScreenAndShow` | |

### Found while porting EDOPro's ImageManager (card art off the render thread)

All of these were read off `javap -c` against
`~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`. This tree runs
unobfuscated MC, so there is no refmap and a mixin target has to be the runtime
name exactly — which makes reading the bytecode the only way to be sure.

| what | what the bytecode actually does | why it matters |
| --- | --- | --- |
| `TextureManager.getTexture(Identifier)` | on a miss: `new SimpleTexture(id)` → `registerAndLoad` → `loadContentsSafe` (read + decode) → `apply` (GPU), all inline | **A blit is where a first sighting is paid for.** `GuiGraphicsExtractor.innerBlit` resolves `getTexture` eagerly, before it builds the `BlitRenderState`, so a retained-mode GUI does not save you from it. |
| `TextureContents.load(ResourceManager, Identifier)` | `getResourceOrThrow` → `Resource.open` → `NativeImage.read` → `metadata()`. **No GPU call.** `public static`. | The decode half, safe on a worker. Vanilla runs the same call on an executor: `TextureManager.scheduleLoad` is `CompletableFuture.supplyAsync(() -> loadContents(...), executor)`. |
| `ReloadableTexture.apply(TextureContents)` | `SamplerCache.getSampler` → `doLoad` (`createTexture` + `createTextureView` + `writeToTexture`) → `NativeImage.close()`. `public`. | The GPU half, render thread only. **It closes the image for you**, so the decode result must not be closed again by the caller on the success path. |
| `NativeImage.close()` | null-checks `pixels`, frees, zeroes it | Idempotent, so a defensive `close()` in a discard path cannot double-free. |
| `FallbackResourceManager.getResource` | builds a **new** `Resource` per call (`createResource`) | `Resource.metadata()` is a lazy memoisation with no happens-before edge, so it would be a data race — except that no two threads ever hold the same `Resource`. Do not "optimise" this by caching `Resource` objects. |
| `Minecraft.renderFrame` | `GameRenderer.extract(DeltaTracker, boolean)` at offset 441, then `GameRenderer.render` at 520 | Once a frame, unconditional. It is the only named point in the frame that is neither inside the draw nor inside the task drain, which is what a per-frame pump wants. |
| `BlockableEventLoop.runAllTasks()` | `while(pollTask());` | **A task submitted through `Minecraft.execute` that re-submits itself is picked up again in the same drain and the frame never ends.** A per-frame pump cannot live there. |
| `TextureManager.register/release` | `byPath.put` / `byPath.remove`, `release` is null-safe | Both public. Pre-registering under an Identifier is how you hand a texture to a retained-mode GUI that only takes Identifiers. |
| `DeltaTracker.getRealtimeDeltaTicks()` | wall-clock ms ÷ `msPerTick`, and `Minecraft` builds its timer with `1000/20 = 50` | Multiply by 50 to get milliseconds. Clamped to 0.5 once a frame passes 350 ms, so it under-reports after a real stall. |

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

   Across so far: `HubTextures`, `NineSlice`, `HubWidgets`, `HubKeybinds`,
   `PlayerSkins`, `EditorState`, and `DuelHubScreen`. **Y opens the hub, and
   Profile and Decks show the player's real collection** — what they own, the
   decks they have built, the active one, whether free mode is on. Outfit and
   Settings still say what they are waiting for.

   `EditorState` cost almost nothing: 687 lines whose only loader dependency
   was `send()`. Forge's `channel.sendToServer(Object)` became
   `ClientPlayNetworking.send(payload)`, which is typed — a message that was
   never registered is a compile error now rather than a packet that vanishes.
   And `accept` takes a `DuelProfile` instead of unpacking a `CompoundTag`,
   because the sync and the disk share one Codec, so the client cannot read the
   format differently from the way the server wrote it.

   Nothing in the hub yet CHANGES a deck — use, rename, duplicate, delete and
   the editor all need `DeckEditorScreen` or the confirmation dialogue. Buttons
   that do nothing would be worse than no buttons.

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

   **`DeckEditorScreen` is across** — 2,371 lines, the largest client file.
   `tools/port_screen.py` does the mechanical half and is worth reusing for the
   screens that follow; what it deliberately leaves alone is
   `RenderSystem.setShaderColor` and the raw blits, because a tint is an
   argument to a blit now and working out *which* blit a colour was modifying
   is reading code, not matching a pattern.

   Three more things changed that the hub was too small to reveal:

   - **Input is objects.** `mouseClicked(double, double, int)` is
     `mouseClicked(MouseButtonEvent, boolean)`; likewise `KeyEvent` and
     `CharacterEvent`. A genuine improvement — `hasShiftDown` used to be a
     static on `Screen` reaching for global state and is now carried by the
     event that needs it.
   - **The GUI matrix is 2D** (`Matrix3x2f`). `poseStack.translate(0, 0, 400)`
     — the trick that lifted text above a panel drawn later — has no
     equivalent and needs none: retained mode draws in the order described.
   - `AbstractWidget.x`/`y` are private; `Button.onPress` takes the input that
     caused it.

   Two facts worth having up front:

   - A **screen** implements `extractRenderState` (`Renderable`'s single
     method); a **widget** implements `extractContents`. Swapping them compiles
     as a new method and silently draws nothing.
   - Retained mode draws in the order described, so a screen's background must
     be described **before** `super.extractRenderState`, not after.

   **Container screens and widgets — the retained-mode shape (verified against
   vanilla's own bytecode, not guessed).** An `AbstractContainerScreen` no
   longer has `render`/`renderBg`/`renderLabels`. Reading vanilla's
   `ContainerScreen`/`AbstractContainerScreen` in the 26.2 jar:
   - `imageWidth`/`imageHeight` are **final** — pass them to the `super(menu,
     inv, title, w, h)` constructor rather than assigning them.
   - The **panel background** is an `extractBackground(extractor, mx, my, pt)`
     override: call `super` (it darkens the world behind), then blit the panel
     at absolute `leftPos, topPos` with `extractor.blit(RenderPipelines
     .GUI_TEXTURED, texture, x, y, u, v, w, h, 256, 256)` (or `DdBlitUtil`).
     This is exactly what vanilla's chest screen does.
   - The base `extractContents` then `pose().translate(leftPos, topPos)` and
     calls `extractLabels` + the slots, so **foreground text goes in an
     `extractLabels(extractor, mx, my)` override in panel-local coordinates** —
     the same 8,6 offsets the Forge `renderLabels` used. Slot rendering and the
     hovered-slot highlight are the base's job; do not reimplement them.
   - Buttons added with `addRenderableWidget` in `init()` are drawn for free.
   - Input is objects: `keyPressed(KeyEvent)` (`keyEvent.key()` is the keycode),
     `mouseClicked(MouseButtonEvent, boolean)`; `channel.send` →
     `ClientPlayNetworking.send(payload)`.
   `carditeminventory/CIIScreen` is the worked example.

   **Widgets:** `Button` is **abstract** in 26.2 (its public constructor is gone
   too). A button subclass calls `super(x, y, w, h, title, onPress,
   DEFAULT_NARRATION)` and implements the abstract `extractContents(extractor,
   mx, my, pt)` — `extractDefaultSprite(extractor)` draws the standard button
   (the label is added around it by the base). A custom-look widget blits its
   own texture there instead. The Forge `renderButton(PoseStack)` /
   `OnTooltip`-constructor / `WIDGETS_LOCATION` sheet are all gone;
   `clientutil/widget/ImprovedButton` is the worked example.

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

## The outfit system — how it shrank

Forge drew a duelist in an outfit with **four baked models, a hidden vanilla
body and a layer that redrew the player from scratch**. The reason was not
ambition: the body underneath had to be the shape the clothes were cut for (a
classic arm is a pixel wider at each side than an Alex sleeve and stuck out from
under it), and on that loader there was no way to say so — a skin could not be
changed without a mixin, and the model type came with the skin.

Here it is **one patch to `getSkin`**, because of one fact worth writing down:

> `EntityRenderDispatcher` picks the player renderer — and therefore the classic
> or slim body — from `getSkin().model()`.

Answer that question correctly and the arms are the right width before anything
is drawn. There is nothing to hide and nothing to redraw, and `OutfitLayer` is
then only the outfit. Two passes became one; four models became two.

The pieces:

- **`OutfitSkins`** — the client-side authority. Which outfit a player is drawn
  in, and what to change about the skin the game resolved: the under-skin edit
  if they have one and are wearing something, otherwise a skin this mod supplies,
  and the slim body if a slim outfit demands it. It takes the game's answer as a
  parameter rather than asking for it, because it runs from inside `getSkin`.
- **`OutfitCarrier` + `AvatarRenderStateMixin`** — a field on the render state
  for the outfit, because a layer never sees the entity. An interface on the
  state rather than a side map keyed by state: a render state is recycled
  between frames and a map would need invalidating by something that knows when.
- **`AvatarRendererMixin`** — fills that field during extraction, the one moment
  where the renderer has both the entity and the state, and puts the outfit's
  sleeve on the first-person hand (`renderRightHand` runs no layers at all).
- **`OutfitLayer`** — the outfit, inflated a quarter pixel so it does not fight
  the skin for pixels.
- **`OutfitPreview`** — the wardrobe's figures. Extracts a **real** render state
  from the **real** renderer and hands it to `graphics.entity(...)`, with
  `OutfitSkins.previewing` set for the duration so the answer is the outfit being
  previewed rather than the one being worn. The alternative — driving bare models
  into the GUI, as Forge did — is not possible in a retained-mode GUI and would
  have been a second code path drifting away from the first.

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

**Building the `MenuType` itself needs an access widener.** Vanilla's
`MenuType` has a *private* constructor and only private `register(...)` helpers,
and the one Fabric helper that used to hand mods a public path
(`ExtendedScreenHandlerType`) is intermediary-mapped (`class_1703` …) and so
unusable against the unobfuscated jar — the same disappearance again. So the mod
carries `src/main/resources/dueldimension.accesswidener`, wired in `build.gradle`
(`loom.accessWidenerPath`) and `fabric.mod.json` (`"accessWidener"`), widening
the constructor and its package-private `MenuType$MenuSupplier`.
`DdContainerTypes` then does `new MenuType<>(supplier, FeatureFlags.VANILLA_SET)`
and `Registry.register(BuiltInRegistries.MENU, key, …)`, exactly like `DdItems`.
Three traps, all found the hard way: loom 1.17 reads the **ClassTweaker**
format, so the header is `classTweaker v1 official` — the token is `classTweaker`
not `accessWidener`, the namespace is `official` not `named` (on an
unobfuscated jar the two names coincide), and **the header must be the first
line** (a leading comment block makes the reader reject it).

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

## The duel screen — the two decisions everything else waits on

The duel screen is **one unit of 34 files and ~9,700 lines**, and it does not
chunk: the animations need the widgets, the widgets need `IDuelScreenContext`
and `DuelScreenDueling`, and the screens need both. Before any of it can be
written, two questions about how a card is drawn have to be settled, because
every widget and animation in the cluster calls through them.

### 1. A card lying on its side — settled: turn the quad

Forge drew a rotated card by **permuting the four texture coordinates** of an
axis-aligned quad: the rectangle stayed put and the art turned inside it
(`blit90Degree` and friends).

That cannot be expressed here. The extractor's blit takes a UV *window*
(`u0, u1, v0, v1`), which is axis-aligned by construction and has no way to say
"this corner maps to that one". So **the quad turns instead**, about its own
centre, using `graphics.pose().rotateAbout(...)` on the 2D GUI matrix.

The two agree exactly **when the region is square** — otherwise a quarter turn
swaps the rectangle's width and height and it stops covering the same pixels.
Every caller that asks for a quarter or three-quarter turn goes through
`renderDuelCardCentered`, which squares the region first
(`x -= (height - width) / 2; width = height;`) precisely so a sideways card
still fits its zone. So the condition holds where it matters. **Done** —
`DdBlitUtil.fullBlitTurned` and the three named wrappers.

### 2. The rarity foil — SETTLED: the two-pass trick works

This is the effect where a foil card glints as the cursor passes over it. Forge
did it in two passes with the framebuffer's alpha channel as scratch space:
draw a soft radial mask at the cursor with
`blendFuncSeparate(ZERO, ONE, SRC_ALPHA, ZERO)` so it writes only alpha, then
draw the foil with a blend that makes it visible only where that alpha is high.
`RenderSystem.blendFuncSeparate` is gone.

**It is still expressible.** `BlendFunction` has constructors taking the same
`BlendFactor`s, and `RenderPipeline.Builder.withColorTargetState(new
ColorTargetState(blendFunction))` puts one on a pipeline — and
`GuiGraphicsExtractor.blit` takes a pipeline. So the two passes become two
custom pipelines.

**The risk was batching** — the second draw has to see what the first wrote, and
a retained-mode GUI is free to reorder or merge draws. **Tested, and it holds.**
`FoilTestScreen` (bound to J) draws the two-pass version beside a control that
omits the mask pass: the two are plainly different, the glint is localised and
follows the cursor, and the control is flat. So `FoilPipelines.MASK` then
`FoilPipelines.FOIL` is the path, and `ADDITIVE` stays only as a fallback nobody
needs.

**But it does not reach inside a picture-in-picture pass.** Those pipelines are
reached through `GuiGraphicsExtractor.blit`, which draws axis-aligned rectangles
only. Anything on a turned quad goes through `FieldQuad`, which submits a
`RenderType` rather than a `RenderPipeline` — a different road entirely — so the
3D card preview (`cardbinder/CardPreviewScreen`) cannot use the two-pass trick
even though it wants the same effect. It draws the same `RarityEntry.layers`, in
the same order, as ordinary textured quads over the card mesh, with the glint's
strength written per vertex and interpolated across each cell.

That costs the cursor. The mask pass exists to localise the glint around the
pointer on a flat card; on a card the player is already turning, the phase is
driven by `yaw`/`pitch` instead, and `RarityLayerType.INVERTED` is honoured as
the arithmetic complement so a `_active`/`_passive` pair crossfades rather than
stacking two coats. Worth knowing before trying to unify the two paths: they are
the same data and deliberately not the same draw.

**And the layer art is data, not resources.** The rarity images live in
`run/ydm_db/rarity_images/` and are served through `DdCardResourcePack`, so they
will not appear under `src/main/resources` and grepping there for them finds
nothing. `run/ydm_db/rarities/*.json` defines which layers each rarity stacks.
Ask `DdDatabase.getRarity(String)`; do not write a rarity table. One was written
by hand here once and had to be deleted.

### 3. Arbitrary quads — BUILT

A perspective field has no axis-aligned rectangles in it. The GUI can only blit
those, so `FieldQuad`'s drawing half went missing in the first pass at this file.
It is back, on `SubmitNodeCollector.submitCustomGeometry`, which hands over a
`PoseStack.Pose` and a `VertexConsumer` — the same raw vertex sink the Forge code
had.

`submitCustomGeometry` only exists inside a render pass and a screen has none, so
the board is drawn as a **picture-in-picture** region (`BoardPip`) — vanilla's own
mechanism for the inventory entity and the lectern book. Fabric registers custom
PiP *renderers* but does not expose the submission side and `guiRenderState` is
private, so one accessor mixin bridges it.

Two things that would otherwise cost an afternoon:

- **Corner order is `0, 3, 2, 1`**, anticlockwise in screen coordinates. Wound
  the other way the face points away and vanishes under back-face culling.
- **An entity render type wants a full vertex** — colour, UV, overlay, light and
  normal. The old GUI shader wanted position and UV only. Leaving one unset does
  not fail; it reads whatever was in the buffer, which is how a quad comes out
  black or invisible for no visible reason.

`FieldQuad`'s draw methods therefore take `(PoseStack, SubmitNodeCollector, ...)`
rather than a `GuiGraphicsExtractor`. Every call in `BoardRenderer` and
`DuelAnimations` goes through them.

## The duel screen — what nearly shipped broken

Three defects that **compiled clean** and were found by adversarial review rather
than by the compiler. All three are the same shape: an API whose replacement
looks equivalent and is not.

**Text on the board was invisible.** `FeatureRenderDispatcher.executeTranslucent`
runs its phases in a fixed sequence within each order group:

    shadows -> translucentModels -> seeThroughNameTags -> nameTags -> texts -> translucentCustomGeometry

`FieldQuad` draws through `submitCustomGeometry`, so **text submitted at the same
order is painted over by the board described after it**. ATK/DEF, the reveal
caption and the number on a rolled die were all submitted and then buried. The
lever is `collector.order(1)`: `submitsPerOrder` is a sorted map drained
ascending, so a higher order runs strictly after. Every `submitText` in this mod
goes through `order(TEXT_ORDER)` for that reason.

**`setScreenAndShow` is not `setScreen`.** It is `gui.setScreen(s)` followed by a
forced `renderFrame(false)`. `DuelClientState.openScreen` runs inside
`tickPlayback`'s lock with the update batch half-drained, so the forced frame
painted a half-applied board. `minecraft.gui.setScreen` is the true 1:1 for
Forge's `setScreen`; reserve `setScreenAndShow` for blocking transitions, which
is what vanilla does with it.

**A picture-in-picture painter is deferred.** It is a callback the game runs
after every screen's extract has returned. `BoardRenderer.render` both drew the
board and built its hit rectangles, so moving it into the painter meant
`hits()` was read a frame before the painter rebuilt it — hover, selection marks
and the click test all working off the previous frame's board. The fix is the
split the retained-mode refactor asks for everywhere: `layout(...)` computes
every rectangle eagerly during extract, `render(pose, collector)` replays them
inside the painter. Zones and piles share one ordered plan list because the
original interleaves them per side and a nearer pile must paint over a further
one.

## Decisions

- **The jar carries the rules engine, and unpacks only the pieces the machine
  is actually missing.** `EngineBundle` writes `<gamedir>/dueldimension_engine/`
  from resources in the jar; `EngineRuntime.Paths` resolves each piece
  independently as *override → discovered EDOPro → bundle → relative fallback*.
  Three things about it are not obvious. **(1) It has to be unpacked at all**
  because none of the three readers can see a classpath resource:
  `cardScriptsDirectory` is `Files.readAllBytes` over a `Path`, `Sqlite.open`
  builds a `jdbc:sqlite:` URL that the driver opens as a real file, and JNA is
  handed an absolute path — so "just read it from the jar" is not available even
  though JNA *could* extract a native by itself. **(2) The 12,727 card scripts
  are one jar entry, not 12,727.** A jar's directories cannot be listed through
  a classloader, so per-file packaging would need an index naming every script
  (which is exactly why the bundled card extras have one); a single `script.zip`
  streams straight out instead, and it also came out **1.27 MB smaller** than
  the same files as individual entries, because 12,727 zip headers are not
  free. **(3) The unpack is per piece.** The common Windows machine has EDOPro
  with a 32-bit core under a 64-bit JVM: it takes the core from the bundle
  (1.5 MB) and scripts, database and strings from EDOPro, and never writes the
  28 MB script tree. `EngineBundle.usable()` has to test `isRegularFile` before
  asking `NativeArchitecture.loadableHere`, which answers "is there a mismatch I
  can prove" and so calls a core that is *absent* loadable — that alone made a
  fresh install decide it needed nothing.
- **The bundle stamps its own version, because nothing upstream has one.**
  `PRAGMA user_version` on `cards.cdb` is 0, there is no `.git` under `script/`,
  and a card script says only its name and its author; EDOPro's version *is* a
  DeltaBagooska commit. So `engineBundleStamp` hashes the content of everything
  shipped into `dueldimension_engine/bundle.properties`, the unpacked tree keeps
  the same value in `.bundle`, and the two are compared on start. The stamp is
  written **last**, so an interrupted unpack reads as unfinished rather than as
  current. The `Zip` task sets `preserveFileTimestamps = false` and
  `reproducibleFileOrder = true` for the same reason — otherwise every build
  would produce a new hash and every player would re-unpack 28 MB for nothing.
  Do **not** copy `installBundledExtras`'s byte-comparison freshness check here:
  at 12,702 files it re-reads 28 MB from disk on every launch. **The cheap
  substitute for it is worse than either.** `unpackScripts` first skipped any
  file whose size already matched the zip entry's, which sounds free and is
  wrong twice over: `ZipInputStream.getSize()` is populated for every one of
  these 12,727 entries, so the branch is live, and a script whose next revision
  is the same length as this one would never be written. It was proved rather
  than argued — one `official/c10000.lua` overwritten with 1,636 dashes, then
  the repair `README.md` tells a player to perform (delete `.bundle`), which
  reported `unpacked cdb, strings, library, scripts -- 3 files` and left every
  dash in place. The tree is therefore decided **as a whole, by the stamp**:
  current means nothing is written, stale means all 12,727 files are. That still
  reads nothing off the disk, so the objection above is untouched.
- **The banlists are the one thing not bundled, and it is a licence answer, not
  a size one.** `ProjectIgnis/LFLists` has no licence file — GitHub's licence
  API returns 404 and the repository root holds only `.gitattributes` and the
  `.lflist.conf` files. `cards.cdb` is in the same position at its *authoring*
  repository (BabelCDB, also unlicensed) but is rescued by the repositories it
  is *distributed* from (DeltaPuppetOfStrings, DeltaBagooska — both carry the
  AGPL, sha `0ad25db4…`); LFLists has no such chain, because EDOPro fetches it
  directly. So `Banlists` keeps discovering them and offering "No banlist".
- **Looking through your own deck mid-duel is a deliberate departure from
  EDOPro, and the randomisation is what makes it defensible.** The reference
  refuses the deck browser outside single mode (`gframe/event_handler.cpp`:
  `if(hovered_location == LOCATION_DECK && !mainGame->dInfo.isSingleMode) break;`)
  and when it does open one it shows TRUE ORDER, top-first
  (`display_cards.assign(deck[player].crbegin(), deck[player].crend())`). So
  there is no parity to port here. What ships instead: `BoardObserver.ownDeck()`
  queries `LOCATION_DECK` with `QueryParser.DECK_FLAGS` (passcode + cover only —
  `BOARD_FLAGS` would drag in QUERY_EQUIP_CARD, a loc_info of controller,
  location and *sequence*) and sorts the result canonically **inside the call
  frame that asked for it**, so no true-ordered deck list exists anywhere else
  to be forwarded by mistake. That matters because the query is bottom-first
  (`ocgapi.cpp` walks `list_main` front to back) while the draw pops
  `list_main.back()` — the last element of a raw query *is* the next draw.
  `DuelistDuels.shuffledDeckList` then shuffles a **copy** with a fresh
  `SecureRandom` (never the duel seed, which is what the core shuffled the real
  deck with) and sends it to one recipient. The request carries no payload at
  all, so the seat is the sender's and there is nothing to validate; the wire
  form is two varint arrays rather than `BoardSnapshot.Slot`, because a Slot has
  fields that *can* encode a position and a field that is not on the wire cannot
  be filled in later by an edit that looks like tidying up.
- **Duel rewards are a server-owned assessment, not a client animation or a
  flat outcome constant.** `DuelRewardTracker` consumes the same absolute,
  ordered OCGCore stream the server already drains, and `DuelReward` is a pure
  evaluator whose visible lines sum to the committed amount. The balance is
  changed before `DuelRewardMessages.Result` is sent. PvP uses the full preset;
  repeatable NPC duels use 65%; a forfeit stops after outcome and elapsed-turn
  lines so surrender cannot manufacture no-spell/no-trap/no-special bonuses.
  The client holds that payload behind the outcome stinger, then opens a
  PNG-nine-slice summary/detail screen. A match carries metrics across games
  and pays once when the contest ends.
- **A container-item's card slots are one `CARD_INVENTORY` component, written
  through.** Forge kept them in a `CARD_ITEM_INVENTORY` capability attached to
  the stack — live storage, so a menu mutating the handler persisted for free,
  and `getShareTag`/`readShareTag` synced it. Neither survives: the slots are
  now `DdComponents.CARD_INVENTORY` (a `List<ItemStack>`, `OPTIONAL_CODEC` for
  disk, `OPTIONAL_LIST_STREAM_CODEC` for the wire — it syncs like any component,
  no share-tag override). A component is immutable, so `YDMItemHandler.boundTo(
  stack, size)` seeds a handler from it and overrides `setChanged()` to write
  the handler back into the stack — restoring the capability's "the handler is
  the storage" behaviour. The deck box, card set and simple binder all read
  their slots this way; the card **binder** does not — its cards are a
  server-side UUID-keyed collection, and only the binder's id sits on the stack.
- **Forge's `IItemHandler` becomes vanilla's `Container`, not a Fabric
  equivalent.** Fabric has no capabilities, and `fabric-transfer-api-v1` is a
  different abstraction aimed at pipes rather than at menus. Vanilla's
  `Container` is what a `Slot` already works with, with no adapter, which is
  what `SlotItemHandler` existed to provide. `YDMItemHandler` therefore extends
  `SimpleContainer` and keeps the handler's method names (`getSlots`,
  `getStackInSlot`, `insertItem`, ...) so the thirty-odd call sites across the
  binders, deck boxes and sets port without being renamed.
- **`CardRenderUtil` is across in halves.** Everything the card pages need is
  ported. The duel field's half is not: the rotated blits have no quad-transform
  in this API, and the rarity foils were composited by masking one texture
  against another with a colour mask that no longer exists. Both are decisions
  that should be made while looking at the field, and the field is not ported.
- **An unowned card is greyed by the DRAW, not by a second image.** It used to
  be a third identifier — `textures/item_unowned/` — whose bytes
  `DdCardResourcePack` desaturated pixel by pixel and re-encoded as a PNG,
  inline on the render thread, because that ran inside the `IoSupplier` MC calls
  from `Resource.open()`. 29 ms for a 512px preview against 3.6 ms for the same
  card owned, plus a 16 MB LRU to stop paying it twice, plus a second GPU
  texture per unowned card. All of it is deleted. **The tint could not do this
  job**: `graphics.blit` takes an ARGB *multiply*, and multiplication darkens or
  colourises — it cannot compute the luminance of the three channels that
  desaturation is. Every `BlendFactor` in 26.2 is per-channel, `withColorLogic`
  is gone, `GlStateManager` has no `glBlendColor`, and there is no texture
  swizzle, so no blend-state trick substitutes either. What ships is a mod
  fragment shader on a copy of the stock pipeline, at both draw sites:
  `UnownedPipelines.GUI` copies `GUI_TEXTURED` for the two blit sites and
  `UnownedPipelines.MESH` copies `BREEZE_WIND` for `CardPreviewScreen`'s quad
  mesh, each with only the fragment shader swapped. Four things worth knowing.
  **(1) No registration, and none is possible** — `RenderPipelines.register` is
  private, but `GlDevice` caches compiled pipelines keyed on the pipeline
  *instance*, so an unregistered one compiles lazily at first use;
  `FoilPipelines` had already proved this. **(2) The shaders need no wiring
  either** — `ShaderManager.prepare` calls `listResources("shaders", …)`
  namespace-agnostically, so `assets/dueldimension/shaders/core/*.fsh` in the
  mod jar is found. Use the `Identifier` overloads of `withFragmentShader` and
  `withLocation`; the `String` ones run `Identifier.withDefaultNamespace` and
  will look for a mod shader under `minecraft:`. **(3) A mod pipeline is not in
  `getStaticPipelines()`**, so `ShaderManager`'s reload-time validate sweep
  skips it and a GLSL typo becomes a draw-time crash rather than a startup
  failure. `UnownedPipelines.refresh()` calls
  `precompilePipeline(...).isValid()` from the `init()` of all three screens to
  turn that into a log line. **(4) The fallback is a DIM and must not be called
  a desaturation** — if the shader will not compile, unowned cards draw through
  the stock pipeline with `0.62, 0.62, 0.68` multiplied into their tint. Dimmer
  and cooler than their neighbours, no second image, and honestly labelled.
  Two things about this change do not show in a diff and have to be looked at in
  a running client: greyed icons in the deck editor and the binder used to get
  the blur+clamp mcmeta and now do not, so they **sharpen**; and splitting one
  pipeline into two changes sort grouping inside a `GuiRenderState` node, which
  the `encompasses`/`up()` mechanism should protect but only *should*.
- **Creative tab names were never renamed from the upstream mod.** `itemGroup.ydm`
  = "YDM: Ygo Dueling Mod" and "YDM Cards"/"YDM Sets" were still in `en_us.json`
  on **both** trees, and the Forge main tab had no key at all — it was keyed on
  `itemGroup.dueldimension` and the lang file only had `itemGroup.ydm`. Fixed on
  both, so the trees do not drift.
- **One repo, `fabric` branch**, not a second repository: the fork shares
  its history with `crumby`, so fixes to the engine core can cherry-pick
  across in either direction.
- **Assets stay in `forge-src` until their feature ports**, so nothing is
  duplicated; each phase `git mv`s what it needs (the starter deck `.ydk`
  lists came across with phase 0 because the deck tests read them).
- `OutfitSkinFormatTest` is parked with phase 5 — it validates outfit PNGs
  that live with the unported client feature.

---

## Card image loading (ported from EDOPro's `image_manager`)

The subsystem that decides when a card picture is read, decoded, uploaded and
released. It is a **port**, not a design: every rule below exists in
`edopro/gframe/image_manager.cpp` and is cited to it. Change it by reading that
file, not by reasoning from first principles — that was tried twice and both
attempts were wrong in ways only measurement caught.

### The problem it solves

`TextureManager.getTexture(id)` is a map lookup that, **on a miss, reads the
file, decodes it and uploads it inline on the calling thread** (verified in
bytecode: `byPath.get` → ifnull → `new SimpleTexture` → `registerAndLoad`).
There is no `isLoaded`, no `tryGet`. So the first frame that draws any card
identifier pays the whole cost, and the cost scales with how many *never before
drawn* cards enter one frame — which is the definition of scroll velocity.

Measured on the development machine: 0.32 ms per 128px icon, 3.64 ms per 512px
preview. A 65-card flick is ~21 ms in a single frame; a 24-card pack summary
~87 ms.

**Rationing this does not work.** It was tried: a fixed count per tick, then a
time budget. Both trade the stall for a slow fill, and a 1,000-card collection
took seconds of placeholder art on every launch. The two symptoms are the same
cost seen from two sides, and only moving the work off the render thread
removes both.

### EDOPro's method, and ours beside it

| # | EDOPro | Ours |
| --- | --- | --- |
| 1 | `LoadCardTexture` does read + decode + resize and returns a CPU image; `addTexture` is the only GPU call | `TextureContents.load` on a worker; `ReloadableTexture.apply` on the render thread |
| 2 | `imageLoadThreads` workers on `LoadPic`, default 4, no metering of decode | worker threads in `CardImageManager`, decode unmetered |
| 3 | `to_load.emplace_front` + `front()/pop_front()` — **LIFO both directions** | `toLoad.addFirst`, drained from the front |
| 4 | `maxImagesPerFrame` uploads per queue per frame, default 50, lock released **before** the upload | uploads counted per frame per size class in `refreshCachedTextures` |
| 5 | `preloadStatus NONE → LOADING → LOADED`, enqueued once | `tMap[index]` holds the same three states |
| 6 | returns `tUnknown` immediately, never blocks; callers re-ask every frame | returns `DuelTextures.UNKNOWN`; screens re-ask every frame |
| 7 | `timestamp_id` epoch, checked **per output row** inside the resampler | `timestampId`, checked between stages |
| 8 | `obj_clear_thread` frees abandoned images off the render thread | same |
| 9 | velocity gate: `drawing.cpp:1378`, 10 rows per frame, frame-rate independent | `CardImageManager.drawThumb(prevRow, row, deltaMillis)` |

### The rules that are load-bearing

**LIFO, not FIFO.** The newest request decodes first. A fair queue spends its
effort on rows that scrolled past long ago while the cards under the player's
cursor wait — which *is* the "everything slowly fills in" complaint. There is no
per-request cancellation; sinking to the bottom of the stack is the whole
supersede mechanism.

**The velocity gate does not request at all.** Above ~10 rows per frame the
screen substitutes the placeholder *without calling the accessor*, so no map
entry and no queue entry are made. A gate that still enqueued would only move
the stall.

**Every producer of an identifier must go through the accessor.** The cache
adopts what the manager registers and `sweep()` releases it; a second site
drawing the same identifier raw will therefore miss in `TextureManager` and pay
the inline cost. This was a real defect — `CardSetSpecialRenderer` drew pack art
directly and reintroduced the stall for items in a hand or on the ground.

**The resident map is access-ordered, so it must be touched on every HIT.**
`CardTextureCache.touch` at upload time only leaves it in *insertion* order, and
the sweep then evicts by upload order rather than by recency. This was also a
real defect.

**Budgets are bytes per size class, never a count.** A 512px preview is sixteen
icons. One number cannot mean the same thing for both, and previews must not be
able to evict the grid.

### Verifying a change

- `./gradlew25.cmd build` — the queue ordering, eviction rule and budget
  arithmetic are unit-tested; a change that breaks LIFO or the byte accounting
  fails there.
- In game: open the collection cold (should populate quickly, not trickle),
  flick fast through unseen cards (should not stall), then look at a pack item
  in the inventory and on the ground (the path that bypassed the manager).
- `HitchWatch` prints tick times, but **its counters watch the producer side
  only** — `images in flight` and `textures loaded` read 0 during a hub scroll
  by construction, because no hub screen feeds them. A zero there is not
  evidence that nothing was loaded.

---

## Custom cards

A card needs three separate things, and they travel by three separate channels.
All three already exist; adding a card is filling them in, not building them.

### Where they go

    <game dir>/dueldimension_custom/
        *.cdb          any number of card databases
        script/        c<passcode>.lua, one per card with behaviour

Outside both the EDOPro install (not ours to write to, and it updates itself)
and `ydm_db` (deleted recursively whenever the card database refreshes, which
would take every custom card with it).

### The three channels

| what | where | how it is picked up |
| --- | --- | --- |
| rules data — type bits, ATK/DEF, level, race | a `.cdb` in `dueldimension_custom/` | `Paths.cdbChain()` appends it **last**, and `CdbCardProvider` merges in order with later rows winning — EDOPro's expansions rule |
| behaviour | `dueldimension_custom/script/c<passcode>.lua` | `Paths.scriptRoots()` searches custom **first**, so a custom script overrides a stock one |
| display — name, text, art | a card JSON in `ydm_db` plus its image | the mod's own database, unchanged |

Custom wins in both chains: last in the database (later rows replace), first in
the scripts (first hit returns). A custom entry can therefore also *correct* a
stock card without editing anybody else's files.

### Passcodes

Reserved block: **900,000,000 – 999,999,999** (`Paths.CUSTOM_PASSCODE_FIRST`).
Konami's printed cards are eight digits and stop well below it. A colliding id
does not fail loudly — it silently replaces a real card in the chain.

### What only the server needs

ocgcore runs **server-side only**, so the `.cdb` row and the Lua script are
needed on the server alone. Clients need the JSON and the art to draw the card.
A server can therefore run custom cards without every player installing
anything.

### The safety net

`ProfilePayloads.syncEngineUnknown` tells each client which passcodes the engine
does not know, and the deck editor hides them. A custom card whose `.cdb` row is
missing simply does not appear, instead of being buildable and then silently
relocated into the main deck at duel start (see the card image notes above for
why: `field::add_card` rewrites the location rather than refusing it).

### Effort

Data and art: minutes. A vanilla beater's script: a dozen lines of Lua. Anything
with a trigger, a chain or a summon condition: real work against EDOPro's script
API, per card. CLAUDE.md's "never invent behaviour" rule does not apply here —
it forbids guessing at how *existing* cards work; a custom card's behaviour is
yours to define.

### Writing a custom card's JSON — the trap

`ydm_db` card JSON is read with `j.get(key).getAsX()`, **not** with a
null-tolerant lookup, so a missing key throws and `DdDatabase` logs
`Failed reading card` and skips it. The card then does not exist, with no error
a player would ever see — it simply is not in the editor.

Which keys are required depends on the type, because `DdUtil.buildProperties`
picks a subclass and each reads its own:

| card | must also carry |
| --- | --- |
| any monster | `attribute`, `atk`, `species`, `monster_type`, `is_pendulum`, `ability`, `has_effect` |
| has a level (normal, effect, fusion, ritual, synchro) | `def`, `level`, **`is_tuner`** |
| Xyz | `def`, `rank` |
| Link | link value and arrows |
| pendulum | `pendulum_text`, `pendulum_scale_left_blue`, `pendulum_scale_right_red` |

`is_tuner` is the one that bites: it is absent from every Xyz and Link in the
shipped database, present on every levelled monster, and omitting it from a
custom levelled monster silently drops the card.

**Copy an existing card of the same shape and edit it** rather than writing the
JSON from scratch — the field set is then correct by construction.

**A custom card has no download source.** Its `images` list is empty, because
its art is authored and placed on disk rather than fetched. `getImageURL`
returns null for such a card and the download task is skipped; do not "fix" an
empty list by inventing a URL. Art goes straight into the image cache, named by
passcode and art index:

    ydm_db_images/cards/raw/<passcode>_0.jpg     original aspect
    ydm_db_images/cards/512/<passcode>_0.png     letterboxed square, RGBA
    ydm_db_images/cards/128/<passcode>_0.png     same, grid size

Both processed sizes must be written, or the card falls back to the placeholder
at whichever size is missing.

### Shipping custom cards with the mod

A card authored in `dueldimension_custom/` works on that machine only. To ship
it, its pieces go in the jar and are unpacked on first run:

    src/main/resources/dueldimension_custom/
        index.json                     names every file below; a jar cannot be listed
        custom.cdb                     rules
        script/c<passcode>.lua         behaviour
        images/{raw,512,128}/<passcode>_0.*   art
    src/main/resources/ydm_extras/cards/<name>.json    display, via the existing extras index

`CustomCardBundle.install()` runs from BOTH entry points — the client (after the
image folders exist, since that is where art lands) and the mod initialiser,
before `DdDatabase.initDatabase()`. A dedicated server needs it too: the rules
half is the server's, and a server whose engine lacks a card its players have is
precisely how a deck gets silently rewritten at duel start.

**It never overwrites an existing file.** The unpacked folder is also where a
player authors their own cards, so replacing its contents every boot would
delete their work. A shipped card whose file was edited stays edited; deleting
it is how the bundled copy returns. This differs deliberately from
`installBundledExtras`, which *does* correct a stale copy — that folder is
generated, this one is authored.

Both must be unpacked before the first duel: the engine opens its `.cdb` chain
lazily but only once, so a file written afterwards is not seen until a restart.
