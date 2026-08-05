package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the one-summon-per-turn rule is actually being enforced.
 * <p>
 * A duel looked like the opponent was normal summoning or setting several
 * monsters in a single turn. The core enforces the rule itself, so either it
 * really is being broken or the board is being drawn wrong — this settles which
 * by counting the engine's own MSG_SUMMONING and MSG_SET per turn.
 * <p>
 * Special summons are deliberately not counted: any number are legal.
 */
class SummonLimitTest
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

    /** One turn's worth of tribute-free summoning activity. */
    private record TurnCount(int turn, int turnPlayer, int normalSummons, int sets)
    {
    }

    /**
     * MSG_SET covers both a monster set (operations.cpp, EVENT_MSET) and a
     * spell/trap set (EVENT_SSET), and any number of spell/trap sets are legal
     * in a turn. Its payload is {@code code} then a loc_info, so byte 5 is the
     * location: only a monster-zone set competes with the normal summon.
     */
    private static boolean isMonsterSet(RawMessage raw)
    {
        byte[] payload = raw.payload();
        return payload.length > 5 && (payload[5] & 0xFF) == OcgConstants.LOCATION_MZONE;
    }

    @Test
    void nobodyNormalSummonsTwiceInATurn() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        List<TurnCount> offenders = new ArrayList<>();
        int totalTurns = 0;

        for(long seed : new long[] {12345, 777777, 20260805, 424242})
        {
            HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed})
                .flags(OcgConstants.DUEL_MODE_MR5)
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
                .deck(0, StarterDecks.YUGI.load().toRunnerDeck())
                .deck(1, StarterDecks.JOEY.load().toRunnerDeck())
                .responder(0, new HeuristicBot(seed, cards, cards.all()))
                .responder(1, new HeuristicBot(seed * 2 + 1, cards, cards.all()))
                .build()
                .run(20000);

            int turn = 0;
            int turnPlayer = 0;
            int normalSummons = 0;
            int sets = 0;

            for(RawMessage raw : trace.messages)
            {
                if(raw.type() == OcgConstants.MSG_NEW_TURN)
                {
                    if(turn > 0 && normalSummons + sets > 1)
                    {
                        offenders.add(new TurnCount(turn, turnPlayer, normalSummons, sets));
                    }
                    turn++;
                    totalTurns++;
                    turnPlayer = DuelMessage.decode(raw) instanceof DuelMessage.NewTurn newTurn
                        ? newTurn.player() : -1;
                    normalSummons = 0;
                    sets = 0;
                }
                else if(raw.type() == OcgConstants.MSG_SUMMONING)
                {
                    normalSummons++;
                }
                else if(raw.type() == OcgConstants.MSG_SET && isMonsterSet(raw))
                {
                    sets++;
                }
            }
        }

        System.out.println("checked " + totalTurns + " turns; turns with more than one normal summon/set: "
            + offenders.size());
        offenders.forEach(offender -> System.out.println("  turn " + offender.turn()
            + " (player " + offender.turnPlayer() + "): " + offender.normalSummons()
            + " normal summons, " + offender.sets() + " sets"));

        assertTrue(totalTurns > 20, "not enough turns played to be meaningful: " + totalTurns);
        assertTrue(offenders.isEmpty(),
            "a player normal summoned/set more than once in a turn: " + offenders);
    }
}
