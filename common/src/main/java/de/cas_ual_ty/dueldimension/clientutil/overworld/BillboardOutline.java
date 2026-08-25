package de.cas_ual_ty.dueldimension.clientutil.overworld;

/**
 * Which monster is being cropped, and whether to show the box.
 * <p>
 * Cropping a sprite means deciding where the art ends and the empty margin
 * begins, and that is a judgement about the PICTURE -- so it has to be made
 * against the picture. The sheet thumbnail in the editor is a few dozen pixels
 * across and shows the frame flat; the hologram is the thing that will actually
 * stand on the board. Drawing the sampled box around the hologram puts the
 * decision and its consequence in the same place.
 * <p>
 * One card at a time, because that is how many the editor edits. A renderer
 * asks {@link #wants} with the code it is about to draw and gets an answer
 * without knowing the editor exists, which is what keeps a debugging aid from
 * becoming a dependency the duel field has to carry.
 * <p>
 * Zero means nobody. A passcode is never zero, so there is no card this can be
 * confused with and no separate flag to keep in step with it.
 */
public final class BillboardOutline
{
    private BillboardOutline()
    {
    }

    private static long watched;
    private static boolean shown = true;

    /** Start outlining this card's hologram. */
    public static void watch(long code)
    {
        watched = code;
    }

    /** Stop outlining anything, for when the editor closes. */
    public static void stop()
    {
        watched = 0L;
    }

    public static boolean shown()
    {
        return shown;
    }

    public static void show(boolean value)
    {
        shown = value;
    }

    /**
     * Whether the hologram about to be drawn is the one being cropped.
     * <p>
     * The switch is kept separate from the subject so that turning the box off
     * to look at the art does not lose track of which card was being edited --
     * turning it back on resumes where it was rather than starting again.
     */
    public static boolean wants(long code)
    {
        return shown && watched != 0L && code == watched;
    }
}
