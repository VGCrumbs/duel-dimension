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

            Vec3[] corners = new Vec3[4];
            for(int corner = 0; corner < 4; corner++)
            {
                corners[corner] = transform.at(part.x()[corner], part.y()[corner],
                    part.height()[corner] * transform.scale());
            }

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
