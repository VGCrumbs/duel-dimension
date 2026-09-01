package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.DdComponents;
import de.cas_ual_ty.dueldimension.DdEntityTypes;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A Duel Bot, carried.
 * <p>
 * The bot is furniture with a deck, so it behaves like furniture: put it down
 * and it stays exactly where it was put, right-click it bare-handed and it comes
 * back. Unlike {@link DuelistPlacerItem}, which is a testing tool that places a
 * fresh duelist each time and removes whatever it is pointed at, this IS the
 * bot -- one item, one bot, and the program it was running travels with it.
 */
public class DuelBotItem extends Item
{
    public DuelBotItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    /** Writes a bot's program onto the stack it is becoming. */
    public static void storeProgram(ItemStack stack, String program, String deckId)
    {
        stack.set(DdComponents.BOT_PROGRAM,
            program == null ? DuelBotEntity.STARTER : program);
        stack.set(DdComponents.BOT_DECK, deckId == null ? "" : deckId);
    }

    public static String program(ItemStack stack)
    {
        String value = stack.get(DdComponents.BOT_PROGRAM);
        return value == null || value.isBlank() ? DuelBotEntity.STARTER : value;
    }

    public static String deck(ItemStack stack)
    {
        String value = stack.get(DdComponents.BOT_DECK);
        return value == null ? "" : value;
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        if(level.isClientSide() || !(level instanceof ServerLevelAccessor server))
        {
            return InteractionResult.SUCCESS;
        }
        // On top of the face that was clicked, so a bot placed on the ground
        // stands on it rather than inside it. Same rule as the placer.
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());

        DuelBotEntity bot = DdEntityTypes.DUEL_BOT.create(server.getLevel(),
            net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if(bot == null)
        {
            return InteractionResult.FAIL;
        }
        bot.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        // The skin and name come from the profile, exactly as a duelist's do;
        // "duel_bot" is a profile like any other, which is what lets it reuse
        // DuelistRenderer without knowing anything about bots.
        bot.setProfileId("duel_bot");
        bot.setCustomName(Component.literal("Duel Bot"));
        bot.setProgram(program(context.getItemInHand()), deck(context.getItemInHand()));

        Player player = context.getPlayer();
        if(player != null)
        {
            // Facing whoever placed it, which is the direction a duel would be
            // held in and saves turning it round by hand.
            bot.setYRot(player.getYRot() + 180F);
            bot.setYHeadRot(bot.getYRot());
            bot.yBodyRot = bot.getYRot();
        }
        server.getLevel().addFreshEntity(bot);

        if(player != null && !player.getAbilities().instabuild)
        {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 26.2 feeds tooltip lines to a {@link Consumer} rather than adding them to
     * a list, and takes the {@code TooltipDisplay} that decides which of the
     * stack's components are shown.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.literal("Place it, then challenge it wearing a duel disk.")
            .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.literal("Right-click it bare-handed to pick it back up.")
            .withStyle(ChatFormatting.DARK_GRAY));
        String deck = deck(stack);
        if(!deck.isBlank())
        {
            lines.accept(Component.literal("Program: " + describe(program(stack), deck))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static String describe(String program, String deckId)
    {
        if(DuelBotEntity.STRUCTURE.equals(program))
        {
            de.cas_ual_ty.dueldimension.ocg.deck.StructureDecks.Entry entry =
                de.cas_ual_ty.dueldimension.ocg.deck.StructureDecks.byId(deckId);
            return entry == null ? "Structure Deck" : entry.displayName();
        }
        if(DuelBotEntity.CUSTOM.equals(program))
        {
            return deckId;
        }
        StarterDecks.Entry entry = StarterDecks.find(deckId);
        return entry == null ? "Starter Deck" : entry.displayName();
    }
}
