package de.cas_ual_ty.dueldimension.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
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

    /**
     * Turns alpha blending on, because 1.21.1's plain blit does not.
     *
     * <h2>The bug this exists for</h2>
     * {@code GuiGraphics} has two {@code innerBlit} overloads and they disagree
     * about blending. The one that takes a colour brackets its draw with
     * {@code RenderSystem.enableBlend()} and {@code disableBlend()}; the one
     * that does not takes the blend state as it finds it and leaves it alone.
     * Every blit in this mod reaches the SECOND one -- the tinted overload below
     * sets a shader colour and then calls the colourless blit -- so not one of
     * them manages blending.
     * <p>
     * That is survivable only while something else happens to have left blending
     * on, and the thing that most often turns it off is <b>drawing text</b>. A
     * string is batched into {@code RenderType.text}, which is translucent, and
     * a translucent render type's teardown ends with {@code disableBlend()}.
     * {@code drawString} flushes immediately, so the teardown runs immediately,
     * and the next PNG is drawn with its alpha channel ignored -- transparent
     * texels come out as whatever RGB the file happens to store behind them,
     * which for these assets is black.
     * <p>
     * Hence the symptom: a label plate that draws correctly in one place and as
     * a black box in another, the difference being nothing but whether a caption
     * was drawn just before it. <b>Every element in this mod's interface is an
     * alpha PNG</b>, so this is not a corner case; it is the common path.
     * <p>
     * 26.2 never meets this. Its pipelines carry their own blend state and a
     * draw cannot inherit one.
     * <p>
     * Left ENABLED afterwards rather than restored. There is no state to restore
     * to -- the caller never established one -- and blending on is what the GUI
     * wants for everything except the opaque background fills, which set their
     * own state anyway.
     *
     * <h2>It enables blending and does NOT choose the function</h2>
     * That distinction is the whole of a bug this caused on its first outing.
     * The Monuments backdrop is two layers, and the second is ADDED at half
     * strength -- {@code FoilPipelines.ADDITIVE.apply()} sets
     * {@code blendFuncSeparate(ONE, ONE, ONE, ONE)} and then blits the tiles.
     * A {@code defaultBlendFunc()} here ran between those two and put the
     * function back to ordinary alpha, so an OPAQUE tile layer painted straight
     * over the lattice instead of adding to it, and the lattice -- the layer
     * with all the depth in it -- disappeared.
     * <p>
     * The bug this method exists for is blending being <em>off</em>, not the
     * function being wrong. The function a translucent render type leaves behind
     * is already {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}, which is the one an
     * alpha PNG wants, and vanilla's own coloured blit likewise enables blending
     * without setting a function. So the deliberate choice a caller has made
     * survives, and the accident it was making survives being fixed.
     */
    private void blending()
    {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y,
        float u, float v, int width, int height, int textureWidth, int textureHeight)
    {
        blending();
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y,
        float u, float v, int width, int height, int regionWidth, int regionHeight,
        int textureWidth, int textureHeight)
    {
        blending();
        graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight,
            textureWidth, textureHeight);
    }

    /**
     * The tinted blit, which 1.21.1 has no single call for.
     * <p>
     * 26.2 takes an ARGB as the last argument and multiplies it into the draw.
     * Here the colour is set on the graphics, the blit issued, and the colour
     * put back -- which is the same result and is how 1.21.1 does a tinted draw
     * everywhere else. Resetting is not optional: {@code setColor} is global
     * state, so leaving it would tint whatever the GUI drew next.
     */
    public void blit(Object pipeline, ResourceLocation texture, int x, int y,
        float u, float v, int width, int height, int regionWidth, int regionHeight,
        int textureWidth, int textureHeight, int tint)
    {
        float alpha = (tint >>> 24) / 255F;
        float red = (tint >>> 16 & 0xFF) / 255F;
        float green = (tint >>> 8 & 0xFF) / 255F;
        float blue = (tint & 0xFF) / 255F;
        // Before the colour, not after: setColor flushes a managed batch, and a
        // flush is one of the things that can leave blending off.
        blending();
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight,
            textureWidth, textureHeight);
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y,
        int width, int height)
    {
        blending();
        graphics.blitSprite(sprite, x, y, width, height);
    }

    /**
     * A texture stretched across a rectangle, given NORMALISED uv bounds.
     * <p>
     * 26.2 takes {@code (x1, y1, x2, y2, minU, maxU, minV, maxV)} with the uvs
     * as fractions. 1.21.1's blit takes uvs in PIXELS plus the texture's own
     * size, and divides one by the other.
     * <p>
     * So a nominal size is supplied and the fractions scaled to it. Any value
     * works and the choice cancels out — 1.21.1 computes {@code u / textureWidth},
     * so feeding it {@code u * N} and {@code N} gives back exactly {@code u}.
     * 256 is arbitrary and is the only reason this is exact rather than
     * approximate; the real texture's dimensions are not needed and are not
     * available here.
     */
    public void blit(ResourceLocation texture, int x1, int y1, int x2, int y2,
        float minU, float maxU, float minV, float maxV)
    {
        blending();
        final int nominal = 256;
        graphics.blit(texture, x1, y1, x2 - x1, y2 - y1,
            minU * nominal, minV * nominal,
            Math.round((maxU - minU) * nominal), Math.round((maxV - minV) * nominal),
            nominal, nominal);
    }

    /**
     * Resolves everything described so far, so what follows lands on top of it.
     * <p>
     * A no-op verb on 26.2, where the render state already carries submission
     * order. Here it is the only way to say "this layer is finished": see
     * {@code mc1211/README.md}, "GuiGraphics draws in TYPE order, not call
     * order".
     */
    public void flush()
    {
        graphics.flush();
    }

    public void item(ItemStack stack, int x, int y)
    {
        graphics.renderItem(stack, x, y);
    }

    // ---- scissor ----

    /**
     * A clip rectangle in the CURRENT POSE's coordinates, which is 26.2's rule
     * and not 1.21.1's.
     * <p>
     * The two versions differ here and nothing catches it: both take four ints
     * and neither complains. 26.2 runs the rectangle through the pose --
     * {@code new ScreenRectangle(...).transformMaxBounds(this.pose)} -- so a
     * caller inside a scaled matrix passes the same numbers it draws with.
     * 1.21.1 pushes the rectangle onto the scissor stack untouched, in raw GUI
     * pixels.
     * <p>
     * <b>Under a scale that is not a near miss, it is everything or nothing.</b>
     * The duel sidebar draws its effect text inside {@code scale(0.75)}: text at
     * pose y=340 lands on screen at 255, while the clip asking for y=340 stays
     * at 340. The band that should have held the text sat entirely below it, so
     * every line was scissored away and the card description came out blank --
     * with the well, the border and the scroll bar all still drawn, because
     * fill() and blit() DO go through the pose. A widget that looks built and
     * empty rather than broken.
     * <p>
     * All four corners are transformed rather than two, so a rotated pose gets
     * its bounding box instead of a rectangle read off two corners that are no
     * longer opposite. Rounded outward, as 26.2 rounds: a clip that splits a
     * pixel should keep it, since the alternative is shaving the edge off the
     * glyph row the caller was trying to show.
     */
    public void enableScissor(int x0, int y0, int x1, int y1)
    {
        org.joml.Matrix4f matrix = graphics.pose().last().pose();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for(int corner = 0; corner < 4; corner++)
        {
            org.joml.Vector3f at = matrix.transformPosition(new org.joml.Vector3f(
                (corner & 1) == 0 ? x0 : x1, (corner & 2) == 0 ? y0 : y1, 0F));
            minX = Math.min(minX, at.x);
            minY = Math.min(minY, at.y);
            maxX = Math.max(maxX, at.x);
            maxY = Math.max(maxY, at.y);
        }
        graphics.enableScissor(Mth.floor(minX), Mth.floor(minY),
            Mth.ceil(maxX), Mth.ceil(maxY));
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
