package de.cas_ual_ty.dueldimension.clientutil.character;

import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.character.CharacterMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The client's own character: what it is, and telling the server about it.
 * <p>
 * <b>Held here rather than read back off the render state.</b> The editor needs
 * a character to show before anyone else has been told about it — a duellist
 * turning a colour slider should see it move, not see it move once the server
 * has agreed — so this is the working copy and the broadcast is what makes it
 * everyone else's.
 * <p>
 * The server is still the one that saves it. Nothing here is authoritative; it
 * is the difference between a preview and a fact.
 */
public final class CharacterEdits
{
    private static CharacterLook editing = CharacterLook.DEFAULT;
    private static boolean known;

    private CharacterEdits()
    {
    }

    /**
     * The character this client is editing or wearing.
     * <p>
     * Seeded from what the server said this player is wearing the first time it
     * is asked, so opening the editor starts from the character somebody
     * already has rather than from the default.
     */
    public static CharacterLook look()
    {
        if(!known)
        {
            Minecraft minecraft = Minecraft.getInstance();
            CharacterLook worn = minecraft.player == null ? null
                : ClientCharacters.look(minecraft.player.getUUID());
            if(worn != null)
            {
                editing = worn;
            }
            known = true;
        }
        return editing;
    }

    /** Changes the character and tells the server, which tells everybody. */
    public static void set(CharacterLook look)
    {
        editing = look == null ? CharacterLook.DEFAULT : look;
        known = true;
        send(worn());
    }

    /** Whether this client is currently being drawn as its character. */
    public static boolean worn()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
            && ClientCharacters.isWearing(minecraft.player.getUUID());
    }

    /** Puts the character on, or takes it off. */
    public static void wear(boolean on)
    {
        send(on);
    }

    private static void send(boolean on)
    {
        // Shown immediately rather than waiting for the round trip, so the
        // editor and the world agree while the packet is in flight. The server
        // sends the same thing straight back, which either confirms it or
        // corrects it.
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.player != null)
        {
            ClientCharacters.set(minecraft.player.getUUID(), look(), on);
        }
        ClientPlayNetworking.send(new CharacterMessages.SetCharacter(look(), on));
    }

    /** Forgotten on disconnect, so a second server does not inherit the first's. */
    public static void clear()
    {
        editing = CharacterLook.DEFAULT;
        known = false;
    }
}
