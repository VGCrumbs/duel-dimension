package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BiConsumer;

/**
 * The window through which the duel board is drawn.
 *
 * <h2>Why this is nearly empty here, and 90 lines on 26.2</h2>
 *
 * The problem the 26.2 class solves does not exist on 1.21.1.
 * <p>
 * A GUI cannot draw an arbitrary quad with its blit helpers -- that much is true
 * on both versions, and it is why {@link FieldQuad} exists at all. What differs
 * is whether a screen is allowed to reach past them. On 26.2 it is not: screens
 * are retained-mode, a screen produces a render STATE rather than drawing, and
 * the only way to get a real render pass inside a rectangle of one is to ask for
 * a <b>picture-in-picture</b> region -- the mechanism vanilla uses for the entity
 * in the inventory and the book on a lectern.
 * <p>
 * 1.21.1 is immediate-mode. {@code GuiGraphics} hands out its own
 * {@code PoseStack} and its own {@code MultiBufferSource.BufferSource}, and
 * drawing into them during a screen's render is ordinary. There is nothing to
 * bridge, so this hands the painter the two things it wants and gets out of the
 * way. The 1.19.2 fork drew the board exactly this way, straight from
 * {@code Tesselator} in {@code EngineDuelScreen}, which is the same shape.
 * <p>
 * Nothing below the {@code draw} call changes as a result: {@link FieldQuad},
 * {@code BoardRenderer} and {@code DuelAnimations} take a pose and a collector
 * and are the same text on both versions.
 *
 * <h2>The three things the pip did that have to be done by hand</h2>
 *
 * <ol>
 * <li><b>Origin at the region's top-left.</b> A pip's space starts at the bottom
 * centre of the region and the 26.2 renderer undoes that; callers are written
 * against the result, and say so -- {@code CardPreviewScreen} centres a card by
 * translating half the view. So the same origin is set up here.
 * <li><b>Clipping to the region.</b> A pip renders into a texture the size of
 * the region, so anything drawn outside it is simply not there. Drawing straight
 * into the screen has no such edge, and the card preview's panel does rely on
 * one. A scissor reproduces it, and intersects with whatever the screen already
 * set rather than replacing it.
 * <li><b>The z flip.</b> {@code PictureInPictureRenderer} scales by
 * {@code (s, s, -s)} -- the same negative z {@code renderEntityInInventory} uses
 * on every version -- so a painter's +z points away from the viewer. Only
 * {@link FieldQuad#draw3D} has a z to point anywhere, but a card preview turned
 * inside out is not a subtle bug to chase later, so the flip is kept.
 * </ol>
 */
public final class BoardPip
{
    private BoardPip()
    {
    }

    /**
     * Called once from the client initialiser, and does nothing.
     * <p>
     * On 26.2 this registers the pip renderer with Fabric, without which the
     * board would silently not draw. Kept so the initialiser is the same text on
     * both versions rather than a version check; there is no registry here
     * because there is no separate renderer to register.
     */
    public static void register()
    {
    }

    /**
     * Draws into a rectangle of the screen with arbitrary geometry allowed.
     * <p>
     * The painter gets the screen's own pose and buffer source, with the origin
     * at {@code (x0, y0)}, clipped to the rectangle, and z pointing away.
     */
    /**
     * How far in front of the screen the pip's contents sit, in GUI depth units.
     * <p>
     * Comfortably past the deepest thing any caller draws -- the statue scene is
     * the deepest at 51 -- and comfortably inside the GUI's own ortho range, so
     * there is room either side to be wrong in.
     */
    private static final float FORWARD = 200F;

    public static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
        BiConsumer<PoseStack, SubmitNodeCollector> painter)
    {
        GuiGraphics vanilla = graphics.vanilla();
        PoseStack pose = vanilla.pose();

        // EVERYTHING ALREADY ASKED FOR, ONTO THE SCREEN, BEFORE ANY OF THIS.
        //
        // GuiGraphics does not draw when it is told to; it fills a buffer per
        // render type and the whole lot comes out at the next flush, in TYPE
        // order. So a screen that blits a backdrop and then calls this has both
        // in the same batch, and which of them lands on top is decided by the
        // order the buffer source happens to iterate its types -- not by the
        // order they were asked for.
        //
        // That is what hid the god statues: the reward screen draws a backdrop,
        // a shimmer and a vignette and THEN the models, and the backdrop came
        // out last and covered them. It also made the backdrop itself look
        // wrong, because its three layers were being resolved against each
        // other rather than stacked.
        //
        // Flushing here settles everything before it, so what this paints is
        // painted over it. The flush at the end does the same for what comes
        // after. Two flushes and the pip is an ordered island in a batched
        // world, which is the same bargain submitCustomGeometry strikes.
        vanilla.flush();

        vanilla.enableScissor(x0, y0, x1, y1);
        pose.pushPose();
        // FORWARD, BEFORE THE FLIP, OR THE DEPTH BUFFER EATS IT.
        //
        // Flushing settles draw ORDER; it says nothing about the depth test,
        // and these are different problems. StatueRenderer emits real depth in
        // a band of 1..51 pose-space units, chosen against 26.2's
        // picture-in-picture, which owns its own ortho of [-1000, 1000] and its
        // own cleared depth buffer. Here there is no such private pass: the pip
        // draws into the screen's depth buffer, alongside a backdrop that has
        // already written to it -- so after the z flip below that band sits
        // BEHIND the backdrop and every statue fails the test. Nothing is
        // reported; they are simply not there.
        //
        // Pushed forward by more than the band is deep, so the whole scene is
        // in front of anything the screen drew first. Relative depth within the
        // pip is untouched, which is the part that has to keep working -- a
        // crown sunk into a podium still needs the podium to win where they
        // interpenetrate.
        pose.translate(x0, y0, FORWARD);
        pose.scale(1F, 1F, -1F);

        painter.accept(pose, new SubmitNodeCollector(vanilla.bufferSource()));

        pose.popPose();

        // Before the scissor comes off, not after: the collector flushes per
        // submission, but a caller that queued anything through the graphics
        // itself would otherwise escape the clip and draw over the panel.
        vanilla.flush();
        vanilla.disableScissor();
    }
}
