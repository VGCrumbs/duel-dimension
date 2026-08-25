package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.duel.profile.Sleeves;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void theFreeColoursAndThePatronSleevesAreNotForSale()
    {
        List<String> ids = ShopStock.sleeves().stream()
            .map(ShopStock.SleeveOffer::sleeve).toList();
        // Free by rule, so there is nothing to sell.
        assertFalse(ids.contains(Sleeves.nameOf(CardSleevesType.CARD_BACK)));
        assertFalse(ids.contains(Sleeves.nameOf(CardSleevesType.RED)));
        // A thank-you, not a product.
        assertFalse(ids.contains(Sleeves.nameOf(CardSleevesType.P_1)));
        // Drawn art, so it is bought -- including the five appended last, which
        // no file in the shop mentions by name.
        assertTrue(ids.contains(Sleeves.nameOf(CardSleevesType.GOLD)));
        assertTrue(ids.contains(Sleeves.nameOf(CardSleevesType.MILLENIUM_VOID)));
        assertTrue(ids.contains(Sleeves.nameOf(CardSleevesType.MILLENIUM_WHITE)));
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
        assertEquals(0, ShopStock.priceOfSleeve(CardSleevesType.RED));
        assertEquals(0, ShopStock.priceOfSleeve(CardSleevesType.P_2));
        assertEquals(500, ShopStock.priceOfSleeve(CardSleevesType.MILLENIUM_RED));
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
