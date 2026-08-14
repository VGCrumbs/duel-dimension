package de.cas_ual_ty.dueldimension.duel.overworld;

import net.minecraft.core.BlockPos;

/**
 * Whether a sited field is actually buildable where it says it is.
 * <p>
 * Pure: it asks a {@link BlockSampler} and nothing else, so the same code
 * validates a proposal on the server, re-checks it when a player finishes
 * placing a block in phase 16, and runs in a unit test against a hand-written
 * set of occupied blocks.
 * <p>
 * Every check returns a typed {@link Refusal} rather than a boolean, because
 * the caller does different things with different answers -- an obstruction is
 * worth searching around, a different dimension is not -- and because the
 * player is owed a reason.
 */
public final class FieldValidator
{
    /** Why a field cannot be built here. */
    public enum Refusal
    {
        /** The two duellists are not in the same world at all. */
        DIFFERENT_WORLD,
        /** Too far apart to be duelling across a board in the world. */
        TOO_FAR,
        /** Standing on the same block, so there is no line to build a field along. */
        TOO_CLOSE,
        /** Diagonal to one another rather than straight across. */
        NOT_ACROSS,
        /** One duellist is standing well above the other. */
        UNEVEN_ELEVATION,
        /** The ground under the field is missing, or not all at one height. */
        UNEVEN_GROUND,
        /** Something is standing in the space the board needs. */
        OBSTRUCTED,
        /** There is nowhere within the search radius that a field fits. */
        NO_ROOM;

        /** Lang key for the message shown to the players. */
        public String key()
        {
            return "dueldimension.overworld.refusal." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private FieldValidator()
    {
    }

    /**
     * Checks the field itself: flat ground under every cell, air above it, and
     * room for a player to stand at each end.
     *
     * @return null when the field can be built here
     */
    public static Refusal check(BlockSampler sampler, FieldSiting siting)
    {
        FieldSpec spec = siting.spec();
        int floorY = siting.anchor().getY();

        // A mutable holder rather than a stream: forEachFloor is a consumer, and
        // this has to stop at the first bad cell -- a 9x9 area with three blocks
        // of clearance is 324 block reads, and the search calls this hundreds of
        // times.
        Refusal[] found = new Refusal[1];
        siting.forEachFloor(pos ->
        {
            if(found[0] != null)
            {
                return;
            }
            if(!sampler.isGround(pos))
            {
                found[0] = Refusal.UNEVEN_GROUND;
                return;
            }
            for(int dy = 1; dy <= spec.clearance(); dy++)
            {
                if(!sampler.isClear(pos.above(dy)))
                {
                    found[0] = Refusal.OBSTRUCTED;
                    return;
                }
            }
        });
        if(found[0] != null)
        {
            return found[0];
        }

        for(int seat = 0; seat < 2; seat++)
        {
            Refusal standing = checkStand(sampler, siting.stand(seat), floorY);
            if(standing != null)
            {
                return standing;
            }
        }
        return null;
    }

    /**
     * A duellist's own block: flat with the field, solid underfoot, and two
     * blocks of air to stand in. Checked separately from the field because a
     * player stands beside the board, not on it.
     */
    public static Refusal checkStand(BlockSampler sampler, BlockPos stand, int floorY)
    {
        if(stand.getY() != floorY)
        {
            return Refusal.UNEVEN_GROUND;
        }
        if(!sampler.isGround(stand))
        {
            return Refusal.UNEVEN_GROUND;
        }
        // Two blocks: a player who does not fit cannot be locked into position
        // without being suffocated by the lock itself.
        for(int dy = 1; dy <= 2; dy++)
        {
            if(!sampler.isClear(stand.above(dy)))
            {
                return Refusal.OBSTRUCTED;
            }
        }
        return null;
    }

    /**
     * Whether two duellists are standing in a way that describes a field at all,
     * before any block is read: same elevation, straight across, close enough.
     * <p>
     * Separate from {@link #check} because these are answers about the players
     * and those are answers about the world, and phase 3 responds to them
     * differently -- a crooked pair gets a field found for them, a pair a
     * hundred blocks apart gets told no.
     *
     * @return null when the pair could duel where they stand
     */
    public static Refusal checkPair(BlockPos floorA, BlockPos floorB, FieldSpec spec)
    {
        int dx = floorB.getX() - floorA.getX();
        int dz = floorB.getZ() - floorA.getZ();
        if(dx == 0 && dz == 0)
        {
            return Refusal.TOO_CLOSE;
        }
        if(Math.abs(dx) > spec.maxSeparation() || Math.abs(dz) > spec.maxSeparation())
        {
            return Refusal.TOO_FAR;
        }
        if(Math.abs(floorB.getY() - floorA.getY()) > spec.elevationTolerance())
        {
            return Refusal.UNEVEN_ELEVATION;
        }
        // Straight across means the off-axis component is small: the shorter of
        // the two horizontal components is how far from square they are.
        if(Math.min(Math.abs(dx), Math.abs(dz)) > spec.lateralTolerance())
        {
            return Refusal.NOT_ACROSS;
        }
        return null;
    }
}
