package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.duel.profile.Sleeves;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the sleeve shop puts in its window, and what it charges for it.
 * <p>
 * The interesting property is not that the list has a length — it is that
 * nothing in this feature enumerates sleeves by hand. The stock is derived from
 * the enum through {@link Sleeves#isPurchasable}, so a constant appended to
 * {@link CardSleevesType} appears for sale without any file being edited, and a
 * free or patron sleeve cannot appear however it is appended.
 */
class SleeveStockTest
{
    @Test
    void everyPurchasableSleeveIsStockAndNothingElseIs()
    {
        List<ShopStock.SleeveOffer> offers = ShopStock.sleeves();
        for(ShopStock.SleeveOffer offer : offers)
        {
            CardSleevesType sleeve = Sleeves.byName(offer.sleeve());
            assertTrue(Sleeves.isPurchasable(sleeve),
                offer.sleeve() + " is on sale but is not purchasable");
        }
        long expected = java.util.Arrays.stream(CardSleevesType.VALUES)
            .filter(Sleeves::isPurchasable).count();
        assertEquals(expected, offers.size());
    }

    /**
     * The stock IS {@link Sleeves#isPurchasable}, over the whole catalogue.
     * <p>
     * Written against the rule rather than against named sleeves. It used to
     * name RED for the free case and P_1 for the patron one, and both vanished
     * with the old catalogue -- so the test that guarded the rule stopped
     * compiling at the moment the rule most needed checking.
     * <p>
     * The patron half is vacuous today: nothing in Master Duel's catalogue is a
     * thank-you, so {@link CardSleevesType#isPatreonReward} has no instances.
     * It is asserted anyway, because it starts working the day one is added
     * back and costs nothing until then.
     */
    @Test
    void theStockIsExactlyWhatIsPurchasable()
    {
        List<String> ids = ShopStock.sleeves().stream()
            .map(ShopStock.SleeveOffer::sleeve).toList();

        int sellable = 0;
        for(CardSleevesType sleeve : CardSleevesType.VALUES)
        {
            String id = Sleeves.nameOf(sleeve);
            if(Sleeves.isPurchasable(sleeve))
            {
                sellable++;
                assertTrue(ids.contains(id), id + " is purchasable but is not stocked");
            }
            else
            {
                assertFalse(ids.contains(id), id + " is stocked but is not purchasable");
            }
            if(Sleeves.isFree(sleeve) || sleeve.isPatreonReward)
            {
                assertFalse(ids.contains(id),
                    id + " is free or a thank-you, so it is not a product");
            }
        }
        assertEquals(sellable, ids.size(), "and nothing is stocked twice");
        assertTrue(sellable > 0, "the shop must have something to sell");
        // Free by rule, so there is nothing to sell.
        assertFalse(ids.contains(Sleeves.nameOf(CardSleevesType.CARD_BACK)));
    }

    @Test
    void everySleeveCostsTheSameFlatFiveHundred()
    {
        for(ShopStock.SleeveOffer offer : ShopStock.sleeves())
        {
            assertEquals(500, offer.price(), offer.sleeve() + " is not the flat sleeve price");
            assertEquals(ShopStock.SLEEVE_PRICE, offer.price());
        }
    }

    /**
     * The one that would have gone unnoticed: a sleeve price derived from
     * {@link ShopStock#BASE_PRICE} would move whenever the game's packs changed
     * size, because that constant is scaled per card by {@link ShopStock#priceOf}.
     */
    @Test
    void theSleevePriceIsItsOwnNumberAndNotThePackBasePrice()
    {
        assertNotEquals(ShopStock.BASE_PRICE, ShopStock.SLEEVE_PRICE);
    }

    @Test
    void aSleeveThatIsNotStockIsWorthNothingRatherThanTheFlatPrice()
    {
        // What the server charges for something it will not sell. The purchase
        // path refuses these before reaching the price at all; this states that
        // even if it did not, there is no money in it.
        assertEquals(0, ShopStock.priceOfSleeve(CardSleevesType.CARD_BACK));
        assertEquals(0, ShopStock.priceOfSleeve(null), "and nothing at all is worth nothing");
        for(CardSleevesType sleeve : CardSleevesType.VALUES)
        {
            assertEquals(Sleeves.isPurchasable(sleeve) ? 500 : 0,
                ShopStock.priceOfSleeve(sleeve), Sleeves.nameOf(sleeve));
        }
    }

    /** Derived once and kept, the same contract the pack catalogue has. */
    @Test
    void theCatalogueIsBuiltOnceAndHandedOutReadOnly()
    {
        assertSame(ShopStock.sleeves(), ShopStock.sleeves());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> ShopStock.sleeves().add(new ShopStock.SleeveOffer("free_money", 0)));
    }
}
