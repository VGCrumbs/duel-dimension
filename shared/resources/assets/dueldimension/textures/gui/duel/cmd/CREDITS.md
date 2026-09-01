# The card-action buttons

Eleven 32×64 PNGs, two states each. **Extracted from a commercial Nintendo DS
cartridge**, and there is no licence for them.

## Where they come from, exactly

*Yu-Gi-Oh! 5D's World Championship 2011 — Over the Nexus*, Konami, NDS, game
code `BYYP` (Europe). `Data_arc_pac/duel2d.pac`, entries `btn_act_2011`,
`btn_sum_2011`, `btn_spsum_2011`, `btn_set_2011`, `btn_set2_2011`,
`btn_atkp_2011`, `btn_defp_2011`, `btn_fsum_2011`, `btn_atk_2011`,
`btn_list_2011`, `btn_sur_2011`.

Decoded by `NexusDecomp/scripts/duel_buttons.py`; the format is written up in
that file, in `NexusDecomp/FINDINGS.md`, and in the vault at
`[DOCUMENTATION]/World Championship 2011/90 The duel buttons.md`.

The only change made to them is the order of the two states: the ROM stores
selected first, and these are written normal-first because the mod's state
atlases put idle at row 0.

## The licence position, stated plainly

**Konami's, with no grant of any kind.** This is a different position from the
other unattributed files this project ships, and it is worth not blurring:
elsewhere in `NOTICE.md` the entries are files whose origin *nobody recorded*.
These are files whose origin is recorded precisely, and the answer is a
copyrighted commercial game.

They are here on the same fan-project footing as the card artwork, which
`textures/duel/LICENSE.md` already describes as "Konami's property, used on the
same fan-project footing as the rest of the mod". That footing is a description
of what this project is, not a licence it has been given.

**If this mod is ever distributed publicly, these are among the first things to
revisit** — either replaced with original icons drawn for the purpose, or
dropped in favour of the EDOPro text menu that `backup/context-menu` preserves.
Nothing about the button layout depends on these particular pixels.
