package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldPayloads;
import net.minecraft.client.Minecraft;

/**
 * What this client knows about the duel field it is standing at.
 * <p>
 * One holder for the whole client, like {@code DuelClientState}, because a
 * player is at one board at a time and everything that draws or reads the board
 * -- the markers, the mesh, the picker, the input mode -- has to agree about
 * which one. Written only by the packet handler on the client thread.
 * <p>
 * Holds nothing about the duel: no cards, no hand, no life points. Those
 * already have a home that redacts them per seat, and duplicating any of it
 * here would be a second place to get hidden information wrong.
 */
public final class ClientDuelField
{
    private ClientDuelField()
    {
    }

    private static FieldSiting siting;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level;
    private static int seat = -1;
    private static boolean locked;
    private static de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot spectatorBoard;

    /** The field standing in the world, or null when there is none to draw here. */
    public static FieldSiting siting()
    {
        return present() ? siting : null;
    }

    /** Which end of the board this player belongs at, or -1 when only watching. */
    public static int seat()
    {
        return seat;
    }

    /** Is this client watching a duel rather than playing one? */
    public static boolean spectating()
    {
        return present() && seat < 0;
    }

    /**
     * The board to draw.
     * <p>
     * A duellist draws their own state; a spectator draws the separately
     * redacted copy the server sent them, which is the ONLY board state a
     * bystander is ever given. Choosing between them here rather than at the
     * renderer keeps the two from ever being confused for one another.
     */
    public static de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot boardToDraw()
    {
        return spectating() ? spectatorBoard
            : de.cas_ual_ty.dueldimension.clientutil.DuelClientState.board;
    }

    /** Takes a spectator's copy of the duel. */
    public static void applySpectatorBoard(
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot board)
    {
        spectatorBoard = board;
    }

    /**
     * True once this player is standing at the board and the duel is under way;
     * false while they still have to walk to their mark. The difference is what
     * separates a marker to walk to from a duel to play.
     */
    public static boolean locked()
    {
        // present(), not the bare flag. Everything that DRAWS the board is
        // gated on present(); if the thing that suppresses the duel screen is
        // not, the two disagree the moment a player changes dimension and the
        // duellist is left with no board and no screen either.
        return present() && locked;
    }

    /**
     * Is there a field to draw, here, now?
     * <p>
     * The dimension is part of the question. A board remembered while its owner
     * steps through a portal would otherwise be drawn at the same coordinates
     * in the Nether, over ground that has nothing to do with it.
     */
    public static boolean present()
    {
        Minecraft client = Minecraft.getInstance();
        return siting != null && client.level != null
            && client.level.dimension().equals(level);
    }

    /**
     * Which hotbar slot was selected when the duel began, or -1 outside one.
     * <p>
     * The hotbar is hidden for the length of a duel. A hidden hotbar that can
     * still be scrolled is worse than a visible one -- what is in hand changes
     * with nothing on screen to say so -- so the slot is pinned to whatever it
     * was, and released when the board goes.
     */
    private static int heldSlot = -1;

    /**
     * When the board's own ending began -- which is not when the duel ended.
     * <p>
     * Zero until the last animation has finished playing. A duel is decided the
     * moment the engine says so, but the CLIENT is still several seconds behind
     * that: the attack that won it, the damage it dealt and the monster it
     * destroyed are all still queued, because they ride the same ordered stream
     * as the result and are played in turn. Starting the ending on the result
     * would talk over the very thing being celebrated.
     */
    private static long endingBegan;

    /** How long the outcome is held at full strength before it starts to go. */
    private static final long HOLD_MS = 1400L;
    /**
     * And how long it takes to go.
     * <p>
     * Long, and meant to be. A board is a table with a duel laid out on it, and
     * the end of a duel is the one moment worth looking at the whole thing --
     * so it dissipates at about the pace somebody would take to sit back from
     * it, rather than at the pace of a screen being closed.
     */
    private static final long FADE_MS = 5000L;

    /**
     * How solid the board is drawn, from one down to nothing.
     * <p>
     * A duel that was won by a direct attack used to end with the board simply
     * gone -- the server took it away the instant the engine decided, which was
     * before the client had played the attack that did it. So the last thing a
     * duellist saw was their monster standing still, and then nothing at all.
     * Now the board waits for its own animations, says who won, and goes.
     */
    public static float endingAlpha()
    {
        if(!de.cas_ual_ty.dueldimension.clientutil.DuelClientState.over)
        {
            endingBegan = 0L;
            return 1F;
        }
        // Not while anything is still playing. The result is at the END of the
        // queue, so a busy animator means the winning blow has not landed yet.
        if(de.cas_ual_ty.dueldimension.clientutil.DuelClientState.animations.isBusy())
        {
            endingBegan = 0L;
            return 1F;
        }
        long now = System.currentTimeMillis();
        if(endingBegan == 0L)
        {
            endingBegan = now;
        }
        long since = now - endingBegan;
        return since < HOLD_MS ? 1F
            : Math.max(0F, 1F - (since - HOLD_MS) / (float)FADE_MS);
    }

    /** Is the board saying who won right now? */
    public static boolean ending()
    {
        return present() && de.cas_ual_ty.dueldimension.clientutil.DuelClientState.over;
    }

    /**
     * Takes the board down once it has finished saying goodbye, and hands over
     * to the result screen.
     * <p>
     * Done on the CLIENT rather than waiting to be told, so the board goes at
     * the exact moment it has finished fading rather than whenever a packet
     * happens to arrive. The server keeps its copy up for longer than this
     * takes and takes it down afterwards, which makes that side a backstop
     * rather than the thing being waited on.
     */
    public static void advanceEnding()
    {
        if(!ending() || endingAlpha() > 0F)
        {
            return;
        }
        clear();
        ClientDuelTargeting.clear();
        endingBegan = 0L;
        // The result screen, with the reward that follows it. Exactly what a
        // duel on the screen does at this point -- only now it happens after
        // the board has had its say instead of instead of it.
        //
        // Straight there, rather than by opening the duel screen and letting it
        // forward: its deadlines run from when the engine decided the duel, and
        // the board's goodbye has already outlasted them, so it would replace
        // itself on its first tick and show one frame of a 2D duel on the way.
        // true: the board held the outcome for HOLD_MS and spent FADE_MS going
        // away, so this player has already been told who won.
        de.cas_ual_ty.dueldimension.clientutil.DuelClientState.finish(true);
    }

    /**
     * Shift, asked of the window rather than of a key event.
     * <p>
     * A question about a key being HELD while the view moves, not about one
     * having been pressed -- and asked from the renderer as well as from the
     * cursor, neither of which is given key events. One copy, because the card
     * text and the card's stats appear on the same hold and appearing on
     * slightly different holds would read as one of them being broken.
     */
    public static boolean shiftHeld()
    {
        com.mojang.blaze3d.platform.Window window =
            net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** Puts the selected slot back if something moved it during a duel. */
    public static void holdHotbar(net.minecraft.client.Minecraft client)
    {
        if(client.player == null)
        {
            return;
        }
        if(!locked())
        {
            heldSlot = -1;
            return;
        }
        if(heldSlot < 0)
        {
            heldSlot = client.player.getInventory().getSelectedSlot();
        }
        else if(client.player.getInventory().getSelectedSlot() != heldSlot)
        {
            client.player.getInventory().setSelectedSlot(heldSlot);
        }
    }

    /**
     * True while the player has asked to watch a world duel on the 2D screen.
     * <p>
     * The board and the screen are two views of one duel, and the screen is
     * normally handed back the moment the board can take the question again.
     * That is right when the screen opened by itself, for a prompt the board
     * could not answer -- and wrong when the player asked for it, which is why
     * asking is remembered rather than inferred from a screen being open.
     */
    private static boolean preferScreen;

    /** Is the player deliberately on the duel screen during a world duel? */
    public static boolean screenPreferred()
    {
        return preferScreen;
    }

    /** Swaps between the board and the duel screen. */
    public static void toggleScreen(net.minecraft.client.Minecraft client)
    {
        if(!locked())
        {
            return;
        }
        preferScreen = !preferScreen;
        if(preferScreen)
        {
            de.cas_ual_ty.dueldimension.clientutil.DuelClientState.openScreen();
        }
        else if(client.gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.EngineDuelScreen open)
        {
            open.onClose();
        }
    }

    /** Is this player walking to a mark right now? */
    public static boolean walking()
    {
        return present() && !locked;
    }

    public static void apply(OverworldPayloads.ShowField field)
    {
        // A board arriving IS a duel beginning, so nothing of the last one's
        // ending may survive into it.
        //
        // Both halves matter and they fail differently. A stale `over` makes
        // ending() true the instant present() is, so advanceEnding fades out a
        // duel that has just started and reports the one before it. A stale
        // endingBegan is worse and quieter: endingAlpha measures from it, and a
        // timestamp from the previous duel is already past HOLD_MS + FADE_MS, so
        // the fade is skipped entirely and the new board is cleared in a tick.
        endingBegan = 0L;
        de.cas_ual_ty.dueldimension.clientutil.DuelClientState.discardEnding();
        siting = field.siting();
        level = field.level();
        seat = field.seat();
        locked = field.locked();
    }

    /**
     * Forgets the field. Also called when leaving a world, because a board
     * remembered across a disconnect would be drawn into the next one.
     */
    public static void clear()
    {
        siting = null;
        level = null;
        seat = -1;
        locked = false;
        // With the board gone nothing calls endingAlpha, which is the only
        // other thing that zeroes this -- so a field cleared mid-fade would
        // leave a timestamp behind for the next duel to measure against.
        endingBegan = 0L;
        heldSlot = -1;
        preferScreen = false;
        spectatorBoard = null;
    }
}
