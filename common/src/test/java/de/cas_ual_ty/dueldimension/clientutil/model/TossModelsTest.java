package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coin and the die, read by the game's own loader.
 *
 * <h2>Why these are asserted rather than looked at</h2>
 * Both files were written by a Python exporter
 * ({@code MasterDuelDecomp/scripts/export_md_model.py}) out of Master Duel's
 * Unity bundles, through two conversions that fail silently when they are
 * wrong: a handedness flip that produces a model which turns the wrong way, and
 * a scale divide that produces one the wrong size. Neither throws. The only
 * cheap way to know the files are what the exporter claims is to read them back
 * with the reader that will actually load them.
 *
 * <h2>They ship, so they are always here</h2>
 * Unlike {@link GlbFixtures#DRAGON}, which is a model a player supplies and a
 * test therefore has to skip without, these two are mod resources. A missing
 * file is a broken build rather than a machine without an optional extra, so
 * these do not guard on presence -- they fail.
 */
class TossModelsTest
{
    /**
     * Resolved by walking up for {@code shared/resources}, the same way the
     * dev-only icon tool finds the source tree: a test's working directory is
     * its own module, and the shared resources are a sibling of it.
     */
    private static Path model(String name)
    {
        Path at = Path.of("").toAbsolutePath();
        for(int i = 0; i < 6 && at != null; i++)
        {
            Path candidate = at.resolve("shared").resolve("resources")
                .resolve("assets").resolve("dueldimension").resolve("models").resolve(name);
            if(Files.isRegularFile(candidate))
            {
                return candidate;
            }
            at = at.getParent();
        }
        throw new AssertionError("could not find shared/resources for " + name);
    }

    private static GlbModel load(String name) throws IOException
    {
        return GlbModel.load(Files.readAllBytes(model(name)));
    }

    @Test
    void dieIsOnePieceWithATexture() throws IOException
    {
        GlbModel die = load("dice.glb");
        assertEquals(1, die.primitives().size(), "one primitive");
        assertEquals(1, die.images().size(), "the pip texture, embedded");
        assertTrue(die.primitives().get(0).triangleCount() > 100,
            "a die that lost its geometry would still load");
    }

    /**
     * The coin is two files, and that is the point.
     * <p>
     * `Coin01Heads` and `Coin01Tails` are both COMPLETE coins occupying exactly
     * the same space -- not two sides of one. Shipping them in a single file
     * put them in a z-fight and drew a mixture of both faces. One file each
     * means the screen shows a result by choosing what to load.
     */
    /**
     * The coin's two faces are two SUBMESHES of one mesh, and that is the point.
     * <p>
     * `Coin01Heads` carries two materials -- `lambert16` for the heads art and
     * `lambert17` for the tails art -- across two submeshes. An export that
     * merged them stretched one texture over both sides, which is what the
     * coin looked like before this was noticed.
     */
    @Test
    void coinKeepsItsTwoFacesApart() throws IOException
    {
        GlbModel coin = load("coin.glb");
        assertEquals(2, coin.primitives().size(), "one primitive per face");
        assertEquals(2, coin.images().size(), "one texture per face");
        assertTrue(coin.primitives().get(0).material() != coin.primitives().get(1).material(),
            "the faces must not share a material, or they share a picture");
        assertFalse(java.util.Arrays.equals(coin.images().get(0), coin.images().get(1)),
            "the two face textures must differ, or the results look the same");
    }

    @Test
    void bothCarryTheirMotion() throws IOException
    {
        for(String name : new String[] {"dice.glb", "coin.glb"})
        {
            GlbModel model = load(name);
            assertFalse(model.animations().isEmpty(), name + " has no animation");
            GlbModel.Animation motion = model.animations().get(0);
            assertFalse(motion.channels().isEmpty(), name + " animates nothing");
            // Both were exported around a second or two of movement. A duration
            // of zero means the sampler read no keys; a huge one means Unity's
            // +/-FLT_MAX sentinel frames survived, which is the trap the stream
            // decoder exists to avoid.
            assertTrue(motion.duration() > 0.5F && motion.duration() < 10F,
                name + " duration is " + motion.duration());
        }
    }

    /**
     * Sizes and shapes, because a scale divide that is wrong does not throw.
     * <p>
     * The exporter measures the source extent and divides by it, so a correct
     * export is bounded by one on its widest axis. The die being as deep as it
     * is wide and the coin being flat is the shape check worth making: a coin
     * as deep as it is wide would mean the axes were mixed up.
     */
    @Test
    void bothAreNormalisedAndTheRightShape() throws IOException
    {
        float[] die = extent(load("dice.glb"));
        assertTrue(die[0] > 0.9F && die[0] <= 1.01F, "die x extent " + die[0]);
        assertTrue(die[2] > 0.9F && die[2] <= 1.01F,
            "a die is as deep as it is wide; z extent is " + die[2]);

        float[] coin = extent(load("coin.glb"));
        assertTrue(coin[0] > 0.9F && coin[0] <= 1.01F, "coin x extent " + coin[0]);
        assertTrue(coin[2] < 0.2F, "a coin is flat; z extent is " + coin[2]);
    }

    /** The largest absolute coordinate on each axis, from the model's own bounds. */
    private static float[] extent(GlbModel model)
    {
        float[] most = new float[3];
        for(int axis = 0; axis < 3; axis++)
        {
            most[axis] = Math.max(Math.abs(model.min()[axis]), Math.abs(model.max()[axis]));
        }
        return most;
    }
}
