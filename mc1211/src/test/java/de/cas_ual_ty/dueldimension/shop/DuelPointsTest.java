package de.cas_ual_ty.dueldimension.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The affordability rule, checked directly.
 * <p>
 * {@link DuelPoints#canAfford} is pure precisely so it can be tested without a
 * server: it is the one line standing between a player and a pack they have not
 * paid for, and it is checked on the server for every purchase regardless of
 * what the shop screen believed.
 */
class DuelPointsTest
{
    @Test
    void aBalanceCoversACostOnlyWhenItReallyDoes()
    {
        assertTrue(DuelPoints.canAfford(150, 150), "exactly enough must be enough");
        assertTrue(DuelPoints.canAfford(151, 150));
        assertFalse(DuelPoints.canAfford(149, 150), "one short is short");
        assertFalse(DuelPoints.canAfford(0, 150));
    }

    @Test
    void nothingCostsNothingButNothingIsFree()
    {
        assertTrue(DuelPoints.canAfford(0, 0), "a free pack is affordable at any balance");
        // A negative price would otherwise read as affordable and then ADD
        // points when charged, which is a way to mint currency.
        assertFalse(DuelPoints.canAfford(1000, -50), "a negative price must not be treated as a gift");
    }

    @Test
    void everyPackHasAPriceAPlayerCouldReach()
    {
        // The floor matters: a pack priced at zero would be an infinite source
        // of cards, and the starting balance has to buy at least one pack or
        // the shop is a locked door on day one.
        assertTrue(ShopStock.BASE_PRICE > 0);
        assertTrue(DuelPoints.STARTING_POINTS >= ShopStock.BASE_PRICE,
            "a new player must be able to afford a pack");
    }
}
