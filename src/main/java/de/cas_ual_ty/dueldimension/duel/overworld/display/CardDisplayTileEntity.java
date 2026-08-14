package de.cas_ual_ty.dueldimension.duel.overworld.display;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The one card a display block is showing.
 * <p>
 * The first block entity in this mod that keeps anything or tells anyone about
 * it -- the duel block's entity stores nothing at all -- so all four halves of
 * that job are written out here rather than copied from a neighbour:
 * <ul>
 * <li>{@code saveAdditional} / {@code loadAdditional} for the disk;</li>
 * <li>{@code getUpdatePacket} for a change while somebody is watching;</li>
 * <li>{@code getUpdateTag} for somebody who walks into the chunk afterwards.
 * </ul>
 * That last one is the easy one to forget, and forgetting it gives a block that
 * is correct until you look away and come back.
 * <p>
 * A raw passcode and an art index are stored, never a resolved card. The
 * database is loaded asynchronously and can be reloaded under a running world,
 * so a block that remembered a {@code Properties} would be remembering a
 * snapshot of something that has moved on. {@code CardHolder} takes the same
 * view of the same problem.
 */
public class CardDisplayTileEntity extends BlockEntity
{
    private long code;
    private byte art;
    /**
     * One {@code OcgConstants.POS_*} bit -- attack, defence, or face-down
     * defence. A single bit rather than a mask: a card lies one way at a time,
     * and the engine's masks exist to offer a CHOICE between positions.
     */
    private int position = OcgConstants.POS_FACEUP_ATTACK;

    public CardDisplayTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    public long code()
    {
        return code;
    }

    public byte art()
    {
        return art;
    }

    public int position()
    {
        return position;
    }

    /** Is the card lying down? */
    public boolean defence()
    {
        return position == OcgConstants.POS_FACEUP_DEFENSE
            || position == OcgConstants.POS_FACEDOWN_DEFENSE;
    }

    /** Is it face down? */
    public boolean faceDown()
    {
        return position == OcgConstants.POS_FACEDOWN_DEFENSE
            || position == OcgConstants.POS_FACEDOWN_ATTACK;
    }

    /**
     * Puts a card on the block and tells everyone who can see it.
     * <p>
     * {@code setChanged} alone only marks the chunk for saving. The block
     * update is what actually sends it, and without it the card changes for
     * whoever chose it and for nobody else until the chunk reloads.
     */
    public void set(long code, byte art, int position)
    {
        this.code = code;
        this.art = art;
        this.position = position;
        setChanged();
        if(level != null)
        {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putLong("Card", code);
        output.putByte("Art", art);
        output.putInt("Position", position);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        code = input.getLongOr("Card", 0L);
        art = input.getByteOr("Art", (byte)0);
        position = input.getIntOr("Position", OcgConstants.POS_FACEUP_ATTACK);
    }

    /** A change, to anybody already watching this block. */
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** And the whole of it, to anybody who arrives later. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries);
    }
}
