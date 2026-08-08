package de.cas_ual_ty.dueldimension.clientutil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DdCardResourcePackTest
{
    @Test
    void unownedColourKeepsFifteenPercentSaturation()
    {
        assertEquals(0xFF542E2E, DdCardResourcePack.unownedColour(0xFFFF0000));
    }

    @Test
    void halfSaturationPreservesNeutralColoursAndAlpha()
    {
        assertEquals(0x7F808080, DdCardResourcePack.unownedColour(0x7F808080));
    }
}
