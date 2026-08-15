# Monster sprites

Billboarded figures that stand on a face-up monster card. Drop a sheet here, add
an entry to `../../../monster_sprites.json`, and that card has a monster.

Neither step is usually done by hand. The **billboard editor** does both: point
it at a card, import a sheet, line the frames up while watching the hologram,
and press **To mod**. Everything below describes what it writes, for reading a
diff or fixing something without opening the game.

## Where a sheet goes

In the folder for the card's **type** — `spellcaster/`, `warrior/`, `fiend/`,
`dragon/`, `zombie/` and so on. The type is printed on the card, so two people
filing the same sprite put it in the same place; make the folder if it does not
exist yet. The folder is part of the name in the list.

The editor asks the card database for the type rather than asking you, for the
same reason: a folder chosen by hand is one that disagrees with the next
person's choice for the same monster.

## The sheet

- **A grid of equal cells**, read left to right and then down. One row is the
  common case; more than one row is how you fit seven frames in a file without
  it being seven cells wide and one tall.
- **Feet on the bottom edge of every cell.** The billboard stands the sprite on
  its card, so a cell with padding under the feet makes the monster hover. This
  is the one rule the code cannot check for you.
- Transparent background.
- **No `.mcmeta`.** Minecraft's own texture animation would fight this one, and
  the blur it enables bleeds one frame into the next.

Nothing else is fixed. Cell size, frame count and proportions are all measured
from the file the first time it is drawn, so a 512×256 sheet of four frames and
a 900×300 sheet of three both work without saying so anywhere.

## The entry

In `assets/dueldimension/monster_sprites.json`:

```json
{
  "card": 46986414,
  "body": {
    "sheet": "spellcaster/dark_magician",
    "columns": 4, "rows": 1, "first": 0, "frames": 4,
    "ticks": 5, "loop": "PING_PONG"
  },
  "scale": 1.0
}
```

The passcode, the file's name without its extension, how the cells are cut, and
how the frames follow one another. Everything except `sheet` may be left out;
the reader fills in the same defaults the editor starts from.

**The passcode is looked up, not remembered.** A wrong one fails in the quietest
way this code has — no error, no warning, just a card that never grows a
monster.

`LOOP` runs `0 1 2 3 0 1 2 3`. `PING_PONG` runs `0 1 2 3 2 1` and repeats, which
is what a sprite drawn as a single sweep needs: the Dark Magician's four frames
are him drifting downwards, so looping them would snap him back to the top every
second, while playing them back again is the float they were drawn to be. `BOB`
holds one cell and moves it instead — for a monster worth putting on a board and
not worth drawing four times — and there `frames` stops meaning a number of
pictures and means how many steps the rise and fall takes.

## A region of a sheet

`x`, `y`, `w` and `h` cut a rectangle out of the file before the grid is applied;
`w` or `h` of 0 means "to that edge". This is how one file carries a body and a
pair of wings at different cell sizes, which is how sheets are actually drawn.

`trimX` and `trimY` pull the sampled box in from the region's **outer** edges, in
pixels. They exist to stop a sample reaching past the boundary, where it wraps
and comes back with the far side of the sheet. They do not touch the divisions
between cells, and they never move or resize the monster — a crop decides what
is read, not where anything stands.

## A sheet whose last cell is a defence pose

A grid usually has cells left over. Those are the pose held while the card lies
down: a `defence` layer over the same sheet whose `first` is the leftover cell
and whose `frames` is 1. Two ordinary layers sharing one file, neither knowing
the other is there.

A face-DOWN card never shows a sprite at all. That is not a style choice: a set
card is one nobody may identify, and a monster looming over it would announce
what it is to the room.

## Wings

A `wings` layer is drawn behind the body and mirrored, so a sheet holds one wing
and the other side is the same frames flipped. `anchor`, `spacing` and `scale`
are all fractions of the **body's** height, so a monster scaled down keeps its
wings in proportion and on the same shoulders.

## Size and pace

`scale` is a multiple of the standard height rather than a measurement, because
what wants saying about a monster is how big it is *for a monster* — a hatchling
is half of one — and that stays true if the standard is ever retuned. Height is
measured in **card lengths**, so the same monster is the right size on a display
pedestal and on a duel board that has been resized through the config.

## The player's own copy

`config/dueldimension/monster_sprites.json` is the same format, laid over this
one by passcode. An edit there survives an update, a definition nobody touched
improves with one, and deleting the file puts everything back exactly as it came.
The editor writes there until you press **To mod**, which moves the entry here.

## Credits

`dark_magician.png` — see `../LICENSE.md` and `../EDOPRO_CREDITS.md` for the
terms the duel-field art in this tree ships under.
