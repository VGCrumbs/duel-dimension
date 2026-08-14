package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

/**
 * One sited duel field: where the board is, which way it faces, and how big it
 * is. This travels from the server to both clients and is the whole description
 * of an overworld duel's place in the world.
 * <p>
 * It carries the {@link FieldSpec} rather than assuming the default, so a board
 * that was validated against one set of dimensions is drawn against those same
 * dimensions even if the default later changes -- a running duel is never
 * resized underneath its players.
 *
 * @param anchor the FLOOR block at the centre of the area; the board's surface
 *               is the top face of this block
 * @param facing from seat 0's feet towards seat 1's
 * @param seatA  where seat 0 stands, when somebody has MARKED where that is;
 *               null for a field the game sited itself, which works its stands
 *               out from the footprint
 * @param seatB  the same for seat 1
 */
public record FieldSiting(BlockPos anchor, Direction facing, FieldSpec spec, BlockPos seatA,
    BlockPos seatB)
{
    /** A field the game found for itself, whose stands follow from its shape. */
    public FieldSiting(BlockPos anchor, Direction facing, FieldSpec spec)
    {
        this(anchor, facing, spec, null, null);
    }

    /**
     * Was this arena built by hand rather than found?
     * <p>
     * Worth asking because the two are trusted differently. A field the game
     * sited is one it proved the ground for, and it keeps proving it every
     * second in case somebody builds into it. A field somebody MARKED OUT is a
     * statement of intent -- the floor under it is whatever they wanted there,
     * and re-validating it would abandon a duel for the crime of being held
     * somewhere decorated.
     */
    public boolean marked()
    {
        return seatA != null && seatB != null;
    }
    /**
     * Only the four compass directions make a field: {@link Direction#UP} would
     * give a board standing on its edge and a footprint with no floor.
     */
    public FieldSiting
    {
        if(facing.getAxis().isVertical())
        {
            throw new IllegalArgumentException("a duel field faces along the ground: " + facing);
        }
    }

    /** The block the given seat stands on. */
    public BlockPos stand(int seat)
    {
        BlockPos marked = seat == 0 ? seatA : seatB;
        return marked != null ? marked : FieldFootprint.stand(anchor, facing, spec, seat);
    }

    /** Which way the given seat looks: at the board, and at the other duellist. */
    public Direction look(int seat)
    {
        return FieldFootprint.look(facing, seat);
    }

    /** Every floor block of the area, in the footprint's fixed order. */
    public void forEachFloor(Consumer<BlockPos> cells)
    {
        FieldFootprint.forEachFloor(anchor, facing, spec, cells);
    }

    /** The floor blocks of one seat's edge row, where its marker is drawn. */
    public void forEachEdge(int seat, Consumer<BlockPos> cells)
    {
        FieldFootprint.forEachEdge(anchor, facing, spec, seat, cells);
    }

    /** Is this position inside the field's floor or the air above it? */
    public boolean contains(BlockPos pos)
    {
        return FieldFootprint.contains(anchor, facing, spec, pos);
    }

    /** Which seat, if any, stands on this block; -1 for neither. */
    public int seatAt(BlockPos pos)
    {
        if(stand(0).equals(pos))
        {
            return 0;
        }
        return stand(1).equals(pos) ? 1 : -1;
    }

    /** The same field seen from the other end, which is how seat 1 is served seat 0's board. */
    public FieldSiting flipped()
    {
        return new FieldSiting(anchor, facing.getOpposite(), spec);
    }
}
