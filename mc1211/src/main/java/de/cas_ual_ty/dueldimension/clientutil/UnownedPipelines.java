package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * How a card the player does not own is drawn: dimmed, on this version.
 *
 * <h2>What this is on 26.2, and why 1.21.1 cannot have it</h2>
 *
 * On 26.2 this class builds four {@code RenderPipeline}s — copies of the stock
 * GUI, mesh and entity recipes with a desaturating fragment shader swapped in —
 * so an unowned card is drawn GREY by the draw itself rather than by a second
 * image of it.
 * <p>
 * 1.21.1 has no such thing. Its {@code com.mojang.blaze3d.pipeline.RenderPipeline}
 * is an unrelated class (a render-call queue), there is no builder, no
 * {@code ColorTargetState}, no {@code ShaderDefines}, and no way to supply a
 * fragment shader to a {@code RenderType} without shipping a core shader and
 * a resource-pack override. All of that was checked against the jar this module
 * compiles against, not assumed.
 *
 * <h2>So it takes the fallback the mod already had</h2>
 *
 * <b>Nothing here is invented.</b> 26.2 already has to cope with the shader not
 * compiling — a bad driver, a resource pack shadowing it — and its answer is
 * this class's own {@link #DIM_RED} and friends: draw through the stock pipeline
 * with a cool multiply in the tint. Its words, kept because they are still true:
 * <blockquote>Dimmer and cooler than the cards beside it, unmistakably "not
 * yours" at thumbnail size, and no second image. It must not be called
 * desaturation anywhere: a multiply leaves HSV saturation exactly where it
 * found it.</blockquote>
 * <p>
 * {@link #available()} therefore returns false here, permanently and by
 * construction rather than by failure, and every caller takes the branch it
 * already had for that case. The look degrades exactly as it degrades on 26.2
 * with an unhappy driver — which is a known, designed-for state, not a new one.
 * <p>
 * If greying matters enough later, the route is a core shader plus a
 * {@code RenderType} built on it, which is what the 1.19.2 tree does through
 * {@code DdBlitUtil.advancedMaskedBlit} and {@code RenderSystem.blendFuncSeparate}.
 * That is a feature to add deliberately, not part of making the port compile.
 */
public final class UnownedPipelines
{
    /**
     * The multiply that says "not yours".
     * <p>
     * Blue kept slightly higher than red and green, so the result reads as cool
     * rather than merely dark. Same numbers as 26.2 — this is the one part of
     * the class that was always version-independent arithmetic.
     */
    public static final float DIM_RED = 0.62F;
    public static final float DIM_GREEN = 0.62F;
    public static final float DIM_BLUE = 0.68F;

    private UnownedPipelines()
    {
    }

    /**
     * Always false on 1.21.1: there is no desaturating pipeline to be available.
     * <p>
     * Kept rather than removed, and kept as a method rather than a constant, so
     * every call site keeps the shape it has on 26.2 and the two trees stay
     * readable side by side. A caller that asks and takes the dim branch is
     * correct on both versions.
     */
    public static boolean available()
    {
        return false;
    }

    /**
     * Nothing to compile, so nothing to refresh.
     * <p>
     * A no-op rather than a removed method: it is called from three places on a
     * resource reload, and those call sites are right on both versions.
     */
    public static void refresh()
    {
    }

    /**
     * The card mesh type — undesaturated, because that is all this version has.
     * <p>
     * {@code RenderType.breezeWind(texture, 0, 0)} is exactly what 26.2's
     * desaturating {@code mesh} is a copy of, minus the shader swap. So a caller
     * asking for the greyed type gets the ordinary one and the card draws in
     * full colour; the dim comes from the tint instead, at the call sites that
     * ask {@link #available()} first.
     * <p>
     * <b>Known gap.</b> {@code FieldQuad.draw}'s {@code desaturate} parameter
     * reaches here WITHOUT asking {@link #available()}, so on the 3D board an
     * unowned card is neither greyed nor dimmed — it looks owned. That is one
     * call site, it only affects the card preview on the world board, and
     * fixing it means threading the dim through {@code FieldQuad} rather than
     * anything about this class. Written down rather than quietly accepted.
     */
    public static RenderType mesh(ResourceLocation texture)
    {
        return RenderType.breezeWind(texture, 0F, 0F);
    }

    /** {@link #DIM_RED} and friends multiplied into an ARGB tint, alpha kept. */
    public static int dimmed(int argb)
    {
        int alpha = argb >>> 24;
        int red = Math.round((argb >>> 16 & 0xFF) * DIM_RED);
        int green = Math.round((argb >>> 8 & 0xFF) * DIM_GREEN);
        int blue = Math.round((argb & 0xFF) * DIM_BLUE);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
