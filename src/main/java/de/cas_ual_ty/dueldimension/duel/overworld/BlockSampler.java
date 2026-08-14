package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;

/**
 * The two questions the siting code asks about a block, and the only two.
 * <p>
 * Everything above this interface -- the footprint, the validator, the search --
 * is a pure function of it, which is what makes phases 1 to 3 testable without a
 * server, a world or a GPU. {@link LevelSampler} is the single adapter that
 * actually reads a {@code Level}; a test hands in a set of occupied positions
 * instead.
 * <p>
 * This is also the seam the phase-16 integrity check reuses: "did someone build
 * in the field" is the same question as "was the field clear", asked later.
 */
public interface BlockSampler
{
    /**
     * Is this block free of anything the board or a duellist would collide
     * with? Air is clear; so is grass; a fence, a chest or water is not.
     */
    boolean isClear(BlockPos pos);

    /**
     * Can the board rest on this block's top face, and can a player stand on
     * it? A full block yes; a slab in its lower half yes; a torch or air no.
     */
    boolean isGround(BlockPos pos);
}
