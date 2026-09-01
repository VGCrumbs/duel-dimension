package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.platform.NativeImage;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repaints the hub's furniture in the active theme.
 *
 * <h2>One set of art, any number of palettes</h2>
 * The textures under {@code textures/gui/indexed} are not pictures. Each pixel
 * carries an INSTRUCTION -- red says which role it belongs to (the menu surface
 * or the accent), green says where on that role's ramp it sits, from black at 0
 * through the role's own colour at 128 to white at 255. Given a
 * {@link MenuTheme}, {@link #build} turns one of those back into a real texture.
 * <p>
 * That is why five presets and any colour a player picks all come off ten files
 * rather than off five copies of every PNG -- and why a colour picked at
 * runtime can be honoured at all, which shipping pre-rendered variants could
 * not do.
 *
 * <h2>Resolved at the choke point</h2>
 * Every panel, button, tab, slot, scrollbar and chip in the mod is drawn by
 * {@link NineSlice#draw}, without exception -- so that is the only place that
 * has to ask. No screen knows this class exists, and none had to change.
 *
 * <h2>Built once per theme, not per frame</h2>
 * {@link #resolve} is called several times a frame. After the first look it is
 * one map lookup; a texture that fails to build caches its own failure so a
 * missing file does not mean a decode attempt every frame forever.
 */
public final class MenuThemes
{
    /** The masters live here, and nothing else does. */
    private static final String INDEXED = "textures/gui/indexed/";

    private static MenuTheme chosen = MenuTheme.GRAPHITE;

    /**
     * A theme the screen on top insists on, whatever the player chose.
     * <p>
     * The Duel Bot is the only thing that does this: it is a machine, and it
     * asks its question in Cobalt. Set for the length of one screen's paint and
     * cleared in a finally, so an exception mid-draw cannot leave every other
     * menu wearing it.
     */
    private static MenuTheme pinned;

    private static final Map<String, Identifier> BUILT = new HashMap<>();

    private MenuThemes()
    {
    }

    public static MenuTheme active()
    {
        return pinned != null ? pinned : chosen;
    }

    /** What the player chose, ignoring anything a screen has pinned over it. */
    public static MenuTheme chosen()
    {
        return chosen;
    }

    public static void choose(MenuTheme theme)
    {
        // Compared WHOLE, not by key. The key exists to tell two sets of built
        // textures apart, so it carries the surface and the accent and not the
        // font -- and a font-only change therefore has the same key. Comparing
        // keys here made choosing a new font a no-op, which is precisely what
        // "the text colour is not wired up" looked like from the outside.
        if(theme == null || theme.equals(chosen))
        {
            return;
        }
        // ...and the textures are only thrown away when something they are
        // actually painted from has moved. A font is not.
        boolean repaint = !theme.key().equals(chosen.key());
        chosen = theme;
        if(repaint)
        {
            forget();
        }
    }

    public static void pin(MenuTheme theme)
    {
        pinned = theme;
    }

    public static void unpin()
    {
        pinned = null;
    }

    /**
     * Drops every built texture.
     * <p>
     * Called when the choice changes, which is a button press rather than
     * anything inside a frame -- releasing a texture the current frame is part
     * way through drawing would not be safe.
     */
    private static void forget()
    {
        Minecraft client = Minecraft.getInstance();
        List<Identifier> going = new ArrayList<>(BUILT.values());
        BUILT.clear();
        for(Identifier id : going)
        {
            if(id != null && DuelDimension.MOD_ID.equals(id.getNamespace())
                && id.getPath().startsWith("themed/"))
            {
                client.getTextureManager().release(id);
            }
        }
    }

    /**
     * The themed stand-in for an indexed master, or the texture itself if it is
     * not one.
     */
    public static Identifier resolve(Identifier texture)
    {
        if(texture == null || !texture.getPath().startsWith(INDEXED))
        {
            return texture;
        }
        MenuTheme theme = active();
        String name = texture.getPath().substring(INDEXED.length());
        String key = theme.key() + '/' + name;
        if(BUILT.containsKey(key))
        {
            Identifier built = BUILT.get(key);
            return built != null ? built : texture;
        }
        Identifier built = build(theme, texture, key);
        BUILT.put(key, built);
        return built != null ? built : texture;
    }

    private static Identifier build(MenuTheme theme, Identifier master, String key)
    {
        Minecraft client = Minecraft.getInstance();
        try(InputStream stream = client.getResourceManager().open(master))
        {
            NativeImage source = NativeImage.read(stream);
            NativeImage out = new NativeImage(source.getWidth(), source.getHeight(), false);
            for(int y = 0; y < source.getHeight(); y++)
            {
                for(int x = 0; x < source.getWidth(); x++)
                {
                    out.setPixel(x, y, paint(theme, source.getPixel(x, y)));
                }
            }
            source.close();
            Identifier id = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
                "themed/" + key.replaceAll("[^a-z0-9/._-]", "_"));
            // The texture keeps the image and uploads from it, so it is not
            // closed here -- release() disposes of both.
            client.getTextureManager().register(id, new DynamicTexture(() -> key, out));
            return id;
        }
        catch(Exception unusable)
        {
            DuelDimension.warn("could not theme " + master + ": " + unusable);
            return null;
        }
    }

    /** One pixel: role and ramp position in, a colour out. */
    private static int paint(MenuTheme theme, int argb)
    {
        int alpha = (argb >>> 24) & 0xFF;
        if(alpha == 0)
        {
            return 0;
        }
        int role = (argb >> 16) & 0xFF;
        int level = (argb >> 8) & 0xFF;
        return (alpha << 24) | ramp(role >= 128 ? theme.accent() : theme.surface(), level);
    }

    /**
     * Black at 0, the role's own colour at 128, white at 255.
     * <p>
     * Two segments rather than one multiply, because the art needs to go
     * BRIGHTER than the base as well as darker: the light rim of every bevel in
     * the hub is above its surface, and a plain tint can only ever darken.
     */
    private static int ramp(int base, int level)
    {
        int out = 0;
        for(int shift = 16; shift >= 0; shift -= 8)
        {
            int channel = (base >> shift) & 0xFF;
            int value = level <= 128
                ? Math.round(channel * (level / 128F))
                : Math.round(channel + (255 - channel) * ((level - 128) / 127F));
            out |= Math.max(0, Math.min(255, value)) << shift;
        }
        return out;
    }
}
