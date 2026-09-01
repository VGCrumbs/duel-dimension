# Notices

This file travels inside the released jar. It says what the artefact contains
that is not this project's own work, under what terms, and what you are entitled
to as a result.

`CREDITS.md` records who wrote what and why it is here. This file is the legal
half: the obligations, and how they are met.

## What licence the artefact is under

**The code and everything the licences reach is AGPL-3.0-or-later. It is not the
only licence in the jar, and `fabric.mod.json` no longer says it is.**

Duel Dimension's own source is GPL-3.0 (`LICENSE-GPL-3.0.txt` in the jar,
`LICENSE` in the repository), inherited from the mod it is forked from. The jar
also contains ocgcore, Project Ignis's card scripts, `cards.cdb`, `strings.conf`
and EDOPro's field art, all of which are AGPL-3.0-or-later. GPL-3.0 section 13
permits exactly this combination and states that the AGPL's section 13 then
governs the combined work — so the *program* you received is
AGPL-3.0-or-later.

But the jar also carries data and media that are under their own terms and are
not swept into that by being shipped beside it. `fabric.mod.json` used to
declare the single string `"AGPL-3.0-or-later"`, which was not true of the
artefact. It now declares the set:

| SPDX id | what it covers |
| --- | --- |
| `AGPL-3.0-or-later` | ocgcore, CardScripts, `cards.cdb`, `strings.conf`, most of EDOPro's field art, and the combined program |
| `GPL-2.0-only` | ten of EDOPro's field textures — Argon Sun's Fluorohydride ones, 57,843 bytes; see below |
| `MIT` | inside `ocgcore.dll`: the Fluorohydride ancestor and Lua 5.4.8 |
| `GPL-3.0-or-later` | this project's own source and assets, and the sleeve and card art inherited byte-for-byte from `YgoDuelingMod` |
| `Unlicense` | the part of the bundled card database (`ydm_db`) that comes from `YDM2-DB`: 10,756 of the 13,862 card files, 357 of the 691 set files, and all 23 `rarity_images` PNGs. The rest is this project's own and falls under its own licence |
| `CC0-1.0` | eleven of EDOPro's sound effects |
| `CC-BY-3.0` | `coinflip`, `diceroll` and `draw` |
| `Apache-2.0` / `LGPL-2.1-or-later` | the nested JNA and sqlite-jdbc jars |
| `LicenseRef-DuelDimension-NOTICE` | **the exceptions below, which have no licence anyone here can state** |

`GPL-2.0-only` rather than `-or-later` because the notice this project received
says only `licensed under GNU GPLv2` and does not say "or later"; the narrower
id is the one that asserts nothing extra. The ten files are `attack.png`,
`chain.png`, `chaintarget.png`, `equip.png`, `lim.png`, `lpf.png`, `mask.png`,
`negated.png`, `number.png` and `target.png` under
`assets/dueldimension/textures/duel/`, and the copyright line is preserved
verbatim in `EDOPRO_CREDITS.md` beside them.

`lim.png` is the most recent of the ten. It is EDOPro's forbidden/limited badge
sheet, added so the deck editor can mark a restricted card in the symbols a
player already reads; it ships byte-for-byte as EDOPro distributes it, and it
was already named in the verbatim notice in `EDOPRO_CREDITS.md` before it was
shipped, because that file is EDOPro's list rather than ours.

**The byte figure changed by more than that one file.** It read 124,309 for
nine, and the nine as they actually ship measure 45,240 — the copies in this
tree are re-encoded and smaller than EDOPro's originals, which come to 123,071
for the same nine. Neither figure reproduces 124,309, so what it was measured
against cannot now be established. The number above is the ten files as this
tree ships them, measured, which is what a notice about the contents of this jar
should have been saying.

`MIT` is in the declaration because both MIT grants require their notice to
travel in every copy, and the binary they are compiled into ships here.
`dueldimension_engine/COPYING.ocgcore.txt` carries both in full.

That last entry is not a licence. It is a pointer to this file, and it is here
because the honest answer to "what is everything in this jar under?" is
currently "these, and fifty-two files nobody can name terms for". Naming a
`LicenseRef` is how SPDX says exactly that; pretending otherwise is what the
single-string declaration was doing.

### The exceptions, named

Sixty-three files, **10,216,120 bytes**, ship today with no attributable
licence. `CREDITS.md` lists them in full with what is known about each. In
summary:

- **the eleven card-action button icons,
  `textures/gui/duel/cmd/*.png`, 12,339 bytes.** These are not "origin
  unrecorded" like everything below — their origin is recorded exactly, and it
  is a commercial Konami cartridge: *Yu-Gi-Oh! 5D's World Championship 2011*,
  `duel2d.pac`. Extracted deliberately, documented in the file beside them, and
  carrying no grant of any kind. They are the clearest case in this list and the
  first thing to revisit before any public release; `backup/context-menu`
  preserves the EDOPro text menu they replaced, and nothing about the button
  layout depends on these particular pixels;

- **seven audio files, 5,529,237 bytes**, whose origin no commit records — two
  of them re-encoded and trimmed derivatives of unidentified `magic_buzz` and
  `magic_fade`, and none of the seven present in EDOPro's own `sound/files.txt`;
- **three sound effects, 104,227 bytes**, credited only as "YGOPro Percy sound
  effects", which is an origin and not a grant;
- **the two card backs, `textures/duel/backs/tcg.png` and `backs/anime.png`,
  631,409 bytes.** `EDOPRO_CREDITS.md` and `textures/duel/LICENSE.md` say only
  that they were "supplied for this mod" and are **not** EDOPro's — a statement
  of what they are not. They replaced Icematoro's AGPL `cover.png` and
  `cover2.png`, they match no file in `YgoDuelingMod`, in EDOPro or in this
  project's Forge tree, and one of them is the retail TCG card back, whose
  design is Konami's;
- **the three duelist skins, `textures/entity/duelist/{joey,kaiba,yusei}.png`,
  5,436 bytes.** Their own `CREDITS.md` — which ships in this jar — names
  authors for two of them and says of all three: *"if this project is ever
  distributed publicly, confirm permission with each author first — fan skins
  are not automatically licensed for redistribution."* That confirmation has
  not happened. `yusei.png` has no named author at all;
- **`textures/misc/orichalcos_seal.png`, 328,676 bytes**, added by a commit that
  does not say where it came from, and present in no other tree of this project;
  `textures/item/16/orichalcos_debug.png`, 1,016 bytes, is the item icon added
  alongside it by the same commit and is in the same position;
- **the five `sleeves_millenium_*` designs, 35 files, 3,603,780 bytes**, absent
  from both `YgoDuelingMod` and the Forge tree and untracked until now.

That list was arrived at by hashing **every** binary asset in the built jar
against `YgoDuelingMod`, the EDOPro install and this project's Forge tree, not
by reading the commit history — which is how the card backs and the skins were
found after an earlier pass put the figure at forty-six.

They are not removed, because removing them would break features that work
today, and because "we do not know" is a reason to find out rather than a
finding. They are written down here so that they cannot be mistaken for
something that has been checked.

`LICENSE-AGPL-3.0.txt` in the jar is that licence in full. It is byte-identical
to the copy Project Ignis ships with the card scripts (md5
`4ae09d45eac4aa08d013b5f2e01c67f6`). The Unlicense travels as `UNLICENSE.txt`
inside the bundled database, and is unpacked into `ydm_db/` beside the data it
covers.

## Your right to the source

**AGPL-3.0 section 6** — you are entitled to the Corresponding Source for the
binaries conveyed to you. **AGPL-3.0 section 13** — that entitlement extends to
anyone who interacts with this software over a network, which for a Minecraft
mod means every player on a server running it.

The source of the whole work is public:

- Duel Dimension — <https://github.com/VGCrumbs/duel-dimension>
- ocgcore — <https://github.com/edo9300/ygopro-core>
- CardScripts — <https://github.com/ProjectIgnis/CardScripts>
- The database and `strings.conf` — <https://github.com/ProjectIgnis/DeltaBagooska>
  and <https://github.com/ProjectIgnis/DeltaPuppetOfStrings>

The exact snapshot bundled in this build is recorded in
`dueldimension_engine/bundle.properties` inside the jar: a content digest, and
where each piece came from.

**And the first of those repositories now contains everything needed to rebuild
this jar.** It did not. The engine payload — the card scripts, `cards.cdb`,
`strings.conf`, the native core — sat on one machine's disk and was untracked,
so a clone of the public repository built a jar with no rules engine in it while
the repository was public precisely to make the source available. It is
committed. `git clone` and `./gradlew25.cmd build` is the whole of it.

### The native core, exactly

`dueldimension_engine/native/win32-x86-64/ocgcore.dll` (sha-256
`9137cc3cd46c12e57d3054ca873fb68b50a918f1be3fa1db0cc50ba196e97ba7`) is a local
64-bit build of **unmodified** ygopro-core at commit
[`623c31a925630639b2978968b2024b104ae3abca`](https://github.com/edo9300/ygopro-core/tree/623c31a925630639b2978968b2024b104ae3abca)
(2026-07-31), made on 2026-08-04 with `ocgcore-build/CMakeLists.txt` from this
project's Forge tree. EDOPro's own Windows build is 32-bit and cannot be loaded
by the 64-bit Java that Minecraft 26.2 requires, which is the only reason a
build of our own exists.

That commit plus that build script is the Corresponding Source for this binary.
The pairing was established by hash rather than by recollection: the build
tree's own output is byte-identical to the shipped file, its `CMakeCache.txt`
names the checkout it compiled, and that checkout is clean at that commit.

## Modifications

- The card scripts are shipped as a **subset**: the tree's root files and
  `official/` only. `rush/`, `skill/`, `goat/`, `unofficial/` and `pre-errata/`
  are omitted because the mod's script provider never looks in them. No file
  that is shipped has been altered, and the tree's own `COPYING.txt` is kept
  exactly as it ships, inside `script.zip`.
- `cards.cdb` and `strings.conf` are shipped unmodified.
- The native core is a local build of unmodified upstream sources; see above.
- EDOPro's field, mat and interface art was ported into this mod's asset layout
  and rescaled. Per-file copyright is preserved in
  `assets/dueldimension/textures/duel/EDOPRO_CREDITS.md`.

## The bundled card database

`dueldimension_engine/ydm_db.zip` in the jar is the mod's own card database —
14,686 files, 16,931,225 bytes raw — unpacked into `<gamedir>/ydm_db` on first
run. It is separate from `cards.cdb`, which is the engine's; this one is what
the deck editor, the shop and the pack openings read.

It comes from <https://github.com/CAS-ual-TY/YDM2-DB> (10,756 of the 13,862 card
files and 357 of the 691 set files) plus this project's own
`tools/update_card_db.py`, which generates the rest from the same public source
YDM2-DB is itself built from. YDM2-DB is under **The Unlicense**, which ships
verbatim as `UNLICENSE.txt` inside the archive:

> "Anyone is free to copy, modify, publish, use, compile, sell, or distribute
> this software, either in source code form or as a compiled binary, for any
> purpose, commercial or non-commercial, and by any means."

That dedication is the database author's and reaches only as far as his own
copyright. **The card names, effect texts and stats are Konami's**, and shipping
them changes nothing about that in either direction; see the trademark note at
the end of this file. YGOPRODeck, from whose API both databases are built, says
the same of its own site: "The literal and graphical information presented on
this site about Yu-Gi-Oh! … is copyright 4K Media Inc, a subsidiary of Konami
Digital Entertainment, Inc."

**Your copy is never overwritten.** The unpack writes a file only when no file
of that name exists. `ydm_db` is a folder players edit — their `alt_art` scans
live in it, and there is no second copy to restore from — so the bundle is not
allowed to correct, merge or refresh anything. Details in `ydm_db/BUNDLED.md`,
which is unpacked alongside.

## What is deliberately NOT bundled

**The Forbidden & Limited card lists.** <https://github.com/ProjectIgnis/LFLists>
carries no licence file of any kind — GitHub's licence API returns 404, the
repository metadata returns `"license": null`, and the repository root holds only
`.gitattributes` and the eight `.lflist.conf` files. Unlike `cards.cdb`, whose
authoring repository is also silent but which is *distributed* through
repositories that do carry the AGPL, there is no such chain for the banlists:
EDOPro fetches them straight from that repository. No permission to redistribute
has been granted, so they are not redistributed. The mod discovers them from an
EDOPro install or from `<gamedir>/lflists`, and offers "No banlist" when it finds
neither.

**Card artwork.** Fetched per installation from YGOPRODeck at runtime, as it
always has been — every bundled card file names its image as a URL rather than
carrying one. That is also what YGOPRODeck asks for: "Do not continually hotlink
images directly from this site. Please download and re-host the images
yourself."

**The extended-art scans (`ydm_db/alt_art`).** Taken from Yugipedia, whose
`Yugipedia:Licensing` page licenses "the text on Yugipedia" under CC BY-SA 4.0
and is silent on card images; the file page for one of the scans states the work
"is copyrighted and unlicensed" and that "Any other uses of this image may be
copyright infringement". A wiki hosting an image under its own fair-use claim
and a mod jar redistributing it are different acts, and that claim does not
travel. The mod reads `alt_art/` whenever it is present, and
`tools/fetch_mamo_extended_art.py` fetches it on your machine — a fetch you
make, not a redistribution we make.

## Third-party libraries inside the jar

- `META-INF/jars/jna-5.13.0.jar` — Java Native Access, Apache-2.0 OR
  LGPL-2.1-or-later. Its own `META-INF/LICENSE`, `META-INF/AL2.0` and
  `META-INF/LGPL2.1` are intact inside it.
- `META-INF/jars/sqlite-jdbc-3.42.0.0.jar` — Apache-2.0. Its own
  `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE` and `LICENSE.zentus` are
  intact inside it.

## Trademarks

Yu-Gi-Oh! and every card name, artwork and effect are the property of Konami.
This is a fan project and is not affiliated with or endorsed by Konami, nor by
Project Ignis.
