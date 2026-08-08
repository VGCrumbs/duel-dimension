# Database extras

The bundled card database (`ydm_db`, downloaded at runtime) stops in **August
2021** — its newest set is King's Court, 07-08-2021. Anything printed after that
is simply absent, which is why a card like Mimicking Man-Eater Bug could not be
found: not a gap in one set, but a cutoff.

This folder is the tracked record of sets imported on top of it. It exists
because `ydm_db` is downloaded and gitignored, so data written only there is
lost the moment the database is refreshed or the workspace is cloned fresh.

## Adding a set

    python tools/import_set.py "Burst of Destiny" BODE "Booster Pack (Series 11)" 05-11-2021

or add it to `EXTRA_SETS` in that script and run it with no arguments to
re-import everything at once. The importer writes to this folder **and** into
`run/ydm_db`, so the game picks it up immediately.

Card JSON is generated in the mod's own schema, which differs per variant — a
Link has no `def` or `level`, an Xyz carries `rank` rather than `level`, a
Pendulum carries its scales — so each is built explicitly rather than by
emitting a superset and hoping the reader ignores the rest. The generated key
sets are checked against the existing database's.

## Known gap

The mod reads `ydm_db` only; nothing merges this folder in automatically. On a
fresh clone the importer has to be run once. Wiring the merge into
`DdDatabase.readFiles` is the obvious follow-up and needs a decision about where
extras should live relative to the game directory.
