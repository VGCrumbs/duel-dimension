package de.cas_ual_ty.dueldimension.character;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The palettes the characters are recoloured through.
 * <p>
 * The interesting assertions are not that the file parses — they are that the
 * rules about WHICH slots may be written are still the ones the DS data
 * supports, because both mistakes here are silent. Painting a slot that is not
 * a placeholder repaints real art; refusing one that is leaves a character grey.
 */
class CharacterPalettesTest
{
    private static Path file(String gender)
    {
        return Path.of("..", "shared", "resources", "assets", "dueldimension",
            "models", "character", "character_" + gender + ".pal");
    }

    static boolean present()
    {
        return Files.isRegularFile(file("f")) && Files.isRegularFile(file("m"));
    }

    private static CharacterPalettes load(String gender) throws IOException
    {
        return CharacterPalettes.read(Files.readAllBytes(file(gender)));
    }

    @Test
    @EnabledIf("present")
    void everyRecolourablePartIsThere() throws IOException
    {
        for(String gender : new String[] {"f", "m"})
        {
            CharacterPalettes got = load(gender);
            // Fifteen each of body, hair and face. Disks are not recoloured and
            // are deliberately absent.
            assertEquals(45, got.size(), gender);
            assertNull(got.part("disc", 1), gender + ": a disk reserves nothing");
            for(String slot : new String[] {"wear", "hair", "face"})
            {
                for(int i = 1; i <= CharacterLook.PARTS; i++)
                {
                    CharacterPalettes.Part part = got.part(slot, i);
                    assertNotNull(part, gender + " " + slot + i);
                    assertEquals(256, part.palette().length, "a full palette");
                    assertEquals(part.pixels(), part.indices().length, "one index per pixel");
                }
            }
        }
    }

    /**
     * The slots really are the placeholders the recolour assumes.
     * <p>
     * This is the claim the whole scheme rests on, so it is checked against
     * every shipped part rather than against the two that were looked at.
     */
    @Test
    @EnabledIf("present")
    void thePlaceholderSlotsAreWhereTheyShouldBe() throws IOException
    {
        for(String gender : new String[] {"f", "m"})
        {
            CharacterPalettes got = load(gender);
            for(int i = 1; i <= CharacterLook.PARTS; i++)
            {
                assertTrue(CharacterRamps.placeholder(got.part("face", i).palette(),
                    CharacterRamps.SKIN_AT), gender + " face" + i + ": skin slot is empty");
                assertTrue(CharacterRamps.placeholder(got.part("wear", i).palette(),
                    CharacterRamps.SKIN_AT), gender + " wear" + i + ": skin slot is empty");
                assertTrue(CharacterRamps.placeholder(got.part("wear", i).palette(),
                    CharacterRamps.HAIR_AT), gender + " wear" + i + ": outfit slot is empty");
                assertTrue(CharacterRamps.placeholder(got.part("hair", i).palette(),
                    CharacterRamps.HAIR_AT), gender + " hair" + i + ": hair slot is empty");
            }
        }
    }

    /** Choosing a colour changes the pixels that colour is for, and no others. */
    @Test
    @EnabledIf("present")
    void recolouringMovesOnlyTheChosenSlot() throws IOException
    {
        CharacterPalettes got = load("f");
        int[] skin = CharacterRamps.ramp(0xC08060);
        CharacterLook red = CharacterLook.DEFAULT.withWearRgb(0xFF0000);
        CharacterLook blue = red.withWearRgb(0x0000FF);
        int[] first = got.paint("wear", 1, skin, red);
        int[] second = got.paint("wear", 1, skin, blue);
        assertEquals(first.length, second.length);
        int moved = 0;
        for(int i = 0; i < first.length; i++)
        {
            if(first[i] != second[i])
            {
                moved++;
            }
        }
        assertTrue(moved > 0, "the outfit colour reaches the texture");
        assertTrue(moved < first.length, "and does not repaint the whole of it");

        // The face has no outfit slot, so an outfit colour must not touch it.
        int[] a = got.paint("face", 1, skin, red);
        int[] b = got.paint("face", 1, skin, blue);
        assertArraysEqual(a, b, "an outfit colour does not reach a face");
    }

    /** A skin tone reaches the body and the face, which both wear it. */
    @Test
    @EnabledIf("present")
    void theSkinToneReachesEveryPartThatHasSkin() throws IOException
    {
        CharacterPalettes got = load("f");
        CharacterLook look = CharacterLook.DEFAULT;
        for(String slot : new String[] {"face", "wear"})
        {
            int[] pale = got.paint(slot, 1, CharacterRamps.ramp(0xF0D0C0), look);
            int[] dark = got.paint(slot, 1, CharacterRamps.ramp(0x503020), look);
            boolean moved = false;
            for(int i = 0; i < pale.length && !moved; i++)
            {
                moved = pale[i] != dark[i];
            }
            assertTrue(moved, slot + " follows the skin tone");
        }
    }

    /** The ramp is the shape the game's own skin ramps are. */
    @Test
    void theRampRunsDarkToLightThroughTheColourAsked()
    {
        int[] ramp = CharacterRamps.ramp(0xA4837B);
        assertEquals(CharacterRamps.RAMP, ramp.length);
        assertTrue(luminance(ramp[0]) < 40, "starts near black");
        assertTrue(luminance(ramp[ramp.length - 1]) > 215, "ends near white");
        // The colour asked for sits at the midpoint, which is what makes a
        // picked colour look like the colour that was picked.
        assertEquals(0xA4, (ramp[CharacterRamps.RAMP / 2] >> 16) & 0xFF, 6);
        for(int i = 1; i < ramp.length; i++)
        {
            assertTrue(luminance(ramp[i]) >= luminance(ramp[i - 1]) - 1,
                "climbs the whole way");
        }
    }

    /** A palette full of real art is left alone. */
    @Test
    void aSlotThatIsNotGreyIsNotAPlaceholder()
    {
        int[] painted = new int[256];
        for(int i = 0; i < painted.length; i++)
        {
            painted[i] = 0xFF000000 | (i * 7 % 256) << 16 | (i * 3 % 256) << 8;
        }
        assertFalse(CharacterRamps.placeholder(painted, CharacterRamps.SKIN_AT));
        int[] grey = new int[256];
        for(int i = 0; i < grey.length; i++)
        {
            int v = i % 256;
            grey[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        assertTrue(CharacterRamps.placeholder(grey, CharacterRamps.SKIN_AT));
    }

    private static int luminance(int rgb)
    {
        return (((rgb >> 16) & 0xFF) * 30 + ((rgb >> 8) & 0xFF) * 59 + (rgb & 0xFF) * 11) / 100;
    }

    private static void assertArraysEqual(int[] a, int[] b, String why)
    {
        assertEquals(a.length, b.length, why);
        for(int i = 0; i < a.length; i++)
        {
            if(a[i] != b[i])
            {
                throw new AssertionError(why + " (pixel " + i + ")");
            }
        }
    }
}
