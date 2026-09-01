package de.cas_ual_ty.dueldimension.clientutil.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The world's light at the eight corners of a monster, to be read anywhere
 * inside it.
 * <p>
 * <b>Sampled at eight points and interpolated, not looked up per vertex.</b> A
 * per-vertex lookup is the obvious way to write this and it is a block query
 * per vertex per frame -- fifteen thousand of them for one Dark Magician, which
 * is not a cost that buys anything: block light does not vary at the scale of a
 * finger. Eight queries and a trilinear blend give the same answer everywhere
 * it differs and cost nothing measurable.
 * <p>
 * That is also what makes it SMOOTH. One light for the whole model is the cheap
 * version and it steps -- a dragon crossing a torch's edge changes brightness
 * all at once, because a single sample can only be one value. With the corners
 * blended, the near side of a five-block monster can be lit while its far side
 * is not, and walking a torch past it moves the gradient rather than flipping
 * it.
 *
 * <h2>Block and sky are interpolated separately</h2>
 *
 * A packed light is two numbers in one int -- block in the low half, sky in the
 * high. Interpolating the int itself would carry the block value's arithmetic
 * into the sky value's bits, which is not a blend of anything; it produces
 * lighting that is wrong in a way that looks like a shader bug. So the pair is
 * taken apart, blended as numbers, and packed again.
 */
public final class ModelLight
{
    /** Full block and full sky, which is what a hologram used to be given. */
    public static final int FULL_BRIGHT = 0xF000F0;

    private final Vec3 min;
    private final Vec3 size;
    /** Block light at each corner, indexed by (x?1:0) | (y?2:0) | (z?4:0). */
    private final int[] block = new int[8];
    private final int[] sky = new int[8];

    private ModelLight(Vec3 min, Vec3 max)
    {
        this.min = min;
        Vec3 span = max.subtract(min);
        // Never zero: a flat box would divide by nothing when a vertex is
        // placed along that axis.
        this.size = new Vec3(Math.max(1.0E-4D, span.x), Math.max(1.0E-4D, span.y),
            Math.max(1.0E-4D, span.z));
    }

    /**
     * Reads the light around a box.
     * <p>
     * <b>Only safe where a renderer is allowed to look at the world.</b> On this
     * version that means extraction, not submission -- {@code submit} is handed
     * what extraction wrote down and nothing else. Building one of these in
     * {@code submit} would be reaching for the level from a thread and a moment
     * that is not promised one.
     */
    public static ModelLight around(BlockAndLightGetter level, AABB box)
    {
        ModelLight out = new ModelLight(new Vec3(box.minX, box.minY, box.minZ),
            new Vec3(box.maxX, box.maxY, box.maxZ));
        for(int corner = 0; corner < 8; corner++)
        {
            BlockPos at = BlockPos.containing(
                (corner & 1) == 0 ? box.minX : box.maxX,
                (corner & 2) == 0 ? box.minY : box.maxY,
                (corner & 4) == 0 ? box.minZ : box.maxZ);
            // getBrightness rather than a packed helper: the packing utility
            // moved between these two Minecraft versions and this did not, so
            // one implementation serves both. The <<4 is the lightmap's own
            // scale -- a level of 0..15 addresses texel 0..240.
            out.block[corner] = level.getBrightness(LightLayer.BLOCK, at) << 4;
            out.sky[corner] = level.getBrightness(LightLayer.SKY, at) << 4;
        }
        return out;
    }


    /**
     * The light around a monster standing at {@code feet}.
     *
     * <b>The samples have to land in the air the creature occupies, not in the
     * block it is standing on.</b> Light inside a solid block is zero, so a box
     * whose bottom face rests on the surface samples the surface: four of the
     * eight corners come back black and the blend drags the whole lower half of
     * the model down with them. Both callers built their box that way at first
     * and every monster went dark from the knees down -- worst on a duel board,
     * which is a stone platform on a pillar, so the horizontal corners were
     * inside it too.
     *
     * So: a column starting just ABOVE the feet and no wider than the creature
     * stands, which for anything in the open is air on all eight corners.
     */
    public static ModelLight standing(BlockAndLightGetter level, Vec3 feet, double height)
    {
        double bottom = feet.y + 0.1D;
        double top = Math.max(bottom + 0.1D, feet.y + height);
        return around(level, new AABB(feet.x - 0.5D, bottom, feet.z - 0.5D,
            feet.x + 0.5D, top, feet.z + 0.5D));
    }

    /** The same light everywhere, for a caller that wants the old behaviour. */
    public static ModelLight flat(int packed)
    {
        ModelLight out = new ModelLight(Vec3.ZERO, new Vec3(1D, 1D, 1D));
        for(int corner = 0; corner < 8; corner++)
        {
            out.block[corner] = packed & 0xFFFF;
            out.sky[corner] = (packed >> 16) & 0xFFFF;
        }
        return out;
    }

    /** Packed light at a world point, trilinear between the corners. */
    public int at(double x, double y, double z)
    {
        double fx = clamp((x - min.x) / size.x);
        double fy = clamp((y - min.y) / size.y);
        double fz = clamp((z - min.z) / size.z);
        return (blend(sky, fx, fy, fz) << 16) | blend(block, fx, fy, fz);
    }

    private static double clamp(double v)
    {
        return v < 0D ? 0D : v > 1D ? 1D : v;
    }

    private static int blend(int[] corners, double fx, double fy, double fz)
    {
        double total = 0D;
        for(int corner = 0; corner < 8; corner++)
        {
            double wx = (corner & 1) == 0 ? 1D - fx : fx;
            double wy = (corner & 2) == 0 ? 1D - fy : fy;
            double wz = (corner & 4) == 0 ? 1D - fz : fz;
            total += corners[corner] * wx * wy * wz;
        }
        // Rounded to a whole lightmap step. The lightmap is a texture lookup, so
        // a fractional coordinate samples between two entries and the result is
        // the same blend by another route -- but keeping it whole means the
        // value written matches what every other renderer writes.
        return ((int)Math.round(total)) & 0xFFFF;
    }
}
