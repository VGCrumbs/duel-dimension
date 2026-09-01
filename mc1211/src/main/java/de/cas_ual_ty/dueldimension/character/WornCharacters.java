package de.cas_ual_ty.dueldimension.character;

import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who has made a character, and who is currently wearing one.
 * <p>
 * Kept on the profile, so it is saved with everything else a duellist owns and
 * survives a restart. Broadcast separately, because a profile is synced only to
 * its owner — without this every other client would draw a stranger as
 * themselves.
 * <p>
 * The same shape as {@link de.cas_ual_ty.dueldimension.duel.dueldisk.WornDisks},
 * for the same reason: this is a fact about a player that everybody has to see.
 */
public final class WornCharacters
{
    private WornCharacters()
    {
    }

    public static CharacterLook look(ServerPlayer player)
    {
        return DuelProfiles.get(player).characterLook();
    }

    /** Whether this player is currently being drawn as their character. */
    public static boolean isWearing(ServerPlayer player)
    {
        return player != null && DuelProfiles.get(player).characterShown();
    }

    /** Takes what a client asked for, and tells everyone. */
    public static void set(ServerPlayer player, CharacterLook look, boolean shown)
    {
        DuelProfile profile = DuelProfiles.get(player);
        profile.setCharacter(look, shown);
        DuelProfiles.save(player);
        announce(player);
    }

    /**
     * Tells everybody about this player, and this player about everybody.
     * <p>
     * Called on join, and again whenever somebody changes. The arriving player
     * is sent to DIRECTLY rather than found by walking the player list, because
     * on join they may not be in it yet — the same trap the duel disks hit.
     */
    public static void announce(ServerPlayer player)
    {
        MinecraftServer server = player.level().getServer();
        if(server == null)
        {
            return;
        }
        DuelProfile profile = DuelProfiles.get(player);
        CharacterMessages.WornCharacter mine = new CharacterMessages.WornCharacter(
            player.getUUID(), profile.characterLook(), profile.characterShown());
        ServerPlayNetworking.send(player, mine);

        for(ServerPlayer other : server.getPlayerList().getPlayers())
        {
            if(other == player)
            {
                continue;
            }
            ServerPlayNetworking.send(other, mine);
            DuelProfile theirs = DuelProfiles.get(other);
            ServerPlayNetworking.send(player, new CharacterMessages.WornCharacter(
                other.getUUID(), theirs.characterLook(), theirs.characterShown()));
        }
    }
}
