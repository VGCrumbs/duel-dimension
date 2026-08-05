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

    /**
     * The mat quad from materials.cpp: matManager.vField spans x -1..9 and
     * y -4..4 as ONE quad covering both halves, with u = (x+1)/10 and
     * v = (y+4)/8. Zone rectangles live inside it.
     */
    public static final float FIELD_MIN_X = -1.0F;
    public static final float FIELD_MAX_X = 9.0F;
    public static final float FIELD_MIN_Y = -4.0F;
    public static final float FIELD_MAX_Y = 4.0F;

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
     * EDOPro's own duel camera, ported from {@code gframe/game.h} and
     * {@code gframe/game.cpp}.
     * <p>
     * The reference client puts the table on the world XY plane (Z is the
     * table's normal) and views it with one camera:
     * <pre>
     * FIELD_X = 4.2, FIELD_Y = 8.0, FIELD_Z = 7.8          (game.h:831-834)
     * eye    = (FIELD_X, FIELD_Y, FIELD_Z) = (4.2, 8, 7.8)
     * target = (FIELD_X, 0, 0)
     * up     = (0, 0, 1)                                    (game.cpp:1942-1954)
     * frustum l=-0.90 r=0.45 b=-0.42 t=0.42 near=1 far=100  (game.h:836-839)
     * </pre>
     * The frustum is deliberately asymmetric horizontally: the projection
     * matrix element M[8] = (l+r)/(l-r) = 1/3 shifts the whole table a sixth
     * of the screen to the right, which is what leaves room for the card-info
     * column on the left. We keep that, so our sidebar sits where EDOPro's
     * does for the same reason.
     * <p>
     * Because the view matrix works out to a pure function of the table's y
     * coordinate, the whole projection reduces to the closed forms below —
     * no matrices needed at draw time.
     */
    public record Projection(float originX, float originY, float scaleX, float scaleY)
    {
        // gframe/game.h:831-834
        private static final float FIELD_X = 4.2F;
        private static final float FIELD_Y = 8.0F;
        private static final float FIELD_Z = 7.8F;
        // gframe/game.h:836-839
        private static final float CAMERA_LEFT = -0.90F;
        private static final float CAMERA_RIGHT = 0.45F;
        private static final float CAMERA_BOTTOM = -0.42F;
        private static final float CAMERA_TOP = 0.42F;
        private static final float NEAR = 1.0F;

        /** Eye-to-target distance; the view axes fall out of it. */
        private static final float LENS = (float)Math.sqrt(FIELD_Y * FIELD_Y + FIELD_Z * FIELD_Z);

        // buildProjectionMatrixPerspectiveLH(width, height, near, far)
        private static final float M0 = 2F * NEAR / ((CAMERA_RIGHT - CAMERA_LEFT));
        private static final float M5 = 2F * NEAR / (CAMERA_TOP - CAMERA_BOTTOM);
        /** The off-centre shift: (l + r) / (l - r) = +1/3. */
        private static final float M8 = (CAMERA_LEFT + CAMERA_RIGHT) / (CAMERA_LEFT - CAMERA_RIGHT);

        /** Camera-space depth of a point on the table. */
        private static float viewDepth(float fieldY)
        {
            return (FIELD_Y * FIELD_Y + FIELD_Z * FIELD_Z - FIELD_Y * fieldY) / LENS;
        }

        /** Normalised device x in [-1, 1]. */
        private static float ndcX(float fieldX, float fieldY)
        {
            return M0 * (fieldX - FIELD_X) / viewDepth(fieldY) + M8;
        }

        /** Normalised device y in [-1, 1]; +1 is the top of the screen. */
        private static float ndcY(float fieldY)
        {
            return M5 * (-FIELD_Z * fieldY / LENS) / viewDepth(fieldY);
        }

        public float x(float fieldX, float fieldY)
        {
            return originX + ndcX(fieldX, fieldY) * scaleX;
        }

        public float y(float fieldY)
        {
            return originY - ndcY(fieldY) * scaleY;
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
     * Maps EDOPro's projection onto a screen box the way the reference client
     * does: {@code keep_aspect_ratio} defaults to false, so the fixed frustum
     * is simply stretched to whatever window it has, NDC x and y each spanning
     * the full extent. Scaling both axes by one factor instead (to "preserve
     * shape") makes the table far too tall on a wide window, because the
     * frustum is much wider than it is high.
     */
    public static Projection fit(int left, int top, int width, int height)
    {
        float scaleX = width / 2F;
        float scaleY = height / 2F;
        // The frustum puts the table's centre at NDC +1/3, which in EDOPro
        // leaves room for its card panel. Our sidebar has its own width, so
        // undo that offset and centre the table in the box we were handed.
        float centreNdcX = Projection.ndcX((FIELD_MIN_X + FIELD_MAX_X) / 2F, 0F);
        float centreNdcY = Projection.ndcY(0F);
        return new Projection(left + width / 2F - centreNdcX * scaleX,
            top + height / 2F + centreNdcY * scaleY, scaleX, scaleY);
    }
}
