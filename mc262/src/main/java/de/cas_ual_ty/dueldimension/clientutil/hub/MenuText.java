package de.cas_ual_ty.dueldimension.clientutil.hub;

/**
 * Text drawn smaller than the font, without going soft.
 *
 * <h2>Why a class for one function</h2>
 * Because three copies of it had already been written -- in
 * {@code DeckEditorScreen}, in {@code StatueRewardScreen}, and as a comment in
 * a fourth place saying the deck tiles ought to have one -- and the places that
 * still lacked it are exactly the places the text looked blurry. A rule that
 * lives in one file can be applied everywhere; one that lives in a screen gets
 * reinvented per screen, which is what happened.
 *
 * @see #crispScale for the whole of the reasoning
 */
public final class MenuText
{
    private MenuText()
    {
    }

    /**
     * A text scale the GUI can render on whole pixels.
     *
     * <h2>The problem</h2>
     * Minecraft's font is a bitmap, and the GUI is already drawn at an integer
     * scale of its own -- 3, on the 1634x920 window this client runs at. Asking
     * for HALF-size text there asks for 1.5 device pixels per GUI pixel, so
     * every glyph lands on a half pixel, the sampler blends the texels either
     * side of it, and the stroke smears. Half size is crisp only when the GUI
     * scale happens to be even.
     * <p>
     * The scale is therefore snapped to one whose product with the GUI scale is
     * a whole number of device pixels. At GUI scale 3, a wanted 0.5 becomes 2/3
     * -- slightly larger, and sharp -- rather than staying 0.5 and blurred.
     * Floored at one device pixel per GUI pixel, because zero would make the
     * text vanish rather than shrink.
     *
     * <h2>What it deliberately does not do</h2>
     * Round to a whole NUMBER. "Integer scaling" is about the device pixel grid,
     * not about the multiplier: forcing the multiplier to 1 would mean small
     * text could not exist, and forcing it to 1/2, 1/3, 1/4 would be crisp only
     * at the GUI scales that happen to divide by those. The grid is the thing
     * the eye sees, so the grid is what is snapped to.
     */
    /**
     * One device pixel smaller than full size.
     *
     * <h2>What "down by one" means</h2>
     * Not "one percent" or "one point", but one step on the grid this class
     * exists to respect: text that was drawn {@code gui} device pixels per font
     * pixel is drawn {@code gui - 1}. At GUI scale 3 that is 2/3; at 4, 3/4. It
     * is the largest reduction that is still perfectly sharp, and the only one
     * that means the same thing at every window size.
     * <p>
     * Floored at full size when the GUI is already at 1, where a step down is a
     * step to nothing.
     * <p>
     * The same formula {@code StatOverlay.nearScale} uses for the far row of a
     * duel board -- moved here once a second caller wanted it, which is the
     * rule this whole class was created under.
     */
    /**
     * Several device pixels smaller than full size.
     * <p>
     * The generalisation of {@link #oneStepSmaller}, and the reason it needed
     * one: asking crispScale for 0.5 does NOT halve anything. It snaps to whole
     * device pixels, so at GUI scale 3 it rounds 0.5 up to 2/3 -- the same
     * number one step down already gives. Two steps at that scale is 1/3, which
     * is genuinely half of 2/3.
     * <p>
     * Floored at one device pixel per GUI pixel, so a small GUI scale simply
     * stops shrinking rather than vanishing.
     */
    public static float smaller(int steps)
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        if(gui <= 1D)
        {
            return 1F;
        }
        return (float)(Math.max(1D, gui - steps) / gui);
    }

    public static float oneStepSmaller()
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        if(gui <= 1D)
        {
            return 1F;
        }
        return (float)((gui - 1D) / gui);
    }

    public static float crispScale(float wanted)
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        if(gui <= 0D)
        {
            return wanted;
        }
        // The device pixels one GUI pixel of text would occupy, rounded to a
        // whole number and floored at 1 -- 0 would make the text vanish.
        long steps = Math.max(1L, Math.round(wanted * gui));
        return (float)(steps / gui);
    }
}
