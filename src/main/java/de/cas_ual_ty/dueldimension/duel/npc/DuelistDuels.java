package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.Duelists;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.ExecutorBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent;
import de.cas_ual_ty.dueldimension.ocg.prompt.HumanResponseSource;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptTranslator;
import de.cas_ual_ty.dueldimension.ocg.session.DuelSession;
import de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges NPC duelists to the rules engine.
 * <p>
 * This is the interim step before the duel GUI exists: challenging a duelist
 * runs a real, fully rules-enforced duel between two bots — the NPC's deck
 * against the challenger's chosen starter deck — and narrates it into chat.
 * It exercises the entire server-side path (engine thread, sessions, event
 * pumping, text) with no screens involved, so the GUI later only has to
 * replace the narration.
 */
public final class DuelistDuels
{
    /** Who is watching a duel: a player, or the server console. */
    public record Watcher(UUID playerId, boolean console)
    {
        public static Watcher of(ServerPlayer player)
        {
            return new Watcher(player.getUUID(), false);
        }

        public static Watcher forConsole()
        {
            return new Watcher(new UUID(0, 0), true);
        }

        void send(net.minecraft.server.MinecraftServer server, Component line)
        {
            if(console)
            {
                org.apache.logging.log4j.LogManager.getLogger().info("[duel] {}", line.getString());
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if(player != null)
            {
                player.sendSystemMessage(line);
            }
        }
    }

    /** Sessions keyed by watcher, so one watcher runs one duel at a time. */
    /**
     * One running duel and who is sitting at it.
     * <p>
     * A duel used to be keyed by its single watcher, which worked while the
     * only human was always seat 0. With two players the SESSION is the thing
     * that exists once and the watchers are two views onto it, so the drain
     * has to happen once and then be told twice, differently.
     */
    static final class RunningDuel
    {
        final DuelSession session;
        /** Indexed by the core's seat number; null where a bot sits. */
        final Watcher[] seats;
        /**
         * The match this duel is one game of, or null for a one-off.
         * <p>
         * The machine was created per invitation and then forgotten the moment
         * the duel started, so nothing carried a match past its first game.
         * Holding it here is what lets the end of a duel be the start of the
         * next one.
         */
        de.cas_ual_ty.dueldimension.duel.match.MatchStateMachine machine;
        de.cas_ual_ty.dueldimension.duel.match.MatchConfig config;
        /** Games won, by seat. */
        final int[] wins = new int[2];
        int gameNumber = 1;

        RunningDuel(DuelSession session, Watcher seat0, Watcher seat1)
        {
            this.session = session;
            this.seats = new Watcher[] {seat0, seat1};
        }

        boolean isMatch()
        {
            return machine != null && config != null && config.format().maxDuels() > 1;
        }

        boolean isTwoPlayer()
        {
            return seats[0] != null && seats[1] != null;
        }
    }

    /**
     * Every watcher's duel. Both seats of a two-player duel map to the SAME
     * RunningDuel, so the tick must de-duplicate by identity before draining.
     */
    private static final Map<Watcher, RunningDuel> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Set by the -Pselftest run flag: play one duel on a freshly started
     * server, report it, then shut the server down. This is how the whole
     * in-game path gets verified without a human clicking anything.
     */
    private static volatile boolean selfTestMode;

    /** The seat each player is currently answering prompts for. */
    private static final Map<UUID, HumanResponseSource> SEATS = new ConcurrentHashMap<>();

    private DuelistDuels()
    {
    }

    public static void challenge(DuelistEntity duelist, Player player)
    {
        if(!(player instanceof ServerPlayer serverPlayer))
        {
            return;
        }

        RunningDuel existingDuel = ACTIVE.get(Watcher.of(serverPlayer));
        DuelSession existing = existingDuel == null ? null : existingDuel.session;
        if(existing != null && existing.isRunning())
        {
            serverPlayer.sendSystemMessage(Component.literal("A duel is already running.")
                .withStyle(ChatFormatting.RED));
            return;
        }

        EngineRuntime.Paths paths = EngineRuntime.Paths.defaults();
        String missing = paths.missing();
        if(missing != null)
        {
            serverPlayer.sendSystemMessage(Component.literal("Ruled duels unavailable: " + missing)
                .withStyle(ChatFormatting.RED));
            return;
        }

        EngineRuntime engine = EngineRuntime.get(paths);
        if(engine == null)
        {
            serverPlayer.sendSystemMessage(Component.literal("Ruled duels unavailable.")
                .withStyle(ChatFormatting.RED));
            return;
        }

        StarterDecks.Entry npcDeck = StarterDecks.byId(duelist.getProfileId());
        // The challenger plays their own chosen deck. The fallback, for a
        // player who has not chosen one or whose choice will not do, is a
        // starter deck that is not the one the NPC is already using.
        ChosenDeck playerDeck = deckFor(serverPlayer,
            npcDeck == StarterDecks.YUGI ? StarterDecks.KAIBA : StarterDecks.YUGI,
            de.cas_ual_ty.dueldimension.duel.match.Banlist.none());

        long seed = serverPlayer.level.getGameTime() ^ serverPlayer.getUUID().getLeastSignificantBits();
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};

        HeadlessDuelRunner.Deck deck0 = playerDeck.cards();
        HeadlessDuelRunner.Deck deck1 = npcDeck.load().toRunnerDeck();

        // The challenger plays seat 0 themselves; the NPC plays seat 1.
        // The prompt callback runs on the duel thread. It used to channel.send
        // directly from there, racing the DuelUpdate stream sent by the server
        // tick -- a prompt could reach the client before the events of the turn
        // it concluded. It now posts into the session's own queue, so the drain
        // sends everything, prompts included, in stream order from one thread.
        PromptTranslator translator = new PromptTranslator(engine.cards(), engine.descriptions());
        DuelSession[] sessionHolder = new DuelSession[1];
        HumanResponseSource human = new HumanResponseSource(translator, (prompt, seat) ->
        {
            DuelSession running = sessionHolder[0];
            if(running != null)
            {
                running.postPrompt(prompt, seat.pendingSerial(), 0);
            }
        });
        SEATS.put(serverPlayer.getUUID(), human);

        DuelSession session = DuelSession.create(
            "npc-" + serverPlayer.getGameProfile().getName(),
            engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(), deck0, deck1,
            human,
            // The NPC plays with its OWN duelist executor, chosen by profile,
            // so Joey and Kaiba differ in what they will do and in what order.
            new ExecutorBot(seed * 2 + 1, Duelists.forProfile(duelist.getProfileId()),
                engine.cards(), engine.cards().all()));

        sessionHolder[0] = session;
        ACTIVE.put(Watcher.of(serverPlayer), new RunningDuel(session, Watcher.of(serverPlayer), null));

        serverPlayer.sendSystemMessage(Component.literal("Duel started: ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(playerDeck.displayName()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" vs "))
            .append(Component.literal(npcDeck.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        serverPlayer.sendSystemMessage(Component.literal("You are playing; prompts will open as the duel needs them.")
            .withStyle(ChatFormatting.DARK_GRAY));

        // Tell the client which cards it will need art for. Only the player's
        // own deck: the opponent's list is hidden information, and their cards
        // are fetched as they hit the field.
        int[] warmUp = deck0.main().stream().mapToInt(Integer::intValue).distinct().toArray();
        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
            new PromptMessages.DuelUpdate(null, List.of(), false, "", warmUp));

        session.start();
    }

    /**
     * Starts a duel between two players, each answering their own prompts.
     *
     * @return null on success, else why it could not start
     */
    public static String startPlayerDuel(ServerPlayer first, ServerPlayer second)
    {
        return startPlayerDuel(first, second,
            de.cas_ual_ty.dueldimension.duel.match.MatchConfig.DEFAULT);
    }

    public static String startPlayerDuel(ServerPlayer first, ServerPlayer second,
        de.cas_ual_ty.dueldimension.duel.match.MatchConfig config)
    {
        for(ServerPlayer player : new ServerPlayer[] {first, second})
        {
            RunningDuel busy = ACTIVE.get(Watcher.of(player));
            if(busy != null && busy.session.isRunning())
            {
                return player.getGameProfile().getName() + " is already duelling";
            }
        }

        EngineRuntime.Paths paths = EngineRuntime.Paths.defaults();
        String missing = paths.missing();
        if(missing != null)
        {
            return "Ruled duels unavailable: " + missing;
        }
        EngineRuntime engine = EngineRuntime.get(paths);
        if(engine == null)
        {
            return "Ruled duels unavailable";
        }

        // Each player brings their own deck. The fallbacks differ so that two
        // players who have both chosen nothing still get a duel rather than a
        // mirror match neither asked for.
        de.cas_ual_ty.dueldimension.duel.match.Banlist banlist =
            de.cas_ual_ty.dueldimension.duel.match.Banlists.byId(config.banlistId());
        ChosenDeck deckA = deckFor(first, StarterDecks.YUGI, banlist);
        ChosenDeck deckB = deckFor(second, StarterDecks.KAIBA, banlist);
        HeadlessDuelRunner.Deck deck0 = deckA.cards();
        HeadlessDuelRunner.Deck deck1 = deckB.cards();

        long seed = first.level.getGameTime()
            ^ first.getUUID().getLeastSignificantBits()
            ^ second.getUUID().getMostSignificantBits();
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};

        PromptTranslator translator = new PromptTranslator(engine.cards(), engine.descriptions());
        DuelSession[] sessionHolder = new DuelSession[1];

        // One responder per player, each posting its prompts under its OWN
        // seat number. The seat is what the drain uses to decide who a
        // question is for, so getting it wrong here would send both players
        // the same prompts and let either answer for the other.
        HumanResponseSource seat0 = new HumanResponseSource(translator, (prompt, seat) ->
        {
            DuelSession running = sessionHolder[0];
            if(running != null)
            {
                running.postPrompt(prompt, seat.pendingSerial(), 0);
            }
        });
        HumanResponseSource seat1 = new HumanResponseSource(translator, (prompt, seat) ->
        {
            DuelSession running = sessionHolder[0];
            if(running != null)
            {
                running.postPrompt(prompt, seat.pendingSerial(), 1);
            }
        });
        SEATS.put(first.getUUID(), seat0);
        SEATS.put(second.getUUID(), seat1);

        DuelSession session = DuelSession.create(
            "pvp-" + first.getGameProfile().getName() + "-" + second.getGameProfile().getName(),
            engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(), deck0, deck1, seat0, seat1);
        sessionHolder[0] = session;

        // Both watchers point at the SAME RunningDuel: the session exists once
        // and the tick de-duplicates by identity before draining it.
        RunningDuel duel = new RunningDuel(session, Watcher.of(first), Watcher.of(second));
        ACTIVE.put(Watcher.of(first), duel);
        ACTIVE.put(Watcher.of(second), duel);

        announceStart(first, second, deckA, deckB, deck0);
        announceStart(second, first, deckB, deckA, deck1);

        session.start();
        return null;
    }

    /** Tells one player the duel has begun, and warms up the art for their own deck. */
    private static void announceStart(ServerPlayer player, ServerPlayer opponent,
        ChosenDeck own, ChosenDeck theirs, HeadlessDuelRunner.Deck ownDeck)
    {
        player.sendSystemMessage(Component.literal("Duel started against ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(opponent.getGameProfile().getName())
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" - "))
            .append(Component.literal(own.displayName()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" vs "))
            .append(Component.literal(theirs.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE)));

        // Only this player's own list: the opponent's deck is hidden
        // information, and their cards are fetched as they reach the field.
        int[] warmUp = ownDeck.main().stream().mapToInt(Integer::intValue).distinct().toArray();
        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new PromptMessages.DuelUpdate(null, List.of(), false, "", warmUp));
    }

    /** Routes a client's answer to the seat that is waiting for it. */
    public static void submitAnswer(ServerPlayer player, HumanResponseSource.Answer answer)
    {
        HumanResponseSource seat = SEATS.get(player.getUUID());
        if(seat != null)
        {
            seat.submit(answer);
        }
    }

    /** Each player's chosen mat id, so a duel can report it to the other seat. */
    private static final Map<UUID, String> MATS = new ConcurrentHashMap<>();

    /**
     * Remembers the mat this player brought. In a duel against an NPC there is
     * nobody to tell; against another player this is what their client draws on
     * the far half of the table.
     */
    public static void setPlayMat(ServerPlayer player, String matId)
    {
        MATS.put(player.getUUID(), matId);
    }

    /** The mat a player brought, defaulting to the classic one. */
    public static String playMatOf(UUID playerId)
    {
        return MATS.getOrDefault(playerId, "classic");
    }

    /** Applies a chain-response policy to the player's seat. */
    public static void setChainPreference(ServerPlayer player,
        de.cas_ual_ty.dueldimension.ocg.prompt.ChainPreference preference)
    {
        HumanResponseSource seat = SEATS.get(player.getUUID());
        if(seat != null)
        {
            seat.setChainPreference(preference);
        }
    }

    /** Concedes the player's running duel. */
    public static void surrender(ServerPlayer player)
    {
        RunningDuel duel = ACTIVE.get(Watcher.of(player));
        if(duel != null && duel.session.isRunning())
        {
            duel.session.stop();
            player.sendSystemMessage(Component.literal("You surrendered.").withStyle(ChatFormatting.RED));
            // The other seat is told, because a duel that simply stops looks
            // like a crash from the far side of the table.
            for(Watcher other : duel.seats)
            {
                if(other != null && !other.equals(Watcher.of(player)) && !other.console())
                {
                    ServerPlayer opponent = player.server.getPlayerList().getPlayer(other.playerId());
                    if(opponent != null)
                    {
                        opponent.sendSystemMessage(Component.literal("Your opponent surrendered.")
                            .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
        }
    }

    /**
     * The deck a player duels with, and what it is called.
     * <p>
     * Their chosen deck if they have one that is fit to play, and the fallback
     * otherwise. Legality is checked here rather than trusted from the client
     * that offered it: this is the moment a deck stops being a thing being
     * built and becomes a thing being played with, and it is the last point at
     * which a deck of forty Blue-Eyes can be turned away.
     */
    private record ChosenDeck(HeadlessDuelRunner.Deck cards, String displayName)
    {
    }

    private static ChosenDeck deckFor(ServerPlayer player, StarterDecks.Entry fallback,
        de.cas_ual_ty.dueldimension.duel.match.Banlist banlist)
    {
        de.cas_ual_ty.dueldimension.duel.profile.DuelProfile profile =
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.get(player);
        String active = profile.activeDeck();
        if(!active.isEmpty())
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckList chosen = profile.deckNamed(active);
            // Judged against the list this duel is being played under, which
            // is the whole reason the lobby chose one.
            java.util.List<String> problems = chosen == null ? java.util.List.of()
                : de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.problemsUnder(chosen,
                    profile.trunk(), banlist);
            if(chosen != null && problems.isEmpty())
            {
                return new ChosenDeck(
                    new HeadlessDuelRunner.Deck(chosen.main(), chosen.extra()), chosen.name());
            }
            if(chosen != null)
            {
                player.sendSystemMessage(Component.literal("\"" + active
                        + "\" cannot be duelled with (" + problems.get(0) + "); using "
                        + fallback.displayName() + ".").withStyle(ChatFormatting.YELLOW));
            }
        }
        return new ChosenDeck(fallback.load().toRunnerDeck(), fallback.displayName());
    }

    /**
     * Ends the duel of a player who has left, and frees their seat.
     * <p>
     * A duel waits on its players: the session thread is blocked on a prompt
     * that a disconnected player will never answer, and the other seat would
     * sit there indefinitely. This is not a surrender — nobody chose it — but
     * the outcome has to be the same, because the alternative is a duel that
     * never ends and a thread that never stops.
     * <p>
     * The seat and the active entry are dropped here rather than left to the
     * tick, which recognises a duel that <em>finished</em>; this one was
     * abandoned, and the player it belonged to is already gone.
     */
    public static void abandon(ServerPlayer player)
    {
        Watcher watcher = Watcher.of(player);
        RunningDuel duel = ACTIVE.remove(watcher);
        SEATS.remove(player.getUUID());
        if(duel == null)
        {
            return;
        }

        duel.session.stop();
        for(Watcher other : duel.seats)
        {
            if(other == null || other.equals(watcher) || other.console())
            {
                continue;
            }
            // The far seat is released too: their duel is over whether or not
            // they know it yet, and leaving them ACTIVE would refuse them a
            // new one forever.
            ACTIVE.remove(other);
            SEATS.remove(other.playerId());
            ServerPlayer opponent = player.server.getPlayerList().getPlayer(other.playerId());
            if(opponent != null)
            {
                opponent.sendSystemMessage(Component.literal("Your opponent disconnected; the duel is over.")
                    .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /**
     * Starts a duel between two starter decks with no player involved, for the
     * server console and for verifying the whole path headlessly.
     *
     * @return null on success, else why it could not start
     */
    public static String startConsoleDuel(String deckA, String deckB, long seed)
    {
        EngineRuntime.Paths paths = EngineRuntime.Paths.defaults();
        String missing = paths.missing();
        if(missing != null)
        {
            return missing;
        }
        EngineRuntime engine = EngineRuntime.get(paths);
        if(engine == null)
        {
            return "engine unavailable";
        }
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};
        DuelSession session = DuelSession.create("console", engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(),
            StarterDecks.byId(deckA).load().toRunnerDeck(),
            StarterDecks.byId(deckB).load().toRunnerDeck(),
            new ExecutorBot(seed, Duelists.forProfile(deckA), engine.cards(), engine.cards().all()),
            new ExecutorBot(seed * 2 + 1, Duelists.forProfile(deckB), engine.cards(), engine.cards().all()));
        ACTIVE.put(Watcher.forConsole(), new RunningDuel(session, Watcher.forConsole(), null));
        session.start();
        return null;
    }

    /**
     * Called every server tick: drains what the duel threads produced and
     * narrates it. Engine threads never touch the game state themselves.
     */
    /** Starts the self-test duel if this server was launched with the flag. */
    public static void maybeStartSelfTest(net.minecraft.server.MinecraftServer server)
    {
        if(!Boolean.getBoolean("dueldimension.selftest"))
        {
            return;
        }
        selfTestMode = true;
        org.apache.logging.log4j.LogManager.getLogger().info("[selftest] starting Yugi vs Kaiba");
        String error = startConsoleDuel("yugi", "kaiba", 20260804L);
        if(error != null)
        {
            org.apache.logging.log4j.LogManager.getLogger().error("[selftest] FAILED to start: {}", error);
            server.halt(false);
        }
    }

    public static void tick(net.minecraft.server.MinecraftServer server)
    {
        if(ACTIVE.isEmpty())
        {
            return;
        }
        DescriptionTable descriptions = EngineRuntime.isLoaded()
            ? EngineRuntime.get(EngineRuntime.Paths.defaults()).descriptions()
            : new DescriptionTable();

        // Both seats of a two-player duel map to the same RunningDuel, and the
        // event queue may be drained exactly once, so de-duplicate by identity
        // before touching any of them.
        java.util.Set<RunningDuel> duels =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        duels.addAll(ACTIVE.values());

        List<Watcher> finished = new ArrayList<>();
        for(RunningDuel duel : duels)
        {
            DuelSession session = duel.session;

            // Everything below is accumulated PER SEAT. The events differ
            // between seats because identity and side are both relative, so a
            // single shared list would be either a leak or a mirrored board.
            @SuppressWarnings("unchecked")
            List<Object>[] outbound = new List[] {new ArrayList<>(), new ArrayList<>()};
            @SuppressWarnings("unchecked")
            List<DuelEvent>[] pending = new List[] {new ArrayList<>(), new ArrayList<>()};
            List<String> log = new ArrayList<>();
            boolean[] over = {false};
            int[] winner = {-2};
            String[] failure = {null};

            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Message message)
                {
                    for(int seat = 0; seat < 2; seat++)
                    {
                        if(duel.seats[seat] != null)
                        {
                            pending[seat].addAll(toDuelEvents(message.message(), seat));
                        }
                    }
                    Component line = narrate(message.message(), descriptions);
                    if(line != null)
                    {
                        log.add(line.getString());
                        for(Watcher watcher : duel.seats)
                        {
                            if(watcher != null && watcher.console())
                            {
                                watcher.send(server, line);
                            }
                        }
                    }
                }
                else if(event instanceof DuelSession.Event.Board board)
                {
                    for(int seat = 0; seat < 2; seat++)
                    {
                        if(duel.seats[seat] == null)
                        {
                            continue;
                        }
                        outbound[seat].add(new PromptMessages.DuelUpdate(board.forSeat(seat),
                            List.of(), false, "", new int[0], new ArrayList<>(pending[seat])));
                        pending[seat].clear();
                    }
                }
                else if(event instanceof DuelSession.Event.Prompt prompt)
                {
                    // A question belongs to one seat. Flush that seat's events
                    // first so the client animates what led up to it, then ask.
                    int seat = prompt.seat();
                    if(seat >= 0 && seat < 2 && duel.seats[seat] != null)
                    {
                        if(!pending[seat].isEmpty())
                        {
                            outbound[seat].add(new PromptMessages.DuelUpdate(null, List.of(), false, "",
                                new int[0], new ArrayList<>(pending[seat])));
                            pending[seat].clear();
                        }
                        outbound[seat].add(prompt);
                    }
                }
                else if(event instanceof DuelSession.Event.Finished done)
                {
                    over[0] = true;
                    winner[0] = done.completed() && done.result() != null ? done.result().winner() : -1;
                }
                else if(event instanceof DuelSession.Event.Failed failed)
                {
                    over[0] = true;
                    failure[0] = "Duel failed: " + failed.reason();
                }
            });

            for(int seat = 0; seat < 2; seat++)
            {
                Watcher watcher = duel.seats[seat];
                if(watcher == null)
                {
                    continue;
                }
                // Anything after the last checkpoint still has to be played.
                if(!pending[seat].isEmpty() || !log.isEmpty() || over[0])
                {
                    outbound[seat].add(new PromptMessages.DuelUpdate(null, List.of(), false, "",
                        new int[0], new ArrayList<>(pending[seat])));
                    pending[seat].clear();
                }

                // The result is stated from this seat's point of view: the same
                // duel is a win to one player and a loss to the other.
                String result = "";
                if(over[0])
                {
                    result = failure[0] != null ? failure[0]
                        : winner[0] == -1 ? "Duel ended without a result"
                        : winner[0] == seat ? "Winner: you"
                        : winner[0] == (1 - seat) ? "Winner: opponent" : "Winner: draw";
                }
                if(watcher.console())
                {
                    if(over[0])
                    {
                        watcher.send(server, Component.literal("Duel over - " + result)
                            .withStyle(ChatFormatting.GOLD));
                    }
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(watcher.playerId());
                if(player == null || outbound[seat].isEmpty())
                {
                    continue;
                }
                int lastUpdate = -1;
                for(int i = 0; i < outbound[seat].size(); i++)
                {
                    if(outbound[seat].get(i) instanceof PromptMessages.DuelUpdate)
                    {
                        lastUpdate = i;
                    }
                }
                for(int i = 0; i < outbound[seat].size(); i++)
                {
                    Object packet = outbound[seat].get(i);
                    if(packet instanceof PromptMessages.DuelUpdate update)
                    {
                        boolean last = i == lastUpdate;
                        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new PromptMessages.DuelUpdate(update.board(),
                                last ? log : List.of(), last && over[0], last ? result : "",
                                new int[0], update.events()));
                    }
                    else if(packet instanceof DuelSession.Event.Prompt prompt)
                    {
                        de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new PromptMessages.ShowPrompt(prompt.prompt(), prompt.serial()));
                    }
                }
            }

            if(!session.isRunning())
            {
                concludeGame(server, duel, winner[0]);
                for(Watcher watcher : duel.seats)
                {
                    if(watcher != null)
                    {
                        finished.add(watcher);
                        SEATS.remove(watcher.playerId());
                        if(selfTestMode && watcher.console())
                        {
                            org.apache.logging.log4j.LogManager.getLogger()
                                .info("[selftest] duel finished, stopping server");
                            server.halt(false);
                        }
                    }
                }
            }
        }
        finished.forEach(ACTIVE::remove);
    }

    /**
     * Scores a finished game and, in a match, starts the next one.
     * <p>
     * Called once per duel that ends. A one-off duel simply finishes; a match
     * tallies the win, tells both players the running score, and either
     * declares the match or plays the next game.
     * <p>
     * Side decking belongs between those games — INTERMISSION is the state for
     * it — and there is no screen for it yet, so the next game starts with the
     * same decks. That is stated here rather than left as a silent gap.
     */
    private static void concludeGame(net.minecraft.server.MinecraftServer server,
        RunningDuel duel, int winnerSeat)
    {
        if(duel.machine == null || duel.config == null)
        {
            return;
        }
        if(!duel.machine.tryMoveTo(de.cas_ual_ty.dueldimension.duel.match.MatchState.GAME_OVER))
        {
            return;
        }
        if(winnerSeat == 0 || winnerSeat == 1)
        {
            duel.wins[winnerSeat]++;
        }

        int needed = duel.config.format().winsNeeded();
        boolean decided = duel.wins[0] >= needed || duel.wins[1] >= needed;
        // A draw takes a game off the count without giving anyone a win, so a
        // match of draws still ends rather than running forever.
        boolean exhausted = duel.gameNumber >= duel.config.format().maxDuels();

        if(!duel.isMatch() || decided || exhausted)
        {
            duel.machine.finish(decided
                ? "match won " + duel.wins[0] + "-" + duel.wins[1]
                : "match ended " + duel.wins[0] + "-" + duel.wins[1]);
            if(duel.isMatch())
            {
                announceToBoth(server, duel, Component.literal("Match over  "
                    + duel.wins[0] + " - " + duel.wins[1]).withStyle(ChatFormatting.GOLD));
            }
            return;
        }

        duel.machine.moveTo(de.cas_ual_ty.dueldimension.duel.match.MatchState.INTERMISSION);
        announceToBoth(server, duel, Component.literal("Game " + duel.gameNumber + " over  "
            + duel.wins[0] + " - " + duel.wins[1] + "   starting game " + (duel.gameNumber + 1))
            .withStyle(ChatFormatting.GOLD));

        ServerPlayer first = server.getPlayerList().getPlayer(duel.seats[0].playerId());
        ServerPlayer second = server.getPlayerList().getPlayer(duel.seats[1].playerId());
        if(first == null || second == null)
        {
            duel.machine.cancel("a player left between games");
            return;
        }

        int[] carried = {duel.wins[0], duel.wins[1]};
        int nextGame = duel.gameNumber + 1;
        de.cas_ual_ty.dueldimension.duel.match.MatchStateMachine machine = duel.machine;
        de.cas_ual_ty.dueldimension.duel.match.MatchConfig config = duel.config;

        String error = startPlayerDuel(first, second);
        if(error != null)
        {
            machine.cancel(error);
            announceToBoth(server, duel, Component.literal(error).withStyle(ChatFormatting.RED));
            return;
        }
        // The new duel is a fresh RunningDuel, so the match travels across by
        // hand: the score and the game number belong to the match, not to any
        // one game of it.
        RunningDuel next = ACTIVE.get(Watcher.of(first));
        if(next != null)
        {
            next.machine = machine;
            next.config = config;
            next.wins[0] = carried[0];
            next.wins[1] = carried[1];
            next.gameNumber = nextGame;
            machine.tryMoveTo(de.cas_ual_ty.dueldimension.duel.match.MatchState.DUELING);
        }
    }

    private static void announceToBoth(net.minecraft.server.MinecraftServer server,
        RunningDuel duel, Component line)
    {
        for(Watcher watcher : duel.seats)
        {
            if(watcher != null)
            {
                watcher.send(server, line);
            }
        }
    }

    /** Attaches a match to the duel these two players just started. */
    public static void attachMatch(ServerPlayer first,
        de.cas_ual_ty.dueldimension.duel.match.MatchStateMachine machine,
        de.cas_ual_ty.dueldimension.duel.match.MatchConfig config)
    {
        RunningDuel duel = ACTIVE.get(Watcher.of(first));
        if(duel != null)
        {
            duel.machine = machine;
            duel.config = config;
        }
    }

    /** Turns an engine message into a chat line, or null for the noisy ones. */
    private static Component narrate(RawMessage raw, DescriptionTable descriptions)
    {
        DuelMessage message = DuelMessage.decode(raw);

        if(message instanceof DuelMessage.PayLpCost cost)
        {
            return Component.literal("Player " + cost.player() + " pays " + cost.amount() + " LP")
                .withStyle(ChatFormatting.RED);
        }
        if(message instanceof DuelMessage.TossCoin coin)
        {
            StringBuilder faces = new StringBuilder();
            coin.results().forEach(r -> faces.append(faces.length() == 0 ? "" : ", ")
                .append(r == 1 ? "heads" : "tails"));
            return Component.literal("Coin toss: " + faces).withStyle(ChatFormatting.YELLOW);
        }
        if(message instanceof DuelMessage.TossDice dice)
        {
            StringBuilder faces = new StringBuilder();
            dice.results().forEach(r -> faces.append(faces.length() == 0 ? "" : ", ").append(r));
            return Component.literal("Dice roll: " + faces).withStyle(ChatFormatting.YELLOW);
        }
        if(message instanceof DuelMessage.Battle battle)
        {
            // The core's own damage-step numbers, so a surprising outcome can
            // be checked against the rules instead of argued about.
            String line = battle.target().location() == 0
                ? "Battle: " + battle.attackerAtk() + " ATK, direct"
                : "Battle: " + battle.attackerAtk() + " ATK vs "
                    + battle.targetAtk() + " ATK / " + battle.targetDef() + " DEF";
            return Component.literal(line).withStyle(ChatFormatting.GRAY);
        }
        if(message instanceof DuelMessage.Damage damage)
        {
            return Component.literal("Player " + damage.player() + " takes " + damage.amount() + " damage")
                .withStyle(ChatFormatting.RED);
        }
        if(message instanceof DuelMessage.Recover recover)
        {
            return Component.literal("Player " + recover.player() + " gains " + recover.amount() + " LP")
                .withStyle(ChatFormatting.GREEN);
        }
        if(message instanceof DuelMessage.Win win)
        {
            return Component.literal("WIN: player " + win.winner() + " (reason " + win.reason() + ")")
                .withStyle(ChatFormatting.GOLD);
        }
        if(message instanceof DuelMessage.Hint hint && hint.hintType() == DescriptionTable.OcgHints.SELECT_MESSAGE)
        {
            return Component.literal("  " + descriptions.describeHint(hint.hintType(), hint.description()))
                .withStyle(ChatFormatting.GRAY);
        }
        if(message instanceof DuelMessage.Draw draw)
        {
            return Component.literal("Player " + draw.player() + " draws " + draw.cards().size())
                .withStyle(ChatFormatting.GRAY);
        }
        // Turn and phase changes are deliberately absent: the phase bar and the
        // turn badge already show both, and narrating them filled the whole log
        // band with "Draw Phase / Standby Phase / Main Phase 1" every turn.
        return null; // per-card moves and chain bookkeeping would drown the log
    }

    /**
     * Turns an engine message into something the client can animate and play a
     * sound for. Seat 0 is the watching player, so controller 0 is "you".
     */
    /**
     * Turns an engine message into animation events AS ONE SEAT MAY SEE THEM.
     * <p>
     * Two things have to be made relative, not just the board. Identity,
     * because the tap reads the core's unfiltered stream and
     * {@code generic_duel.cpp} would have rewritten it per player. And the
     * controller byte, because the client draws controller 0 on the near side
     * of the table -- sending seat 1 the absolute numbering would play every
     * animation on the wrong half of the field.
     *
     * @param viewer the seat this stream is for, in the core's numbering
     */
    private static List<DuelEvent> toDuelEvents(RawMessage raw, int viewer)
    {
        DuelMessage message = DuelMessage.decode(raw);

        // An effect going onto the chain, and the cards it picked. Without
        // these the client had no way to show that anything was happening: an
        // effect resolved, the board simply changed, and a flip effect looked
        // like it had never activated at all.
        if(message instanceof DuelMessage.Chaining chaining)
        {
            return List.of(new DuelEvent(DuelEvent.Kind.CHAINING, chaining.code(),
                -1, zoneOf(chaining.card(), viewer), 0, side(chaining.card().controller(), viewer)));
        }
        // A reveal goes to the seat being shown it and to nobody else. This is
        // the one message whose entire purpose is to hand information to one
        // side, so sending it to both would leak exactly what it exists to
        // control.
        if(message instanceof DuelMessage.ConfirmCards confirm)
        {
            if(confirm.player() != viewer)
            {
                return List.of();
            }
            List<DuelEvent> revealed = new ArrayList<>();
            for(int i = 0; i < confirm.codes().size(); i++)
            {
                int code = confirm.codes().get(i);
                if(code == 0)
                {
                    // A face-down the core names as zero: there is nothing to
                    // show, and drawing a blank would read as a broken card.
                    continue;
                }
                de.cas_ual_ty.dueldimension.ocg.msg.CardLocation at = confirm.cards().get(i);
                revealed.add(new DuelEvent(DuelEvent.Kind.REVEAL, code, -1,
                    zoneOf(at, viewer), i, side(at.controller(), viewer)));
            }
            return revealed;
        }

        if(message instanceof DuelMessage.BecomeTarget target)
        {
            List<DuelEvent> events = new ArrayList<>();
            for(de.cas_ual_ty.dueldimension.ocg.msg.CardLocation location : target.targets())
            {
                events.add(new DuelEvent(DuelEvent.Kind.BECOME_TARGET, 0,
                    -1, zoneOf(location, viewer), 0, side(location.controller(), viewer)));
            }
            return events;
        }
        if(message instanceof DuelMessage.FlipSummoning flip)
        {
            // A flip summon always ends face up.
            return List.of(new DuelEvent(DuelEvent.Kind.FLIP, flip.code(),
                -1, zoneOf(flip.card(), viewer), 1, side(flip.card().controller(), viewer)));
        }
        // The announce messages carry the pause a summon has in the reference:
        // duelclient.cpp:3281 holds a card splash for 30 then 11 frames before
        // the MSG_MOVE slide. Without these events a summon was only its slide.
        if(message instanceof DuelMessage.Summoning summoning)
        {
            return List.of(new DuelEvent(DuelEvent.Kind.SUMMON, summoning.code(),
                -1, -1, 0, side(summoning.card().controller(), viewer)));
        }
        if(message instanceof DuelMessage.SpSummoning spSummoning)
        {
            return List.of(new DuelEvent(DuelEvent.Kind.SPECIAL_SUMMON, spSummoning.code(),
                -1, -1, 0, side(spSummoning.card().controller(), viewer)));
        }
        if(message instanceof DuelMessage.PositionChange position)
        {
            // Turning a card face down hides it again, so the same rule as a
            // set applies: the core names it to every seat, we do not.
            boolean nowHidden = (position.position()
                & de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN) != 0;
            int shown = nowHidden && position.controller() != viewer ? 0 : position.code();
            // `amount` carries which way the card is turning, so the animation
            // knows whether it ends on the face or the back.
            return List.of(new DuelEvent(DuelEvent.Kind.POSITION, shown, -1,
                DuelEvent.zoneOf(side(position.controller(), viewer), position.location(),
                    position.sequence(), 0),
                nowHidden ? 0 : 1, side(position.controller(), viewer)));
        }
        if(message instanceof DuelMessage.PayLpCost cost)
        {
            // duelclient.cpp:3690 plays the damage sound for a paid cost, so
            // it rides the same event as battle damage.
            return List.of(new DuelEvent(DuelEvent.Kind.DAMAGE, 0, -1, -1, cost.amount(),
                side(cost.player(), viewer)));
        }
        if(message instanceof DuelMessage.TossCoin coin)
        {
            // The results ride in `amount`, a bit per coin, so the client can
            // show what actually came up rather than inventing a result.
            int packed = 0;
            for(int i = 0; i < coin.results().size() && i < 30; i++)
            {
                packed |= (coin.results().get(i) & 1) << i;
            }
            return List.of(new DuelEvent(DuelEvent.Kind.COIN, coin.results().size(), -1, -1,
                packed, side(coin.player(), viewer)));
        }
        if(message instanceof DuelMessage.TossDice dice)
        {
            // Six bits a die: enough for 1-6 with room to spare.
            int packed = 0;
            for(int i = 0; i < dice.results().size() && i < 5; i++)
            {
                packed |= (dice.results().get(i) & 0x3F) << (i * 6);
            }
            return List.of(new DuelEvent(DuelEvent.Kind.DICE, dice.results().size(), -1, -1,
                packed, side(dice.player(), viewer)));
        }
        if(message instanceof DuelMessage.SetCard set)
        {
            // A set card's identity is hidden information; only its owner's
            // client may hear the code.
            return List.of(new DuelEvent(DuelEvent.Kind.SET,
                set.card().controller() == viewer ? set.code() : 0,
                -1, zoneOf(set.card(), viewer), 0, side(set.card().controller(), viewer)));
        }

        DuelEvent single = toDuelEvent(message, viewer);
        return single == null ? List.of() : List.of(single);
    }

    /** The core's controller byte as this viewer sees it: 0 is always their own side. */
    private static int side(int controller, int viewer)
    {
        return controller == viewer ? 0 : 1;
    }

    /** Packs a decoded location the way the animation layer expects, for one viewer. */
    private static int zoneOf(de.cas_ual_ty.dueldimension.ocg.msg.CardLocation location, int viewer)
    {
        return DuelEvent.zoneOf(side(location.controller(), viewer), location.location(),
            location.sequence(), 0);
    }

    private static DuelEvent toDuelEvent(DuelMessage message, int viewer)
    {

        if(message instanceof DuelMessage.Move move)
        {
            int from = DuelEvent.zoneOf(side(move.from().controller(), viewer),
                move.from().location(), move.from().sequence(), 0);
            int to = DuelEvent.zoneOf(side(move.to().controller(), viewer),
                move.to().location(), move.to().sequence(), 0);
            // A move is just the slide, as in the reference: the pageantry of a
            // summon or activation arrives separately (MSG_SUMMONING splash,
            // MSG_CHAINING marker), so classifying moves as summons here both
            // double-counted the pause and mislabelled ordinary shuffles.
            DuelEvent.Kind kind =
                move.to().location() == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE
                    ? DuelEvent.Kind.DESTROY : DuelEvent.Kind.MOVE;
            // generic_duel.cpp rewrites each message per player before sending
            // it, so a real client never receives the identity of a card it
            // cannot see. This tap reads the core's unfiltered stream, and
            // without the same concealment the SLIDE of an opponent's set card
            // briefly wore its true art -- the settled board was honest, the
            // animation was not.
            int shownCode = move.code();
            if(move.to().controller() != viewer
                && ((move.to().position() & de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN) != 0
                    || move.to().location() == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_HAND
                    || move.to().location() == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_DECK))
            {
                shownCode = 0;
            }
            return new DuelEvent(kind, shownCode, from, to, 0, side(move.to().controller(), viewer));
        }
        if(message instanceof DuelMessage.Attack attack)
        {
            // A direct attack has no target zone; the animation lunges at the
            // defending player's side of the table instead.
            int from = DuelEvent.zoneOf(side(attack.attacker().controller(), viewer),
                attack.attacker().location(), attack.attacker().sequence(), 0);
            int to = attack.isDirect() ? -1
                : DuelEvent.zoneOf(side(attack.target().controller(), viewer),
                    attack.target().location(), attack.target().sequence(), 0);
            return new DuelEvent(DuelEvent.Kind.ATTACK, 0, from, to, 0,
                side(attack.attacker().controller(), viewer));
        }
        if(message instanceof DuelMessage.Damage damage)
        {
            return new DuelEvent(DuelEvent.Kind.DAMAGE, 0, -1, -1, damage.amount(), side(damage.player(), viewer));
        }
        if(message instanceof DuelMessage.Recover recover)
        {
            return new DuelEvent(DuelEvent.Kind.RECOVER, 0, -1, -1, recover.amount(), side(recover.player(), viewer));
        }
        if(message instanceof DuelMessage.Draw draw)
        {
            return new DuelEvent(DuelEvent.Kind.DRAW, 0, -1, -1, draw.cards().size(), side(draw.player(), viewer));
        }
        if(message instanceof DuelMessage.ShuffleDeck shuffle)
        {
            return new DuelEvent(DuelEvent.Kind.SHUFFLE, 0, -1, -1, 0, side(shuffle.player(), viewer));
        }
        if(message instanceof DuelMessage.NewPhase phase)
        {
            return new DuelEvent(DuelEvent.Kind.PHASE, 0, -1, -1, phase.phase(), 0);
        }
        if(message instanceof DuelMessage.NewTurn turn)
        {
            return new DuelEvent(DuelEvent.Kind.NEW_TURN, 0, -1, -1, 0, side(turn.player(), viewer));
        }
        if(message instanceof DuelMessage.Win win)
        {
            return new DuelEvent(DuelEvent.Kind.WIN, 0, -1, -1, win.reason(), win.winner());
        }
        return null;
    }

    private static String phaseName(int phase)
    {
        return switch(phase)
        {
            case de.cas_ual_ty.dueldimension.ocg.OcgConstants.PHASE_DRAW -> "Draw Phase";
            case de.cas_ual_ty.dueldimension.ocg.OcgConstants.PHASE_STANDBY -> "Standby Phase";
            case de.cas_ual_ty.dueldimension.ocg.OcgConstants.PHASE_MAIN1 -> "Main Phase 1";
            case de.cas_ual_ty.dueldimension.ocg.OcgConstants.PHASE_MAIN2 -> "Main Phase 2";
            case de.cas_ual_ty.dueldimension.ocg.OcgConstants.PHASE_END -> "End Phase";
            default -> (phase & 0xF8) != 0 ? "Battle Phase" : "Phase " + phase;
        };
    }

    public static void stopAll()
    {
        ACTIVE.values().forEach(duel -> duel.session.stop());
        de.cas_ual_ty.dueldimension.duel.match.DuelInvites.clear();
        ACTIVE.clear();
    }
}
