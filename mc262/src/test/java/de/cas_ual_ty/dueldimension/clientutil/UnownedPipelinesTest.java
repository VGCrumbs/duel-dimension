package de.cas_ual_ty.dueldimension.clientutil;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnownedPipelinesTest
{
    @Test
    void unownedColourKeepsFifteenPercentSaturation()
    {
        assertEquals(0xFF542E2E, UnownedPipelines.unownedColour(0xFFFF0000));
    }

    @Test
    void halfSaturationPreservesNeutralColoursAndAlpha()
    {
        assertEquals(0x7F808080, UnownedPipelines.unownedColour(0x7F808080));
    }

    /**
     * The two tests above pin the Java reference, and nothing draws through it.
     * <p>
     * What draws is GLSL, which javac never sees and no other test can reach —
     * so a shader edited to a different weight or a different residual would
     * change the look with all 255 tests still green. This reads both files off
     * the classpath and asserts they still spell the constants the reference
     * uses. It is not a substitute for looking at the client, but it is the one
     * cheap way to stop the two halves drifting apart silently.
     */
    @Test
    void bothShadersSpellTheSameConstantsAsTheJavaReference()
    {
        String luma = "dot(color.rgb, vec3("
            + UnownedPipelines.RED_WEIGHT + ", "
            + UnownedPipelines.GREEN_WEIGHT + ", "
            + UnownedPipelines.BLUE_WEIGHT + "))";
        String mix = "mix(vec3(grey), color.rgb, " + UnownedPipelines.KEPT_CHROMA + ")";

        for(String name : new String[] {"card_desaturate", "card_desaturate_entity"})
        {
            String source = read("assets/dueldimension/shaders/core/" + name + ".fsh");
            assertTrue(source.contains(luma), name + ".fsh no longer computes " + luma);
            assertTrue(source.contains(mix), name + ".fsh no longer applies " + mix);
            // The card art is letterboxed inside a square with transparent
            // margins, so dropping the alpha test changes how those margins
            // blend. Both stock shaders have one; neither copy may lose it.
            assertTrue(source.contains("discard;"), name + ".fsh no longer discards");
        }
    }

    private static String read(String path)
    {
        try(InputStream in = UnownedPipelinesTest.class.getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(in, path + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch(java.io.IOException e)
        {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
