# The duel disk, as it was before editing

A byte-for-byte copy of the standard duel disk's three files, taken before the
model was opened in Blockbench. Nothing here is compiled or loaded — it is
outside `src/main/resources`, so the game never sees it.

| file | sha256 (first 12) | what it is |
| --- | --- | --- |
| `assets/dueldimension/items/duel_disk.json` | `92ac89a7ab6e` | the item definition, 96 bytes |
| `assets/dueldimension/models/item/duel_disk.json` | `5002d8f3de9b` | the model: 71 elements, groups, all eight display transforms |
| `assets/dueldimension/textures/item/duel_disk.png` | `9e287619a46f` | 16×16 RGBA |

## Putting it back

    cp -r backup/duel-disk/assets/. src/main/resources/assets/

Or one file at a time, which is usually what is wanted — the texture is rarely
the thing that went wrong:

    cp backup/duel-disk/assets/dueldimension/models/item/duel_disk.json \
       src/main/resources/assets/dueldimension/models/item/

## The model is now split before it is loaded

`models/item/duel_disk.json` is still the file to edit in Blockbench, but the
game no longer reads it. `tools/gen_disk_cards.py` derives two files from it:

    python tools/gen_disk_cards.py

- `duel_disk_frame.json` — everything that is not a card slot. This is what
  `items/duel_disk.json` points at.
- `duel_disk_slots.json` — the ten `cards_top`/`cards_bottom` boxes as numbers,
  drawn by `DiskCardsRenderer` so they can appear and disappear with the board.

**Restoring the model from here is not enough on its own** — run the script
afterwards, or the frame the game loads stays whatever it was.

## What this does NOT cover

Only the **regular** disk. The other nine — academia (and its three colours),
chaos, jewel, kaibaman, rock_spirit, trueman — are untouched and have no copy
here.

Blockbench writes the whole model file on save, so a save is all-or-nothing:
element edits, group changes and display transforms come back together. If only
the display transforms need reverting, they are the `"display"` key and can be
lifted out of the copy by hand rather than restoring the file.
