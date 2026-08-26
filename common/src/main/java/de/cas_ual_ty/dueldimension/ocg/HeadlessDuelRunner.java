package de.cas_ual_ty.dueldimension.ocg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * @param mainArts   the artwork each copy in {@code main} wears, by the
     *                   same index, 0 being the printed one. May be shorter
     *                   than {@code main} or empty — and it is empty for
     *                   almost every deck, because almost no card has a
     *                   second artwork.
     * @param extraArts  the same for {@code extra}.
     */
    public record Deck(List<Integer> main, List<Integer> extra, int guaranteed,
        List<Integer> mainArts, List<Integer> extraArts)
    {
        /** A deck that promises nothing, which is nearly all of them. */
        public Deck(List<Integer> main, List<Integer> extra)
        {
            this(main, extra, 0, List.of(), List.of());
        }

        /** A deck with a promise but no chosen artwork. */
        public Deck(List<Integer> main, List<Integer> extra, int guaranteed)
        {
            this(main, extra, guaranteed, List.of(), List.of());
        }

        /** The same deck, promising to draw this card. */
        public Deck guaranteeing(int passcode)
        {
            return new Deck(main, extra, passcode, mainArts, extraArts);
        }

        /** The same deck, with each copy's chosen artwork attached. */
        public Deck wearing(List<Integer> mainArts, List<Integer> extraArts)
        {
            return new Deck(main, extra, guaranteed, mainArts, extraArts);
        }

        /**
         * Whether any copy in this deck was dressed. False is the answer for
         * almost every deck ever built, and it is the switch that keeps the
         * whole artwork path — the extra lists, the generated Lua, the
         * verification query — from costing an ordinary duel anything.
         */
        public boolean hasAlternateArt()
        {
            return anyNonZero(mainArts) || anyNonZero(extraArts);
        }

        private static boolean anyNonZero(List<Integer> arts)
        {
            if(arts == null)
            {
                return false;
            }
            for(Integer art : arts)
            {
                if(art != null && art != 0)
                {
                    return true;
                }
            }
            return false;
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
        /**
         * Forwards to the game log by DEFAULT rather than requiring every
         * caller to remember, because the caller that forgets is the one whose
         * duel is going wrong. A silent sink here cost four rounds of reading
         * the wrong half of the codebase: the core was reporting "unknown card
         * code 40706444" on every duel while an Xyz monster was being rerouted
         * into the main deck, and nothing was listening.
         */
        private OcgDuel.LogSink log = HeadlessDuelRunner::logFromCore;
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
     * Whether a directory actually holds CARD scripts.
     *
     * <h2>Why this is not "does constant.lua exist"</h2>
     *
     * Because that was the test, in two places, and it is the wrong question.
     * {@code constant.lua} is one of about thirty SHARED files -- constants,
     * utility functions, procedures -- and an EDOPro install can have every one
     * of them and not a single {@code c<passcode>.lua}. One does: 31 files in
     * {@code script/}, no {@code official/}, and the card scripts kept
     * somewhere else entirely.
     * <p>
     * On that install everything looked right and nothing worked. The bundle
     * asked "are the scripts supplied?", saw {@code constant.lua}, answered yes
     * and unpacked none of its own; the path resolver asked "does the directory
     * exist?", saw that it did, and pointed the engine at it. Then every card in
     * the duel failed to load, one {@code Script not found} per card, and the
     * duel ran with no cards that do anything.
     * <p>
     * So the question is asked the way the READER answers it: is there a card
     * script here, at the root or under {@code official/}, by the same rule
     * {@link #cardScriptsDirectories} uses to find one. Stops at the first hit,
     * so it costs one directory entry rather than a walk of twelve thousand.
     */
    public static boolean hasCardScripts(Path root)
    {
        if(root == null)
        {
            return false;
        }
        for(Path directory : new Path[] {root, root.resolve("official")})
        {
            if(!Files.isDirectory(directory))
            {
                continue;
            }
            try(java.util.stream.Stream<Path> entries = Files.list(directory))
            {
                if(entries.anyMatch(HeadlessDuelRunner::isCardScript))
                {
                    return true;
                }
            }
            catch(Exception unreadable)
            {
                // An unreadable directory holds nothing this can use, which is
                // the same answer as an empty one.
            }
        }
        return false;
    }

    /** {@code c} then digits then {@code .lua} -- a card script and not a shared one. */
    private static boolean isCardScript(Path file)
    {
        String name = file.getFileName().toString();
        if(!name.startsWith("c") || !name.endsWith(".lua") || name.length() < 6)
        {
            return false;
        }
        for(int i = 1; i < name.length() - 4; i++)
        {
            if(!Character.isDigit(name.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * {@link OcgDuel.ScriptProvider} over a ProjectIgnis/CardScripts checkout:
     * constant.lua/utility.lua at the root, card scripts under official/.
     */
    public static OcgDuel.ScriptProvider cardScriptsDirectory(Path root)
    {
        return cardScriptsDirectories(List.of(root));
    }

    /**
     * The same, over several checkouts searched in order. EDOPro keeps the
     * scripts printed since its installer was cut in a separate repository, so
     * the current one has to be searched BEFORE the base install or a stale
     * copy of a rewritten card shadows its errata.
     */
    public static OcgDuel.ScriptProvider cardScriptsDirectories(List<Path> roots)
    {
        return name ->
        {
            for(Path root : roots)
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

            @Override
            public List<de.cas_ual_ty.dueldimension.ocg.query.CardView> ownDeck()
            {
                OcgStructs.OcgQueryInfo info = new OcgStructs.OcgQueryInfo();
                // Two fields, because the panel draws two things. See DECK_FLAGS.
                info.flags = de.cas_ual_ty.dueldimension.ocg.query.QueryParser.DECK_FLAGS;
                // `player`, closed over from observerFor's argument, never a
                // parameter of this method: an observer belongs to one seat and
                // cannot be talked into fetching the other's deck.
                info.con = (byte)player;
                info.loc = OcgConstants.LOCATION_DECK;
                info.seq = 0;
                info.overlay_seq = 0;

                List<de.cas_ual_ty.dueldimension.ocg.query.CardView> cards =
                    new ArrayList<>(de.cas_ual_ty.dueldimension.ocg.query.QueryParser.parseLocation(
                        duel.queryLocation(info)));
                // A pile has no empty slots, but parseLocation's contract allows
                // a null and the comparator below would not survive one.
                cards.removeIf(java.util.Objects::isNull);
                // The order dies HERE, in the frame that asked for it, not later
                // on the way out. The query is bottom-of-deck first (ocgapi.cpp
                // walks list_main front to back) and the draw takes
                // list_main.back(), so `cards`'s last element is the very next
                // card this player will draw. Sorting it before it can be
                // returned means no true-ordered copy of a deck ever exists for
                // a future refactor to forget to shuffle.
                cards.sort(java.util.Comparator
                    .comparingInt(de.cas_ual_ty.dueldimension.ocg.query.CardView::code)
                    .thenComparingInt(de.cas_ual_ty.dueldimension.ocg.query.CardView::art));
                return List.copyOf(cards);
            }

            @Override
            public int coverOf(int controller, int location, int sequence, int expectedCode)
            {
                // Nothing to ask about, or nothing that can be asked about with
                // the three numbers a prompt carries. An overlay unit is
                // addressed by its host's sequence PLUS an overlay_seq that no
                // option has, and MSG_SELECT_CARD's second layout (playerop.cpp
                // select_cards_codes) writes loc_info{ playerid, 0, 0, 0 } --
                // a real controller with a zeroed location and sequence, which
                // would otherwise become a query for "card 0 of location 0".
                // ocgapi.cpp rejects a multi-bit location too, but the guard is
                // ours to make rather than the native side's to be trusted with
                // -- and the controller is not guarded there at all:
                // field::get_field_card indexes player[playerid] straight into
                // a two-element array, so a -1 that arrived as an option's
                // "unknown controller" would be an out-of-bounds read inside
                // the core.
                if(expectedCode == 0 || sequence < 0 || controller < 0 || controller > 1
                    || location == 0 || (location & OcgConstants.LOCATION_OVERLAY) != 0
                    || Integer.bitCount(location) != 1)
                {
                    return 0;
                }

                OcgStructs.OcgQueryInfo info = new OcgStructs.OcgQueryInfo();
                // The deck-view flag pair, and for the same reason: a passcode
                // and an artwork are the whole question. See DECK_FLAGS on why
                // asking for more near a deck is a rule, not a preference.
                info.flags = de.cas_ual_ty.dueldimension.ocg.query.QueryParser.DECK_FLAGS;
                info.con = (byte)controller;
                info.loc = location;
                info.seq = sequence;
                info.overlay_seq = 0;

                // OCG_DuelQuery, the SINGLE-card call: it resolves one index of
                // one pile and answers nothing about its neighbours, so a deck
                // selection cannot be turned into a look at the deck. The
                // location call would return the pile in draw order.
                de.cas_ual_ty.dueldimension.ocg.query.CardView card =
                    de.cas_ual_ty.dueldimension.ocg.query.QueryParser.parseSingle(duel.query(info));
                if(card == null || card.code() != expectedCode)
                {
                    // Moved, or never there. The core writes pcard->data.code
                    // into the message and QUERY_CODE reads back the same field,
                    // so this comparison is exact.
                    return 0;
                }

                // The same test BoardState.conceal makes, in the one place that
                // knows the seat: `player` is closed over from observerFor's
                // argument, never the `controller` parameter. Your own card you
                // may identify; anyone's card the core calls public you may
                // identify; an opponent's set backrow or deck card you may not,
                // and 0 is the printed artwork, which tells nothing.
                return controller == player || card.isPublic() ? card.art() : 0;
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
            // The one gate. A deck nobody dressed takes the branch it always
            // took: no parallel list, no map, no query, no Lua.
            boolean dressed = deck.hasAlternateArt();

            // The shuffle is applied to an INDEX PERMUTATION rather than to the
            // cards, so the artwork each copy wears is carried by the same
            // movement instead of by a second shuffle that would have to match
            // -- which it would not, and the mismatch would be invisible
            // because the deck is shuffled anyway. Collections.shuffle makes
            // the same swaps for a list of any element type, so the order this
            // produces is the order the old code produced, seed for seed, and
            // no existing replay or test shifts.
            List<Integer> order = new ArrayList<>(deck.main().size());
            for(int i = 0; i < deck.main().size(); i++)
            {
                order.add(i);
            }
            if(!pseudoShuffle)
            {
                // A per-player stream so one player's deck size cannot shift the other's order.
                java.util.Collections.shuffle(order, shuffleRandom(config.seed, player));
            }

            List<Integer> main = new ArrayList<>(order.size());
            List<Integer> mainArts = dressed ? new ArrayList<>(order.size()) : null;
            for(int index : order)
            {
                main.add(deck.main().get(index));
                if(dressed)
                {
                    mainArts.add(artAt(deck.mainArts(), index));
                }
            }

            placeGuaranteed(main, deck.guaranteed(), mainArts);

            for(int i = main.size() - 1; i >= 0; i--)
            {
                duel.newCard(player, 0, main.get(i), player, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }

            List<Integer> extra = deck.extra();
            for(int i = extra.size() - 1; i >= 0; i--)
            {
                duel.newCard(player, 0, extra.get(i), player, OcgConstants.LOCATION_EXTRA, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }

            if(dressed)
            {
                dress(duel, player, main, mainArts, extra, deck.extraArts());
            }
        }
    }

    /** The artwork at one position of a list that is allowed to be short or absent. */
    private static int artAt(List<Integer> arts, int index)
    {
        if(arts == null || index < 0 || index >= arts.size())
        {
            return 0;
        }
        Integer art = arts.get(index);
        return art == null ? 0 : art;
    }

    /**
     * Attaches each copy's chosen artwork to the engine's own card objects,
     * immediately after registration and before the duel starts.
     * <p>
     * This is the only moment it can be done. The shuffled order exists here
     * and nowhere else — it is a local list that is discarded when this method
     * returns — and once {@code OCG_StartDuel} has run, nothing the engine
     * reports can tell two copies of one card apart again.
     * <p>
     * Registration runs back to front and {@code newCard(..., sequence 0, ...)}
     * means "deck top", which the core implements as {@code push_back}
     * (field.cpp). So the card registered last sits at the back of
     * {@code list_main}, which is the top of the deck, and core sequence
     * {@code s} holds list index {@code size - 1 - s}. The extra deck is the
     * same: {@code push_back} then {@code reset_sequence}, with
     * {@code extra_p_count} zero because every card is registered face down.
     * <p>
     * That mapping is <em>checked</em> rather than trusted, because
     * {@code field::add_card} silently redirects an extra-deck monster found
     * in a main deck into the extra deck, which would shift every sequence
     * after it. If the check fails the pile is simply left undressed and every
     * copy in it wears its printed artwork — an honest fallback, where a
     * confident wrong artwork would not be.
     */
    private static void dress(OcgDuel duel, int player, List<Integer> main, List<Integer> mainArts,
        List<Integer> extra, List<Integer> extraArts)
    {
        Map<Integer, Integer> deckBySequence = bySequence(duel, player, OcgConstants.LOCATION_DECK, main,
            index -> artAt(mainArts, index));
        Map<Integer, Integer> extraBySequence = bySequence(duel, player, OcgConstants.LOCATION_EXTRA, extra,
            index -> artAt(extraArts, index));

        String chunk = AltArtScript.chunk(player, deckBySequence, extraBySequence);
        if(chunk != null)
        {
            duel.loadScript("dd_altart_" + player + ".lua", chunk.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Core sequence to artwork for one pile, or an empty map if the core did
     * not lay the pile out where registration expects.
     */
    private static Map<Integer, Integer> bySequence(OcgDuel duel, int player, int location,
        List<Integer> registered, java.util.function.IntUnaryOperator artOf)
    {
        Map<Integer, Integer> bySequence = new java.util.LinkedHashMap<>();
        if(registered.isEmpty())
        {
            return bySequence;
        }

        OcgStructs.OcgQueryInfo info = new OcgStructs.OcgQueryInfo();
        info.flags = OcgConstants.QUERY_CODE;
        info.con = (byte)player;
        info.loc = location;
        info.seq = 0;
        info.overlay_seq = 0;
        List<de.cas_ual_ty.dueldimension.ocg.query.CardView> placed =
            de.cas_ual_ty.dueldimension.ocg.query.QueryParser.parseLocation(duel.queryLocation(info));

        if(placed.size() != registered.size())
        {
            return Map.of();
        }
        for(int sequence = 0; sequence < placed.size(); sequence++)
        {
            int index = registered.size() - 1 - sequence;
            de.cas_ual_ty.dueldimension.ocg.query.CardView card = placed.get(sequence);
            if(card == null || card.code() != registered.get(index))
            {
                return Map.of();   // not where we put it: leave the whole pile undressed
            }
            int art = artOf.applyAsInt(index);
            if(art != 0)
            {
                bySequence.put(sequence, art);
            }
        }
        return bySequence;
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
    /**
     * The core's own log, named by kind so an engine complaint is not mistaken
     * for one of ours. Script output is the loudest and least urgent of the
     * four, so it goes to debug; everything else is worth seeing.
     */
    private static void logFromCore(String message, int type)
    {
        String kind = switch(type)
        {
            case OcgConstants.LOG_TYPE_ERROR -> "error";
            case OcgConstants.LOG_TYPE_FROM_SCRIPT -> "script";
            case OcgConstants.LOG_TYPE_FOR_DEBUG -> "debug";
            default -> "core";
        };
        String line = "ocgcore [" + kind + "]: " + message;
        if(type == OcgConstants.LOG_TYPE_FROM_SCRIPT || type == OcgConstants.LOG_TYPE_FOR_DEBUG)
        {
            de.cas_ual_ty.dueldimension.util.DdLog.debug(line);
        }
        else
        {
            de.cas_ual_ty.dueldimension.util.DdLog.log(line);
        }
    }

    static void placeGuaranteed(List<Integer> main, int passcode)
    {
        placeGuaranteed(main, passcode, null);
    }

    /**
     * @param arts the artwork beside each card, moved with it. Null when
     *             nothing in this deck was dressed, which is the usual case.
     *             It is passed in rather than handled by the caller so the
     *             "where does it land" arithmetic exists once: a card and its
     *             artwork that disagree about that would put every chosen
     *             artwork one place out.
     */
    static void placeGuaranteed(List<Integer> main, int passcode, List<Integer> arts)
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
        int to = Math.min(OPENING_SLOT, main.size());
        main.add(to, passcode);
        if(arts != null && at < arts.size())
        {
            arts.add(Math.min(to, arts.size()), arts.remove(at));
        }
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
                    // Told to the ONE responder whose answer was refused, which
                    // is whoever the still-standing question was asked of. Told
                    // to both, seat 1's illegal click titled seat 0's next
                    // prompt "Not allowed: ..." -- a rejection for a move that
                    // player never made.
                    if(lastMessage != null)
                    {
                        config.responders[lastMessage.promptedPlayer() & 1].onAnswerRejected();
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
