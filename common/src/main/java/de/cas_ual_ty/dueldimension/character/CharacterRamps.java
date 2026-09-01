package de.cas_ual_ty.dueldimension.character;

/**
 * Customisation is three palette slots, and the art ships grey on purpose.
 * <p>
 * Every character part reserves the top of its 256-colour palette for colours
 * the player picks. The art indexes those slots and is otherwise finished, which
 * is why an outfit looks flatly grey until something fills them in.
 * <pre>
 *     192..223   32   SKIN
 *     224..255   32   HAIR on a hair part, OUTFIT on a body
 * </pre>
 * Pinned down in the DS data by colouring each candidate range in and seeing
 * which pixels lit up: a face uses 192..223 for 3805 pixels and 224..255 for
 * none, hair is the exact mirror, and a body uses both — skin for the arms and
 * the other for the jacket.
 * <p>
 * <b>There is no eye slot, and an eye colour is a REPAINT rather than a fill.</b>
 * See {@link #iris}: the eye is art, drawn per face, and it is found by looking
 * for it. 176..191 looks like a slot and is not: on every face
 * it is a run of near-white greys climbing 206 to 255 in luminance, and the
 * pixels near the eye that use it are the catchlight and the white of the eye.
 * Writing a colour there blows the eyes out and turns a white mask into a
 * coloured one. The game offers no eye colour either — its wardrobe has hair and
 * clothes and nothing else.
 * <p>
 * <b>A duel disk reserves nothing.</b> Its palette carries real, saturated art
 * across all three ranges, so writing a ramp into one repaints the disk in skin
 * tones. Which slots a part uses is decided by the KIND of part, and then
 * {@link #placeholder} checks the slot is actually empty before writing —
 * `ddisk01` is 93% grey where a body is 100%, close enough to fool a test that
 * only looked at the colours.
 */
public final class CharacterRamps
{
    /** Where the skin ramp lives, and how long it is. */
    public static final int SKIN_AT = 192;
    public static final int HAIR_AT = 224;
    public static final int RAMP = 32;

    /** How grey an entry has to be to count as an unfilled slot. */
    private static final int TOLERANCE = 24;
    /** And how much of a slot has to be grey before it is safe to write over. */
    private static final int PLACEHOLDER = 90;

    private CharacterRamps()
    {
    }

    /**
     * A ramp through {@code base}, shaped like the game's own skin ramps.
     * <p>
     * Black to the colour over the first half, the colour to white over the
     * second, so the midpoint is exactly what was asked for and the ends still
     * have somewhere to go. A single black-to-colour ramp loses every highlight;
     * a single colour-to-white one loses every shadow. Measured against
     * `hadapal_01`, this tracks it to about 30/255 at its worst, which is a
     * difference nobody can see once it is a gradient.
     *
     * @param base 0xRRGGBB
     * @return {@link #RAMP} colours, 0xRRGGBB each
     */
    public static int[] ramp(int base)
    {
        int red = (base >> 16) & 0xFF;
        int green = (base >> 8) & 0xFF;
        int blue = base & 0xFF;
        int[] out = new int[RAMP];
        for(int i = 0; i < RAMP; i++)
        {
            float t = i / (float) (RAMP - 1);
            int r;
            int g;
            int b;
            if(t < 0.5F)
            {
                float k = t * 2F;
                r = (int) (red * k);
                g = (int) (green * k);
                b = (int) (blue * k);
            }
            else
            {
                float k = (t - 0.5F) * 2F;
                r = (int) (red + (255 - red) * k);
                g = (int) (green + (255 - green) * k);
                b = (int) (blue + (255 - blue) * k);
            }
            out[i] = (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /**
     * Whether a slot is still the grey the art shipped with.
     * <p>
     * Both this and the part's kind are needed. The kind says which slots are
     * eligible at all; this says whether the slot is actually empty.
     *
     * @param palette 256 entries, 0xAARRGGBB
     */
    public static boolean placeholder(int[] palette, int at)
    {
        if(palette == null || at + RAMP > palette.length)
        {
            return false;
        }
        int grey = 0;
        for(int i = at; i < at + RAMP; i++)
        {
            int r = (palette[i] >> 16) & 0xFF;
            int g = (palette[i] >> 8) & 0xFF;
            int b = palette[i] & 0xFF;
            int high = Math.max(r, Math.max(g, b));
            int low = Math.min(r, Math.min(g, b));
            if(high - low < TOLERANCE)
            {
                grey++;
            }
        }
        return grey * 100 / RAMP >= PLACEHOLDER;
    }

    /**
     * A part's palette with the player's choices written into its slots.
     *
     * @param palette 256 entries, 0xAARRGGBB; not modified
     * @param slot    face / hair / wear / disc
     * @param skin    the chosen skin ramp, {@link #RAMP} entries of 0xRRGGBB
     * @param look    where the hair and outfit colours come from
     */
    public static int[] recolour(int[] palette, String slot, int[] skin,
        CharacterLook look)
    {
        return recolour(palette, slot, skin, look, null);
    }

    /**
     * @param iris which entries draw the iris, from {@link #iris}; null or
     *             empty leaves the face's own eye colour, which is also what an
     *             unset {@code eyeRgb} means
     */
    public static int[] recolour(int[] palette, String slot, int[] skin,
        CharacterLook look, int[] iris)
    {
        int[] out = palette.clone();
        if("face".equals(slot) && look != null && look.tintedEyes())
        {
            paintEyes(out, iris, look.eyeRgb());
        }
        boolean usesSkin = "face".equals(slot) || "hair".equals(slot) || "wear".equals(slot);
        if(usesSkin && skin != null && placeholder(out, SKIN_AT))
        {
            paint(out, SKIN_AT, skin);
        }
        if("hair".equals(slot) && placeholder(out, HAIR_AT))
        {
            paint(out, HAIR_AT, ramp(look.hairRgb()));
        }
        else if("wear".equals(slot) && placeholder(out, HAIR_AT))
        {
            paint(out, HAIR_AT, ramp(look.wearRgb()));
        }
        return out;
    }

    /**
     * How saturated an entry has to be to read as iris rather than as shading.
     * <p>
     * The face art ships with its SKIN as the grey placeholder ramp, which is
     * the whole reason this works: on an unpainted face every coloured pixel is
     * a deliberate feature -- an eyebrow, a lip, an iris -- and everything else
     * is grey. So "coloured" is a usable question to ask of a palette entry, in
     * a way it would not be on art that shipped finished.
     */
    private static final double IRIS_SATURATION = 0.28D;

    /** And the band it has to sit in: not a lash, not a catchlight. */
    private static final double IRIS_DARKEST = 30D;
    private static final double IRIS_LIGHTEST = 200D;

    /**
     * The rows an eye occupies on a 64x64 face, top and bottom inclusive.
     * <p>
     * Measured off the art rather than assumed: every face in both sets draws
     * its eye between these rows, and the brow sits above them and the mouth
     * below. The band is what separates the iris from the two other coloured
     * things on a face -- an eyebrow is the same brown as a brown iris, and no
     * test on colour alone can tell them apart.
     */
    private static final int EYE_TOP = 20;
    private static final int EYE_BOTTOM = 36;

    /**
     * Which palette entries draw the iris.
     *
     * <h2>There is no eye slot, so this has to be found rather than read</h2>
     * See the class note: the DS reserves 192..223 and 224..255 and nothing for
     * eyes, because the game never offered an eye colour -- each face is drawn
     * with its own, brown or grey or violet, and that is part of the face. So an
     * eye colour cannot be a slot to fill; it can only be a repaint of art that
     * is already there, and the art has to be located first.
     *
     * <h2>Coloured, in the eye band, and nowhere else</h2>
     * Three conditions, and the third is the one that does the work. An entry
     * that also appears outside the eye band is shared with something else on
     * the face -- the eyebrow, the mouth, a shadow -- and repainting it would
     * drag that along with the iris. Requiring the entry to be UNIQUE to the
     * band is what makes a palette-level repaint safe at all, and it is why this
     * returns entries rather than pixels: the recolour then costs nothing per
     * pixel, and fits the pass {@link #recolour} was already doing.
     * <p>
     * A face whose eyes are closed, or covered, or drawn in flat grey, yields
     * few entries or none. That is the right answer for it: there is no iris to
     * repaint, and the face comes out as drawn.
     *
     * @param palette the part's own palette, 0xAARRGGBB
     * @param indices one byte per pixel
     * @param width   the texture's width, so a pixel's row can be found
     * @return the entries to repaint, ascending; never null
     */
    public static int[] iris(int[] palette, byte[] indices, int width)
    {
        if(palette == null || indices == null || width <= 0)
        {
            return new int[0];
        }
        boolean[] inBand = new boolean[palette.length];
        boolean[] outside = new boolean[palette.length];
        for(int i = 0; i < indices.length; i++)
        {
            int at = indices[i] & 0xFF;
            // The reserved ramps are never iris: they are the skin the player
            // picked and the hair or outfit ramp, and they are shared by half
            // the face.
            if(at >= SKIN_AT || at >= palette.length)
            {
                continue;
            }
            int row = i / width;
            if(row >= EYE_TOP && row <= EYE_BOTTOM)
            {
                inBand[at] = true;
            }
            else
            {
                outside[at] = true;
            }
        }
        int count = 0;
        for(int at = 0; at < palette.length; at++)
        {
            if(inBand[at] && !outside[at] && coloured(palette[at]))
            {
                count++;
            }
        }
        int[] out = new int[count];
        int put = 0;
        for(int at = 0; at < palette.length && put < count; at++)
        {
            if(inBand[at] && !outside[at] && coloured(palette[at]))
            {
                out[put++] = at;
            }
        }
        return out;
    }

    /** Saturated enough, and neither a lash nor a catchlight. See {@link #iris}. */
    private static boolean coloured(int argb)
    {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if(max == 0)
        {
            return false;
        }
        double lum = 0.299D * r + 0.587D * g + 0.114D * b;
        return (max - min) / (double) max > IRIS_SATURATION
            && lum >= IRIS_DARKEST && lum <= IRIS_LIGHTEST;
    }

    /**
     * Repaints the iris entries in {@code base}'s hue, keeping their lightness.
     *
     * <h2>Lightness is the art; hue is the choice</h2>
     * An iris is not one colour. It is a dark rim, a body, a lighter lower half
     * and a catchlight, and that gradient is what makes it read as an eye rather
     * than as a dot. Replacing the entries outright would flatten all of it, so
     * only the HUE and the SATURATION are taken from the chosen colour and each
     * entry keeps the lightness it was drawn with.
     * <p>
     * Which also means a dark iris stays dark and a bright one stays bright --
     * the face keeps its character and only its eyes change, which is what "eye
     * colour" means.
     */
    public static void paintEyes(int[] palette, int[] iris, int base)
    {
        if(palette == null || iris == null || base == 0)
        {
            return;
        }
        float[] want = hsl((base >> 16) & 0xFF, (base >> 8) & 0xFF, base & 0xFF);
        for(int at : iris)
        {
            if(at < 0 || at >= palette.length)
            {
                continue;
            }
            int argb = palette[at];
            float[] was = hsl((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
            palette[at] = (argb & 0xFF000000) | rgb(want[0], want[1], was[2]);
        }
    }

    /** Hue 0..1, saturation 0..1, lightness 0..1. */
    private static float[] hsl(int r, int g, int b)
    {
        float rf = r / 255F;
        float gf = g / 255F;
        float bf = b / 255F;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float l = (max + min) / 2F;
        if(max == min)
        {
            return new float[] {0F, 0F, l};
        }
        float d = max - min;
        float s = l > 0.5F ? d / (2F - max - min) : d / (max + min);
        float h;
        if(max == rf)
        {
            h = (gf - bf) / d + (gf < bf ? 6F : 0F);
        }
        else if(max == gf)
        {
            h = (bf - rf) / d + 2F;
        }
        else
        {
            h = (rf - gf) / d + 4F;
        }
        return new float[] {h / 6F, s, l};
    }

    private static int rgb(float h, float s, float l)
    {
        if(s <= 0F)
        {
            int v = Math.round(l * 255F);
            return (v << 16) | (v << 8) | v;
        }
        float q = l < 0.5F ? l * (1F + s) : l + s - l * s;
        float p = 2F * l - q;
        int r = Math.round(hue(p, q, h + 1F / 3F) * 255F);
        int g = Math.round(hue(p, q, h) * 255F);
        int b = Math.round(hue(p, q, h - 1F / 3F) * 255F);
        return (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    private static float hue(float p, float q, float t)
    {
        if(t < 0F) { t += 1F; }
        if(t > 1F) { t -= 1F; }
        if(t < 1F / 6F) { return p + (q - p) * 6F * t; }
        if(t < 1F / 2F) { return q; }
        if(t < 2F / 3F) { return p + (q - p) * (2F / 3F - t) * 6F; }
        return p;
    }

    private static int clampByte(int v)
    {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * One of the game's ramps re-tinted so its middle is {@code base}.
     * <p>
     * <b>The shape is borrowed, not invented.</b> A skin ramp is not a straight
     * line from black to white through a colour -- `hadapal_01` desaturates
     * towards its highlight and holds a warm shadow, and the art leans on that:
     * a face averages index 215 of 192..223, so it lives in the top quarter
     * where a fitted ramp is already washing out. Scaling a real one channel by
     * channel keeps every one of those relationships and only moves the colour.
     * <p>
     * Scaled about the ramp's own midpoint, so asking for exactly what a preset
     * already is returns that preset unchanged.
     *
     * @param reference one of the shipped ramps, {@link #RAMP} entries 0xRRGGBB
     * @param base      0xRRGGBB, the colour the middle should become
     */
    public static int[] tint(int[] reference, int base)
    {
        if(reference == null || reference.length < RAMP)
        {
            return ramp(base);
        }
        int middle = reference[RAMP / 2];
        int[] out = new int[RAMP];
        for(int i = 0; i < RAMP; i++)
        {
            int packed = 0;
            for(int shift = 16; shift >= 0; shift -= 8)
            {
                int was = (middle >> shift) & 0xFF;
                int want = (base >> shift) & 0xFF;
                int here = (reference[i] >> shift) & 0xFF;
                // A channel the reference has none of cannot be scaled into
                // one, so it is offset instead -- otherwise a ramp with a
                // black-ish shadow could never be tinted at all.
                int value = was == 0 ? here + want : Math.round(here * (want / (float) was));
                packed |= Math.min(255, Math.max(0, value)) << shift;
            }
            out[i] = packed;
        }
        return out;
    }

    /**
     * Which shipped ramp is the closest starting point for a colour.
     * <p>
     * By lightness, because that is what the three differ in and what decides
     * how much headroom a tint has: tinting the palest ramp towards a dark skin
     * would clip every shadow to black, and the dark one towards a pale skin
     * would clip every highlight to white.
     *
     * @return a 1-based tone number
     */
    public static int nearestTone(int[][] tones, int base)
    {
        if(tones == null || tones.length == 0)
        {
            return 1;
        }
        int want = lightness(base);
        int best = 1;
        int gap = Integer.MAX_VALUE;
        for(int i = 0; i < tones.length; i++)
        {
            if(tones[i] == null || tones[i].length < RAMP)
            {
                continue;
            }
            int here = Math.abs(lightness(tones[i][RAMP / 2]) - want);
            if(here < gap)
            {
                gap = here;
                best = i + 1;
            }
        }
        return best;
    }

    private static int lightness(int rgb)
    {
        // Rec. 601, which is what everything else in this mod weights with.
        return (299 * ((rgb >> 16) & 0xFF) + 587 * ((rgb >> 8) & 0xFF)
            + 114 * (rgb & 0xFF)) / 1000;
    }

    /** Writes a ramp over a slot, keeping each entry's own alpha. */
    private static void paint(int[] palette, int at, int[] colours)
    {
        for(int i = 0; i < RAMP && at + i < palette.length; i++)
        {
            int rgb = colours[i * colours.length / RAMP] & 0xFFFFFF;
            palette[at + i] = (palette[at + i] & 0xFF000000) | rgb;
        }
    }
}
