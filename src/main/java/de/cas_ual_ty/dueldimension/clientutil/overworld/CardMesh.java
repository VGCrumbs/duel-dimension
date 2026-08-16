package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A card as a real object: a front, a back, and four edges with thickness
 * between them.
 * <p>
 * <b>Why a solid and not two sprites.</b> The requirement is that a face-down
 * card stays visible when somebody crouches to look under it -- which is what
 * you would try in the anime, and so what somebody will try here. The tempting
 * fix is to turn backface culling off and draw one quad. This builds the object
 * instead, which is better on every count: culling stays on and stays correct,
 * the card has a visible edge when seen from the side, and a card lying on the
 * mat casts the silhouette of a card rather than of a decal.
 * <p>
 * It also settles the hidden-information question by construction. The underside
 * of a card is its back, always -- for a face-up card because that is what the
 * underside of a real card is, and for a face-down one because the client was
 * never sent the identity to draw. There is no viewing angle from which the
 * geometry can show something the server withheld, because the geometry has
 * nothing else on it.
 * <p>
 * Pure, in field units, with height measured in field units too: the renderer
 * multiplies by the board's scale, so a card resized with the field keeps its
 * proportions instead of staying a fixed number of blocks thick.
 */
public final class CardMesh
{
    private CardMesh()
    {
    }

    /**
     * A card's own size on the table, from EDOPro's materials.cpp:28
     * {@code SetS3DVertex(vCardFront, -0.35f, -0.5f, 0.35f, 0.5f, ...)} -- 0.7
     * by 1.0 field units, drawn centred in its 1.1 by 1.2 zone. The same two
     * numbers the 2D board uses; filling the zone instead is what once made
     * every card look stretched.
     */
    public static final float CARD_W = FieldLayout.CARD_W;
    public static final float CARD_H = FieldLayout.CARD_H;

    /**
     * How thick a card is, in field units.
     * <p>
     * A real card is about 0.3mm against 86mm of height, which at this scale is
     * far thinner than one pixel and would render as nothing at all -- the two
     * faces would z-fight and the edges would vanish. Thick enough to see and
     * to crouch under, thin enough to still read as a card: this is the one
     * number here that is chosen rather than ported.
     */
    public static final float THICKNESS = 0.015F;

    /** Which part of the card a face is, and therefore what goes on it. */
    public enum Kind
    {
        /** The upward face: art if the card is face up, its back if not. */
        FRONT,
        /** The downward face: always the card's back. */
        BACK,
        /** One of the four sides. */
        EDGE
    }

    /**
     * One face of the card. Coordinates are field units across the board, with
     * {@code height} in field units above the board's surface.
     */
    public record Face(Kind kind, float[] x, float[] y, float[] height)
    {
    }

    /**
     * The rectangle a card occupies in its zone: its own proportions, centred,
     * and turned a quarter if it is lying in defence.
     * <p>
     * The same construction the 2D board uses, so a card is in the same place on
     * both presentations of the same board.
     */
    public static FieldLayout.Rect placement(FieldLayout.Rect zone, boolean defence)
    {
        return FieldLayout.cardIn(zone, defence);
    }

    /**
     * The six faces of a card lying in the given rectangle.
     *
     * @param lift how far the card floats above the board, in field units, so a
     *             card in a pile can sit on the cards below it
     */
    public static List<Face> faces(FieldLayout.Rect rect, float lift)
    {
        return faces(rect, lift, THICKNESS);
    }

    /**
     * The same, at an explicit height -- which is how a pile of forty cards is
     * drawn as one solid forty cards tall instead of as forty solids.
     */
    public static List<Face> faces(FieldLayout.Rect rect, float lift, float thickness)
    {
        float x0 = rect.x();
        float x1 = rect.x() + rect.w();
        float y0 = rect.y();
        float y1 = rect.y() + rect.h();
        float bottom = lift;
        float top = lift + Math.max(thickness, THICKNESS);

        List<Face> faces = new ArrayList<>(6);

        // Front: wound so its normal points up. Same winding as the board's
        // pieces, which is what makes a card's face and the mat under it agree
        // about which way is out.
        faces.add(new Face(Kind.FRONT, new float[] {x0, x0, x1, x1},
            new float[] {y0, y1, y1, y0}, level(top)));
        // Back: the same rectangle wound the other way, so it faces down.
        faces.add(new Face(Kind.BACK, new float[] {x0, x1, x1, x0},
            new float[] {y0, y0, y1, y1}, level(bottom)));

        // The four sides, each wound so its normal points away from the card.
        // Top corners first: each side's in-plane corners are a palindrome, so
        // the height order is what decides the winding, and bottom-first wound
        // every one of them inwards. CardMeshTest measures each face's normal
        // against the direction out of the card, which is how that was caught
        // rather than by walking round a card in game looking for a missing
        // side from one angle.
        faces.add(new Face(Kind.EDGE, new float[] {x0, x1, x1, x0},
            new float[] {y0, y0, y0, y0}, new float[] {top, top, bottom, bottom}));
        faces.add(new Face(Kind.EDGE, new float[] {x1, x0, x0, x1},
            new float[] {y1, y1, y1, y1}, new float[] {top, top, bottom, bottom}));
        faces.add(new Face(Kind.EDGE, new float[] {x0, x0, x0, x0},
            new float[] {y1, y0, y0, y1}, new float[] {top, top, bottom, bottom}));
        faces.add(new Face(Kind.EDGE, new float[] {x1, x1, x1, x1},
            new float[] {y0, y1, y1, y0}, new float[] {top, top, bottom, bottom}));
        return faces;
    }

    private static float[] level(float height)
    {
        return new float[] {height, height, height, height};
    }
}
