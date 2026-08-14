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
