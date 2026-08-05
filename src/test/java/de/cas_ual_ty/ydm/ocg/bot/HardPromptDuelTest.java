package de.cas_ual_ty.ydm.ocg.bot;

import de.cas_ual_ty.ydm.ocg.CdbCardProvider;
import de.cas_ual_ty.ydm.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.ydm.ocg.OcgApi;
import de.cas_ual_ty.ydm.ocg.OcgConstants;
import de.cas_ual_ty.ydm.ocg.RawMessage;
import de.cas_ual_ty.ydm.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A deck that forces the prompts a vanilla beatdown deck never produces —
 * above all MSG_SELECT_SUM, the subset-sum selection behind Ritual tributes
 * and Synchro materials. A bot that passes the vanilla test but not this one
 * cannot play half the game.
 */
class HardPromptDuelTest
{
    // Ritual package: tribute monsters totalling exactly Level 8.
    private static final int BLACK_LUSTER_RITUAL = 55761792;
    private static final int BLACK_LUSTER_SOLDIER = 5405694;
    // Synchro package: Junk Synchron (Tuner, Lv3) + Lv2 non-tuners -> Junk Warrior (Lv5).
    private static final int JUNK_SYNCHRON = 63977008;
    private static final int DOPPELWARRIOR = 53855409;
    private static final int SONIC_CHICK = 36472900;
    private static final int JUNK_WARRIOR = 60800381;
    // Vanilla filler, also ritual tribute fodder: Lv3 and Lv4 give the sum
    // solver several ways to reach 8.
    private static final int GIANT_SOLDIER_OF_STONE = 13039848; // Lv3
    private static final int MYSTICAL_ELF = 15025844; // Lv4

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

    private static List<Integer> ritualSynchroDeck()
    {
        List<Integer> deck = new ArrayList<>(40);
        add(deck, BLACK_LUSTER_RITUAL, 3);
        add(deck, BLACK_LUSTER_SOLDIER, 3);
        add(deck, JUNK_SYNCHRON, 3);
        add(deck, DOPPELWARRIOR, 3);
        add(deck, SONIC_CHICK, 3);
        add(deck, GIANT_SOLDIER_OF_STONE, 13);
        add(deck, MYSTICAL_ELF, 12);
        return deck;
    }

    private static void add(List<Integer> deck, int code, int copies)
    {
        for(int i = 0; i < copies; i++)
        {
            deck.add(code);
        }
    }

    @Test
    void ritualAndSynchroPromptsAreAnsweredLegally() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        OcgApi api = OcgApi.load(lib());
        HeadlessDuelRunner.Deck deck = new HeadlessDuelRunner.Deck(ritualSynchroDeck(), List.of(
            JUNK_WARRIOR, JUNK_WARRIOR, JUNK_WARRIOR));

        Set<String> promptsSeen = new LinkedHashSet<>();
        int completed = 0;
        boolean sawSum = false;

        for(int seed = 1; seed <= 12; seed++)
        {
            HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed, seed * 7L + 1, seed * 13L + 2, seed * 29L + 3})
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
                .deck(0, deck)
                .deck(1, deck)
                .responder(0, new RandomBot(seed * 1000L, cards.all()))
                .responder(1, new RandomBot(seed * 2000L, cards.all()))
                .build()
                .run(20000);

            assertFalse(trace.sawMessage(OcgConstants.MSG_RETRY),
                "core rejected a bot response in seed " + seed);

            for(RawMessage message : trace.messages)
            {
                DuelMessage decoded = DuelMessage.decode(message); // must not throw
                if(decoded instanceof DuelMessage.Prompt)
                {
                    promptsSeen.add(message.name());
                }
            }
            if(trace.completed)
            {
                completed++;
            }
            sawSum |= trace.sawMessage(OcgConstants.MSG_SELECT_SUM);
        }

        System.out.println("Prompt types exercised: " + promptsSeen);
        assertTrue(sawSum, "no MSG_SELECT_SUM in 12 duels — ritual/synchro path never exercised");
        assertTrue(completed >= 10, "only " + completed + "/12 duels reached a result");
    }

    @Test
    void sumSolverMatchesCoreAcceptance()
    {
        // Exact-count mode: two Level 4s for a Level 8 ritual.
        DuelMessage.SumCard four = new DuelMessage.SumCard(1, null, 4);
        DuelMessage.SumCard three = new DuelMessage.SumCard(2, null, 3);
        DuelMessage.SelectSum exact = new DuelMessage.SelectSum(0, true, 8, 1, 3,
            List.of(), List.of(four, four, three));

        assertTrue(SumSolver.accepts(exact, new int[] {0, 1}), "4 + 4 = 8 must be accepted");
        assertFalse(SumSolver.accepts(exact, new int[] {0, 2}), "4 + 3 = 7 must be rejected");
        assertFalse(SumSolver.accepts(exact, new int[] {0}), "4 alone must be rejected");

        // Dual-parameter card (e.g. a level-changing effect): 4 or 6.
        DuelMessage.SumCard fourOrSix = new DuelMessage.SumCard(3, null, 4 | (6 << 16));
        DuelMessage.SelectSum dual = new DuelMessage.SelectSum(0, true, 10, 1, 3,
            List.of(), List.of(fourOrSix, four));
        assertTrue(SumSolver.accepts(dual, new int[] {0, 1}), "6 + 4 = 10 via the alternate value");
    }
}
