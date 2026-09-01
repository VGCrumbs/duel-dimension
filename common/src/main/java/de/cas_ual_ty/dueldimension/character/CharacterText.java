package de.cas_ual_ty.dueldimension.character;

/**
 * A character as one short string, for saving.
 * <p>
 * <pre>f 1 1 1 1 1 966034 5c6894 0</pre>
 * written as {@code f-1.1.1.1.1-966034-5c6894-0}: gender, the four parts and
 * the tone, then the hair, outfit and mixed-skin colours in hex.
 * <p>
 * The last field was added after the first characters were saved, so it is
 * OPTIONAL on the way in: a string written before mixed skin existed reads back
 * as zero, which is exactly what it meant.
 * <p>
 * <b>One field rather than eight.</b> A profile's Codec grows a key per field,
 * and a character is not a thing anything else reads a piece of — nobody wants
 * to know somebody's hair number without wanting the rest. So it goes in as one
 * string, which also means adding a ninth choice later is a change to this
 * parser and not to the save format.
 * <p>
 * <b>Anything unreadable becomes the default</b> rather than throwing. This is
 * parsed while loading a save: a profile hand-edited into nonsense should cost
 * somebody their character, not their world.
 */
public final class CharacterText
{
    private CharacterText()
    {
    }

    public static String write(CharacterLook look)
    {
        if(look == null)
        {
            return "";
        }
        return look.gender() + "-" + look.face() + "." + look.hair() + "."
            + look.wear() + "." + look.disc() + "." + look.tone()
            + "-" + Integer.toHexString(look.hairRgb())
            + "-" + Integer.toHexString(look.wearRgb())
            + "-" + Integer.toHexString(look.skinRgb())
            + "-" + Integer.toHexString(look.eyeRgb());
    }

    public static CharacterLook read(String text)
    {
        if(text == null || text.isBlank())
        {
            return CharacterLook.DEFAULT;
        }
        try
        {
            String[] fields = text.split("-");
            if(fields.length < 4)
            {
                return CharacterLook.DEFAULT;
            }
            String[] parts = fields[1].split("\\.");
            if(parts.length < 5)
            {
                return CharacterLook.DEFAULT;
            }
            return new CharacterLook(fields[0].charAt(0),
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]),
                Integer.parseInt(fields[2], 16), Integer.parseInt(fields[3], 16),
                fields.length > 4 ? Integer.parseInt(fields[4], 16) : 0,
                // Appended, and read only if present: a character saved before
                // eyes were a thing has five fields and means "leave them".
                fields.length > 5 ? Integer.parseInt(fields[5], 16) : 0);
        }
        catch(RuntimeException unreadable)
        {
            return CharacterLook.DEFAULT;
        }
    }
}
