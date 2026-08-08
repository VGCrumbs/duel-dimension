package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.pipeline.BlendFunction;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The two blends behind a foil card's glint.
 * <p>
 * A rare card catches the light as the cursor passes over it. Forge produced
 * that with two draws and the framebuffer's alpha channel as scratch space:
 * <ol>
 * <li>draw a soft radial mask at the cursor with a blend that writes
 *     <em>only</em> alpha and leaves colour alone, so nothing appears but the
 *     alpha channel now holds "how lit is this pixel";</li>
 * <li>draw the foil with a blend that shows it in proportion to that alpha.</li>
 * </ol>
 * {@code RenderSystem.blendFuncSeparate} is gone, but the blend itself is not:
 * it moved onto the pipeline. These are {@code GUI_TEXTURED} rebuilt with the
 * two blend functions Forge asked for, factor for factor.
 * <p>
 * <b>Whether this works is an open question, and it is about batching, not
 * about blending.</b> The second draw has to see what the first wrote, and a
 * retained-mode GUI is free to reorder or merge draws. {@code FoilTestScreen}
 * exists to answer that before the duel screen is built on it.
 */
public final class FoilPipelines
{
    /**
     * Writes alpha, not colour.
     * <p>
     * {@code (ZERO, ONE)} for colour: the source contributes nothing and the
     * destination is kept exactly, so the screen does not change. {@code
     * (SRC_ALPHA, ZERO)} for alpha: the destination's alpha is replaced by the
     * mask's. Straight from the Forge call.
     */
    public static final RenderPipeline MASK = copyOfGuiTextured("foil_mask",
        new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE,
            BlendFactor.SRC_ALPHA, BlendFactor.ZERO));

    /** Shows the foil where that alpha is high. */
    public static final RenderPipeline FOIL = copyOfGuiTextured("foil",
        new BlendFunction(BlendFactor.ONE_MINUS_DST_ALPHA, BlendFactor.DST_COLOR,
            BlendFactor.DST_ALPHA, BlendFactor.ONE_MINUS_DST_ALPHA));

    /** The inverted variant, for rarity layers that mask the other way round. */
    public static final RenderPipeline FOIL_INVERTED = copyOfGuiTextured("foil_inverted",
        new BlendFunction(BlendFactor.DST_ALPHA, BlendFactor.DST_COLOR,
            BlendFactor.ONE_MINUS_DST_ALPHA, BlendFactor.DST_ALPHA));

    /**
     * The fallback: a plain additive glint that never reads the framebuffer.
     * <p>
     * If batching turns out to break the two-pass trick, this is what the foil
     * becomes -- brighter where the foil texture is bright, with no cursor
     * tracking. Visibly close, and it degrades a cosmetic rather than a rule.
     */
    public static final RenderPipeline ADDITIVE = copyOfGuiTextured("foil_additive",
        BlendFunction.ADDITIVE);

    private FoilPipelines()
    {
    }

    /**
     * {@code GUI_TEXTURED} with a different blend and nothing else changed.
     * <p>
     * There is no {@code toBuilder}, but every property has a getter, so the
     * pipeline is read back and rebuilt. Copying rather than writing one from
     * scratch matters: the shaders, the vertex format and the shader defines
     * are what make a GUI blit a GUI blit, and guessing any of them would
     * produce something that draws, just not where or how the rest of the
     * screen does.
     */
    private static RenderPipeline copyOfGuiTextured(String name, BlendFunction blend)
    {
        RenderPipeline base = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
                "pipeline/" + name))
            .withVertexShader(base.getVertexShader())
            .withFragmentShader(base.getFragmentShader())
            .withCull(base.isCull())
            .withPolygonMode(base.getPolygonMode())
            .withPrimitiveTopology(base.getPrimitiveTopology())
            .withDepthStencilState(java.util.Optional.ofNullable(base.getDepthStencilState()))
            .withColorTargetState(new ColorTargetState(blend));

        for(int binding = 0; binding < base.getVertexFormatBindings().length; binding++)
        {
            builder.withVertexBinding(binding, base.getVertexFormatBinding(binding));
        }
        for(com.mojang.blaze3d.pipeline.BindGroupLayout layout : base.getBindGroupLayouts())
        {
            builder.withBindGroupLayout(layout);
        }
        // flags() are the bare defines -- GUI_TEXTURED's is IS_GUI, and it is
        // what tells the shader it is drawing in screen space rather than in
        // the world. Losing it would put the quad somewhere else entirely.
        base.getShaderDefines().flags().forEach(builder::withShaderDefine);

        // values() are name=value defines. The builder only accepts int and
        // float values, so a string-valued one cannot be copied faithfully.
        // GUI_TEXTURED has none; if that ever changes, this says so rather than
        // guessing a type and producing a pipeline that is subtly not the one
        // it was copied from.
        if(!base.getShaderDefines().values().isEmpty())
        {
            DuelDimension.warn("GUI_TEXTURED now carries value defines "
                + base.getShaderDefines().values().keySet()
                + " which " + name + " cannot copy; the foil blend may render wrong.");
        }

        return builder.build();
    }
}
