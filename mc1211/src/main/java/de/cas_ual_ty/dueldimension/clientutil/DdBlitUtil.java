package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.compat.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing a piece of a texture, with an optional tint.
 * <p>
 * The Forge version of this class built its own quads: it took a
 * {@code PoseStack}, computed four corners and four texture coordinates, and
 * pushed them through {@code RenderSystem.setShader}. None of that exists any
 * more — the GUI is retained-mode, and a caller describes a blit rather than
 * performing one.
 * <p>
 * So what is left is arithmetic and a choice of overload. Two things the
 * callers need are not on the same method:
 * <ul>
 * <li>a <b>UV window</b>, because card art is letterboxed inside a square image
 *     and only the card's own part of it should be drawn;</li>
 * <li>a <b>tint</b>, because a buried card in a pile is drawn dimmer and an
 *     unowned card pulses red — both of which were
 *     {@code RenderSystem.setShaderColor} before a draw, and that call is gone
 *     with everything else immediate-mode.</li>
 * </ul>
 * The extractor's UV-window blit takes no colour, and its coloured blit takes a
 * texel region rather than a UV window. This converts between them so the
 * coloured overload serves both and there is one path instead of two.
 */
public final class DdBlitUtil
{
    /** Opaque white — the colour that changes nothing. */
    public static final int NO_TINT = 0xFFFFFFFF;

    /**
     * The imaginary file size a UV window is expressed against.
     * <p>
     * Any number works as long as the same one is used for the offset and the
     * size; this is large enough that rounding a region to a whole "texel"
     * cannot shift a card's art by a visible fraction.
     */
    private static final int SCALE = 4096;

    private DdBlitUtil()
    {
    }

    /**
     * The three images that are already card-shaped and must be drawn whole.
     * <p>
     * The javadoc here used to say "same test DuelAnimations.isEdoproArt makes
     * for the duel field", which was true and was the problem: three files
     * agreed in prose about a list that only one of them could be changed in.
     * {@link CardFaces#isCardShaped} is that list.
     */
    private static boolean isPlaceholder(ResourceLocation texture)
    {
        return CardFaces.isCardShaped(texture);
    }

    /**
     * Draws part of a texture into a rectangle.
     *
     * @param u0 v0 u1 v1 the part to sample, in 0..1 of the file
     * @param tint        ARGB multiplied into it; {@link #NO_TINT} for none
     */
    public static void blit(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height,
        float u0, float v0, float u1, float v1, int tint)
    {
        blit(graphics, texture, x, y, width, height, u0, v0, u1, v1, tint, false);
    }

    /**
     * As above, greyed.
     * <p>
     * <b>Ownership is an argument to the draw, not a property of the texture.</b>
     * It used to be the latter: {@code DuelTextures.cardUnowned} handed back a
     * third identifier whose bytes the resource pack desaturated on the CPU, so
     * an unowned card was a second image. Now it is the same file drawn through
     * a pipeline whose fragment shader takes a luminance. The flag defaults to
     * false through the overload above rather than being added to it, so a call
     * site that never learns about ownership fails safe as full colour.
     * <p>
     * The tint still multiplies on top and still has the last word — see
     * {@code card_desaturate.fsh} — so a buried card stays dimmed and an
     * unowned one can still pulse red.
     *
     * @param desaturate whether the player does not own this card
     */
    public static void blit(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height,
        float u0, float v0, float u1, float v1, int tint, boolean desaturate)
    {
        // The coloured overload works in texels against a stated file size:
        // it divides the offset by that size and adds the region to reach the
        // far edge. Read off the bytecode, its arguments are
        //     (x, y, uTexels, vTexels, width, height, regionW, regionH,
        //      texW, texH, colour)
        // so a UV window becomes texels by multiplying through a resolution we
        // choose. The region has to be an int, so the resolution sets the
        // precision: SCALE is far finer than any card image, and the actual
        // file size is deliberately not used -- card art is fetched at runtime
        // and its size is a setting, so nothing here can depend on it.
        //
        // Nothing is timed here any more. This blit used to be where a first
        // sighting was paid for, so it was also where it could be measured; the
        // read and decode are on a worker now and the upload is in
        // CardImageManager.refreshCachedTextures, so the two halves are timed
        // separately at the places that actually do them.
        // A placeholder is NOT letterboxed art. unknown.png is 177x254 -- card
        // shaped, not a card inside a square -- so sampling it through the card
        // window draws the middle 60% of it, cropped and stretched. The duel
        // path has always guarded this per call site; hoisting it here covers
        // the thirteen hub sites that never did, which only became the common
        // case once the texture ration started refusing first sightings during
        // a fast scroll.
        if(isPlaceholder(texture))
        {
            u0 = 0F;
            v0 = 0F;
            u1 = 1F;
            v1 = 1F;
            // And it is not the card, so it must not be greyed. cardUnowned
            // returned this same plain UNKNOWN while art was loading, failed or
            // refused by the texture ration, which is why an unowned card with
            // no art yet has always drawn in full colour. Carrying that across
            // is what this line is: the flag arrives unconditionally now, so
            // without it the placeholder would start greying.
            desaturate = false;
        }
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        if(desaturate)
        {
            if(UnownedPipelines.available())
            {
                pipeline = UnownedPipelines.GUI;
            }
            else
            {
                // The shader did not compile. This is a DIM, not a
                // desaturation -- a multiply cannot remove colour -- and it is
                // multiplied INTO the caller's tint rather than replacing it so
                // the deck editor's per-card alpha survives.
                tint = UnownedPipelines.dimmed(tint);
            }
        }
        graphics.blit(pipeline, texture,
            x, y, u0 * SCALE, v0 * SCALE, width, height,
            Math.round((u1 - u0) * SCALE), Math.round((v1 - v0) * SCALE),
            SCALE, SCALE, tint);
    }

    /**
     * A rectangle of texels from a texture, the way {@code GuiComponent.blit}
     * took them.
     * <p>
     * That method was everywhere in the duel screen: {@code blit(x, y, u, v, w,
     * h)}, where the offsets are texels and the file is assumed to be 256x256.
     * The assumption held -- every widget atlas in this mod is -- so the
     * conversion is arithmetic, and doing it here keeps the call sites reading
     * like the originals instead of growing four divisions each.
     *
     * @param u v the top-left texel of the region to draw
     */
    public static void blitTexels(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int u, int v, int width, int height, int tint)
    {
        blit(graphics, texture, x, y, width, height,
            u / (float)ATLAS, v / (float)ATLAS,
            (u + width) / (float)ATLAS, (v + height) / (float)ATLAS, tint);
    }

    /** The size {@code GuiComponent.blit} assumed, and every atlas here is. */
    private static final int ATLAS = 256;

    /** The whole texture, stretched into the rectangle. */
    public static void fullBlit(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height)
    {
        graphics.blit(texture, x, y, x + width, y + height, 0F, 1F, 0F, 1F);
    }

    /** The whole texture, tinted. */
    public static void fullBlit(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height, int tint)
    {
        blit(graphics, texture, x, y, width, height, 0F, 0F, 1F, 1F, tint);
    }

    /**
     * The whole texture, turned a quarter, half or three-quarter turn.
     * <p>
     * A card in defence position lies on its side, and a card on the far side
     * of the field is upside down. Forge drew these by <b>permuting the four
     * texture coordinates</b> of an axis-aligned quad — the rectangle stayed
     * put and the art turned inside it.
     * <p>
     * That cannot be expressed here: the extractor's blit takes a UV
     * <em>window</em> ({@code u0, u1, v0, v1}), which is axis-aligned by
     * construction and has no way to say "this corner maps to that one". So the
     * quad is turned instead, about its own centre.
     * <p>
     * <b>The two are the same picture, and it is worth being precise about
     * why.</b> Turning a quad and permuting its UVs agree exactly when the
     * region is square — otherwise a quarter turn would swap the rectangle's
     * width and height and it would no longer cover the same pixels. Every
     * caller that asks for a quarter or three-quarter turn goes through
     * {@code renderDuelCardCentered}, which squares the region first
     * ({@code x -= (height - width) / 2; width = height;}) precisely so the
     * sideways card still fits its zone. So the condition holds wherever it
     * matters, and a half turn is safe regardless.
     *
     * @param quarterTurns 1, 2 or 3; the same three cases Forge had methods for
     */
    public static void fullBlitTurned(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height, int quarterTurns, int tint)
    {
        int turns = Math.floorMod(quarterTurns, 4);
        if(turns == 0)
        {
            fullBlit(graphics, texture, x, y, width, height, tint);
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().rotateAbout((float)(Math.PI / 2D) * turns,
            x + width / 2F, y + height / 2F);
        fullBlit(graphics, texture, x, y, width, height, tint);
        graphics.pose().popMatrix();
    }

    /** A quarter turn, which is a card lying on its side. */
    public static void fullBlit90Degree(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height, int tint)
    {
        fullBlitTurned(graphics, texture, x, y, width, height, 1, tint);
    }

    /** A half turn, which is a card on the far side of the field. */
    public static void fullBlit180Degree(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height, int tint)
    {
        fullBlitTurned(graphics, texture, x, y, width, height, 2, tint);
    }

    /** Both at once: the opponent's card, in defence. */
    public static void fullBlit270Degree(GuiGraphicsExtractor graphics, ResourceLocation texture,
        int x, int y, int width, int height, int tint)
    {
        fullBlitTurned(graphics, texture, x, y, width, height, 3, tint);
    }

    /**
     * How a card is drawn, so a caller can pass "sideways" around as a value.
     * <p>
     * The Forge interface took a {@code PoseStack}; this takes the extractor and
     * the texture, because a texture is an argument to a draw now rather than
     * something bound before one.
     */
    public interface FullBlitMethod
    {
        void fullBlit(GuiGraphicsExtractor graphics, ResourceLocation texture,
            int x, int y, int width, int height, int tint);
    }

    /**
     * An ARGB colour from a white-multiplied alpha, which is how the Forge code
     * expressed "the same picture, fainter".
     */
    public static int alpha(float alpha)
    {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255F)));
        return (a << 24) | 0x00FFFFFF;
    }

    /** An ARGB colour from the red/green/blue/alpha the old shader colour took. */
    public static int tint(float red, float green, float blue, float alpha)
    {
        return (Math.round(alpha * 255F) << 24)
            | (Math.round(red * 255F) << 16)
            | (Math.round(green * 255F) << 8)
            | Math.round(blue * 255F);
    }
}
