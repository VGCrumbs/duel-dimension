package de.cas_ual_ty.dueldimension.clientutil.character;

import com.mojang.blaze3d.platform.NativeImage;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.character.CharacterPalettes;
import de.cas_ual_ty.dueldimension.character.CharacterRamps;
import de.cas_ual_ty.dueldimension.clientutil.model.GlbModel;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The player characters: two model files, and a texture per set of colours.
 * <p>
 * <b>One file per gender holds every part.</b> Fifteen bodies, hairstyles, faces
 * and duel disks share one skeleton, and a character is four of them. Baking is
 * therefore done ONCE per gender rather than once per character — the geometry
 * of hair 3 does not depend on who is wearing it — and choosing a character is
 * choosing which four of the sixty primitives to draw. See
 * {@code NexusDecomp/scripts/export_glb.py} for how the file is built.
 * <p>
 * <b>Colours are a palette rewrite, not a tint.</b> The art is authored greyscale
 * in three reserved slots and the game fills them in; a shader tint would wash
 * the whole part instead of the skin or the jacket. So a look's textures are
 * built by rewriting 256 palette entries and mapping the index map through them,
 * which is a pass over 16k pixels done when the colours change and never per
 * frame.
 * <p>
 * <b>Registered textures are never taken back.</b> A {@link DynamicTexture} and
 * its upload belong to the texture manager for the session, so building one per
 * look-and-slot is a leak if looks are unbounded. They are not: a player has one
 * look at a time and the editor changes it a slider at a time, so the cache is
 * bounded by how much fiddling one session does — and {@link #forget} exists for
 * the editor to hand back what it was only previewing.
 */
public final class CharacterModels
{
    private static final String BASE = "models/character/";

    private static final Map<Character, ModelMesh> MESHES = new HashMap<>();
    private static final Map<Character, CharacterPalettes> PALETTES = new HashMap<>();
    /** Which primitive is which part, in the order the exporter writes them. */
    private static final Map<Character, Map<String, Integer>> INDEX = new HashMap<>();
    /**
     * How many painted textures to keep before dropping the least recently
     * used.
     * <p>
     * <b>Keyed by LOOK, not by player</b>, so a server where everybody picked
     * the same character costs one set. Where everybody is different it costs
     * three each — body, hair and face; a duel disk is not repainted — and a
     * hundred and twenty is forty distinct characters on screen at once, which
     * is more than are ever drawn at a time and about eight megabytes.
     * <p>
     * The bound is what makes this safe rather than a slow leak: a registered
     * texture and its upload belong to the texture manager until something
     * hands them back, and the editor builds a new one for every position a
     * colour slider passes through.
     */
    private static final int KEEP = 120;

    private static final Map<String, ResourceLocation> TEXTURES =
        new java.util.LinkedHashMap<>(16, 0.75F, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest)
            {
                if(size() <= KEEP)
                {
                    return false;
                }
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
        };
    private static final Map<Character, Boolean> FAILED = new HashMap<>();

    /**
     * The order the exporter emits parts in, which is the contract between it
     * and this class. Fifteen of each, bodies first.
     */
    private static final String[] SLOTS = {"wear", "hair", "face", "disc"};

    private CharacterModels()
    {
    }

    /**
     * The baked model for a gender, or null if it could not be read.
     * <p>
     * Must be called on the render thread: baking registers textures.
     */
    public static ModelMesh mesh(char gender)
    {
        char which = gender == 'm' ? 'm' : 'f';
        if(MESHES.containsKey(which))
        {
            return MESHES.get(which);
        }
        if(Boolean.TRUE.equals(FAILED.get(which)))
        {
            return null;
        }
        try
        {
            byte[] bytes = read(BASE + "character_" + which + ".glb");
            ModelMesh baked = ModelMesh.bake(GlbModel.load(bytes), "character_" + which);
            MESHES.put(which, baked);
            Map<String, Integer> order = new HashMap<>();
            int at = 0;
            for(String slot : SLOTS)
            {
                for(int number = 1; number <= CharacterLook.PARTS; number++)
                {
                    order.put(slot + number, at++);
                }
            }
            INDEX.put(which, Map.copyOf(order));
            return baked;
        }
        catch(Exception broken)
        {
            // Once. A model that will not read will not read the next frame
            // either, and this is called from a renderer.
            FAILED.put(which, Boolean.TRUE);
            DuelDimension.warn("the character model for '" + which
                + "' could not be read: " + broken);
            return null;
        }
    }

    private static CharacterPalettes palettes(char gender)
    {
        char which = gender == 'm' ? 'm' : 'f';
        return PALETTES.computeIfAbsent(which, key ->
        {
            try
            {
                return CharacterPalettes.read(read(BASE + "character_" + key + ".pal"));
            }
            catch(Exception broken)
            {
                DuelDimension.warn("the character palettes for '" + key
                    + "' could not be read: " + broken);
                return null;
            }
        });
    }

    private static byte[] read(String path) throws java.io.IOException
    {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, path);
        Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(id);
        if(found.isEmpty())
        {
            throw new java.io.FileNotFoundException(id.toString());
        }
        try(InputStream in = found.get().open())
        {
            return in.readAllBytes();
        }
    }

    /**
     * Which primitive draws a slot for this look, or -1 if the model is absent.
     */
    public static int primitive(CharacterLook look, String slot)
    {
        Map<String, Integer> order = INDEX.get(look.gender());
        if(order == null)
        {
            return -1;
        }
        Integer found = order.get(slot + look.part(slot));
        return found == null ? -1 : found;
    }

    /**
     * The texture a slot is drawn with for this look, building it if needed.
     * <p>
     * A duel disk has no recolourable slots, so it keeps the texture the model
     * file shipped — asking the palettes for one would return nothing and drop
     * the disk from the character.
     */
    public static ResourceLocation texture(CharacterLook look, String slot)
    {
        ModelMesh baked = mesh(look.gender());
        int part = primitive(look, slot);
        if(baked == null || part < 0 || part >= baked.parts().size())
        {
            return null;
        }
        ResourceLocation shipped = baked.parts().get(part).texture();
        if("disc".equals(slot))
        {
            return shipped;
        }
        String key = look.key() + "/" + slot;
        ResourceLocation known = TEXTURES.get(key);
        if(known != null)
        {
            return known;
        }
        CharacterPalettes palettes = palettes(look.gender());
        if(palettes == null)
        {
            return shipped;
        }
        int[] pixels = palettes.paint(slot, look.part(slot), skinRamp(palettes, look), look);
        CharacterPalettes.Part source = palettes.part(slot, look.part(slot));
        if(pixels == null || source == null)
        {
            return shipped;
        }
        NativeImage image = new NativeImage(source.width(), source.height(), false);
        for(int y = 0; y < source.height(); y++)
        {
            for(int x = 0; x < source.width(); x++)
            {
                // The palette holds ARGB; 1.21.1's NativeImage is ABGR in
                // memory order, so the two ends swap. 26.2's setPixel takes
                // ARGB straight -- see PORTING.md, and MenuThemes for the same
                // swap on the themed menu art.
                image.setPixelRGBA(x, y, abgr(pixels[y * source.width() + x]));
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "character/" + sanitise(key));
        Minecraft.getInstance().getTextureManager()
            .register(id, new DynamicTexture(image));
        TEXTURES.put(key, id);
        return id;
    }

    /** The ramp a look's skin actually resolves to, for the editor to show. */
    public static int[] tone(CharacterLook look)
    {
        return skinRamp(palettes(look.gender()), look);
    }

    /**
     * What a colour would look like as skin, without committing to it.
     * <p>
     * The same path the model takes, so the strip under the wheel is the ramp
     * that will land on the character rather than a picture of one.
     */
    public static int[] skinPreview(CharacterLook look, int rgb)
    {
        return skinRamp(palettes(look.gender()), look.withSkinRgb(rgb));
    }

    /**
     * What this face's own eyes are, for the wheel to open on.
     * <p>
     * A character nobody has retinted has no eye colour of its own recorded --
     * the field is zero, meaning "as drawn" -- so the wheel would otherwise open
     * on black and the first drag would look like a jump. This averages the
     * entries {@link CharacterRamps#iris} found, which is the colour the artist
     * actually used, weighted by nothing: an iris is a handful of entries and
     * the mean of them reads as the eye.
     *
     * @return 0xRRGGBB, or a plain brown if this face has no iris to read --
     *         closed eyes, or covered ones
     */
    public static int eyePreview(CharacterLook look)
    {
        CharacterPalettes got = palettes(look.gender());
        if(got != null)
        {
            CharacterPalettes.Part face = got.part("face", look.face());
            if(face != null)
            {
                int[] iris = CharacterRamps.iris(face.palette(), face.indices(), face.width());
                long r = 0;
                long g = 0;
                long b = 0;
                for(int at : iris)
                {
                    int argb = face.palette()[at];
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                }
                if(iris.length > 0)
                {
                    return (int) (r / iris.length) << 16
                        | (int) (g / iris.length) << 8
                        | (int) (b / iris.length);
                }
            }
        }
        return 0x6B4A2F;
    }

    /**
     * The skin ramp for a look's tone.
     * <p>
     * <b>The game's own table, not a curve through a chosen colour.</b> These
     * are `hadapal_01..03`, shipped in the palette file -- see
     * {@link CharacterPalettes#tone}. Approximating them put the tone at the
     * MIDDLE of a ramp whose upper three quarters is where the art actually
     * looks, so all three came out pale and nearly the same.
     * <p>
     * The fallback is the old approximation, for a palette file written before
     * the ramps were shipped. It is wrong in the way described above; it is
     * here so a stale file draws a character rather than nothing.
     */
    private static int[] skinRamp(CharacterPalettes palettes, CharacterLook look)
    {
        if(palettes != null && look.mixedSkin())
        {
            // A colour the player mixed: the closest shipped ramp, re-tinted.
            // Closest by LIGHTNESS, because that decides how much headroom the
            // tint has -- see CharacterRamps.nearestTone.
            int[] reference = palettes.tone(
                CharacterRamps.nearestTone(palettes.tones(), look.skinRgb()));
            if(reference != null)
            {
                return CharacterRamps.tint(reference, look.skinRgb());
            }
        }
        int[] shipped = palettes == null ? null : palettes.tone(look.tone());
        if(shipped != null)
        {
            return shipped;
        }
        return CharacterRamps.ramp(switch(look.tone())
        {
            case 2 -> 0xC08A5E;
            case 3 -> 0x8A5E3C;
            default -> 0xE8B89C;
        });
    }

    /**
     * ARGB to 1.21.1's in-memory ABGR, and back; the swap is its own inverse.
     * <p>
     * The same helper {@code MenuThemes} needs for the same reason. Red and
     * blue trade places and the other two bytes stay put.
     */
    private static int abgr(int packed)
    {
        return (packed & 0xFF00FF00) | ((packed >> 16) & 0xFF) | ((packed & 0xFF) << 16);
    }

    /** Texture paths take a narrow alphabet; a look's key is wider than that. */
    private static String sanitise(String key)
    {
        StringBuilder out = new StringBuilder(key.length());
        for(int i = 0; i < key.length(); i++)
        {
            char c = key.charAt(i);
            out.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' ? c : '_');
        }
        return out.toString();
    }

    /**
     * Drops a look's built textures.
     * <p>
     * For the editor, which builds one on every change and would otherwise leave
     * a texture behind for every position a slider passed through.
     */
    public static void forget(CharacterLook look)
    {
        for(String slot : SLOTS)
        {
            ResourceLocation id = TEXTURES.remove(look.key() + "/" + slot);
            if(id != null)
            {
                Minecraft.getInstance().getTextureManager().release(id);
            }
        }
    }
}
