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
        return Command.SINGLE_SUCCESS;
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
