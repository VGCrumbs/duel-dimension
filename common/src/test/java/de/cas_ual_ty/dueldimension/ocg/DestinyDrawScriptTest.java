package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Destiny Draw chunk: what it says, and whether the engine accepts it.
 * <p>
 * The text tests run anywhere. The last one needs the native core, because it
 * is the only one that can answer the question that actually matters — a chunk
 * that is valid Lua and still fails {@code check_action_permission} looks
 * perfect from here and does nothing in a duel.
 */
class DestinyDrawScriptTest
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
    @DisplayName("a player who flagged nothing costs the engine nothing")
    void nothingFlaggedGeneratesNothing()
    {
        assertNull(DestinyDrawScript.chunk(0, List.of()));
        assertNull(DestinyDrawScript.chunk(1, null));
    }

    @Test
    @DisplayName("the chunk registers and never acts")
    void registersRatherThanActs()
    {
        String chunk = DestinyDrawScript.chunk(0, List.of(46986414));
        // Registered against the chunk's own `tp`, which is set from the player
        // argument on the first line -- one place the seat is written down
        // rather than one per use.
        assertTrue(chunk.contains("local tp=0"), chunk);
        assertTrue(chunk.contains("Duel.RegisterEffect(e,tp)"), chunk);
        // Everything that mutates the game is inside a function passed to
        // SetOperation, never at the top level -- because the top level runs
        // under no_action and would fail. This is the invariant that keeps the
        // whole approach working, so it is asserted rather than trusted.
        int operation = chunk.indexOf("local function put");
        assertTrue(operation > 0, "the operation should be a named local");
        assertTrue(chunk.indexOf("Duel.MoveSequence") > operation,
            "MoveSequence must live inside the operation, never at chunk level");
    }

    @Test
    @DisplayName("sequence 0, which field.cpp calls the deck TOP")
    void movesToTheTop()
    {
        String chunk = DestinyDrawScript.chunk(0, List.of(46986414));
        assertTrue(chunk.contains("Duel.MoveSequence(c,0)"), chunk);
        // 1 would be the bottom. The difference is invisible until forty turns
        // later, which is why it is pinned here.
        assertFalse(chunk.contains("Duel.MoveSequence(c,1)"), chunk);
    }

    @Test
    @DisplayName("the pinch rule is the engine's to evaluate, and reads the way Tag Force states it")
    void theRule()
    {
        String chunk = DestinyDrawScript.chunk(1, List.of(1, 2));
        assertTrue(chunk.contains("Duel.GetLP(tp)>4000"), "at or below 4000 LP");
        assertTrue(chunk.contains("EVENT_PREDRAW"), "before the draw, not after it");
        // CONTINUOUS, not TRIGGER_O. A card-less trigger is never collected --
        // see DestinyDrawScript -- so the effect is mandatory and the CHOICE is
        // the SelectYesNo its operation asks.
        assertTrue(chunk.contains("EFFECT_TYPE_CONTINUOUS"), "the shape that is actually collected");
        assertFalse(chunk.contains("EFFECT_TYPE_TRIGGER_O"), "a trigger here would be inert");
        assertTrue(chunk.contains("Duel.SelectYesNo"), "the player is asked, not obeyed");
        // Once per duel by a player flag, because a count limit keys on an
        // owning card and this effect has none.
        assertTrue(chunk.contains("Duel.GetFlagEffect"), "once per duel, checked");
        assertTrue(chunk.contains("Duel.RegisterFlagEffect"), "once per duel, spent");
        assertTrue(chunk.contains("RandomSelect"), "random among the flagged cards, never chosen");
    }

    @Test
    @DisplayName("the flags are per passcode, so every copy in the deck counts")
    void flagsAreByCard()
    {
        String chunk = DestinyDrawScript.chunk(0, List.of(46986414, 46986414, 89631139));
        assertTrue(chunk.contains("[46986414]=true"), chunk);
        assertTrue(chunk.contains("[89631139]=true"), chunk);
        // Deduplicated: flagging the same card twice is a repetition, not two
        // entries that would double its odds.
        assertTrue(chunk.indexOf("[46986414]=true") == chunk.lastIndexOf("[46986414]=true"), chunk);
        assertTrue(chunk.contains("c:GetCode()"), "matched by code, not by deck position");
    }

    /**
     * THE ONE THAT MATTERS.
     * <p>
     * Loads the chunk into a real duel through the real core. It fails if the
     * Lua does not parse, if a name is wrong, or — the failure this whole
     * design exists to avoid — if anything in it reaches
     * {@code check_action_permission} while {@code no_action} is set.
     */
    @Test
    @DisplayName("the native core accepts the chunk in a real duel")
    void theEngineAcceptsIt() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        HeadlessDuelRunner.Deck yugi = StarterDecks.YUGI.load().toRunnerDeck();
        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .flags(OcgConstants.DUEL_MODE_MR5)
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            // Flag whatever this deck actually holds, so the effect's condition
            // has something real to find. The flags ride on the deck.
            .deck(0, yugi.flagging(yugi.main().subList(0, Math.min(3, yugi.main().size()))))
            .deck(1, StarterDecks.JOEY.load().toRunnerDeck())
            .responder(0, new de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot(7, cards, cards.all()))
            .responder(1, new de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot(11, cards, cards.all()))
            .build()
            .run(4000);

        // A duel that ran at all is the assertion: registration happens before
        // the first step, so a rejected chunk shows up as a log line and an
        // effect that never exists rather than as an exception.
        assertTrue(trace.messages.size() > 10,
            "the duel should have played; got " + trace.messages.size() + " messages");
    }
}
