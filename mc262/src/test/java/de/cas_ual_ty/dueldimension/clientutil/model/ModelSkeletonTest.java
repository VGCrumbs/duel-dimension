package de.cas_ual_ty.dueldimension.clientutil.model;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Posing and skinning, against numbers from an independent implementation.
 * <p>
 * Same reasoning as {@link GlbModelTest}, one step further along: the loader's
 * numbers are only worth having if what is done WITH them is right, and every
 * step here has a plausible wrong version that still draws a dragon. Column-major
 * read as row-major, {@code w} read first instead of last, the inverse bind
 * multiply the wrong way round, children resolved before parents — each produces
 * a mesh made of triangles, roughly dragon-sized, and wrong. Only figures tell
 * them apart, and these come from {@code skin_truth.py}, which was written from
 * the spec rather than from this code.
 */
class ModelSkeletonTest
{
    private static final Path DRAGON = dragon();

    private static Path dragon()
    {
        String override = System.getProperty("dueldimension.testModel");
        if(override != null && !override.isBlank())
        {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), "AppData", "Roaming", "ModrinthApp",
            "profiles", "Duel", "config", "dueldimension", "models", "curse_of_dragon.glb");
    }

    static boolean dragonPresent()
    {
        return Files.isRegularFile(DRAGON);
    }

    private static ModelSkeleton skeleton() throws IOException
    {
        return new ModelSkeleton(GlbModel.load(Files.readAllBytes(DRAGON)));
    }

    @Test
    @EnabledIf("dragonPresent")
    void restPoseIsTheBindPose() throws IOException
    {
        // For this model the two agree, so every joint matrix at rest is the
        // identity. That is worth pinning rather than assuming: it is what makes
        // "the posed model looks like the unposed one" mean the maths is right
        // instead of meaning nothing ran. It is ALSO the check that the inverse
        // bind multiply happens and happens the right way round — drop it and
        // these matrices become the bones' world transforms, which are anything
        // but identity.
        Matrix4f[] rest = skeleton().pose(-1, 0F);
        assertEquals(33, rest.length);
        Matrix4f identity = new Matrix4f();
        float[] posed = new float[16];
        float[] want = new float[16];
        identity.get(want, 0);
        for(int joint = 0; joint < rest.length; joint++)
        {
            rest[joint].get(posed, 0);
            for(int i = 0; i < 16; i++)
            {
                assertEquals(want[i], posed[i], 1e-4F,
                    "element " + i + " of joint " + joint + " at rest");
            }
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void posesEveryAnimationWhereTheReferenceDoes() throws IOException
    {
        ModelSkeleton skeleton = skeleton();
        // Sampled at 37% of each animation, which lands BETWEEN keyframes.
        // On a keyframe the interpolation could be deleted entirely and every
        // number here would still match.
        float[] at = {0.74F, 3.26833F, 1.11F, 1.72667F, 0.24667F};
        // joints 0, 1, 16 and 32 — the root, its first child, the middle of the
        // rig and the last bone, so a hierarchy resolved in the wrong order
        // cannot pass by getting the roots right.
        float[][][] want = {
            {{0F, 3.34248F, 0F}, {0F, 3.34355F, -0.00279F},
                {-0.96638F, 1.69888F, 1.43339F}, {0.00123F, 0.50841F, -12.80754F}},
            {{0F, 4.82549F, 9.15749F}, {0F, 4.82795F, 9.15088F},
                {2.69208F, 1.44703F, 7.02672F}, {0.00256F, -45.53026F, -17.55920F}},
            {{1.48676F, -8.62854F, -5.58135F}, {1.49111F, -8.61184F, -5.58942F},
                {5.28533F, -11.45302F, -11.52150F}, {-28.80994F, -3.87888F, -13.24792F}},
            {{1.86762F, 8.13853F, -0.10443F}, {1.86515F, 8.13887F, -0.10689F},
                {9.00378F, 4.71536F, -0.68398F}, {4.82868F, 6.74778F, 13.23918F}},
            {{0F, 0.36944F, -0.83698F}, {-0.00087F, 0.37176F, -0.85003F},
                {5.45659F, -12.61852F, 1.18747F}, {0.12057F, 6.59627F, -4.50038F}},
        };
        int[] joints = {0, 1, 16, 32};

        for(int animation = 0; animation < at.length; animation++)
        {
            float[] posed = ModelSkeleton.flatten(skeleton.pose(animation, at[animation]));
            for(int i = 0; i < joints.length; i++)
            {
                int m = joints[i] * 16;
                // The translation is the last column: elements 12, 13, 14 of a
                // column-major mat4, and not every fourth one.
                for(int axis = 0; axis < 3; axis++)
                {
                    assertEquals(want[animation][i][axis], posed[m + 12 + axis], 1e-3F,
                        "axis " + axis + " of joint " + joints[i] + " in animation " + animation);
                }
            }
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void skinsVerticesWhereTheReferenceDoes() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));
        // The FIRST primitive, un-de-indexed, because the reference indexes
        // vertices the way the file does and ModelMesh does not.
        GlbModel.Primitive primitive = model.primitives().get(0);
        float[] matrices = ModelSkeleton.flatten(new ModelSkeleton(model).pose(0, 0.74F));

        int[] which = {0, 100, 1000};
        float[][] want = {
            {0.53517F, -3.00884F, -1.25298F},
            {-4.83936F, 2.53893F, 2.87567F},
            {12.56008F, -1.41305F, 6.06033F},
        };
        float[] got = new float[3];
        for(int i = 0; i < which.length; i++)
        {
            int vertex = which[i];
            ModelSkeleton.apply(matrices, primitive.joints(), primitive.weights(), vertex,
                primitive.positions()[vertex * 3], primitive.positions()[vertex * 3 + 1],
                primitive.positions()[vertex * 3 + 2], true, got);
            for(int axis = 0; axis < 3; axis++)
            {
                assertEquals(want[i][axis], got[axis], 1e-3F,
                    "axis " + axis + " of vertex " + vertex);
            }
            // And the animation actually moved it, so a skinning pass that
            // quietly did nothing could not pass the assertions above by
            // matching positions it never touched.
            assertNotEquals(primitive.positions()[vertex * 3 + 1], got[1], 1e-3F);
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void aNormalDoesNotPickUpTheBonesTranslation() throws IOException
    {
        // The distinction the `translate` flag exists for. Joint 32 at this
        // moment sits nearly thirteen units from the origin, so a normal put
        // through the position path would come back that long instead of unit —
        // and would light the model as though every face pointed at that bone.
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));
        GlbModel.Primitive primitive = model.primitives().get(0);
        float[] matrices = ModelSkeleton.flatten(new ModelSkeleton(model).pose(0, 0.74F));

        float[] got = new float[3];
        for(int vertex : new int[] {0, 100, 1000, 1152})
        {
            ModelSkeleton.apply(matrices, primitive.joints(), primitive.weights(), vertex,
                primitive.normals()[vertex * 3], primitive.normals()[vertex * 3 + 1],
                primitive.normals()[vertex * 3 + 2], false, got);
            float length = (float)Math.sqrt(got[0] * got[0] + got[1] * got[1] + got[2] * got[2]);
            // Rotation preserves length; blending two bones' rotations shortens
            // it a little, which is inherent to linear blend skinning and is why
            // this is a range rather than an equality.
            assertTrue(length > 0.7F && length < 1.05F,
                "normal of vertex " + vertex + " came back " + length + " long");
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void namesResolveToTheirIndex() throws IOException
    {
        ModelSkeleton skeleton = skeleton();
        assertEquals(0, skeleton.indexOf("slot_0"));
        assertEquals(2, skeleton.indexOf("slot_4"));
        assertEquals(4, skeleton.indexOf("slot_6"));
        // Nothing chosen, and something that is no longer in the file, both mean
        // the rest pose rather than a wrong animation. A model re-exported with
        // its animations renamed must not silently play a different one.
        assertEquals(-1, skeleton.indexOf(null));
        assertEquals(-1, skeleton.indexOf(""));
        assertEquals(-1, skeleton.indexOf("slot_9"));
    }

    // ---- the arithmetic, which needs no file ----

    @Test
    void blendsByWeightAndNormalisesWhatDoesNotSumToOne()
    {
        // Two bones, one shifting +10 along x and one leaving the vertex alone,
        // at weights that sum to 0.5. Unnormalised this would come back at half
        // the distance from the origin -- the failure that reads as a model
        // that shrank rather than as weights that were wrong.
        float[] matrices = new float[32];
        new Matrix4f().translation(10F, 0F, 0F).get(matrices, 0);
        new Matrix4f().get(matrices, 16);

        float[] got = new float[3];
        ModelSkeleton.apply(matrices, new int[] {0, 1, 0, 0},
            new float[] {0.25F, 0.25F, 0F, 0F}, 0, 4F, 6F, 8F, true, got);
        assertEquals(9F, got[0], 1e-4F);
        assertEquals(6F, got[1], 1e-4F);
        assertEquals(8F, got[2], 1e-4F);
    }

    @Test
    void leavesAnUnweightedVertexWhereItWasModelled()
    {
        // Not at the origin: a stray vertex collapsed to 0,0,0 drags a triangle
        // across the whole model, which is far louder than one that simply does
        // not animate.
        float[] matrices = new float[16];
        new Matrix4f().translation(10F, 0F, 0F).get(matrices, 0);

        float[] got = new float[3];
        ModelSkeleton.apply(matrices, new int[] {0, 0, 0, 0},
            new float[] {0F, 0F, 0F, 0F}, 0, 4F, 6F, 8F, true, got);
        assertEquals(4F, got[0], 1e-4F);
        assertEquals(6F, got[1], 1e-4F);
        assertEquals(8F, got[2], 1e-4F);
    }

    @Test
    void ignoresABoneThatIsNotThere()
    {
        // A joint index past the end of the skin is a corrupt or mis-read file,
        // and the answer is a vertex left alone rather than an exception thrown
        // from inside a render pass sixty times a second.
        float[] matrices = new float[16];
        new Matrix4f().get(matrices, 0);

        float[] got = new float[3];
        ModelSkeleton.apply(matrices, new int[] {7, 0, 0, 0},
            new float[] {1F, 0F, 0F, 0F}, 0, 4F, 6F, 8F, true, got);
        assertEquals(4F, got[0], 1e-4F);
        assertEquals(6F, got[1], 1e-4F);
        assertEquals(8F, got[2], 1e-4F);
    }

    @Test
    void flattenIsColumnMajorWithTheTranslationLast()
    {
        // The layout every other assertion in this file depends on, asserted
        // once directly so that a JOML change could not move it silently.
        Matrix4f matrix = new Matrix4f().translation(1F, 2F, 3F);
        float[] flat = ModelSkeleton.flatten(new Matrix4f[] {new Matrix4f(), matrix});
        assertEquals(32, flat.length);
        assertEquals(1F, flat[16 + 12], 1e-6F);
        assertEquals(2F, flat[16 + 13], 1e-6F);
        assertEquals(3F, flat[16 + 14], 1e-6F);
        assertEquals(1F, flat[16 + 15], 1e-6F);
    }
}
