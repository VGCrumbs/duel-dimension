package de.cas_ual_ty.dueldimension.ocg.session;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import de.cas_ual_ty.dueldimension.ocg.prompt.HumanResponseSource;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptTranslator;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A duel between two PEOPLE, played from both sides.
 * <p>
 * Everything the suite had covered one human: the NPC path seats the only
 * player at 0, where the core's player numbering and the numbering the client
 * draws in happen to coincide. They do not coincide at seat 1, and nothing
 * noticed — a two-player duel was unplayable for the second player (they could
 * end their turn and nothing else) through two whole trees, because no test
 * ever asked seat 1 to do anything.
 * <p>
 * So this drives a real duel with a {@link HumanResponseSource} at BOTH seats,
 * wired exactly as {@code DuelistDuels.startPlayerDuel} wires them, and plays
 * it the way the screen does: it finds what to click on the board it was sent,
 * matches the prompt's options against that click the way
 * {@code EngineDuelScreen.optionsFor} matches them, and answers with the index
 * it found. An option the screen could not have reached is an option the player
 * could not have used.
 * <p>
 * What it covers: prompt delivery per seat, answer routing per seat, and that
 * every card option names its slot in the numbering the seat's own board is
 * drawn in — for BOTH seats, against the live core.
 * <p>
 * What it does not cover: the pixels. There is no screen here, so the hit
 * rectangles, the menu and the packet round trip are stood in for by the same
 * rules written out again. It also plays a deliberately dull game — one action
 * then end turn — so it says nothing about long chains or the battle phase.
 */
class PvpBothSeatsPlayableTest
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

    private static Path stringsConf()
    {
        return Path.of(System.getProperty("ocg.strings", "C:/ProjectIgnis/config/strings.conf"));
    }

    /** How long the whole duel may take before the test gives up on it. */
    private static final long BUDGET_NANOS = 120_000_000_000L;

    @Test
    void bothSeatsCanActOnTheirOwnCards() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        DescriptionTable text = new DescriptionTable(
            Files.isRegularFile(stringsConf()) ? stringsConf() : null, List.of(cdb()));

        // ---- wired as DuelistDuels.startPlayerDuel wires it ----
        DuelSession[] sessionHolder = new DuelSession[1];
        HumanResponseSource[] responders = new HumanResponseSource[2];
        for(int index = 0; index < 2; index++)
        {
            int seat = index;
            responders[seat] = new HumanResponseSource(new PromptTranslator(cards, text),
                (prompt, source) ->
                {
                    DuelSession running = sessionHolder[0];
                    if(running != null)
                    {
                        running.postPrompt(prompt, source.pendingSerial(), seat);
                    }
                });
        }

        // The SEATS map, in miniature. An answer is routed by WHO SENT IT and
        // never by "the challenger": two ids, two responders, and the duel
        // below never learns which of them started it.
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<UUID, HumanResponseSource> seats = new HashMap<>();
        seats.put(first, responders[0]);
        seats.put(second, responders[1]);
        UUID[] bySeat = {first, second};

        DuelSession session = DuelSession.create("pvp-both-seats", api, OcgConstants.DUEL_MODE_MR5,
            new long[] {90210, 8675309, 4815162, 31337},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
            responders[0], responders[1]);
        sessionHolder[0] = session;

        Clicker[] clickers = {new Clicker(0), new Clicker(1)};
        int crossSeatRejections = 0;
        List<String> leaks = new ArrayList<>();

        session.start();
        try
        {
            long deadline = System.nanoTime() + BUDGET_NANOS;
            while(session.isRunning() && System.nanoTime() < deadline
                && !(clickers[0].boardClicks > 0 && clickers[1].boardClicks > 0))
            {
                // The server tick: one thread drains, exactly as DuelistDuels
                // does, so nothing here ever touches the engine.
                List<DuelSession.Event.Prompt> asked = new ArrayList<>();
                session.drainEvents(event ->
                {
                    if(event instanceof DuelSession.Event.Prompt prompt)
                    {
                        asked.add(prompt);
                    }
                });

                for(DuelSession.Event.Prompt asking : asked)
                {
                    int seat = asking.seat();
                    Clicker clicker = clickers[seat];
                    EnginePrompt prompt = asking.prompt();
                    clicker.prompts++;

                    // Hidden information, per prompt: the board a prompt
                    // carries is that seat's own view, so the other player's
                    // hand must arrive as blanks in it.
                    for(BoardSnapshot.Slot slot : prompt.field().opponent().hand())
                    {
                        if(slot.present() && slot.code() != 0)
                        {
                            leaks.add("seat " + seat + " could identify an opponent hand card: "
                                + slot.code());
                        }
                    }

                    clicker.checkAddressing(prompt);

                    // The other seat is not the one being asked, so its
                    // responder must refuse this answer even though the serial
                    // is the one currently outstanding. This is what proves the
                    // routing is per seat rather than per duel.
                    if(!responders[1 - seat].submit(new HumanResponseSource.Answer(
                        new int[] {0}, 0, asking.serial())))
                    {
                        crossSeatRejections++;
                    }

                    int[] chosen = clicker.choose(prompt);
                    // Routed the way submitAnswer routes it: look the player's
                    // own id up, answer through whatever that returns.
                    HumanResponseSource mine = seats.get(bySeat[seat]);
                    deliver(session, mine, new HumanResponseSource.Answer(chosen, 0, asking.serial()));
                }

                Thread.sleep(2);
            }
        }
        finally
        {
            session.stop();
        }

        for(Clicker clicker : clickers)
        {
            System.out.println("seat " + clicker.seat + ": " + clicker.prompts + " prompts, "
                + clicker.boardClicks + " actions taken by clicking the board, "
                + clicker.ownCardOptions + " options naming one of its own cards, "
                + clicker.misaddressed.size() + " of those in the wrong numbering");
        }
        System.out.println("cross-seat answers refused: " + crossSeatRejections);

        assertTrue(leaks.isEmpty(), "hidden information crossed seats: "
            + leaks.subList(0, Math.min(5, leaks.size())));

        for(Clicker clicker : clickers)
        {
            assertTrue(clicker.prompts > 0, "seat " + clicker.seat + " was never asked anything");
            assertEquals(List.of(), clicker.misaddressed,
                "seat " + clicker.seat + " was offered an option whose controller is not the one"
                    + " its own board is drawn in, so no click could ever match it");
            assertTrue(clicker.ownCardOptions > 0,
                "seat " + clicker.seat + " was never offered one of its own cards -- this test"
                    + " proved nothing about that seat");
            assertTrue(clicker.boardClicks > 0,
                "seat " + clicker.seat + " could not act on a single one of its own cards:"
                    + " every option it was given missed the board it was drawn on");
        }

        assertTrue(crossSeatRejections > 0,
            "an answer sent by the player who was NOT asked was accepted");
    }

    /**
     * Hands an answer over, waiting for the duel thread to be ready for it.
     * <p>
     * {@code submit} offers into a SynchronousQueue, and the prompt is posted
     * just BEFORE the duel thread parks to take from it. A real player answers
     * a network round trip later, so the two never race; a test that answers in
     * microseconds does, and a dropped answer would hang the duel until the
     * ten-minute timeout. Retrying is legal because the serial has not changed:
     * it is the same question, still being asked.
     */
    private static void deliver(DuelSession session, HumanResponseSource seat,
        HumanResponseSource.Answer answer) throws InterruptedException
    {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while(System.nanoTime() < deadline && session.isRunning())
        {
            if(seat.submit(answer))
            {
                return;
            }
            Thread.sleep(1);
        }
    }

    /** One click target, as BoardRenderer lays them out. Piles use sequence -1. */
    private record Target(int controller, int location, int sequence, int code)
    {
        boolean isPile()
        {
            return sequence < 0;
        }
    }

    /**
     * A stand-in for the screen: it knows only what the client knows, which is
     * a viewer-relative board and a list of options.
     */
    private static final class Clicker
    {
        private final int seat;
        int prompts;
        int boardClicks;
        int ownCardOptions;
        final List<String> misaddressed = new ArrayList<>();

        private Clicker(int seat)
        {
            this.seat = seat;
        }

        /**
         * Every option that names a card this seat can see on its own half of
         * the table must say so, or the click that lands on that card will
         * match nothing.
         * <p>
         * The card is found by looking, not by trusting the option: the option
         * is believed about WHICH card it is (the passcode) and about where in
         * a zone it sits, and the seat's own snapshot is then asked which side
         * that is. Only a card the seat is entitled to identify can be checked
         * this way, which is exactly the set the player can click.
         */
        void checkAddressing(EnginePrompt prompt)
        {
            BoardSnapshot field = prompt.field();
            for(EnginePrompt.Option option : prompt.options())
            {
                if(!option.hasSlot() || option.cardCode() == 0)
                {
                    continue;
                }
                boolean mine = holds(field.self(), option);
                boolean theirs = holds(field.opponent(), option);
                if(mine == theirs)
                {
                    // Not in either view (a card in a deck), or the same card
                    // in the same zone on both sides: nothing to conclude.
                    continue;
                }
                ownCardOptions += mine ? 1 : 0;
                int expected = mine ? 0 : 1;
                if(option.controller() != expected)
                {
                    misaddressed.add("seat " + seat + ": " + option.label() + " (card "
                        + option.cardCode() + ", location " + option.location() + ", sequence "
                        + option.sequence() + ") says controller " + option.controller()
                        + " but that card is on side " + expected + " of this seat's board");
                }
            }
        }

        private static boolean holds(BoardSnapshot.Side side, EnginePrompt.Option option)
        {
            List<BoardSnapshot.Slot> zone = zoneOf(side, option.location());
            if(option.sequence() < 0 || option.sequence() >= zone.size())
            {
                return false;
            }
            BoardSnapshot.Slot slot = zone.get(option.sequence());
            return slot.present() && slot.code() == option.cardCode();
        }

        private static List<BoardSnapshot.Slot> zoneOf(BoardSnapshot.Side side, int location)
        {
            return switch(location)
            {
                case OcgConstants.LOCATION_MZONE -> side.monsters();
                case OcgConstants.LOCATION_SZONE -> side.spells();
                case OcgConstants.LOCATION_HAND -> side.hand();
                case OcgConstants.LOCATION_GRAVE -> side.grave();
                case OcgConstants.LOCATION_REMOVED -> side.banished();
                case OcgConstants.LOCATION_EXTRA -> side.extra();
                // The deck is only counted for a client, so a card offered out
                // of it has no slot to be checked against.
                default -> List.of();
            };
        }

        /** What this seat answers, and how it got there. */
        int[] choose(EnginePrompt prompt)
        {
            if(prompt.chainWindow())
            {
                return new int[0]; // decline, as a player who wants nothing does
            }

            switch(prompt.kind())
            {
                case SORT:
                {
                    int[] order = new int[prompt.options().size()];
                    for(int i = 0; i < order.length; i++)
                    {
                        order[i] = i;
                    }
                    return order;
                }
                case COUNTERS:
                {
                    int[] amounts = new int[prompt.options().size()];
                    int remaining = prompt.minSelect();
                    for(int i = 0; i < amounts.length && remaining > 0; i++)
                    {
                        amounts[i] = Math.min(remaining, prompt.options().get(i).max());
                        remaining -= amounts[i];
                    }
                    return amounts;
                }
                case CHOOSE:
                {
                    // The one that matters: an idle or battle command list is
                    // reachable ONLY by clicking the board. EngineDuelScreen's
                    // buildBottomStrip skips every option with a slot and
                    // pickableCards refuses command options, so an option no
                    // click matches is an option that does not exist.
                    int clicked = clickBoard(prompt);
                    if(clicked >= 0)
                    {
                        boardClicks++;
                        return new int[] {clicked};
                    }
                    int phase = phaseOption(prompt);
                    if(phase >= 0)
                    {
                        return new int[] {phase};
                    }
                    break;
                }
                default:
                    break;
            }

            if(prompt.options().isEmpty())
            {
                return new int[0];
            }
            int count = Math.max(prompt.minSelect(), prompt.isSingleChoice() ? 1 : 0);
            count = Math.min(count, prompt.options().size());
            int[] chosen = new int[count];
            for(int i = 0; i < count; i++)
            {
                chosen[i] = i;
            }
            return chosen;
        }

        /**
         * One action per seat by clicking, then always end the turn -- the
         * point is to reach the OTHER seat's turn, not to duel well.
         */
        private int clickBoard(EnginePrompt prompt)
        {
            if(boardClicks > 0)
            {
                return -1;
            }
            for(Target target : targets(prompt.field()))
            {
                // Only this seat's own half of the table, and only the card
                // actually under the cursor. Both halves are drawn and both are
                // clickable, so an option in the wrong numbering DOES match --
                // it matches the opponent's card back sitting at the same
                // sequence. Counting that as "the player acted" would let the
                // very bug this test exists for pass, dressed up as summoning a
                // monster out of the other player's hand.
                if(target.controller() != 0)
                {
                    continue;
                }
                for(int index : optionsFor(prompt, target))
                {
                    EnginePrompt.Option option = prompt.options().get(index);
                    if(option.cardCode() != target.code())
                    {
                        continue;
                    }
                    // A summon or a set is an action whose result is visible in
                    // the next snapshot, which is what makes "seat 1 acted"
                    // more than "seat 1 answered".
                    if(option.command() == CardCommands.COMMAND_SUMMON
                        || option.command() == CardCommands.COMMAND_MSET
                        || option.command() == CardCommands.COMMAND_SSET)
                    {
                        return index;
                    }
                }
            }
            return -1;
        }

        /** {@code EngineDuelScreen.optionsFor}, written out again. */
        private static List<Integer> optionsFor(EnginePrompt prompt, Target target)
        {
            List<Integer> found = new ArrayList<>();
            for(int i = 0; i < prompt.options().size(); i++)
            {
                EnginePrompt.Option option = prompt.options().get(i);
                if(!option.hasSlot())
                {
                    continue;
                }
                if(option.isAt(target.controller(), target.location(), target.sequence()))
                {
                    found.add(i);
                }
                else if(target.isPile() && option.controller() == target.controller()
                    && option.location() == target.location())
                {
                    found.add(i);
                }
            }
            return found;
        }

        private static int phaseOption(EnginePrompt prompt)
        {
            int fallback = -1;
            for(int i = 0; i < prompt.options().size(); i++)
            {
                int command = prompt.options().get(i).command();
                if(command == CardCommands.PHASE_END_TURN)
                {
                    return i;
                }
                if(CardCommands.isPhaseAction(command) && command != CardCommands.PHASE_SHUFFLE)
                {
                    fallback = i;
                }
            }
            return fallback;
        }

        /** Every slot BoardRenderer draws a hit rectangle for, in its order. */
        private static List<Target> targets(BoardSnapshot field)
        {
            List<Target> targets = new ArrayList<>();
            for(int controller = 0; controller <= 1; controller++)
            {
                BoardSnapshot.Side side = controller == 0 ? field.self() : field.opponent();
                // Hands first: a first-turn summon comes out of one, and the
                // renderer lays them out over the field for the same reason.
                slots(targets, controller, OcgConstants.LOCATION_HAND, side.hand());
                slots(targets, controller, OcgConstants.LOCATION_MZONE, side.monsters());
                slots(targets, controller, OcgConstants.LOCATION_SZONE, side.spells());
                // Piles are one target each, matched by location alone.
                for(int location : new int[] {OcgConstants.LOCATION_GRAVE,
                    OcgConstants.LOCATION_REMOVED, OcgConstants.LOCATION_EXTRA,
                    OcgConstants.LOCATION_DECK})
                {
                    targets.add(new Target(controller, location, -1, 0));
                }
            }
            return targets;
        }

        private static void slots(List<Target> targets, int controller, int location,
            List<BoardSnapshot.Slot> zone)
        {
            for(int sequence = 0; sequence < zone.size(); sequence++)
            {
                BoardSnapshot.Slot slot = zone.get(sequence);
                if(slot.present())
                {
                    targets.add(new Target(controller, location, sequence, slot.code()));
                }
            }
        }
    }
}
