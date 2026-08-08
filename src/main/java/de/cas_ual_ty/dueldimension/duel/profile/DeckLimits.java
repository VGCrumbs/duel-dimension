package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.duel.match.Banlist;

/**
 * Whether one more copy of a card may go into a deck, and if not, why.
 * <p>
 * Four separate rules can each refuse, and the editor needs to say which,
 * because "you cannot add that" is not actionable and the four have completely
 * different remedies — collect another copy, free a slot, pick a different
 * banlist, or nothing at all:
 * <ul>
 * <li>the part is full (main 60, extra 15, side 15);</li>
 * <li>the deck already holds three, the hard ceiling for any card;</li>
 * <li>the banlist allows fewer than three, or none at all;</li>
 * <li>the player does not own that many copies.</li>
 * </ul>
 * The copy rules count across main, extra and side <em>together</em>: three in
 * the side deck plus one in the main is four copies of that card.
 * <p>
 * Note the trunk is never spent. Owning three copies lets all of a player's
 * decks hold three each at the same time; the count is a ceiling on any one
 * deck, not a pool that decks draw down.
 */
public final class DeckLimits
{
    /** The hard ceiling on copies of one card in one deck, before any banlist. */
    public static final int MAX_COPIES = 3;

    /** The verdict on adding a card, and the reason when it is refused. */
    public record Verdict(boolean allowed, String reason)
    {
        public static final Verdict OK = new Verdict(true, "");

        public static Verdict no(String reason)
        {
            return new Verdict(false, reason);
        }
    }

    private DeckLimits()
    {
    }

    /**
     * May one more copy go into a deck draft?
     * <p>
     * Ownership deliberately is not part of this answer. The editor is also a
     * planning tool: missing cards may be saved in a deck, but
     * {@link #validate} keeps that deck out of duels until the copies are owned
     * or free mode is enabled.
     */
    /**
     * Whether a card may go in that part of the deck at all.
     * <p>
     * Fusion, Synchro, Xyz and Link monsters live in the Extra Deck and
     * nothing else may; the Main Deck takes the rest. The Side Deck takes
     * either, because it is swapped into both halves between games.
     * <p>
     * Asked here rather than at each place a card can be put down, because
     * there are several -- dropping one on a grid, the card menu's "Add 1",
     * double-clicking in the pool -- and every one of them already comes
     * through {@link #canAddToDraft}. The editor used the card's own home
     * when it chose the part for you, but an explicit drop said where it
     * went, so a Main Deck card dropped on the Extra grid stayed there.
     * <p>
     * A card the database cannot resolve is allowed through: an unknown
     * passcode is a database problem and refusing it here would report it as
     * a deck-building one.
     */
    public static Verdict belongsIn(DeckList.Part part, int passcode)
    {
        if(part == DeckList.Part.SIDE)
        {
            return Verdict.OK;
        }
        de.cas_ual_ty.dueldimension.card.properties.Properties card =
            de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)passcode);
        if(card == null)
        {
            return Verdict.OK;
        }
        boolean extraCard = card.getIsInExtraDeck();
        if(extraCard == (part == DeckList.Part.EXTRA))
        {
            return Verdict.OK;
        }
        return Verdict.no(extraCard
            ? "Fusion, Synchro, Xyz and Link monsters go in the Extra Deck"
            : "The Extra Deck only holds Fusion, Synchro, Xyz and Link monsters");
    }

    public static Verdict canAddToDraft(DeckList deck, DeckList.Part part, int passcode,
        Banlist banlist)
    {
        Verdict belongs = belongsIn(part, passcode);
        if(!belongs.allowed())
        {
            return belongs;
        }

        if(deck.partFor(part).size() >= part.capacity())
        {
            return Verdict.no(name(part) + " is full (" + part.capacity() + ")");
        }

        int inDeck = deck.copiesOf(passcode);
        int banlistLimit = banlist == null ? MAX_COPIES : banlist.limitFor(passcode);
        if(banlistLimit <= 0)
        {
            return Verdict.no("Forbidden by "
                + (banlist == null ? Banlist.none() : banlist).displayName());
        }
        int allowed = Math.min(MAX_COPIES, banlistLimit);
        if(inDeck >= allowed)
        {
            return Verdict.no(banlistLimit < MAX_COPIES
                ? "Limited to " + banlistLimit + " by " + banlist.displayName()
                : "Maximum " + MAX_COPIES + " copies per deck");
        }
        return Verdict.OK;
    }

    /**
     * May one more copy of this card go into this part of this deck?
     *
     * @param banlist the list in force, or {@link Banlist#none()} for none
     */
    public static Verdict canAdd(DeckList deck, DeckList.Part part, int passcode,
        Trunk trunk, Banlist banlist)
    {
        return canAddToDraft(deck, part, passcode, banlist);
    }

    /**
     * The most copies of this card this deck could legally hold. The editor
     * shows this beside a trunk entry so a player can see "1 / 3" at a glance.
     */
    public static int maxCopies(int passcode, Trunk trunk, Banlist banlist)
    {
        return maxCopies(passcode, trunk, banlist, false);
    }

    /**
     * As above, but ignoring the collection when free mode is on.
     * <p>
     * The banlist still applies: free mode is about what a player owns, not
     * about what the rules allow, and a list that stopped being enforced
     * because cards were free would make the setting a way to cheat rather
     * than a way to build.
     */
    public static int maxCopies(int passcode, Trunk trunk, Banlist banlist, boolean freeMode)
    {
        int banlistLimit = banlist == null ? MAX_COPIES : banlist.limitFor(passcode);
        int allowed = Math.min(MAX_COPIES, banlistLimit);
        return Math.max(0, freeMode ? allowed : Math.min(allowed, trunk.countOf(passcode)));
    }

    /** Whether a deck is legal to duel with, reusing the banlist's own rules. */
    public static java.util.List<String> validate(DeckList deck, Trunk trunk, Banlist banlist)
    {
        return validate(deck, trunk, banlist, false);
    }

    /**
     * As above. In free mode the ownership half is skipped, which is what makes
     * a deck built with cards nobody owns playable — and what makes it stop
     * being playable the moment the setting goes off, without the deck itself
     * changing at all.
     */
    public static java.util.List<String> validate(DeckList deck, Trunk trunk, Banlist banlist,
        boolean freeMode)
    {
        java.util.List<String> problems = new java.util.ArrayList<>(
            (banlist == null ? Banlist.none() : banlist)
                .validate(deck.main(), deck.extra(), deck.side()));
        // The banlist knows nothing about ownership, so that is checked here.
        if(!freeMode)
        {
            deck.counts().forEach((code, count) ->
            {
                int owned = trunk.countOf(code);
                if(count > owned)
                {
                    problems.add("Deck uses " + count + " of card " + code + " but you own " + owned);
                }
            });
        }
        return problems;
    }

    private static String name(DeckList.Part part)
    {
        return switch(part)
        {
            case MAIN -> "Main Deck";
            case EXTRA -> "Extra Deck";
            case SIDE -> "Side Deck";
        };
    }
}
