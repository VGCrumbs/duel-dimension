package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.CardFaces;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One card lying on top of a block, drawn on its own.
 * <p>
 * Deliberately standalone. The duel board's card renderer takes a mapping from
 * EDOPro's field units into the world, and everything about that mapping exists
 * because a card on a board is one of forty things laid out against a mat --
 * the mirrored y axis, the zone rectangles, the scale that follows the board's
 * configured size. A card on a pedestal has none of those neighbours, and
 * borrowing the machinery meant borrowing its whole coordinate system to place
 * a single quad in a single block.
 * <p>
 * So this works in BLOCK-LOCAL coordinates and nothing else: the origin is the
 * block's own lower corner, x runs east, z runs south, y is up. That is the
 * space the level renderer has already put the pose in before a block entity
 * renderer is called, so the numbers below are the numbers that reach the
 * screen -- there is no second frame of reference for them to be measured
 * against by mistake.
 */
public final class DisplayCard
{
    private DisplayCard()
    {
    }

    /** The card's long side, in blocks: a margin of an eighth at each end. */
    public static final float LENGTH = 0.75F;
    /** And its short side, at a real card's proportions. */
    public static final float WIDTH = LENGTH * 0.7F;
    /** Thick enough to have an edge, thin enough to still be a card. */
    public static final float THICKNESS = 0.012F;

    /**
     * Draws the card on the block's upper face.
     *
     * @param defence lying sideways, as a defending monster does
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int code,
        byte art, boolean defence, boolean faceDown, int tint)
    {
        ResourceLocation face = CardFaces.face(slotOf(code, art, defence, faceDown), false, 0);
        ResourceLocation back = CardFaces.underside(slotOf(code, art, defence, faceDown), 0);

        // Turned a quarter when it lies sideways: the card's own footprint
        // swaps, and its artwork turns with it.
        float acrossX = defence ? LENGTH : WIDTH;
        float acrossZ = defence ? WIDTH : LENGTH;

        float x0 = 0.5F - acrossX / 2F;
        float x1 = 0.5F + acrossX / 2F;
        float z0 = 0.5F - acrossZ / 2F;
        float z1 = 0.5F + acrossZ / 2F;
        // Sitting ON the block, not in it: the top face of a block is y = 1.
        float y0 = 1F;
        float y1 = 1F + THICKNESS;

        // The face, upwards. Wound so that (c1 - c0) x (c3 - c0) points up,
        // which is the winding WorldQuad reads a normal out of.
        boolean whole = CardFaces.isCardShaped(face);
        float[][] top = CardRenderer.turned(false, whole ? 0F : DuelTextures.CARD_U0,
            whole ? 0F : DuelTextures.CARD_V0, whole ? 1F : DuelTextures.CARD_U1,
            whole ? 1F : DuelTextures.CARD_V1, defence ? 1 : 0);
        quad(poseStack, collector, face, tint, top,
            new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0));

        // And the underside, wound the other way so it looks down. Its own
        // texture coordinates, mirrored, because a picture that reads correctly
        // from above reads backwards from beneath.
        boolean wholeBack = CardFaces.isCardShaped(back);
        float[][] under = CardRenderer.turned(true, wholeBack ? 0F : DuelTextures.CARD_U0,
            wholeBack ? 0F : DuelTextures.CARD_V0, wholeBack ? 1F : DuelTextures.CARD_U1,
            wholeBack ? 1F : DuelTextures.CARD_V1, defence ? 1 : 0);
        quad(poseStack, collector, back, tint, under,
            new Vec3(x0, y0, z1), new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1));

        // The four edges, in the white-over-grey strip a card's edge is drawn
        // with. Per-corner coordinates, because the default mapping runs v
        // along a quad's first edge -- which here is the card's length rather
        // than its thickness, and the strip comes out lying on its side.
        float[] edgeU = {0F, 1F, 1F, 0F};
        float[] edgeV = {0F, 0F, 1F, 1F};
        float[][] edge = {edgeU, edgeV};
        ResourceLocation side = DuelTextures.STACK_SIDE;
        quad(poseStack, collector, side, tint, edge,
            new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), new Vec3(x0, y0, z0));
        quad(poseStack, collector, side, tint, edge,
            new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), new Vec3(x0, y0, z1), new Vec3(x1, y0, z1));
        quad(poseStack, collector, side, tint, edge,
            new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), new Vec3(x0, y0, z0), new Vec3(x0, y0, z1));
        quad(poseStack, collector, side, tint, edge,
            new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), new Vec3(x1, y0, z0));
    }

    /**
     * The height the card's upper face ends up at, for anything that has to
     * stand on it.
     */
    public static double surface()
    {
        return 1D + THICKNESS;
    }

    /**
     * A board slot standing in for the card, so which face shows is decided by
     * the same code that decides it in a duel -- including the rule that
     * matters here, that a face-down card shows its back.
     */
    private static de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot slotOf(int code,
        byte art, boolean defence, boolean faceDown)
    {
        return new de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot(true, code, faceDown,
            defence, 0, 0, 0, 0, 0, 0, 0, null, false, art);
    }

    /**
     * One quad, in block-local coordinates.
     * <p>
     * Zero is passed as the origin because the corners ARE already relative to
     * the block, and the pose the level renderer set up carries the rest of the
     * way to the eye. Nothing here needs to know where the camera is.
     */
    private static void quad(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, int tint, float[][] uv, Vec3 a, Vec3 b, Vec3 c, Vec3 d)
    {
        WorldQuad.submit(poseStack, collector, WorldQuad.Kind.SOLID, texture, Vec3.ZERO,
            new Vec3[] {a, b, c, d}, tint, uv[0], uv[1]);
    }
}
