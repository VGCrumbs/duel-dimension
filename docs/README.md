# Documentation

Project documentation for this fork of the Ygo Dueling Mod.

## Contents

- [DuelDimension.md](DuelDimension.md) — **project hub** (Obsidian-flavored): goals, decision log, phase checklists with gates, and how every feature works. Start here.
- [ocgcore-integration.md](ocgcore-integration.md) — how the EDOPro-core rules engine is embedded (JNA binding, duel loop, licensing, toolchain).
- [ydm2-db-wiki/](ydm2-db-wiki/README.md) — mirror of the [YDM2-DB wiki](https://github.com/CAS-ual-TY/YDM2-DB/wiki), which documents the JSON format of the card database (cards, sets, distributions, `db.json`).

## Project at a glance

| | |
| --- | --- |
| Type | Minecraft Forge mod |
| Minecraft | 1.19.2 |
| Forge | 43.2.8 |
| Mappings | Parchment `2022.11.27-1.19.2` |
| Mod id | `dueldimension` (display "Duel Dimension") |
| Group | `de.cas_ual_ty.dueldimension` |
| License | GPL-3.0 |

The card data is **not** in this repo — the mod downloads it from a `db.json` source URL configured in the YDM config file. See the wiki mirror for the format and update rules.

## Upstream

- Mod: <https://github.com/CAS-ual-TY/YgoDuelingMod>
- Database: <https://github.com/CAS-ual-TY/YDM2-DB>
- CurseForge: <https://www.curseforge.com/minecraft/mc-mods/ydm-ygo-dueling-mod-ii>

Card data credit: [ygoprodeck.com](https://ygoprodeck.com/).
