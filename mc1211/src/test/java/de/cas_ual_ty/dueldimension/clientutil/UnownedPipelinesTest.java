package de.cas_ual_ty.dueldimension.clientutil;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

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
     * <b>26.2 has a third test here and this version cannot.</b>
     *
     * <p>There it reads both {@code .fsh} files off the classpath and asserts
     * they still spell the constants above — because GLSL is invisible to javac
     * and to every other test, so a shader edited to a different weight would
     * change the look with the whole suite still green.
     *
     * <p>There are no shaders in this build. The two written for 26.2 use its
     * uniform blocks, which 1.21.1's core-shader pipeline does not have, so
     * {@link UnownedPipelines#available()} is permanently false and an unowned
     * card is dimmed rather than greyed. Restoring the check is the second half
     * of restoring the shaders; until then the reference above is pinned and
     * nothing draws through it. See mc1211/README.md.
     */
}
