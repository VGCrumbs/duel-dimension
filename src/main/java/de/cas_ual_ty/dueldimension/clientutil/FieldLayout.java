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
     * A pinhole camera looking down at the table, which is what makes the
     * board look tilted.
     * <p>
     * The previous attempt scaled horizontal and vertical distances by
     * different laws, so cards stretched and sheared instead of simply
     * receding. Here a single depth divisor drives both axes, exactly as a
     * real camera does: a point at table depth {@code d} from the lens
     * projects to {@code f/d} times its size, so a card keeps its shape and
     * rows bunch towards the horizon on their own.
     *
     * @param centreX    screen x of the table's centre line
     * @param horizonY   screen y the table converges to at infinite depth
     * @param focal      focal length in pixels
     * @param cameraDist distance from lens to the table's near edge, field units
     * @param cameraHigh height of the lens above the table plane, field units
     */
    public record Projection(float centreX, float horizonY, float focal, float cameraDist, float cameraHigh)
    {
        private static final float FIELD_CENTRE_X = (FIELD_MIN_X + FIELD_MAX_X) / 2F;

        /** Distance from the lens to a row of the table. */
        private float depth(float fieldY)
        {
            return cameraDist + (FIELD_MAX_Y - fieldY);
        }

        public float y(float fieldY)
        {
            return horizonY + cameraHigh * focal / depth(fieldY);
        }

        public float x(float fieldX, float fieldY)
        {
            return centreX + (fieldX - FIELD_CENTRE_X) * focal / depth(fieldY);
        }

        /** On-screen height of one field unit at this depth, for card sizing. */
        public float scaleAt(float fieldY)
        {
            return focal / depth(fieldY);
        }

        /** The four projected corners of a zone rectangle. */
        public FieldQuad.Corners quad(Rect rect)
        {
            float far = rect.y();
            float near = rect.y() + rect.h();
            return new FieldQuad.Corners(
                x(rect.x(), far), y(far),
                x(rect.x() + rect.w(), far), y(far),
                x(rect.x() + rect.w(), near), y(near),
                x(rect.x(), near), y(near));
        }

        /** A free-floating card (a hand), centred on a point of the table. */
        public FieldQuad.Corners cardQuad(float centreFieldX, float fieldY, float width, float height)
        {
            return quad(new Rect(centreFieldX - width / 2F, fieldY - height / 2F, width, height));
        }
    }

    /**
     * Fits the tilted table into a screen box.
     * <p>
     * {@code farNearRatio} is how wide the far edge appears relative to the
     * near edge; it fixes the camera distance, and the remaining parameters
     * follow from making the table exactly fill the box.
     */
    public static Projection fit(int left, int top, int width, int height)
    {
        final float farNearRatio = 0.62F;
        float fieldDepth = FIELD_MAX_Y - FIELD_MIN_Y;
        float fieldWidth = FIELD_MAX_X - FIELD_MIN_X;

        // near width : far width = (dist + depth) : dist
        float cameraDist = farNearRatio * fieldDepth / (1F - farNearRatio);
        // Near edge spans the full box width.
        float focal = cameraDist * width / fieldWidth;
        // Choose the lens height that makes the table exactly as tall as the box.
        float spread = 1F / cameraDist - 1F / (cameraDist + fieldDepth);
        float cameraHigh = height / (focal * spread);
        // Far edge lands on the top of the box.
        float horizonY = top - cameraHigh * focal / (cameraDist + fieldDepth);

        return new Projection(left + width / 2F, horizonY, focal, cameraDist, cameraHigh);
    }
}
