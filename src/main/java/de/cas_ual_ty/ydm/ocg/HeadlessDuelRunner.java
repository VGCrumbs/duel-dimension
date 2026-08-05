package de.cas_ual_ty.ydm.ocg;

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
    /** Cards of one player, as passcodes. Order is pre-shuffle (the core shuffles by seed). */
    public record Deck(List<Integer> main, List<Integer> extra)
    {
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
                config.responders[player].onDuelStart(player);
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

    private void registerDecks(OcgDuel duel)
    {
        for(int player = 0; player < 2; player++)
        {
            Deck deck = config.decks[player];
            for(int code : deck.main())
            {
                duel.newCard(player, 0, code, player, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }
            for(int code : deck.extra())
            {
                duel.newCard(player, 0, code, player, OcgConstants.LOCATION_EXTRA, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }
        }
    }

    private void pump(OcgDuel duel, DuelTrace trace, int maxSteps)
    {
        RawMessage lastMessage = null;

        for(trace.steps = 0; trace.steps < maxSteps; trace.steps++)
        {
            int status = duel.process();

            for(byte[] bytes : duel.getMessages())
            {
                RawMessage message = RawMessage.of(bytes);
                trace.messages.add(message);
                lastMessage = message;

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
