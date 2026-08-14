package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.DdEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Puts a duelist somewhere, and takes it away again.
 * <p>
 * A testing tool, not content. An overworld duel needs two duellists standing
 * ten blocks apart on flat ground, which is a tedious thing to arrange twice
 * over every time a card's geometry needs looking at -- so this places an
 * opponent who stands exactly where it is put and never wanders off.
 * <p>
 * Right-click a block to place one. Right-click a duelist to remove it. Sneak
 * and right-click a duelist to cycle which starter deck it plays, so the same
 * spot can be used to test against several decks without replacing anything.
 * <p>
 * Duelists placed this way are flagged to duel on a board in the world rather
 * than on the screen, because that is the thing they exist to test.
 */
public class DuelistPlacerItem extends Item
{
    public DuelistPlacerItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        if(level.isClientSide() || !(level instanceof ServerLevelAccessor server))
        {
            return InteractionResult.SUCCESS;
        }
        // On top of the face that was clicked, so a duelist placed on the
        // ground stands on it rather than inside it.
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());

        DuelistEntity duelist = DdEntityTypes.DUELIST.create(server.getLevel(),
            net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if(duelist == null)
        {
            return InteractionResult.FAIL;
        }
        duelist.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        // Facing whoever placed it, which is the direction a duel would be
        // held in and saves turning it round by hand.
        Player player = context.getPlayer();
        if(player != null)
        {
            duelist.setYRot(player.getYRot() + 180F);
            duelist.setYHeadRot(duelist.getYRot());
        }
        duelist.setStationary(true);
        server.addFreshEntity(duelist);

        tell(player, Component.literal("Placed " + duelist.displayName()
            + " -- right-click it to duel, sneak-click it to change deck")
            .withStyle(ChatFormatting.GREEN));
        return InteractionResult.CONSUME;
    }

    /**
     * On a duelist: remove it, or cycle its deck while sneaking. On anything
     * else: nothing, so this cannot be used to delete a player's horse.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
        LivingEntity entity, InteractionHand hand)
    {
        if(player.level().isClientSide() || !(entity instanceof DuelistEntity duelist))
        {
            return InteractionResult.PASS;
        }
        if(player.isShiftKeyDown())
        {
            duelist.cycleProfile();
            tell(player, Component.literal("Now duelling as " + duelist.displayName())
                .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.CONSUME;
        }
        duelist.discard();
        tell(player, Component.literal("Removed the duelist").withStyle(ChatFormatting.YELLOW));
        return InteractionResult.CONSUME;
    }

    private static void tell(Player player, Component message)
    {
        if(player != null)
        {
            player.sendSystemMessage(message);
        }
    }

    /** Where the placed duelist faces, for a caller that wants to line one up by hand. */
    public static Direction facingOf(Player player)
    {
        return player.getDirection().getOpposite();
    }
}
