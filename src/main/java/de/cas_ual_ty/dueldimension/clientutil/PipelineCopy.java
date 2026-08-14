package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A stock render pipeline rebuilt with one thing changed.
 * <p>
 * There is no {@code toBuilder}, but every property has a getter, so the
 * pipeline is read back and rebuilt. <b>Copying rather than writing one from
 * scratch matters</b>: the shaders, the vertex format, the bind group layouts
 * and the shader defines are what make a GUI blit a GUI blit and an entity
 * quad an entity quad, and guessing any of them would produce something that
 * draws, just not where or how the rest of the screen does. A copy also
 * inherits whatever vanilla grows next, where a transcription would silently
 * stay at whatever vanilla had on the day it was written.
 * <p>
 * Two overrides are offered because two callers need one each:
 * {@link FoilPipelines} changes the blend and keeps the shader,
 * {@link UnownedPipelines} changes the fragment shader and keeps the blend.
 */
final class PipelineCopy
{
    private PipelineCopy()
    {
    }

    /**
     * @param name            the mod-namespaced half of the new location; this
     *                        is also the name a compile failure is logged under
     * @param fragmentShader  replaces the base's, or null to keep it. Must be
     *                        an {@link Identifier} and not a string: the
     *                        builder's String overload runs
     *                        {@code Identifier.withDefaultNamespace}, so a mod
     *                        shader named that way is looked for under
     *                        {@code minecraft:} and never found
     * @param blend           replaces the base's colour target state, or null
     *                        to keep it
     */
    static RenderPipeline of(RenderPipeline base, String name,
        @Nullable Identifier fragmentShader, @Nullable BlendFunction blend)
    {
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
                "pipeline/" + name))
            .withVertexShader(base.getVertexShader())
            .withFragmentShader(fragmentShader == null ? base.getFragmentShader() : fragmentShader)
            .withCull(base.isCull())
            .withPolygonMode(base.getPolygonMode())
            .withPrimitiveTopology(base.getPrimitiveTopology())
            .withDepthStencilState(Optional.ofNullable(base.getDepthStencilState()));

        // Both bases have exactly one colour target, but the array is walked
        // rather than assumed: build() trims it to the count actually set, so
        // its length is the truth and a base that grows a second target copies
        // rather than silently losing it. A blend override replaces target 0,
        // which is where the single-argument withColorTargetState puts one.
        ColorTargetState[] targets = base.getColorTargetStates();
        for(int index = 0; index < targets.length; index++)
        {
            ColorTargetState target = index == 0 && blend != null
                ? new ColorTargetState(blend) : targets[index];
            if(target == null)
            {
                builder.withUnusedColorTargetState(index);
            }
            else
            {
                builder.withColorTargetState(index, target);
            }
        }

        for(int binding = 0; binding < base.getVertexFormatBindings().length; binding++)
        {
            builder.withVertexBinding(binding, base.getVertexFormatBinding(binding));
        }
        // Order is the bind group index, so this has to walk the list rather
        // than name the layouts it expects to find.
        for(BindGroupLayout layout : base.getBindGroupLayouts())
        {
            builder.withBindGroupLayout(layout);
        }

        // flags() are the bare defines and values() the name=value ones. Both
        // decide which #ifdef branches the shader compiles at all, so a copy
        // that dropped either would be a different shader wearing the same
        // source file -- BREEZE_WIND alone carries ALPHA_CUTOUT 0.1,
        // APPLY_TEXTURE_MATRIX, NO_OVERLAY and NO_CARDINAL_LIGHTING.
        base.getShaderDefines().flags().forEach(builder::withShaderDefine);
        base.getShaderDefines().values().forEach((key, value) ->
            copyValueDefine(builder, name, key, value));

        return builder.build();
    }

    /**
     * One {@code name=value} define, re-typed.
     * <p>
     * {@code ShaderDefines} holds every value as a string, and the builder only
     * takes int or float — so the type has to be recovered by parsing. That
     * round-trips exactly, because the builder wrote the string with
     * {@code String.valueOf} in the first place. A value that is neither is
     * reported rather than dropped: a missing define is not a missing tweak, it
     * is a different {@code #ifdef} branch.
     */
    private static void copyValueDefine(RenderPipeline.Builder builder, String name,
        String key, String value)
    {
        try
        {
            builder.withShaderDefine(key, Integer.parseInt(value));
            return;
        }
        catch(NumberFormatException notAnInt)
        {
            // A float, or not a number at all. Both are handled below.
        }
        try
        {
            builder.withShaderDefine(key, Float.parseFloat(value));
        }
        catch(NumberFormatException notANumber)
        {
            DuelDimension.warn("Pipeline " + name + " cannot copy the define "
                + key + '=' + value + ", which is neither an int nor a float;"
                + " the copy is not the pipeline it was copied from.");
        }
    }
}
