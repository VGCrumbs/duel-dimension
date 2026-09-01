package de.cas_ual_ty.dueldimension.ocg;

import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The end of the Destiny Draw chain: does the engine ever actually OFFER it?
 *
 * <h2>This is the test that says the feature works</h2>
 * Everything else about the Destiny Draw can pass while the feature does
 * nothing -- the chunk can be valid Lua, the core can accept it, the effect can
 * register, and the player can still never once be asked. This is the only test
 * that runs whole duels and looks for the question actually reaching a seat, so
 * it is the only one whose passing means anything to a player.
 * <p>
 * <b>A seed that offers nothing is not a failure.</b> The rule needs a board
 * where the turn player is at or below 4000 life and is staring at a monster
 * they cannot beat, and some seeds never produce one. The assertion is
 * therefore across a set of seeds rather than per seed; at the time of writing
 * four of the five offer it and 12345 does not.
 *
 * <h2>What a TRIGGER could not do, and why the effect is continuous</h2>
 * The obvious shape for "the engine asks you" is {@code EFFECT_TYPE_TRIGGER_O},
 * and it is silently inert here: it loads, it registers, and its condition is
 * never once called. Everything below was ruled out before that was understood,
 * and is kept because it is the map of where the bug was NOT.
 * Read out of {@code Other/ygopro-core}, not guessed:
 * <ul>
 * <li><b>The chunk not loading.</b> {@code loadScript} returns true for these
 * and false for deliberately broken Lua, so the plumbing is honest.</li>
 * <li><b>{@code Processors::Startup} wiping registrations.</b> It draws opening
 * hands and raises EVENT_STARTUP; it clears no effects.</li>
 * <li><b>The effect being filed as an aura instead of a trigger.</b>
 * {@code SetType} ORs in {@code EFFECT_TYPE_ACTIONS} itself for anything in the
 * {@code 0x0ff0} mask, which includes TRIGGER_O, so {@code field::add_effect}
 * files it into {@code trigger_o_effect}.</li>
 * <li><b>The handler/controller checks in {@code is_activateable}.</b> The whole
 * block is wrapped in {@code if (!is_flag(EFFECT_FLAG_FIELD_ONLY))}, and
 * {@code add_effect} sets FIELD_ONLY for a card-less effect.</li>
 * <li><b>The condition being wrong.</b> A bare trigger with no condition at all
 * is not offered either.</li>
 * <li><b>Range and EVENT_PLAYER.</b> {@code DestinyDrawProbeTest} tries
 * LOCATION_HAND, 0xff and EFFECT_FLAG_EVENT_PLAYER in every combination; none
 * produces an offer.</li>
 * </ul>
 *
 * <h2>Where it actually stopped</h2>
 * {@code processor.cpp:1251}, in {@code process_instant_event}. The trigger_o
 * collection reads {@code phandler->is_status(STATUS_EFFECT_ENABLED)}, and a
 * card-less effect's handler is the core's {@code temp_card}, which is never
 * given that status -- so a {@code GlobalEffect} trigger cannot be collected at
 * all, whatever range or property it is given.
 * <p>
 * The continuous loop at the TOP of the same function has no such gate: it asks
 * {@code is_activateable} directly, and that skips every handler check for a
 * {@code FIELD_ONLY} effect, which is what {@code Duel.RegisterEffect} makes a
 * card-less one. So the effect is {@code EFFECT_TYPE_CONTINUOUS} and therefore
 * mandatory, and the optionality moved into its operation as a
 * {@code Duel.SelectYesNo} -- which is why this test looks for a yes/no
 * carrying our description rather than for a chain option.
 *
 * @see DestinyDrawProbeTest the harness that narrowed this down, and which
 *      still reports CONTINUOUS as the only shape whose condition runs
 */
class DestinyDrawOfferedTest
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

    /** How many chain offers carried our description, across one duel. */
    private static int offers(OcgApi api, CdbCardProvider cards, long seed)
    {
        HeadlessDuelRunner.Deck yugi = StarterDecks.YUGI.load().toRunnerDeck();
        // Every card in the deck is flagged, so the condition's "is one still in
        // the deck" clause can never be the reason nothing appears.
        List<Integer> all = new ArrayList<>(yugi.main());
        // Both seats open on the condition's own life threshold, so the only
        // thing left to wait for is a board where one side has a monster and the
        // other has nothing bigger.
        OcgDuel.PlayerConfig low = new OcgDuel.PlayerConfig(4000,
            OcgDuel.PlayerConfig.DEFAULT.startingDrawCount(),
            OcgDuel.PlayerConfig.DEFAULT.drawCountPerTurn());

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed})
            .flags(OcgConstants.DUEL_MODE_MR5)
            .players(low, low)
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, yugi.flagging(all))
            .deck(1, StarterDecks.JOEY.load().toRunnerDeck())
            .responder(0, new HeuristicBot(seed, cards, cards.all()))
            .responder(1, new HeuristicBot(seed * 2 + 1, cards, cards.all()))
            .build()
            .run(20000);

        int found = 0;
        for(RawMessage raw : trace.messages)
        {
            // A yes/no carrying our description. The effect is continuous and
            // therefore mandatory; the CHOICE is the SelectYesNo its operation
            // asks, so that message -- not a chain option -- is the offer.
            if(DuelMessage.decode(raw) instanceof DuelMessage.SelectYesNo yesNo
                && yesNo.description() == DestinyDrawScript.DESCRIPTION)
            {
                found++;
            }
        }
        return found;
    }

    @Test
    @DisplayName("the engine offers the Destiny Draw when a duellist is in a pinch")
    void theOfferReachesThePlayer() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        int total = 0;
        for(long seed : new long[] {12345, 777777, 20260901, 42, 9001})
        {
            int found = offers(api, cards, seed);
            System.out.println("seed " + seed + ": Destiny Draw offered " + found + " time(s)");
            total += found;
        }
        assertTrue(total > 0,
            "no seed ever offered the Destiny Draw -- the effect registers but never fires");
    }
}
