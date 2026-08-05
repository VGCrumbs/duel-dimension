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
        PromptTranslator translator = new PromptTranslator(engine.cards(), engine.descriptions());
        HumanResponseSource human = new HumanResponseSource(translator, (prompt, seat) ->
            de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                new PromptMessages.ShowPrompt(prompt)));
        SEATS.put(serverPlayer.getUUID(), human);

        DuelSession session = DuelSession.create(
            "npc-" + serverPlayer.getGameProfile().getName(),
            engine.api(), engine.defaultFlags(), seeds,
            engine.cards(), engine.scripts(), deck0, deck1,
            human,
            new HeuristicBot(seed * 2 + 1, engine.cards(), engine.cards().all()));

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
            List<DuelEvent> events = new ArrayList<>();
            BoardSnapshot[] latestBoard = {null};
            boolean[] over = {false};
            String[] result = {""};

            session.drainEvents(event ->
            {
                if(event instanceof DuelSession.Event.Message message)
                {
                    DuelEvent animated = toDuelEvent(message.message());
                    if(animated != null)
                    {
                        events.add(animated);
                    }
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
                    latestBoard[0] = board.snapshot();
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

            if(!watcher.console() && (latestBoard[0] != null || !log.isEmpty() || over[0] || !events.isEmpty()))
            {
                ServerPlayer player = server.getPlayerList().getPlayer(watcher.playerId());
                if(player != null)
                {
                    de.cas_ual_ty.dueldimension.DuelDimension.channel.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate(
                            latestBoard[0], log, over[0], result[0], new int[0], events));
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

        if(message instanceof DuelMessage.NewTurn turn)
        {
            return Component.literal("— Turn: player " + turn.player() + " —").withStyle(ChatFormatting.YELLOW);
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
        if(message instanceof DuelMessage.NewPhase phase)
        {
            return Component.literal(phaseName(phase.phase())).withStyle(ChatFormatting.YELLOW);
        }
        if(message instanceof DuelMessage.Draw draw)
        {
            return Component.literal("Player " + draw.player() + " draws " + draw.cards().size())
                .withStyle(ChatFormatting.GRAY);
        }
        return null; // per-card moves and chain bookkeeping would drown the log
    }

    /**
     * Turns an engine message into something the client can animate and play a
     * sound for. Seat 0 is the watching player, so controller 0 is "you".
     */
    private static DuelEvent toDuelEvent(RawMessage raw)
    {
        DuelMessage message = DuelMessage.decode(raw);

        if(message instanceof DuelMessage.Move move)
        {
            int from = DuelEvent.zoneOf(move.from().controller(), move.from().location(),
                move.from().sequence(), 0);
            int to = DuelEvent.zoneOf(move.to().controller(), move.to().location(),
                move.to().sequence(), 0);
            DuelEvent.Kind kind = switch(move.to().location())
            {
                case de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE -> DuelEvent.Kind.DESTROY;
                case de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE ->
                    (move.to().position() & de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN) != 0
                        ? DuelEvent.Kind.SET : DuelEvent.Kind.SUMMON;
                case de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE ->
                    (move.to().position() & de.cas_ual_ty.dueldimension.ocg.OcgConstants.POS_FACEDOWN) != 0
                        ? DuelEvent.Kind.SET : DuelEvent.Kind.ACTIVATE;
                default -> DuelEvent.Kind.MOVE;
            };
            return new DuelEvent(kind, move.code(), from, to, 0, move.to().controller());
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
