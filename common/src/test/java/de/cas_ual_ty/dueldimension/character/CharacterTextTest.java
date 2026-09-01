package de.cas_ual_ty.dueldimension.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saved form of a character.
 * <p>
 * This is what is in a player's save file, so the case that matters is not the
 * happy one — it is what happens to somebody's world when the string is not
 * what this expects.
 */
class CharacterTextTest
{
    @Test
    void everyLookSurvivesTheRoundTrip()
    {
        for(char gender : new char[] {'f', 'm'})
        {
            for(int part = 1; part <= CharacterLook.PARTS; part++)
            {
                CharacterLook look = new CharacterLook(gender, part,
                    CharacterLook.PARTS + 1 - part, part, part,
                    1 + part % CharacterLook.TONES, 0x123456 * part & 0xFFFFFF,
                    0xABCDEF ^ part, 0x334455 * part & 0xFFFFFF,
                    0x778899 * part & 0xFFFFFF);
                assertEquals(look, CharacterText.read(CharacterText.write(look)),
                    "round trip " + gender + part);
            }
        }
    }

    /** Black is a colour somebody will pick, and it has leading zeroes in hex. */
    @Test
    void aBlackColourSurvives()
    {
        CharacterLook look = CharacterLook.DEFAULT.withHairRgb(0x000000).withWearRgb(0x00FF00);
        assertEquals(look, CharacterText.read(CharacterText.write(look)));
    }

    /** Nonsense costs a character, not a world. */
    @Test
    void anythingUnreadableBecomesTheDefault()
    {
        for(String broken : new String[] {"", "   ", "garbage", "f-1.1-x-y",
            "f-1.1.1.1.1", "f-a.b.c.d.e-0-0", "-----", "f-1.1.1.1.1-zz-zz"})
        {
            assertEquals(CharacterLook.DEFAULT, CharacterText.read(broken), broken);
        }
        assertEquals(CharacterLook.DEFAULT, CharacterText.read(null));
    }

    /**
     * A character saved before skin could be mixed still reads.
     * <p>
     * The last field was added afterwards, so a string without it has to mean
     * what it meant when it was written -- no mixed skin -- rather than being
     * refused and costing somebody their character.
     */
    @Test
    void aLookWrittenBeforeMixedSkinStillReads()
    {
        CharacterLook was = CharacterText.read("f-2.3.4.5.2-966034-5c6894");
        assertEquals('f', was.gender());
        assertEquals(2, was.face());
        assertEquals(0x966034, was.hairRgb());
        assertEquals(0, was.skinRgb(), "no mix, which is what it meant");
        assertFalse(was.mixedSkin());
    }

    /** A profile saved before characters existed reads as having none. */
    @Test
    void anEmptyFieldIsTheDefaultCharacter()
    {
        assertEquals(CharacterLook.DEFAULT, CharacterText.read(""));
        assertTrue(CharacterLook.DEFAULT.valid());
    }

    /** Out-of-range numbers are clamped rather than trusted. */
    @Test
    void aHostileStringCannotNameAPartThatDoesNotExist()
    {
        CharacterLook got = CharacterText.read("f-99.0.-5.1000.7-ffffff-ffffff");
        assertTrue(got.face() >= 1 && got.face() <= CharacterLook.PARTS);
        assertTrue(got.hair() >= 1 && got.hair() <= CharacterLook.PARTS);
        assertTrue(got.wear() >= 1 && got.wear() <= CharacterLook.PARTS);
        assertTrue(got.disc() >= 1 && got.disc() <= CharacterLook.PARTS);
        assertTrue(got.tone() >= 1 && got.tone() <= CharacterLook.TONES);
    }

    /** Two different characters must not share a cache key. */
    @Test
    void theCacheKeyTellsCharactersApart()
    {
        CharacterLook one = CharacterLook.DEFAULT;
        assertNotEquals(one.key(), one.withPart("hair", 2).key());
        assertNotEquals(one.key(), one.withTone(2).key());
        assertNotEquals(one.key(), one.withHairRgb(0x010203).key());
        assertNotEquals(one.key(), one.withWearRgb(0x010203).key());
        assertNotEquals(one.key(), one.withGender('m').key());
        // ...and the hair and outfit colours must not be interchangeable, which
        // a key that merely added them would allow.
        assertNotEquals(one.withHairRgb(0x111111).key(), one.withWearRgb(0x111111).key());
    }
}
