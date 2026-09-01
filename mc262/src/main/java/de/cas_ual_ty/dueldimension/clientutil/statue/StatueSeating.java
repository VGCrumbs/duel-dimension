package de.cas_ual_ty.dueldimension.clientutil.statue;

import java.util.HashMap;
import java.util.Map;

/**
 * Stands a statue on its crown's platform.
 * <p>
 * Derived from the geometry rather than written down as a constant, because the
 * offset is not in any file: {@code menu_m_10} has no {@code igTransform}, and
 * neither it nor a statue records where one sits on the other. What IS knowable
 * is the shape of both, so the placement is measured from them -- the statue's
 * lowest point meets the platform's top face, centred on the platform.
 * <p>
 * Cached per pair; these are static props and the answer never changes.
 */
public final class StatueSeating
{
    /**
     * The crown's platform is narrower than the crown's widest ring, which is the
     * spike crown around it. Measuring the "top" over the whole footprint would
     * find a spike tip and stand the statue on that instead of on the floor it is
     * meant to be on, so the search is restricted to the middle of the platform.
     */
    private static final float PLATFORM_FRACTION = 0.75F;

    private static final Map<StatueMesh, float[]> CACHE = new HashMap<>();

    private StatueSeating()
    {
    }

    /**
     * @return {@code {offsetX, offsetY, offsetZ}} to apply to the statue, in the
     *         podium's local space
     */
    public static float[] seat(StatueMesh crown, StatueMesh statue)
    {
        float[] cached = CACHE.get(statue);
        if(cached != null)
        {
            return cached;
        }
        float[] crownBounds = bounds(crown);
        float[] statueBounds = bounds(statue);

        float cx = (crownBounds[0] + crownBounds[3]) / 2F;
        float cz = (crownBounds[2] + crownBounds[5]) / 2F;
        float radius = Math.max(crownBounds[3] - crownBounds[0], crownBounds[5] - crownBounds[2])
            / 2F * PLATFORM_FRACTION;

        float top = -Float.MAX_VALUE;
        for(StatueMesh.Part part : crown.parts())
        {
            float[] p = part.positions();
            for(int i = 0; i < p.length; i += 3)
            {
                float dx = p[i] - cx;
                float dz = p[i + 2] - cz;
                if(dx * dx + dz * dz <= radius * radius && p[i + 1] > top)
                {
                    top = p[i + 1];
                }
            }
        }
        if(top == -Float.MAX_VALUE)
        {
            top = crownBounds[4];
        }

        float sx = (statueBounds[0] + statueBounds[3]) / 2F;
        float sz = (statueBounds[2] + statueBounds[5]) / 2F;
        float[] offset = {cx - sx, top - statueBounds[1], cz - sz};
        CACHE.put(statue, offset);
        return offset;
    }

    /** @return {@code {minX, minY, minZ, maxX, maxY, maxZ}} */
    private static float[] bounds(StatueMesh mesh)
    {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for(StatueMesh.Part part : mesh.parts())
        {
            float[] p = part.positions();
            for(int i = 0; i < p.length; i += 3)
            {
                for(int a = 0; a < 3; a++)
                {
                    box[a] = Math.min(box[a], p[i + a]);
                    box[a + 3] = Math.max(box[a + 3], p[i + a]);
                }
            }
        }
        return box;
    }
}
