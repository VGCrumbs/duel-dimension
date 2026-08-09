package de.cas_ual_ty.dueldimension.ocg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives one complete duel outside Minecraft: creates the core duel, loads
 * base scripts, registers decks, pumps the message loop, routes the stream to
 * both {@link ResponseSource}s, and collects a full {@link DuelTrace}.
 * <p>
 * The trace triple (seed, decks, responses) fully determines a duel — it is
 * the foundation of the replay/fuzz-repro format (Phase 2).
 * <p>
 * Single-threaded, like the {@link OcgDuel} it drives.
 */
public class HeadlessDuelRunner
{
    /**
     * Cards of one player, as passcodes, in deck-list order. {@link #registerDecks}
     * shuffles {@code main} before handing it to the core — the core itself never
     * shuffles a deck at duel start.
     */
    /**
     * A deck, and optionally one card it promises to open with.
     *
     * @param guaranteed a passcode to place at {@link #OPENING_SLOT}, or 0 for
     *                   an untouched deck. The card must already be in
     *                   {@code main} — this moves a card, it never adds one, so
     *                   the deck list stays exactly as legal as it was.
     */
    public record Deck(List<Integer> main, List<Integer> extra, int guaranteed)
    {
        /** A deck that promises nothing, which is nearly all of them. */
        public Deck(List<Integer> main, List<Integer> extra)
        {
            this(main, extra, 0);
        }

        /** The same deck, promising to draw this card. */
        public Deck guaranteeing(int passcode)
        {
            return new Deck(main, extra, passcode);
        }
    
        public static final Deck EMPTY = new Deck(List.of(), List.of());
    }

    /** Decoded MSG_WIN. Winner 0/1 is a player index; 2 is a draw. */
    public record DuelResult(int winner, int reason)
    {
    }

    /** Everything that happened in one run. */
    public static class DuelTrace
    {
        public final List<RawMessage> messages = new ArrayList<>();
        public final List<byte[]> responses = new ArrayList<>();
        public DuelResult result;
        /**
         * True if the duel reached a decisive end: MSG_WIN observed (the core
         * does NOT return DUEL_STATUS_END after a win — it keeps processing
         * zombie turns; stopping is the host's job) or the core reported END.
         * False if the run aborted (no response, step cap).
         */
        public boolean completed;
        public int steps;

        public boolean sawMessage(int type)
        {
            return messages.stream().anyMatch(m -> m.type() == type);
        }
    }

    public static class Builder
    {
        private final OcgApi api;
        private long[] seed = {1, 2, 3, 4};
        private long flags = OcgConstants.DUEL_MODE_MR5;
        private OcgDuel.PlayerConfig team1 = OcgDuel.PlayerConfig.DEFAULT;
        private OcgDuel.PlayerConfig team2 = OcgDuel.PlayerConfig.DEFAULT;
        private OcgDuel.CardProvider cards = code -> null;
        private OcgDuel.ScriptProvider scripts = name -> null;
        private OcgDuel.LogSink log = (message, type) -> {};
        private final Deck[] decks = {Deck.EMPTY, Deck.EMPTY};
        private final ResponseSource[] responders = new ResponseSource[2];
        private boolean stopOnWin = true;

        private Builder(OcgApi api)
        {
            this.api = api;
        }

        public Builder seed(long[] seed)
        {
            this.seed = seed;
            return this;
        }

        public Builder flags(long flags)
        {
            this.flags = flags;
            return this;
        }

        public Builder players(OcgDuel.PlayerConfig team1, OcgDuel.PlayerConfig team2)
        {
            this.team1 = team1;
            this.team2 = team2;
            return this;
        }

        public Builder cards(OcgDuel.CardProvider cards)
        {
            this.cards = cards;
            return this;
        }

        public Builder scripts(OcgDuel.ScriptProvider scripts)
        {
            this.scripts = scripts;
            return this;
        }

        public Builder log(OcgDuel.LogSink log)
        {
            this.log = log;
            return this;
        }

        public Builder deck(int player, Deck deck)
        {
            decks[player] = deck;
            return this;
        }

        public Builder responder(int player, ResponseSource source)
        {
            responders[player] = source;
            return this;
        }

        /**
         * When false, keep pumping after MSG_WIN (the core happily plays
         * zombie turns) — useful as a prompt generator in tests.
         */
        public Builder stopOnWin(boolean stopOnWin)
        {
            this.stopOnWin = stopOnWin;
            return this;
        }

        public HeadlessDuelRunner build()
        {
            for(int i = 0; i < 2; i++)
            {
                if(responders[i] == null)
                {
                    throw new IllegalStateException("No responder for player " + i);
                }
            }
            return new HeadlessDuelRunner(this);
        }
    }

    public static Builder builder(OcgApi api)
    {
        return new Builder(api);
    }

    /**
     * {@link OcgDuel.ScriptProvider} over a ProjectIgnis/CardScripts checkout:
     * constant.lua/utility.lua at the root, card scripts under official/.
     */
    public static OcgDuel.ScriptProvider cardScriptsDirectory(Path root)
    {
        return name ->
        {
            for(Path candidate : new Path[] {root.resolve(name), root.resolve("official").resolve(name)})
            {
                if(Files.isRegularFile(candidate))
                {
                    try
                    {
                        return Files.readAllBytes(candidate);
                    }
                    catch(Exception e)
                    {
                        return null;
                    }
                }
            }
            return null;
        };
    }

    private final Builder config;

    private HeadlessDuelRunner(Builder config)
    {
        this.config = config;
    }

    /**
     * Runs the duel to completion, abort, or the step cap; never throws for
     * in-duel conditions — inspect the returned trace.
     */
    public DuelTrace run(int maxSteps)
    {
        DuelTrace trace = new DuelTrace();

        try(OcgDuel duel = OcgDuel.create(config.api, config.seed, config.flags,
            config.team1, config.team2, config.cards, config.scripts, config.log))
        {
            loadBaseScripts(duel);
            registerDecks(duel);

            for(int player = 0; player < 2; player++)
            {
                config.responders[player].onDuelStart(player, observerFor(duel, player));
            }

            duel.start();
            pump(duel, trace, maxSteps);
        }

        for(ResponseSource responder : config.responders)
        {
            responder.onDuelEnd(trace.result);
        }
        return trace;
    }

    private static de.cas_ual_ty.dueldimension.ocg.query.BoardObserver observerFor(OcgDuel duel, int player)
    {
        return new de.cas_ual_ty.dueldimension.ocg.query.BoardObserver()
        {
            @Override
            public de.cas_ual_ty.dueldimension.ocg.query.BoardState observe()
            {
                return de.cas_ual_ty.dueldimension.ocg.query.BoardState.observe(duel, player);
            }

            @Override
            public de.cas_ual_ty.dueldimension.ocg.query.BoardState observeOmnisciently()
            {
                return de.cas_ual_ty.dueldimension.ocg.query.BoardState.observeOmnisciently(duel, player);
            }
        };
    }

    private void loadBaseScripts(OcgDuel duel)
    {
        for(String name : new String[] {"constant.lua", "utility.lua"})
        {
            byte[] content = config.scripts.load(name);
            if(content != null)
            {
                duel.loadScript(name, content);
            }
        }
    }

    /**
     * Shuffles and registers both decks, following the reference host in
     * {@code gframe/generic_duel.cpp}.
     * <p>
     * The core does <em>not</em> shuffle at duel start — {@code processor.cpp}
     * only shuffles a deck when {@code core.shuffle_deck_check[p]} is raised by
     * a card effect. Shuffling the opening deck is the host's job, and skipping
     * it is why every duel dealt an identical hand.
     * <p>
     * Ported verbatim from the reference, twice over:
     * <ul>
     * <li>only {@code main} is shuffled; the extra deck keeps its order, since
     * it is chosen from rather than drawn;</li>
     * <li>the shuffle is skipped when {@link OcgConstants#DUEL_PSEUDO_SHUFFLE}
     * is set, which is how hand-testing and replays keep a fixed order;</li>
     * <li>cards are registered back to front ({@code for i = size-1; i >= 0})
     * so element 0 of the list ends up on top of the deck.</li>
     * </ul>
     * The one deliberate difference: the reference seeds its shuffle from
     * {@code Utils::GetRandomNumberGenerator()}, a fresh random_device-backed
     * Xoshiro256**, and records the resulting order in the replay file. We have
     * no replay file to lean on, so the shuffle is derived from the duel seed
     * instead — that keeps (seed, decks, responses) a complete description of a
     * duel, which the fuzzer and {@link Replay} both rely on.
     */
    private void registerDecks(OcgDuel duel)
    {
        boolean pseudoShuffle = (config.flags & OcgConstants.DUEL_PSEUDO_SHUFFLE) != 0;

        for(int player = 0; player < 2; player++)
        {
            Deck deck = config.decks[player];

            List<Integer> main = new ArrayList<>(deck.main());
            if(!pseudoShuffle)
            {
                // A per-player stream so one player's deck size cannot shift the other's order.
                java.util.Collections.shuffle(main, shuffleRandom(config.seed, player));
            }

            placeGuaranteed(main, deck.guaranteed());

            for(int i = main.size() - 1; i >= 0; i--)
            {
                duel.newCard(player, 0, main.get(i), player, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }

            List<Integer> extra = deck.extra();
            for(int i = extra.size() - 1; i >= 0; i--)
            {
                duel.newCard(player, 0, extra.get(i), player, OcgConstants.LOCATION_EXTRA, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }
        }
    }

    /**
     * Where a guaranteed card sits: the sixth from the top.
     * <p>
     * The opening hand is the first five, so index 5 is the very next card
     * drawn. With the first-turn draw on, that is the card the player going
     * first draws on turn one — and the player going second draws it on theirs.
     * The same promise either way, without the seat changing what it means.
     */
    private static final int OPENING_SLOT = 5;

    /**
     * Moves one card to {@link #OPENING_SLOT}, after the shuffle.
     * <p>
     * After, not instead of: the deck is still shuffled properly and every
     * other card is where chance put it. This is possible at all only because
     * the core does not shuffle the opening deck — see {@link #registerDecks} —
     * so an order set here is the order that is played.
     * <p>
     * Moves rather than inserts, so the deck holds exactly the cards it held
     * before; a deck without the card is left alone rather than given one.
     */
    // Package-private rather than private so the ordering guarantee can be
    // tested without the native engine, which is 32-bit and Windows-only.
    static void placeGuaranteed(List<Integer> main, int passcode)
    {
        if(passcode == 0)
        {
            return;
        }
        int at = main.indexOf(passcode);
        if(at < 0)
        {
            return;   // not in this deck; nothing was promised
        }
        main.remove(at);
        // A deck shorter than the opening draw keeps the card as deep as it can.
        main.add(Math.min(OPENING_SLOT, main.size()), passcode);
    }

    /** Mixes the four seed words and the seat into one shuffle stream. */
    private static java.util.Random shuffleRandom(long[] seed, int player)
    {
        long mixed = 0x9E3779B97F4A7C15L * (player + 1);
        for(long word : seed)
        {
            mixed = mixed * 0x100000001B3L ^ word;
        }
        return new java.util.Random(mixed);
    }

    /** Rejections of one question before the duel is given up on. */
    private static final int MAX_REJECTIONS = 32;

    private void pump(OcgDuel duel, DuelTrace trace, int maxSteps)
    {
        RawMessage lastMessage = null;
        // Consecutive rejections of the same question. A human corrects
        // themselves and a bot may not, so this is bounded rather than trusted
        // to end: without it a bot that keeps giving the same illegal answer
        // spins the duel thread forever.
        int rejections = 0;

        for(trace.steps = 0; trace.steps < maxSteps; trace.steps++)
        {
            int status = duel.process();

            for(byte[] bytes : duel.getMessages())
            {
                RawMessage message = RawMessage.of(bytes);
                trace.messages.add(message);
                if(message.type() == OcgConstants.MSG_RETRY)
                {
                    // NOT the message to answer. MSG_RETRY says the previous
                    // answer was refused and the previous QUESTION still
                    // stands, so the prompt being responded to is left alone --
                    // treating the rejection itself as the prompt asked the
                    // responder to answer a message it cannot read, and its
                    // first payload byte, meaning nothing, chose which player
                    // was asked.
                    rejections++;
                    for(ResponseSource responder : config.responders)
                    {
                        responder.onAnswerRejected();
                    }
                    continue;
                }
                lastMessage = message;
                rejections = 0;

                if(message.type() == OcgConstants.MSG_WIN && message.payload().length >= 2)
                {
                    trace.result = new DuelResult(message.payload()[0] & 0xFF, message.payload()[1] & 0xFF);
                }

                for(ResponseSource responder : config.responders)
                {
                    responder.observe(message);
                }
            }

            if(config.stopOnWin && trace.result != null)
            {
                trace.completed = true;
                return;
            }

            if(status == OcgConstants.DUEL_STATUS_END)
            {
                trace.completed = true;
                return;
            }

            if(status == OcgConstants.DUEL_STATUS_AWAITING)
            {
                // The response belongs to the most recent message (EDOPro's model).
                if(lastMessage == null)
                {
                    return; // AWAITING with no prompt: corrupt state, abort
                }
                if(rejections > MAX_REJECTIONS)
                {
                    // The same question refused this many times running means
                    // nobody here can answer it; ending is better than a thread
                    // that never returns.
                    return;
                }
                byte[] response = config.responders[lastMessage.promptedPlayer() & 1].respond(lastMessage);
                if(response == null)
                {
                    return; // no responder available: abort, completed stays false
                }
                trace.responses.add(response);
                duel.setResponse(response);
            }
        }
    }
}
