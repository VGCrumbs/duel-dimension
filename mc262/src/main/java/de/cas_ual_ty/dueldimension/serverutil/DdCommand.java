package de.cas_ual_ty.dueldimension.serverutil;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.cardbinder.CardBinderCardsManager;
import de.cas_ual_ty.dueldimension.rarity.Rarities;
import de.cas_ual_ty.dueldimension.set.CardSetBaseItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class DdCommand
{
    private static int engineStatus(CommandContext<CommandSourceStack> context)
    {
        de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.Paths paths =
                de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.Paths.defaults();
        String status = de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.status(paths);
        context.getSource().sendSuccess(() -> Component.literal("Rules engine: " + status), false);

        // WHICH engine, not just whether there is one. Neither the card scripts
        // nor cards.cdb carries a version of its own, so "duels work" and "duels
        // are being judged by rules from a year ago" look identical from in
        // game. These four lines are the only way to tell them apart -- and the
        // first thing to ask for when a card behaves unexpectedly.
        context.getSource().sendSuccess(() -> Component.literal("  scripts: "
            + paths.scriptsDir().toAbsolutePath()), false);
        context.getSource().sendSuccess(() -> Component.literal("  database: "
            + paths.cdb().toAbsolutePath()), false);
        context.getSource().sendSuccess(() -> Component.literal("  core: "
            + paths.library().toAbsolutePath()), false);

        String version = de.cas_ual_ty.dueldimension.ocg.session.EngineBundle.version();
        String bundled = version == null ? "none bundled with this build"
            : version + " (" + de.cas_ual_ty.dueldimension.ocg.session.EngineBundle.outcome() + ")";
        context.getSource().sendSuccess(() -> Component.literal("  bundle: " + bundled), false);
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Whether the card database is actually there.
     * <p>
     * The counterpart to {@code /dueldimension engine}: a server admin looking
     * at a world full of unknown cards needs somewhere to ask what happened
     * that is not the log file of a boot that may have been days ago.
     */
    private static int databaseStatus(CommandContext<CommandSourceStack> context)
    {
        String problem = DdDatabase.problem();
        if(problem == null)
        {
            context.getSource().sendSuccess(() -> Component.literal("Card database: "
                + DdDatabase.cardCount() + " cards from "
                + DuelDimension.mainFolder.getAbsolutePath()), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal("Card database unavailable: " + problem
            + ". Expected at " + DuelDimension.mainFolder.getAbsolutePath()
            + "; it is downloaded automatically from " + DuelDimension.dbSourceUrl
            + " on start."));
        return 0;
    }

    private static int testDuel(CommandContext<CommandSourceStack> context)
    {
        String error = de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels
                .startConsoleDuel("yugi", "kaiba", System.nanoTime());
        if(error != null)
        {
            context.getSource().sendFailure(Component.literal("Cannot start duel: " + error));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Test duel started (Yugi vs Kaiba); watch the log."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnDuelist(CommandContext<CommandSourceStack> context, String profile)
    {
        CommandSourceStack source = context.getSource();
        de.cas_ual_ty.dueldimension.duel.npc.DuelistEntity duelist =
                de.cas_ual_ty.dueldimension.DdEntityTypes.DUELIST.create(source.getLevel(),
                        net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if(duelist == null)
        {
            source.sendFailure(Component.literal("Could not create duelist"));
            return 0;
        }
        // moveTo became snapTo: same act, and the name now says it is a
        // teleport rather than a step, which is what spawning wants.
        duelist.snapTo(source.getPosition().x, source.getPosition().y, source.getPosition().z,
                source.getRotation().y, 0F);
        duelist.setProfileId(profile);
        source.getLevel().addFreshEntity(duelist);
        source.sendSuccess(() -> Component.literal("Spawned duelist: " + profile), false);
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Whether this source may run the operator-only branches.
     * <p>
     * The null check is not defensive padding. A {@code requires} predicate is
     * also evaluated when the command tree is sent to a joining client, to
     * decide which branches they are allowed to see -- and that happens through
     * a source with no server attached. Calling {@code getServer().isSingleplayer()}
     * there threw, and because it threw while placing the player in the world,
     * the client was disconnected with "Invalid player data".
     * <p>
     * Permission first, so the common case does not depend on the server being
     * reachable at all.
     */
    private static boolean singleplayerOrOp(CommandSourceStack source)
    {
        if(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))
        {
            return true;
        }
        return source.getServer() != null && source.getServer().isSingleplayer();
    }

    /**
     * Downloads every card's full art, then scales it, with a progress bar.
     * <p>
     * Opt-in and not cheap: the full raws run to well over a gigabyte, which is
     * why this is a command the player types rather than something the mod
     * decides to do to their disk. Anyone may run it — it costs the caller's own
     * storage and nobody else's — so it takes no permission level.
     */
    private static int preload(CommandContext<CommandSourceStack> context, boolean start)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        net.minecraft.server.level.ServerPlayer player =
            context.getSource().getPlayerOrException();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new de.cas_ual_ty.dueldimension.net.PreloadMessages.Preload(start));
        context.getSource().sendSuccess(() -> Component.literal(start
            ? "Preloading card art. Progress is shown above your hotbar; "
                + "run /dueldimension preload stop to cancel."
            : "Stopping the card art preload. What has already downloaded is kept."), false);
        return 1;
    }

    /**
     * Marks the caller for the Seal of Orichalcos, without a duel.
     * <p>
     * The sequence is otherwise only reachable by losing a real duel with the
     * field spell up, which is a long way to go to check that a timer fires.
     * Gamemaster-only, and it reports what it did rather than killing silently.
     */
    private static int sealTest(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        return sealMark(context, java.util.List.of(context.getSource().getPlayerOrException()));
    }

    /**
     * Marks whatever the selector picked. Any living entity, not just players:
     * the seal needs nothing player-specific, and a mob standing still is a far
     * easier thing to watch the animation on than a player who has to lose a
     * duel first.
     */
    private static int sealTarget(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        return sealMark(context,
            net.minecraft.commands.arguments.EntityArgument.getEntities(context, "targets"));
    }

    private static int sealMark(CommandContext<CommandSourceStack> context,
        java.util.Collection<? extends net.minecraft.world.entity.Entity> targets)
    {
        int marked = 0;
        for(net.minecraft.world.entity.Entity entity : targets)
        {
            if(entity instanceof net.minecraft.world.entity.LivingEntity living
                && de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.force(living))
            {
                marked++;
            }
        }
        if(marked == 0)
        {
            // Nothing living in the selection, or everything in it was marked
            // already. Either way saying "marked 0" is more use than success.
            context.getSource().sendFailure(Component.literal(
                "Nothing there for the Seal of Orichalcos to take."));
            return 0;
        }
        int count = marked;
        context.getSource().sendSuccess(() -> Component.literal(
            "The Seal of Orichalcos has marked " + count
                + (count == 1 ? " target." : " targets.")), true);
        return count;
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(DuelDimension.MOD_ID)
                .then(Commands.literal("engine")
                        .executes((context) -> DdCommand.engineStatus(context))
                        .then(Commands.literal("testduel")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes((context) -> DdCommand.testDuel(context))
                        )
                )
                .then(Commands.literal("database")
                        .executes((context) -> DdCommand.databaseStatus(context))
                )
                .then(Commands.literal("preload")
                        .executes((context) -> DdCommand.preload(context, true))
                        .then(Commands.literal("stop")
                                .executes((context) -> DdCommand.preload(context, false))
                        )
                )
                .then(Commands.literal("seal")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("test")
                                .executes((context) -> DdCommand.sealTest(context))
                        )
                        .then(Commands.argument("targets",
                                net.minecraft.commands.arguments.EntityArgument.entities())
                                .executes((context) -> DdCommand.sealTarget(context))
                        )
                )
                .then(Commands.literal("duelist")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("profile", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((context, builder) ->
                                {
                                    de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.ALL
                                            .forEach(entry -> builder.suggest(entry.id()));
                                    return builder.buildFuture();
                                })
                                .executes((context) -> DdCommand.spawnDuelist(context,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "profile")))
                        )
                )
                .then(Commands.literal("setcontents")
                        .requires((source) -> source.getEntity() instanceof Player)
                        .executes((source) -> DdCommand.setcontents(source))
                )
                .then(Commands.literal("binders")
                        .then(Commands.literal("uuid")
                                .requires((source) -> source.getEntity() instanceof Player)
                                .then(Commands.literal("get")
                                        .executes((context) -> DdCommand.bindersGet(context))
                                )
                                .then(Commands.literal("create")
                                        .requires((source) -> DdCommand.singleplayerOrOp(source))
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes((context) -> DdCommand.bindersSet(context, UuidArgument.getUuid(context, "uuid")))
                                        )
                                )
                                .then(Commands.literal("set")
                                        .requires((source) -> DdCommand.singleplayerOrOp(source))
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes((context) -> DdCommand.bindersSet(context, UuidArgument.getUuid(context, "uuid")))
                                        )
                                )
                        )
                        .then(Commands.literal("fill")
                                .requires((source) -> source.getEntity() instanceof Player)
                                .executes((context) -> DdCommand.bindersFill(context, 3))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes((context) -> DdCommand.bindersFill(context, IntegerArgumentType.getInteger(context, "count")))
                                )
                        )
                )
        );
    }
    
    public static int bindersGet(CommandContext<CommandSourceStack> context)
    {
        if(context.getSource().getEntity() instanceof Player)
        {
            ItemStack itemStack = DdItems.CARD_BINDER.getActiveBinder((Player) context.getSource().getEntity());
            
            if(!itemStack.isEmpty())
            {
                CardBinderCardsManager m = DdItems.CARD_BINDER.getInventoryManager(itemStack);
                context.getSource().sendSuccess(() -> Component.literal("Binder UUID: " + m.getUUID()), true);
            }
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    public static int bindersSet(CommandContext<CommandSourceStack> context, UUID uuid)
    {
        if(context.getSource().getEntity() instanceof Player)
        {
            ItemStack itemStack = DdItems.CARD_BINDER.getActiveBinder((Player) context.getSource().getEntity());
            
            if(!itemStack.isEmpty())
            {
                DdItems.CARD_BINDER.setUUIDAndUpdateManager(itemStack, uuid);
                
                context.getSource().sendSuccess(() -> Component.literal("Set Binder UUID to: " + uuid.toString()), true);
            }
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    public static int bindersFill(CommandContext<CommandSourceStack> context, int amount)
    {
        if(context.getSource().getEntity() instanceof Player)
        {
            ItemStack itemStack = DdItems.CARD_BINDER.getActiveBinder((Player) context.getSource().getEntity());
            
            if(!itemStack.isEmpty())
            {
                CardBinderCardsManager m = DdItems.CARD_BINDER.getInventoryManager(itemStack);
                
                if(m.isInIdleState())
                {
                    // --- Start ---
                    
                    m.setWorking();
                    
                    // --- Loading ---
                    
                    if(!m.isLoaded())
                    {
                        context.getSource().sendSuccess(() -> Component.literal("Loading Binder..."), true);
                        m.loadRunnable().run();
                    }
                    
                    // --- Filling ---
                    
                    context.getSource().sendSuccess(() -> Component.literal("Filling Binder..."), true);
                    
                    List<CardHolder> list = m.forceGetList();
                    
                    DdDatabase.forAllCardVariants((card, imageIndex) ->
                    {
                        for(int i = 0; i < amount; ++i)
                        {
                            list.add(new CardHolder(card, imageIndex, Rarities.CREATIVE.name));
                        }
                    });
                    
                    // --- Saving ---
                    
                    context.getSource().sendSuccess(() -> Component.literal("Saving Binder..."), true);
                    m.safeRunnable().run();
                    
                    // --- Done ---
                    
                    m.setIdle();
                    
                    context.getSource().sendSuccess(() -> Component.literal("Done! Binder can now be opened!"), true);
                }
            }
            
            return Command.SINGLE_SUCCESS;
        }
        
        return 0;
    }
    
    public static int setcontents(CommandContext<CommandSourceStack> context)
    {
        if(context.getSource().getEntity() instanceof Player)
        {
            Player player = (Player) context.getSource().getEntity();
            InteractionHand hand = CardSetBaseItem.getActiveSetItem(player);
            
            if(hand != null)
            {
                ItemStack itemStack = player.getItemInHand(hand);
                
                ((CardSetBaseItem) itemStack.getItem()).viewSetContents(player.level(), player, itemStack);
                
                return Command.SINGLE_SUCCESS;
            }
        }
        
        return 0;
    }
}
