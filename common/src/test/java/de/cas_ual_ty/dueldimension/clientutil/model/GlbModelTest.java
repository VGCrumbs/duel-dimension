package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The glTF reader, against numbers taken from an independent implementation.
 * <p>
 * A loader like this is arithmetic over byte offsets, and arithmetic that is
 * wrong does not throw — it produces a mesh that merely looks badly exported. So
 * the figures below come from a separate Python reader (glb_truth.py) rather
 * than from this code, and they are asserted exactly.
 * <p>
 * The parts that need no file are always run: the container checks, and the
 * strided read that is the easiest thing in the format to get quietly wrong.
 */
class GlbModelTest
{
    /**
     * Where the model is, and whether it is here.
     * <p>
     * Both live in {@link GlbFixtures} now rather than in this class. They were
     * package-visible fields here, which a sibling test in ANOTHER module cannot
     * see once the tree is split by Minecraft version -- a test source set is
     * not shared by a project dependency.
     */
    static final Path DRAGON = GlbFixtures.DRAGON;

    static boolean dragonPresent()
    {
        return GlbFixtures.dragonPresent();
    }

    // ---- the model, where it is available ----

    @Test
    @EnabledIf("dragonPresent")
    void followsMaterialThroughTextureToImage() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));

        // Ground truth from the file itself: four materials, each naming one
        // texture, each naming one image, 0->0 1->1 2->2 3->3.
        //
        // That identity is exactly why this needs a test rather than an eyeball.
        // The code used to skip the chain and index the images with the material
        // number, which is right for this file by coincidence and wrong for any
        // export where several materials share an atlas -- and being wrong there
        // costs a monster two thirds of its triangles, silently.
        assertArrayEquals(new int[] {0, 1, 2, 3}, model.materialImages());
        assertEquals(4, model.images().size());
        for(GlbModel.Primitive primitive : model.primitives())
        {
            int material = primitive.material();
            assertTrue(material >= 0 && material < model.materialImages().length,
                "primitive names material " + material);
            int image = model.materialImages()[material];
            assertTrue(image >= 0 && image < model.images().size(),
                "material " + material + " resolves to image " + image);
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void readsEveryPrimitive() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));

        assertEquals(4, model.primitives().size());
        int[] verts = {1153, 1760, 448, 74};
        int[] tris = {1722, 2762, 416, 86};
        for(int i = 0; i < verts.length; i++)
        {
            GlbModel.Primitive primitive = model.primitives().get(i);
            assertEquals(verts[i], primitive.vertexCount(), "vertex count of primitive " + i);
            assertEquals(tris[i], primitive.triangleCount(), "triangle count of primitive " + i);
            assertEquals(i, primitive.material(), "material of primitive " + i);
            // Skinned: four bone indices and four weights for every vertex.
            assertEquals(primitive.vertexCount() * 4, primitive.joints().length);
            assertEquals(primitive.vertexCount() * 4, primitive.weights().length);
            assertEquals(primitive.vertexCount() * 3, primitive.normals().length);
            assertEquals(primitive.vertexCount() * 2, primitive.uvs().length);
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void boundsMatchTheIndependentReader() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));
        assertArrayEquals(new float[] {-22.4228F, -33.5991F, -13.5456F}, model.min(), 1e-3F);
        assertArrayEquals(new float[] {21.8475F, 9.7735F, 24.8935F}, model.max(), 1e-3F);
        // The reason a height setting is not optional: the model is ~43 units
        // tall, and a Minecraft unit is a block.
        assertEquals(43.3726F, model.modelHeight(), 1e-3F);
    }

    @Test
    @EnabledIf("dragonPresent")
    void readsTheSkeletonAndItsAnimations() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));

        assertEquals(74, model.nodes().size());
        assertNotNull(model.skin());
        assertEquals(33, model.skin().jointCount());
        // One mat4 per joint.
        assertEquals(33 * 16, model.skin().inverseBindMatrices().length);

        assertEquals(5, model.animations().size());
        String[] names = {"slot_0", "slot_2", "slot_4", "slot_5", "slot_6"};
        float[] durations = {2.0F, 8.8333F, 3.0F, 4.6667F, 0.6667F};
        int[] channels = {31, 31, 30, 29, 31};
        for(int i = 0; i < names.length; i++)
        {
            GlbModel.Animation animation = model.animations().get(i);
            assertEquals(names[i], animation.name());
            assertEquals(durations[i], animation.duration(), 1e-3F);
            assertEquals(channels[i], animation.channels().size());
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void readsTheEmbeddedTextures() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));
        assertEquals(4, model.images().size());
        int[] sizes = {26596, 28483, 8432, 6159};
        for(int i = 0; i < sizes.length; i++)
        {
            byte[] png = model.images().get(i);
            assertNotNull(png, "image " + i);
            assertEquals(sizes[i], png.length);
            // A PNG signature, so the bytes are the image and not the view
            // beside it -- an off-by-one buffer view is otherwise invisible.
            assertEquals((byte)0x89, png[0]);
            assertEquals('P', png[1]);
            assertEquals('N', png[2]);
            assertEquals('G', png[3]);
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void everyJointIndexAddressesARealBone() throws IOException
    {
        // A joint index read as a signed byte comes out negative above 127, and
        // the mesh then skins against a bone that does not exist.
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));
        int bones = model.skin().jointCount();
        for(GlbModel.Primitive primitive : model.primitives())
        {
            for(int joint : primitive.joints())
            {
                assertTrue(joint >= 0 && joint < bones, "joint index " + joint + " of " + bones);
            }
        }
    }

    @Test
    @EnabledIf("dragonPresent")
    void everyIndexAddressesARealVertex() throws IOException
    {
        GlbModel model = GlbModel.load(Files.readAllBytes(DRAGON));
        for(GlbModel.Primitive primitive : model.primitives())
        {
            for(int index : primitive.indices())
            {
                assertTrue(index >= 0 && index < primitive.vertexCount(),
                    "index " + index + " of " + primitive.vertexCount());
            }
        }
    }

    // ---- container handling, which needs no file ----

    @Test
    void rejectsSomethingThatIsNotAGlb()
    {
        byte[] nonsense = "this is not a model".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> GlbModel.load(nonsense));
    }

    @Test
    void rejectsAFutureGltfVersion()
    {
        assertThrows(IllegalArgumentException.class,
            () -> GlbModel.load(glb(3, "{}", new byte[0])));
    }

    @Test
    void readsAnInterleavedBufferView() throws IOException
    {
        // byteStride is the one part of the format that produces plausible
        // numbers when ignored: the first vertex reads correctly and every
        // vertex after it is wrong. Two vec3s interleaved in one view, with the
        // accessor reading only the second, is the smallest case that catches it.
        float[] packed = {
            1F, 2F, 3F, /* wanted */ 10F, 20F, 30F,
            4F, 5F, 6F, /* wanted */ 40F, 50F, 60F,
        };
        ByteBuffer raw = ByteBuffer.allocate(packed.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for(float value : packed)
        {
            raw.putFloat(value);
        }

        String json = """
            {"meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0}]}],
             "accessors":[
               {"bufferView":0,"byteOffset":12,"componentType":5126,"count":2,"type":"VEC3"},
               {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}],
             "bufferViews":[
               {"buffer":0,"byteOffset":0,"byteLength":48,"byteStride":24},
               {"buffer":0,"byteOffset":48,"byteLength":6}]}
            """;

        ByteArrayOutputStream bin = new ByteArrayOutputStream();
        bin.write(raw.array());
        ByteBuffer indices = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        indices.putShort((short)0).putShort((short)1).putShort((short)0);
        bin.write(indices.array());

        GlbModel model = GlbModel.load(glb(2, json, bin.toByteArray()));
        assertArrayEquals(new float[] {10F, 20F, 30F, 40F, 50F, 60F},
            model.primitives().get(0).positions(), 1e-6F);
    }

    /** Wraps JSON and BIN into a .glb container. */
    private static byte[] glb(int version, String json, byte[] bin) throws IOException
    {
        byte[] text = json.getBytes(StandardCharsets.UTF_8);
        int jsonPad = (4 - text.length % 4) % 4;
        int binPad = (4 - bin.length % 4) % 4;
        int total = 12 + 8 + text.length + jsonPad + (bin.length == 0 ? 0 : 8 + bin.length + binPad);

        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546C67).putInt(version).putInt(total);
        out.putInt(text.length + jsonPad).putInt(0x4E4F534A);
        out.put(text);
        for(int i = 0; i < jsonPad; i++)
        {
            out.put((byte)' ');
        }
        if(bin.length > 0)
        {
            out.putInt(bin.length + binPad).putInt(0x004E4942);
            out.put(bin);
            for(int i = 0; i < binPad; i++)
            {
                out.put((byte)0);
            }
        }
        return out.array();
    }

    // ------------------------------------------------- node scale tolerance --

    /**
     * The exact scales that were in the shipped Falsebound rips, measured out
     * of the files after all three failed to load. The old tolerance was 1e-6
     * and every one of these is larger than that, which is why Slifer, Obelisk
     * and Ra all came back as "apply scale before exporting" -- for a scale
     * nobody had applied, produced by float32 coming out of a decomposition.
     */
    @Test
    void float_noise_from_an_exporter_counts_as_unit_scale()
    {
        float[][] measured = {
            {1F, 1.0000054836273193F, 1F},      // Slifer, j015
            {1F, 1F, 1.0000022649765015F},      // Obelisk, j107
            {1.000032901763916F, 1F, 1F},       // Ra, j019 -- the worst seen
        };
        for(float[] scale : measured)
        {
            assertTrue(GlbModel.isUnitScale(scale),
                "rejected " + java.util.Arrays.toString(scale));
        }
    }

    /**
     * And the point of keeping the check at all. These are the scales the
     * Falsebound skeletons genuinely carry on their joints, and a model whose
     * nodes are scaled would be drawn at the wrong size with nothing to say so.
     */
    @Test
    void a_scale_somebody_meant_is_still_refused()
    {
        assertFalse(GlbModel.isUnitScale(new float[] {1.4F, 1.4F, 1.4F}), "Slifer's root");
        assertFalse(GlbModel.isUnitScale(new float[] {2F, 2F, 2F}), "Obelisk's root");
        assertFalse(GlbModel.isUnitScale(new float[] {10F, 10F, 10F}), "Ra's joints");
        assertFalse(GlbModel.isUnitScale(new float[] {1F, 0.5F, 1F}), "one axis is enough");
    }

    /** There is a lot of room between the noise and anything deliberate. */
    @Test
    void the_tolerance_sits_well_clear_of_both()
    {
        assertTrue(GlbModel.isUnitScale(new float[] {1.0009F, 1F, 1F}), "inside");
        assertFalse(GlbModel.isUnitScale(new float[] {1.002F, 1F, 1F}), "outside");
    }

    static boolean godsPresent()
    {
        return GlbFixtures.godsPresent();
    }

    /**
     * The three that were failing, loaded for real.
     * <p>
     * The tolerance test above proves the arithmetic; this proves there was
     * nothing else wrong with them. A loader that rejects on the FIRST thing it
     * dislikes cannot tell you about the second, so widening one check and
     * declaring the model loadable is a guess until the file goes through.
     */
    @Test
    @EnabledIf("godsPresent")
    void the_falsebound_rips_load() throws IOException
    {
        for(String name : new String[] {"slifer_the_sky_dragon", "obelisk_the_tormentor",
            "the_winged_dragon_of_ra"})
        {
            GlbModel model = GlbModel.load(Files.readAllBytes(GlbFixtures.god(name)));
            assertEquals(1, model.primitives().size(), name);
            assertNotNull(model.skin(), name);
            assertTrue(model.modelHeight() > 0F, name + " has no height");
            assertTrue(model.animations().size() >= 4, name + " lost its animations");
        }
    }
}
