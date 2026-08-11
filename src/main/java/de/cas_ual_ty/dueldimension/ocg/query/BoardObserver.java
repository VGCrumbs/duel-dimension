package de.cas_ual_ty.dueldimension.ocg.query;

/**
 * A responder's window onto the field. Deliberately narrow: it can look, but
 * it cannot drive the duel (no process/setResponse), and by default it only
 * shows what its player is entitled to see.
 */
public interface BoardObserver
{
    /** The honest view for this responder's player. */
    BoardState observe();

    /**
     * The view with hidden information intact. Reserved for duelists
     * explicitly configured to cheat; using this elsewhere silently turns an
     * NPC into a mind-reader.
     */
    BoardState observeOmnisciently();

    /**
     * The cards in THIS responder's own main deck, already de-ordered.
     * <p>
     * A player is entitled to know which cards are in the deck they built; they
     * are not entitled to know where any of them is. The engine makes no such
     * distinction — {@code OCG_DuelQueryLocation} walks {@code list_main} front
     * to back and the draw pops {@code list_main.back()}, so the last element of
     * an untouched query IS the next card to be drawn. The implementation
     * therefore de-orders the list inside the same call frame that asked for it,
     * so a true-ordered deck never exists anywhere a later edit could reach.
     * <p>
     * There is no parameter, and that is the protection: an observer is built
     * for one seat and closes over that seat's number, so this method
     * structurally cannot be asked for the other player's deck.
     */
    java.util.List<CardView> ownDeck();

    /**
     * The artwork worn by the copy the ENGINE has just named at this exact
     * (controller, location, sequence), or 0 when this viewer may not identify
     * that card.
     * <p>
     * This exists for the cards a prompt offers that are in no board snapshot.
     * The main deck is only ever COUNTED for the client, so when an effect lets
     * a player pick a card out of it there is nothing on the client side to read
     * an artwork from, and every such card came back wearing its printed one.
     * The engine knows; it is the only thing that does.
     * <p>
     * Unlike {@link #ownDeck()} this does take a controller, so the seat check
     * cannot be made by an absent parameter and has to be made inside the
     * implementation instead — which is still one place, and still not the
     * caller's to get wrong. Only ever a single card, never a list: asking the
     * core for a whole deck LOCATION would hand back its true order.
     * <p>
     * {@code expectedCode} is the passcode the message carried for that card.
     * An answer whose code differs is discarded rather than trusted, so a
     * sequence that has gone stale lends nobody else's artwork.
     * <p>
     * Duel thread only, like every other query.
     */
    int coverOf(int controller, int location, int sequence, int expectedCode);
}
