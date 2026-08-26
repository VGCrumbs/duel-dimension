package de.cas_ual_ty.dueldimension.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Minecraft 26.2's GUI drawing surface, on top of 1.21.1's.
 *
 * <h2>Why this exists</h2>
 *
 * 26.2 replaced {@code GuiGraphics} with {@code GuiGraphicsExtractor} as part of
 * the retained-mode rewrite, and renamed most of the drawing verbs on the way:
 * {@code drawString} became {@code text}, {@code renderItem} became {@code item},
 * {@code drawCenteredString} became {@code centeredText}. The primitives
 * themselves all still exist in 1.21.1 -- checked against the 1.21.1 mappings,
 * every method this delegates to is there. What changed is spelling and one
 * extra argument.
 * <p>
 * That difference appears in <b>78 files and 330 compile errors</b>, which is by
 * a wide margin the largest single obstacle in the port. Rewriting all of it by
 * hand would mean touching every screen in the mod to change the name of a call
 * whose behaviour is identical.
 * <p>
 * So the platform provides the older surface instead, and the drawing code ports
 * unchanged. This is the same trade the mod already makes with {@code CardLine}
 * and {@code CardPresentation}: name the thing that differs, once, in one place.
 * It also keeps the two trees textually parallel, which is what makes a fix
 * applied to one of them applyable to the other.
 *
 * <h2>What it does not do</h2>
 *
 * It is a translation, not an emulation. 26.2 draws in retained mode -- calls
 * build a render state that is submitted later -- and 1.21.1 draws immediately.
 * Anything that depends on that difference is NOT handled here and has to be
 * dealt with at the call site: {@code nextStratum}, {@code blurBeforeThisStratum}
 * and {@code dispose} have no meaning in immediate mode and are no-ops with a
 * comment saying so, rather than silently pretending.
 *
 * @see Pose the 2D matrix stack 26.2 exposes, over 1.21.1's PoseStack
 */
public class GuiGraphicsExtractor
{
    private final GuiGraphics graphics;
    private final Pose pose;

    public GuiGraphicsExtractor(GuiGraphics graphics)
    {
        this.graphics = graphics;
        this.pose = new Pose(graphics);
    }

    /** The 1.21.1 object underneath, for code that has been properly ported. */
    public GuiGraphics vanilla()
    {
        return graphics;
    }

    // ---- text ----

    public void text(Font font, String s, int x, int y, int colour)
    {
        graphics.drawString(font, s, x, y, colour);
    }

    public void text(Font font, String s, int x, int y, int colour, boolean shadow)
    {
        graphics.drawString(font, s, x, y, colour, shadow);
    }

    public void text(Font font, Component s, int x, int y, int colour)
    {
        graphics.drawString(font, s, x, y, colour);
    }

    public void text(Font font, Component s, int x, int y, int colour, boolean shadow)
    {
        graphics.drawString(font, s, x, y, colour, shadow);
    }

    public void text(Font font, FormattedCharSequence s, int x, int y, int colour)
    {
        graphics.drawString(font, s, x, y, colour);
    }

    public void text(Font font, FormattedCharSequence s, int x, int y, int colour, boolean shadow)
    {
        graphics.drawString(font, s, x, y, colour, shadow);
    }

    public void centeredText(Font font, String s, int x, int y, int colour)
    {
        graphics.drawCenteredString(font, s, x, y, colour);
    }

    public void centeredText(Font font, Component s, int x, int y, int colour)
    {
        graphics.drawCenteredString(font, s, x, y, colour);
    }

    public void centeredText(Font font, FormattedCharSequence s, int x, int y, int colour)
    {
        graphics.drawCenteredString(font, s, x, y, colour);
    }

    // ---- shapes ----

    public void fill(int x0, int y0, int x1, int y1, int colour)
    {
        graphics.fill(x0, y0, x1, y1, colour);
    }

    /**
     * The pipeline argument is discarded.
     * <p>
     * 26.2 picks a {@code RenderPipeline} per draw; 1.21.1 has no such concept
     * for GUI fills and uses one path for all of them. Discarding it is correct
     * for every pipeline the mod passes -- they are all the standard GUI ones --
     * and would be wrong only for a custom pipeline, which the GUI never uses.
     */
    public void fill(Object pipeline, int x0, int y0, int x1, int y1, int colour)
    {
        graphics.fill(x0, y0, x1, y1, colour);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int from, int to)
    {
        graphics.fillGradient(x0, y0, x1, y1, from, to);
    }

    /** A one-pixel border, which 1.21.1 has no single call for. */
    public void outline(int x, int y, int width, int height, int colour)
    {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y + 1, x + 1, y + height - 1, colour);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, colour);
    }

    public void horizontalLine(int x0, int x1, int y, int colour)
    {
        graphics.hLine(x0, x1, y, colour);
    }

    public void verticalLine(int x, int y0, int y1, int colour)
    {
        graphics.vLine(x, y0, y1, colour);
    }

    // ---- textures ----

    public void blit(Object pipeline, ResourceLocation texture, int x, int y,
        float u, float v, int width, int height, int textureWidth, int textureHeight)
    {
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y,
        float u, float v, int width, int height, int regionWidth, int regionHeight,
        int textureWidth, int textureHeight)
    {
        graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight,
            textureWidth, textureHeight);
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y,
        int width, int height)
    {
        graphics.blitSprite(sprite, x, y, width, height);
    }

    public void item(ItemStack stack, int x, int y)
    {
        graphics.renderItem(stack, x, y);
    }

    // ---- scissor ----

    public void enableScissor(int x0, int y0, int x1, int y1)
    {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor()
    {
        graphics.disableScissor();
    }

    // ---- tooltips ----

    public void setTooltipForNextFrame(Font font, Component text, int x, int y)
    {
        graphics.renderTooltip(font, text, x, y);
    }

    /** The already-wrapped form the duel screens build. */
    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y)
    {
        graphics.renderTooltip(font, lines, x, y);
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y)
    {
        graphics.renderComponentTooltip(font, lines, x, y);
    }

    // ---- geometry ----

    public int guiWidth()
    {
        return graphics.guiWidth();
    }

    public int guiHeight()
    {
        return graphics.guiHeight();
    }

    public Pose pose()
    {
        return pose;
    }

    // ---- retained-mode only ----

    /**
     * No-op: strata order a retained render state, and 1.21.1 draws in the order
     * it is called. A caller relying on this to sort layers has to be looked at,
     * which is why this does nothing rather than something plausible.
     */
    public void nextStratum()
    {
    }

    /** No-op, same reason as {@link #nextStratum}. */
    public void blurBeforeThisStratum()
    {
    }

    /** No-op: there is no retained state to release in immediate mode. */
    public void dispose()
    {
    }

    /**
     * 26.2's 2D matrix stack, over 1.21.1's 3D one.
     * <p>
     * The GUI moved from {@code PoseStack} to JOML's {@code Matrix3x2fStack} in
     * 26.2, and the verbs changed with it -- {@code pushPose} became {@code
     * pushMatrix}. Every use in this mod is one of six calls, all of which a 3D
     * stack does with z left alone.
     */
    public static class Pose
    {
        private final GuiGraphics graphics;

        Pose(GuiGraphics graphics)
        {
            this.graphics = graphics;
        }

        public void pushMatrix()
        {
            graphics.pose().pushPose();
        }

        public void popMatrix()
        {
            graphics.pose().popPose();
        }

        public void translate(float x, float y)
        {
            graphics.pose().translate(x, y, 0F);
        }

        public void scale(float x, float y)
        {
            graphics.pose().scale(x, y, 1F);
        }

        /** Radians about the z axis, which is the only axis a 2D stack has. */
        public void rotate(float radians)
        {
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(radians));
        }

        /** As {@link #rotate}, about a point rather than the origin. */
        public void rotateAbout(float radians, float x, float y)
        {
            graphics.pose().translate(x, y, 0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(radians));
            graphics.pose().translate(-x, -y, 0F);
        }
    }
}
