package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The playmats a duelist can bring to the table.
 * <p>
 * A duel draws two mats, not one: yours on your half and your opponent's on
 * theirs, each the right way up for its owner — so theirs reads upside down
 * from your seat, exactly as two physical mats laid end to end do. The
 * reference client has no equivalent (it draws a single field texture chosen by
 * the rules era, duelclient.cpp picking tField by duel_field), so this is ours;
 * every mat still carries field4's printed zone borders and pile boxes over its
 * own backdrop, so a custom mat never costs you the ability to read the field.
 * <p>
 * Each entry is one PNG under {@code textures/duel/mats}, laid out exactly like
 * field4.png. Adding a handmade mat means dropping in the file and adding a
 * line here.
 */
public enum PlayMats
{
    CLASSIC("classic", "Classic", 0x8FA3B8),
    CRIMSON("crimson", "Crimson", 0xD8563F),
    ABYSS("abyss", "Abyss", 0x2FB6C8),
    VERDANT("verdant", "Verdant", 0x4FBF6A),
    AMETHYST("amethyst", "Amethyst", 0xA678D8),
    SANDSTORM("sandstorm", "Sandstorm", 0xD8B25A);

    public static final List<PlayMats> ALL = List.of(values());

    private final String id;
    private final String displayName;
    private final int accent;
    private final ResourceLocation texture;

    PlayMats(String id, String displayName, int accent)
    {
        this.id = id;
        this.displayName = displayName;
        this.accent = accent;
        this.texture = new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/mats/" + id + ".png");
    }

    public String id()
    {
        return id;
    }

    public String displayName()
    {
        return displayName;
    }

    public ResourceLocation texture()
    {
        return texture;
    }

    /**
     * The mat's signature colour, matching the slot outlines printed on its
     * own art. The zones the mat itself does not cover -- the piles, the extra
     * monster zones, the field spell -- are tinted with this, so a duelist's
     * whole half of the table carries their theme rather than just the middle.
     */
    public int accent()
    {
        return accent;
    }

    public PlayMats next()
    {
        return ALL.get((ordinal() + 1) % ALL.size());
    }

    /** Falls back to the default rather than failing on an unknown id. */
    public static PlayMats byId(String id)
    {
        for(PlayMats mat : ALL)
        {
            if(mat.id.equals(id))
            {
                return mat;
            }
        }
        return CLASSIC;
    }
}
