package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckOrderingTest
{
    @Test
    void savedDecksMoveAndPersistInTheirManualOrder()
    {
        DuelProfile profile = new DuelProfile();
        profile.addDeck(new DeckList("Alpha", DeckList.Origin.SAVED));
        profile.addDeck(new DeckList("Granted", DeckList.Origin.STRUCTURE));
        profile.addDeck(new DeckList("Beta", DeckList.Origin.SAVED));
        profile.addDeck(new DeckList("Gamma", DeckList.Origin.SAVED));

        assertTrue(profile.moveSavedDeck("Gamma", "Alpha"));
        assertEquals(List.of("Gamma", "Alpha", "Beta"),
            profile.savedDecks().stream().map(DeckList::name).toList());
        assertEquals("Granted", profile.decks().get(1).name());

        DuelProfile loaded = DuelProfile.CODEC.parse(JsonOps.INSTANCE,
                DuelProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile)
                    .getOrThrow(error -> new AssertionError(error)))
            .getOrThrow(error -> new AssertionError(error));
        assertEquals(List.of("Gamma", "Alpha", "Beta"),
            loaded.savedDecks().stream().map(DeckList::name).toList());
        assertEquals("Granted", loaded.decks().get(1).name());
    }

    @Test
    void missingOrSelfTargetsDoNotChangeOrder()
    {
        DuelProfile profile = new DuelProfile();
        profile.addDeck(new DeckList("Alpha", DeckList.Origin.SAVED));
        profile.addDeck(new DeckList("Beta", DeckList.Origin.SAVED));

        assertFalse(profile.moveSavedDeck("Alpha", "Alpha"));
        assertFalse(profile.moveSavedDeck("Missing", "Beta"));
        assertEquals(List.of("Alpha", "Beta"),
            profile.savedDecks().stream().map(DeckList::name).toList());
    }
}
