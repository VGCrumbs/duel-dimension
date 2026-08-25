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
 * A pile of cards stands as tall as the cards in it.
 * <p>
 * Drawn as one solid rather than as a card apiece -- forty cards would be forty
 * solids and two hundred and forty quads for something whose inside is never
 * seen -- so the two things worth pinning are that the one solid is the right
 * height, and that a pile of one card is still thick enough to see.
 */
public class PileMeshTest
{
    private static final FieldLayout.Rect ZONE = new FieldLayout.Rect(6.9F, 2.7F, 1.1F, 1.2F);

    @Test
    public void aPileGrowsWithItsCards()
    {
        assertEquals(0F, PileMesh.height(0), 1e-6F);
        assertEquals(PileMesh.LIFT_PER_CARD, PileMesh.height(1), 1e-6F);
        assertEquals(0.4F, PileMesh.height(40), 1e-6F, "EDOPro's 0.01 a card, unflattened");
    }

    /**
     * A deck of sixty would otherwise stand half a block proud of the board and
     * put its own top card out of reach of somebody looking down at it.
     */
    @Test
    public void aPileStopsGrowingEventually()
    {
        assertEquals(PileMesh.height(PileMesh.LIFT_CAP), PileMesh.height(60), 1e-6F);
        assertEquals(PileMesh.LIFT_CAP, PileMesh.stripes(60));
    }

    /** One stripe per card, and never none: a single card still has an edge. */
    @Test
    public void thereIsAStripeForEveryCard()
    {
        assertEquals(1, PileMesh.stripes(0));
        assertEquals(1, PileMesh.stripes(1));
        assertEquals(40, PileMesh.stripes(40));
    }

    @Test
    public void aPileIsOneSolidHoweverManyCardsAreInIt()
    {
        assertEquals(6, CardMesh.faces(ZONE, 0F, PileMesh.height(1)).size());
        assertEquals(6, CardMesh.faces(ZONE, 0F, PileMesh.height(40)).size());
    }

    /**
     * A one-card pile is thinner than a card, which would put its two faces in
     * the same plane. The card's own thickness is the floor.
     */
    @Test
    public void aPileIsNeverThinnerThanACard()
    {
        FieldTransform transform = new FieldTransform(
            new FieldSiting(new BlockPos(0, 63, 0), Direction.EAST, FieldSpec.DEFAULT));
        List<CardMesh.Face> faces = CardMesh.faces(ZONE, 0F, PileMesh.height(1));

        double top = transform.at(faces.get(0).x()[0], faces.get(0).y()[0],
            faces.get(0).height()[0] * transform.scale()).y;
        double bottom = transform.at(faces.get(1).x()[0], faces.get(1).y()[0],
            faces.get(1).height()[0] * transform.scale()).y;

        assertTrue(top - bottom >= CardMesh.THICKNESS * transform.scale() - 1e-9D,
            "a one-card pile has collapsed to nothing");
    }

    /** A tall pile really is taller in the world, not just in field units. */
    @Test
    public void aTallPileIsTallerInTheWorld()
    {
        FieldTransform transform = new FieldTransform(
            new FieldSiting(new BlockPos(0, 63, 0), Direction.SOUTH, FieldSpec.DEFAULT));

        assertTrue(topOf(transform, 40) > topOf(transform, 5));
        assertEquals(PileMesh.height(40) * transform.scale(),
            topOf(transform, 40) - transform.surfaceY(), 1e-6D);
    }

    private static double topOf(FieldTransform transform, int count)
    {
        CardMesh.Face front = CardMesh.faces(ZONE, 0F, PileMesh.height(count)).get(0);
        Vec3 corner = transform.at(front.x()[0], front.y()[0],
            front.height()[0] * transform.scale());
        return corner.y;
    }
}
