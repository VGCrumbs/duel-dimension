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

    /** The painter, with the same shape it has on 26.2. */
    public interface CustomGeometryRenderer
    {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
