package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The card item's face, drawn in the inventory and the hand.
 * <p>
 * <b>Forge origin.</b> This is the reincarnation of {@code CardBakedModel} plus
 * {@code FinalCardBakedModel}. There, a card item was a {@code BakedModel} whose
 * {@code getQuads} produced two textured quads — a card back on the NORTH face
 * and, on the SOUTH face, the card's own front image (or a "blanc" placeholder
 * when the stack carried no card). An {@code ItemOverrides} pulled the {@code
 * ItemStack} in through {@code resolve(...)} so the quads could depend on which
 * card the stack held.
 * <p>
 * <b>26.2 mapping.</b> {@code BakedModel}/{@code IDynamicBakedModel}/{@code
 * ItemOverrides}/{@code UnbakedGeometryHelper} are all gone. The replacement for
 * an item whose geometry depends on stack data is a {@link SpecialModelRenderer}:
 * {@link #extractArgument} plays the role of {@code ItemOverrides.resolve} (it
 * lifts the {@link CardHolder} off the stack), and {@link #submit} plays the role
 * of {@code getQuads} (it draws the two faces). The drawing itself reuses the
 * already-ported {@link FieldQuad}, which is the 26.2 way to put an arbitrary
 * textured quad into the render pass.
 * <p>
 * The two faces are two quads a hair apart in z with opposite winding, exactly as
 * the Forge NORTH/SOUTH pair were: the back always shows, the front shows over it
 * on the other side. A card with no {@code Properties} falls back to the card
 * back on both sides (the Forge "blanc"/single-back branch).
 */
public class CardSpecialRenderer implements SpecialModelRenderer<CardHolder>
{
    /**
     * Half the card quad's side, in world units. The Forge model baked a unit
     * item quad; here the card spans roughly -0.5..0.5 in x and y, centred at the
     * origin, matching the default item transform's expectation of a unit square.
     */
    static final float HALF = 0.5F;

    /**
     * The two faces sit a sliver either side of z = 0 so the back never z-fights
     * the front. Forge separated them by nudging each element's z by {@code off *
     * 0.1}; this is the same idea at a fixed small offset.
     */
    static final float Z = 0.01F;

    @Override
    public void submit(CardHolder card, PoseStack pose, SubmitNodeCollector collector,
        int light, int overlay, boolean glint, int outlineColor)
    {
        ResourceLocation back = CardRenderUtil.getMainCardBack();

        // The front: the card's own image if it holds one, otherwise the back
        // again (the Forge "blanc"/single-back fallback — a stack with no card
        // still needs two drawable faces).
        ResourceLocation front = (card != null && card.getCard() != null)
            ? CardPresentation.itemImage(card)
            : back;

        drawTwoFaces(pose, collector, front, back);
    }

    /**
     * A card back on one side and a front on the other, centred at the origin.
     * <p>
     * The front faces +z (the Forge SOUTH face) at z = +{@link #Z}; the back
     * faces -z (the Forge NORTH face) at z = -{@link #Z}, with its corners walked
     * the other way round so the winding points it away from the front. {@link
     * FieldQuad.Corners} lists corners top-left, top-right, bottom-right,
     * bottom-left; {@link FieldQuad#draw} reorders them for the anticlockwise
     * winding it wants.
     */
    static void drawTwoFaces(PoseStack pose, SubmitNodeCollector collector,
        ResourceLocation front, ResourceLocation back)
    {
        // Front face (+z), corners TL, TR, BR, BL in screen space.
        pose.pushPose();
        pose.translate(0F, 0F, Z);
        FieldQuad.draw(pose, collector, front,
            new FieldQuad.Corners(-HALF, HALF, HALF, HALF, HALF, -HALF, -HALF, -HALF),
            0F, 0F, 1F, 1F, -1);
        pose.popPose();

        // Back face (-z), corners mirrored in x so the back reads the right way
        // round when seen from behind and its winding is reversed.
        pose.pushPose();
        pose.translate(0F, 0F, -Z);
        FieldQuad.draw(pose, collector, back,
            new FieldQuad.Corners(HALF, HALF, -HALF, HALF, -HALF, -HALF, HALF, -HALF),
            0F, 0F, 1F, 1F, -1);
        pose.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer)
    {
        // The four corners of the card quad, at both z offsets, so lighting and
        // culling see the real bounds of what submit draws.
        consumer.accept(new Vector3f(-HALF, -HALF, -Z));
        consumer.accept(new Vector3f(HALF, -HALF, -Z));
        consumer.accept(new Vector3f(HALF, HALF, Z));
        consumer.accept(new Vector3f(-HALF, HALF, Z));
    }

    @Override
    public CardHolder extractArgument(ItemStack stack)
    {
        // The Forge ItemOverrides.resolve equivalent: lift the CardHolder off the
        // stack. May carry a null card; submit handles that with the back
        // fallback.
        return DdItems.CARD.getCardHolder(stack);
    }
}
