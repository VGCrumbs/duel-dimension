# Duel Dimension

A Minecraft mod that plays real Yu-Gi-Oh!, by embedding the actual ygopro-core
(`ocgcore`) engine through JNA rather than reimplementing the rules. Forked from
CAS-ual-TY/YgoDuelingMod; mod id `dueldimension`.

Two trees, one repository (`VGCrumbs/duel-dimension`):

| | branch | MC | loader | JDK | wrapper |
| --- | --- | --- | --- | --- | --- |
| **`CrumbyDueling`** — this tree, canonical and playable | `crumby` | 1.19.2 | Forge 43.2.8 | 17 | `gradlew17.cmd` |
| **`CrumbyDuelingFabric`** — port in progress | `fabric` | 26.2 | Fabric | 25 | `gradlew25.cmd` |

This is the tree that works. Features are built here first and ported across;
the Fabric tree's `PORTING.md` records what has made the crossing and how.
A fix made here that is already ported should be made there too, or the two
drift.

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

    ./gradlew17.cmd build        # Forge tree, JDK 17
    ./gradlew17.cmd test          # unit tests
    ./gradlew17.cmd runClient     # dev client, 1634×920

Two things do not work outside a Windows machine with a GPU:

- **`native/ocgcore.dll` is a Windows native**, loaded through JNA. The duel
  engine tests need it. (It is 32-bit — a 64-bit JVM cannot load it, which cost
  an afternoon once.)
- **`runClient` launches a real game.** Anything verified by looking at it has to
  be verified on that machine.

Everything else — the card database, the deck and profile logic, the bot's
decisions, the pure-Java tests — is portable.
