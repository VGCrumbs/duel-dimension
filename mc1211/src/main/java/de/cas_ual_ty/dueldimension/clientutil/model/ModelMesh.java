package de.cas_ual_ty.dueldimension.clientutil.model;

import com.mojang.blaze3d.platform.NativeImage;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@link GlbModel} turned into something the renderer can hand to the GPU.
 * <p>
 * Everything expensive happens once, here, because the alternative is doing it
 * sixty times a second: the indices are expanded, the textures are decoded and
 * registered, and the render type is memoized.
 * <p>
 * <b>De-indexed on purpose, not by oversight.</b> Minecraft generates the index
 * buffer itself from the render type's topology and asserts that the submitted
 * mesh carries none of its own, so a shared vertex cannot be shared: every
 * triangle spells out its three corners. For Curse of Dragon that turns 3,435
 * vertices into 14,958. That is the real cost of drawing a model this way, and
 * it is paid in vertex writes rather than in maths.
 * <p>
 * <b>The render type must be memoized</b>, which is the one thing here that is
 * silent when got wrong. Consecutive draws are merged by comparing render types
 * by REFERENCE, so building a fresh one per frame does not fail — it quietly
 * stops any batching and issues a separate draw call per submission. Reference
 * identity decides more than batching, too: it is also how a shader mod decides
 * which program to draw this with. See {@link #typeFor}.
 */
public final class ModelMesh
{
    /**
     * One render type per texture, kept forever.
     * <p>
     * Not a cache with a policy: there are as many entries as there are model
     * textures in the game, which is a handful, and dropping one would only
     * mean rebuilding an object whose identity is the thing that matters.
     */
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    /** The same, for the blended pipeline a fading monster is drawn by. */
    private static final Map<ResourceLocation, RenderType> BLENDED = new HashMap<>();

    /**
     * One drawable run of triangles, already flattened.
     *
     * @param joints  four bone indices per vertex, or null if this part is not
     *                skinned — a model may mix the two, and a part with no bones
     *                is drawn where it was modelled
     * @param weights how much each of those four bones moves the vertex
     */
    public record Part(float[] positions, float[] normals, float[] uvs,
        int[] joints, float[] weights, ResourceLocation texture)
    {
        public int vertexCount()
        {
            return positions.length / 3;
        }

        public boolean skinned()
        {
            return joints != null && weights != null;
        }
    }

    private final List<Part> parts;
    private final float[] min;
    private final float[] max;
    private final List<String> animationNames;
    private final ModelSkeleton skeleton;

    private ModelMesh(List<Part> parts, float[] min, float[] max, List<String> animationNames,
        ModelSkeleton skeleton)
    {
        this.parts = parts;
        this.min = min;
        this.max = max;
        this.animationNames = animationNames;
        this.skeleton = skeleton;
    }

    /**
     * What the file calls its animations, in file order.
     * <p>
     * They are rarely meaningful: an export from a console game names them after
     * the slots it loaded them into, so {@code slot_4} is as much as the file
     * will say about what it contains. Which is why the editor lets a duellist
     * cycle them and watch, rather than picking one by name.
     */
    public List<String> animationNames()
    {
        return animationNames;
    }

    /** The bones, or null for a model that has none. */
    public ModelSkeleton skeleton()
    {
        return skeleton;
    }

    /** The index of the named animation, or -1 for the rest pose. */
    public int animationIndex(String name)
    {
        return skeleton == null ? -1 : skeleton.indexOf(name);
    }

    public List<Part> parts()
    {
        return parts;
    }

    /** How tall the model is in its own units, in its rest pose. */
    public float modelHeight()
    {
        return max[1] - min[1];
    }

    /**
     * The point that should sit on the card: the middle of the model's
     * footprint, at the bottom of it.
     * <p>
     * Not the model's origin, which is wherever the artist left it — for this
     * dragon it is a third of the way up, so anchoring by origin would bury half
     * of it in the mat.
     */
    public float[] footOffset()
    {
        return new float[] {(min[0] + max[0]) / 2F, min[1], (min[2] + max[2]) / 2F};
    }

    /**
     * The render type for a part, built once per texture.
     * <p>
     * <b>Triangles, and Iris is told about them separately.</b> Vanilla's own
     * entity type would be routed to a shaderpack's entity program for free,
     * because shader mods key on pipeline identity — but every stock entity
     * pipeline pins QUADS, and a triangle mesh only fits that by repeating a
     * corner per face. Iris then computes ONE face normal per group of four
     * vertices and writes it over all four, so a model's smooth per-vertex
     * normals are flattened before the shader sees them and the creature renders
     * faceted. Its triangle path reads the supplied normal and leaves it alone.
     * <p>
     * So the topology is what decides whether a model can be smooth-shaded, and
     * being unknown to Iris is the thing to solve some other way — which
     * {@link IrisCompat} does, by naming this pipeline through Iris's own API.
     * That gets both: the pack's entity program AND the normals the artist
     * exported.
     *
     * @see #TYPES for why this may not be rebuilt per frame
     */
    public static RenderType typeFor(ResourceLocation texture)
    {
        return typeFor(texture, false);
    }

    /**
     * @param blend true for a monster part way through a fade; see
     *              {@code UnownedPipelines.MODEL_BLEND} for why a fade cannot be
     *              drawn by the ordinary alpha-tested pipeline
     */
    public static RenderType typeFor(ResourceLocation texture, boolean blend)
    {
        Map<ResourceLocation, RenderType> types = blend ? BLENDED : TYPES;
        // The stock types 26.2's mod pipelines were copies of. It built its own
        // in order to swap the fragment shader; nothing here needs that, so the
        // vanilla pair is the same recipe without the copy.
        // BOTH CULL. entityCutout does already; entityTranslucent does NOT --
        // it is the NO_CULL variant, and entityTranslucentCull is the one that
        // does. A monster is a closed solid, so its far side has no business
        // being visible through its near side, and drawing it was most of what
        // made a half-solid creature read as damaged rather than as translucent.
        //
        // It also halves the geometry that reaches the blend, which is the one
        // thing that helps an unsorted translucent draw: fewer overlapping
        // fragments, fewer places for the order to be wrong.
        return types.computeIfAbsent(texture,
            id -> blend ? RenderType.entityTranslucentCull(id) : RenderType.entityCutout(id));
    }

    /**
     * Bakes a loaded model.
     * <p>
     * Must run on the render thread: registering a texture reaches for the
     * graphics device, and only the decode before it is safe anywhere else.
     *
     * @param name a stable, unique name for this model, used to namespace the
     *             textures it registers
     */
    public static ModelMesh bake(GlbModel model, String name)
    {
        // Geometry first, textures last, and the order is the point. Registering
        // a texture is a side effect that cannot be taken back: a NativeImage
        // and its GPU upload are held by the texture manager for the session.
        // Everything below can still throw on a file GlbModel.load accepted --
        // an index past the end of a position accessor, a joint naming a node
        // that is not there -- and doing the irreversible half first meant a
        // model that failed to bake left its textures behind, four megabytes at
        // a time, with the failure cached so nothing would ever ask for them
        // again.
        List<Part> parts = new ArrayList<>();
        List<Integer> partImages = new ArrayList<>();

        for(GlbModel.Primitive primitive : model.primitives())
        {
            int[] indices = primitive.indices();
            float[] source = primitive.positions();
            float[] sourceNormals = primitive.normals();
            float[] sourceUvs = primitive.uvs();
            int[] sourceJoints = primitive.joints();
            float[] sourceWeights = primitive.weights();
            boolean skinned = sourceJoints != null && sourceWeights != null;

            float[] positions = new float[indices.length * 3];
            float[] normals = new float[indices.length * 3];
            float[] uvs = new float[indices.length * 2];
            // The bone bindings are de-indexed alongside the positions, because
            // a de-indexed vertex has no index left to look them up by. This is
            // the memory the skinning costs: four ints and four floats per
            // vertex, for 14,958 of them.
            int[] joints = skinned ? new int[indices.length * 4] : null;
            float[] weights = skinned ? new float[indices.length * 4] : null;

            for(int i = 0; i < indices.length; i++)
            {
                int vertex = indices[i];
                positions[i * 3] = source[vertex * 3];
                positions[i * 3 + 1] = source[vertex * 3 + 1];
                positions[i * 3 + 2] = source[vertex * 3 + 2];
                if(sourceNormals != null)
                {
                    normals[i * 3] = sourceNormals[vertex * 3];
                    normals[i * 3 + 1] = sourceNormals[vertex * 3 + 1];
                    normals[i * 3 + 2] = sourceNormals[vertex * 3 + 2];
                }
                else
                {
                    normals[i * 3 + 1] = 1F;
                }
                if(sourceUvs != null)
                {
                    uvs[i * 2] = sourceUvs[vertex * 2];
                    uvs[i * 2 + 1] = sourceUvs[vertex * 2 + 1];
                }
                if(skinned)
                {
                    for(int bone = 0; bone < 4; bone++)
                    {
                        joints[i * 4 + bone] = sourceJoints[vertex * 4 + bone];
                        weights[i * 4 + bone] = sourceWeights[vertex * 4 + bone];
                    }
                }
            }

            // material -> image, and not material -> texture. Those are
            // different index spaces; see GlbModel.materialImages. Resolved to
            // an ResourceLocation below, once the images have been registered.
            int material = primitive.material();
            int[] images = model.materialImages();
            int image = material >= 0 && material < images.length ? images[material] : -1;
            if(image < 0)
            {
                DuelDimension.warn("the model " + name + " has a part using material " + material
                    + ", which names no image this reader can decode;"
                    + " that part will not be drawn");
            }
            partImages.add(image);
            parts.add(new Part(positions, normals, uvs, joints, weights, null));
        }

        List<String> names = new ArrayList<>();
        for(int i = 0; i < model.animations().size(); i++)
        {
            String declared = model.animations().get(i).name();
            // An unnamed animation still has to be selectable, so it is called
            // after its index rather than left blank.
            names.add(declared == null || declared.isBlank() ? "anim " + i : declared);
        }

        ModelSkeleton skeleton = model.skin() == null ? null : new ModelSkeleton(model);
        float[][] bounds = standingBounds(parts, skeleton, model);

        // Nothing above this line has touched the graphics device. From here on
        // it is only side effects, so a model that was going to fail has already
        // failed and taken nothing with it.
        List<ResourceLocation> textures = register(model, name);
        for(int i = 0; i < parts.size(); i++)
        {
            int image = partImages.get(i);
            ResourceLocation texture = image >= 0 && image < textures.size()
                ? textures.get(image) : null;
            Part part = parts.get(i);
            // A new wrapper round the SAME arrays -- the geometry is not copied.
            parts.set(i, new Part(part.positions(), part.normals(), part.uvs(),
                part.joints(), part.weights(), texture));
        }
        return new ModelMesh(List.copyOf(parts), bounds[0], bounds[1], List.copyOf(names),
            skeleton);
    }

    /**
     * How many moments of the idle to measure the model at.
     * <p>
     * Enough to catch the bottom of a breath without paying for a full frame's
     * worth of skinning per sample. This runs once per model, at bake.
     */
    private static final int SAMPLES = 16;

    /**
     * How big the model is across the animation it will spend its life playing.
     * <p>
     * <b>Measured over the IDLE, not over the rest pose.</b> The rest pose is the
     * skeleton's authored TRS with no animation applied — and no monster is ever
     * drawn in it, because an unset animation now means slot_0. Anchoring to a
     * pose that is never shown put models into the mat by however far the two
     * disagree, which is not a rounding error: measured across the rips, Curse of
     * Dragon idles 11.7 units below its own rest pose, Blue-Eyes 8.0, and Skelgon
     * 25.4. Every one of those was standing buried.
     * <p>
     * Sampled ACROSS the cycle rather than at its first frame, because an idle is
     * a breath: the frame it starts on is not the lowest it reaches, and a model
     * anchored to the top of its breath dips into the card on every exhale.
     * <p>
     * Action animations may still go below this — a lunge crouches, a recoil
     * drops, and Kuriboh, which hovers, falls to the ground when struck. That is
     * authored motion rather than a misplacement, and flattening it would mean
     * either floating every monster by the depth of its deepest animation (a body
     * height, for Kuriboh) or re-anchoring per clip, which would make the monster
     * jump the instant it was hit.
     *
     * @return min, then max
     */
    private static float[][] standingBounds(List<Part> parts, ModelSkeleton skeleton,
        GlbModel model)
    {
        if(skeleton == null)
        {
            return new float[][] {model.min(), model.max()};
        }
        int idle = skeleton.idleIndex();
        float cycle = idle < 0 ? 0F : skeleton.animations().get(idle).duration();
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] point = new float[3];
        boolean any = false;

        int steps = cycle > 0F ? SAMPLES : 1;
        for(int sample = 0; sample < steps; sample++)
        {
            float[] matrices = ModelSkeleton.flatten(
                skeleton.pose(idle, cycle * sample / steps));
            for(Part part : parts)
            {
                float[] positions = part.positions();
                for(int i = 0; i < part.vertexCount(); i++)
                {
                    if(part.skinned())
                    {
                        ModelSkeleton.apply(matrices, part.joints(), part.weights(), i,
                            positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2],
                            true, point);
                    }
                    else
                    {
                        // Measured where it was authored, which is where the
                        // renderer draws it. Skipping these entirely would
                        // measure only part of the model while drawing all of
                        // it: a skinned dragon with an unskinned base plate
                        // would be scaled by the dragon's height and stood on
                        // the dragon's lowest bone, leaving the plate buried.
                        point[0] = positions[i * 3];
                        point[1] = positions[i * 3 + 1];
                        point[2] = positions[i * 3 + 2];
                    }
                    for(int axis = 0; axis < 3; axis++)
                    {
                        min[axis] = Math.min(min[axis], point[axis]);
                        max[axis] = Math.max(max[axis], point[axis]);
                    }
                    any = true;
                }
            }
        }
        return any ? new float[][] {min, max} : new float[][] {model.min(), model.max()};
    }

    /**
     * Decodes the embedded PNGs and hands them to the texture manager.
     * <p>
     * The image is NOT closed: a dynamic texture uploads from the pixels it
     * holds, so releasing them here would register an empty sheet. That is the
     * same ownership rule the sprite sheets follow.
     * <p>
     * A {@link Ps2Texture} rather than a plain one, so these are filtered the
     * way the hardware that drew them filtered — bilinear, where Minecraft's
     * own textures are point-sampled.
     */
    private static List<ResourceLocation> register(GlbModel model, String name)
    {
        List<ResourceLocation> out = new ArrayList<>();
        for(int i = 0; i < model.images().size(); i++)
        {
            byte[] png = model.images().get(i);
            if(png == null)
            {
                out.add(null);
                continue;
            }
            final String label = name + "_tex" + i;
            try(ByteArrayInputStream stream = new ByteArrayInputStream(png))
            {
                NativeImage image = NativeImage.read(stream);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
                    "model/" + label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
                Minecraft.getInstance().getTextureManager()
                    .register(id, new Ps2Texture(() -> label, image));
                out.add(id);
            }
            catch(Exception broken)
            {
                DuelDimension.warn("could not read texture " + i + " of the model "
                    + name + ": " + broken);
                out.add(null);
            }
        }
        return out;
    }
}
