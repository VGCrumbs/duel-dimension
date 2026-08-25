package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;

/**
 * One answer to "is a duel using the screen right now?".
 * <p>
 * Asked by everything that has to get out of a duel's way: Jade's tooltip,
 * Xaero's minimap, and whatever comes next. Kept in one place so those answers
 * cannot drift apart -- a duel that hides one overlay and not another is worse
 * than one that hides neither, because it looks like a bug rather than a
 * decision.
 * <p>
 * True for BOTH presentations. The world board owns the screen a duellist is
 * looking through; the duel screen owns the screen outright.
 */
public final class DuelSuppression
{
    private DuelSuppression()
    {
    }

    /** Should overlays that are not part of the duel stay off the screen? */
    public static boolean hudHidden()
    {
        return inDuel();
    }

    /**
     * Is this client's player in a duel at all, in either presentation?
     * <p>
     * The same question the overlays ask, given its own name because movement
     * asks it too and "the HUD is hidden" is not a reason to stand still. A
     * duellist is at a board: walking away from it is not a move the duel has,
     * and on the world board it would carry the camera off the field entirely.
     */
    public static boolean inDuel()
    {
        return ClientDuelField.locked() || duelOnScreen();
    }

    /**
     * Is a duel actually running on this client?
     * <p>
     * Asked of the board rather than of a flag, for the same reason the server
     * asks its seat registry: a flag set when a duel begins and cleared when it
     * ends is one abnormal ending away from hiding a player's minimap forever.
     * The board is EMPTY between duels and is replaced the moment one starts.
     */
    private static boolean duelOnScreen()
    {
        BoardSnapshot board = DuelClientState.board;
        return board != null && board != BoardSnapshot.EMPTY && !DuelClientState.over;
    }
}
