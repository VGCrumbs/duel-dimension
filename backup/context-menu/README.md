# The context menu, as EDOPro has it

A snapshot taken before any work on replacing the card-action prompts with the
Nintendo DS ones. Nothing here has been deleted from the build — unlike
`../outfit-system`, this is a **reference copy of code that is still live**, so
that the EDOPro behaviour can be read side by side with whatever replaces it.

## Why a file copy and not a tag

The other backup in this folder points at `pre-outfit-removal` and says to use
`git checkout <tag> -- <path>`. That is the better mechanism and it is not
available here: this snapshot was taken with a working tree that had unrelated
unfinished work in it, so a tag would either have captured that too or would
have needed a commit nobody asked for. These are plain copies of the files as
they stood, outside `src/main/java`, so nothing here is compiled.

If a tag is made later, prefer it and delete this folder.

## What the system is

The menu a player gets on a card during a duel. It is **EDOPro's**, ported
rather than designed, and the port is deliberate down to the row height.

| file | what it does |
| --- | --- |
| `common/ocg/prompt/CardCommands.java` | the whole of it. EDOPro's `COMMAND_*` bitmask from `gframe/game.h`, the menu order and captions from `ClientField::ShowMenu` in `gframe/event_handler.cpp`, and the caption ids 1150–1162 out of `config/strings.conf` |
| `mc1211/clientutil/PromptOptions.java` | which of the engine's offered options act on the card being pointed at. Holds no rules — the engine already said what is legal |
| `mc1211/clientutil/EngineDuelScreen.java` | the 2D duel screen, which draws the menu and the card picker |
| `mc1211/clientutil/overworld/BoardPointerScreen.java` | the same menu for the 3D board |
| `mc1211/clientutil/overworld/CardChooser.java` | the picker for cards that are not individually visible on the field |
| `mc1211/ocg/prompt/PromptTranslator.java` | turns the engine's prompt into text |
| `mc1211/ocg/prompt/EnginePrompt.java` | the prompt as the client receives it |

`mc262` carries its own copy of every `mc1211` file above, textually parallel.
Only `CardCommands` is shared, in `common`.

## The two things worth knowing before changing any of it

**The labels are not ours to choose.** `CardCommands.label` reproduces
`ShowMenu`, conditional captions included — a monster in defence reads "To
Attack" and the same monster in attack reads "To Defence", off the same
`COMMAND_REPOS` bit. Replacing the wording is a change to that reproduction, and
the comment in that file naming `event_handler.cpp` stops being true the moment
it happens. Say so there rather than leaving the reference pointing at code the
mod no longer follows.

**`MENU_ORDER` is EDOPro's stacking order, not a preference.** Activate, Summon,
Special Summon, Set, S/T Set, Reposition, Attack, List, Operation, Reset — top
to bottom, and `MENU_ROW_HEIGHT` is its `Scale(21)`. A different game orders its
verbs differently, so a port of another game's prompts is a change to this array
as much as to the strings.

## Restoring

Copy a file back over its counterpart under `src/main/java`. They are unmodified
originals; the paths under `src/` here mirror the tree they came from with the
`de/cas_ual_ty/dueldimension` prefix removed.
