package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import java.util.function.BiConsumer;

/**
 * The window through which the duel board is drawn.
 * <p>
 * A GUI cannot draw an arbitrary quad. Its blit helpers take an axis-aligned
 * rectangle and that is the whole vocabulary, which is no use to a perspective
 * field where every zone is a trapezoid narrowing towards the far edge.
 * <p>
 * The way back in is {@code submitCustomGeometry}, which hands over a
 * {@code PoseStack.Pose} and a {@code VertexConsumer} — but it only exists
 * inside a render pass, and a screen does not have one. A
 * <b>picture-in-picture</b> region is what bridges the two: the screen asks for
 * a rectangle, the game renders that rectangle off-screen through a real pass,
 * and blits the result back. Vanilla uses the same mechanism for the entity in
 * the inventory and for the book on a lectern.
 * <p>
 * So this is deliberately thin. It owns no drawing of its own; it carries a
 * callback and gives it the {@code PoseStack} and {@code SubmitNodeCollector}
 * that {@link FieldQuad} needs, in a place where using them is allowed.
 */
public final class BoardPip
{
    /**
     * What to draw, and where.
     *
     * @param painter given the pose and the collector, inside the pass
     */
    public record State(int x0, int y0, int x1, int y1, float scale,
        BiConsumer<PoseStack, SubmitNodeCollector> painter,
        ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements PictureInPictureRenderState
    {
    }

    /** Renders one of those states by handing the pass to its painter. */
    public static final class Renderer extends PictureInPictureRenderer<State>
    {
        @Override
        public Class<State> getRenderStateClass()
        {
            return State.class;
        }

        /**
         * Hands the pass to the painter, with the origin moved to the top-left.
         * <p>
         * The base class sets up a space whose origin is the <b>bottom</b>-centre
         * of the region, which is not symmetric and is worth reading off the
         * bytecode rather than assuming:
         * <pre>
         * poseStack.translate(widthPx / 2F, getTranslateY(heightPx, guiScale), 0F);
         * poseStack.scale(s, s, -s);          // s = guiScale * state.scale()
         * </pre>
         * and {@code getTranslateY} returns its first argument unchanged -- the
         * <em>full</em> height, not half of it. So x is centred but y is at the
         * far edge. Undoing it with half the height, as if both axes matched,
         * puts everything drawn here exactly half a region too low.
         * <p>
         * The translate happens before the scale, so it is in physical pixels
         * while everything below is in GUI units; undoing it in GUI units is
         * therefore the right move, and the guiScale factor cancels.
         */
        @Override
        protected void renderToTexture(State state, PoseStack poseStack,
            SubmitNodeCollector collector)
        {
            poseStack.pushPose();
            poseStack.translate(-(state.x1() - state.x0()) / 2F,
                -(float)(state.y1() - state.y0()), 0F);
            state.painter().accept(poseStack, collector);
            poseStack.popPose();
        }

        @Override
        protected String getTextureLabel()
        {
            return "dueldimension board";
        }
    }

    private BoardPip()
    {
    }

    /** Called once from the client initialiser. */
    public static void register()
    {
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(
            context -> new Renderer());
    }

    /**
     * Draws into a rectangle of the screen with arbitrary geometry allowed.
     * <p>
     * The scissor area comes from the extractor so the board is clipped by
     * whatever the screen has already clipped to, exactly as a blit would be.
     */
    public static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
        BiConsumer<PoseStack, SubmitNodeCollector> painter)
    {
        ((de.cas_ual_ty.dueldimension.mixin.client.GuiGraphicsExtractorAccessor)graphics)
            .dueldimension$guiRenderState()
            .addPicturesInPictureState(new State(x0, y0, x1, y1, 1F, painter, null,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null)));
    }
}
