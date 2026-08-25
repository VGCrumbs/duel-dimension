package de.cas_ual_ty.dueldimension.duel.overworld.display;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.CardSpace;
import net.minecraft.world.phys.Vec3;

/**
 * One card lying flat on top of a block.
 * <p>
 * The smallest possible {@link CardSpace}: a point, a size, and the same mirror
 * the duel field applies. That mirror is not decoration -- {@code FieldLayout}
 * puts controller 0's rows at positive y while the world's z runs the other
 * way, so a space that skipped it would draw every card's face downwards and
 * every edge inside out.
 * <p>
 * No rotation. The block is a plain cube with no facing, so the card lies
 * north-up on every one of them; a builder who wants it turned turns the
 * pedestal it stands on. If a facing is ever added to the block, it belongs
 * here as one more term and nowhere else.
 */
public record PedestalSpace(Vec3 origin, float scale) implements CardSpace
{
    /**
     * How much of a block the card takes up, along its long side.
     * <p>
     * A card is one field unit long, so this IS the scale -- three quarters of
     * a block leaves a margin of an eighth at each end, which reads as a card
     * placed on a pedestal rather than one wedged onto it.
     */
    public static final float CARD_ON_BLOCK = 0.75F;

    /** The zone a lone card sits in: itself, centred on the origin. */
    public static FieldLayout.Rect lone()
    {
        return new FieldLayout.Rect(-0.5F, -0.5F, 1F, 1F);
    }

    @Override
    public Vec3 at(float fx, float fy, double lift)
    {
        // Field y is mirrored into world z, exactly as FieldTransform mirrors
        // it. Everything downstream -- the winding, the normals, the UV turns --
        // was written against a space that does this.
        return origin.add(fx * scale, lift, -fy * scale);
    }

    @Override
    public float scale()
    {
        return scale;
    }
}
