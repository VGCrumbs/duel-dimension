package de.cas_ual_ty.dueldimension.clientutil.character;

import de.cas_ual_ty.dueldimension.character.CharacterLook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every player looks like, as this client knows it.
 * <p>
 * A {@code DuelProfile} is synced only to its owner, so without the broadcast
 * behind this map every other client would draw a stranger as a vanilla player.
 * The same reason {@code ClientWornDisks} exists.
 * <p>
 * <b>Only players actually WEARING a character are in here.</b> Somebody who has
 * made one and turned it off is removed rather than stored with a flag, so
 * "should this player be drawn as a character" is one lookup with no second
 * condition to forget — and a player nobody has heard of is drawn the vanilla
 * way, which is the right answer for anyone without the mod's character.
 * <p>
 * Cleared on disconnect: the map is keyed by UUID and nothing else empties it,
 * so a second server would otherwise start with the first one's characters on
 * whoever happened to share a UUID.
 */
public final class ClientCharacters
{
    private static final Map<UUID, CharacterLook> WORN = new ConcurrentHashMap<>();

    private ClientCharacters()
    {
    }

    public static void set(UUID player, CharacterLook look, boolean shown)
    {
        if(!shown || look == null || !look.valid())
        {
            WORN.remove(player);
            return;
        }
        WORN.put(player, look);
    }

    /** The character this player is wearing, or null if they are not. */
    public static CharacterLook look(UUID player)
    {
        return player == null ? null : WORN.get(player);
    }

    public static boolean isWearing(UUID player)
    {
        return look(player) != null;
    }

    public static void clear()
    {
        WORN.clear();
    }

    /** How many distinct looks are on screen, which is what the texture cache costs. */
    public static int distinctLooks()
    {
        return (int) WORN.values().stream().map(CharacterLook::key).distinct().count();
    }
}
