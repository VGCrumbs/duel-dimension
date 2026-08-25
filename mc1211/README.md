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

## What Phase 1 faces

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
