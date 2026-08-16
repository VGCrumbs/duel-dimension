package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.clientutil.PlayMats;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel board's surface, as a list of textured rectangles in field units.
 * <p>
 * Pure geometry: it knows nothing about the world, the camera, or how a quad is
 * submitted, so it can be listed and checked in a test. {@link FieldTransform}
 * turns each rectangle into four points in space, and
 * {@link OverworldBoardRenderer} submits them.
 * <p>
 * The layout is not re-derived here. Every rectangle comes from
 * {@link FieldLayout}, which is EDOPro's own vertex table and is what the 2D
 * board draws from -- so the world board has the same zones in the same places
 * as the screen it is an alternative to, and a fix to one is a fix to both.
 * Which pieces exist and which texture each takes is copied from what the 2D
 * board actually draws: a playmat band per controller covering their ten card
 * squares, and a plain slot square for each zone the band does not cover.
 */
public final class BoardMesh
{
    private BoardMesh()
    {
    }

    /**
     * One piece of the board.
     *
     * @param rect    where it sits, in field units
     * @param texture what to draw on it
     * @param lift    blocks above the board surface, so pieces that overlap
     *                have a settled order instead of fighting for depth
     */
    public record Piece(FieldLayout.Rect rect, Identifier texture, double lift)
    {
    }

    /**
     * Successive layers of the board, a centimetre apart.
     * <p>
     * A millimetre was not enough. Coplanar quads on a GPU have no defined
     * winner, and a depth buffer far from the near plane cannot tell a
     * millimetre from nothing -- so a lit zone and the square under it took
     * turns being in front, which is the flicker seen while choosing where to
     * place a card. A centimetre is still invisible at the scale a card is
     * drawn and is comfortably above the depth buffer's resolution out at the
     * distance a duellist stands.
     */
    private static final double LAYER = 0.01D;

    /**
     * The highest any piece of the BOARD reaches. Anything standing on the
     * board -- a card, a pile, a glow -- has to start above this, or it is
     * inside the furniture rather than on it.
     */
    public static final double TOP_LAYER = LAYER * 2D;

    /**
     * The zones outside the playmat band: the two extra monster zones, the
     * field spell, and the four piles. Same list the 2D board lays out, in the
     * same order.
     */
    private static final int[][] LOOSE_ZONES = {
        {OcgConstants.LOCATION_MZONE, 5},
        {OcgConstants.LOCATION_MZONE, 6},
        {OcgConstants.LOCATION_SZONE, 5},
        {OcgConstants.LOCATION_DECK, 0},
        {OcgConstants.LOCATION_EXTRA, 0},
        {OcgConstants.LOCATION_GRAVE, 0},
        {OcgConstants.LOCATION_REMOVED, 0},
    };

    /**
     * The whole board, back to front.
     *
     * @param mats the playmat each controller brought, indexed by controller,
     *             not by seat -- the board is one object both duellists walk
     *             around, so there is no "self" side of it
     */
    public static List<Piece> pieces(PlayMats[] mats)
    {
        List<Piece> pieces = new ArrayList<>();

        // The two playmats first and lowest: everything else sits on top of
        // them, which is also how they are stacked on the 2D board.
        for(int controller = 0; controller <= 1; controller++)
        {
            PlayMats mat = mats[controller] == null ? PlayMats.CLASSIC : mats[controller];
            // The world copy, whose black backing has been cut out: in the
            // world the ground is already the table.
            pieces.add(new Piece(FieldLayout.zoneBand(controller), mat.worldTexture(), 0D));
        }

        for(int controller = 0; controller <= 1; controller++)
        {
            for(int[] zone : LOOSE_ZONES)
            {
                FieldLayout.Rect rect = FieldLayout.zone(controller, zone[0], zone[1]);
                if(rect == null)
                {
                    continue;
                }
                // The field spell zone has its own square, marked with a
                // compass rose; the rest share the plain one.
                Identifier texture = zone[0] == OcgConstants.LOCATION_SZONE
                    ? DuelTextures.FIELD_SPELL : DuelTextures.SLOT;
                pieces.add(new Piece(rect, texture, LAYER));
            }
        }
        return pieces;
    }

    /**
     * The zone squares that light up: the ones the engine is currently offering
     * as a choice. Drawn over the board and under the cards, exactly as the 2D
     * board draws them, and from the same set of highlighted zones.
     */
    public static Piece highlight(int controller, int location, int sequence)
    {
        return highlight(FieldLayout.zone(controller, location, sequence));
    }

    /**
     * The same square, for a caller that already has the rectangle.
     * <p>
     * A placement prompt names its zones as packed references, which the board
     * already knows how to turn into rectangles -- unpacking them a second time
     * into a controller and a sequence just to look the rectangle up again is
     * two decoders of one packing.
     */
    public static Piece highlight(FieldLayout.Rect rect)
    {
        return rect == null ? null : new Piece(rect, DuelTextures.SLOT_ACTIVE, TOP_LAYER);
    }
}
