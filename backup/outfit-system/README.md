# The outfit system, shelved

Duelists could wear an outfit — a body's worth of clothing drawn over their
skin, chosen from a wardrobe in the duel hub, owned per profile and shown to
everybody. It is not being used for the time being, so it is out of the build.

Nothing here is lost. The whole thing, working, is at the tag
**`pre-outfit-removal`**:

    git show pre-outfit-removal --stat
    git checkout pre-outfit-removal -- <path>

The `src/` folder beside this file is a plain copy of the deleted classes, for
reading without checking anything out. It is outside `src/main/java`, so nothing
here is compiled.

## What was deleted outright

| file | what it did |
| --- | --- |
| `duel/outfit/Outfits.java` | the catalogue: which outfits exist, their textures, whether each is slim |
| `duel/outfit/WornOutfits.java` | who is wearing what, client-side, and the broadcast that keeps it true |
| `duel/outfit/OutfitMessages.java` | `Wear` (client asks) and `Worn` (server tells everybody) |
| `clientutil/OutfitLayer.java` | the render layer that drew the clothes |
| `clientutil/OutfitHand.java` | the sleeve on your own arm in first person |
| `clientutil/OutfitCarrier.java` | the interface letting a render state carry an outfit |
| `clientutil/OutfitSkins.java` | which skin and which body a duelist is drawn on — **see below** |
| `clientutil/UnderSkin.java` | the player's own edited skin, drawn under the clothes |
| `clientutil/hub/OutfitPreview.java` | the wardrobe's figures |
| `mixin/client/AvatarRenderStateMixin.java` | gave a render state somewhere to keep an outfit |
| `textures/entity/outfit/` | kaiba.png, yusei.png and their credits |

## What could NOT be restored by copying files back

Three shared files had outfit hooks removed by surgery. Putting the classes above
back is not enough on its own; these are the seams.

**`clientutil/PlayerSkins.java` gained `patch(player, resolved)`.** This is the
one that matters. `OutfitSkins.patch` did *two* jobs behind one name: it applied
the outfit's under-skin and slim body, and it applied the skins this mod supplies
for players who have none — which is what stops every player on an offline
development client being Steve. Deleting the class wholesale would have taken
that with it. The surviving half moved to `PlayerSkins`, where it always
belonged. If outfits come back, the outfit half goes back on top of it rather
than replacing it.

**`mixin/client/AvatarRendererMixin.java` kept its duel disk.** The same
`extractRenderState` hook carried both the outfit and the worn duel disk, and the
disk is still in use. Only the outfit carry and the two `renderRightHand` /
`renderLeftHand` sleeve hooks came out.

**`mixin/client/AbstractClientPlayerMixin.java` stayed.** It is the `getSkin`
patch point, and it is still needed for `PlayerSkins`. Only the call it makes
changed.

## The rest of the seams

Straightforward removals, listed so nothing has to be rediscovered:

- `net/DdNetwork.java` — the `Wear` and `Worn` registrations and both handlers
- `fabric/DuelDimensionFabric.java` — the join announce and the leave broadcast
- `fabric/DuelDimensionFabricClient.java` — the disconnect clear and the `OutfitLayer` registration
- `clientutil/hub/DuelHubScreen.java` — the whole `OUTFIT` tab and its wardrobe, plus the under-skin import/reset controls
- `clientutil/hub/EditorState.java` — `wear(String)`
- `clientutil/SkinLayersCompat.java` — `applyOutfit`, which voxelised an outfit for the 3D-skin-layers mod
- `duel/profile/DuelProfile.java` — the `outfit` field, its accessors and its codec entry
- `dueldimension.mixins.json` — the `AvatarRenderStateMixin` entry

## Save data

`DuelProfile` persisted the choice as an optional `"Outfit"` string. The field is
gone from the codec, and a profile saved with one still loads: an unknown key in
a record codec is ignored, not an error. Nobody's profile breaks, and if outfits
return, whatever each player last wore is still sitting in their save file.
