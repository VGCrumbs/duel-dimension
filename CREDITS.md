# Credits

Duel Dimension does not implement Yu-Gi-Oh!'s rules. It embeds other people's
work and plays their card scripts. This file records whose, and under what terms.

Where a licence is stated below it is stated because it **binds this project**,
not as a courtesy. Anything bundled into a release brings its licence with it.

**Most of what follows is now inside the jar.** The mod used to require a
separate EDOPro install and read the engine out of it; it now ships a copy and
unpacks it beside the game on first run. `NOTICE.md` — which ships in the jar
too — states the obligations that creates and how they are met, and
`bundle/provenance.properties` records exactly which snapshot was taken. What is
bundled is marked **[bundled]** below.

## The rules engine

**ocgcore** — Project Ignis / EDOPro. **[bundled]**
The duel engine, loaded through JNA. Every rule, every timing window and every
card interaction in a duel comes from here; none of it is reimplemented.

Licensed **AGPL-3.0-or-later**. That obliges this project to keep its own source
available to anyone it is distributed to, including over a network. See
`src/main/resources/assets/dueldimension/textures/duel/LICENSE.md`, which already
records the same obligation for the field art.

`COPYING.ocgcore.txt` travels with it, in the jar and in the unpacked folder. It
carries the AGPL grant **and two MIT grants** whose own terms require them to be
included in every copy: Argon Sun's Fluorohydride ancestor (`Copyright (c) 2015`)
and Lua (`Copyright © 1994–2019 Lua.org, PUC-Rio`). The core bundled here links
Lua 5.4.8.

That file is ygopro-core's own `LICENSE` **at the commit the shipped binary was
built from**, byte for byte, and not EDOPro's copy of the same notice — theirs
comes from an older release and still reads `Copyright (C) 2019-2025` where the
source this binary was compiled from reads `2019-2026`. A notice that travels
beside a binary has to be the notice from that binary's own source.

The shipped binary is a local 64-bit build of unmodified upstream at commit
`623c31a925630639b2978968b2024b104ae3abca`, because EDOPro's own Windows build
is 32-bit and a 64-bit JVM cannot load it. `NOTICE.md` records how that pairing
was established.

- https://github.com/edo9300/ygopro-core
- https://projectignis.github.io/

## The card scripts

**CardScripts** — Project Ignis. **[bundled]**
The Lua behind every card. ocgcore ships no card behaviour of its own: a missing
script means a card that summons and battles correctly and whose effect text is
inert. Also **AGPL-3.0-or-later**.

Shipped as the tree's root files plus `official/` — 12,727 files, the only ones
this mod's script provider ever looks in. The tree's own `COPYING.txt` is the
sole licence notice for all of them (no card script carries a per-file header),
so it is kept inside the bundle exactly as it ships, byte for byte.

- https://github.com/ProjectIgnis/CardScripts

## The card database

**cards.cdb** — Project Ignis. **[bundled]**
Passcodes, types, stats and strings, as the engine reads them.

**Where the permission comes from matters here.** BabelCDB, the repository that
authors these, carries no licence file at all — GitHub's licence API returns 404
for it. Its own README names
<https://github.com/ProjectIgnis/DeltaPuppetOfStrings> as "the repository the
users get updates from", and that repository — like
<https://github.com/ProjectIgnis/DeltaBagooska>, which is what EDOPro actually
fetches from — carries the AGPL-3.0 text. The grant is theirs, not BabelCDB's,
so both are cited.

- https://github.com/ProjectIgnis/BabelCDB (authored)
- https://github.com/ProjectIgnis/DeltaPuppetOfStrings (distributed, AGPL-3.0)
- https://github.com/ProjectIgnis/DeltaBagooska (distributed, AGPL-3.0)

## The engine's text tables

**strings.conf** — Project Ignis. **[bundled]**
The system strings, counter names and victory reasons the engine refers to by
number; without it a prompt reads as an integer. Shipped from DeltaBagooska,
**AGPL-3.0-or-later**.

- https://github.com/ProjectIgnis/DeltaBagooska

## The banlists — deliberately NOT bundled

**LFLists** — Project Ignis.
The Forbidden & Limited lists that decide deck legality. **Not shipped**, and not
for a size reason: <https://github.com/ProjectIgnis/LFLists> carries no licence
file of any kind, and unlike `cards.cdb` there is no distribution repository that
supplies one — EDOPro fetches those lists straight from it. Nobody has granted
permission to redistribute them, so nobody here does.

The mod finds them in an EDOPro install or in `<gamedir>/lflists`, and offers
"No banlist" when it finds neither.

- https://github.com/ProjectIgnis/LFLists

## Libraries

**Java Native Access (JNA)** 5.13.0 — bundled inside the jar at
`META-INF/jars/jna-5.13.0.jar`. What loads ocgcore. **Apache-2.0 OR
LGPL-2.1-or-later**; its own licence files are intact inside that jar.

- https://github.com/java-native-access/jna

**sqlite-jdbc** 3.42.0.0 — bundled inside the jar at
`META-INF/jars/sqlite-jdbc-3.42.0.0.jar`. What reads `cards.cdb`.
**Apache-2.0**; its own `LICENSE` and `LICENSE.zentus` are intact inside that
jar.

- https://github.com/xerial/sqlite-jdbc

## The mod's card database

**YDM2-DB** — CAS_ual_TY. **[bundled]**
`ydm_db` — the card, set, rarity and distribution JSONs the mod's own systems
read, as distinct from `cards.cdb`, which is the engine's. It used to be a
download on first launch, tens of megabytes fetched from GitHub with the game
waiting; the jar now carries it and unpacks what is missing.

Licensed **The Unlicense**, a public-domain dedication. It ships verbatim as
`UNLICENSE.txt` inside the bundle (md5 `d512eeaf0d5285acc53d4569054cbb08`,
byte-identical to YDM2-DB's own `LICENSE`):

> "Anyone is free to copy, modify, publish, use, compile, sell, or distribute
> this software, either in source code form or as a compiled binary, for any
> purpose, commercial or non-commercial, and by any means."

**What is ours and what is theirs**, established by listing both trees rather
than by recollection: 10,756 of the 13,862 card files and 357 of the 691 set
files are YDM2-DB's; the remaining 3,106 cards and 334 sets were generated here
by `tools/update_card_db.py`. The files are re-serialised (ours LF, theirs
CRLF), so no hash matches upstream — the content of every shared card file but
`elemental_hero_stratos.json` does. The Unlicense permits "copy, modify,
publish"; only byte-identity is not claimed.

The 23 overlay PNGs in `rarity_images/` **are** byte-identical to YDM2-DB's, and
are CAS_ual_TY's rather than EDOPro's. `rarities/` and `distributions/` are
partly this project's own design work and contain no card text at all.

- https://github.com/CAS-ual-TY/YDM2-DB

**YGOPRODeck** — where both databases get their card text and set lists, and
where the artwork is fetched from at runtime. Every card file under
`ydm_db/cards` names its image as a URL, so **no artwork is bundled**; that is
also what they ask for — "Do not continually hotlink images directly from this
site. Please download and re-host the images yourself."

They disclaim the underlying rights themselves: "The literal and graphical
information presented on this site about Yu-Gi-Oh!, including card images, the
attribute, level/rank and type symbols, and card text, is copyright 4K Media
Inc, a subsidiary of Konami Digital Entertainment, Inc."

- https://ygoprodeck.com/api-guide/

**Yugipedia** — the extended-art scans under `ydm_db/alt_art`, taken from the
Set Card Galleries for *Limit Over Collection: The Heroes*. Used because the
artwork model everywhere else keys on passcode, and an extended art shares its
base card's passcode, so no API can express it.

**Not bundled, and this one is not a size decision.** Yugipedia's
`Yugipedia:Licensing` covers "the text on Yugipedia" under CC BY-SA 4.0 and says
nothing about card images; the file page for one of the scans we hold states
that the work "is copyrighted and unlicensed" and that "Any other uses of this
image may be copyright infringement". A wiki's own fair-use claim does not
travel into a mod jar. The mod still reads `alt_art/` whenever it is there —
`tools/fetch_mamo_extended_art.py` fetches it onto your machine, which is a
fetch you make rather than a redistribution we make.

- https://yugipedia.com/

## Field, mat and interface art

EDOPro's own textures, with per-file copyright preserved verbatim in
`textures/duel/EDOPRO_CREDITS.md` and summarised in `textures/duel/LICENSE.md`.
Icematoro, NaimSantos, LogicalNonsense and Argon Sun each hold copyright on part
of it, under AGPL-3.0-or-later or GPL-2.0.

The card backs under `textures/duel/backs/` and the sleeve art under
`textures/item/*/sleeves_*.png` are **not** EDOPro's and are not covered by that
licence.

## Sleeve art and the Patreon card art

**YgoDuelingMod** — CAS_ual_TY, **GPL-3.0**, whole repository, assets included.

This used to be recorded only as what it is *not*. It has now been settled by
hashing every file against that repository:

- **32 of the 37 sleeve designs** — 224 files across all seven size tiers,
  22,954,631 bytes — are **byte-identical** to `YgoDuelingMod` and are therefore
  covered by its GPL-3.0, the same inheritance this project's code already
  relies on.
- **All 77 files of the eleven Patreon card arts** (`1_0.png`…`11_0.png`,
  18,035,288 bytes) are likewise byte-identical and likewise covered. Their size
  in the jar is a size question; it is not a rights question.

The five `sleeves_millenium_*` designs are the exception and are listed below as
unresolved.

- https://github.com/CAS-ual-TY/YgoDuelingMod

## Unresolved — shipped, and nobody here knows whose

This section exists so these are not mistaken for things that have been checked.
Every file named below is in the released jar today and appears in no credits
file in this repository. No claim is made about what any of it is; the point is
that the question is open and needs an answer from whoever added them.

**Seven audio files, 5,529,237 bytes.** `sounds/duel/EDOPRO_SOUND_CREDITS.md`
does have a background-music section, but it names different files by different
names (Ketsa, Chad Crouch, Mid-Air Machine, Steve Combs) and none of these is
among them:

    sounds/duel/music/something_evil.ogg      3,482,966
    sounds/duel/music/normal.ogg              1,898,367
    sounds/duel/orichalcos/fade.ogg              74,552
    sounds/duel/orichalcos/buzz.ogg              23,010
    sounds/duel/num_green.ogg                    22,061
    sounds/duel/num_red.ogg                      15,417
    sounds/duel/phasechange.ogg                  12,864

`textures/misc/orichalcos_seal.png` (328,676 bytes) is in the same position. It
exists only in the Fabric tree — the Forge tree has no file matching
`*orichalcos*` at all — and the commit that added it describes the feature at
length without naming a source.

**Three EDOPro sound effects with no licence stated.**
`sounds/duel/EDOPRO_SOUND_CREDITS.md` gives CC0 or CC BY 3.0 for seventeen of
the twenty SFX shipped, and for these three says only "YGOPro Percy sound
effects" — which names an origin, not a grant:

    sounds/duel/activate.ogg                     41,817
    sounds/duel/specialsummon.ogg                39,001
    sounds/duel/destroyed.ogg                    23,409

**The five `sleeves_millenium_*` designs** — 35 files, 3,603,780 bytes. They are
in neither `YgoDuelingMod` nor the Forge tree, `git status` showed all of them
untracked, and `tools/gen_sleeve_assets.py` names them but generates only their
models, never the PNGs. The other 32 designs are settled; see above.

**A correction to an earlier version of this section.** It warned that some
credits in `EDOPRO_SOUND_CREDITS.md` are CC BY-NC and that Ketsa's is
CC BY-NC-ND, and that these sit badly under a blanket AGPL statement. That
warning was about files this mod does not carry: **there is no `.mp3` and no
`BGM/` path anywhere in the jar.** Those entries describe EDOPro's background
music, inherited along with the credits file itself. The objection stands for
the four items above and not for the music.

## The mod this one is forked from

**YgoDuelingMod** — CAS_ual_TY.
Duel Dimension is a fork. The collection, deck box, binder, pack and shop systems
are all descended from that codebase, and the Fabric tree is a port of it — see
`PORTING.md`.

- https://github.com/CAS-ual-TY/YgoDuelingMod

## Cards themselves

Yu-Gi-Oh! and every card name, artwork and effect are the property of Konami.
This is a fan project, used on the same footing as the reference client it is
built on, and is not affiliated with or endorsed by Konami.
