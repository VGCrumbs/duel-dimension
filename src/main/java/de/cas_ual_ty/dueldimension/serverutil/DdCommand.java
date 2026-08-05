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
    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(DuelDimension.MOD_ID)
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
                                        .requires((source) -> source.getServer().isSingleplayer() || source.hasPermission(2))
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes((context) -> DdCommand.bindersSet(context, UuidArgument.getUuid(context, "uuid")))
                                        )
                                )
                                .then(Commands.literal("set")
                                        .requires((source) -> source.getServer().isSingleplayer() || source.hasPermission(2))
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
            ItemStack itemStack = DdItems.CARD_BINDER.get().getActiveBinder((Player) context.getSource().getEntity());
            
            if(!itemStack.isEmpty())
            {
                CardBinderCardsManager m = DdItems.CARD_BINDER.get().getInventoryManager(itemStack);
                context.getSource().sendSuccess(Component.literal("Binder UUID: " + m.getUUID()), true);
            }
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    public static int bindersSet(CommandContext<CommandSourceStack> context, UUID uuid)
    {
        if(context.getSource().getEntity() instanceof Player)
        {
            ItemStack itemStack = DdItems.CARD_BINDER.get().getActiveBinder((Player) context.getSource().getEntity());
            
            if(!itemStack.isEmpty())
            {
                DdItems.CARD_BINDER.get().setUUIDAndUpdateManager(itemStack, uuid);
                
                context.getSource().sendSuccess(Component.literal("Set Binder UUID to: " + uuid.toString()), true);
            }
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    public static int bindersFill(CommandContext<CommandSourceStack> context, int amount)
    {
        if(context.getSource().getEntity() instanceof Player)
        {
            ItemStack itemStack = DdItems.CARD_BINDER.get().getActiveBinder((Player) context.getSource().getEntity());
            
            if(!itemStack.isEmpty())
            {
                CardBinderCardsManager m = DdItems.CARD_BINDER.get().getInventoryManager(itemStack);
                
                if(m.isInIdleState())
                {
                    // --- Start ---
                    
                    m.setWorking();
                    
                    // --- Loading ---
                    
                    if(!m.isLoaded())
                    {
                        context.getSource().sendSuccess(Component.literal("Loading Binder..."), true);
                        m.loadRunnable().run();
                    }
                    
                    // --- Filling ---
                    
                    context.getSource().sendSuccess(Component.literal("Filling Binder..."), true);
                    
                    List<CardHolder> list = m.forceGetList();
                    
                    DdDatabase.forAllCardVariants((card, imageIndex) ->
                    {
                        for(int i = 0; i < amount; ++i)
                        {
                            list.add(new CardHolder(card, imageIndex, Rarities.CREATIVE.name));
                        }
                    });
                    
                    // --- Saving ---
                    
                    context.getSource().sendSuccess(Component.literal("Saving Binder..."), true);
                    m.safeRunnable().run();
                    
                    // --- Done ---
                    
                    m.setIdle();
                    
                    context.getSource().sendSuccess(Component.literal("Done! Binder can now be opened!"), true);
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
                
                ((CardSetBaseItem) itemStack.getItem()).viewSetContents(player.level, player, itemStack);
                
                return Command.SINGLE_SUCCESS;
            }
        }
        
        return 0;
    }
}
