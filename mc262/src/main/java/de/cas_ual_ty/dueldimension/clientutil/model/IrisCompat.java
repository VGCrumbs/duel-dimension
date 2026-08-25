package de.cas_ual_ty.dueldimension.clientutil.model;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Tells Iris that the models are entities, so it draws them with the pack's
 * entity program.
 * <p>
 * <b>Why this exists at all.</b> Iris maps a render pipeline to a shader program
 * by OBJECT IDENTITY — its lookup is a map keyed on the vanilla
 * {@code RenderPipelines} instances themselves, and {@code RenderPipeline}
 * declares no {@code equals}. A mod's pipeline, however faithful a copy, is
 * simply absent from that map and gets no program:
 * <pre>
 * [ERROR]: Missing program dueldimension:pipeline/model_triangles in override
 *          list. This is not a critical problem, but it could lead to weird
 *          rendering.
 * </pre>
 * <p>
 * <b>Why not just use vanilla's entity type.</b> That was tried, and it works —
 * but every stock entity pipeline pins QUADS, and a triangle mesh only fits that
 * by repeating a corner. Iris computes ONE face normal per group of four
 * vertices and writes it over all four, so any QUADS geometry is flattened
 * before the shader ever sees it: the smooth per-vertex normals a model ships
 * are discarded and it renders faceted. Its triangle path does the opposite —
 * it reads the supplied normal and leaves it alone. So the topology decides
 * whether a model can be smooth-shaded at all, and being routed by identity is
 * the thing to solve some other way. This is that way.
 * <p>
 * Reflection because Iris is optional, following {@code SkinLayersCompat}. With
 * no Iris installed this is a no-op and the models draw unshaded exactly as they
 * always did.
 */
public final class IrisCompat
{
    /**
     * Every pipeline already claimed. Iris throws if one is assigned twice, and
     * there is more than one of these now -- the solid models and the blended
     * ones they fade through.
     */
    private static final java.util.Set<RenderPipeline> ASSIGNED =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private IrisCompat()
    {
    }

    /**
     * Claims a pipeline as entity geometry.
     * <p>
     * Once per pipeline, ever. Iris throws if a pipeline is assigned twice, and
     * this is called from client init, which is not a place to be throwing.
     */
    public static void assign(RenderPipeline pipeline)
    {
        if(pipeline == null || !FabricLoader.getInstance().isModLoaded("iris")
            || !ASSIGNED.add(pipeline))
        {
            return;
        }
        try
        {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method assign = api.getMethod("assignPipeline", RenderPipeline.class, program);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object entities = Enum.valueOf((Class<? extends Enum>)program.asSubclass(Enum.class),
                "ENTITIES");
            assign.invoke(instance, pipeline, entities);
            DuelDimension.log("iris: models assigned to the entity program");
        }
        catch(Throwable unavailable)
        {
            // Named rather than swallowed, because the visible symptom of this
            // failing is subtle -- the models simply go unlit under a pack --
            // and the log line is the only thing that would point at it.
            DuelDimension.warn("could not tell iris about the model pipeline;"
                + " models will not be shaded by a shaderpack: " + unavailable);
        }
    }
}
