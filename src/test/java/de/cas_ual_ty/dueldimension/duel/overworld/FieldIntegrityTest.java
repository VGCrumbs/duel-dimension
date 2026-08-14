package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldValidator.Refusal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What pulls a running board down, and what does not.
 * <p>
 * A board re-checks its ground once a second with the SAME validator that
 * accepted the site, so this is that contract written out: the disturbances
 * that end an overworld duel's presentation, and -- just as important -- the
 * ones that must not, because a board that fell back whenever a neighbour laid
 * a path nearby would be a board nobody used.
 * <p>
 * Falling back is never a loss. The engine is the authority on the duel and the
 * presentation is not part of the rules, which is what stops somebody with a
 * stack of dirt from deciding a match.
 */
public class FieldIntegrityTest
{
    private static final int FLOOR = 63;
    private static final FieldSpec SPEC = FieldSpec.DEFAULT;
    private static final FieldSiting SITING =
        new FieldSiting(new BlockPos(0, FLOOR, 0), Direction.EAST, SPEC);

    /** A flat world with whatever a test has done to it since. */
    private static final class World implements BlockSampler
    {
        private final Set<BlockPos> added = new HashSet<>();
        private final Set<BlockPos> dug = new HashSet<>();

        World place(int x, int y, int z)
        {
            added.add(new BlockPos(x, y, z));
            return this;
        }

        World dig(int x, int z)
        {
            dug.add(new BlockPos(x, FLOOR, z));
            return this;
        }

        private boolean solid(BlockPos pos)
        {
            return added.contains(pos) || (pos.getY() <= FLOOR && !dug.contains(pos));
        }

        @Override
        public boolean isClear(BlockPos pos)
        {
            return !solid(pos);
        }

        @Override
        public boolean isGround(BlockPos pos)
        {
            return solid(pos);
        }
    }

    @Test
    public void anUndisturbedFieldStaysValid()
    {
        assertNull(FieldValidator.check(new World(), SITING));
    }

    @Test
    public void aBlockPlacedInTheFieldEndsIt()
    {
        assertEquals(Refusal.OBSTRUCTED,
            FieldValidator.check(new World().place(2, FLOOR + 1, -1), SITING));
    }

    @Test
    public void diggingTheFloorOutFromUnderItEndsIt()
    {
        assertEquals(Refusal.UNEVEN_GROUND,
            FieldValidator.check(new World().dig(-3, 2), SITING));
    }

    /** Building on a duellist's head counts, because they cannot move away from it. */
    @Test
    public void blockingADuellistsHeadroomEndsIt()
    {
        BlockPos stand = SITING.stand(1);

        assertEquals(Refusal.OBSTRUCTED, FieldValidator.check(
            new World().place(stand.getX(), stand.getY() + 2, stand.getZ()), SITING));
    }

    /**
     * The other half of the contract. A duel that fell back because somebody
     * was building next door would be a duel nobody could finish in a town.
     */
    @Test
    public void buildingBesideTheFieldIsNotADisturbance()
    {
        assertNull(FieldValidator.check(new World().place(5, FLOOR + 1, 5), SITING),
            "one block past the edge is somebody else's business");
        assertNull(FieldValidator.check(new World().place(0, FLOOR + SPEC.clearance() + 1, 0),
            SITING), "above the headroom the field asked for");
        assertNull(FieldValidator.check(new World().dig(6, 0), SITING),
            "digging outside the footprint");
    }

    /**
     * The check is the same call that accepted the site, so a board can never
     * be pulled down for failing something it never passed.
     */
    @Test
    public void theCheckIsTheSameOneThatAcceptedTheSite()
    {
        World world = new World();
        SitingResult result = SitingSearch.site(world, SITING.stand(0), SITING.stand(1), SPEC);

        assertEquals(new SitingResult.Ready(SITING), result);
        assertNull(FieldValidator.check(world, result.siting()));
    }
}
