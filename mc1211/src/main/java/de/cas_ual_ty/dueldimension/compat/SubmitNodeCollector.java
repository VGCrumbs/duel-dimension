package de.cas_ual_ty.dueldimension.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * 26.2's render-pass submission API, over 1.21.1's buffer sources.
 *
 * <h2>Why a shim rather than a rewrite</h2>
 *
 * 26.2 replaced immediate-mode drawing with a submission model: a call does not
 * draw, it hands geometry to a collector that the render pass replays later.
 * 1.21.1 has no such type, which made this 67 compile errors -- the largest
 * remaining cluster after the GUI.
 * <p>
 * But almost all of that is the type appearing in method signatures. The actual
 * API this mod uses is two calls, {@link #order} and {@link #submitCustomGeometry},
 * and both have a faithful 1.21.1 equivalent. So the board's drawing code ports
 * unchanged and the difference lives here.
 *
 * <h2>The subtle part: ordering</h2>
 *
 * <b>Submission order is not draw order on either version, and for the same
 * reason.</b> 26.2 batches submissions by render type; 1.21.1's {@link
 * MultiBufferSource.BufferSource} batches buffers by render type and flushes them
 * at the end. Both will happily draw the playmat over the cards standing on it if
 * left to themselves -- which is exactly the bug {@code FieldQuad}'s layer
 * counter was written to fix, and its comment says so:
 *
 * <blockquote>submission order is NOT draw order [...] On the board that put the
 * playmat on top of the cards standing on it -- and the mat is drawn tinted, so
 * everything under it looked darkened.</blockquote>
 *
 * On 26.2, {@code order()} solves it by giving each draw the next layer key. Here
 * the equivalent is to end the batch after each submission, which forces the
 * geometry out immediately and makes call order the draw order again. That is why
 * {@link #submitCustomGeometry} flushes rather than simply filling a buffer: doing
 * the obvious thing would compile, run, and reproduce a bug that took a while to
 * find the first time.
 */
public class SubmitNodeCollector
{
    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers)
    {
        this.buffers = buffers;
    }

    /**
     * The buffer source underneath, for the one thing that cannot go through
     * this shim: an ItemStack, which 1.21.1 renders with its own renderer and
     * its own choice of render types.
     */
    public MultiBufferSource buffers()
    {
        return buffers;
    }

    /**
     * The layer a submission belongs to, which 1.21.1 does not need.
     * <p>
     * Returns {@code this} so the fluent call sites read unchanged. The ordering
     * the layer key buys on 26.2 is provided here by flushing per draw instead;
     * see the class note.
     */
    public SubmitNodeCollector order(int layer)
    {
        return this;
    }

    /**
     * Draws the geometry now.
     * <p>
     * The painter is handed the same two things it gets on 26.2 -- a pose and a
     * vertex sink -- so its body is untouched.
     */
    public void submitCustomGeometry(PoseStack pose, RenderType type,
        CustomGeometryRenderer painter)
    {
        painter.render(pose.last(), buffers.getBuffer(type));

        // Flush THIS type before the next submission, so call order survives.
        // Without it the buffer source batches everything by render type and
        // emits it in its own order at the end of the frame.
        if(buffers instanceof MultiBufferSource.BufferSource source)
        {
            source.endBatch(type);
        }
    }

    /**
     * Draws a line of text now, into the same buffer source everything else goes
     * into.
     * <p>
     * The argument list is 26.2's, verbatim, so the call sites port unchanged:
     * {@code (pose, x, y, string, dropShadow, displayMode, lightCoords, color,
     * backgroundColor, outlineColor)}. 1.21.1's {@code Font.drawInBatch} takes the
     * same information in a different order and one field short, so the mapping is
     * a reshuffle rather than a translation.
     * <p>
     * <b>{@code outlineColor} is dropped, and that is safe here rather than in
     * general.</b> 26.2 carries it as a fourth colour on the submission; 1.21.1's
     * font has no outlined-batch variant reachable from this call. Both call sites
     * in this mod pass {@code 0} -- no outline -- so nothing is lost today. A
     * future caller that wants one will get plain text and no warning, which is why
     * this says so out loud.
     * <p>
     * Flushed per call for the same reason {@link #submitCustomGeometry} is: text
     * batched by render type would otherwise surface at the end of the frame, on
     * top of board geometry drawn after it.
     */
    public void submitText(PoseStack pose, float x, float y,
        net.minecraft.util.FormattedCharSequence string, boolean dropShadow,
        net.minecraft.client.gui.Font.DisplayMode displayMode, int lightCoords,
        int color, int backgroundColor, int outlineColor)
    {
        net.minecraft.client.gui.Font font =
            net.minecraft.client.Minecraft.getInstance().font;
        font.drawInBatch(string, x, y, color, dropShadow, pose.last().pose(),
            buffers, displayMode, backgroundColor, lightCoords);

        if(buffers instanceof MultiBufferSource.BufferSource source)
        {
            source.endBatch();
        }
    }

    /** The painter, with the same shape it has on 26.2. */
    public interface CustomGeometryRenderer
    {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
