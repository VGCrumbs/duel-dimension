package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collection remembers rarities, and does so without stranding anyone.
 * <p>
 * These are here because the save format is the one part of this feature that
 * cannot be taken back by deleting code. The rarity itself is a display detail;
 * a collection that loses cards when the code is reverted is somebody's
 * afternoon. Most of what follows is about that, not about rarities.
 */
class TrunkRarityTest
{
    private static final int BLUE_EYES = 89631139;
    private static final int DARK_MAGICIAN = 46986414;

    private static Trunk roundTrip(Trunk trunk)
    {
        JsonElement written = Trunk.CODEC.encodeStart(JsonOps.INSTANCE, trunk)
            .getOrThrow(IllegalStateException::new);
        return Trunk.CODEC.parse(JsonOps.INSTANCE, written)
            .getOrThrow(IllegalStateException::new);
    }

    @Test
    void aPrintingIsRememberedSeparatelyFromTheCard()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1);
        trunk.add(BLUE_EYES, "Common", 2);

        assertEquals(3, trunk.countOf(BLUE_EYES), "three copies in total");
        assertEquals(1, trunk.countOf(BLUE_EYES, "Ultra Rare"));
        assertEquals(2, trunk.countOf(BLUE_EYES, "Common"));
        assertEquals(0, trunk.countOf(BLUE_EYES, "Secret Rare"), "never pulled");
        assertEquals(1, trunk.distinctCards(), "still one card");
        assertEquals(2, trunk.distinctPrintings(), "but two printings");
    }

    @Test
    void printingsSurviveASaveAndLoad()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1);
        trunk.add(DARK_MAGICIAN, "Common", 3);
        trunk.add(DARK_MAGICIAN, 1);   // no rarity recorded

        Trunk loaded = roundTrip(trunk);

        assertEquals(1, loaded.countOf(BLUE_EYES, "Ultra Rare"));
        assertEquals(3, loaded.countOf(DARK_MAGICIAN, "Common"));
        assertEquals(1, loaded.countOf(DARK_MAGICIAN, Trunk.UNKNOWN_RARITY));
        assertEquals(5, loaded.totalCards(), "nothing gained or lost");
    }

    /**
     * The reason the old field is still written.
     * <p>
     * A build without this change reads only {@code Cards}. If that field were
     * dropped, or re-keyed as {@code passcode|rarity}, the old loader's
     * {@code Integer.parseInt} would throw on every entry and quietly discard
     * it — so abandoning this feature would delete the collections of everyone
     * who had played with it.
     */
    @Test
    void anOlderBuildReadingThisSaveStillSeesEveryCard()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1);
        trunk.add(BLUE_EYES, "Common", 2);
        trunk.add(DARK_MAGICIAN, "Secret Rare", 1);

        JsonObject written = Trunk.CODEC.encodeStart(JsonOps.INSTANCE, trunk)
            .getOrThrow(IllegalStateException::new).getAsJsonObject();

        JsonObject cards = written.getAsJsonObject("Cards");
        assertTrue(written.has("Cards"), "the compatibility field must still be written");
        for(String key : cards.keySet())
        {
            assertTrue(key.chars().allMatch(Character::isDigit),
                "an old build parses these as ints; '" + key + "' would be dropped");
        }
        assertEquals(3, cards.get(Integer.toString(BLUE_EYES)).getAsInt(),
            "the total across every rarity, which is all an old build can know");
        assertEquals(1, cards.get(Integer.toString(DARK_MAGICIAN)).getAsInt());
    }

    /** A save from before rarities were kept loads whole, as unknown printings. */
    @Test
    void anOlderSaveLoadsWithoutItsCardsGoingMissing()
    {
        JsonObject old = new JsonObject();
        JsonObject cards = new JsonObject();
        cards.addProperty(Integer.toString(BLUE_EYES), 3);
        cards.addProperty(Integer.toString(DARK_MAGICIAN), 1);
        old.add("Cards", cards);
        // No CardPrintings at all, which is exactly what an older profile has.

        Trunk loaded = Trunk.CODEC.parse(JsonOps.INSTANCE, old)
            .getOrThrow(IllegalStateException::new);

        assertEquals(3, loaded.countOf(BLUE_EYES));
        assertEquals(1, loaded.countOf(DARK_MAGICIAN));
        assertEquals(4, loaded.totalCards());
        assertEquals(3, loaded.countOf(BLUE_EYES, Trunk.UNKNOWN_RARITY),
            "nothing recorded what these were, and the trunk should not invent it");
    }

    /**
     * The two fields describe the same cards, so a naive load would count them
     * twice. This is the test that catches that.
     */
    @Test
    void aSaveFromThisBuildDoesNotLoadItsCardsTwice()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 2);

        Trunk loaded = roundTrip(trunk);

        assertEquals(2, loaded.countOf(BLUE_EYES), "two copies, not four");
        assertEquals(2, loaded.totalCards());
        assertEquals(0, loaded.countOf(BLUE_EYES, Trunk.UNKNOWN_RARITY),
            "the detail accounted for all of them");
    }

    /** Everything that asks "do I own this" keeps working, rarity or not. */
    @Test
    void theOldPasscodeOnlyApiIsUnchanged()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1);
        trunk.add(BLUE_EYES, 1);

        assertTrue(trunk.has(BLUE_EYES));
        assertFalse(trunk.has(DARK_MAGICIAN));
        assertEquals(2, trunk.countOf(BLUE_EYES));
        assertEquals(Map.of(BLUE_EYES, 2), trunk.all(), "flattened for the editor");

        assertEquals(2, trunk.remove(BLUE_EYES, 5), "never below zero");
        assertFalse(trunk.has(BLUE_EYES));
        assertEquals(0, trunk.distinctCards(), "an emptied card leaves no husk");
    }

    /** Removing takes the vaguest record first, keeping the specific ones. */
    @Test
    void removingSpendsUnknownPrintingsBeforeKnownOnes()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1);
        trunk.add(BLUE_EYES, 2);

        assertEquals(2, trunk.remove(BLUE_EYES, 2));

        assertEquals(1, trunk.countOf(BLUE_EYES, "Ultra Rare"), "the known one survived");
        assertEquals(0, trunk.countOf(BLUE_EYES, Trunk.UNKNOWN_RARITY));
    }
}
