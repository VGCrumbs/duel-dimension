package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;

/**
 * Skins this mod supplies for named players, and the body they are drawn on.
 * <p>
 * A development client runs offline. Offline means no session, no session means
 * no profile properties, and no profile properties means Minecraft has nowhere
 * to fetch a skin from — so every dev player is Steve regardless of who they
 * actually are. This is the answer to that: a table of names to skins shipped
 * with the mod, read when the player is drawn.
 * <p>
 * By name rather than by UUID on purpose. An offline player's id is derived from
 * their name and differs from their real one, so the name is the only thing that
 * is the same in both worlds.
 */
public final class PlayerSkins
{
    /**
     * @param texture the skin to draw
     * @param slim    whether it was drawn for the Alex body — three-pixel arms,
     *                and a different arm layout in the texture. Wrong here and
     *                the sleeves land in the wrong place.
     */
    public record Skin(ResourceLocation texture, boolean slim)
    {
    }

    private static ResourceLocation skin(String file)
    {
        return ResourceLocation.fromNamespaceAndPath(de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID,
            "textures/entity/player/" + file + ".png");
    }

    private static final Skin VGCRUMBS = new Skin(skin("vgcrumbs"), true);

    /**
     * Names to skins, lower case.
     * <p>
     * "alpha" is the name the development client launches under, and is here so
     * that a developer testing the mod sees themselves rather than Steve.
     */
    private static final Map<String, Skin> BY_NAME = Map.of(
        "vgcrumbs", VGCRUMBS,
        "alpha", VGCRUMBS);

    private PlayerSkins()
    {
    }

    /**
     * A {@link net.minecraft.client.resources.PlayerSkin} for a texture this
     * mod ships.
     * <p>
     * 1.21.1's PlayerSkin is a flat record and there is no {@code Patch} type to
     * lay over a default, so this calls the constructor with exactly the values
     * vanilla's own defaults carry: the texture, no texture URL, no cape, no
     * elytra, the body type, and secure. Checked against
     * {@code DefaultPlayerSkin}, which builds its eighteen built-in skins that
     * way.
     * <p>
     * There is no {@code ClientAsset} on 1.21.1 either -- a PlayerSkin's texture
     * is a bare, complete ResourceLocation -- so the asset-id derivation 26.2
     * needed is gone rather than ported.
     */
    public static net.minecraft.client.resources.PlayerSkin skinFor(ResourceLocation texture,
        boolean slim)
    {
        return new net.minecraft.client.resources.PlayerSkin(texture, null, null, null,
            slim ? net.minecraft.client.resources.PlayerSkin.Model.SLIM
                : net.minecraft.client.resources.PlayerSkin.Model.WIDE,
            true);
    }

    /** The skin this mod supplies for that player, or null to leave them alone. */
    public static Skin of(AbstractClientPlayer player)
    {
        String name = player.getGameProfile() == null ? null : player.getGameProfile().getName();
        if(name == null)
        {
            return null;
        }
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * What to draw this player's body with, and on which shape — an override if
     * there is one, otherwise whatever the game already resolved.
     */
    /**
     * The skin the game resolved with this mod's override applied, or null to
     * leave it be.
     * <p>
     * Takes the game's answer rather than asking for it: this runs from inside
     * {@code getSkin}, and asking there would call itself.
     * <p>
     * 1.21.1 has no {@code Patch} type, so the replacement is rebuilt field by
     * field out of the resolved skin: only the texture and the body type change,
     * and the cape, the elytra texture, the texture URL and the secure flag are
     * carried across untouched. That is what the 26.2 patch did; here it is
     * spelled out rather than expressed by a type, which means a field added to
     * PlayerSkin later would have to be added here too.
     * <p>
     * The body matters as much as the texture. The game picks the player
     * renderer -- and therefore the classic or slim arms -- from
     * {@code getSkin().model()}, and the two layouts put the arm faces at
     * different offsets: a skin on the wrong body samples a neighbouring face
     * down the edge of the hand, a stray column that cannot be erased because
     * it is not in the part of the texture anyone would think to erase.
     * <p>
     * This lived in {@code OutfitSkins} while outfits existed, because dressing
     * a player and supplying them a skin were answered by one patch. Outfits
     * are shelved; supplying a skin is not, and on a development client -- no
     * session, so no profile properties, so nowhere for the game to fetch a
     * skin from -- it is the only reason anyone is not Steve.
     */
    public static net.minecraft.client.resources.PlayerSkin patched(
        AbstractClientPlayer player, net.minecraft.client.resources.PlayerSkin resolved)
    {
        Skin supplied = of(player);
        if(supplied == null)
        {
            return null;
        }
        return new net.minecraft.client.resources.PlayerSkin(
            supplied.texture(),
            resolved.textureUrl(),
            resolved.capeTexture(),
            resolved.elytraTexture(),
            supplied.slim() ? net.minecraft.client.resources.PlayerSkin.Model.SLIM
                : net.minecraft.client.resources.PlayerSkin.Model.WIDE,
            resolved.secure());
    }

    public static Skin resolve(AbstractClientPlayer player)
    {
        Skin override = of(player);
        if(override != null)
        {
            return override;
        }
        // One PlayerSkin record rather than two separate accessors, which is a
        // better shape: a skin and the body it is drawn on always travelled
        // together and could previously disagree. On 1.21.1 its texture is a
        // bare ResourceLocation -- there is no ClientAsset in the way.
        net.minecraft.client.resources.PlayerSkin skin = player.getSkin();
        return new Skin(skin.texture(),
            skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM);
    }
}
