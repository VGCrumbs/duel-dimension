package de.cas_ual_ty.dueldimension.character;

/**
 * Everything that describes one created character.
 * <p>
 * Eight small numbers, which is the whole point: this is what gets saved, what
 * gets sent to other players so they see the same person, and what the editor
 * mutates while it is open. The models it names are shipped in the jar, so a
 * look is meaningful on any client without transferring anything.
 * <p>
 * <b>Parts are numbered from one</b>, the way the game's own creator numbers
 * them, and the same number means the same part in both the sprite and the
 * model. Zero is not a part; it is what an unset field reads as, and
 * {@link #valid()} is what says so.
 *
 * @param gender  {@code 'f'} or {@code 'm'} — two separate sets of art, not a
 *                palette swap, so it decides which model file is even opened
 * @param face    1..15
 * @param hair    1..15
 * @param wear    1..15
 * @param disc    1..15
 * @param tone    1..3, one of the game's three skin ramps
 * @param hairRgb the colour the hair ramp is built through, 0xRRGGBB
 * @param wearRgb the same for the outfit
 * @param skinRgb a mixed skin colour, or 0 for one of the three presets
 * @param eyeRgb  the iris colour, or 0 to leave the face's own
 */
public record CharacterLook(char gender, int face, int hair, int wear, int disc,
    int tone, int hairRgb, int wearRgb, int skinRgb, int eyeRgb)
{
    /** How many choices each slot offers. */
    public static final int PARTS = 15;
    /** How many skin ramps the game ships. */
    public static final int TONES = 3;

    /**
     * What a new character starts as.
     * <p>
     * Deliberately not all-ones: a first-time character that already looks like
     * somebody is a better starting point than one that looks like a default,
     * and every one of these is a real choice the creator offers.
     */
    public static final CharacterLook DEFAULT =
        new CharacterLook('f', 1, 1, 1, 1, 1, 0x966034, 0x5C6894, 0, 0);

    /**
     * A skin the player mixed themselves, or 0 for one of the game's three.
     * <p>
     * <b>Zero means "not chosen", and that is a real state rather than a
     * missing one.</b> The DS ships three skin ramps and they are not three
     * colours -- they are three hand-authored 32-entry tables with their own
     * shading, which is why a colour picked off a wheel could never reproduce
     * one exactly. So the presets stay presets, and a mixed colour is applied
     * by re-tinting whichever of the three is closest in lightness. Storing the
     * choice as "tone 2, untinted" rather than as "tone 2's midpoint" is what
     * keeps the preset exact.
     */
    public boolean mixedSkin()
    {
        return skinRgb != 0;
    }

    public CharacterLook
    {
        gender = gender == 'm' ? 'm' : 'f';
        face = clamp(face);
        hair = clamp(hair);
        wear = clamp(wear);
        disc = clamp(disc);
        tone = Math.max(1, Math.min(TONES, tone));
        hairRgb &= 0xFFFFFF;
        wearRgb &= 0xFFFFFF;
        skinRgb &= 0xFFFFFF;
        eyeRgb &= 0xFFFFFF;
    }

    private static int clamp(int value)
    {
        return Math.max(1, Math.min(PARTS, value));
    }

    /** Whether this names a real character rather than an empty field. */
    public boolean valid()
    {
        return face >= 1 && hair >= 1 && wear >= 1 && disc >= 1;
    }

    public boolean female()
    {
        return gender == 'f';
    }

    public CharacterLook withGender(char which)
    {
        return new CharacterLook(which, face, hair, wear, disc, tone, hairRgb, wearRgb,
            skinRgb, eyeRgb);
    }

    public CharacterLook withPart(String slot, int number)
    {
        return switch(slot)
        {
            case "face" -> new CharacterLook(gender, number, hair, wear, disc, tone, hairRgb, wearRgb,
                skinRgb, eyeRgb);
            case "hair" -> new CharacterLook(gender, face, number, wear, disc, tone, hairRgb, wearRgb,
                skinRgb, eyeRgb);
            case "wear" -> new CharacterLook(gender, face, hair, number, disc, tone, hairRgb, wearRgb,
                skinRgb, eyeRgb);
            case "disc" -> new CharacterLook(gender, face, hair, wear, number, tone, hairRgb, wearRgb,
                skinRgb, eyeRgb);
            default -> this;
        };
    }

    public int part(String slot)
    {
        return switch(slot)
        {
            case "face" -> face;
            case "hair" -> hair;
            case "wear" -> wear;
            case "disc" -> disc;
            default -> 1;
        };
    }

    /** One of the game's three ramps, exactly, with no tint over it. */
    public CharacterLook withTone(int which)
    {
        return new CharacterLook(gender, face, hair, wear, disc, which, hairRgb, wearRgb,
            0, eyeRgb);
    }

    /**
     * A mixed skin colour. The tone is kept: it is which of the game's ramps
     * this is a tint OF, and going back to a preset is then one press.
     */
    public CharacterLook withSkinRgb(int rgb)
    {
        return new CharacterLook(gender, face, hair, wear, disc, tone, hairRgb, wearRgb,
            rgb == 0 ? 1 : rgb, eyeRgb);
    }

    public CharacterLook withHairRgb(int rgb)
    {
        return new CharacterLook(gender, face, hair, wear, disc, tone, rgb, wearRgb,
            skinRgb, eyeRgb);
    }

    /**
     * The iris colour, or 0 for the one the face was drawn with.
     * <p>
     * <b>Zero is a real state.</b> Every face in the DS set is authored with an
     * eye colour of its own -- brown, grey, red, violet -- and those are part of
     * the face rather than a slot waiting to be filled. So "leave it alone" has
     * to be expressible, and it is the default: a character nobody has touched
     * looks exactly as the artist drew them.
     */
    public CharacterLook withEyeRgb(int rgb)
    {
        return new CharacterLook(gender, face, hair, wear, disc, tone, hairRgb, wearRgb,
            skinRgb, rgb);
    }

    /** Whether the player has chosen an iris colour. See {@link #withEyeRgb}. */
    public boolean tintedEyes()
    {
        return eyeRgb != 0;
    }

    public CharacterLook withWearRgb(int rgb)
    {
        return new CharacterLook(gender, face, hair, wear, disc, tone, hairRgb, rgb,
            skinRgb, eyeRgb);
    }

    /**
     * A stable key for caches.
     * <p>
     * Two looks that differ anywhere must differ here, because this is what
     * decides whether a built texture can be reused — and a collision would show
     * one player wearing another's colours.
     */
    public String key()
    {
        return gender + "/" + face + "." + hair + "." + wear + "." + disc
            + "." + tone + "." + Integer.toHexString(hairRgb)
            + "." + Integer.toHexString(wearRgb)
            + "." + Integer.toHexString(skinRgb)
            + "." + Integer.toHexString(eyeRgb);
    }
}
