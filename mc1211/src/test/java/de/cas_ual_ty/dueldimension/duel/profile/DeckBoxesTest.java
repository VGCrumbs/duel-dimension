package de.cas_ual_ty.dueldimension.duel.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckBoxesTest
{
    private static DuelProfile profileWithoutDeckBoxes()
    {
        JsonObject deck = new JsonObject();
        deck.addProperty("Name", "Burn");
        JsonArray decks = new JsonArray();
        decks.add(deck);
        JsonObject profile = new JsonObject();
        profile.add("Decks", decks);
        return DuelProfile.CODEC.parse(JsonOps.INSTANCE, profile)
            .getOrThrow(error -> new AssertionError(error));
    }

    private static JsonObject write(DuelProfile profile)
    {
        return DuelProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile)
            .getOrThrow(error -> new AssertionError(error)).getAsJsonObject();
    }

    @Test
    void anOldDeckDefaultsToBlueWithoutGrowingItsSave()
    {
        DuelProfile profile = profileWithoutDeckBoxes();
        assertEquals(DeckBoxStyle.BLUE, profile.deckNamed("Burn").deckBox());
        assertFalse(write(profile).getAsJsonArray("Decks").get(0).getAsJsonObject()
            .has("DeckBox"));
    }

    @Test
    void choiceSurvivesPersistenceAndCopies()
    {
        DuelProfile profile = profileWithoutDeckBoxes();
        assertNull(DeckEdits.setDeckBox(profile, "Burn", DeckBoxStyle.PURPLE));

        DuelProfile loaded = DuelProfile.CODEC.parse(JsonOps.INSTANCE, write(profile))
            .getOrThrow(error -> new AssertionError(error));
        assertEquals(DeckBoxStyle.PURPLE, loaded.deckNamed("Burn").deckBox());
        assertEquals(DeckBoxStyle.PURPLE,
            loaded.deckNamed("Burn").copy("Burn copy", DeckList.Origin.SAVED).deckBox());
    }

    @Test
    void invalidTargetsAreRefused()
    {
        DuelProfile profile = profileWithoutDeckBoxes();
        assertNotNull(DeckEdits.setDeckBox(profile, "Missing", DeckBoxStyle.RED));
        assertNotNull(DeckEdits.setDeckBox(profile, "Burn", null));
        assertEquals(DeckBoxStyle.BLUE, profile.deckNamed("Burn").deckBox());
    }

    @Test
    void premiumCasesMustBeBoughtAndSurvivePersistence()
    {
        DuelProfile profile = profileWithoutDeckBoxes();
        assertFalse(profile.ownsDeckBox(DeckBoxStyle.CYBER_KAISER));
        assertNotNull(DeckEdits.setDeckBox(profile, "Burn",
            DeckBoxStyle.CYBER_KAISER));

        assertTrue(profile.grantDeckBox(DeckBoxStyle.CYBER_KAISER));
        assertNull(DeckEdits.setDeckBox(profile, "Burn",
            DeckBoxStyle.CYBER_KAISER));
        DuelProfile loaded = DuelProfile.CODEC.parse(JsonOps.INSTANCE, write(profile))
            .getOrThrow(error -> new AssertionError(error));

        assertTrue(loaded.ownsDeckBox(DeckBoxStyle.CYBER_KAISER));
        assertEquals(DeckBoxStyle.CYBER_KAISER,
            loaded.deckNamed("Burn").deckBox());
    }

    @Test
    void basicCasesAreAlwaysOwnedAndCannotBeGranted()
    {
        DuelProfile profile = profileWithoutDeckBoxes();
        assertTrue(profile.ownsDeckBox(DeckBoxStyle.BLUE));
        assertTrue(profile.ownsDeckBox(DeckBoxStyle.RED));
        assertFalse(profile.grantDeckBox(DeckBoxStyle.PURPLE));
    }

    @Test
    void guessedNamesMigrateToTheAssetsActualNames()
    {
        assertEquals(DeckBoxStyle.CYBER_KAISER,
            DeckBoxStyle.CODEC.parse(JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive("BLUE_EYES_WHITE_DRAGON"))
                .getOrThrow(error -> new AssertionError(error)));
        assertEquals(DeckBoxStyle.THE_MILLENNIUM_PUZZLE,
            DeckBoxStyle.CODEC.parse(JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive("DARK_MAGICIAN"))
                .getOrThrow(error -> new AssertionError(error)));
    }
}
