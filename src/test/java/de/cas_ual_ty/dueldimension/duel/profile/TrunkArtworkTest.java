package de.cas_ual_ty.dueldimension.duel.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collection remembers which artwork each copy was printed in.
 * <p>
 * The set files have always said so — Obelisk the Tormentor is
 * {@code image_index 1} in BP01 and GLD4, and {@code image_index 2} in MVP1 —
 * and the pull has always carried it as far as the item. It stopped at the
 * trunk, so every copy a player owned was recorded as the printed art and the
 * deck editor had nothing to default from.
 * <p>
 * As with the rarities, most of what follows is about the save format rather
 * than about artwork: the third field has to be as safe to abandon as the
 * second one was.
 */
class TrunkArtworkTest
{
    private static final int OBELISK = 10000000;
    private static final int BLUE_EYES = 89631139;
    private static final int DARK_MAGICIAN = 46986414;

    private static Trunk roundTrip(Trunk trunk)
    {
        JsonElement written = Trunk.CODEC.encodeStart(JsonOps.INSTANCE, trunk)
            .getOrThrow(IllegalStateException::new);
        return Trunk.CODEC.parse(JsonOps.INSTANCE, written)
            .getOrThrow(IllegalStateException::new);
    }

    private static JsonObject write(Trunk trunk)
    {
        return Trunk.CODEC.encodeStart(JsonOps.INSTANCE, trunk)
            .getOrThrow(IllegalStateException::new).getAsJsonObject();
    }

    /** The worked example: three printings of Obelisk, two of them alternates. */
    @Test
    void aPrintingIsRememberedWithItsArtwork()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Rare", 1, 1);              // BP01-021
        trunk.add(OBELISK, "Ultra Rare", 2, 1);        // MVP1-SV5

        assertEquals(2, trunk.countOf(OBELISK), "two copies in total");
        assertEquals(1, trunk.countOf(OBELISK, "Rare"));
        assertEquals(1, trunk.countOf(OBELISK, "Rare", 1));
        assertEquals(0, trunk.countOf(OBELISK, "Rare", 2), "that one is the Ultra Rare");
        assertEquals(1, trunk.countOf(OBELISK, "Ultra Rare", 2));
    }

    /**
     * Why the artwork is part of the key rather than a fact about the rarity.
     * <p>
     * Measured over the shipped set files, 94 (passcode, rarity) pairs disagree
     * about the artwork, and Blue-Eyes at Ultra Rare spans six. A side map from
     * rarity to artwork could hold one of these two copies, not both.
     */
    @Test
    void twoUltraRaresInDifferentArtworksAreTwoDifferentThings()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1, 1);
        trunk.add(BLUE_EYES, "Ultra Rare", 4, 1);

        assertEquals(2, trunk.countOf(BLUE_EYES, "Ultra Rare"), "both are Ultra Rares");
        assertEquals(1, trunk.countOf(BLUE_EYES, "Ultra Rare", 1));
        assertEquals(1, trunk.countOf(BLUE_EYES, "Ultra Rare", 4));
        assertEquals(List.of(4, 1), trunk.artsOwned(BLUE_EYES));
        assertEquals(1, trunk.distinctPrintings(),
            "set completion still counts rarities, and this is one of them");
    }

    /** Fanciest first, one entry per copy, which is what a deck deals from. */
    @Test
    void theCopiesAreDealtFanciestFirst()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Common", 3);               // printed art, three of them
        trunk.add(OBELISK, "Rare", 1, 1);
        trunk.add(OBELISK, "Ultra Rare", 2, 1);

        assertEquals(List.of(2, 1, 0, 0, 0), trunk.artsOwned(OBELISK));
        assertEquals(2, trunk.artForCopy(OBELISK, 0), "the MVP1 copy");
        assertEquals(1, trunk.artForCopy(OBELISK, 1), "then the BP01 one");
        assertEquals(0, trunk.artForCopy(OBELISK, 2), "then the ordinary ones");
        assertEquals(0, trunk.artForCopy(OBELISK, 99), "and nothing beyond them");
        assertEquals(0, trunk.artForCopy(OBELISK, -1), "nor before them");
    }

    /** A card nobody owns, and a card owned only on its printed art. */
    @Test
    void aCardWithNothingSpecialAboutItCostsNothing()
    {
        Trunk trunk = new Trunk();
        trunk.add(DARK_MAGICIAN, "Common", 3);

        assertEquals(0, trunk.artForCopy(DARK_MAGICIAN, 0));
        assertEquals(0, trunk.artForCopy(BLUE_EYES, 0), "not owned at all");
        assertTrue(trunk.artsOwned(BLUE_EYES).isEmpty());
    }

    @Test
    void artworkSurvivesASaveAndLoad()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Rare", 1, 1);
        trunk.add(OBELISK, "Ultra Rare", 2, 1);
        trunk.add(OBELISK, "Common", 2);
        trunk.add(DARK_MAGICIAN, 1);

        Trunk loaded = roundTrip(trunk);

        assertEquals(1, loaded.countOf(OBELISK, "Rare", 1));
        assertEquals(1, loaded.countOf(OBELISK, "Ultra Rare", 2));
        assertEquals(2, loaded.countOf(OBELISK, "Common", 0));
        assertEquals(1, loaded.countOf(DARK_MAGICIAN, Trunk.UNKNOWN_RARITY, 0));
        assertEquals(5, loaded.totalCards(), "nothing gained or lost");
        assertEquals(List.of(2, 1, 0, 0), loaded.artsOwned(OBELISK));
    }

    /**
     * The reason the artwork went into a THIRD field.
     * <p>
     * A build that knows about rarities and not about artwork reads
     * {@code CardPrintings}, and it must find exactly what it would have written
     * itself: one row per (passcode, rarity), counting every artwork of it. A
     * key re-cut as {@code passcode|rarity|art} would have been read there as a
     * rarity literally named "Ultra Rare|2" — the card is not lost, but set
     * completion silently stops matching and the invented rarity is written
     * back to disk on the next save.
     */
    @Test
    void aBuildThatKnowsRaritiesButNotArtworkReadsThisSaveUnchanged()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Ultra Rare", 2, 1);
        trunk.add(OBELISK, "Ultra Rare", 0, 1);
        trunk.add(OBELISK, "Rare", 1, 1);

        JsonObject written = write(trunk);
        JsonObject printings = written.getAsJsonObject("CardPrintings");

        assertEquals(2, printings.get(OBELISK + "|Ultra Rare").getAsInt(),
            "both Ultra Rares, whatever they are wearing");
        assertEquals(1, printings.get(OBELISK + "|Rare").getAsInt());
        for(String key : printings.keySet())
        {
            assertEquals(key.indexOf('|'), key.lastIndexOf('|'),
                "an older loader cuts at the first separator and takes the rest as"
                    + " the rarity; '" + key + "' would invent one");
        }

        JsonObject cards = written.getAsJsonObject("Cards");
        assertEquals(3, cards.get(Integer.toString(OBELISK)).getAsInt(),
            "and the oldest field is still the plain total");
        for(String key : cards.keySet())
        {
            assertTrue(key.chars().allMatch(Character::isDigit),
                "an old build parses these as ints; '" + key + "' would be dropped");
        }
    }

    /** ~122 cards of 13,862 have a second artwork, so nearly no save pays for this. */
    @Test
    void aCollectionWithNoAlternateArtworkWritesNothingExtra()
    {
        Trunk trunk = new Trunk();
        trunk.add(BLUE_EYES, "Ultra Rare", 1);
        trunk.add(DARK_MAGICIAN, "Common", 3);
        trunk.add(DARK_MAGICIAN, 1);

        JsonObject written = write(trunk);

        assertTrue(!written.has("CardArtPrintings")
            || written.getAsJsonObject("CardArtPrintings").size() == 0,
            "an empty optional field must not be a cost every collection pays");
    }

    /** A save from before artwork was kept loads whole, every copy on printed art. */
    @Test
    void anOlderSaveLoadsAsAllPrintedArt()
    {
        JsonObject old = new JsonObject();
        JsonObject cards = new JsonObject();
        cards.addProperty(Integer.toString(OBELISK), 3);
        old.add("Cards", cards);
        JsonObject printings = new JsonObject();
        printings.addProperty(OBELISK + "|Ultra Rare", 1);
        printings.addProperty(OBELISK + "|Rare", 2);
        old.add("CardPrintings", printings);
        // No CardArtPrintings at all, which is exactly what an older profile has.

        Trunk loaded = Trunk.CODEC.parse(JsonOps.INSTANCE, old)
            .getOrThrow(IllegalStateException::new);

        assertEquals(3, loaded.totalCards());
        assertEquals(1, loaded.countOf(OBELISK, "Ultra Rare", 0));
        assertEquals(2, loaded.countOf(OBELISK, "Rare", 0));
        assertEquals(0, loaded.artForCopy(OBELISK, 0),
            "nothing recorded what these wore, and the trunk should not invent it");
    }

    /**
     * Three fields describe the same cards, so a naive load would count them
     * three times over. This is the test that catches that.
     */
    @Test
    void aSaveFromThisBuildDoesNotLoadItsCardsTwice()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Ultra Rare", 2, 2);
        trunk.add(OBELISK, "Ultra Rare", 0, 1);

        Trunk loaded = roundTrip(trunk);

        assertEquals(3, loaded.countOf(OBELISK), "three copies, not six or nine");
        assertEquals(2, loaded.countOf(OBELISK, "Ultra Rare", 2));
        assertEquals(1, loaded.countOf(OBELISK, "Ultra Rare", 0));
        assertEquals(0, loaded.countOf(OBELISK, Trunk.UNKNOWN_RARITY),
            "the detail accounted for all of them");
    }

    /** Everything that asks "do I own this" keeps working, artwork or not. */
    @Test
    void theOlderApiIsUnchanged()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Ultra Rare", 2, 1);
        trunk.add(OBELISK, "Ultra Rare", 1);

        assertTrue(trunk.has(OBELISK));
        assertTrue(trunk.has(OBELISK, "Ultra Rare"));
        assertFalse(trunk.has(OBELISK, "Rare"));
        assertEquals(2, trunk.countOf(OBELISK));
        assertEquals(java.util.Map.of(OBELISK, 2), trunk.all(), "flattened for the editor");
        assertEquals(java.util.Map.of("Ultra Rare", 2), trunk.printingsOf(OBELISK),
            "collapsed over the artwork, which is what set completion asks");
        assertEquals(1, trunk.distinctCards());
    }

    /**
     * Losing a card takes the copy that says least about itself.
     * <p>
     * The unknown-rarity pile still goes first, as it always did. What is new is
     * the tie-break underneath it: within one rarity the printed art goes before
     * an alternate, so a player who loses a card does not lose the MVP1 Obelisk
     * while an ordinary one sits beside it.
     */
    @Test
    void removingSpendsThePlainCopiesFirst()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Ultra Rare", 2, 1);
        trunk.add(OBELISK, "Ultra Rare", 0, 1);
        trunk.add(OBELISK, 1);

        assertEquals(2, trunk.remove(OBELISK, 2));

        assertEquals(0, trunk.countOf(OBELISK, Trunk.UNKNOWN_RARITY), "the vaguest went first");
        assertEquals(0, trunk.countOf(OBELISK, "Ultra Rare", 0), "then the plain Ultra Rare");
        assertEquals(1, trunk.countOf(OBELISK, "Ultra Rare", 2), "the MVP1 copy survived");
    }

    /** A full-fidelity copy, which is what the profile now takes on every save. */
    @Test
    void copyIntoKeepsWhatAllWouldHaveThrownAway()
    {
        Trunk trunk = new Trunk();
        trunk.add(OBELISK, "Ultra Rare", 2, 1);
        trunk.add(OBELISK, "Rare", 1, 1);
        trunk.add(DARK_MAGICIAN, "Common", 3);

        Trunk copy = new Trunk();
        trunk.copyInto(copy);

        assertEquals(1, copy.countOf(OBELISK, "Ultra Rare", 2));
        assertEquals(1, copy.countOf(OBELISK, "Rare", 1));
        assertEquals(3, copy.countOf(DARK_MAGICIAN, "Common", 0));
        assertEquals(5, copy.totalCards());

        // Detached: the copy is a new collection, not a view of this one.
        trunk.add(OBELISK, "Secret Rare", 1);
        assertEquals(2, copy.countOf(OBELISK));
    }
}
