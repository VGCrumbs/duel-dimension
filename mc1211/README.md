# The 1.21.1 platform

**The mod is ported, it builds, it runs, and its test suite matches 26.2's.**
See [Where the port actually is](#where-the-port-actually-is) for what that does
and does not mean — the short version is that the code is across and most of the
UI has still never been looked at on this version.

`gradlew25 buildAll` at the repository root builds this alongside 26.2.

## What `ToolchainProof` was for

Kept because the three questions it answered are the three that made this
platform possible, and the reasoning is worth more than the file.

It is one file, it is not an entrypoint, and it is registered nowhere. It exists
because three things could not be known until a 1.21.1 build actually compiled:

1. **Does Loom resolve 1.21.1 alongside a 26.2 build?** They disagree about
   almost everything Loom configures.
2. **Is `common` usable from here?** It is compiled to Java 21 against no
   Minecraft at all. The proof reads `OcgConstants.POS_FACEUP_ATTACK` from it.
3. **Do Mojang names work?** The proof names `ResourceLocation`, which under
   Yarn would be `Identifier` and would not compile. This matters more than it
   sounds: the whole existing codebase is written in Mojang's vocabulary because
   26.2 ships unobfuscated, so official mappings mean a ported file differs only
   where the API genuinely differs — not in every type name.

All three answer yes, and 407 ported files now ask them on every build, which is
what the file was holding the place for.

## Why the repository is a composite

Loom **1.17.18**, which 26.2 requires, cannot configure an obfuscated Minecraft.
Asked to, it fails at configuration time:

```
> Cannot use Mojang mappings in a non-obfuscated environment
> Could not find method mappings() for arguments [net.fabricmc:yarn:1.21.1+build.3:v2]
```

Two explanations were ruled out before concluding:

- **Not cross-project state.** It fails identically with `mc262` removed from the
  build entirely.
- **Not a missing class.** `Constants$Configurations` in the 1.17.18 jar still
  declares `MAPPINGS = "mappings"`, and the layered mapping spec classes ship
  with it. The pipeline is there; this Loom will not enable it, because modern
  Minecraft has no use for one and 1.21.1 predates that.

A Gradle build resolves **one** version of a plugin. So each platform is its own
build:

```
settings.gradle   includeBuild 'common'   includeBuild 'mc1211'   include 'mc262'

common/           its own build — no Loom at all,  Java 21
mc1211/           its own build — Loom 1.12.7,     Java 21,  Minecraft 1.21.1
mc262/            a root subproject — Loom 1.17.18, Java 25,  Minecraft 26.2
```

`common` had to become a build of its own too, for a consequence of the same
rule: a build included in a composite can see other *included builds*, and
cannot see another build's *subprojects*. Left as a subproject of the root it
would have been invisible from here, and this platform would have needed its own
copy of the shared core.

Cross-build dependencies are declared **by coordinate**
(`de.cas_ual_ty.dueldimension:common:0.1.0-fabric`); Gradle substitutes the local
source for it. Nothing is published and nothing is fetched from a repository.

## The first launch, and what it found

`mc1211` had never been run — only compiled — and the first launch died before
the title screen with **no crash report**, only a JVM `hs_err` file:

```
EXCEPTION_ACCESS_VIOLATION (0xc0000005) at lwjgl_opengl.dll+0xc75d
  org.lwjgl.opengl.GL11C.nglGenTextures
  ...
  net.minecraft.class_1043.<init>            (DynamicTexture)
  MonsterSheets.read
  DuelDimensionFabricClient.onInitializeClient
  net.minecraft.class_310.<init>             (Minecraft's CONSTRUCTOR)
```

**`onInitializeClient` runs inside Minecraft's constructor, before there is an
OpenGL context**, and on 1.21.1 `new DynamicTexture(image)` calls
`glGenTextures()` from its own constructor. With no current context that is not
an exception any `catch` could see — it is a native access violation that takes
the JVM with it. The `try/catch` around that call was doing nothing.

26.2 makes the same call at the same point and is completely fine, because its
`DynamicTexture` takes a label supplier and defers the upload. **Same line, same
place, different Minecraft.** Both loads now run from
`ClientLifecycleEvents.CLIENT_STARTED`, together and in their original order.

Worth generalising, because it is the thesis of this file: `compileJava` proves
a call EXISTS, never that it may be made YET. Anything that touches the GPU at
init is a candidate — the audit found only this one (`CardImageManager`'s
`DynamicTexture` is inside a per-frame pump on the render thread, which is
fine), but the next constructor to quietly acquire a GL resource will fail the
same way, and it will not leave a stack trace in Java.

## `enableScissor` measures in a different space on each version

The same four ints mean different things, and neither compiler nor test can see
it:

| | what the rectangle is in |
| --- | --- |
| 26.2 | the **current pose** — `new ScreenRectangle(...).transformMaxBounds(this.pose)` |
| 1.21.1 | raw GUI pixels, pushed onto the scissor stack untouched |

At an identity pose those are the same thing, which is why nine of the mod's ten
clip calls never noticed. The tenth is the duel sidebar's effect text, drawn
inside `scale(0.75)`: text at pose y=340 lands on screen at 255 while the clip
asking for y=340 stays at 340, so the band sat entirely *below* the text and
scissored every line of it away. **The card description came out blank while its
well, border and scroll bar all drew correctly** — `fill()` and `blit()` do go
through the pose — so it read as a widget with nothing to say rather than a
broken one.

`GuiGraphicsExtractor.enableScissor` now transforms the rectangle itself, which
restores 26.2's meaning for every caller and changes nothing for the ones already
at identity.

The general form is the same as the `DynamicTexture` lesson above: a signature
that survives a version bump unchanged is the *easiest* kind of difference to
miss, because nothing anywhere reports it.

## Two things that each cost a round

- **The plugin id here is `fabric-loom`, not `net.fabricmc.fabric-loom`.**
  Checked against the Fabric maven's own metadata rather than guessed: the long
  id's plugin marker is published for 1.17.x only, while the short id's goes back
  through 1.11. The root build uses the long form because 1.17.18 has it.

- **The shared classes have to be merged into the jar.** A dependency puts them
  on the classpath, which is enough for tests and a dev run and is not enough for
  a shipped jar. The 26.2 platform's first jar after the split contained no rules
  engine at all and would have loaded fine right up until someone started a duel.
  Both platforms merge `common` into `jar` now, non-transitively — JNA and sqlite
  are bundled deliberately by Loom's `include`, and gson, Guava and the logging
  facade come from Minecraft at runtime.

## Where the port actually is

**All 407 Java files compile, the resources are across, and the test suite is
437 tests — the same 437 mc262 runs, none failing and none skipped.** (mc262
skips one, for want of a card database.) `port-excludes.txt` is empty, and
`dueldimension-fabric-1.21.1.jar` builds with its mixins remapped in place — no
refmap, because Loom's remapper rewrites the annotation strings into
intermediary itself. `./gradlew25.cmd -p mc1211 exportJar` puts it in the
Modrinth profile.

**It has been run.** The client launches, a world loads, the bundled engine
unpacks, and a duel starts. Four things the first run found are fixed: the
interface drew blurred, the jar carried no engine, "are the card scripts
supplied?" was asking the wrong question, and card backs flipped 180° when a
placement animation ended.

**What is still unproven is most of the UI.** One hub screen and one duel start.
The deck editor, the shops, pack opening, the 3D board and every animation have
not been looked at on this version. A green suite and a clean launch are not the
same as a screen that lays out.

### Parity, measured rather than asserted

The two trees are compared by DECLARED MEMBERS -- methods, static constants and
record components -- rather than by diffing text, because a line diff of two
ports is mostly the ports. What that comparison finds is a real gap; what it
does not find, a rename cannot hide.

It is down to five files, and every one of them is an API 1.21.1 does not have:

| file | what 26.2 declares | why it is not a gap |
| --- | --- | --- |
| `BoardPip` | `State` and its components | 26.2's picture-in-picture render state; 1.21.1 draws in place |
| `UnownedPipelines` | `GUI`, `MESH`, `MODEL`, `MODEL_BLEND` | named pipelines; 1.21.1 has one GUI path |
| `DdCardModels` | `CARD`, `CARD_SET`, `DUEL_DISK_CARDS` | the 1.21.4 item-model registry, gone here on purpose |
| `FreeMode` | `CODEC`, `TYPE` | `SavedData` became Codec-driven in 26.2 |
| `PlayerSkins` | `asset`, `patch` | 26.2's skin API |

And seven files exist only in 26.2, for the same kind of reason: `PipelineCopy`
and `GuiGraphicsExtractorAccessor` are render plumbing, `JadeIntegration` and
`ModMenuIntegration` are optional mod compat, and `CharacterCarrier` with
`AvatarRenderStateMixin` and `AvatarRendererMixin` are the render-state split
this version does not have -- see the character section below.

**A record component is not a declaration**, and the first version of that
comparison could not see one. `BoardMesh.Piece` had quietly lost its
`controller` field here, which is why the overworld playmat ignored the colour
chosen in the hub and drew as the grey it is stored as: the mat texture is
greyscale and exists to be multiplied by that colour. Worth naming because the
tool was the thing at fault, not the reading of it.

### What came across when that was measured

- **The overhead camera.** `DuelCamera`, its mixin, the two that hide the player
  and the first-person hand while it is on, the editor screen behind backslash,
  and `V` itself. `DuelCamera` is version-agnostic and copied verbatim; the
  mixins needed new targets -- `Camera.setup` for 26.2's `update`, and
  `renderHandsWithItems` for `submitHandsWithItems`.
  <p>
  The keybind is POLLED rather than consumed, and that is not an accident of the
  port: Minecraft only feeds key presses to a `KeyMapping` while no screen is
  open, and a duel is played with the board pointer open almost all the time.
- **The chain toggle.** `ChainSettings` (version-agnostic, verbatim), the button
  on the duel HUD, and the three places the pointer screen reads it -- whether a
  chain window is declined, what its caption says, and the click that flips it.
- **The tribute animation**, which the section this replaced said was not across
  and would be "a rendering job on the version whose rendering has not been
  looked at yet". It has been looked at now. The `releases` list, the dispatch,
  `DdSounds.TRIBUTE`, `releasesInFlight`, and `drawReleases` -- the card lifting
  and narrowing into light rather than breaking -- are all here, none of it
  touching an API the two versions spell differently.
- **The life-point bar's height floor.** 26.2 gained a `BAR_THICK` minimum
  because honouring the frame art's 10:1 ratio exactly made the bar 19 units
  tall where it had been 24, "and a life total is the number on this screen that
  is read from furthest away". Copying 26.2's whole `DuelHud` would also have
  dragged the chain toggle in before its own dependencies existed; it was taken
  as the one fix and the toggle followed separately.

### The player characters are across, and the port is SIMPLER than 26.2

The created character — the DS game's customisable duellist, extracted by
`NexusDecomp` and shipped as two GLBs and two palette files — draws on this
version too. Most of it did not need porting at all: `CharacterLook`,
`CharacterRamps`, `CharacterPalettes` and `CharacterText` are in `common`, and
`CharacterMessages`, `WornCharacters`, `ClientCharacters` and `CharacterEdits`
are byte-identical copies, because Fabric's `CustomPacketPayload` networking is
the same API on both.

Five things differ, and only one of them is a loss:

- **The render hook is one mixin, not three.** 26.2 extracts a render state per
  frame and the renderer never sees the entity again, so the look has to be
  written onto the state (`AvatarRenderStateMixin`) by the renderer that still
  has the player (`AvatarRendererMixin`) and read back by a third
  (`CharacterRendererMixin`) — and that third has to target
  `LivingEntityRenderer`, because `AvatarRenderer` inherits `submit` rather than
  declaring it and a mixin into an inherited method fails at LOAD. Here
  `PlayerRenderer` declares `render` AND is handed the player, so
  `CharacterRendererMixin` looks the character up itself. No carrier interface,
  no state mixin, no narrowing `instanceof`.
- **`NativeImage` is ABGR.** The palette rewrite produces ARGB, so
  `CharacterModels` swaps red and blue on the way in — the same `abgr` helper
  `MenuThemes` needs, for the same reason. 26.2's `setPixel` takes ARGB straight.
- **`ModelHologram` grew the same skin overload**, an `IntFunction` from part
  index to texture, so selecting four primitives out of sixty and repainting them
  happens inside the one skinning loop rather than in a second copy of the vertex
  path.
- **Everything the walk cycle needs is a carrier field on 26.2 and a question
  here.** Sprinting, going backwards, swinging, whether they moved at all,
  whether they are RIDING, and whether the ground under them is SLIPPERY:
  `CharacterRenderer` asks the player directly on this version -- `isPassenger`,
  and `getBlockPosBelowThatAffectsMyMovement().getBlock().getFriction()` -- and
  26.2 asks the same questions during extraction and carries the answers. The
  thresholds are the same on both: `1.0E-6` for moved, and for coasting a
  threshold DERIVED from the surface -- `(friction * 0.91 + 1) / 2`, the midpoint
  between what an undriven velocity decays by and holding speed. 0.773 on
  ordinary ground, 0.946 on ice. It has to be the
  position and not `walkAnimationSpeed` — that one is eased, `update(f, 0.4F)`,
  so it decays for most of a second after the keys are let go and the walk cycle
  ran on well past the duellist stopping.
- **The item-anchor editor's chosen category is static, and on 26.2 the gizmo
  therefore shows on everybody.** `ItemAnchorScreen.editing()` is what the
  renderer asks for the grip to draw. Here the branch is also gated on
  `player == minecraft.player`; on 26.2 the render state carries no identity to
  gate on, so every character in view grows handles while the screen is open. A
  dev-only screen and a duellist alone with it, so it has not been worth a field.

**Not looked at on this version:** how the editor lays out, and whether the
model reads correctly against 1.21.1's lighting. It compiles and its mixin
targets are checked; that is not the same as a screen that lays out, which is
what the paragraph above about the UI already says.

### Two mods live in `run/mods`

`leawind_third_person` and `perspective_api`, both from Modrinth, both Fabric
builds for 1.21. Dropped into the run directory rather than declared as
`modRuntimeOnly` because their version strings contain a `+`, which Gradle reads
as a dynamic-version wildcard -- and because they are a thing to LOOK at the
game with rather than a thing the mod depends on. Fabric Loader remaps mods
found there into the dev namespace, so a production jar works unchanged.

They are here because the player characters need watching from outside the
player, and 1.21.1 is the tree that gets launched now.

### `GuiGraphics` draws in TYPE order, not call order

The trap behind two bugs that looked unrelated: the god statues not appearing,
and the Monuments backdrop looking wrong.

`GuiGraphics` does not draw when it is told to. It fills one buffer per render
type and the lot comes out at the next flush, in whatever order the buffer
source iterates those types. So a screen that blits a backdrop, then a shimmer,
then a vignette, then paints 3D models through `BoardPip` has all four in one
batch — and which lands on top is decided by type, not by the order they were
asked for. The backdrop came out last, over the statues.

26.2 never meets this: its pip renders to a texture and composites the result,
so the ordering is explicit in the composite.

`BoardPip.draw` now flushes on the way IN as well as on the way out. Two
flushes make the pip an ordered island in a batched world, which is the same
bargain `SubmitNodeCollector.submitCustomGeometry` strikes for the same reason —
its comment is worth reading beside this one.

**The general form:** on this version, anything that must be layered against
something else drawn through `GuiGraphics` has to say so with a flush. Nothing
reports a violation; it renders, in an order that looks arbitrary and is not.

### A plain blit does not turn blending on, and text turns it off

`GuiGraphics` has two `innerBlit` overloads and they disagree. The one taking a
colour brackets its draw with `RenderSystem.enableBlend()` and `disableBlend()`;
the one without takes the blend state as it finds it and leaves it alone. Read
off the 1.21.1 bytecode, not inferred.

Every blit in this mod reaches the **second** one. `DdBlitUtil`'s tinted path
sets a shader colour through `GuiGraphics.setColor` and then calls the
*colourless* blit, so not one draw in the interface manages blending for itself.

That is survivable only while something else has left blending on, and the thing
that most reliably turns it off is **drawing text**. A string batches into
`RenderType.text`, which is translucent, and a translucent render type's teardown
ends in `disableBlend()`. `drawString` flushes immediately, so the teardown runs
immediately — and the next PNG is drawn with its alpha ignored, which for these
assets means transparent texels come out black.

Hence a label plate that is correct in one place and a black box in another, the
only difference being whether a caption was drawn just before it. Every element
in this interface is an alpha PNG, so this is the common path rather than a
corner case.

`GuiGraphicsExtractor` now calls `enableBlend()` before every blit it forwards,
and leaves blending on afterwards: there is no prior state to restore to, and
blending on is what the GUI wants for everything except the opaque background
fills, which set their own state.

**It enables blending and does not choose the function**, and that distinction
cost a bug on the first attempt. The version that also called
`defaultBlendFunc()` broke the Monuments backdrop: `FoilPipelines.ADDITIVE`
sets `blendFuncSeparate(ONE, ONE, ONE, ONE)` and then blits the tile layer, so
resetting the function between those two put an *opaque* layer straight over the
lattice and the lattice vanished. The bug being fixed here is blending being
off; the function a translucent render type leaves behind is already the one an
alpha PNG wants, and vanilla's own coloured blit enables blending without
setting a function either.

26.2 never meets this — its pipelines carry their own blend state and a draw
cannot inherit one.

### The shims, and what each one costs

Written where 1.21.1 has no counterpart for something 26.2 assumes. All in
`de.cas_ual_ty.dueldimension.compat`:

| shim | stands in for | what it cannot do |
| --- | --- | --- |
| `GuiGraphicsExtractor` | 26.2's retained-mode GUI extractor | `nextStratum`, `blurBeforeThisStratum` and `dispose` are no-ops |
| `SubmitNodeCollector` | the submission API | flushes per submission to keep call order; `submitText` drops the outline colour |
| `RenderPipelines` | named GUI pipelines | valueless `Object`s; 1.21.1 has one GUI path |
| `InputEvents` | 26.2's input records | the double-click flag is lost, and costs nothing — no mod code on either version branches on it, and 1.21.1's `AbstractContainerScreen` does its own detection |
| `SpecialModelRenderer` | the 1.21.4 item-render split | nothing; it is the same interface, mod-owned |

### Where a decision was made rather than a rename

- **`BoardPip`** is nearly empty here. 26.2 needs picture-in-picture because a
  screen cannot draw a quad; 1.21.1 hands out a `PoseStack` and a
  `BufferSource`, so it does the three things the pip did implicitly — origin at
  the region's top-left, a scissor, and the `(s, s, -s)` z flip — and gets out of
  the way.
- **The item-model layer is gone.** 1.21.4's `items/foo.json` has no 1.21.1
  counterpart. Sixty of the seventy-three said only "use my own model", which is
  the default; the card, the two sets and ten disks became `builtin/entity`
  markers bound per ITEM in `DdCardModels`. A missing binding there is silent —
  the item just draws plain.
- **A disk draws its own frame.** 26.2 composites [frame model, cards]; here
  `DiskCardsItemModel` does both, and `tools/port_resources_1211.py` moves each
  disk's geometry to `<id>_frame.json` so there is one rule and no special case.
- **Unowned cards are tinted, not shaded.** The two fragment shaders are not
  ported — they are written against 26.2's uniform blocks — so
  `UnownedPipelines.available()` is permanently false and every caller takes the
  `dimmed(tint)` branch the mod already had, including `FieldQuad.draw` on the 3D
  board. A tint cannot desaturate, so the look is dimmer and cooler rather than
  grey; that is the same degraded state 26.2 falls back to when the shader will
  not compile, not a new one.
- **`CardDisplayRenderer`** keeps 26.2's extract/submit split as a plain object,
  because `drawMonster` is forty lines of placement rules shared with the board
  and identical parameters are what keep the two versions one piece of text.
- **The HUD.** 1.21.1's `HudRenderCallback` can only add, so the two additions
  are in the client initialiser and the two REPLACEMENTS (hiding the hotbar and
  the held-item name) are injections in `DuelHudMixin`.

### Still missing

- **The two card-desaturate shaders** — the one remaining gap you can see. An
  unowned card is dimmed here and greyed on 26.2. `UnownedPipelines` carries the
  arithmetic and the constants, and `available()` is permanently false. Closing
  it means 1.21.1 core shaders (a `.json`, a `.vsh` and a `.fsh` each), a
  `CoreShaderRegistrationCallback` registration, and an access widener for
  `RenderType.create` — which would be the one entry of mc262's four that turns
  out to be needed here after all.
- `JadeIntegration` and `ModMenuIntegration` — see
  `src/main/java/.../fabric/README-integrations.md`.
- One duel-disk element is drawn at 45° where it was authored at 42.5°, because
  1.21.1 accepts only multiples of 22.5. Re-authoring it onto a legal angle is
  the fix; snapping is what the resource port does meanwhile.

## What Phase 1 faced

Measured, not estimated — see the root `PORTING.md` and `tools/phase0_split.py`:

- 64 of 483 main files are already shared and need no porting.
- Of the rest, 44 are broken only by `Identifier` → `ResourceLocation`.
- 137 need real API work, and that is a **floor**: the diff behind it is
  class-level, so a method that still exists with a changed signature — the NBT
  accessors, `Item.Properties.setId`, `hurtServer` — does not appear in it.
- 92 client files exist only in the 26.2 tree and have no immediate-mode original
  to port from. That is the actual cost.

The client layer should be ported from the **`crumby` branch** (Forge 1.19.2),
not from `mc262`. That tree draws with `PoseStack`, `RenderSystem` and
`BufferBuilder` — 1.21.1's idiom — whereas 26.2 is a retained-mode architecture
inversion away.
