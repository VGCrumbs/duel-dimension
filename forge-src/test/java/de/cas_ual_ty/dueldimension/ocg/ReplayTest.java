package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.FuzzDecks;
import de.cas_ual_ty.dueldimension.ocg.bot.RandomBot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReplayTest
{
    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    private static Path cdb()
    {
        return Path.of(System.getProperty("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"));
    }

    @Test
    void encodeDecodeRoundTrips()
    {
        HeadlessDuelRunner.Deck deck = FuzzDecks.vanillaBeatdown();
        Replay original = Replay.of(OcgConstants.DUEL_MODE_MR5, new long[] {1, 2, 3, 4}, deck, deck,
            List.of(new byte[] {7, 0, 0, 0}, new byte[] {2, 0, 0, 0, 1, 0, 0, 0, 3}));

        Replay parsed = Replay.decode(original.encode());

        assertEquals(Replay.VERSION, parsed.version());
        assertEquals(original.flags(), parsed.flags());
        assertArrayEquals(original.seed(), parsed.seed());
        assertEquals(original.mainDeck0(), parsed.mainDeck0());
        assertEquals(original.extraDeck1(), parsed.extraDeck1());
        assertEquals(2, parsed.responses().size());
        assertArrayEquals(original.responses().get(1), parsed.responses().get(1));
    }

    /**
     * The whole point of the format: a recorded duel replays into the same
     * duel. If this breaks, fuzz failures stop being reproducible.
     */
    @Test
    void recordedDuelReplaysIdentically() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        HeadlessDuelRunner.Deck deck = FuzzDecks.ritualSynchro();
        long[] seed = {77, 88, 99, 111};

        HeadlessDuelRunner.DuelTrace original = HeadlessDuelRunner.builder(api)
            .seed(seed)
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, deck)
            .deck(1, deck)
            .responder(0, new RandomBot(4242, cards.all()))
            .responder(1, new RandomBot(2424, cards.all()))
            .build()
            .run(20000);

        assertTrue(original.completed, "seed duel must finish for this test to mean anything");

        Replay replay = Replay.decode(
            Replay.of(OcgConstants.DUEL_MODE_MR5, seed, deck, deck, original.responses).encode());
        ResponseSource playback = replay.asResponseSource();

        HeadlessDuelRunner.DuelTrace replayed = HeadlessDuelRunner.builder(api)
            .seed(seed)
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, deck)
            .deck(1, deck)
            .responder(0, playback) // one recorded stream feeds both seats, in order
            .responder(1, playback)
            .build()
            .run(20000);

        assertFalse(replayed.sawMessage(OcgConstants.MSG_RETRY), "replayed responses were rejected");
        assertTrue(replayed.completed, "replay did not reach the same conclusion");
        assertEquals(original.messages.size(), replayed.messages.size(), "message stream diverged");
        assertEquals(original.result, replayed.result, "different winner on replay");
    }
}
