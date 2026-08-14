package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.clientutil.CardFaces;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * One card, as a real object standing on the world board.
 * <p>
 * Splits cleanly from {@link CardMesh}, which is the shape, and from
 * {@link CardFaces}, which decides what may be shown on it. This class only
 * turns the one into world space and hands it the other, so there is nowhere
 * here for a rule about hidden information to be got wrong.
 */
public final class CardRenderer
{
    private CardRenderer()
    {
    }

    /**
     * Which way up a card's art is printed, in quarter turns.
     * <p>
     * Straight from the 2D board's {@code turnsFor}: EDOPro's own transform
     * table gives the opponent's cards a half turn ({@code {0,0,PI}}) so they
     * face the opponent, and a defending monster adds a quarter on top of that.
     * The geometry already carries the defence turn, because the card's
     * rectangle is swapped -- so this is the turn the TEXTURE needs, and both
     * come from the same expression to keep them in step.
     */
    public static int turnsFor(int controller, boolean defence)
    {
        return (controller == 1 ? 2 : 0) + (defence ? 1 : 0);
    }

    /**
     * Draws a card in the given zone.
     *
     * @param controller whose half of the board this is, which decides which
     *                   way up the art is printed
     * @param face       what belongs on its upward side: its art, or its back,
     *                   as {@link CardFaces#face} decided
     * @param back       what belongs on its underside -- always a back,
     *                   whichever way the card is turned
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect zone, int controller,
        boolean defence, float lift, Identifier face, Identifier back)
    {
        FieldLayout.Rect rect = CardMesh.placement(zone, defence);
        int turns = turnsFor(controller, defence);

        for(CardMesh.Face part : CardMesh.faces(rect, lift))
        {
            Identifier texture = switch(part.kind())
            {
                case FRONT -> face;
                // The underside of a card is its back. For a face-up card
                // because that is what the underside of a card is; for a
                // face-down one because the client was never told what it is.
                case BACK -> back;
                // The card's edge: the white-over-grey strip the 2D board tiles
                // down the side of a pile so a stack reads as many thin cards.
                case EDGE -> DuelTextures.STACK_SIDE;
            };

            Vec3[] corners = worldCorners(transform, part);

            if(part.kind() == CardMesh.Kind.EDGE)
            {
                WorldQuad.submit(poseStack, collector, texture, camera, corners, 0xFFFFFFFF);
                continue;
            }

            // Card art is letterboxed inside a square canvas; a card back and
            // the unknown-card art are already card-shaped and take the whole
            // texture. The same distinction the 2D board makes, asked through
            // the same predicate so the two cannot drift apart.
            boolean whole = CardFaces.isCardShaped(texture);
            float u0 = whole ? 0F : DuelTextures.CARD_U0;
            float v0 = whole ? 0F : DuelTextures.CARD_V0;
            float u1 = whole ? 1F : DuelTextures.CARD_U1;
            float v1 = whole ? 1F : DuelTextures.CARD_V1;
            float[][] uv = turned(u0, v0, u1, v1, turns);
            WorldQuad.submit(poseStack, collector, texture, camera, corners, 0xFFFFFFFF,
                uv[0], uv[1]);
        }
    }

    /**
     * A pile of cards: one solid as tall as the stack, with its side striped
     * once per card.
     * <p>
     * The stripes are cut by hand rather than by letting the texture repeat,
     * because the wrap mode here is not repeat -- a v of forty stretches the
     * stripe into one long smear. The 2D board found that out first and does
     * the same slicing; this is that fact reused rather than rediscovered.
     *
     * @param count how many cards are in the pile, which is both its height and
     *              the number of stripes down its side
     * @param top   what is showing on the top of the pile
     */
    public static void submitPile(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, FieldLayout.Rect zone, int controller, int count,
        float lift, Identifier top, Identifier back)
    {
        if(count <= 0)
        {
            return;
        }
        FieldLayout.Rect rect = CardMesh.placement(zone, false);
        float height = PileMesh.height(count);
        int turns = turnsFor(controller, false);
        int stripes = PileMesh.stripes(count);

        for(CardMesh.Face part : CardMesh.faces(rect, lift, height))
        {
            if(part.kind() == CardMesh.Kind.EDGE)
            {
                submitStripedEdge(poseStack, collector, transform, camera, part, stripes);
                continue;
            }
            Identifier texture = part.kind() == CardMesh.Kind.FRONT ? top : back;
            Vec3[] corners = worldCorners(transform, part);
            boolean whole = CardFaces.isCardShaped(texture);
            float[][] uv = turned(whole ? 0F : DuelTextures.CARD_U0,
                whole ? 0F : DuelTextures.CARD_V0, whole ? 1F : DuelTextures.CARD_U1,
                whole ? 1F : DuelTextures.CARD_V1, turns);
            WorldQuad.submit(poseStack, collector, texture, camera, corners, 0xFFFFFFFF,
                uv[0], uv[1]);
        }
    }

    /**
     * One side of a pile, cut into a quad per card so the stripe texture reads
     * as many thin cards rather than as one stretched band. The last slice may
     * be partial and samples only that much of the stripe, exactly as the 2D
     * board's does.
     */
    private static void submitStripedEdge(PoseStack poseStack, SubmitNodeCollector collector,
        FieldTransform transform, Vec3 camera, CardMesh.Face part, int stripes)
    {
        // The face's corners are top, top, bottom, bottom: interpolating
        // between the two pairs walks down the side of the pile.
        for(int slice = 0; slice < stripes; slice++)
        {
            float f0 = (float)slice / stripes;
            float f1 = (float)(slice + 1) / stripes;
            Vec3[] corners = new Vec3[4];
            corners[0] = between(transform, part, 0, 3, f0);
            corners[1] = between(transform, part, 1, 2, f0);
            corners[2] = between(transform, part, 1, 2, f1);
            corners[3] = between(transform, part, 0, 3, f1);
            // Per corner, because the default mapping runs v across the
            // quad's FIRST edge -- which here is the card's width, not its
            // height. The stripe is a white row over a grey row meant to lie
            // flat like the cards it stands for, and mapped the default way it
            // came out on its side: one white half and one grey half down the
            // length of the pile instead of bands across it.
            WorldQuad.submit(poseStack, collector, DuelTextures.STACK_SIDE, camera, corners,
                0xFFFFFFFF, new float[] {0F, 1F, 1F, 0F}, new float[] {0F, 0F, 1F, 1F});
        }
    }

    /** A point a fraction of the way from one of the face's corners to another. */
    private static Vec3 between(FieldTransform transform, CardMesh.Face part, int from, int to,
        float fraction)
    {
        float x = part.x()[from] + (part.x()[to] - part.x()[from]) * fraction;
        float y = part.y()[from] + (part.y()[to] - part.y()[from]) * fraction;
        float height = part.height()[from]
            + (part.height()[to] - part.height()[from]) * fraction;
        return transform.at(x, y, height * transform.scale());
    }

    private static Vec3[] worldCorners(FieldTransform transform, CardMesh.Face part)
    {
        Vec3[] corners = new Vec3[4];
        for(int corner = 0; corner < 4; corner++)
        {
            corners[corner] = transform.at(part.x()[corner], part.y()[corner],
                part.height()[corner] * transform.scale());
        }
        return corners;
    }

    /**
     * The four corners' texture coordinates, rotated by whole quarter turns.
     * <p>
     * Rotating which UV lands on which corner rather than rotating the geometry:
     * the card's rectangle is already the right shape on the board, and turning
     * the quad instead would move the card off its zone.
     *
     * @return {us, vs}, in the corner order {@link WorldQuad} expects
     */
    private static float[][] turned(float u0, float v0, float u1, float v1, int turns)
    {
        float[] us = {u0, u0, u1, u1};
        float[] vs = {v0, v1, v1, v0};
        float[] outU = new float[4];
        float[] outV = new float[4];
        for(int corner = 0; corner < 4; corner++)
        {
            int from = Math.floorMod(corner + turns, 4);
            outU[corner] = us[from];
            outV[corner] = vs[from];
        }
        return new float[][] {outU, outV};
    }
}
