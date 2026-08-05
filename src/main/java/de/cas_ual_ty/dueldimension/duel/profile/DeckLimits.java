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
     * May one more copy of this card go into this part of this deck?
     *
     * @param banlist the list in force, or {@link Banlist#none()} for none
     */
    public static Verdict canAdd(DeckList deck, DeckList.Part part, int passcode,
        Trunk trunk, Banlist banlist)
    {
        if(deck.partFor(part).size() >= part.capacity())
        {
            return Verdict.no(name(part) + " is full (" + part.capacity() + ")");
        }

        int inDeck = deck.copiesOf(passcode);
        int owned = trunk.countOf(passcode);
        if(owned <= 0)
        {
            return Verdict.no("You do not own this card");
        }

        int banlistLimit = banlist == null ? MAX_COPIES : banlist.limitFor(passcode);
        if(banlistLimit <= 0)
        {
            return Verdict.no("Forbidden by " + banlist.displayName());
        }

        // The tightest of the three ceilings decides, and the message names
        // whichever one actually bit.
        int allowed = Math.min(Math.min(MAX_COPIES, banlistLimit), owned);
        if(inDeck >= allowed)
        {
            if(allowed == owned && owned < Math.min(MAX_COPIES, banlistLimit))
            {
                return Verdict.no("You only own " + owned + " cop" + (owned == 1 ? "y" : "ies"));
            }
            if(banlistLimit < MAX_COPIES)
            {
                return Verdict.no("Limited to " + banlistLimit + " by " + banlist.displayName());
            }
            return Verdict.no("Maximum " + MAX_COPIES + " copies per deck");
        }
        return Verdict.OK;
    }

    /**
     * The most copies of this card this deck could legally hold. The editor
     * shows this beside a trunk entry so a player can see "1 / 3" at a glance.
     */
    public static int maxCopies(int passcode, Trunk trunk, Banlist banlist)
    {
        int banlistLimit = banlist == null ? MAX_COPIES : banlist.limitFor(passcode);
        return Math.max(0, Math.min(Math.min(MAX_COPIES, banlistLimit), trunk.countOf(passcode)));
    }

    /** Whether a deck is legal to duel with, reusing the banlist's own rules. */
    public static java.util.List<String> validate(DeckList deck, Trunk trunk, Banlist banlist)
    {
        java.util.List<String> problems = new java.util.ArrayList<>(
            (banlist == null ? Banlist.none() : banlist)
                .validate(deck.main(), deck.extra(), deck.side()));
        // The banlist knows nothing about ownership, so that is checked here.
        deck.counts().forEach((code, count) ->
        {
            int owned = trunk.countOf(code);
            if(count > owned)
            {
                problems.add("Deck uses " + count + " of card " + code + " but you own " + owned);
            }
        });
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
