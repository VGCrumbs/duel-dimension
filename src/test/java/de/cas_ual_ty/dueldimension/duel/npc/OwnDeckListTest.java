package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The last step before a deck list leaves the server.
 * <p>
 * Everything this feature promises about draw order rests on two properties of
 * {@code shuffledDeckList}: it randomises, and it does so on a copy. Neither is
 * visible by reading the call site — a shuffle in place looks identical there
 * and would quietly turn the cache into a liar for every request after the
 * first — so both are pinned here.
 */
class OwnDeckListTest
{
    /** A card as the deck query produces one: a passcode and an artwork, nothing else. */
    private static CardView card(int code, int art)
    {
        return new CardView(code, 0, 0, 0, -1, -1, -1, -1, -1, -1, false, false, null, 0, 0, art);
    }

    /** Forty distinct cards, so a permutation is easy to recognise. */
    private static List<CardView> deck()
    {
        List<CardView> deck = new ArrayList<>();
        for(int i = 0; i < 40; i++)
        {
            deck.add(card(1000 + i, i % 3));
        }
        return List.copyOf(deck);
    }

    @Test
    void theOrderSentIsRandomisedRatherThanTheOrderHeld()
    {
        List<CardView> held = deck();
        PromptMessages.OwnDeckList sent = DuelistDuels.shuffledDeckList(held);

        int[] asHeld = held.stream().mapToInt(CardView::code).toArray();
        assertNotEquals(java.util.Arrays.toString(asHeld),
            java.util.Arrays.toString(sent.codes()),
            "the deck went out in exactly the order it was held in");
    }

    @Test
    void everyLookIsShuffledFreshlyRatherThanOnce()
    {
        List<CardView> held = deck();
        // Ten looks at a forty-card deck. Two of them landing on the same
        // permutation by chance is not a thing that happens.
        List<String> orders = new ArrayList<>();
        for(int look = 0; look < 10; look++)
        {
            orders.add(java.util.Arrays.toString(
                DuelistDuels.shuffledDeckList(held).codes()));
        }
        assertEquals(orders.size(), new java.util.HashSet<>(orders).size(),
            "two looks produced the same order -- the shuffle is not being re-rolled");
    }

    @Test
    void shufflingDoesNotDisturbTheCachedList()
    {
        List<CardView> held = deck();
        List<Integer> before = held.stream().map(CardView::code).toList();
        for(int look = 0; look < 5; look++)
        {
            DuelistDuels.shuffledDeckList(held);
        }
        assertEquals(before, held.stream().map(CardView::code).toList(),
            "the cached deck was shuffled in place; the next request would read a lie");
    }

    /**
     * The payload is a multiset. Whatever order it goes out in, it must name
     * exactly the cards in the deck — no card withheld, none invented, and the
     * artwork still attached to the copy that wears it.
     */
    @Test
    void thePayloadIsTheSameCardsWithTheirOwnArtwork()
    {
        List<CardView> held = deck();
        PromptMessages.OwnDeckList sent = DuelistDuels.shuffledDeckList(held);

        assertEquals(held.size(), sent.codes().length, "the count changed");
        assertEquals(held.size(), sent.arts().length, "codes and artworks disagree in length");

        List<String> expected = held.stream()
            .map(view -> view.code() + "/" + view.art()).sorted().toList();
        List<String> actual = new ArrayList<>();
        for(int i = 0; i < sent.codes().length; i++)
        {
            actual.add(sent.codes()[i] + "/" + sent.arts()[i]);
        }
        assertEquals(expected, actual.stream().sorted().toList(),
            "the multiset of (passcode, artwork) pairs is not the deck's");
    }

    /** An empty cache -- asked before the first checkpoint -- must not explode. */
    @Test
    void anEmptyDeckIsAnEmptyAnswer()
    {
        PromptMessages.OwnDeckList sent = DuelistDuels.shuffledDeckList(List.of());
        assertEquals(0, sent.codes().length);
        assertEquals(0, sent.arts().length);
    }
}
