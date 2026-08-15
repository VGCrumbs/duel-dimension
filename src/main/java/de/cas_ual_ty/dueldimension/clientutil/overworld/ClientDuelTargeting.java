package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PromptOptions;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What the duellist is looking at, and whether they can do anything with it.
 * <p>
 * The two states of §9's freelook, and the reason they are only two: while no
 * screen is open the mouse turns the camera and this follows it; while a menu is
 * open the mouse is the cursor and this holds still, so the card that was being
 * looked at when the menu opened is the card the menu is about. Vanilla already
 * releases and recaptures the cursor when a screen opens and closes, so that
 * half is the game's job and not this class's.
 * <p>
 * Recomputed once a client tick rather than once a frame: a duellist standing at
 * a board is not moving, twenty answers a second is far more than a person can
 * point with, and the picker walks every zone on the board each time.
 */
public final class ClientDuelTargeting
{
    private ClientDuelTargeting()
    {
    }

    /** How far down the board a duellist can reach, in blocks. */
    private static final double REACH = 24D;

    private static BoardTarget looking;
    private static boolean actionable;

    /** What the player is looking at on the board, or null. */
    public static BoardTarget looking()
    {
        return looking;
    }

    /** Is there anything the engine is currently offering for it? */
    public static boolean actionable()
    {
        return actionable;
    }

    /**
     * Sets the target from a freed cursor rather than from the crosshair.
     * <p>
     * While the pointer is open {@link #tick} deliberately holds still -- a
     * screen is open, and the crosshair is no longer what the duellist is
     * aiming with. Without this the cursor would move over the board and the
     * board would not respond to it, which reads as the pointer not working at
     * all. The pointer knows what it is over; this is how it says so.
     */
    public static void point(BoardTarget target)
    {
        looking = target;
        actionable = PromptOptions.actionable(DuelClientState.prompt, false, target);
    }

    /** Forgets the target: the duel ended, or the board went away. */
    public static void clear()
    {
        looking = null;
        actionable = false;
    }

    /**
     * Follows the player's view. Held still while a screen is open, which is
     * what preserves the target a menu was opened about.
     */
    public static void tick(Minecraft client)
    {
        FieldSiting siting = ClientDuelField.siting();
        if(siting == null || !ClientDuelField.locked())
        {
            clear();
            return;
        }
        if(client.gui.screen() != null)
        {
            return;
        }
        LocalPlayer player = client.player;
        if(player == null)
        {
            clear();
            return;
        }

        FieldTransform transform = new FieldTransform(siting);
        Vec3 eye = player.getEyePosition(1F);
        float[] field = BoardPicker.aim(transform, eye, player.getLookAngle(), REACH);
        // The board that is DRAWN, so the crosshair cannot point at a square
        // the eye is not being shown -- a spectator is sent a stripped copy and
        // was being aimed with the duellist's.
        looking = BoardPicker.at(ClientDuelField.boardToDraw(),
            Math.max(0, ClientDuelField.seat()), field,
            target -> de.cas_ual_ty.dueldimension.clientutil.PromptOptions.optionsFor(
                DuelClientState.prompt, false, target).isEmpty() ? 0 : 4);
        // The engine decides what is legal, here as everywhere: this only asks
        // which of the options it already sent are about the card being looked
        // at. Nothing here knows a rule.
        actionable = PromptOptions.actionable(DuelClientState.prompt, false, looking);
    }
}
