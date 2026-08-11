# The rules engine, unpacked

Duel Dimension does not implement Yu-Gi-Oh!'s rules. It loads **ocgcore** and
plays **Project Ignis's card scripts**, and this folder is the copy of those the
mod carries inside its own jar, written out here because nothing in the engine's
reading path can see inside a jar:

- the card scripts are read with `Files.readAllBytes` over a real path,
- `cards.cdb` is opened by the SQLite driver through a `jdbc:sqlite:` URL, which
  needs a file and not a stream,
- the native core is handed to JNA by absolute path.

**The mod owns this folder.** It is rewritten whenever the version in `.bundle`
stops matching the one in the jar, so anything edited here is lost on the next
mod update. Delete `.bundle` to force a fresh unpack.

**It is a fallback, not a preference.** If you have EDOPro installed, the mod
uses yours and not this — yours updates several times a week and its scripts and
database were cut from the same commit as each other. On a machine with EDOPro
this folder holds only the pieces EDOPro could not supply, and usually nothing
at all. Point `-Docg.scripts`, `-Docg.cdb`, `-Docg.lib` or `-Docg.strings`
somewhere else to override either.

    script/                  card scripts (constant.lua, utility.lua, proc_*.lua, official/)
    expansions/cards.cdb     the card database the engine reads
    config/strings.conf      the engine's own text tables
    native/<platform>/       the ocgcore build for this platform
    .bundle                  which version is unpacked, and which pieces

## Licence

`script/`, `cards.cdb`, `strings.conf` and the native core are all
**AGPL-3.0-or-later**. `COPYING.AGPL-3.0.txt` is that licence in full;
`COPYING.ocgcore.txt` is ocgcore's own notice, which also carries the MIT grants
for its Fluorohydride ancestor and for Lua; `script/COPYING.txt` is the scripts'
own copy, kept exactly as it ships.

Because these are conveyed to you, you are entitled to the corresponding source
of the whole work, including over a network. It is at
<https://github.com/VGCrumbs/duel-dimension>, and the upstreams are
<https://github.com/edo9300/ygopro-core>,
<https://github.com/ProjectIgnis/CardScripts> and
<https://github.com/ProjectIgnis/DeltaBagooska>. See the mod's own `CREDITS.md`
for what was taken from where, and `bundle.properties` inside the jar for the
exact snapshot.
