package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;

/**
 * Saying that one thing is drawn in front of another.
 *
 * <h2>Why it has to be said at all</h2>
 * On 1.21.1 {@code GuiGraphics} does not draw when it is told to. It fills one
 * buffer per render type and resolves the lot at a flush, in whatever order the
 * buffer source iterates those types -- so call order settles nothing between
 * two DIFFERENT types. Glyphs and textures are different types, which is how
 * the deck editor's filter captions ended up over its hover preview while the
 * chips' own plates went under it: two halves of one widget on opposite sides
 * of a panel drawn after both of them.
 * <p>
 * {@code mc1211/README.md} records the same trap costing the god statues and
 * the Monuments backdrop, and states the general form: anything that must be
 * layered against something else drawn through {@code GuiGraphics} has to say
 * so with a flush.
 *
 * <h2>Why it is a class and not a method on the screens</h2>
 * Because 26.2 says the same thing differently -- it has strata, and its
 * version of this file is {@code nextStratum} and nothing else. Keeping the
 * SENTENCE the same in both trees is what lets a fix to one of these screens be
 * applied to the other, which is the whole bargain
 * {@code GuiGraphicsExtractor} exists for.
 */
public final class Layering
{
    /**
     * How far in front an overlay is drawn, in GUI depth units.
     * <p>
     * Short of the 400 vanilla reserves for tooltips, which have to stay above
     * these as they do above everything.
     */
    private static final float FOREGROUND_Z = 200F;

    private Layering()
    {
    }

    /**
     * Draws something that must be in front of the whole screen.
     * <p>
     * Both halves of the claim are made. The flush on the way in closes the
     * screen underneath, so nothing already described can be resolved after
     * this. The z-lift puts the overlay in front on the depth buffer as well,
     * which is what keeps it there if a later pass batches anyway. The flush on
     * the way out closes the overlay before the pose is popped, so the lift
     * cannot outlive it.
     * <p>
     * The same bargain {@code BoardPip.draw} strikes, for the same reason.
     */
    public static void foreground(GuiGraphicsExtractor graphics, Runnable draw)
    {
        graphics.flush();
        graphics.vanilla().pose().pushPose();
        graphics.vanilla().pose().translate(0F, 0F, FOREGROUND_Z);
        draw.run();
        graphics.flush();
        graphics.vanilla().pose().popPose();
    }

    /**
     * Everything drawn from here on is in front of everything drawn before it.
     *
     * <h2>Why this exists beside {@link #foreground}</h2>
     * {@code foreground} takes a Runnable because it lifts the pose and has to
     * put it back, and that is the right shape for an overlay that draws itself
     * and is done. It is the wrong shape when the thing that must come out on
     * top is drawn by somebody else -- {@code super.render} painting a screen's
     * widgets, say. A backdrop cannot be wrapped without wrapping the buttons
     * that stand on it, and the buttons are not ours to wrap.
     * <p>
     * So this is the same claim with no scope and no lift: a bare seam. What
     * follows resolves after what came before, and two things drawn after the
     * same seam still settle between themselves by call order, which is what
     * lets a plate and its buttons stay in the right order relative to each
     * other while both clear the panels underneath.
     * <p>
     * No z-lift is what makes that work, and is also its limit: this orders
     * against what has already been DESCRIBED, not against a later
     * {@code foreground}, which lifts past it on the depth buffer regardless.
     */
    public static void above(GuiGraphicsExtractor graphics)
    {
        graphics.flush();
    }
}
