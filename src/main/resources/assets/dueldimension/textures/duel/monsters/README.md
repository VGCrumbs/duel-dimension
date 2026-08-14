# Monster sprites

Billboarded figures that stand on a face-up monster card. Drop a sheet here,
add one line to `MonsterSprites`, and that card has a monster.

## The sheet

- **One row**, frames left to right, every cell the same width.
- **Feet on the bottom edge of every cell.** The billboard stands the sprite on
  its card, so a cell with padding under the feet makes the monster hover. This
  is the one rule the code cannot check for you.
- Transparent background.
- **No `.mcmeta`.** Minecraft's own texture animation would fight this one, and
  the blur it enables bleeds one frame into the next.

Nothing else is fixed. Cell size, frame count and proportions are all measured
from the file the first time it is drawn, so a 512×256 sheet of four frames and
a 900×300 sheet of three both work without saying so anywhere.

## The line

In `clientutil/overworld/MonsterSprites.java`, in the block marked *the list*:

```java
monster(46986414L, "dark_magician", 4, Loop.PING_PONG);
```

The passcode, the file's name without its extension, how many frames, and how
they follow one another.

`Loop.LOOP` runs `0 1 2 3 0 1 2 3`. `Loop.PING_PONG` runs `0 1 2 3 2 1` and
repeats, which is what a sprite drawn as a single sweep needs — the Dark
Magician's four frames are him drifting downwards, so looping them would snap
him back to the top every second while playing them back again is the float they
were drawn to be.

## A monster that lies down differently

Most do not, and a card with one sprite uses it in either battle position. For
one that needs both:

```java
monster(12345678L,
    sheet("some_monster", 4, Loop.PING_PONG),          // face-up attack
    sheet("some_monster_defence", 2, Loop.LOOP));      // face-up defence
```

A face-DOWN card never shows a sprite at all. That is not a style choice: a set
card is one nobody may identify, and a monster looming over it would announce
what it is to the room.

## Size and pace

`sheet(...)` uses the defaults in `MonsterSprites` — `DEFAULT_HEIGHT` card
lengths tall and `DEFAULT_TICKS` per frame. Height is measured in **card
lengths** rather than blocks so a sprite scales with whatever it is standing on:
the same monster is the right size on a display pedestal and on a duel board
that has been resized through the config. Build a `Sheet` by hand if one monster
needs its own height or pace.

## Credits

`dark_magician.png` — see `../LICENSE.md` and `../EDOPRO_CREDITS.md` for the
terms the duel-field art in this tree ships under.
