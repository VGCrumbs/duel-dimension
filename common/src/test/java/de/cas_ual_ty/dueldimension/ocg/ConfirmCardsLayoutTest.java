package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reads the real byte layout of the reveal messages off a live core.
 * <p>
 * Written because guessing it got it wrong: MSG_CONFIRM_CARDS was decoded as
 * player, count, then code plus a three-byte location per card, and every duel
 * came back with six undecoded bytes. Rather than try another shape, this
 * prints what the core actually sends so the decoder can be written against
 * the bytes instead of against an assumption.
 * <p>
 * Not an assertion about behaviour — it is a measuring instrument, and it only
 * fails if a message it captured cannot be explained by the layout it prints.
 */
class ConfirmCardsLayoutTest
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

    private static String hex(byte[] bytes)
    {
        StringBuilder out = new StringBuilder();
        for(byte value : bytes)
        {
            out.append(String.format("%02x ", value));
        }
        return out.toString().trim();
    }

    @Test
    void printTheRevealMessageLayout() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        de.cas_ual_ty.dueldimension.ocg.CdbCardProvider cards =
            new de.cas_ual_ty.dueldimension.ocg.CdbCardProvider(List.of(cdb()));

        Map<Integer, List<byte[]>> captured = new TreeMap<>();
        // Several seeds, because a reveal only happens when an effect that
        // shows a card actually resolves.
        for(long seed : new long[] {12345, 777777, 20260805, 424242, 999331, 5150})
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

            for(RawMessage message : trace.messages)
            {
                if(message.type() == OcgConstants.MSG_CONFIRM_CARDS
                    || message.type() == OcgConstants.MSG_CONFIRM_DECKTOP
                    || message.type() == OcgConstants.MSG_CONFIRM_EXTRATOP)
                {
                    captured.computeIfAbsent(message.type(), key -> new ArrayList<>())
                        .add(message.payload());
                }
            }
        }

        System.out.println("---- reveal message payloads ----");
        if(captured.isEmpty())
        {
            System.out.println("none seen in these duels");
        }
        captured.forEach((type, payloads) ->
        {
            String name = switch(type)
            {
                case OcgConstants.MSG_CONFIRM_CARDS -> "MSG_CONFIRM_CARDS";
                case OcgConstants.MSG_CONFIRM_DECKTOP -> "MSG_CONFIRM_DECKTOP";
                default -> "MSG_CONFIRM_EXTRATOP";
            };
            System.out.println(name + ": " + payloads.size() + " seen");
            payloads.stream().limit(6).forEach(payload ->
            {
                int count = payload.length > 1 ? payload[1] & 0xFF : -1;
                String perCard = count > 0
                    ? String.valueOf((payload.length - 2) / (double)count) : "n/a";
                System.out.println("   len=" + payload.length + "  player=" + (payload[0] & 0xFF)
                    + "  byte1=" + count + "  (len-2)/byte1=" + perCard);
                System.out.println("      " + hex(payload));
            });
        });
    }
}
