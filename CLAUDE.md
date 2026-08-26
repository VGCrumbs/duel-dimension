# Duel Dimension

A Minecraft mod that plays real Yu-Gi-Oh!, by embedding the actual ygopro-core
(`ocgcore`) engine through JNA rather than reimplementing the rules. Forked from
CAS-ual-TY/YgoDuelingMod; mod id `dueldimension`.

**This tree — `CrumbyDuelingMulti`, branch `multi-version` — is canonical.** It
supersedes `CrumbyDuelingFabric`, which is the same history up to `0ffdbaf5` and
is kept only for reference. Work here.

One repository (`VGCrumbs/duel-dimension`), and this tree is a Gradle
**composite** of three builds, because two Minecraft versions cannot share one:

| | what it is | MC | Java | Loom |
| --- | --- | --- | --- | --- |
| `common/` | knows nothing about Minecraft | — | 21 | none |
| `mc262/` | the working mod | 26.2 | 25 | 1.17.18 |
| `mc1211/` | the port; compiles, never launched | 1.21.1 | 21 | 1.12.7 |

`CrumbyDueling` (branch `crumby`, 1.19.2 / Forge 43.2.8, JDK 17,
`gradlew17.cmd`) is a separate checkout and still the reference for anything
drawn in immediate mode — its rendering idiom is 1.21.1's, where 26.2's is not.

### Why a composite and not subprojects

Loom 1.17.18, which 26.2 requires, cannot configure an obfuscated Minecraft, and
1.21.1 is obfuscated. A Gradle build resolves one version of a plugin, so each
platform is its own build. `common` had to become one too: a build included in a
composite can see other included builds, not another build's subprojects.

Cross-build dependencies are named by coordinate
(`de.cas_ual_ty.dueldimension:common:0.1.0-fabric`) and substituted from local
source. Nothing is published or fetched.

### The two maps

**`PORTING.md`** — the 1.19.2 → 26.2 port: the toolchain, every measured API
difference, what is across and what is not. **`mc1211/README.md`** — the 26.2 →
1.21.1 port: why the composite exists, what has been shimmed, and what is left.
Read the relevant one before porting anything; add to it when you learn
something the next person would otherwise rediscover.

## How to work on this

**Zero guesswork, strict parity.** EDOPro is the reference implementation, and
this mod ports its code and assets directly rather than approximating them. Where
the engine or the database can be asked a question, ask it — parse what the
engine generates instead of hardcoding a table that will drift. Keep the original
variable names; a port should not rename what it is not changing.

**AI rules are strictly defined in EDOPro and are followed precisely.** Never
invent behaviour for a bot, a phase, or a card interaction. If the answer is not
in EDOPro's source, it is not an answer yet.

**Hidden information must never reach the client.** A player's hand, deck order
and facedown cards are the server's. This is a correctness rule, not a policy
one: a client that receives what it must not show is a cheat waiting to happen.

**All UI elements are PNG graphics** — only text uses the font. No shapes drawn
in code standing in for art.

The client runs at **920p (1634×920)**, which is what the hub and duel field
layouts are measured against.

## Building

    ./gradlew25.cmd buildAll     # every module; this is the one to run
    ./gradlew25.cmd :mc262:build # the working mod alone
    ./gradlew25.cmd -p mc1211 compileJava   # the port alone
    ./gradlew25.cmd -p common test          # the shared core, on Java 21

`buildAll` green means **no finished part has regressed**.

`mc1211/port-excludes.txt` is now **empty**: every file compiles and the build
skips nothing. It stays in the build because it is how a file is taken back out
if one has to be, and because a build that silently compiled everything would
lose the one place that says how much was left.

Compiling is not running. `mc1211` has never been launched, and the mixin target
strings — invisible to `compileJava`, fatal at load — are checked by
`:mc1211:test` (`MixinTargetsTest`) rather than by the compiler. Run it after
touching anything under `mixin/`.

The 26.2 `runClient` still exists under `:mc262:runClient` (`--username Alpha`,
1634×920).

Two things do not work outside a Windows machine with a GPU:

- **`native/ocgcore.dll` is a Windows native**, loaded through JNA. The duel
  engine tests need it. It lives at the REPOSITORY root, shared by every module;
  `common`'s test task points at it with the `ocg.lib` property, since its own
  working directory is `common/`. (It is 32-bit — a 64-bit JVM cannot load it,
  which cost an afternoon once.)
- **`runClient` launches a real game.** Anything verified by looking at it has to
  be verified on that machine.

Everything else — the source port, the pure-Java tests, `compileJava` — is
portable, which is most of what is left to do.

The engine tests run in `common` and therefore on **Java 21**, which is 1.21.1's.
That is deliberate: the rules engine ships in both jars, and a shared engine
proven on only one version is a claim rather than parity.
