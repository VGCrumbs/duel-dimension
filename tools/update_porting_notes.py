"""Records this session's API findings and decisions in PORTING.md."""
import io

PATH = "PORTING.md"

DRIFT = """
### Found while porting the outfit system and the card pages

| Forge / older | 26.2 | why it matters |
| --- | --- | --- |
| `net.minecraft.Util` | `net.minecraft.util.Util` | Moved out of the root package. The simple name is unchanged, so only a fully qualified use breaks — and it breaks at compile time, so it is safe. In `port_rewrite.py`'s `RENAMES`. |
| `new ClientAsset.ResourceTexture(path)` | `new ClientAsset.ResourceTexture(assetId, path)` | **The one-argument form takes an ASSET ID and derives the file: `ns:foo` becomes `ns:textures/foo.png`.** Handing it a path that is already complete wraps it twice and the result does not exist. This is silent — no warning, no exception, just a player rendered in missing-texture magenta. `OutfitSkins.asset` works the id back out of the path so the pair stays consistent. |
| `CreativeModeTab.builder(Row, column)` | `FabricCreativeModeTab.builder()` | Vanilla's own fourteen tabs fill both rows of the strip; there is no free slot to ask for, and a mod tab given one lands **on top of** a vanilla tab. Fabric's builder puts mod tabs on pages of their own. The module is `fabric-creative-tab-api-v1` — `FabricItemGroup` from `fabric-item-group-api-v1` is the old name and is not what 26.2 ships. |
| `RenderLayer.render(PoseStack, MultiBufferSource, int, T entity, ...)` | `RenderLayer.submit(PoseStack, SubmitNodeCollector, int, S state, float, float)` | A layer is given a render **state**, never the entity. Anything it needs about *who* the entity is has to be put on the state during extraction. |
| `PlayerRenderer` / `PlayerModel<AbstractClientPlayer>` | `AvatarRenderer` / `net.minecraft.client.model.player.PlayerModel` | `PlayerModel` is no longer generic and lives in `.model.player`. `AvatarRenderState` carries a `PlayerSkin` and the cape fields but **no identity**. |
| `model.setupAnim(...)` before drawing | `collector.submitModel(model, state, ...)` | The pose is applied at draw time from the state that was submitted alongside the model, which is why vanilla's own layers never call `setupAnim`. Layer models can therefore be shared across every entity on screen. |
| `RenderType.entityCutoutNoCull` | `RenderTypes.entityCutout` | The names swapped round: `entityCutout` is the no-cull one and `entityCutoutCull` is the culled one. Getting this backwards makes a jacket invisible from the inside. |
| `ItemStackHandler` (Forge capability) | `net.minecraft.world.Container` | See the container decision below. |
| `handler.serializeNBT()` | `serializeNBT(HolderLookup.Provider)` | An `ItemStack` is written through its codec now, and that codec resolves the item against the registry. Saving a slot is no longer something an object can do alone. |
| `Screen.renderBackground(pose)` | `Screen.extractBackground(graphics, mouseX, mouseY, partialTick)` | |
| `screen.renderTooltip(pose, text, x, y)` | `graphics.setTooltipForNextFrame(font, text, x, y)` | A tooltip belongs to the frame, not to the widget, so a **screen** sets it — and it has to be set after the widgets are extracted or something described later covers it. |
| `Screen.renderables` | `Screen.children()` | `renderables` is private now. |
| `minecraft.setScreen` | `minecraft.setScreenAndShow` | |
"""

OUTFIT = """
## The outfit system — how it shrank

Forge drew a duelist in an outfit with **four baked models, a hidden vanilla
body and a layer that redrew the player from scratch**. The reason was not
ambition: the body underneath had to be the shape the clothes were cut for (a
classic arm is a pixel wider at each side than an Alex sleeve and stuck out from
under it), and on that loader there was no way to say so — a skin could not be
changed without a mixin, and the model type came with the skin.

Here it is **one patch to `getSkin`**, because of one fact worth writing down:

> `EntityRenderDispatcher` picks the player renderer — and therefore the classic
> or slim body — from `getSkin().model()`.

Answer that question correctly and the arms are the right width before anything
is drawn. There is nothing to hide and nothing to redraw, and `OutfitLayer` is
then only the outfit. Two passes became one; four models became two.

The pieces:

- **`OutfitSkins`** — the client-side authority. Which outfit a player is drawn
  in, and what to change about the skin the game resolved: the under-skin edit
  if they have one and are wearing something, otherwise a skin this mod supplies,
  and the slim body if a slim outfit demands it. It takes the game's answer as a
  parameter rather than asking for it, because it runs from inside `getSkin`.
- **`OutfitCarrier` + `AvatarRenderStateMixin`** — a field on the render state
  for the outfit, because a layer never sees the entity. An interface on the
  state rather than a side map keyed by state: a render state is recycled
  between frames and a map would need invalidating by something that knows when.
- **`AvatarRendererMixin`** — fills that field during extraction, the one moment
  where the renderer has both the entity and the state, and puts the outfit's
  sleeve on the first-person hand (`renderRightHand` runs no layers at all).
- **`OutfitLayer`** — the outfit, inflated a quarter pixel so it does not fight
  the skin for pixels.
- **`OutfitPreview`** — the wardrobe's figures. Extracts a **real** render state
  from the **real** renderer and hands it to `graphics.entity(...)`, with
  `OutfitSkins.previewing` set for the duration so the answer is the outfit being
  previewed rather than the one being worn. The alternative — driving bare models
  into the GUI, as Forge did — is not possible in a retained-mode GUI and would
  have been a second code path drifting away from the first.
"""

DECISIONS = """- **Forge's `IItemHandler` becomes vanilla's `Container`, not a Fabric
  equivalent.** Fabric has no capabilities, and `fabric-transfer-api-v1` is a
  different abstraction aimed at pipes rather than at menus. Vanilla's
  `Container` is what a `Slot` already works with, with no adapter, which is
  what `SlotItemHandler` existed to provide. `YDMItemHandler` therefore extends
  `SimpleContainer` and keeps the handler's method names (`getSlots`,
  `getStackInSlot`, `insertItem`, ...) so the thirty-odd call sites across the
  binders, deck boxes and sets port without being renamed.
- **`CardRenderUtil` is across in halves.** Everything the card pages need is
  ported. The duel field's half is not: the rotated blits have no quad-transform
  in this API, and the rarity foils were composited by masking one texture
  against another with a colour mask that no longer exists. Both are decisions
  that should be made while looking at the field, and the field is not ported.
- **Creative tab names were never renamed from the upstream mod.** `itemGroup.ydm`
  = "YDM: Ygo Dueling Mod" and "YDM Cards"/"YDM Sets" were still in `en_us.json`
  on **both** trees, and the Forge main tab had no key at all — it was keyed on
  `itemGroup.dueldimension` and the lang file only had `itemGroup.ydm`. Fixed on
  both, so the trees do not drift.
"""

src = io.open(PATH, encoding="utf-8").read()

anchor = "## What is across (phase 0 — done)"
assert anchor in src
src = src.replace(anchor, DRIFT.strip() + "\n\n" + anchor)

anchor = "## Menus with extra data — resolved"
assert anchor in src
src = src.replace(anchor, OUTFIT.strip() + "\n\n" + anchor)

anchor = "- **One repo, `fabric` branch**"
assert anchor in src
src = src.replace(anchor, DECISIONS.strip() + "\n" + anchor)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("PORTING.md updated")
