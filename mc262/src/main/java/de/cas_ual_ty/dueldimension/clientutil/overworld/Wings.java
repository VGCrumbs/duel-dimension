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
 * @param anchor  the height of the wings' MIDDLE above the feet, as a fraction
 *                of the body's height -- a wing is lined up with a shoulder,
 *                and a shoulder is in the middle of a wing rather than under it
 * @param spacing the gap between the body's centre line and each wing's INNER
 *                edge, as a fraction of the body's height, so zero means the
 *                pair meets in the middle
 * @param scale   the wing's height, again as a fraction of the body's
 */
public record Wings(SpriteLayer layer, float anchor, float spacing, float scale)
{
    public static final float DEFAULT_ANCHOR = 0.62F;
    /** Touching the body, which is where a wing starts before anybody moves it. */
    public static final float DEFAULT_SPACING = 0F;
    public static final float DEFAULT_SCALE = 0.55F;

    public static Wings of(SpriteLayer layer)
    {
        return new Wings(layer, DEFAULT_ANCHOR, DEFAULT_SPACING, DEFAULT_SCALE);
    }
}
