package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;

/**
 * The outfit a player is being drawn in, carried on their render state.
 * <p>
 * Entity rendering is two phases now. A renderer first <em>extracts</em> a
 * state from the entity — position, pose, skin, everything a frame needs — and
 * afterwards the layers draw from that state alone. A layer never sees the
 * entity, which is the point: extraction happens once and drawing can then be
 * batched, reordered or skipped without anything reaching back into the world.
 * <p>
 * That is a problem for an outfit, because {@code AvatarRenderState} carries a
 * skin but no identity, and which outfit to draw is a fact about <em>who</em>
 * the player is. So the answer is worked out during extraction, when the entity
 * is still in hand, and carried on the state — which is exactly the pattern the
 * refactor intends.
 * <p>
 * Implemented onto {@code AvatarRenderState} by a mixin. An interface rather
 * than a side map keyed by state, because a render state is recycled between
 * frames and a map would have to be invalidated by something that knows when;
 * a field on the object it belongs to cannot go stale.
 */
public interface OutfitCarrier
{
    Outfits.Outfit dueldimension$outfit();

    void dueldimension$setOutfit(Outfits.Outfit outfit);
}
