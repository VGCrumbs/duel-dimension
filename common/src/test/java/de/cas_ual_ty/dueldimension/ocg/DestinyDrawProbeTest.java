package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A scratch harness for finding out WHERE the Destiny Draw stops.
 * <p>
 * Not an assertion about the mod -- it loads hand-written chunks and reports
 * what the engine does with each, so a failure can be attributed to
 * registration, to collection, or to the condition.
 */
class DestinyDrawProbeTest
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

    private static final long DESC = DestinyDrawScript.DESCRIPTION;
    /** A value nothing else emits, so a hint carrying it can only be ours. */
    private static final long MARKER = 0x5EEDBEEFL;
    /** A condition that announces itself, so "never called" is distinguishable. */
    private static final String LOUD_TRUE =
        "e:SetCondition(function(e,tp) Duel.Hint(HINT_MESSAGE,0," + 0x5EEDBEEFL
            + ") return true end)";

    private static String chunk(String... body)
    {
        return typed("EFFECT_TYPE_FIELD+EFFECT_TYPE_TRIGGER_O", body);
    }

    private static String typed(String type, String... body)
    {
        StringBuilder lua = new StringBuilder("do\n");
        lua.append("  local e=Effect.GlobalEffect()\n");
        lua.append("  e:SetDescription(").append(DESC).append(")\n");
        lua.append("  e:SetType(").append(type).append(")\n");
        lua.append("  e:SetCode(EVENT_PREDRAW)\n");
        for(String line : body)
        {
            lua.append("  ").append(line).append('\n');
        }
        lua.append("  e:SetOperation(function(e,tp) end)\n");
        lua.append("  Duel.RegisterEffect(e,0)\n");
        lua.append("end\n");
        return lua.toString();
    }


    /** Whether any MSG_HINT in the trace carried our marker value. */
    private static boolean sawMarker(HeadlessDuelRunner.DuelTrace trace, long marker)
    {
        for(RawMessage raw : trace.messages)
        {
            if(raw.type() != OcgConstants.MSG_HINT)
            {
                continue;
            }
            byte[] p = raw.payload();
            for(int i = 0; i + 8 <= p.length; i++)
            {
                long v = 0;
                for(int b = 7; b >= 0; b--)
                {
                    v = (v << 8) | (p[i + b] & 0xFFL);
                }
                if(v == marker)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Counts chain offers carrying DESC when the given chunk is registered. */
    private static int run(OcgApi api, CdbCardProvider cards, String label, String lua)
    {
        HeadlessDuelRunner.Deck yugi = StarterDecks.YUGI.load().toRunnerDeck();
        OcgDuel.PlayerConfig low = new OcgDuel.PlayerConfig(4000,
            OcgDuel.PlayerConfig.DEFAULT.startingDrawCount(),
            OcgDuel.PlayerConfig.DEFAULT.drawCountPerTurn());

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {12345, 999, 4242, 7})
            .flags(OcgConstants.DUEL_MODE_MR5)
            .players(low, low)
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, yugi)
            .deck(1, StarterDecks.JOEY.load().toRunnerDeck())
            .extraScript("probe.lua", lua)
            .responder(0, new HeuristicBot(3, cards, cards.all()))
            .responder(1, new HeuristicBot(5, cards, cards.all()))
            .build()
            .run(20000);

        int found = 0;
        for(RawMessage raw : trace.messages)
        {
            if(DuelMessage.decode(raw) instanceof DuelMessage.SelectChain chain)
            {
                for(DuelMessage.ChainOption option : chain.chains())
                {
                    if(option.description() == DESC)
                    {
                        found++;
                    }
                }
            }
        }
        System.out.println("PROBE " + label + ": " + found + " offer(s), condition ran="
            + sawMarker(trace, MARKER));
        return found;
    }

    @Test
    @DisplayName("probe: which part of the Destiny Draw chunk stops it firing")
    void probe() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        // Does anything load at all? A chunk that cannot parse must report
        // false; if it reports true, loadScript is not telling us the truth.
        run(api, cards, "deliberately broken", "this is not lua(((");
        run(api, cards, "bare", chunk());
        run(api, cards, "true condition", chunk(LOUD_TRUE));
        // processor.cpp:1255 skips a trigger whose range excludes LOCATION_HAND
        // unless its handler is effect-enabled -- and temp_card never is.
        run(api, cards, "range HAND",
            chunk("e:SetRange(LOCATION_HAND)",
                LOUD_TRUE));
        run(api, cards, "range 0xff",
            chunk("e:SetRange(0xff)",
                LOUD_TRUE));
        // triggering_player is temp_card's controller (PLAYER_NONE) unless the
        // effect asks for the event's player instead.
        run(api, cards, "range 0xff + EVENT_PLAYER",
            chunk("e:SetRange(0xff)",
                "e:SetProperty(EFFECT_FLAG_EVENT_PLAYER)",
                LOUD_TRUE));
        // The continuous loop at the TOP of process_instant_event calls
        // is_activateable directly -- no STATUS_EFFECT_ENABLED gate, and
        // is_activateable skips every handler check for a FIELD_ONLY effect.
        // If a card-less effect can be collected at all, it is here.
        run(api, cards, "CONTINUOUS",
            typed("EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS", LOUD_TRUE));
        run(api, cards, "CONTINUOUS + EVENT_PLAYER",
            typed("EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS",
                "e:SetProperty(EFFECT_FLAG_EVENT_PLAYER)", LOUD_TRUE));

        run(api, cards, "range HAND + EVENT_PLAYER + count",
            chunk("e:SetRange(LOCATION_HAND)",
                "e:SetProperty(EFFECT_FLAG_EVENT_PLAYER)",
                "e:SetCountLimit(1," + DestinyDrawScript.EFFECT_ID + ",EFFECT_COUNT_CODE_DUEL)",
                LOUD_TRUE));
    }
}
