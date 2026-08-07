package de.cas_ual_ty.dueldimension.duel.outfit;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What a duelist can be seen wearing.
 * <p>
 * The same shape the Crysis mod's suit skins use: an outfit is a player skin
 * texture drawn over the player by a feature layer, rather than armour models
 * bolted onto the body. That keeps a duelist looking like a person in a jacket
 * instead of a person in a box, costs one texture per outfit, and means adding
 * one is content — a 64x64 skin and a line here — rather than code.
 * <p>
 * Both sides read this class: the server to validate what a client claims to be
 * wearing, the client to draw it and to list it in the hub.
 */
public final class Outfits
{
    /**
     * @param id      what the profile stores and the wire carries
     * @param name    shown in the hub
     * @param texture the skin drawn over the player, or null for no outfit
     * @param credit  who made it, shown on hover; empty when there is nobody to
     *                credit. Kept beside the outfit rather than in a readme, so
     *                the attribution travels with the thing it is for.
     */
    public record Outfit(String id, String name, ResourceLocation texture, String credit)
    {
    }

    private static ResourceLocation skin(String file)
    {
        return new ResourceLocation(de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID,
            "textures/entity/outfit/" + file + ".png");
    }

    /** No outfit: the player's own skin, unchanged. */
    public static final Outfit NONE = new Outfit("", "None", null, "");

    public static final Outfit YUSEI = new Outfit("yusei", "Yusei's Outfit", skin("yusei"),
        "Credit: Daiosity (PlanetMinecraft)");

    public static final List<Outfit> ALL = List.of(NONE, YUSEI);

    private Outfits()
    {
    }

    /** The outfit with this id, or {@link #NONE} — never null, and never a guess. */
    public static Outfit byId(String id)
    {
        if(id == null || id.isEmpty())
        {
            return NONE;
        }
        for(Outfit outfit : ALL)
        {
            if(outfit.id().equals(id))
            {
                return outfit;
            }
        }
        return NONE;
    }

    /** Whether an id names a real outfit; what the server checks a claim against. */
    public static boolean exists(String id)
    {
        return id == null || id.isEmpty() || byId(id) != NONE;
    }
}
