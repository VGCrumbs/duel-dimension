package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The cards standing on a worn duel disk.
 * <p>
 * Draws one flat quad per OCCUPIED zone, in the placeholder boxes {@link
 * DiskCardSlots} carries over from the model. An empty zone draws nothing at
 * all — the disk shows a bare plate there, which is what a disk with nothing in
 * that zone looks like.
 * <p>
 * <b>It cannot show what it was not told.</b> The face of every card comes from
 * {@link CardFaces#face}, the same policy the duel field uses: a face-down card
 * is drawn as its back, and a card this client was never sent the identity of
 * has no identity to draw. Wearing the disk is not a way to look at a set card,
 * neither your own nor anybody else's.
 */
public class DiskCardsRenderer implements SpecialModelRenderer<DiskCardsRenderer.Rack>
{
    /**
     * A quarter turn for a monster in defence.
     * <p>
     * Negative is clockwise seen from above, which is the way a card is turned
     * on a real table. If it ever wants to be the other way it is this constant
     * and nothing else.
     */
    private static final float DEFENCE_TURN = -90F;

    /**
     * One card on the disk: which zone it stands in, what it shows, and whether
     * it is lying sideways.
     *
     * @param zone   sequence within its row, indexing {@link DiskCardSlots}
     * @param turned a monster set or in defence position, so turned a quarter
     *               turn. Never true for a spell or trap: those cannot be in
     *               defence position, so they are never sideways
     */
    public record Placed(int zone, ResourceLocation face, boolean turned)
    {
    }

    /**
     * Everything the disk is showing.
     * <p>
     * Only the zones that hold something are listed. That is the "hide each card
     * model when there is no card there" rule expressed in the data rather than
     * as a branch in the renderer — an empty rack is an empty list and draws
     * nothing.
     */
    public record Rack(List<Placed> monsters, List<Placed> spells)
    {
        public static final Rack EMPTY = new Rack(List.of(), List.of());

        public boolean isEmpty()
        {
            return monsters.isEmpty() && spells.isEmpty();
        }
    }

    /**
     * The rack a player's own side of the board makes.
     * <p>
     * Reads only the first five of each row. A duel has seven monster zones and
     * eight spell zones — the extra monster zones, the field zone and the two
     * pendulum zones — and the disk has five boxes per row, because that is what
     * a duel disk has. Zones with no box are not drawn rather than being crammed
     * into somebody else's.
     */
    public static Rack rackFor(BoardSnapshot.Side side, int controller)
    {
        // inHand is false: these are cards on the table, so a set one shows its
        // back even to the player who set it.
        return rackFor(side, DiskCardSlots.MONSTERS.size(), DiskCardSlots.SPELLS.size(),
            slot -> CardFaces.face(slot, false, controller));
    }

    /**
     * What each slot is showing.
     * <p>
     * A seam, so the zone and posture rules below can be pinned without a
     * running game: resolving a real face reaches {@link CardFaces} and through
     * it the card database and the texture manager, none of which exist in a
     * test.
     */
    public interface Faces
    {
        ResourceLocation of(BoardSnapshot.Slot slot);
    }

    /** As above, against a given number of boxes and a given face policy. */
    public static Rack rackFor(BoardSnapshot.Side side, int monsterBoxes, int spellBoxes,
        Faces faces)
    {
        if(side == null)
        {
            return Rack.EMPTY;
        }
        return new Rack(row(side.monsters(), monsterBoxes, true, faces),
            row(side.spells(), spellBoxes, false, faces));
    }

    private static List<Placed> row(List<BoardSnapshot.Slot> zones, int boxes, boolean monsters,
        Faces faces)
    {
        List<Placed> out = new ArrayList<>();
        for(int zone = 0; zone < Math.min(boxes, zones.size()); zone++)
        {
            BoardSnapshot.Slot slot = zones.get(zone);
            if(slot == null || !slot.present())
            {
                continue;
            }
            // A monster is turned when it is in defence, which covers a set one
            // too -- set IS face-down defence. A spell or trap never is: it
            // cannot be in defence position, so it is never sideways, however
            // it is placed.
            out.add(new Placed(zone, faces.of(slot), monsters && slot.defence()));
        }
        return List.copyOf(out);
    }

    @Override
    public void submit(Rack rack, PoseStack pose, SubmitNodeCollector collector,
        int light, int overlay, boolean glint, int outlineColor)
    {
        if(rack == null)
        {
            return;
        }
        draw(rack.monsters(), DiskCardSlots.MONSTERS, pose, collector);
        draw(rack.spells(), DiskCardSlots.SPELLS, pose, collector);
    }

    private static void draw(List<Placed> cards, List<DiskCardSlots.Slot> boxes,
        PoseStack pose, SubmitNodeCollector collector)
    {
        for(Placed card : cards)
        {
            if(card.zone() < 0 || card.zone() >= boxes.size())
            {
                continue;
            }
            DiskCardSlots.Slot box = boxes.get(card.zone());

            FieldQuad.Corners3D corners = corners(box, card.turned());
            CardFaces.Window window = CardFaces.window(card.face());
            FieldQuad.draw3D(pose, collector, card.face(), corners,
                window.u0(), window.v0(), window.u1(), window.v1(), -1);
        }
    }

    private static FieldQuad.Corners3D corners(DiskCardSlots.Slot box, boolean turned)
    {
        DiskCardSlots.Point a = place(box, box.topLeft(), turned);
        DiskCardSlots.Point b = place(box, box.topRight(), turned);
        DiskCardSlots.Point c = place(box, box.bottomRight(), turned);
        DiskCardSlots.Point d = place(box, box.bottomLeft(), turned);
        return new FieldQuad.Corners3D(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(),
            c.x(), c.y(), c.z(), d.x(), d.y(), d.z());
    }

    /**
     * Converts one exact Blockbench vertex into Minecraft's baked-model space.
     * JSON model coordinates are divided by 16 but remain in the native 0..1
     * cube; ItemFeatureRenderer does not recenter baked vertices around zero.
     */
    private static DiskCardSlots.Point place(DiskCardSlots.Slot box,
        DiskCardSlots.Point point, boolean turned)
    {
        float x = point.x();
        float z = point.z();
        if(turned)
        {
            double radians = Math.toRadians(DEFENCE_TURN);
            float dx = x - box.x();
            float dz = z - box.z();
            x = box.x() + (float)(dx * Math.cos(radians) + dz * Math.sin(radians));
            z = box.z() + (float)(-dx * Math.sin(radians) + dz * Math.cos(radians));
        }
        return modelPoint(x, point.y(), z);
    }

    static DiskCardSlots.Point modelPoint(float x, float y, float z)
    {
        return new DiskCardSlots.Point(x * DiskCardSlots.WORLD,
            y * DiskCardSlots.WORLD, z * DiskCardSlots.WORLD);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer)
    {
        // The whole plate rather than the cards actually present: extents feed
        // culling, and a bound that shrinks when a zone empties would make the
        // disk pop in and out as the duel went on.
        for(DiskCardSlots.Slot box : DiskCardSlots.MONSTERS)
        {
            corners(box, consumer);
        }
        for(DiskCardSlots.Slot box : DiskCardSlots.SPELLS)
        {
            corners(box, consumer);
        }
    }

    private static void corners(DiskCardSlots.Slot box, Consumer<Vector3fc> consumer)
    {
        // A defending card sticks out further along x than the box does, so the
        // reach is the longer side either way round.
        float reach = Math.max(box.width(), box.length()) * DiskCardSlots.WORLD * 0.5F;
        float x = box.x() * DiskCardSlots.WORLD;
        float y = box.y() * DiskCardSlots.WORLD;
        float z = box.z() * DiskCardSlots.WORLD;
        consumer.accept(new Vector3f(x - reach, y, z - reach));
        consumer.accept(new Vector3f(x + reach, y, z + reach));
    }

    @Override
    public Rack extractArgument(ItemStack stack)
    {
        // A stack does not know whose disk it is, and the board is not in it.
        // DiskCardsItemModel has the wearer and passes the real rack straight
        // to setupSpecialModel; this is only what the interface asks for.
        return Rack.EMPTY;
    }
}
