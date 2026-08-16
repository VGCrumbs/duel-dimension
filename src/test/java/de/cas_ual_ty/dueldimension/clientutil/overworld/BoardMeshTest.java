package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
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

        // Two mats, then seven loose zones each -- the extra monster zones,
        // the field spell, and the four piles -- and then the two pendulum
        // marks each, which sit ON the playmat band rather than outside it.
        assertEquals(2 + 2 * 7 + 2 * 2, pieces.size());
        assertEquals(PlayMats.CLASSIC.worldTexture(), pieces.get(0).texture());
        assertEquals(PlayMats.CLASSIC.worldTexture(), pieces.get(1).texture());
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
            new FieldSpec(9, 9, 3, 0.35F, 0F, 1, 1, 16, 8, 3)})
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

        assertEquals(PlayMats.CLASSIC.worldTexture(), pieces.get(0).texture());
        assertEquals(PlayMats.CLASSIC.worldTexture(), pieces.get(1).texture());
    }

    /**
     * The two backrow squares that are also Pendulum Zones say so.
     * <p>
     * Under MR5 ocgcore puts a scale in backrow 0 or 4 rather than in a zone of
     * its own, and a playmat is not printed with anything that says which two
     * those are. The flat board has marked them since it was written; this is
     * the same mark on the same squares.
     */
    @Test
    public void bothPendulumZonesAreMarkedOnEachHalf()
    {
        List<BoardMesh.Piece> pieces = BoardMesh.pieces(MATS);

        int blue = 0;
        int red = 0;
        for(BoardMesh.Piece piece : pieces)
        {
            if(piece.texture().equals(DuelTextures.PENDULUM_ZONE_LEFT))
            {
                blue++;
            }
            else if(piece.texture().equals(DuelTextures.PENDULUM_ZONE_RIGHT))
            {
                red++;
            }
        }
        assertEquals(2, blue, "one blue gem per duellist");
        assertEquals(2, red, "one red gem per duellist");
    }

    /**
     * Each mark sits inside the zone it labels, square, and smaller than it.
     * <p>
     * A mark that overhung its square would be a mark on the neighbouring zone
     * as well, and one that filled it would be a zone that looked occupied.
     */
    @Test
    public void aPendulumMarkIsASquareInsideItsOwnZone()
    {
        for(int controller = 0; controller <= 1; controller++)
        {
            for(int sequence : new int[] {0, 4})
            {
                FieldLayout.Rect zone = FieldLayout.zone(controller,
                    de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE, sequence);
                BoardMesh.Piece mark = markIn(zone);
                assertTrue(mark != null,
                    "controller " + controller + " backrow " + sequence + " is unmarked");
                assertEquals(mark.rect().w(), mark.rect().h(), 1e-4F, "the gem should be square");
                assertTrue(mark.rect().w() < zone.w() && mark.rect().h() < zone.h(),
                    "the mark should not fill its zone");
                // Centred, so it reads as a label for the square rather than
                // as something sitting in a corner of it.
                assertEquals(zone.x() + zone.w() / 2F,
                    mark.rect().x() + mark.rect().w() / 2F, 1e-4F);
                assertEquals(zone.y() + zone.h() / 2F,
                    mark.rect().y() + mark.rect().h() / 2F, 1e-4F);
            }
        }
    }

    /**
     * A duellist's own left zone takes the blue gem, whichever half they are on.
     * <p>
     * Sequence rather than screen side: the opponent's half is the point
     * reflection of yours, so their sequence 0 is physically opposite yours.
     * Both of them are still that duellist's left, which is what a Pendulum
     * card prints blue.
     */
    @Test
    public void theBlueGemIsAlwaysItsOwnersLeftHandZone()
    {
        for(int controller = 0; controller <= 1; controller++)
        {
            BoardMesh.Piece left = markIn(FieldLayout.zone(controller,
                de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE, 0));
            BoardMesh.Piece right = markIn(FieldLayout.zone(controller,
                de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE, 4));
            assertEquals(DuelTextures.PENDULUM_ZONE_LEFT, left.texture());
            assertEquals(DuelTextures.PENDULUM_ZONE_RIGHT, right.texture());
            // And turned to stand up for its owner, the way their cards are.
            assertEquals(FieldLayout.turnsFor(controller, false), left.turns());
            assertEquals(FieldLayout.turnsFor(controller, false), right.turns());
        }
    }

    /** The mark drawn inside this zone, or null. */
    private static BoardMesh.Piece markIn(FieldLayout.Rect zone)
    {
        for(BoardMesh.Piece piece : BoardMesh.pieces(MATS))
        {
            if(!piece.texture().equals(DuelTextures.PENDULUM_ZONE_LEFT)
                && !piece.texture().equals(DuelTextures.PENDULUM_ZONE_RIGHT))
            {
                continue;
            }
            FieldLayout.Rect rect = piece.rect();
            if(rect.x() >= zone.x() - 1e-4F && rect.y() >= zone.y() - 1e-4F
                && rect.x() + rect.w() <= zone.x() + zone.w() + 1e-4F
                && rect.y() + rect.h() <= zone.y() + zone.h() + 1e-4F)
            {
                return piece;
            }
        }
        return null;
    }
}
