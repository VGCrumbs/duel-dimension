package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character models this mod ships, read by the loader that will draw them.
 * <p>
 * These are exported from the DS game by {@code NexusDecomp/scripts/export_glb.py}
 * and shipped in the jar, so unlike the monster models there is no install step
 * and no reason for this to skip on a clean machine.
 * <p>
 * <b>What it is really checking is the exporter</b>, which is written against
 * what {@link GlbModel} accepts rather than against the specification at large:
 * TRS nodes and no matrices, no node scale, LINEAR samplers, indexed triangles,
 * one skin. Every one of those is rejected loudly by the loader, so a mistake in
 * the exporter fails here rather than at the first frame drawn on a client that
 * only exists on one machine.
 */
class CharacterGlbTest
{
    /** Fifteen choices in each of four slots, one primitive apiece. */
    private static final int SLOTS = 4;
    private static final int PER_SLOT = 15;

    private static Path model(String gender)
    {
        // The tests run with `common/` as the working directory; the resources
        // are shared by every platform and live beside it.
        return Path.of("..", "shared", "resources", "assets", "dueldimension",
            "models", "character", "character_" + gender + ".glb");
    }

    static boolean present()
    {
        return Files.isRegularFile(model("f")) && Files.isRegularFile(model("m"));
    }

    @Test
    @EnabledIf("present")
    void bothGendersParse() throws IOException
    {
        for(String gender : new String[] {"f", "m"})
        {
            GlbModel got = GlbModel.load(Files.readAllBytes(model(gender)));
            assertNotNull(got, gender);
            assertEquals(SLOTS * PER_SLOT, got.primitives().size(),
                gender + ": one primitive per part");
            assertNotNull(got.skin(), gender + ": the parts are skinned");
        }
    }

    /**
     * Every vertex belongs to a real joint, at full weight.
     * <p>
     * The DS binds a vertex to whichever matrix was loaded when it was emitted,
     * which is rigid skinning under another name — so exactly one weight per
     * vertex is 1 and the rest are 0. A joint index past the end of the skin
     * would read off the end of the pose array at draw time.
     */
    @Test
    @EnabledIf("present")
    void everyVertexIsBoundToOneRealJoint() throws IOException
    {
        for(String gender : new String[] {"f", "m"})
        {
            GlbModel got = GlbModel.load(Files.readAllBytes(model(gender)));
            int joints = got.skin().jointCount();
            for(GlbModel.Primitive primitive : got.primitives())
            {
                assertNotNull(primitive.joints(), gender + ": joints present");
                assertNotNull(primitive.weights(), gender + ": weights present");
                for(int i = 0; i < primitive.vertexCount(); i++)
                {
                    assertTrue(primitive.joints()[i * 4] < joints,
                        gender + ": joint index within the skin");
                    assertEquals(1F, primitive.weights()[i * 4], 1e-5F,
                        gender + ": the first weight is the whole of it");
                    for(int k = 1; k < 4; k++)
                    {
                        assertEquals(0F, primitive.weights()[i * 4 + k], 1e-5F,
                            gender + ": the other three weigh nothing");
                    }
                }
            }
        }
    }

    /**
     * The skeleton is a tree, and the inverse bind matrices match it.
     * <p>
     * One matrix per joint is the thing that is silently wrong when it is
     * wrong: too few and the pose reads past the end, too many and every bone
     * after the miscount is transformed by its neighbour's.
     */
    @Test
    @EnabledIf("present")
    void skeletonAndBindMatricesAgree() throws IOException
    {
        for(String gender : new String[] {"f", "m"})
        {
            GlbModel got = GlbModel.load(Files.readAllBytes(model(gender)));
            int joints = got.skin().jointCount();
            assertEquals(joints * 16, got.skin().inverseBindMatrices().length,
                gender + ": one mat4 per joint");
            assertEquals("f".equals(gender) ? 32 : 30, joints,
                gender + ": the body's own bone count");
            for(int joint : got.skin().joints())
            {
                assertTrue(joint >= 0 && joint < got.nodes().size(),
                    gender + ": every joint names a node");
            }
        }
    }

    /**
     * The animations are there and are usable.
     * <p>
     * `fpa01` is the idle, `fpa03`/`fpa04` are the walk and the run, and
     * `fpa11bR` is the strike — the four the mod actually names — so their
     * absence is worth failing over rather than counting clips and hoping.
     * <p>
     * The count is 28 rather than 26 because two clips also ship REFLECTED:
     * `11b` swings the arm the duel disk is bolted to, so the mod plays its
     * mirror and the free arm does the hitting. See
     * {@code export_glb.mirror_map}.
     */
    @Test
    @EnabledIf("present")
    void theAnimationsThatAreNeededAreThere() throws IOException
    {
        GlbModel female = GlbModel.load(Files.readAllBytes(model("f")));
        assertEquals(28, female.animations().size(), "every clip for this skeleton");
        for(String want : new String[] {"fpa01", "fpa03", "fpa04", "fpa11bR"})
        {
            GlbModel.Animation found = female.animations().stream()
                .filter(a -> a.name().equals(want)).findFirst().orElse(null);
            assertNotNull(found, want + " is missing");
            assertTrue(found.duration() > 0F, want + " has no length");
            assertTrue(found.channels().size() > 0, want + " drives nothing");
        }
    }

    /**
     * The male right knee follows the ROM's curve instead of alternating with
     * its inverse.
     * <p>
     * An NSBCA five-word rotation hides its sixth signed Q12 component in the
     * low three bits of the five stored words. Treating the complete words as
     * Q15 values and guessing that component geometrically made {@code legR2}
     * jump by roughly 120 degrees in one frame whenever the quantised terms
     * used for the sign were near zero. The walk and run themselves never move
     * this knee by thirty degrees between adjacent source frames.
     */
    @Test
    @EnabledIf("present")
    void maleWalkAndRunKneeNeverInvert() throws IOException
    {
        GlbModel male = GlbModel.load(Files.readAllBytes(model("m")));
        int knee = -1;
        for(int i = 0; i < male.nodes().size(); i++)
        {
            if("legR2".equals(male.nodes().get(i).name()))
            {
                knee = i;
                break;
            }
        }
        assertTrue(knee >= 0, "male legR2 is missing");

        for(String want : new String[] {"mpa03", "mpa04"})
        {
            GlbModel.Animation animation = male.animations().stream()
                .filter(a -> a.name().equals(want)).findFirst().orElse(null);
            assertNotNull(animation, want + " is missing");
            GlbModel.Channel channel = null;
            for(GlbModel.Channel candidate : animation.channels())
            {
                if(candidate.node() == knee && candidate.path() == GlbModel.Path.ROTATION)
                {
                    channel = candidate;
                    break;
                }
            }
            assertNotNull(channel, want + " does not animate legR2");
            float[] rotations = animation.samplers().get(channel.sampler()).values();
            for(int at = 4; at < rotations.length; at += 4)
            {
                float dot = 0F;
                for(int axis = 0; axis < 4; axis++)
                {
                    dot += rotations[at - 4 + axis] * rotations[at + axis];
                }
                dot = Math.min(1F, Math.abs(dot));
                double degrees = Math.toDegrees(2.0 * Math.acos(dot));
                assertTrue(degrees < 30.0,
                    want + " legR2 moves " + degrees + " degrees at key " + (at / 4));
            }
        }
    }

    /**
     * Every character's head reaches exactly a player's height.
     *
     * <h2>The crown, and not the tallest body</h2>
     * 1.8 blocks is a vanilla player's own height -- its HITBOX, not the two
     * blocks of headroom it needs to stand up in -- and the scale is baked in by
     * the exporter, because the loader refuses node scale. So getting it wrong
     * produces a character who is simply the wrong size with nothing to say so,
     * which is what this exists to catch.
     * <p>
     * This asserted the tallest BODY until it caught a real one. Two things were
     * wrong with that. A body is not a person: some of these meshes are a torso
     * and some include a collar, so their heights do not measure the same thing
     * on the two genders. And a maximum is not a summary: eleven of the fifteen
     * male bodies measure within a whisker of each other while `wear10` is half
     * again as tall as any of them, so dividing by the tallest sized every
     * ordinary male duellist against one outlier -- 1.68 blocks against the
     * women's 1.84.
     * <p>
     * A HEAD measures the same thing on both. Every character has exactly one,
     * and its top is where a person's height is measured to -- so this checks
     * the faces, all fifteen of them, on both sets. Hair is deliberately not
     * checked and is expected to rise above the line: it sits ON the head, and
     * "a hat must not decide how big anyone is" is that same rule from the other
     * side.
     */
    @Test
    @EnabledIf("present")
    void everyHeadReachesPlayerHeight() throws IOException
    {
        for(String gender : new String[] {"f", "m"})
        {
            GlbModel got = GlbModel.load(Files.readAllBytes(model(gender)));
            // wear, then hair, then face: the faces are the third block of
            // fifteen. See the manifest order the exporter writes.
            for(int i = 0; i < PER_SLOT; i++)
            {
                int at = PER_SLOT * 2 + i;
                float high = -Float.MAX_VALUE;
                float[] positions = got.primitives().get(at).positions();
                for(int v = 1; v < positions.length; v += 3)
                {
                    high = Math.max(high, positions[v]);
                }
                // Loose enough for a face drawn with a crest, tight enough that
                // a whole set scaled from the wrong thing cannot pass.
                assertEquals(1.8F, high, 0.05F,
                    gender + ": the crown of face" + (i + 1));
            }
        }
    }
}
