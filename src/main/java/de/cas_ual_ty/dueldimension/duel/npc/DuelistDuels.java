package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
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
    private static final Map<Watcher, DuelSession> ACTIVE = new ConcurrentHashMap<>();

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

        DuelSession existing = ACTIVE.get(Watcher.of(serverPlayer));
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
        // Until deck selection exists, the challenger runs Yugi's deck unless
        // that is what the NPC is using.
        StarterDecks.Entry playerDeck = npcDeck == StarterDecks.YUGI ? StarterDecks.KAIBA : StarterDecks.YUGI;

        long seed = serverPlayer.level.getGameTime() ^ serverPlayer.getUUID().getLeastSignificantBits();
        long[] seeds = {seed | 1, seed * 31 + 7, seed * 131 + 17, ~seed};

        HeadlessDuelRunner.Deck deck0 = playerDeck.load().toRunnerDeck();
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
                running.postPrompt(prompt, seat.pendingSerial());
            }
        });
        SEATS.put(serverPlayer.getUUID(), human);

        DuelSession session = DuelSession.create(
            "npc-" + serverPlayer.getGameProfile().getName(),
            engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(), deck0, deck1,
            human,
            new HeuristicBot(seed * 2 + 1, engine.cards(), engine.cards().all()));

        sessionHolder[0] = session;
        ACTIVE.put(Watcher.of(serverPlayer), session);

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
        DuelSession session = ACTIVE.get(Watcher.of(player));
        if(session != null && session.isRunning())
        {
            session.stop();
            player.sendSystemMessage(Component.literal("You surrendered.").withStyle(ChatFormatting.RED));
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
            new HeuristicBot(seed, engine.cards(), engine.cards().all()),
            new HeuristicBot(seed * 2 + 1, engine.cards(), engine.cards().all()));
        ACTIVE.put(Watcher.forConsole(), session);
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

        List<Watcher> finished = new ArrayList<>();
        ACTIVE.forEach((watcher, session) ->
        {
            List<String> log = new ArrayList<>();
            // One checkpoint per board the duel produced, each carrying the
            // events that happened since the previous one. Collapsing a whole
            // drain into "all the events plus the last board" reordered the
            // duel: boards are captured interleaved with messages (after
            // MSG_MOVE, MSG_DAMAGE, MSG_RECOVER, MSG_DRAW, MSG_WIN), so when a
            // batch ended on a message the board sent with it predated the
            // events sent with it, and the field jumped backwards until the
            // next batch corrected it.
            // Outbound packets in stream order: DuelUpdates and ShowPrompts
            // interleaved exactly as the duel produced them. All are sent from
            // this thread on one channel, so they arrive in this order too.
            List<Object> outbound = new ArrayList<>();
            List<DuelEvent> events = new ArrayList<>();
            boolean[] over = {false};
            String[] result = {""};

            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Message message)
                {
                    events.addAll(toDuelEvents(message.message()));
                    Component line = narrate(message.message(), descriptions);
                    if(line != null)
                    {
                        if(watcher.console())
                        {
                            watcher.send(server, line);
                        }
                        else
                        {
                            log.add(line.getString());
                        }
                    }
                }
                else if(event instanceof DuelSession.Event.Board board)
                {
                    // Checkpoint: everything up to here, then this board.
                    outbound.add(new PromptMessages.DuelUpdate(board.snapshot(), List.of(), false, "",
                        new int[0], new ArrayList<>(events)));
                    events.clear();
                }
                else if(event instanceof DuelSession.Event.Prompt prompt)
                {
                    // The question the duel stopped on, in its place in the
                    // stream: whatever led up to it is flushed first so the
                    // client animates it before the prompt appears.
                    if(!events.isEmpty())
                    {
                        outbound.add(new PromptMessages.DuelUpdate(null, List.of(), false, "",
                            new int[0], new ArrayList<>(events)));
                        events.clear();
                    }
                    outbound.add(prompt);
                }
                else if(event instanceof DuelSession.Event.Finished done)
                {
                    over[0] = true;
                    result[0] = done.completed()
                        ? "Winner: " + (done.result() == null ? "?"
                            : done.result().winner() == 0 ? "you" : done.result().winner() == 1 ? "opponent" : "draw")
                        : "Duel ended without a result";
                    watcher.send(server, Component.literal("Duel over - " + result[0])
                        .withStyle(ChatFormatting.GOLD));
                }
                else if(event instanceof DuelSession.Event.Failed failed)
                {
                    over[0] = true;
                    result[0] = "Duel failed: " + failed.reason();
                    watcher.send(server, Component.literal(result[0]).withStyle(ChatFormatting.RED));
                }
            });

            // Anything after the last checkpoint still has to be played; it
            // commits no board of its own.
            if(!events.isEmpty() || !log.isEmpty() || over[0])
            {
                outbound.add(new PromptMessages.DuelUpdate(null, List.of(), false, "", new int[0],
                    new ArrayList<>(events)));
                events.clear();
            }

            if(!watcher.console() && !outbound.isEmpty())
            {
                ServerPlayer player = server.getPlayerList().getPlayer(watcher.playerId());
                if(player != null)
                {
                    int lastUpdate = -1;
                    for(int i = 0; i < outbound.size(); i++)
                    {
                        if(outbound.get(i) instanceof PromptMessages.DuelUpdate)
                        {
                            lastUpdate = i;
                        }
                    }
                    for(int i = 0; i < outbound.size(); i++)
                    {
                        if(outbound.get(i) instanceof PromptMessages.DuelUpdate update)
                        {
                            // The log and the result belong to the batch, so
                            // they ride on its final update.
                            boolean last = i == lastUpdate;
                            de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                                new PromptMessages.DuelUpdate(update.board(),
                                    last ? log : List.of(), last && over[0], last ? result[0] : "",
                                    new int[0], update.events()));
                        }
                        else if(outbound.get(i) instanceof DuelSession.Event.Prompt prompt)
                        {
                            de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                                new PromptMessages.ShowPrompt(prompt.prompt(), prompt.serial()));
                        }
                    }
                }
            }

            if(!session.isRunning())
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
        });
        finished.forEach(ACTIVE::remove);
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
    private static List<DuelEvent> toDuelEvents(RawMessage raw)
    {
        DuelMessage message = DuelMessage.decode(raw);

        // An effect going onto the chain, and the cards it picked. Without
        // these the client had no way to show that anything was happening: an
        // effect resolved, the board simply changed, and a flip effect looked
        // like it had never activated at all.
        if(message instanceof DuelMessage.Chaining chaining)
        {
            return List.of(new DuelEvent(DuelEvent.Kind.CHAINING, chaining.code(),
                -1, zoneOf(chaining.card()), 0, chaining.card().controller()));
        }
        if(message instanceof DuelMessage.BecomeTarget target)
        {
            List<DuelEvent> events = new ArrayList<>();
            for(de.cas_ual_ty.dueldimension.ocg.msg.CardLocation location : target.targets())
            {
                events.add(new DuelEvent(DuelEvent.Kind.BECOME_TARGET, 0,
                    -1, zoneOf(location), 0, location.controller()));
            }
            return events;
        }
        if(message instanceof DuelMessage.FlipSummoning flip)
        {
            // A flip summon always ends face up.
            return List.of(new DuelEvent(DuelEvent.Kind.FLIP, flip.code(),
                -1, zoneOf(flip.card()), 1, flip.card().controller()));
        }
        // The announce messages carry the pause a summon has in the reference:
        // duelclient.cpp:3281 holds a card splash for 30 then 11 frames before
        // the MSG_MOVE slide. Without these events a summon was only its slide.
        if(message instanceof DuelMessage.Summoning summoning)
        {
            return List.of(new DuelEvent(DuelEvent.Kind.SUMMON, summoning.code(),
                -1, -1, 0, summoning.card().controller()));
        }
        if(message instanceof DuelMessage.SpSummoning spSummoning)
        {
            return List.of(new DuelEvent(DuelEvent.Kind.SPECIAL_SUMMON, spSummoning.code(),
                -1, -1, 0, spSummoning.card().controller()));
        }
        if(message instanceof DuelMessage.PositionChange position)
        {
            // Turning a card face down hides it again, so the same rule as a
            // set applies: the core names it to every seat, we do not.
            boolean nowHidden = (position.position()
                & de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN) != 0;
            int shown = nowHidden && position.controller() != 0 ? 0 : position.code();
            // `amount` carries which way the card is turning, so the animation
            // knows whether it ends on the face or the back.
            return List.of(new DuelEvent(DuelEvent.Kind.POSITION, shown, -1,
                DuelEvent.zoneOf(position.controller(), position.location(), position.sequence(), 0),
                nowHidden ? 0 : 1, position.controller()));
        }
        if(message instanceof DuelMessage.PayLpCost cost)
        {
            // duelclient.cpp:3690 plays the damage sound for a paid cost, so
            // it rides the same event as battle damage.
            return List.of(new DuelEvent(DuelEvent.Kind.DAMAGE, 0, -1, -1, cost.amount(),
                cost.player()));
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
                packed, coin.player()));
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
                packed, dice.player()));
        }
        if(message instanceof DuelMessage.SetCard set)
        {
            // A set card's identity is hidden information; only its owner's
            // client may hear the code.
            return List.of(new DuelEvent(DuelEvent.Kind.SET,
                set.card().controller() == 0 ? set.code() : 0,
                -1, zoneOf(set.card()), 0, set.card().controller()));
        }

        DuelEvent single = toDuelEvent(message);
        return single == null ? List.of() : List.of(single);
    }

    /** Packs a decoded location the way the animation layer expects. */
    private static int zoneOf(de.cas_ual_ty.dueldimension.ocg.msg.CardLocation location)
    {
        return DuelEvent.zoneOf(location.controller(), location.location(), location.sequence(), 0);
    }

    private static DuelEvent toDuelEvent(DuelMessage message)
    {

        if(message instanceof DuelMessage.Move move)
        {
            int from = DuelEvent.zoneOf(move.from().controller(), move.from().location(),
                move.from().sequence(), 0);
            int to = DuelEvent.zoneOf(move.to().controller(), move.to().location(),
                move.to().sequence(), 0);
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
            if(move.to().controller() != 0
                && ((move.to().position() & de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN) != 0
                    || move.to().location() == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_HAND
                    || move.to().location() == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_DECK))
            {
                shownCode = 0;
            }
            return new DuelEvent(kind, shownCode, from, to, 0, move.to().controller());
        }
        if(message instanceof DuelMessage.Attack attack)
        {
            // A direct attack has no target zone; the animation lunges at the
            // defending player's side of the table instead.
            int from = DuelEvent.zoneOf(attack.attacker().controller(), attack.attacker().location(),
                attack.attacker().sequence(), 0);
            int to = attack.isDirect() ? -1
                : DuelEvent.zoneOf(attack.target().controller(), attack.target().location(),
                    attack.target().sequence(), 0);
            return new DuelEvent(DuelEvent.Kind.ATTACK, 0, from, to, 0,
                attack.attacker().controller());
        }
        if(message instanceof DuelMessage.Damage damage)
        {
            return new DuelEvent(DuelEvent.Kind.DAMAGE, 0, -1, -1, damage.amount(), damage.player());
        }
        if(message instanceof DuelMessage.Recover recover)
        {
            return new DuelEvent(DuelEvent.Kind.RECOVER, 0, -1, -1, recover.amount(), recover.player());
        }
        if(message instanceof DuelMessage.Draw draw)
        {
            return new DuelEvent(DuelEvent.Kind.DRAW, 0, -1, -1, draw.cards().size(), draw.player());
        }
        if(message instanceof DuelMessage.ShuffleDeck shuffle)
        {
            return new DuelEvent(DuelEvent.Kind.SHUFFLE, 0, -1, -1, 0, shuffle.player());
        }
        if(message instanceof DuelMessage.NewPhase phase)
        {
            return new DuelEvent(DuelEvent.Kind.PHASE, 0, -1, -1, phase.phase(), 0);
        }
        if(message instanceof DuelMessage.NewTurn turn)
        {
            return new DuelEvent(DuelEvent.Kind.NEW_TURN, 0, -1, -1, 0, turn.player());
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
        ACTIVE.values().forEach(DuelSession::stop);
        ACTIVE.clear();
    }
}
