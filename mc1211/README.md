# The 1.21.1 platform — blocked on Loom, not on porting

This module does not build yet, and it is not in `settings.gradle`. The reason is
worth reading before anyone tries to fix it by porting code into it: **nothing
here is waiting on code.**

## What was tried, and what happened

Loom **1.17.18** is what Minecraft 26.2 requires. Asked to configure 1.21.1 it
fails at configuration time, before compiling anything:

```
> Cannot use Mojang mappings in a non-obfuscated environment
```

and the `mappings` dependency configuration is never created either:

```
> Could not find method mappings() for arguments [net.fabricmc:yarn:1.21.1+build.3:v2]
```

Both were measured against `mc1211/build.gradle` as it stands.

Two things were checked before concluding:

- **It is not cross-project state.** The same failure happens with `mc262`
  removed from `settings.gradle` entirely, so it is not that a 26.2 module
  configured a non-obfuscated environment first.
- **It is not a missing class.** `net.fabricmc.loom.util.Constants$Configurations`
  in the 1.17.18 jar still declares `MAPPINGS = "mappings"`, and the layered
  mapping spec classes are all present. The pipeline exists; this Loom simply
  will not enable it, because modern Minecraft ships unobfuscated and does not
  need one. 1.21.1 predates that.

## Why this cannot be fixed in place

A Gradle build resolves **one** version of a plugin, in `pluginManagement`. The
26.2 platform needs 1.17.18; 1.21.1 needs an older Loom with the obfuscated
pipeline. They cannot both be subprojects of one build.

## The fix

A **composite build**. `mc1211` gets its own `settings.gradle` pinning its own
Loom, and the root includes it:

```groovy
// root settings.gradle
includeBuild 'mc1211'
```

`common` has to be reachable from inside that child build. It has no Loom
dependency and no Minecraft dependency, so the straightforward options are to
make it an included build of its own, or to publish it to `mavenLocal` and
depend on it by coordinate. The first keeps one source of truth; the second is
simpler to wire.

## What already works

`ToolchainProof.java` is written and is the first thing that should compile once
the above is done. It exists to answer three questions a 26.2 build cannot:

1. does Loom resolve 1.21.1 alongside a 26.2 module,
2. is `common` — Java 21, no Minecraft — usable from a 1.21.1 module,
3. do Mojang names work, so ported files keep the vocabulary they are in.

The Java-21 half of (2) is already proven: `common` is compiled at release 21
today and the 26.2 platform consumes it.
