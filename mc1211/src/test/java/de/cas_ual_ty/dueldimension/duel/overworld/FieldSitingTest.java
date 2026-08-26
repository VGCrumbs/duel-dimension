package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldValidator.Refusal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Siting an overworld duel: can these two duel where they stand, and if not,
 * where is the nearest place they can?
 * <p>
 * Every one of these runs against a hand-written world -- a flat plane with
 * holes and obstacles poked into it -- because the whole siting layer is a pure
 * function of {@link BlockSampler}. No server, no level, no GPU, which is the
 * point of that interface existing.
 */
public class FieldSitingTest
{
    private static final int FLOOR = 63;
    private static final FieldSpec SPEC = FieldSpec.DEFAULT;

    /**
     * A flat world at y=63: everything at or below the floor is solid, and
     * everything above it is air, except where a test says otherwise.
     */
    private static final class FlatWorld implements BlockSampler
    {
        private final Set<BlockPos> holes = new HashSet<>();
        private final Set<BlockPos> obstacles = new HashSet<>();

        FlatWorld hole(int x, int z)
        {
            holes.add(new BlockPos(x, FLOOR, z));
            return this;
        }

        FlatWorld obstacle(int x, int y, int z)
        {
            obstacles.add(new BlockPos(x, y, z));
            return this;
        }

        /** A wall of obstacles filling one block level over a whole square region. */
        FlatWorld wall(int minX, int minZ, int maxX, int maxZ, int y)
        {
            for(int x = minX; x <= maxX; x++)
            {
                for(int z = minZ; z <= maxZ; z++)
                {
                    obstacle(x, y, z);
                }
            }
            return this;
        }

        private boolean solid(BlockPos pos)
        {
            if(obstacles.contains(pos))
            {
                return true;
            }
            return pos.getY() <= FLOOR && !holes.contains(pos);
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

    private static BlockPos floor(int x, int z)
    {
        return new BlockPos(x, FLOOR, z);
    }

    // -------------------------------------------------------------- geometry

    @Test
    public void theAreaIsCentredOnItsAnchor()
    {
        FieldSiting siting = new FieldSiting(floor(0, 0), Direction.EAST, SPEC);
        List<BlockPos> cells = new ArrayList<>();
        siting.forEachFloor(cells::add);

        // Measured against the spec rather than against the number it happens
        // to hold. The field has been resized once already and every hard-coded
        // 9 in this file went red at the same moment.
        int expected = SPEC.areaWidth() * SPEC.areaDepth();
        assertEquals(expected, cells.size());
        assertEquals(expected, new HashSet<>(cells).size(), "no block is enumerated twice");
        for(BlockPos cell : cells)
        {
            // Projected onto the FIELD's axes, not the world's. The area is no
            // longer square -- it is wider than it is deep, so the board can be
            // bigger without moving the duellists -- and "x" is across only for
            // one of the four facings.
            int across = FieldFootprint.lateralOffset(floor(0, 0), Direction.EAST, cell);
            int along = FieldFootprint.forwardOffset(floor(0, 0), Direction.EAST, cell);
            assertTrue(Math.abs(across) <= SPEC.halfWidth() && Math.abs(along) <= SPEC.halfDepth(),
                cell + " is outside the area centred on the anchor");
            assertEquals(FLOOR, cell.getY(), "the whole floor is at one height");
        }
    }

    /**
     * Turning the board end for end covers the same ground -- the footprint is
     * symmetric about its anchor on both axes, which is also why the search
     * does not bother trying the opposite facing. A QUARTER turn does not,
     * because the area is wider than it is deep.
     */
    @Test
    public void turningTheBoardEndForEndCoversTheSameGround()
    {
        Set<BlockPos> east = new HashSet<>();
        new FieldSiting(floor(0, 0), Direction.EAST, SPEC).forEachFloor(east::add);
        Set<BlockPos> west = new HashSet<>();
        new FieldSiting(floor(0, 0), Direction.WEST, SPEC).forEachFloor(west::add);
        assertEquals(east, west, "a field and its opposite must cover the same ground");

        Set<BlockPos> north = new HashSet<>();
        new FieldSiting(floor(0, 0), Direction.NORTH, SPEC).forEachFloor(north::add);
        if(SPEC.areaWidth() != SPEC.areaDepth())
        {
            assertNotEquals(east, north,
                "a quarter turn of a field that is not square covers different ground");
        }
    }

    @Test
    public void duellistsStandOutsideTheFieldAndFaceEachOther()
    {
        FieldSiting siting = new FieldSiting(floor(0, 0), Direction.EAST, SPEC);

        int reach = SPEC.halfDepth() + 1;
        assertEquals(floor(-reach, 0), siting.stand(0));
        assertEquals(floor(reach, 0), siting.stand(1));
        assertEquals(SPEC.areaDepth() + 1, SPEC.separation());
        assertFalse(siting.contains(siting.stand(0)), "seat 0 would be standing on the mat");
        assertFalse(siting.contains(siting.stand(1)), "seat 1 would be standing on the mat");
        assertEquals(Direction.EAST, siting.look(0));
        assertEquals(Direction.WEST, siting.look(1));
        assertEquals(0, siting.seatAt(siting.stand(0)));
        assertEquals(1, siting.seatAt(siting.stand(1)));
        assertEquals(-1, siting.seatAt(siting.anchor()));
    }

    @Test
    public void containsCoversTheFloorAndTheAirTheBoardNeeds()
    {
        FieldSiting siting = new FieldSiting(floor(0, 0), Direction.NORTH, SPEC);

        assertTrue(siting.contains(floor(0, 0)), "the anchor is in its own field");
        assertTrue(siting.contains(floor(SPEC.halfWidth(), SPEC.halfDepth())),
            "a corner is in the field");
        assertTrue(siting.contains(floor(0, 0).above(SPEC.clearance())),
            "the top of the clearance is in the field");
        assertFalse(siting.contains(floor(SPEC.halfWidth() + 1, 0)),
            "one block past the edge is outside");
        assertFalse(siting.contains(floor(0, 0).above(SPEC.clearance() + 1)),
            "above the clearance is outside");
        assertFalse(siting.contains(floor(0, 0).below()), "under the floor is outside");
    }

    /**
     * The mat is EDOPro's ten-by-eight field, so a nine-block area pins the
     * scale. If this drifts, the board is being drawn to a different size than
     * the ground that was validated for it.
     */
    @Test
    public void theMatFitsTheAreaItWasValidatedFor()
    {
        assertEquals(FieldSpec.fittingScale(SPEC.areaWidth(), SPEC.areaDepth()),
            SPEC.blocksPerFieldUnit(), 1e-6F, "the default board fills the area it asks for");
        assertTrue(SPEC.matWidth() <= SPEC.areaWidth(),
            "a board wider than its own validated ground");
        assertTrue(SPEC.matDepth() < SPEC.areaDepth(),
            "the mat has to leave the duellists somewhere to stand");
    }

    // ---------------------------------------------------------------- siting

    @Test
    public void twoDuellistsStandingRightBeginWhereTheyAre()
    {
        int apart = SPEC.separation();
        SitingResult result = SitingSearch.site(new FlatWorld(), floor(0, 0), floor(apart, 0),
            SPEC);

        SitingResult.Ready ready = assertInstanceOf(SitingResult.Ready.class, result,
            "flat ground, exactly a field apart, straight across: nobody should have to move");
        assertEquals(floor(apart / 2, 0), ready.siting().anchor());
        assertEquals(Direction.EAST, ready.siting().facing());
        assertEquals(floor(0, 0), ready.siting().stand(0));
        assertEquals(floor(apart, 0), ready.siting().stand(1));
    }

    @Test
    public void duellistsStandingTooCloseAreAskedToStepBack()
    {
        SitingResult result = SitingSearch.site(new FlatWorld(), floor(0, 0), floor(6, 0), SPEC);

        SitingResult.Move move = assertInstanceOf(SitingResult.Move.class, result);
        assertEquals(floor(3, 0), move.siting().anchor(), "the field still sits between them");
        int reach = SPEC.halfDepth() + 1;
        assertEquals(floor(3 - reach, 0), move.siting().stand(0));
        assertEquals(floor(3 + reach, 0), move.siting().stand(1));
    }

    @Test
    public void duellistsStandingDiagonallyAreGivenAFieldRatherThanRefused()
    {
        assertEquals(Refusal.NOT_ACROSS,
            FieldValidator.checkPair(floor(0, 0), floor(8, 6), SPEC),
            "six blocks off the axis is not straight across");

        SitingResult result = SitingSearch.site(new FlatWorld(), floor(0, 0), floor(8, 6), SPEC);

        SitingResult.Move move = assertInstanceOf(SitingResult.Move.class, result,
            "there is nothing wrong with the ground, so they should be moved, not refused");
        assertNull(FieldValidator.check(new FlatWorld(), move.siting()));
    }

    @Test
    public void duellistsOnDifferentLevelsAreGivenFlatGround()
    {
        assertEquals(Refusal.UNEVEN_ELEVATION,
            FieldValidator.checkPair(floor(0, 0), new BlockPos(10, FLOOR + 3, 0), SPEC));

        SitingResult result = SitingSearch.site(new FlatWorld(), floor(0, 0),
            new BlockPos(10, FLOOR + 3, 0), SPEC);

        SitingResult.Move move = assertInstanceOf(SitingResult.Move.class, result);
        assertEquals(FLOOR, move.siting().anchor().getY(), "the field lands on the real floor");
    }

    @Test
    public void duellistsFarApartAreRefusedRatherThanMarchedAcrossTheMap()
    {
        SitingResult result = SitingSearch.site(new FlatWorld(), floor(0, 0), floor(40, 0), SPEC);

        assertEquals(new SitingResult.Refused(Refusal.TOO_FAR), result);
        assertNull(result.siting(), "a refusal has no field");
    }

    @Test
    public void duellistsOnTheSameBlockAreRefused()
    {
        assertEquals(new SitingResult.Refused(Refusal.TOO_CLOSE),
            SitingSearch.site(new FlatWorld(), floor(0, 0), floor(0, 0), SPEC));
    }

    // ------------------------------------------------------------ the ground

    @Test
    public void aPillarInTheFieldMovesTheDuelAsideRatherThanCancellingIt()
    {
        // Right on the centre line, so the field as the two of them stand
        // cannot be built and something has to give.
        FlatWorld world = new FlatWorld().obstacle(5, FLOOR + 1, 0);
        assertEquals(Refusal.OBSTRUCTED,
            FieldValidator.check(world, new FieldSiting(floor(5, 0), Direction.EAST, SPEC)));

        SitingResult result = SitingSearch.site(world, floor(0, 0), floor(10, 0), SPEC);

        SitingResult.Move move = assertInstanceOf(SitingResult.Move.class, result);
        assertFalse(move.siting().contains(new BlockPos(5, FLOOR + 1, 0)),
            "the field was moved but still swallows the pillar");
        assertNull(FieldValidator.check(world, move.siting()));
    }

    @Test
    public void aHoleInTheFloorInvalidatesTheField()
    {
        FlatWorld world = new FlatWorld().hole(3, 1);

        assertEquals(Refusal.UNEVEN_GROUND,
            FieldValidator.check(world, new FieldSiting(floor(5, 0), Direction.EAST, SPEC)));
    }

    @Test
    public void aCeilingTooLowForTheBoardIsAnObstruction()
    {
        FlatWorld world = new FlatWorld().wall(-20, -20, 20, 20, FLOOR + SPEC.clearance());

        assertEquals(Refusal.OBSTRUCTED,
            FieldValidator.check(world, new FieldSiting(floor(0, 0), Direction.EAST, SPEC)));
    }

    @Test
    public void aCeilingAboveTheClearanceIsFine()
    {
        FlatWorld world = new FlatWorld().wall(-20, -20, 20, 20, FLOOR + SPEC.clearance() + 1);

        assertNull(FieldValidator.check(world, new FieldSiting(floor(0, 0), Direction.EAST, SPEC)));
    }

    @Test
    public void aRoomTooLowForTheBoardIsRefusedRatherThanSitedBadly()
    {
        // A ceiling a player fits under but the board does not, everywhere the
        // search can reach. Note the ceiling is not itself an escape: floorAt
        // takes the floor they are standing on before it considers climbing.
        FlatWorld world = new FlatWorld().wall(-64, -64, 64, 64, FLOOR + SPEC.clearance());

        SitingResult result = SitingSearch.site(world, floor(0, 0), floor(10, 0), SPEC);

        // OBSTRUCTED rather than NO_ROOM: both are true, and only one of them
        // tells the player to look up.
        assertEquals(new SitingResult.Refused(Refusal.OBSTRUCTED), result);
    }

    @Test
    public void aDuellistNeedsHeadroomOfTheirOwn()
    {
        // The field is clear; the block over seat 1's feet is not.
        FlatWorld world = new FlatWorld().obstacle(10, FLOOR + 2, 0);
        FieldSiting siting = new FieldSiting(floor(5, 0), Direction.EAST, SPEC);

        assertEquals(Refusal.OBSTRUCTED, FieldValidator.check(world, siting));
    }

    // ---------------------------------------------------------- determinism

    @Test
    public void theSearchTakesTheNearestFieldThatActuallyClearsTheObstacle()
    {
        // One pillar, and the field is nine wide: shuffling along by a block
        // still swallows it. The nearest field that does NOT is five away,
        // which is the whole reason the search exists rather than a nudge.
        FlatWorld world = new FlatWorld().obstacle(5, FLOOR + 1, 0);

        FieldSiting found = SitingSearch.search(world, floor(5, 0), Direction.EAST, SPEC);

        assertNotNull(found);
        assertEquals(Direction.EAST, found.facing(), "the board should not have been turned");
        assertFalse(found.contains(new BlockPos(5, FLOOR + 1, 0)));
        // The pillar sits on the facing axis, so the field escapes it along
        // that axis first -- half its DEPTH away, which is the shorter of the
        // two now that the area is not square.
        int moved = Math.max(Math.abs(found.anchor().getX() - 5), Math.abs(found.anchor().getZ()));
        assertEquals(SPEC.halfDepth() + 1, moved,
            "a field only clears a pillar once it is half its own extent away");
    }

    /**
     * Nearest means nearest as the player sees it, not nearest in whatever
     * shape the loop happened to walk: a square ring's corner is further away
     * than the next ring's edge, so a ring walk would take the corner first.
     * <p>
     * Proved rather than asserted as a number. The nearest VALID spot is not
     * something to work out by hand -- at five blocks the field clears the
     * pillar but a duellist's own stand block lands in its column -- so the
     * test finds the best answer by brute force and insists the search found
     * one just as good.
     */
    @Test
    public void theSearchMeasuresDistanceHonestly()
    {
        FlatWorld world = new FlatWorld().obstacle(5, FLOOR + 1, 0);
        BlockPos around = floor(5, 0);

        FieldSiting found = SitingSearch.search(world, around, Direction.EAST, SPEC);
        assertNotNull(found);

        int best = Integer.MAX_VALUE;
        for(int dx = -SPEC.searchRadius(); dx <= SPEC.searchRadius(); dx++)
        {
            for(int dz = -SPEC.searchRadius(); dz <= SPEC.searchRadius(); dz++)
            {
                FieldSiting candidate = new FieldSiting(around.offset(dx, 0, dz),
                    Direction.EAST, SPEC);
                if(FieldValidator.check(world, candidate) == null)
                {
                    best = Math.min(best, dx * dx + dz * dz);
                }
            }
        }

        int dx = found.anchor().getX() - around.getX();
        int dz = found.anchor().getZ() - around.getZ();
        assertEquals(best, dx * dx + dz * dz,
            "a nearer valid field existed and the search walked past it");
    }

    @Test
    public void theFacingIsTheAxisTheyAreFurtherApartOn()
    {
        assertEquals(Direction.EAST, SitingSearch.facing(floor(0, 0), floor(10, 1)));
        assertEquals(Direction.WEST, SitingSearch.facing(floor(0, 0), floor(-10, 1)));
        assertEquals(Direction.SOUTH, SitingSearch.facing(floor(0, 0), floor(1, 10)));
        assertEquals(Direction.NORTH, SitingSearch.facing(floor(0, 0), floor(1, -10)));
    }

    @Test
    public void aFieldCannotStandOnItsEdge()
    {
        try
        {
            new FieldSiting(floor(0, 0), Direction.UP, SPEC);
            org.junit.jupiter.api.Assertions.fail("a vertical field has no floor");
        }
        catch(IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("UP") || expected.getMessage().contains("up"));
        }
    }

    @Test
    public void flippingAFieldSwapsTheEndsAndNothingElse()
    {
        FieldSiting siting = new FieldSiting(floor(0, 0), Direction.EAST, SPEC);
        FieldSiting flipped = siting.flipped();

        assertEquals(siting.anchor(), flipped.anchor());
        assertSame(siting.spec(), flipped.spec());
        assertEquals(siting.stand(0), flipped.stand(1));
        assertEquals(siting.stand(1), flipped.stand(0));
    }
}
