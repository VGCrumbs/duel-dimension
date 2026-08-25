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
        assertFalse(loaded.ownsSleeve(CardSleevesType.GOLD),
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
        profile.grantSleeve(CardSleevesType.GOLD);
        assertNull(DeckEdits.setDeckSleeve(profile, "Burn", CardSleevesType.GOLD));

        JsonObject written = write(profile);
        assertEquals("gold",
            written.getAsJsonArray("Decks").get(0).getAsJsonObject().get("Sleeve").getAsString(),
            "an ordinal here would become a different sleeve the day a constant is inserted");
        assertEquals("gold", written.getAsJsonArray("Sleeves").get(0).getAsString());

        DuelProfile back = parse(written);
        assertEquals(CardSleevesType.GOLD, back.deckNamed("Burn").sleeve());
        assertTrue(back.ownsSleeve(CardSleevesType.GOLD));
    }

    /**
     * The snapshot is what the attachment persists, and it copies field by
     * field: anything it forgets saves as empty every time, for everyone.
     */
    @Test
    void aSnapshotCarriesTheSleeveAndTheEntitlement()
    {
        DuelProfile profile = parse(oldProfile());
        profile.grantSleeve(CardSleevesType.RUBY);
        DeckEdits.setDeckSleeve(profile, "Burn", CardSleevesType.RUBY);

        DuelProfile saved = profile.snapshot();
        assertTrue(saved.ownsSleeve(CardSleevesType.RUBY), "the entitlement must survive");
        assertEquals(CardSleevesType.RUBY, saved.deckNamed("Burn").sleeve(),
            "and so must the deck's choice");
    }

    @Test
    void aClientCannotDressADeckInASleeveItDoesNotOwn()
    {
        DuelProfile profile = parse(oldProfile());

        assertNotNull(DeckEdits.setDeckSleeve(profile, "Burn", CardSleevesType.GOLD),
            "asking for an unowned sleeve is refused");
        assertEquals(CardSleevesType.CARD_BACK, profile.deckNamed("Burn").sleeve(),
            "and refused means nothing changed");

        assertTrue(profile.grantSleeve(CardSleevesType.GOLD), "granted for the first time");
        assertFalse(profile.grantSleeve(CardSleevesType.GOLD), "and not a second time");
        assertNull(DeckEdits.setDeckSleeve(profile, "Burn", CardSleevesType.GOLD));
        assertEquals(CardSleevesType.GOLD, profile.deckNamed("Burn").sleeve());
    }

    @Test
    void aFreeSleeveIsOwnedByRuleAndNeverStored()
    {
        DuelProfile profile = parse(oldProfile());

        assertTrue(profile.ownsSleeve(CardSleevesType.RED), "the dye colours cost nothing");
        assertFalse(profile.grantSleeve(CardSleevesType.RED), "so there is nothing to grant");
        assertNull(DeckEdits.setDeckSleeve(profile, "Burn", CardSleevesType.RED));

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
        assertEquals(CardSleevesType.GOLD, Sleeves.byName("gold"));
        assertEquals(CardSleevesType.GOLD, Sleeves.byName("GOLD"),
            "the enum's own constant name is accepted; it cannot mean anything else");
        assertNotNull(DeckEdits.setDeckSleeve(profile, "Nothing", CardSleevesType.RED),
            "and a deck the player does not have is refused too");
    }
}
