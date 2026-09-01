# Duel field textures

Taken from Project Ignis: EDOPro. **Two licences, not one** — most of it is
AGPL-3.0-or-later, the same licence as the rules engine and card scripts this
mod already embeds, and Argon Sun's Fluorohydride set is GPL-2.0. Copyright
notices are preserved verbatim in `EDOPRO_CREDITS.md`; in short:

| Files | Copyright | Licence |
| --- | --- | --- |
| `field4.png`, `field-transparent.png`, `field-transparent4.png` | © 2020 Icematoro | AGPL-3.0-or-later |
| `act.png`, `lp.png`, `unknown.png` (converted from `unknown.jpg`) | © 2020 NaimSantos | AGPL-3.0-or-later |
| `bg.png` | © 2020 LogicalNonsense | AGPL-3.0-or-later |
| `attack.png`, `chain.png`, `chaintarget.png`, `equip.png`, `lim.png`, `lpf.png`, `mask.png`, `negated.png`, `number.png`, `target.png` | © 2012 Argon Sun | **GPL-2.0** |

**The last row used to be wrong here** and right in the two files either side of
it. This table credited nine of those ten to NaimSantos under AGPL, while
`EDOPRO_CREDITS.md` beside it — which is EDOPro's own notice, verbatim — and the
repository's `NOTICE.md` and `CREDITS.md` all said Argon Sun, GPL-2.0. The
verbatim notice is the one that governs; this summary was a paraphrase that had
drifted. `lim.png` joining the set is what brought it to light, and the same
warning further down this file about two files not being allowed to disagree
applies to this row as much as to the card backs.

`GPL-2.0`, not `-or-later`: the notice received says only `licensed under GNU
GPLv2`, so the narrower reading is the one that claims nothing extra.

Because these are copyleft, this mod's source must remain available to anyone it
is distributed to (and to users of any network service running it). That is
already the case for the engine, so no new obligation is introduced — but do
not strip these notices, and do not ship these files in a closed-source build.

## Card backs — not from EDOPro

`backs/tcg.png` and `backs/anime.png` were supplied for this mod and are **not**
covered by the table above. They replaced Icematoro's `cover.png` and
`cover2.png`, which is why those two no longer appear in it and no longer exist
in the tree; the same correction is recorded in `EDOPRO_CREDITS.md`, and the two
files must not be allowed to disagree again. Sleeve art under
`textures/item/*/sleeves_*.png` is likewise outside this licence.

Card artwork is **not** here: it comes from the mod's own card-image pipeline
and remains Konami's property, used on the same fan-project footing as the
rest of the mod.
