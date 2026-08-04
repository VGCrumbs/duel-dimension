# YDM2-DB Wiki (mirror)

Verbatim copy of the wiki for the card-database repository that YDM loads at runtime.

- Source: <https://github.com/CAS-ual-TY/YDM2-DB/wiki>
- Database repo: <https://github.com/CAS-ual-TY/YDM2-DB>
- Author: CAS-ual-TY
- Snapshot taken: 2026-08-04, from wiki commit `4941784` ("Updated Card Template", 2023-10-31)

The pages are unmodified, so links inside them still point at the upstream `CAS-ual-TY/YDM2-DB` repo. Anything referring to a sidebar or "table of contents on the right" is GitHub wiki UI — use the list below instead.

## Contents

| Page | What it covers |
| --- | --- |
| [Home](Home.md) | `db.json` format, how the mod decides to update the DB, gotchas around cached images |
| [Card-Template](Card-Template.md) | JSON keys for a card file, per card type (Monster / Spell / Trap) |
| [Set-Template](Set-Template.md) | JSON keys for a set file, the three `pull_type` modes, sub-set examples |
| [Distribution-Template](Distribution-Template.md) | JSON keys for a distribution file, pull weights and how probabilities work out |

## Refreshing this mirror

```bash
git clone https://github.com/CAS-ual-TY/YDM2-DB.wiki.git /tmp/ydm2-db-wiki && cp /tmp/ydm2-db-wiki/*.md docs/ydm2-db-wiki/
```
