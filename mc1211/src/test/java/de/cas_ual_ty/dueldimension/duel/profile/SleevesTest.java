package de.cas_ual_ty.dueldimension.duel.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deck remembers its sleeve, a profile remembers which sleeves it owns, and
 * neither claim costs an existing save anything.
 * <p>
 * The two that matter are the ones a compiler cannot check: what happens to a
 * profile written before any of this existed, and whether a sleeve survives the
 * two places a deck is rebuilt from parts — the save payload and the attachment
 * snapshot. Both have silently eaten a field in this codebase before.
 */
class SleevesTest
{
    private static DuelProfile parse(JsonElement json)
    {
        return DuelProfile.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(error -> new AssertionError(error));
    }

    private static JsonObject write(DuelProfile profile)
    {
        return DuelProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile)
            .getOrThrow(error -> new AssertionError(error)).getAsJsonObject();
    }

    /** A profile exactly as a build without sleeves wrote it: no such fields. */
    private static JsonObject oldProfile()
    {
        JsonObject deck = new JsonObject();
        deck.addProperty("Name", "Burn");
        JsonArray main = new JsonArray();
        main.add(46986414);
        deck.add("Main", main);

        JsonArray decks = new JsonArray();
        decks.add(deck);

        JsonObject profile = new JsonObject();
        profile.add("Decks", decks);
        profile.addProperty("Active", "Burn");
        return profile;
    }

    @Test
    void aProfileWrittenBeforeSleevesExistedLoadsUnchanged()
    {
        DuelProfile loaded = parse(oldProfile());

        DeckList burn = loaded.deckNamed("Burn");
        assertNotNull(burn, "the deck itself must still load");
        assertEquals(List.of(46986414), burn.main(), "and its cards must be untouched");
        assertEquals(CardSleevesType.CARD_BACK, burn.sleeve(),
            "no Sleeve field means the plain back -- which is what it was drawn in anyway");
        assertEquals("Burn", loaded.activeDeck());

        assertTrue(loaded.ownedSleeves().contains(CardSleevesType.CARD_BACK),
            "everybody owns the plain back from the start");
        assertFalse(loaded.ownsSleeve(paid(0)),
            "and nobody owns a bought sleeve merely by having a profile");
    }

    /**
     * The other direction, which is the one that makes this safe to abandon: a
     * profile written by THIS build, with nothing customised, is byte for byte
     * what the previous build wrote, because a Codec omits a field equal to its
     * default.
     */
    @Test
    void anUncustomisedProfileWritesNoSleeveFieldsAtAll()
    {
        DuelProfile profile = parse(oldProfile());
        JsonObject written = write(profile);

        assertFalse(written.has("Sleeves"), "no sleeve was ever granted");
        assertFalse(written.getAsJsonArray("Decks").get(0).getAsJsonObject().has("Sleeve"),
            "and no deck was ever dressed");
    }

    @Test
    void aSleeveIsWrittenByNameSoTheEnumMayGrow()
    {
        DuelProfile profile = parse(oldProfile());
        profile.grantSleeve(paid(0));
        assertNull(DeckEdits.setDeckSleeve(profile, "Burn", paid(0)));

        JsonObject written = write(profile);
        assertEquals(paid(0).name,
            written.getAsJsonArray("Decks").get(0).getAsJsonObject().get("Sleeve").getAsString(),
            "an ordinal here would become a different sleeve the day a constant is inserted");
        assertEquals(paid(0).name, written.getAsJsonArray("Sleeves").get(0).getAsString());

        DuelProfile back = parse(written);
        assertEquals(paid(0), back.deckNamed("Burn").sleeve());
        assertTrue(back.ownsSleeve(paid(0)));
    }

    /**
     * The snapshot is what the attachment persists, and it copies field by
     * field: anything it forgets saves as empty every time, for everyone.
     */
    @Test
    void aSnapshotCarriesTheSleeveAndTheEntitlement()
    {
        DuelProfile profile = parse(oldProfile());
        profile.grantSleeve(paid(1));
        DeckEdits.setDeckSleeve(profile, "Burn", paid(1));

        DuelProfile saved = profile.snapshot();
        assertTrue(saved.ownsSleeve(paid(1)), "the entitlement must survive");
        assertEquals(paid(1), saved.deckNamed("Burn").sleeve(),
            "and so must the deck's choice");
    }

    @Test
    void aClientCannotDressADeckInASleeveItDoesNotOwn()
    {
        DuelProfile profile = parse(oldProfile());

        assertNotNull(DeckEdits.setDeckSleeve(profile, "Burn", paid(0)),
            "asking for an unowned sleeve is refused");
        assertEquals(CardSleevesType.CARD_BACK, profile.deckNamed("Burn").sleeve(),
            "and refused means nothing changed");

        assertTrue(profile.grantSleeve(paid(0)), "granted for the first time");
        assertFalse(profile.grantSleeve(paid(0)), "and not a second time");
        assertNull(DeckEdits.setDeckSleeve(profile, "Burn", paid(0)));
        assertEquals(paid(0), profile.deckNamed("Burn").sleeve());
    }

    @Test
    void aFreeSleeveIsOwnedByRuleAndNeverStored()
    {
        DuelProfile profile = parse(oldProfile());

        // The plain back is the only free sleeve now. It used to be the back
        // plus the sixteen dye colours; those went with the old catalogue, and
        // the rule they were an example of did not.
        assertTrue(profile.ownsSleeve(CardSleevesType.CARD_BACK), "the plain back costs nothing");
        assertFalse(profile.grantSleeve(CardSleevesType.CARD_BACK), "so there is nothing to grant");
        assertNull(DeckEdits.setDeckSleeve(profile, "Burn", CardSleevesType.CARD_BACK));

        assertFalse(write(profile).has("Sleeves"),
            "a rule is not a grant, so it takes no room on disk and cannot be lost");
    }

    @Test
    void anIdThisBuildDoesNotKnowReadsAsThePlainBackRatherThanFailingTheProfile()
    {
        JsonObject old = oldProfile();
        old.getAsJsonArray("Decks").get(0).getAsJsonObject()
            .addProperty("Sleeve", "sleeves_from_a_mod_that_left");
        JsonArray owned = new JsonArray();
        owned.add("sleeves_from_a_mod_that_left");
        old.add("Sleeves", owned);

        DuelProfile loaded = parse(old);
        assertEquals(CardSleevesType.CARD_BACK, loaded.deckNamed("Burn").sleeve(),
            "one missing cosmetic is not worth refusing a player their collection");
        assertFalse(write(loaded).has("Sleeves"),
            "and the unknown grant is dropped rather than kept as a stale entry");
    }

    @Test
    void aNameOffTheWireIsCheckedRatherThanForgiven()
    {
        DuelProfile profile = parse(oldProfile());

        assertNull(Sleeves.byName("sleeves_from_a_mod_that_left"),
            "byName is strict where the Codec is lenient");
        assertEquals(paid(0), Sleeves.byName(paid(0).name));
        assertEquals(paid(0), Sleeves.byName(paid(0).name.toUpperCase(java.util.Locale.ROOT)),
            "the enum's own constant name is accepted; it cannot mean anything else");
        assertNotNull(DeckEdits.setDeckSleeve(profile, "Nothing", paid(0)),
            "and a deck the player does not have is refused too");
    }
    /**
     * A sleeve that has to be bought, taken from the catalogue by position.
     * <p>
     * Every test here is about a RULE -- written by name, granted before worn,
     * free by rule, checked on the way in -- and not one of them is about a
     * particular sleeve. Naming GOLD and RUBY meant the whole file stopped
     * compiling the day the catalogue was replaced with Master Duel's: a suite
     * failing for a reason it was not testing. Asking the catalogue instead
     * means the next replacement costs nothing.
     */
    private static CardSleevesType paid(int nth)
    {
        int seen = 0;
        for(CardSleevesType sleeve : CardSleevesType.VALUES)
        {
            if(Sleeves.isPurchasable(sleeve) && seen++ == nth)
            {
                return sleeve;
            }
        }
        throw new IllegalStateException("no purchasable sleeve at " + nth);
    }
}
