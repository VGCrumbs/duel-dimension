package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The first real duel: two RandomBots with 40-card vanilla beatdown decks
 * pulled from the cdb (no hardcoded passcodes). This exercises the entire
 * stack at once — CdbCardProvider through the card-reader callback, deck
 * registration, script loading, every basic-duel prompt type, and the
 * response encoders, with the core validating everything (RETRY = fail).
 */
class RandomBotDuelTest
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

    /**
     * Level-4 non-pendulum vanilla normal monsters, no tokens, no alt-art
     * aliases — cards whose only prompts are summon/set/place/position/attack.
     */
    private static List<Integer> vanillaBeatdownDeck() throws Exception
    {
        List<Integer> codes = new ArrayList<>();
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + cdb());
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(
                "SELECT id FROM datas WHERE (level & 255) = 4 AND (type & 0x11) = 0x11" // TYPE_MONSTER|TYPE_NORMAL
                    + " AND (type & 0x4000) = 0 AND (type & 0x1000000) = 0 AND alias = 0" // no tokens, no pendulums
                    + " ORDER BY id LIMIT 14"))
        {
            while(rows.next())
            {
                codes.add(rows.getInt(1));
            }
        }

        List<Integer> deck = new ArrayList<>(40);
        for(int code : codes)
        {
            for(int i = 0; i < 3 && deck.size() < 40; i++)
            {
                deck.add(code);
            }
        }
        return deck;
    }

    @Test
    void twoRandomBotsFinishARealDuel() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        List<Integer> deck = vanillaBeatdownDeck();
        assertTrue(deck.size() == 40, "expected a full 40-card deck, got " + deck.size());

        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        assertTrue(cards.size() > 10000, "cdb looks implausibly small: " + cards.size());

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(OcgApi.load(lib()))
            .seed(new long[] {0xC0FFEE, 0xBEEF, 0xF00D, 0xD00D})
            .flags(OcgConstants.DUEL_MODE_MR5)
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, new HeadlessDuelRunner.Deck(deck, List.of()))
            .deck(1, new HeadlessDuelRunner.Deck(deck, List.of()))
            .responder(0, new RandomBot(101))
            .responder(1, new RandomBot(202))
            .build()
            .run(20000);

        assertFalse(trace.sawMessage(OcgConstants.MSG_RETRY),
            "core rejected a bot response — encoder or legality bug");
        assertTrue(trace.completed,
            "duel did not reach END (steps=" + trace.steps + ", messages=" + trace.messages.size()
                + ", responses=" + trace.responses.size() + ")");
        assertNotNull(trace.result, "finished duel must have a result");
        assertTrue(trace.sawMessage(OcgConstants.MSG_MOVE), "no cards ever moved — decks not registered?");
        assertTrue(trace.responses.size() > 10, "suspiciously few decisions for a full duel");

        // Every single message in the stream decodes without format errors.
        for(RawMessage message : trace.messages)
        {
            DuelMessage.decode(message);
        }
    }
}
