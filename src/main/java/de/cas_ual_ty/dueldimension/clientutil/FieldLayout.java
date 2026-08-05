package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;

/**
 * The duel field's spatial arrangement, ported from EDOPro's vertex table in
 * {@code gframe/materials.cpp} (vFieldMzone / vFieldSzone / vFieldDeck /
 * vFieldGrave / vFieldExtra / vFieldRemove).
 * <p>
 * Coordinates are kept in EDOPro's own field units so the arrangement is
 * verifiably the same — zone sizes, the 1.1-unit column pitch, the side
 * columns, and the two extra monster zones straddling the centre line. Only
 * the final projection to screen pixels is ours, since EDOPro renders this
 * table in 3D perspective and a Minecraft GUI is 2D.
 * <p>
 * Player 0 (you) uses the table verbatim. Player 1 is the point reflection
 * {@code (x, y) -> (MIRROR_X - x, -y)}, which is exactly how materials.cpp
 * lists the opponent-side copies (e.g. vFieldDeck[0][1] = 1.0,-2.7,0.2,-3.9
 * against self 6.9,2.7,7.7,3.9).
 */
public final class FieldLayout
{
    /** A zone rectangle in EDOPro field units. */
    public record Rect(float x, float y, float w, float h)
    {
    }

    // materials.cpp: cells are 1.1 wide and 1.2 tall, columns pitch 1.1.
    private static final float CELL_W = 1.1F;
    private static final float CELL_H = 1.2F;
    private static final float COLUMN_PITCH = 1.1F;
    private static final float FIRST_COLUMN_X = 1.2F;

    private static final float MZONE_Y = 0.8F;
    private static final float SZONE_Y = 2.0F;
    private static final float SIDE_LOW_Y = 0.1F;   // field spell, grave, banished
    private static final float SIDE_HIGH_Y = 2.7F;  // extra deck, main deck
    private static final float EMZ_Y = -0.6F;

    private static final float EXTRA_X = 0.2F;
    private static final float FIELD_SPELL_X = 0.2F;
    private static final float DECK_X = 6.9F;
    private static final float GRAVE_X = 6.9F;
    private static final float REMOVED_X = 7.9F;
    private static final float EMZ_LEFT_X = 2.3F;
    private static final float EMZ_RIGHT_X = 4.5F;

    /** Reflection axis for the opponent's side, from the mirrored table entries. */
    private static final float MIRROR_X = 7.9F;

    /** Extent of the whole table, used to fit it on screen. */
    public static final float FIELD_MIN_X = -0.8F;
    public static final float FIELD_MAX_X = 8.7F;
    public static final float FIELD_MIN_Y = -3.9F;
    public static final float FIELD_MAX_Y = 3.9F;

    public static final float CARD_ASPECT = CELL_W / CELL_H;

    private FieldLayout()
    {
    }

    /**
     * @param controller 0 = you, 1 = opponent
     * @return the zone's rectangle in field units, or null if there isn't one
     */
    public static Rect zone(int controller, int location, int sequence)
    {
        Rect self = selfZone(location, sequence);
        if(self == null)
        {
            return null;
        }
        if(controller == 0)
        {
            return self;
        }
        // Point reflection through the table centre.
        return new Rect(MIRROR_X - self.x() - self.w(), -self.y() - self.h(), self.w(), self.h());
    }

    private static Rect selfZone(int location, int sequence)
    {
        switch(location)
        {
            case OcgConstants.LOCATION_MZONE:
            {
                if(sequence < 5)
                {
                    return new Rect(FIRST_COLUMN_X + sequence * COLUMN_PITCH, MZONE_Y, CELL_W, CELL_H);
                }
                // vFieldMzone[0][5] and [0][6]: the extra monster zones.
                return new Rect(sequence == 5 ? EMZ_LEFT_X : EMZ_RIGHT_X, EMZ_Y, CELL_W, CELL_H);
            }
            case OcgConstants.LOCATION_SZONE:
            {
                if(sequence < 5)
                {
                    return new Rect(FIRST_COLUMN_X + sequence * COLUMN_PITCH, SZONE_Y, CELL_W, CELL_H);
                }
                if(sequence == 5)
                {
                    return new Rect(FIELD_SPELL_X, SIDE_LOW_Y, 0.8F, CELL_H); // field spell
                }
                // Pendulum zones sit at the ends of the spell row.
                return new Rect(FIRST_COLUMN_X + (sequence == 6 ? -COLUMN_PITCH : 5 * COLUMN_PITCH),
                    SZONE_Y, CELL_W, CELL_H);
            }
            case OcgConstants.LOCATION_DECK:
                return new Rect(DECK_X, SIDE_HIGH_Y, 0.8F, CELL_H);
            case OcgConstants.LOCATION_EXTRA:
                return new Rect(EXTRA_X, SIDE_HIGH_Y, 0.8F, CELL_H);
            case OcgConstants.LOCATION_GRAVE:
                return new Rect(GRAVE_X, SIDE_LOW_Y, 0.8F, CELL_H);
            case OcgConstants.LOCATION_REMOVED:
                return new Rect(REMOVED_X, SIDE_LOW_Y, 0.8F, CELL_H);
            default:
                return null;
        }
    }

    /**
     * Projects the field onto the screen as a trapezoid, so the table appears
     * tilted away from the viewer as it does in the reference client's 3D
     * scene. The far (opponent) edge is narrower than the near (your) edge,
     * and equal steps in field depth compress towards the far edge.
     *
     * @param farHalfWidth  half-width of the table's far edge, in pixels
     * @param nearHalfWidth half-width of the near edge
     */
    public record Projection(float centreX, float topY, float bottomY, float farHalfWidth, float nearHalfWidth)
    {
        /** Depth fraction: 0 at the opponent's edge, 1 at yours. */
        private float depth(float fieldY)
        {
            return (fieldY - FIELD_MIN_Y) / (FIELD_MAX_Y - FIELD_MIN_Y);
        }

        /**
         * Perspective-correct screen fraction for a depth. With k the ratio of
         * far to near width, s(v) = k·v / (1 + (k-1)·v) — the standard
         * projective interpolation across a trapezoid, so rows bunch up
         * towards the horizon instead of being evenly spaced.
         */
        private float screenFraction(float fieldY)
        {
            float v = depth(fieldY);
            float k = farHalfWidth / nearHalfWidth;
            return k * v / (1F + (k - 1F) * v);
        }

        public float y(float fieldY)
        {
            return topY + screenFraction(fieldY) * (bottomY - topY);
        }

        /** Half-width of the table at this depth; edges of the trapezoid are straight. */
        public float halfWidth(float fieldY)
        {
            float s = screenFraction(fieldY);
            return farHalfWidth + (nearHalfWidth - farHalfWidth) * s;
        }

        public float x(float fieldX, float fieldY)
        {
            float centreFieldX = (FIELD_MIN_X + FIELD_MAX_X) / 2F;
            float halfFieldWidth = (FIELD_MAX_X - FIELD_MIN_X) / 2F;
            return centreX + ((fieldX - centreFieldX) / halfFieldWidth) * halfWidth(fieldY);
        }

        /** The four projected corners of a zone rectangle. */
        public FieldQuad.Corners quad(Rect rect)
        {
            float farY = rect.y();
            float nearY = rect.y() + rect.h();
            return new FieldQuad.Corners(
                x(rect.x(), farY), y(farY),
                x(rect.x() + rect.w(), farY), y(farY),
                x(rect.x() + rect.w(), nearY), y(nearY),
                x(rect.x(), nearY), y(nearY));
        }

        /** A free-floating card (hand), sized as if it sat at the given depth. */
        public FieldQuad.Corners cardQuad(float centreFieldX, float fieldY, float width, float height)
        {
            return quad(new Rect(centreFieldX - width / 2F, fieldY - height / 2F, width, height));
        }
    }

    /** Fits the tilted table into the given screen box. */
    public static Projection fit(int left, int top, int width, int height)
    {
        // Reserve room at the bottom edge for the near hand row.
        float nearHalf = width / 2F * 0.98F;
        float farHalf = nearHalf * 0.58F;
        return new Projection(left + width / 2F, top, top + height, farHalf, nearHalf);
    }
}
