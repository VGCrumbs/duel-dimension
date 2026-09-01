package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Saying that one thing is drawn in front of another.
 *
 * <h2>Why this is almost nothing here</h2>
 * Because 26.2's GUI is retained-mode: a screen DESCRIBES itself into a render
 * state and the game draws that state in the order it was described. Call order
 * is layering, and {@code nextStratum} is the only thing needed to keep an
 * overlay out of the stratum the screen underneath is still filling.
 * <p>
 * 1.21.1 is where this class earns its existence. There {@code GuiGraphics}
 * fills one buffer per render type and resolves the lot at a flush, in whatever
 * order the buffer source iterates those types, so call order settles nothing
 * between two DIFFERENT types -- and glyphs and textures are different types.
 * That is how the deck editor's filter captions ended up over its hover preview
 * while the chips' own plates went under it. The 1.21.1 copy of this file
 * flushes and lifts on the depth buffer; see {@code mc1211/README.md},
 * "GuiGraphics draws in TYPE order, not call order".
 * <p>
 * The two are kept as one SENTENCE at the call sites -- {@code
 * Layering.foreground(graphics, () -> ...)} in both trees -- because that is
 * what lets a fix to one of these screens be applied to the other.
 */
public final class Layering
{
    private Layering()
    {
    }

    /** Draws something that must be in front of the whole screen. */
    public static void foreground(GuiGraphicsExtractor graphics, Runnable draw)
    {
        graphics.nextStratum();
        draw.run();
    }

    /**
     * Everything drawn from here on is in front of everything drawn before it.
     * <p>
     * The same claim as {@link #foreground} with no scope, for the case where
     * the thing that must come out on top is drawn by somebody else -- {@code
     * super.render} painting a screen's widgets, say, which cannot be wrapped
     * in a Runnable without wrapping the backdrop it stands on as well.
     * <p>
     * Here that is literally all {@code foreground} was doing anyway, since a
     * retained-mode GUI needs no lift and nothing to put back.
     */
    public static void above(GuiGraphicsExtractor graphics)
    {
        graphics.nextStratum();
    }
}
