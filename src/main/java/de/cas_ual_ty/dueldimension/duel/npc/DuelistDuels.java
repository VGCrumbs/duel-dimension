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
        final DuelRewardTracker rewards;
        final UUID rewardId = UUID.randomUUID();
        boolean paid;
        /**
         * Whether The Seal of Orichalcos was on the field at the last look.
         * <p>
         * Current state rather than ever-seen, so a Seal that gets destroyed
         * stops counting -- the rule is that the field spell is ACTIVE when the
         * duel is lost, not that it was played at some point.
         */
        boolean sealOnField;
        boolean forfeit;
        boolean concluded;
        /**
         * Each seat's own main deck, de-ordered on the duel thread and refreshed
         * at every board checkpoint.
         * <p>
         * Indexed by seat and read only by {@link #viewOwnDeck}, which resolves
         * the seat from the sending player. It is deliberately NOT attached to
         * {@code PromptMessages.DuelUpdate}: that is the per-seat broadcast, and
         * keeping the deck off it means no broadcast bug has a path to the wrong
         * seat.
         */
        @SuppressWarnings("unchecked")
        final List<de.cas_ual_ty.dueldimension.ocg.query.CardView>[] ownDeck =
            new List[] {List.of(), List.of()};
        /**
         * When each seat last asked to look at its deck, so a scripted client
         * cannot make the server serialise fifty cards a tick. Hygiene, not
         * concealment -- the answer is the player's own deck list either way.
         */
        final long[] lastDeckView = new long[2];
        /** A player who concedes this contest can never receive its DP packet. */
        final boolean[] rewardEligible = {true, true};
        /**
         * The duelist across the table, or null in a duel between two players.
         * <p>
         * Held as an id rather than the entity: a duel outlives a chunk unload,
         * and the seal has to be able to find whoever lost it afterwards.
         */
        UUID duelistId;

        RunningDuel(DuelSession session, Watcher seat0, Watcher seat1,
            de.cas_ual_ty.dueldimension.ocg.CdbCardProvider cards)
        {
            this.session = session;
            this.seats = new Watcher[] {seat0, seat1};
            this.rewards = new DuelRewardTracker(cards);
        }

        boolean isMatch()
        {
            return machine != null && config != null && config.format().maxDuels() > 1;
        }

        boolean isTwoPlayer()
        {
            return seats[0] != null && seats[1] != null;
        }

        void disqualifyReward(int seat)
        {
            if(seat >= 0 && seat < rewardEligible.length)
            {
                rewardEligible[seat] = false;
            }
        }

        boolean canReward(int seat)
        {
            return seat >= 0 && seat < rewardEligible.length && rewardEligible[seat];
        }

        void carryRewardEligibilityFrom(RunningDuel previous)
        {
            System.arraycopy(previous.rewardEligible, 0, rewardEligible, 0,
                rewardEligible.length);
        }

        boolean beginConclusion()
        {
            if(concluded)
            {
                return false;
            }
            concluded = true;
            return true;
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
            serverPlayer.sendSystemMessage(EngineRuntime.requirementMessage(missing,
                EngineRuntime.hostedElsewhere(serverPlayer)));
            return;
        }

        EngineRuntime engine = EngineRuntime.get(paths);
        if(engine == null)
        {
            // The engine was there and would not load -- a wrong build, most
            // likely. "Ruled duels unavailable" on its own left a player with
            // nothing to act on, so they get the same message as a missing
            // install, carrying the actual reason.
            serverPlayer.sendSystemMessage(EngineRuntime.requirementMessage(
                EngineRuntime.unavailable(paths), EngineRuntime.hostedElsewhere(serverPlayer)));
            return;
        }

        StarterDecks.Entry npcDeck = StarterDecks.byId(duelist.getProfileId());
        // The challenger plays their own chosen deck. The fallback, for a
        // player who has not chosen one or whose choice will not do, is a
        // starter deck that is not the one the NPC is already using.
        ChosenDeck playerDeck = deckFor(serverPlayer,
            npcDeck == StarterDecks.YUGI ? StarterDecks.KAIBA : StarterDecks.YUGI,
            de.cas_ual_ty.dueldimension.duel.match.Banlist.none());

        long seed = serverPlayer.level().getGameTime() ^ serverPlayer.getUUID().getLeastSignificantBits();
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};

        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(serverPlayer, playerDeck.cards());
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
            "npc-" + serverPlayer.getGameProfile().name(),
            engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(), deck0, deck1,
            human,
            // The NPC plays with its OWN duelist executor, chosen by profile,
            // so Joey and Kaiba differ in what they will do and in what order.
            new ExecutorBot(seed * 2 + 1, Duelists.forProfile(duelist.getProfileId()),
                engine.cards(), engine.cards().all()));

        sessionHolder[0] = session;
        RunningDuel npcDuel = new RunningDuel(session,
            Watcher.of(serverPlayer), null, engine.cards());
        npcDuel.duelistId = duelist.getUUID();
        ACTIVE.put(Watcher.of(serverPlayer), npcDuel);

        serverPlayer.sendSystemMessage(Component.literal("Duel started: ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(playerDeck.displayName()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" vs "))
            .append(Component.literal(npcDeck.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        serverPlayer.sendSystemMessage(Component.literal("You are playing; prompts will open as the duel needs them.")
            .withStyle(ChatFormatting.DARK_GRAY));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
            new PromptMessages.DuelNames(serverPlayer.getGameProfile().name(),
                duelist.displayName()));

        // Tell the client which cards it will need art for. Only the player's
        // own deck: the opponent's list is hidden information, and their cards
        // are fetched as they hit the field.
        int[] warmUp = deck0.main().stream().mapToInt(Integer::intValue).distinct().toArray();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, new PromptMessages.DuelUpdate(null, List.of(), false, "", warmUp));
        // The back this player's cards wear, decided here because this is where
        // the deck was decided -- see deckFor's fallback branch.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
            new PromptMessages.OwnSleeve(
                de.cas_ual_ty.dueldimension.duel.profile.Sleeves.nameOf(playerDeck.sleeve())));

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
                return player.getGameProfile().name() + " is already duelling";
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
            return "Ruled duels unavailable: " + EngineRuntime.unavailable(paths);
        }

        // Each player brings their own deck. The fallbacks differ so that two
        // players who have both chosen nothing still get a duel rather than a
        // mirror match neither asked for.
        de.cas_ual_ty.dueldimension.duel.match.Banlist banlist =
            de.cas_ual_ty.dueldimension.duel.match.Banlists.byId(config.banlistId());
        ChosenDeck deckA = deckFor(first, StarterDecks.YUGI, banlist);
        ChosenDeck deckB = deckFor(second, StarterDecks.KAIBA, banlist);
        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(first, deckA.cards());
        HeadlessDuelRunner.Deck deck1 = withChaosDiskPromise(second, deckB.cards());

        long seed = first.level().getGameTime()
            ^ first.getUUID().getLeastSignificantBits()
            ^ second.getUUID().getMostSignificantBits();
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};

        // A translator EACH. It is stateless but for the select hint, which the
        // core addresses to one player (generic_duel.cpp:843-857) -- one
        // instance would let seat 0's caption title seat 1's next selection,
        // and would make any per-seat state added here a cross-seat leak by
        // default. The lookup and the seat stay per-call arguments regardless.
        PromptTranslator translator0 = new PromptTranslator(engine.cards(), engine.descriptions());
        PromptTranslator translator1 = new PromptTranslator(engine.cards(), engine.descriptions());
        DuelSession[] sessionHolder = new DuelSession[1];

        // One responder per player, each posting its prompts under its OWN
        // seat number. The seat is what the drain uses to decide who a
        // question is for, so getting it wrong here would send both players
        // the same prompts and let either answer for the other. It is also
        // what rebases every option's controller into the numbering that
        // player's board is drawn in; see HumanResponseSource.respond.
        HumanResponseSource seat0 = new HumanResponseSource(translator0, (prompt, seat) ->
        {
            DuelSession running = sessionHolder[0];
            if(running != null)
            {
                running.postPrompt(prompt, seat.pendingSerial(), 0);
            }
        });
        HumanResponseSource seat1 = new HumanResponseSource(translator1, (prompt, seat) ->
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
            "pvp-" + first.getGameProfile().name() + "-" + second.getGameProfile().name(),
            engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(), deck0, deck1, seat0, seat1);
        sessionHolder[0] = session;

        // Both watchers point at the SAME RunningDuel: the session exists once
        // and the tick de-duplicates by identity before draining it.
        RunningDuel duel = new RunningDuel(session, Watcher.of(first), Watcher.of(second),
            engine.cards());
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
            .append(Component.literal(opponent.getGameProfile().name())
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" - "))
            .append(Component.literal(own.displayName()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" vs "))
            .append(Component.literal(theirs.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE)));

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new PromptMessages.DuelNames(player.getGameProfile().name(),
                opponent.getGameProfile().name()));

        // Only this player's own list: the opponent's deck is hidden
        // information, and their cards are fetched as they reach the field.
        int[] warmUp = ownDeck.main().stream().mapToInt(Integer::intValue).distinct().toArray();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new PromptMessages.DuelUpdate(null, List.of(), false, "", warmUp));
        // Both sleeves. This used to send only the player's own, on the reasoning
        // that the opponent's was theirs to see -- but a sleeve is worn by the
        // CARDS, and the person it is shown to is the opponent. Sending one way
        // meant nobody ever saw anybody else's. A sleeve name is cosmetic and
        // reveals nothing about what the deck holds.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new PromptMessages.OwnSleeve(
                de.cas_ual_ty.dueldimension.duel.profile.Sleeves.nameOf(own.sleeve())));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new PromptMessages.OpponentSleeve(
                de.cas_ual_ty.dueldimension.duel.profile.Sleeves.nameOf(theirs.sleeve())));
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

    /** No player may ask to see their deck more often than this. */
    private static final long DECK_VIEW_COOLDOWN_MS = 250L;

    /**
     * Sends the requesting player the cards in THEIR OWN deck, shuffled.
     * <p>
     * Ownership is enforced by construction rather than by validation. The
     * request carries no seat, no controller and no location, so the only seat
     * this method can find is the sender's own — resolved the same way
     * {@link #surrender} resolves it, by matching the player's uuid against the
     * seats of the duel they are registered in. A player who is not seated has
     * no {@code ACTIVE} entry and is answered with nothing.
     * <p>
     * The list itself was captured and de-ordered on the duel thread (see
     * {@code BoardObserver.ownDeck}); the engine is emphatically NOT touched
     * here, because this runs on the server tick thread. The shuffle below is on
     * a fresh copy, so the cached list stays as it was and the order that leaves
     * this method is a fresh one each time it is asked for.
     * <p>
     * There is exactly one recipient and it is the sender. Spectators do not
     * exist in this mod — a {@code Watcher} is either a seated player or the
     * console, and the console has no {@code ServerPlayer} to send to — so if
     * spectating is ever added, this is the line that has to be changed
     * deliberately rather than the one that quietly already worked.
     */
    public static void viewOwnDeck(ServerPlayer player)
    {
        RunningDuel duel = ACTIVE.get(Watcher.of(player));
        if(duel == null || !duel.session.isRunning())
        {
            return;
        }
        int seat = -1;
        for(int index = 0; index < duel.seats.length; index++)
        {
            if(duel.seats[index] != null
                && duel.seats[index].playerId().equals(player.getUUID()))
            {
                seat = index;
                break;
            }
        }
        if(seat < 0)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if(now - duel.lastDeckView[seat] < DECK_VIEW_COOLDOWN_MS)
        {
            return;
        }
        duel.lastDeckView[seat] = now;

        PromptMessages.OwnDeckList payload = shuffledDeckList(duel.ownDeck[seat]);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }

    /**
     * One seat's cached deck, shuffled onto the wire.
     * <p>
     * The shuffle is applied to a COPY and re-rolled on every call. Shuffling
     * the cached list in place would leave it claiming an order it no longer
     * holds, and the cache is what the next request reads — the observer hands
     * back an immutable list precisely so that mistake throws instead of
     * happening quietly.
     * <p>
     * The random is a fresh {@link java.security.SecureRandom} and is
     * emphatically not derived from the duel seed: the core shuffled the real
     * deck from that seed, so a display order that was also a function of it
     * could be inverted by anyone who knew or guessed it.
     * <p>
     * Package-private so a test can hold it to those two promises without
     * needing a running server.
     */
    static PromptMessages.OwnDeckList shuffledDeckList(
        List<de.cas_ual_ty.dueldimension.ocg.query.CardView> deck)
    {
        List<de.cas_ual_ty.dueldimension.ocg.query.CardView> shown = new ArrayList<>(deck);
        java.util.Collections.shuffle(shown, new java.security.SecureRandom());

        int[] codes = new int[shown.size()];
        int[] arts = new int[shown.size()];
        for(int i = 0; i < shown.size(); i++)
        {
            // Code and artwork, and that is the entire card. Nothing here knows
            // where any of these sat, so nothing here can say.
            codes[i] = shown.get(i).code();
            arts[i] = shown.get(i).art();
        }
        return new PromptMessages.OwnDeckList(codes, arts);
    }

    /** Concedes the player's running duel. */
    public static void surrender(ServerPlayer player)
    {
        RunningDuel duel = ACTIVE.get(Watcher.of(player));
        if(duel != null && duel.session.isRunning())
        {
            int surrenderingSeat = -1;
            for(int seat = 0; seat < duel.seats.length; seat++)
            {
                if(duel.seats[seat] != null
                    && duel.seats[seat].playerId().equals(player.getUUID()))
                {
                    surrenderingSeat = seat;
                    break;
                }
            }
            if(surrenderingSeat < 0 || !duel.session.forfeit(1 - surrenderingSeat))
            {
                return;
            }
            duel.forfeit = true;
            duel.disqualifyReward(surrenderingSeat);
            player.sendSystemMessage(Component.literal("You surrendered.").withStyle(ChatFormatting.RED));
            // The other seat is told, because a duel that simply stops looks
            // like a crash from the far side of the table.
            for(Watcher other : duel.seats)
            {
                if(other != null && !other.equals(Watcher.of(player)) && !other.console())
                {
                    ServerPlayer opponent = player.level().getServer().getPlayerList().getPlayer(other.playerId());
                    if(opponent != null)
                    {
                        opponent.sendSystemMessage(Component.literal("Your opponent surrendered.")
                            .withStyle(ChatFormatting.GOLD));
                    }
                }
            }

        }
    }

    /** Releases every registry entry owned by one duel, without touching a newer rematch. */
    /**
     * Whether this duelist is currently at a table.
     * <p>
     * Asked rather than told. A flag set when a duel begins and cleared when it
     * ends is one abnormal ending -- a crash, a disconnect, a seat released down
     * a path nobody thought about -- away from a duelist frozen in place
     * forever. Reading the live registry cannot desynchronise: when the duel
     * leaves ACTIVE the duelist walks again by itself.
     */
    /**
     * Is this PLAYER in a duel?
     * <p>
     * Not the same question as {@link #isDueling}, which asks about a DUELIST
     * -- it matches its argument against {@code RunningDuel.duelistId}, the
     * NPC's id, so handing it a player's UUID quietly answers "no" forever.
     * That mistake cost an evening: a caller used it to check whether a duel it
     * had just started was running, got false every time, and tore down the
     * board it had just built one statement later.
     * <p>
     * Read from the live seat registry for the same reason as its neighbour:
     * a flag is one abnormal ending away from lying.
     */
    public static boolean isSeated(java.util.UUID playerId)
    {
        return playerId != null && SEATS.containsKey(playerId);
    }

    /**
     * The player a duelist is duelling, or null.
     * <p>
     * Read from the live registry rather than remembered on the entity, for the
     * same reason its neighbour is: a field set when a duel begins and cleared
     * when it ends is one abnormal ending away from a duelist staring at
     * somebody forever.
     */
    public static java.util.UUID opponentOf(java.util.UUID duelistId)
    {
        if(duelistId == null)
        {
            return null;
        }
        for(java.util.Map.Entry<Watcher, RunningDuel> entry : ACTIVE.entrySet())
        {
            if(duelistId.equals(entry.getValue().duelistId) && !entry.getKey().console())
            {
                return entry.getKey().playerId();
            }
        }
        return null;
    }

    public static boolean isDueling(java.util.UUID duelistId)
    {
        if(duelistId == null)
        {
            return false;
        }
        for(RunningDuel duel : ACTIVE.values())
        {
            if(duelistId.equals(duel.duelistId))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Frees both seats of a finished duel, and takes down the board if it was
     * being played on one.
     *
     * @param server may be null in tests; a board can only be taken off a
     *               client that a server can reach
     */
    static void releaseSeats(net.minecraft.server.MinecraftServer server, RunningDuel duel)
    {
        for(Watcher watcher : duel.seats)
        {
            if(watcher == null)
            {
                continue;
            }
            ACTIVE.remove(watcher, duel);
            SEATS.remove(watcher.playerId());
            // A board in the world stands for a duel, but a duel that has
            // just ENDED still has an ending to play -- the last attack, the
            // damage it dealt, who won -- and all of that happens on the board.
            // So it lingers rather than going, and the client takes it down
            // when it has finished. Harmless for the duels that never had one.
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels
                .finish(server, watcher.playerId());
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
    private record ChosenDeck(HeadlessDuelRunner.Deck cards, String displayName,
        de.cas_ual_ty.dueldimension.card.CardSleevesType sleeve)
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
                    profile.trunk(), banlist,
                    de.cas_ual_ty.dueldimension.duel.profile.FreeMode.isEnabled(player));
            if(chosen != null && problems.isEmpty())
            {
                // The artwork each copy wears travels with the deck order, and
                // this is the only place it can be picked up: past here the
                // deck is a list of passcodes and one Dark Magician is every
                // other Dark Magician. artsFor takes the list itself, by
                // identity, so the deck's own list is passed and never a copy.
                // Says what the server is about to hand the engine, because an
                // Extra Deck card that turns up in the opening hand can only
                // come from one of two places -- this list, or the way the
                // client draws it -- and nothing else distinguishes them.
                logDeckSplit(player, chosen);
                return new ChosenDeck(
                    new HeadlessDuelRunner.Deck(chosen.main(), chosen.extra())
                        .wearing(chosen.artsFor(chosen.main()), chosen.artsFor(chosen.extra())),
                    chosen.name(), chosen.sleeve());
            }
            if(chosen != null)
            {
                player.sendSystemMessage(Component.literal("\"" + active
                        + "\" cannot be duelled with (" + problems.get(0) + "); using "
                        + fallback.displayName() + ".").withStyle(ChatFormatting.YELLOW));
            }
        }
        // A starter deck has no sleeve of its own, and this is the branch where
        // the player's chosen deck was refused -- so the back is the plain one,
        // which is also the honest signal that the deck on the table is not theirs.
        return new ChosenDeck(fallback.load().toRunnerDeck(), fallback.displayName(),
            de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT);
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
        // Abandonment does not go through releaseSeats -- it drops the entries
        // itself, because the player it belonged to has already gone -- so the
        // board has to be taken down here as well.
        de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels
            .release(player.level().getServer(), player.getUUID());
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
            // Their board too, not just the leaver's. Releasing one seat took
            // the duel away from the player who had gone and left the one who
            // had not standing on a field they could not leave, locked to a
            // duel with nobody in it.
            de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels
                .release(player.level().getServer(), other.playerId());
            ServerPlayer opponent = player.level().getServer().getPlayerList().getPlayer(other.playerId());
            if(opponent != null)
            {
                opponent.sendSystemMessage(Component.literal("Your opponent disconnected; the duel is over.")
                    .withStyle(ChatFormatting.GOLD));
                // And told the duel is OVER, in the words the client already
                // knows. Taking the board away says the board has gone; this
                // says the duel has, which is what unwinds the prompt they may
                // be sitting on, closes whatever screen is up and hands them
                // back an ordinary game. Without it the far seat kept a duel
                // screen waiting on a reply for a duel that no longer existed.
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(opponent,
                    new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate(
                        null, java.util.List.of(), true, "Opponent disconnected", new int[0]));
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
            return "engine unavailable: " + EngineRuntime.unavailable(paths);
        }
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};
        DuelSession session = DuelSession.create("console", engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(),
            StarterDecks.byId(deckA).load().toRunnerDeck(),
            StarterDecks.byId(deckB).load().toRunnerDeck(),
            new ExecutorBot(seed, Duelists.forProfile(deckA), engine.cards(), engine.cards().all()),
            new ExecutorBot(seed * 2 + 1, Duelists.forProfile(deckB), engine.cards(), engine.cards().all()));
        ACTIVE.put(Watcher.forConsole(), new RunningDuel(session, Watcher.forConsole(), null,
            engine.cards()));
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

        List<RunningDuel> finished = new ArrayList<>();
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
            boolean[] completed = {false};
            int[] winner = {-2};
            String[] failure = {null};

            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Message message)
                {
                    duel.rewards.accept(DuelMessage.decode(message.message()));
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
                    // seat0 is an absolute view for the server-side tracker:
                    // its self is core seat 0 and its opponent core seat 1.
                    duel.rewards.observe(board.forSeat(0));
                    // Same absolute seat-0 view the reward tracker reads, so
                    // the Seal is seen whichever side played it.
                    duel.sealOnField = de.cas_ual_ty.dueldimension.duel.orichalcos
                        .OrichalcosSouls.sealOnField(board.forSeat(0));
                    for(int seat = 0; seat < 2; seat++)
                    {
                        if(duel.seats[seat] == null)
                        {
                            continue;
                        }
                        // Kept server-side against the seat it belongs to, and
                        // NOT put on the update below. A DuelUpdate is the one
                        // thing broadcast per seat, so a deck that never rides
                        // it has no path to the wrong player at all.
                        duel.ownDeck[seat] = board.deckForSeat(seat);
                        outbound[seat].add(new PromptMessages.DuelUpdate(board.forSeat(seat),
                            List.of(), false, "", new int[0], new ArrayList<>(pending[seat])));
                        pending[seat].clear();
                        // Anyone standing at the board sees it too, from the
                        // same ordered point -- but never this update. Their
                        // copy is built by stripping this one, so a bystander
                        // cannot be handed a duellist's hand by an oversight
                        // about which packet went where.
                        if(seat == 0 && duel.seats[0] != null)
                        {
                            de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels
                                .showToSpectators(server, duel.seats[0].playerId(),
                                    board.forSeat(0));
                        }
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
                    completed[0] = done.completed() && done.result() != null;
                    winner[0] = completed[0] ? done.result().winner() : -2;
                }
                else if(event instanceof DuelSession.Event.Forfeited forfeited)
                {
                    over[0] = true;
                    completed[0] = true;
                    winner[0] = forfeited.winner();
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
                        // Just the outcome. This string is built PER SEAT, so
                        // each player is already being told about their own duel
                        // -- naming who won on top of that is saying the same
                        // thing twice, and "Winner: you" reads like a scoreboard
                        // rather than a result.
                        : winner[0] == seat ? "Victory"
                        : winner[0] == (1 - seat) ? "Lose" : "Draw";
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
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new PromptMessages.DuelUpdate(update.board(),
                                last ? log : List.of(), last && over[0], last ? result : "",
                                new int[0], update.events()));
                    }
                    else if(packet instanceof DuelSession.Event.Prompt prompt)
                    {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new PromptMessages.ShowPrompt(prompt.prompt(), prompt.serial()));
                    }
                }
            }

            // A stopped thread and its queued terminal event are published in
            // two operations. Only the event authorizes conclusion; observing
            // isRunning=false in the tiny interval before the queue add must
            // not finalize this duel with an invented empty result.
            if(over[0])
            {
                concludeGame(server, duel, winner[0], completed[0]);
                finished.add(duel);
                for(Watcher watcher : duel.seats)
                {
                    if(watcher != null)
                    {
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
        finished.forEach(duel -> releaseSeats(server, duel));
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
        RunningDuel duel, int winnerSeat, boolean completed)
    {
        if(!duel.beginConclusion())
        {
            return;
        }
        duel.rewards.finishGame(winnerSeat);
        if(duel.machine == null || duel.config == null)
        {
            if(winnerSeat == 0 || winnerSeat == 1)
            {
                duel.wins[winnerSeat]++;
            }
            if(completed)
            {
                payOut(server, duel);
            }
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
            if(completed)
            {
                payOut(server, duel);
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

        // With the config, not without it. Game two used to be started through
        // the two-argument overload, which substitutes MatchConfig.DEFAULT, so
        // everything the two of them agreed in the lobby -- the banlist their
        // decks were checked against, and now where the duel is played -- was
        // silently dropped for the second and third games of a match. The
        // config was only re-attached to the new RunningDuel afterwards, which
        // is too late to affect the duel it configures.
        String error = startPlayerDuel(first, second, config);
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
            next.rewards.carryFrom(duel.rewards);
            next.carryRewardEligibilityFrom(duel);
            machine.tryMoveTo(de.cas_ual_ty.dueldimension.duel.match.MatchState.DUELING);
        }
    }

    /**
     * Pays both duellists once the contest is over.
     * <p>
     * Once per contest, not once per game: a match is one duel played as
     * several, and paying per game would make a best-of-three worth three times
     * a single duel for the same evening's work.
     * <p>
     * NPC duels now participate in the same explained assessment at a reduced
     * scale. They are the single-player progression loop; reducing every line
     * keeps PvP the faster economy without making solo duels worthless.
     */
    private static void payOut(net.minecraft.server.MinecraftServer server, RunningDuel duel)
    {
        if(duel.paid)
        {
            return;
        }
        duel.paid = true;

        // Before the rewards, and deliberately outside their loop. That loop
        // skips a seat whose Watcher is null -- which is exactly a duelist's
        // seat -- and skips anyone who failed canReward(), i.e. conceded.
        // Neither of those should save you from the seal.
        if(duel.sealOnField)
        {
            markSealLoser(server, duel);
        }
        // A draw pays both the losing rate. Nobody won it, and the alternative
        // -- paying nobody -- punishes two players for a game that ran long.
        boolean drawn = duel.wins[0] == duel.wins[1];
        for(int seat = 0; seat < duel.seats.length; seat++)
        {
            Watcher watcher = duel.seats[seat];
            if(watcher == null || watcher.console() || !duel.canReward(seat))
            {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(watcher.playerId());
            if(player == null)
            {
                // Left before the end. Their opponent is still paid: they won
                // the duel that was actually played.
                continue;
            }
            boolean won = duel.wins[seat] > duel.wins[1 - seat];
            de.cas_ual_ty.dueldimension.shop.DuelReward.Outcome outcome = won
                ? de.cas_ual_ty.dueldimension.shop.DuelReward.Outcome.WIN
                : drawn ? de.cas_ual_ty.dueldimension.shop.DuelReward.Outcome.DRAW
                : de.cas_ual_ty.dueldimension.shop.DuelReward.Outcome.LOSS;
            // NPCs are the repeatable single-player loop, so they do pay, but
            // at a lower rate than a contest another person had to play.
            double scale = duel.isTwoPlayer() ? 1D : 0.65D;
            de.cas_ual_ty.dueldimension.shop.DuelReward.Breakdown reward =
                de.cas_ual_ty.dueldimension.shop.DuelReward.calculate(outcome,
                    duel.rewards.metrics(seat, duel.forfeit), scale);
            int before = de.cas_ual_ty.dueldimension.shop.DuelPoints.get(player);
            de.cas_ual_ty.dueldimension.shop.DuelPoints.award(player, reward.total());
            int after = de.cas_ual_ty.dueldimension.shop.DuelPoints.get(player);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints(after));
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result(
                    duel.rewardId, outcome, reward.lines(), reward.total(), before, after,
                    duel.gameNumber, duel.wins[seat], duel.wins[1 - seat], !duel.isTwoPlayer()));
        }
    }

    /**
     * Takes the loser's soul, player or duelist alike.
     * <p>
     * Called once per contest from {@link #payOut}, after {@code wins[]} is
     * final. A duelist has no {@link Watcher} — its seat is null — so it is
     * found through the id captured when the challenge was made.
     */
    private static void markSealLoser(net.minecraft.server.MinecraftServer server,
        RunningDuel duel)
    {
        if(duel.wins[0] == duel.wins[1])
        {
            return;   // a draw has no loser
        }
        int loser = duel.wins[0] > duel.wins[1] ? 1 : 0;
        Watcher watcher = duel.seats[loser];
        if(watcher == null)
        {
            // The duelist lost. Its soul goes the same way a player's does, and
            // on the same schedule: the player is still watching the duel
            // screen, and should be back in the world to see it happen.
            // Waits for the human at the OTHER seat: a duelist has no screen
            // to close, and the point of the wait is that somebody is back in
            // the world to watch.
            Watcher winner = duel.seats[1 - loser];
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.markById(
                server, duel.duelistId,
                winner == null || winner.console() ? null : winner.playerId());
            return;
        }
        if(watcher.console())
        {
            return;
        }
        de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
            .markById(server, watcher.playerId(), watcher.playerId());
    }

    /**
     * The Chaos Duel Disk's promise: draw The Seal of Orichalcos.
     * <p>
     * Worn in the off hand and holding the Seal somewhere in the deck, a
     * duelist opens with it — the card is moved to sixth from the top, which
     * with the first-turn draw is the card they draw on their first turn.
     * <p>
     * It moves a card the deck already contains. A deck without the Seal is
     * handed back untouched, so the disk cannot conjure one, and the deck stays
     * exactly the size and contents it was built as.
     */
    /**
     * Logs the main/extra split the duel is starting from, and names any card
     * in the main list that belongs in the Extra Deck. Diagnostic only: it
     * reports, and deliberately does not relocate anything, because moving a
     * card here would hide whichever earlier step put it in the wrong list.
     */
    private static void logDeckSplit(ServerPlayer player,
        de.cas_ual_ty.dueldimension.duel.profile.DeckList deck)
    {
        StringBuilder strays = new StringBuilder();
        for(int code : deck.main())
        {
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card != null && card.getIsInExtraDeck())
            {
                strays.append(strays.isEmpty() ? "" : ", ").append(card.getName())
                    .append(" (").append(code).append(")");
            }
        }
        de.cas_ual_ty.dueldimension.DuelDimension.log("Duel deck for "
            + player.getGameProfile().name() + " \"" + deck.name() + "\": main="
            + deck.main().size() + " extra=" + deck.extra().size()
            + (strays.isEmpty() ? " (no strays)" : " STRAYS IN MAIN: " + strays));
    }

    private static HeadlessDuelRunner.Deck withChaosDiskPromise(ServerPlayer player,
        HeadlessDuelRunner.Deck deck)
    {
        if(player == null || deck == null)
        {
            return deck;
        }
        // The disk SLOT, not a held item: the promise belongs to the disk being
        // worn for this duel, which is now profile state rather than whatever
        // happens to be in a hand.
        if(!de.cas_ual_ty.dueldimension.duel.dueldisk.WornDisks.isWearing(player,
            de.cas_ual_ty.dueldimension.DdItems.CHAOS_DISK))
        {
            return deck;
        }
        int seal = de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.SEAL_PASSCODE;
        if(!deck.main().contains(seal))
        {
            return deck;
        }
        de.cas_ual_ty.dueldimension.DuelDimension.log("Chaos Disk: " + player.getGameProfile().name()
            + " will open with The Seal of Orichalcos");
        return deck.guaranteeing(seal);
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
            // Relative like every other event here. A draw is winner 2, which
            // is nobody's side of the table, so only a real winner is rebased.
            return new DuelEvent(DuelEvent.Kind.WIN, 0, -1, -1, win.reason(),
                win.winner() < 2 ? side(win.winner(), viewer) : win.winner());
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
