package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import de.cas_ual_ty.dueldimension.duel.outfit.WornOutfits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;

/**
 * What a duelist is drawn as: the skin under their outfit, and the body both sit
 * on.
 * <p>
 * On Forge this took four baked models, a hidden vanilla body and a layer that
 * redrew the player from scratch, because the skin itself could not be changed
 * without a mixin and the body type came with it. Here it is one patch to
 * {@code getSkin}, for a reason worth writing down: the game picks the player
 * renderer — and therefore the classic or slim body — from
 * {@code getSkin().model()}. Answer that question correctly and the arms are
 * the right width before anything is drawn, so there is nothing to hide and
 * nothing to redraw. {@link OutfitLayer} is then only the outfit.
 * <p>
 * Two things are decided here.
 * <p>
 * <b>Which skin.</b> The player's edited under-skin if they have one and are
 * actually wearing something ({@link UnderSkin} says why), otherwise a skin this
 * mod supplies for them ({@link PlayerSkins}), otherwise the game's own answer.
 * <p>
 * <b>Which body.</b> Its own, normally — the two layouts put the arm faces at
 * different offsets, so a skin on the wrong body samples a neighbouring face
 * down the edge of the hand, a stray column that cannot be erased because it is
 * not in the part of the texture anyone would think to erase. The exception is a
 * slim outfit: the outfit is the outer surface and has to contain what is under
 * it, and a three-pixel sleeve cannot contain a four-pixel arm. A classic outfit
 * over a slim skin has no such problem, which is why the rule is one-sided.
 */
public final class OutfitSkins
{
    /**
     * An outfit to answer with instead of the worn one, for the local player
     * only, while a wardrobe tile is being extracted.
     * <p>
     * The hub previews outfits the player is not wearing, and a preview that
     * went down a second code path would be a preview of something else. So it
     * goes down this one: set, extract, clear — all on the render thread, within
     * a single call, so nothing else can observe it set.
     */
    private static Outfits.Outfit previewing;

    private OutfitSkins()
    {
    }

    /** The outfit this player is drawn in, never null. */
    public static Outfits.Outfit worn(AbstractClientPlayer player)
    {
        if(previewing != null && player == Minecraft.getInstance().player)
        {
            return previewing;
        }
        return WornOutfits.of(player.getUUID());
    }

    /** @param outfit what to answer with for the local player, or null to stop */
    public static void previewing(Outfits.Outfit outfit)
    {
        previewing = outfit;
    }

    /**
     * What to change about the skin the game resolved, or null to leave it be.
     * <p>
     * Takes the game's answer rather than asking for it: this runs from inside
     * {@code getSkin}, and asking there would call itself.
     * <p>
     * A {@code Patch} rather than a rebuilt {@code PlayerSkin} because only the
     * fields named are replaced, so a cape, an elytra texture and anything added
     * later survive untouched.
     */
    public static PlayerSkin.Patch patch(AbstractClientPlayer player, PlayerSkin resolved)
    {
        Outfits.Outfit worn = worn(player);
        PlayerSkins.Skin supplied = PlayerSkins.of(player);

        Identifier texture = null;
        if(worn.texture() != null && player == Minecraft.getInstance().player)
        {
            // Local to this client and never sent: everyone else sees the real
            // skin under the same outfit, which is the honest thing for a
            // cosmetic that only its owner has edited.
            texture = UnderSkin.texture();
        }
        if(texture == null && supplied != null)
        {
            texture = supplied.texture();
        }

        PlayerModelType body = null;
        if(worn.texture() != null && worn.slim())
        {
            body = PlayerModelType.SLIM;
        }
        else if(supplied != null)
        {
            body = supplied.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        }

        if(texture == null && body == null)
        {
            return null;
        }
        return new PlayerSkin.Patch(
            texture == null ? Optional.empty() : Optional.of(asset(texture)),
            Optional.empty(),
            Optional.empty(),
            body == null ? Optional.empty() : Optional.of(body));
    }

    /**
     * A skin file, named the way a {@code ClientAsset} wants to be named.
     * <p>
     * The one-argument constructor takes an <em>asset id</em> and derives the
     * file from it — {@code ns:foo} becomes {@code ns:textures/foo.png}. Handing
     * it a path that is already complete wraps it a second time, and the result
     * is a texture that does not exist: the player renders in the missing-texture
     * magenta, which is exactly what happened.
     * <p>
     * Everything else in this mod holds full paths, because that is what a
     * render layer draws with. So the two-argument constructor is used and the
     * id is worked back out of the path, which keeps the pair consistent —
     * the id is what the one-argument form would have been given.
     */
    private static ClientAsset.ResourceTexture asset(Identifier texture)
    {
        String path = texture.getPath();
        if(path.startsWith("textures/") && path.endsWith(".png"))
        {
            path = path.substring("textures/".length(), path.length() - ".png".length());
        }
        return new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath(texture.getNamespace(), path), texture);
    }
}
