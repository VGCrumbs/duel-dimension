package de.cas_ual_ty.dueldimension.character;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * The index map and palette of every part that can be recoloured.
 * <p>
 * The model file carries each texture already painted, which is what a
 * character looks like before anyone chooses anything. Recolouring is a change
 * to the PALETTE, so what is needed is the palette that was and which pixel used
 * which entry. Repainting the finished image by matching colours instead would
 * work right up until two parts of a texture happened to share one — and that is
 * not a fault anybody would trace back to here.
 * <p>
 * Written by {@code NexusDecomp/scripts/export_glb.py}:
 * <pre>
 *     "DDPAL\0" u16 version, u16 parts
 *     per part: u8 name length, name, u16 width, u16 height,
 *               u16 colours, colours x RGBA, width*height index bytes
 * </pre>
 * zlib'd whole, because an index map is long runs of the same byte.
 * <p>
 * Duel disks are absent, and on purpose: they reserve none of the ramp slots and
 * spend all 256 entries on their own art.
 */
public final class CharacterPalettes
{
    /** One part's texture, as indices into a palette. */
    public record Part(String name, int width, int height, byte[] indices, int[] palette)
    {
        public int pixels()
        {
            return width * height;
        }
    }

    private static final byte[] MAGIC = {'D', 'D', 'P', 'A', 'L', 0};

    private final Map<String, Part> parts;
    /**
     * The game's own three skin ramps, 32 colours each, 0xAARRGGBB.
     * <p>
     * `hadapal_01..03` out of `skin.pac`, shipped rather than approximated. A
     * ramp fitted black-to-colour-to-white puts the chosen colour at its
     * MIDPOINT, and the art does not sit at the midpoint -- a face averages
     * index 215 of 192..223, which is three quarters of the way along, and past
     * the middle every fitted ramp is climbing to white. All three tones
     * therefore arrived washed out and near-identical: the difference between
     * them lived in the half of the ramp the art never touches.
     * <p>
     * Empty on a version-1 file, which had none.
     */
    private final int[][] tones;

    private CharacterPalettes(Map<String, Part> parts, int[][] tones)
    {
        this.parts = parts;
        this.tones = tones;
    }

    /**
     * One of the game's skin ramps, 1-based, or null if this file has none.
     *
     * @return {@link CharacterRamps#RAMP} colours of 0xRRGGBB
     */
    public int[] tone(int which)
    {
        return which >= 1 && which <= tones.length ? tones[which - 1] : null;
    }

    /** All of them, for choosing between. Never null; empty on an old file. */
    public int[][] tones()
    {
        return tones;
    }

    /** How many the file carries. */
    public int toneCount()
    {
        return tones.length;
    }

    /** One part by the name the exporter gave it, e.g. {@code wear01}. */
    public Part part(String slot, int number)
    {
        return parts.get(slot + (number < 10 ? "0" : "") + number);
    }

    public int size()
    {
        return parts.size();
    }

    public static CharacterPalettes read(byte[] compressed)
    {
        byte[] raw = inflate(compressed);
        ByteBuffer in = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for(byte expected : MAGIC)
        {
            if(in.get() != expected)
            {
                throw new IllegalArgumentException("not a DDPAL palette file");
            }
        }
        int version = in.getShort() & 0xFFFF;
        if(version != 1 && version != 2)
        {
            throw new IllegalArgumentException("DDPAL version " + version
                + " is not supported");
        }
        int count = in.getShort() & 0xFFFF;
        Map<String, Part> found = new HashMap<>();
        for(int i = 0; i < count; i++)
        {
            byte[] name = new byte[in.get() & 0xFF];
            in.get(name);
            int width = in.getShort() & 0xFFFF;
            int height = in.getShort() & 0xFFFF;
            int colours = in.getShort() & 0xFFFF;
            int[] palette = new int[colours];
            for(int c = 0; c < colours; c++)
            {
                int r = in.get() & 0xFF;
                int g = in.get() & 0xFF;
                int b = in.get() & 0xFF;
                int a = in.get() & 0xFF;
                palette[c] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            byte[] indices = new byte[width * height];
            in.get(indices);
            found.put(new String(name, StandardCharsets.US_ASCII),
                new Part(new String(name, StandardCharsets.US_ASCII),
                    width, height, indices, palette));
        }
        // Version 2 appends the skin ramps. Read only if the file says so, so
        // a palette file from before they were shipped still loads.
        int[][] tones = new int[0][];
        if(version >= 2 && in.remaining() >= 1)
        {
            tones = new int[in.get() & 0xFF][];
            for(int t = 0; t < tones.length; t++)
            {
                tones[t] = new int[CharacterRamps.RAMP];
                for(int c = 0; c < CharacterRamps.RAMP; c++)
                {
                    int r = in.get() & 0xFF;
                    int g = in.get() & 0xFF;
                    int b = in.get() & 0xFF;
                    in.get();
                    tones[t][c] = (r << 16) | (g << 8) | b;
                }
            }
        }
        return new CharacterPalettes(Map.copyOf(found), tones);
    }

    private static byte[] inflate(byte[] compressed)
    {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
        byte[] chunk = new byte[64 * 1024];
        try
        {
            while(!inflater.finished())
            {
                int got = inflater.inflate(chunk);
                if(got == 0 && (inflater.needsInput() || inflater.needsDictionary()))
                {
                    break;
                }
                out.write(chunk, 0, got);
            }
        }
        catch(java.util.zip.DataFormatException broken)
        {
            throw new IllegalArgumentException("palette file is not zlib", broken);
        }
        finally
        {
            inflater.end();
        }
        return out.toByteArray();
    }

    /**
     * Which entries of a part draw its iris, worked out once.
     * <p>
     * Cached because {@link CharacterRamps#iris} walks every pixel of the part
     * and the answer cannot change: it is a property of the shipped art, not of
     * anything the player picked. Without the cache it would run on every
     * repaint, which is every time a colour slider moves.
     */
    private final java.util.Map<String, int[]> irises = new java.util.HashMap<>();

    private int[] iris(Part part)
    {
        return irises.computeIfAbsent(part.name(), name ->
            CharacterRamps.iris(part.palette(), part.indices(), part.width()));
    }

    /**
     * One part's texture, recoloured, as 0xAARRGGBB rows.
     * <p>
     * The whole of the customisation reaches the model here: 256 palette entries
     * are rewritten and then every pixel is looked up through them, which is a
     * pass over 16k pixels rather than anything per-frame.
     */
    public int[] paint(String slot, int number, int[] skin, CharacterLook look)
    {
        Part got = part(slot, number);
        if(got == null)
        {
            return null;
        }
        int[] palette = CharacterRamps.recolour(got.palette(), slot, skin, look,
            iris(got));
        int[] out = new int[got.pixels()];
        for(int i = 0; i < out.length; i++)
        {
            int index = got.indices()[i] & 0xFF;
            out[i] = index < palette.length ? palette[index] : 0;
        }
        return out;
    }
}
