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
     * A {@link net.minecraft.world.entity.player.PlayerSkin} for a texture this
     * mod ships.
     * <p>
     * Built by patching the default skin rather than calling a constructor: a
     * PlayerSkin also carries a cape, an elytra texture and a "secure" flag, and
     * a patch leaves all of those at sensible values instead of making this
     * class invent them.
     * <p>
     * Note what the id has to be. The one-argument {@code ResourceTexture}
     * constructor takes an ASSET id and derives the file from it -- {@code ns:foo}
     * becomes {@code ns:textures/foo.png} -- so a complete path handed to it is
     * wrapped twice and the result does not exist. Everything here holds
     * complete paths, so the id is worked back out of the path.
     */
    public static net.minecraft.world.entity.player.PlayerSkin skinFor(ResourceLocation texture,
        boolean slim)
    {
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin()
            .with(new net.minecraft.world.entity.player.PlayerSkin.Patch(
                java.util.Optional.of(asset(texture)),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(slim
                    ? net.minecraft.world.entity.player.PlayerModelType.SLIM
                    : net.minecraft.world.entity.player.PlayerModelType.WIDE)));
    }

    /**
     * A complete texture path, named the way a {@code ClientAsset} wants it.
     * <p>
     * The one-argument {@code ResourceTexture} constructor takes an ASSET id and
     * derives the file from it -- {@code ns:foo} becomes
     * {@code ns:textures/foo.png} -- so a path that is already complete gets
     * wrapped a second time and the result does not exist. There is no warning
     * for it; the player simply renders in missing-texture magenta. Everything
     * in this mod holds complete paths, so the id is worked back out of the
     * path and the two-argument constructor is used.
     */
    public static net.minecraft.core.ClientAsset.ResourceTexture asset(ResourceLocation texture)
    {
        String path = texture.getPath();
        if(path.startsWith("textures/") && path.endsWith(".png"))
        {
            path = path.substring("textures/".length(), path.length() - ".png".length());
        }
        return new net.minecraft.core.ClientAsset.ResourceTexture(
            ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), path), texture);
    }

    /** The skin this mod supplies for that player, or null to leave them alone. */
    public static Skin of(AbstractClientPlayer player)
    {
        String name = player.getGameProfile() == null ? null : player.getGameProfile().name();
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
     * What to change about the skin the game resolved, or null to leave it be.
     * <p>
     * Takes the game's answer rather than asking for it: this runs from inside
     * {@code getSkin}, and asking there would call itself.
     * <p>
     * A {@code Patch} rather than a rebuilt {@code PlayerSkin} because only the
     * fields named are replaced, so a cape, an elytra texture and anything
     * added later survive untouched.
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
    public static net.minecraft.world.entity.player.PlayerSkin.Patch patch(
        AbstractClientPlayer player, net.minecraft.world.entity.player.PlayerSkin resolved)
    {
        Skin supplied = of(player);
        if(supplied == null)
        {
            return null;
        }
        return new net.minecraft.world.entity.player.PlayerSkin.Patch(
            java.util.Optional.of(asset(supplied.texture())),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.of(supplied.slim()
                ? net.minecraft.world.entity.player.PlayerModelType.SLIM
                : net.minecraft.world.entity.player.PlayerModelType.WIDE));
    }

    public static Skin resolve(AbstractClientPlayer player)
    {
        Skin override = of(player);
        if(override != null)
        {
            return override;
        }
        // The two separate accessors became one PlayerSkin record, which is a
        // better shape: a skin and the body it is drawn on always travelled
        // together and could previously disagree. The texture is behind a
        // ClientAsset now rather than being a bare ResourceLocation.
        net.minecraft.world.entity.player.PlayerSkin skin = player.getSkin();
        return new Skin(skin.body().texturePath(),
            skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
    }
}
