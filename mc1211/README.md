# The 1.21.1 platform

**The toolchain works. Nothing is ported yet.**

`gradlew25 buildAll` at the repository root builds this alongside 26.2 and
produces a real, loadable Fabric 1.21.1 mod jar. It contains `ToolchainProof`
and the shared rules engine, and no mod — porting is Phase 1.

## What `ToolchainProof` is for

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

All three now answer yes. Delete the file once real code lands; at that point
the compiler asks these questions on every file anyway.

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

**All 407 Java files compile and 1,299 of 1,355 resources are across.**
`port-excludes.txt` is empty and `dueldimension-fabric-1.21.1.jar` builds, with
its mixins remapped in place — no refmap, because Loom's remapper rewrites the
annotation strings into intermediary itself.

**That is not the same as "it runs."** Nothing in this tree has been launched.
What is proven is that it compiles, that every mixin names a method 1.21.1
actually has (`MixinTargetsTest`, which is the only thing between a stale target
string and a crash before the title screen), and that the jar assembles. What is
NOT proven is that any injection point is reachable, that any screen lays out,
or that a duel plays. The next step is a run, and the first run of a port this
size is a list, not a verdict.

### The shims, and what each one costs

Written where 1.21.1 has no counterpart for something 26.2 assumes. All in
`de.cas_ual_ty.dueldimension.compat`:

| shim | stands in for | what it cannot do |
| --- | --- | --- |
| `GuiGraphicsExtractor` | 26.2's retained-mode GUI extractor | `nextStratum`, `blurBeforeThisStratum` and `dispose` are no-ops |
| `SubmitNodeCollector` | the submission API | flushes per submission to keep call order; `submitText` drops the outline colour |
| `RenderPipelines` | named GUI pipelines | valueless `Object`s; 1.21.1 has one GUI path |
| `InputEvents` | 26.2's input records | the double-click flag is lost — the bridge passes `false` |
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
  `dimmed(tint)` branch the mod already had. Known gap: `FieldQuad.draw`'s
  `desaturate` reaches `mesh()` without asking `available()`, so an unowned card
  on the 3D board still looks owned.
- **`CardDisplayRenderer`** keeps 26.2's extract/submit split as a plain object,
  because `drawMonster` is forty lines of placement rules shared with the board
  and identical parameters are what keep the two versions one piece of text.
- **The HUD.** 1.21.1's `HudRenderCallback` can only add, so the two additions
  are in the client initialiser and the two REPLACEMENTS (hiding the hotbar and
  the held-item name) are injections in `DuelHudMixin`.

### Still missing

- `JadeIntegration` and `ModMenuIntegration` — see
  `src/main/java/.../fabric/README-integrations.md`.
- The two card-desaturate shaders.
- 73 of the 74 `mc262` tests. Only `MixinTargetsTest` is here; the engine's own
  suite lives in `common` and runs on Java 21, which is 1.21.1's.

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
