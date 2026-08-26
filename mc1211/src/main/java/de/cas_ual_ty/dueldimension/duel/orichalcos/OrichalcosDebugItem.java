package de.cas_ual_ty.dueldimension.duel.orichalcos;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Right-click any living thing to take its soul.
 * <p>
 * Purely a testing aid. The seal is otherwise only reachable by losing a real
 * duel with the field spell on the board, which is a long and unreliable way to
 * look at a six-second animation — and an animation is the kind of thing that
 * has to be looked at, repeatedly, from several angles, on something that is
 * not the camera holder.
 * <p>
 * It bypasses the {@code sealOfOrichalcosDeath} switch on purpose: someone
 * holding this item and clicking a cow has said what they want more clearly
 * than a config file could.
 */
public class OrichalcosDebugItem extends Item
{
    public OrichalcosDebugItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
        LivingEntity target, InteractionHand hand)
    {
        if(player.level().isClientSide())
        {
            // The seal is the server's to schedule. Claiming the click here
            // keeps the hand from swinging twice.
            return InteractionResult.SUCCESS;
        }
        if(!OrichalcosSouls.force(target))
        {
            // sendOverlayMessage, not displayClientMessage: the action-bar
            // method was renamed in 26.2.
            player.displayClientMessage(
                Component.literal(target.getName().getString() + " is already marked."), true);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(
            Component.literal("The Seal of Orichalcos closes on "
                + target.getName().getString() + "."), true);
        return InteractionResult.SUCCESS;
    }
}
