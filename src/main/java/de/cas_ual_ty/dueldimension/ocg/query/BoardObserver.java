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
}
