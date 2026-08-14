package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.clientutil.PlayMats;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSpec;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the world board is made of.
 * <p>
 * The board is a list of rectangles and a transform, both pure, so the thing a
 * player would see can be checked without a GPU. The two properties worth
 * pinning are the ones that would otherwise only show up by looking at it: that
 * no piece of the board lands outside the ground that was validated for the
 * field, and that no two pieces are coplanar -- coplanar quads have no defined
 * winner on a GPU, and the resulting flicker is the most obvious way a world
 * board looks broken.
 */
public class BoardMeshTest
{
    private static final PlayMats[] MATS = {PlayMats.CLASSIC, PlayMats.CLASSIC};

    @Test
    public void theBoardIsTwoPlaymatsAndTheZonesTheyDoNotCover()
    {
        List<BoardMesh.Piece> pieces = BoardMesh.pieces(MATS);

        // Two mats, then seven loose zones each: the extra monster zones, the
        // field spell, and the four piles.
        assertEquals(2 + 2 * 7, pieces.size());
        assertEquals(PlayMats.CLASSIC.texture(), pieces.get(0).texture());
        assertEquals(PlayMats.CLASSIC.texture(), pieces.get(1).texture());
    }

    /**
     * The mats are the floor of the board and everything else stands on them.
     * Equal lifts would be coplanar, which flickers.
     */
    @Test
    public void thePiecesAreStackedInADefinedOrder()
    {
        List<BoardMesh.Piece> pieces = BoardMesh.pieces(MATS);

        assertEquals(0D, pieces.get(0).lift(), 1e-9D);
        assertEquals(0D, pieces.get(1).lift(), 1e-9D);
        for(int piece = 2; piece < pieces.size(); piece++)
        {
            assertTrue(pieces.get(piece).lift() > 0D,
                "a zone square coplanar with the playmat will flicker against it");
        }
        assertTrue(BoardMesh.highlight(0, de.cas_ual_ty.dueldimension.ocg.OcgConstants
            .LOCATION_MZONE, 0).lift() > pieces.get(2).lift(),
            "a lit zone has to sit above the square it lights");
    }

    /** Controller 0's mat is on the half of the board with controller 0's rows. */
    @Test
    public void eachPlaymatIsOnItsOwnControllersHalf()
    {
        List<BoardMesh.Piece> pieces = BoardMesh.pieces(MATS);

        assertTrue(pieces.get(0).rect().y() > 0F, "controller 0's own rows are at positive y");
        assertTrue(pieces.get(1).rect().y() < 0F, "controller 1's are the point reflection");
    }

    /**
     * The whole board has to land on validated ground. A piece outside the mat
     * is a piece outside the footprint the siting checked, which is a board
     * drawn over whatever happened to be there.
     */
    @Test
    public void noPieceOfTheBoardLeavesTheMat()
    {
        for(BoardMesh.Piece piece : BoardMesh.pieces(MATS))
        {
            FieldLayout.Rect rect = piece.rect();
            assertTrue(rect.x() >= FieldLayout.FIELD_MIN_X - 1e-4F
                && rect.x() + rect.w() <= FieldLayout.FIELD_MAX_X + 1e-4F,
                piece.texture() + " runs off the board across: " + rect);
            assertTrue(rect.y() >= FieldLayout.FIELD_MIN_Y - 1e-4F
                && rect.y() + rect.h() <= FieldLayout.FIELD_MAX_Y + 1e-4F,
                piece.texture() + " runs off the board along: " + rect);
        }
    }

    /**
     * The same thing again, but through the transform and in blocks, because
     * that is the form the mistake would actually take: a field resized from
     * the settings screen and a board that no longer fits the ground.
     */
    @Test
    public void theBoardStaysInsideTheValidatedAreaAtEverySize()
    {
        for(FieldSpec spec : new FieldSpec[] {FieldSpec.DEFAULT,
            FieldSpec.fitting(21, 21, 3, 1, 1, 16, 8, 3),
            FieldSpec.fitting(31, 9, 3, 1, 1, 16, 8, 3),
            new FieldSpec(9, 9, 3, 0.35F, 1, 1, 16, 8, 3)})
        {
            for(Direction facing : Direction.Plane.HORIZONTAL)
            {
                FieldSiting siting = new FieldSiting(new BlockPos(0, 63, 0), facing, spec);
                FieldTransform transform = new FieldTransform(siting);
                double halfWidth = spec.areaWidth() / 2D + 0.5D;
                double halfDepth = spec.areaDepth() / 2D + 0.5D;

                for(BoardMesh.Piece piece : BoardMesh.pieces(MATS))
                {
                    for(Vec3 corner : transform.corners(piece.rect(), 0D))
                    {
                        double dx = corner.x - 0.5D;
                        double dz = corner.z - 0.5D;
                        double across = dx * transform.right().getStepX()
                            + dz * transform.right().getStepZ();
                        double along = dx * facing.getStepX() + dz * facing.getStepZ();
                        assertTrue(Math.abs(across) <= halfWidth + 1e-6D,
                            spec + " " + facing + ": a board piece reached " + across);
                        assertTrue(Math.abs(along) <= halfDepth + 1e-6D,
                            spec + " " + facing + ": a board piece reached " + along);
                    }
                }
            }
        }
    }

    @Test
    public void aZoneThatDoesNotExistHasNoSquareToLight()
    {
        assertNotNull(BoardMesh.highlight(0, de.cas_ual_ty.dueldimension.ocg.OcgConstants
            .LOCATION_MZONE, 0));
        assertNull(BoardMesh.highlight(0, de.cas_ual_ty.dueldimension.ocg.OcgConstants
            .LOCATION_HAND, 0), "the hand is never drawn on the board");
    }

    /** A missing playmat is the classic one, not a crash and not a hole. */
    @Test
    public void anAbsentPlaymatFallsBack()
    {
        List<BoardMesh.Piece> pieces = BoardMesh.pieces(new PlayMats[] {null, null});

        assertEquals(PlayMats.CLASSIC.texture(), pieces.get(0).texture());
        assertEquals(PlayMats.CLASSIC.texture(), pieces.get(1).texture());
    }
}
