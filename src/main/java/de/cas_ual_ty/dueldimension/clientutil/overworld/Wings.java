package de.cas_ual_ty.dueldimension.clientutil.overworld;

/**
 * A pair of wings behind a monster, drawn from one set of frames and mirrored.
 * <p>
 * One animation, twice: a sheet holds a single wing and the other side is the
 * same frames flipped. That is not a shortcut, it is what makes the pair
 * symmetrical -- two hand-drawn wings never quite match, and a creature whose
 * left wing beats a pixel ahead of its right reads as broken long before
 * anybody works out why.
 * <p>
 * Everything here is measured as a fraction of the BODY's height rather than in
 * blocks or pixels, so a monster scaled to half size keeps its wings in
 * proportion and on the same shoulders. A number in blocks would have to be
 * re-tuned every time the body's size changed.
 *
 * @param layer   the wing's own frames, which may live in the same sheet as the
 *                body at a different cell size
 * @param anchor  how far up the body the wings are centred, from the feet, as a
 *                fraction of its height
 * @param spacing how far out from the middle each wing sits, as a fraction of
 *                the body's height
 * @param scale   the wing's height, again as a fraction of the body's
 */
public record Wings(SpriteLayer layer, float anchor, float spacing, float scale)
{
    public static final float DEFAULT_ANCHOR = 0.62F;
    public static final float DEFAULT_SPACING = 0.22F;
    public static final float DEFAULT_SCALE = 0.55F;

    public static Wings of(SpriteLayer layer)
    {
        return new Wings(layer, DEFAULT_ANCHOR, DEFAULT_SPACING, DEFAULT_SCALE);
    }
}
