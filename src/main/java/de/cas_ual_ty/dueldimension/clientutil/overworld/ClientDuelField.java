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
        return locked;
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

    /** Is this player walking to a mark right now? */
    public static boolean walking()
    {
        return present() && !locked;
    }

    public static void apply(OverworldPayloads.ShowField field)
    {
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
        spectatorBoard = null;
    }
}
