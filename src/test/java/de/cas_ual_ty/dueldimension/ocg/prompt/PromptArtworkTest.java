package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.bot.RandomBot;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.query.BoardObserver;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A prompt names cards the board snapshot does not contain.
 * <p>
 * The main deck is never sent to a client — it is only counted — so when an
 * effect offers a choice out of it there is no slot on the client side to read
 * an artwork from, and every dressed copy came back wearing its printed one.
 * The graveyard, which IS a queried zone, was right all along, which is what
 * made the difference look like a rendering bug rather than a missing field.
 * <p>
 * These tests pin both halves of the fix against the live core: the artwork is
 * decided server-side at the moment the option is built, and it is withheld
 * from a viewer who may not identify the card.
 */
class PromptArtworkTest
{
    /** Normal Spell: "Banish 1 card from your Deck face-down." The reported case. */
    private static final int DIFFERENT_DIMENSION_CAPSULE = 11961740;
    private static final int GIANT_SOLDIER_OF_STONE = 13039848;

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

    private static void requireEngine()
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");
    }

    /** Every copy dressed, one artwork each, so a copy IS its artwork. */
    private static HeadlessDuelRunner.Deck dressed(List<Integer> main, int firstArt)
    {
        List<Integer> arts = new ArrayList<>(main.size());
        for(int i = 0; i < main.size(); i++)
        {
            arts.add(firstArt + i);
        }
        return new HeadlessDuelRunner.Deck(main, List.of()).wearing(arts, List.of());
    }

    private static List<Integer> deckOf(int copies, int code, int fillCopies, int fillCode)
    {
        List<Integer> main = new ArrayList<>();
        for(int i = 0; i < copies; i++)
        {
            main.add(code);
        }
        for(int i = 0; i < fillCopies; i++)
        {
            main.add(fillCode);
        }
        return main;
    }

    // ---- the reported bug ----

    /**
     * Plays a duel with a fully dressed deck, activating Different Dimension
     * Capsule to force a selection out of the DECK, and reads what the options
     * carry.
     */
    private static class Clicker implements ResponseSource
    {
        private final PromptTranslator translator;
        private final Random random = new Random(4242);
        private BoardObserver board;

        /** One entry per selection the engine offered out of our own deck. */
        final List<List<EnginePrompt.Option>> deckSelections = new ArrayList<>();
        /** The same selections, built the way a caller with no lookup would. */
        final List<List<EnginePrompt.Option>> undressed = new ArrayList<>();

        Clicker(PromptTranslator translator)
        {
            this.translator = translator;
        }

        @Override
        public void onDuelStart(int playerIndex, BoardObserver observer)
        {
            board = observer;
        }

        @Override
        public byte[] respond(RawMessage raw)
        {
            DuelMessage decoded = DuelMessage.decode(raw);
            byte[] automatic = translator.autoAnswer(decoded);
            if(automatic != null)
            {
                return automatic;
            }
            BoardSnapshot field = BoardSnapshot.of(board.observe());
            // Built twice from one message, on the duel thread, exactly as the
            // production path builds it once: with the lookup and without. The
            // pair is the whole claim -- without it the client is looking at a
            // field that is not on the wire.
            EnginePrompt bare = translator.toPrompt(decoded, field);
            EnginePrompt prompt = translator.toPrompt(decoded, field, board::coverOf);
            if(prompt == null
                || (prompt.options().isEmpty() && prompt.kind() != EnginePrompt.Kind.DECLARE_CARD))
            {
                return translator.autoAnswer(decoded);
            }

            List<EnginePrompt.Option> fromDeck = new ArrayList<>();
            List<EnginePrompt.Option> bareFromDeck = new ArrayList<>();
            for(int i = 0; i < prompt.options().size(); i++)
            {
                EnginePrompt.Option option = prompt.options().get(i);
                if(option.location() == OcgConstants.LOCATION_DECK && option.controller() == 0
                    && option.cardCode() != 0 && option.command() == 0)
                {
                    fromDeck.add(option);
                    bareFromDeck.add(bare.options().get(i));
                }
            }
            if(!fromDeck.isEmpty())
            {
                deckSelections.add(fromDeck);
                undressed.add(bareFromDeck);
            }

            return translator.toResponse(decoded, choose(prompt), 0);
        }

        /** Clicks the way a player chasing the capsule would. */
        private int[] choose(EnginePrompt prompt)
        {
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
                default:
                    break;
            }
            for(int i = 0; i < prompt.options().size(); i++)
            {
                // The capsule is the only activation in this deck, so
                // preferring one is how the deck selection is reached at all.
                if(prompt.options().get(i).command() == CardCommands.COMMAND_ACTIVATE)
                {
                    return new int[] {i};
                }
            }
            if(prompt.isSingleChoice())
            {
                return new int[] {random.nextInt(prompt.options().size())};
            }
            int count = Math.min(Math.max(prompt.minSelect(), 1), prompt.options().size());
            int[] chosen = new int[count];
            for(int i = 0; i < count; i++)
            {
                chosen[i] = i;
            }
            return chosen;
        }
    }

    @Test
    void aCardChosenFromTheDeckCarriesTheCopysOwnArtwork() throws Exception
    {
        requireEngine();

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        DescriptionTable text = new DescriptionTable(
            Files.isRegularFile(stringsConf()) ? stringsConf() : null, List.of(cdb()));

        int selectionsSeen = 0;
        int optionsSeen = 0;
        for(int seed = 1; seed <= 6 && selectionsSeen == 0; seed++)
        {
            // One translator, as in production -- and the reason the lookup is
            // an argument rather than a field.
            PromptTranslator translator = new PromptTranslator(cards, text);
            Clicker human = new Clicker(translator);
            HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed, seed * 31 + 5, seed * 131 + 9, ~seed})
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
                .deck(0, dressed(deckOf(3, DIFFERENT_DIMENSION_CAPSULE, 37, GIANT_SOLDIER_OF_STONE), 1)
                    .guaranteeing(DIFFERENT_DIMENSION_CAPSULE))
                .deck(1, new HeadlessDuelRunner.Deck(
                    deckOf(40, GIANT_SOLDIER_OF_STONE, 0, 0), List.of()))
                .responder(0, human)
                .responder(1, new RandomBot(seed * 7L, cards.all()))
                .build()
                .run(20000);

            for(int selection = 0; selection < human.deckSelections.size(); selection++)
            {
                List<EnginePrompt.Option> options = human.deckSelections.get(selection);
                selectionsSeen++;
                Set<Integer> arts = new HashSet<>();
                for(EnginePrompt.Option option : options)
                {
                    optionsSeen++;
                    assertTrue(option.art() != 0,
                        "a card offered out of a fully dressed deck came back on its printed"
                            + " artwork: code " + option.cardCode()
                            + " at deck sequence " + option.sequence());
                    assertTrue(option.art() >= 1 && option.art() <= 40,
                        "artwork " + option.art() + " is not one this deck was dressed with");
                    // Every copy wears a different artwork, so two options
                    // agreeing would mean the artwork came from the passcode
                    // rather than from the copy.
                    assertTrue(arts.add(option.art()),
                        "two cards in one deck selection wear artwork " + option.art());
                }
                // The same message, built without a lookup: this is exactly what
                // the client had to work with before, and why it drew printed art.
                for(EnginePrompt.Option option : human.undressed.get(selection))
                {
                    assertEquals(0, option.art(),
                        "an option built with no lookup invented an artwork");
                }
            }
        }

        System.out.println("deck selections observed: " + selectionsSeen
            + ", options dressed: " + optionsSeen);
        assertTrue(selectionsSeen > 0,
            "the capsule never resolved -- this test proved nothing about deck selections");
    }

    // ---- the safety line ----

    /** Holds both seats' observers so one responder can ask each the same question. */
    private static final class Seats
    {
        final BoardObserver[] observers = new BoardObserver[2];
    }

    private static class Capture implements ResponseSource
    {
        private final ResponseSource inner;
        private final Seats seats;
        private final int probeFrom;

        int ownDeckAnswered;
        int opponentDeckDressed;
        int leaks;
        int staleCodeAnswered;
        int malformedAnswered;
        final Set<Integer> ownArts = new HashSet<>();

        Capture(ResponseSource inner, Seats seats, int probeFrom)
        {
            this.inner = inner;
            this.seats = seats;
            this.probeFrom = probeFrom;
        }

        @Override
        public void onDuelStart(int playerIndex, BoardObserver board)
        {
            seats.observers[playerIndex] = board;
            inner.onDuelStart(playerIndex, board);
        }

        @Override
        public void observe(RawMessage message)
        {
            inner.observe(message);
        }

        @Override
        public byte[] respond(RawMessage prompt)
        {
            if(probeFrom == 0)
            {
                probe();
            }
            return inner.respond(prompt);
        }

        /** Asks both seats the same questions about the same cards. */
        private void probe()
        {
            BoardObserver mine = seats.observers[0];
            BoardObserver theirs = seats.observers[1];
            for(int sequence = 0; sequence < 5; sequence++)
            {
                int own = mine.coverOf(0, OcgConstants.LOCATION_DECK, sequence, GIANT_SOLDIER_OF_STONE);
                if(own != 0)
                {
                    ownDeckAnswered++;
                    ownArts.add(own);
                }
                // The positive control: seat 1 IS dressed, so there is
                // something here for the guard below to fail to withhold.
                if(theirs.coverOf(1, OcgConstants.LOCATION_DECK, sequence, GIANT_SOLDIER_OF_STONE) != 0)
                {
                    opponentDeckDressed++;
                }
                // The guard: `controller == player || card.isPublic()`. Seat 0
                // is neither the owner of seat 1's deck nor entitled by the
                // core to read it, so this must be 0 every single time.
                if(mine.coverOf(1, OcgConstants.LOCATION_DECK, sequence, GIANT_SOLDIER_OF_STONE) != 0)
                {
                    leaks++;
                }
            }
            // A sequence that has gone stale, or a triple no card can sit at.
            if(mine.coverOf(0, OcgConstants.LOCATION_DECK, 0, GIANT_SOLDIER_OF_STONE + 1) != 0)
            {
                staleCodeAnswered++;
            }
            if(mine.coverOf(0, 0, 0, GIANT_SOLDIER_OF_STONE) != 0
                || mine.coverOf(0, OcgConstants.LOCATION_DECK, -1, GIANT_SOLDIER_OF_STONE) != 0
                // -1 is an option's "controller unknown"; the core would index
                // player[255] with it.
                || mine.coverOf(-1, OcgConstants.LOCATION_DECK, 0, GIANT_SOLDIER_OF_STONE) != 0
                || mine.coverOf(2, OcgConstants.LOCATION_DECK, 0, GIANT_SOLDIER_OF_STONE) != 0
                || mine.coverOf(0, OcgConstants.LOCATION_MZONE | OcgConstants.LOCATION_OVERLAY, 0,
                    GIANT_SOLDIER_OF_STONE) != 0
                || mine.coverOf(0, OcgConstants.LOCATION_DECK | OcgConstants.LOCATION_GRAVE, 0,
                    GIANT_SOLDIER_OF_STONE) != 0)
            {
                malformedAnswered++;
            }
        }
    }

    /**
     * The artwork of a card in the OPPONENT's deck is theirs and not ours, and
     * asking for it by name does not change that.
     */
    @Test
    void anOpponentsDeckArtworkIsWithheldFromTheOtherSeat() throws Exception
    {
        requireEngine();

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        Seats seats = new Seats();
        // Both decks dressed, in ranges that cannot be confused: 1..40 is ours,
        // 41..80 is theirs. An artwork out of the wrong range is a leak with a
        // return address.
        Capture watcher = new Capture(new RandomBot(3, cards.all()), seats, 0);
        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {5, 15, 25, 35})
            .cards(cards)
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .deck(0, dressed(deckOf(40, GIANT_SOLDIER_OF_STONE, 0, 0), 1))
            .deck(1, dressed(deckOf(40, GIANT_SOLDIER_OF_STONE, 0, 0), 41))
            .responder(0, watcher)
            .responder(1, new Capture(new RandomBot(9, cards.all()), seats, 1))
            .build()
            .run(20000);

        assertTrue(trace.completed, "duel must finish");
        System.out.println("own deck answered: " + watcher.ownDeckAnswered
            + ", opponent deck dressed: " + watcher.opponentDeckDressed
            + ", leaks: " + watcher.leaks
            + ", distinct own artworks: " + new java.util.TreeSet<>(watcher.ownArts));

        assertTrue(watcher.ownDeckAnswered > 0,
            "our own deck never answered -- the query path was never exercised");
        assertTrue(watcher.opponentDeckDressed > 0,
            "the opponent's deck was never dressed -- there was nothing to leak");
        assertEquals(0, watcher.leaks,
            "an opponent's deck artwork reached the other seat");
        assertEquals(0, watcher.staleCodeAnswered,
            "a card whose passcode does not match answered anyway");
        assertEquals(0, watcher.malformedAnswered,
            "a triple no card can sit at answered anyway");
        for(int art : watcher.ownArts)
        {
            assertTrue(art >= 1 && art <= 40,
                "artwork " + art + " belongs to the opponent's range");
        }
    }
}
