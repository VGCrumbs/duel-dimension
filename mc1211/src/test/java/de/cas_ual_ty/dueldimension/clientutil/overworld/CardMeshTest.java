package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSpec;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A card on the world board is a solid, and a solid has to be wound correctly.
 * <p>
 * These are the checks that would otherwise be made by walking round a card in
 * game and squinting: every face points OUT. A face wound the wrong way has its
 * normal pointing into the card, which with culling on means the card is
 * missing a side from exactly one angle -- the kind of fault that survives a
 * dozen looks and then shows up in a screenshot.
 * <p>
 * Everything is measured through {@link FieldTransform} in world space, which
 * is how the renderer sees it, and at all four facings, because the field-to-
 * world mapping flips an axis and a winding that is right for one facing being
 * wrong for another is exactly the mistake to expect.
 */
public class CardMeshTest
{
    private static final FieldLayout.Rect ZONE = new FieldLayout.Rect(1.2F, 0.8F, 1.1F, 1.2F);

    private static FieldTransform transform(Direction facing)
    {
        return new FieldTransform(
            new FieldSiting(new BlockPos(0, 63, 0), facing, FieldSpec.DEFAULT));
    }

    /** A face's four corners in world space, the way the renderer builds them. */
    private static Vec3[] world(FieldTransform transform, CardMesh.Face face)
    {
        Vec3[] corners = new Vec3[4];
        for(int corner = 0; corner < 4; corner++)
        {
            corners[corner] = transform.at(face.x()[corner], face.y()[corner],
                face.height()[corner] * transform.scale());
        }
        return corners;
    }

    /**
     * The same cross product the renderer uses, written independently here:
     * what is being checked is the winding, not the arithmetic.
     */
    private static Vec3 normal(Vec3[] corners)
    {
        Vec3 a = corners[1].subtract(corners[0]);
        Vec3 b = corners[3].subtract(corners[0]);
        return a.cross(b).normalize();
    }

    private static Vec3 centre(Vec3[] corners)
    {
        Vec3 sum = Vec3.ZERO;
        for(Vec3 corner : corners)
        {
            sum = sum.add(corner);
        }
        return sum.scale(0.25D);
    }

    @Test
    public void aCardHasSixFaces()
    {
        List<CardMesh.Face> faces = CardMesh.faces(CardMesh.placement(ZONE, false), 0F);

        assertEquals(6, faces.size(), "front, back and four edges");
        assertEquals(CardMesh.Kind.FRONT, faces.get(0).kind());
        assertEquals(CardMesh.Kind.BACK, faces.get(1).kind());
        for(int face = 2; face < 6; face++)
        {
            assertEquals(CardMesh.Kind.EDGE, faces.get(face).kind());
        }
    }

    @Test
    public void theFrontFacesUpAndTheBackFacesDown()
    {
        for(Direction facing : Direction.Plane.HORIZONTAL)
        {
            FieldTransform transform = transform(facing);
            List<CardMesh.Face> faces = CardMesh.faces(CardMesh.placement(ZONE, false), 0F);

            assertEquals(1D, normal(world(transform, faces.get(0))).y, 1e-6D,
                "facing " + facing + ": the card's face is not pointing up");
            assertEquals(-1D, normal(world(transform, faces.get(1))).y, 1e-6D,
                "facing " + facing + ": the card's back is not pointing down");
        }
    }

    /**
     * The point of building a solid rather than turning culling off: every side
     * points away from the card, so culling stays on and stays correct, and the
     * card is a real object from every angle -- including from underneath,
     * which is where somebody will look.
     */
    @Test
    public void everyFacePointsAwayFromTheCard()
    {
        for(Direction facing : Direction.Plane.HORIZONTAL)
        {
            FieldTransform transform = transform(facing);
            FieldLayout.Rect rect = CardMesh.placement(ZONE, false);
            List<CardMesh.Face> faces = CardMesh.faces(rect, 0F);

            Vec3 middle = transform.at(rect.x() + rect.w() / 2F, rect.y() + rect.h() / 2F,
                CardMesh.THICKNESS / 2F * transform.scale());

            for(CardMesh.Face face : faces)
            {
                Vec3[] corners = world(transform, face);
                Vec3 outward = centre(corners).subtract(middle).normalize();
                double agreement = normal(corners).dot(outward);
                assertTrue(agreement > 0.99D,
                    "facing " + facing + ": the " + face.kind()
                        + " face is wound inwards (normal agreement " + agreement + ")");
            }
        }
    }

    @Test
    public void theEdgesStandBetweenTheTwoFaces()
    {
        FieldTransform transform = transform(Direction.EAST);
        List<CardMesh.Face> faces = CardMesh.faces(CardMesh.placement(ZONE, false), 0F);

        double frontY = world(transform, faces.get(0))[0].y;
        double backY = world(transform, faces.get(1))[0].y;
        assertEquals(CardMesh.THICKNESS * transform.scale(), frontY - backY, 1e-6D,
            "the card has no thickness, so its two faces will z-fight");

        for(int face = 2; face < 6; face++)
        {
            Vec3[] corners = world(transform, faces.get(face));
            double low = Math.min(Math.min(corners[0].y, corners[1].y),
                Math.min(corners[2].y, corners[3].y));
            double high = Math.max(Math.max(corners[0].y, corners[1].y),
                Math.max(corners[2].y, corners[3].y));
            assertEquals(backY, low, 1e-6D, "an edge does not reach the back");
            assertEquals(frontY, high, 1e-6D, "an edge does not reach the front");
            assertEquals(0D, normal(corners).y, 1e-6D, "an edge should point sideways");
        }
    }

    /** A card is drawn at its own proportions, centred, not stretched to its zone. */
    @Test
    public void aCardSitsCentredInItsZoneAtItsOwnShape()
    {
        FieldLayout.Rect upright = CardMesh.placement(ZONE, false);

        assertEquals(CardMesh.CARD_W, upright.w(), 1e-6F);
        assertEquals(CardMesh.CARD_H, upright.h(), 1e-6F);
        assertEquals(ZONE.x() + ZONE.w() / 2F, upright.x() + upright.w() / 2F, 1e-6F);
        assertEquals(ZONE.y() + ZONE.h() / 2F, upright.y() + upright.h() / 2F, 1e-6F);
    }

    /** A defending monster lies on its side, and keeps its centre. */
    @Test
    public void aDefendingCardIsTurnedAQuarter()
    {
        FieldLayout.Rect lying = CardMesh.placement(ZONE, true);

        assertEquals(CardMesh.CARD_H, lying.w(), 1e-6F);
        assertEquals(CardMesh.CARD_W, lying.h(), 1e-6F);
        assertEquals(ZONE.x() + ZONE.w() / 2F, lying.x() + lying.w() / 2F, 1e-6F);
        assertEquals(ZONE.y() + ZONE.h() / 2F, lying.y() + lying.h() / 2F, 1e-6F);
    }

    /** A card raised to sit on a pile takes its whole solid with it. */
    @Test
    public void liftingACardLiftsAllOfIt()
    {
        List<CardMesh.Face> low = CardMesh.faces(CardMesh.placement(ZONE, false), 0F);
        List<CardMesh.Face> high = CardMesh.faces(CardMesh.placement(ZONE, false), 0.5F);

        for(int face = 0; face < 6; face++)
        {
            for(int corner = 0; corner < 4; corner++)
            {
                assertEquals(low.get(face).height()[corner] + 0.5F,
                    high.get(face).height()[corner], 1e-6F);
            }
        }
    }
}
