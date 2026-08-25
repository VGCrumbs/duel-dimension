package de.cas_ual_ty.dueldimension.duel.overworld.arena;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * The marker in the hand, which only a creative-mode builder may put down.
 * <p>
 * Living in the creative menu is nearly enough on its own -- that is all
 * barrier does -- but nearly is not the same as is: an item can be handed over
 * by a command, dropped by a builder switching to survival, or left in a chest.
 * This asks at the moment of placing instead, which is the only moment the
 * answer matters, and gives the same answer every time regardless of how the
 * stack got where it is.
 * <p>
 * Refused rather than consumed: a survival player right-clicking with one gets
 * nothing at all rather than losing the item to a block that never appeared.
 */
public class ArenaMarkerItem extends BlockItem
{
    public ArenaMarkerItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        return allowed(context.getPlayer()) ? super.useOn(context) : InteractionResult.PASS;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context,
        net.minecraft.world.level.block.state.BlockState state)
    {
        // Both doors, because useOn is not the only way in: dispensers and any
        // other route to a placement arrive here instead, and an arena wall
        // that can be built by a machine is an arena wall that can be built by
        // accident.
        return allowed(context.getPlayer()) && super.placeBlock(context, state);
    }

    private static boolean allowed(Player player)
    {
        return player != null && player.isCreative();
    }
}
