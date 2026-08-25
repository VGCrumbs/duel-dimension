package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.resources.Identifier;

/**
 * What a card on the field looks like: its face, its back, and which of the two
 * a given slot is showing.
 * <p>
 * <b>Lifted, not copied.</b> This was private inside {@code BoardRenderer}, and
 * a near-copy already existed in {@code DuelAnimations}. A third copy for the
 * world board would have made the face-down policy three places that must agree,
 * and the failure mode of them disagreeing is not a cosmetic one -- it is a set
 * card showing its art. One implementation, three callers.
 * <p>
 * The policy itself is unchanged from the 2D board, including the two decisions
 * worth restating:
 * <ul>
 * <li>a face-up card whose art is missing is NOT drawn as a face-down card. It
 * gets the "unknown card" art, which says "no picture" without lying about the
 * game state. The engine plays from EDOPro's database, which is far larger than
 * this mod's, so that case is common on the opponent's field;</li>
 * <li>each side gets its own back, because EDOPro does ({@code tCover[controler]}),
 * and a player's own sleeve stands in for it when they have one.</li>
 * </ul>
 */
public final class CardFaces
{
    private CardFaces()
    {
    }

    /**
     * NOT {@code activeCardMainImageSize}. That setting defaults to 64 and
     * governs DOWNLOADED card art, fetched at whatever size is asked for; a
     * sleeve is shipped at all seven sizes, so paying for the large one costs
     * only the texture memory of the one sleeve on the table. At 64 the field
     * drew a 56-pixel back stretched over roughly 210 real pixels, which is
     * what made sleeves look coarse beside the cards.
     */
    public static final int SLEEVE_FIELD_SIZE = 512;

    /** The back this controller's cards are wearing. */
    public static Identifier back(int controller)
    {
        if(controller != 0)
        {
            // The opponent's own sleeve, not a fixed back. Falling through to
            // COVER_OPPONENT when they have none keeps the two sides distinct
            // for the common case where only one player has sleeved up.
            CardSleevesType theirs = DuelClientState.opponentSleeve;
            if(theirs == null || theirs.isCardBack())
            {
                return DuelTextures.COVER_OPPONENT;
            }
            return theirs.getMainRL(SLEEVE_FIELD_SIZE);
        }
        CardSleevesType sleeve = DuelClientState.ownSleeve;
        if(sleeve == null || sleeve.isCardBack())
        {
            return DuelTextures.COVER;
        }
        return sleeve.getMainRL(SLEEVE_FIELD_SIZE);
    }

    /**
     * The face this slot is showing: its art, or its back.
     * <p>
     * A concealed slot arrives with {@code code == 0} -- the server never sends
     * a face-down card's identity -- so this cannot show art it was not given,
     * whatever the caller asks for. That is the whole reason the 3D board can
     * be crawled under safely.
     *
     * @param inHand a face-down card in your OWN hand is one you may look at
     */
    public static Identifier face(BoardSnapshot.Slot slot, boolean inHand, int controller)
    {
        if(showsBack(slot, inHand))
        {
            return back(controller);
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        // Not (byte)0: this copy may have been dressed in the deck editor and
        // the index rode here on the slot. artIndex folds anything the card
        // does not actually have back to the printed art, so a stale choice
        // degrades to the right card rather than to nothing.
        return properties == null ? DuelTextures.UNKNOWN
            : DuelTextures.card(properties, DuelTextures.artIndex(properties, slot.art()),
                DuelTextures.FIELD_CARD_SIZE);
    }

    /**
     * Does this slot show its back rather than its face?
     * <p>
     * Split out from {@link #face} because this is the rule and the rest is a
     * texture lookup. It is also the one part that can be tested without a
     * running game -- {@code DuelClientState} reads the config directory in its
     * static initialiser, so anything that resolves an actual texture needs a
     * real client, and this is the half worth pinning anyway.
     * <p>
     * Two conditions, and the first is the one that matters: a slot with no
     * code is a slot the server concealed, and there is nothing to reveal even
     * if every other check were wrong.
     */
    public static boolean showsBack(BoardSnapshot.Slot slot, boolean inHand)
    {
        return slot.code() == 0 || (slot.faceDown() && !inHand);
    }

    /**
     * The card's own art, whatever face it is turned to -- or its back when
     * this client was never told what it is.
     * <p>
     * For the UNDERSIDE of a card. A set card is lying face DOWN, so its face
     * is against the table and somebody looking up at it from below is looking
     * at the face; drawing a back there is drawing a card with two backs.
     * <p>
     * It leaks nothing, and that is a property of the DATA rather than of this
     * method: a client is only sent the code of a face-down card it is allowed
     * to know about -- its own. The opponent's set card arrives with no code at
     * all, so this returns their back for the same reason the top face does,
     * and no viewing angle can produce art that was never sent.
     */
    public static Identifier underside(BoardSnapshot.Slot slot, int controller)
    {
        if(slot.code() == 0)
        {
            return back(controller);
        }
        Properties properties = DdDatabase.PROPERTIES_LIST.get((long)slot.code());
        return properties == null ? DuelTextures.UNKNOWN
            : DuelTextures.card(properties, DuelTextures.artIndex(properties, slot.art()),
                DuelTextures.FIELD_CARD_SIZE);
    }

    /**
     * Is this texture already card-shaped, so it is drawn through the full UV
     * range rather than the {@link DuelTextures#CARD_U0} letterbox window?
     * <p>
     * Compares against the fields as they are NOW, deliberately: the same
     * fields are the only producer of a back, so producer and predicate cannot
     * disagree even though the player can change their card back mid-game. A
     * sleeve is none of these and falls to the letterboxed branch, which is
     * right -- sleeve art is a square canvas with the card inside the window.
     */
    /**
     * The window to sample a card texture through.
     * <p>
     * The predicate was shared and its four-line consequence was not, so every
     * caller wrote out the same conditional pair of UVs -- and three places on
     * the flat board wrote out the PREDICATE again as well. One texture that
     * stopped being letterboxed would have had to be remembered in all of them.
     */
    public record Window(float u0, float v0, float u1, float v1)
    {
    }

    /** Card-shaped art fills its file; a downloaded card sits in a window of one. */
    private static final Window WHOLE = new Window(0F, 0F, 1F, 1F);
    private static final Window LETTERBOXED = new Window(DuelTextures.CARD_U0,
        DuelTextures.CARD_V0, DuelTextures.CARD_U1, DuelTextures.CARD_V1);

    /** Which of the two this texture wants. */
    public static Window window(Identifier texture)
    {
        return isCardShaped(texture) ? WHOLE : LETTERBOXED;
    }

    public static boolean isCardShaped(Identifier texture)
    {
        return texture.equals(DuelTextures.COVER) || texture.equals(DuelTextures.COVER_OPPONENT)
            || texture.equals(DuelTextures.UNKNOWN);
    }
}
