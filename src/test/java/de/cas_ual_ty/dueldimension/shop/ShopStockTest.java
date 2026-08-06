package de.cas_ual_ty.dueldimension.shop;

import com.google.common.collect.ImmutableList;
import de.cas_ual_ty.dueldimension.set.CardPuller;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shop's derived facts are worked out once.
 * <p>
 * This is a performance contract rather than a behavioural one, which is
 * exactly why it needs a test: nothing about the shop looks different when it
 * regresses. Working out how many cards a pack holds means rolling a real pack,
 * and the catalogue covers every set in the game, so a lost cache turns one
 * right-click into several hundred rolls on the server thread with every other
 * player waiting behind it.
 */
class ShopStockTest
{
    /** Counts how often the set was actually rolled, which is the cost being avoided. */
    private static final class CountingSet extends CardSet
    {
        private final AtomicInteger rolls = new AtomicInteger();

        private CountingSet(String code)
        {
            super("Counting Set", code, "Booster Pack", new Date(0), new CardPuller(null, null)
            {
                @Override
                public List<ItemStack> open(Random random)
                {
                    return ImmutableList.of();
                }

                @Override
                public boolean addInformationInComposition()
                {
                    return false;
                }
            }, ImmutableList.of());
        }

        @Override
        public List<ItemStack> open(Random random)
        {
            rolls.incrementAndGet();
            return super.open(random);
        }
    }

    @BeforeEach
    void forgetEverything()
    {
        // Each test owns its own set code as well, so this is belt and braces:
        // the cache is static and outlives any one test.
        ShopStock.invalidate();
    }

    @Test
    void aPackIsRolledOnceNoMatterHowOftenItIsAsked()
    {
        CountingSet set = new CountingSet("ONCE");

        for(int i = 0; i < 20; i++)
        {
            ShopStock.cardsPerPack(set);
        }

        assertEquals(1, set.rolls.get(), "the second question must be answered from memory");
    }

    @Test
    void pricingAPackDoesNotRollItAgain()
    {
        CountingSet set = new CountingSet("PRICE");

        // priceOf is derived from the pack size, so before the cache existed
        // listing one pack cost two rolls rather than one.
        ShopStock.cardsPerPack(set);
        int price = ShopStock.priceOf(set);

        assertEquals(1, set.rolls.get(), "price must reuse the size already known");
        assertTrue(price > 0);
    }

    @Test
    void forgettingMakesItAskAgain()
    {
        CountingSet set = new CountingSet("FORGET");

        ShopStock.cardsPerPack(set);
        ShopStock.invalidate();
        ShopStock.cardsPerPack(set);

        // A cache that could not be dropped would survive a database reload and
        // quietly describe sets that no longer exist.
        assertEquals(2, set.rolls.get(), "after invalidation the answer is worked out afresh");
    }

    @Test
    void theCardsOfASetAreListedOnce()
    {
        CountingSet set = new CountingSet("IDS");

        // The shop draws a completion figure for the selected pack every frame,
        // so this is asked sixty times a second while the screen is open.
        List<Integer> first = ShopStock.cardIds(set);
        List<Integer> second = ShopStock.cardIds(set);

        assertSame(first, second, "the same list must come back rather than a new one each frame");
    }

    @Test
    void anUnknownSetIsSimplyAbsent()
    {
        // The lookup is a binary search over the keyed set list now; a code
        // that is not there must still answer null rather than throw.
        assertNull(ShopStock.setOf(null));
        assertNull(ShopStock.setOf("NO-SUCH-SET"));
        assertEquals(5, ShopStock.cardsPerPack(null), "a missing set falls back rather than failing");
        assertTrue(ShopStock.cardIds(null).isEmpty());
    }
}
