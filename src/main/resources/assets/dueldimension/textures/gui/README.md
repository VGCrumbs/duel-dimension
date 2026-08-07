# GUI textures

Every UI element in the Duel Hub and Deck Editor is a PNG. Nothing is drawn as
a flat filled rectangle and nothing uses a vanilla widget texture, so the whole
interface can be reskinned by replacing files here without touching code. The
only thing still drawn by the game is text.

## Layout

| Folder | Holds |
| --- | --- |
| `common/` | Shared furniture: buttons, frames, scrollbars, search fields, tooltips. Anything used by more than one screen. |
| `hub/` | The Duel Hub (`Y`): backdrop, tab strip, section headers, profile panel. |
| `deckeditor/` | The deck editor: deck/trunk panels, card slots, part headers, filter and sort bars. |
| `settings/` | Settings widgets: the mat colour picker gradient, sliders, toggles. |
| `action_icons/` | Pre-existing. Per-action symbols used by the duel command menu. |
| `action_animations/` | Pre-existing. Frame strips for action feedback. |

Loose files at this level (`card_binder.png`, `deck_box.png`, …) belong to the
base mod's own container screens and are left where they are so existing
screens keep working.

Duel-field art stays in `../duel/` — it is drawn in a perspective projection
against the board rather than as flat GUI, so it does not share these
conventions.

## Conventions

* **Nine-slice panels** are cut with a 4px border unless the file says
  otherwise, so a panel can be any size without stretching its corners.
* **Atlases** put states in rows, in the order idle / hovered / disabled, so a
  widget indexes its row by state rather than needing three files.
* **Tinting** is done by drawing a white or greyscale source and multiplying by
  a colour at draw time. The mat colour picker relies on this: one greyscale mat
  texture serves every colour a player chooses.
* Sizes are powers of two where practical, and every file is documented in the
  generator that produced it under `build/`.
