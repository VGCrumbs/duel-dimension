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

    /** Projects field units onto the screen; y grows upward in field units. */
    public record Projection(int originX, int originY, float scale)
    {
        public int x(float fieldX)
        {
            return originX + Math.round(fieldX * scale);
        }

        /** Field +y is towards the player, which is down the screen. */
        public int y(float fieldY)
        {
            return originY - Math.round(fieldY * scale);
        }

        public int size(float fieldSize)
        {
            return Math.max(1, Math.round(fieldSize * scale));
        }

        /** Screen rectangle of a zone: x, y, width, height. */
        public int[] rect(Rect rect)
        {
            int width = size(rect.w());
            int height = size(rect.h());
            return new int[] {x(rect.x()), y(rect.y() + rect.h()), width, height};
        }
    }

    /** Fits the whole table into the given screen box. */
    public static Projection fit(int left, int top, int width, int height)
    {
        float unitsWide = FIELD_MAX_X - FIELD_MIN_X;
        float unitsTall = FIELD_MAX_Y - FIELD_MIN_Y;
        float scale = Math.min(width / unitsWide, height / unitsTall);
        int usedWidth = Math.round(unitsWide * scale);
        int usedHeight = Math.round(unitsTall * scale);
        int originX = left + (width - usedWidth) / 2 - Math.round(FIELD_MIN_X * scale);
        int originY = top + (height - usedHeight) / 2 + Math.round(FIELD_MAX_Y * scale);
        return new Projection(originX, originY, scale);
    }
}
