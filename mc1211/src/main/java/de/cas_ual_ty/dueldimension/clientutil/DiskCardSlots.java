package de.cas_ual_ty.dueldimension.clientutil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the ten card slots sit on the duel disk.
 * <p>
 * The disk is authored as one Blockbench file with the slots in it as
 * placeholder boxes — that is how they were positioned against the plate, and it
 * is how they should stay editable. But a baked item model draws every element
 * it has, always, and these ten have to come and go with the board. So {@code
 * tools/gen_disk_cards.py} splits the authored model in two: the frame, which is
 * an ordinary model, and this table, which is read here and drawn by {@link
 * DiskCardsRenderer}.
 * <p>
 * <b>Nothing here is hand-written.</b> Move a slot in Blockbench, re-run the
 * script, and both derived files follow. The numbers below are only ever the
 * ones that came out of the placeholder boxes, which is why a card lands exactly
 * where the placeholder was rather than somewhere that looked about right.
 * <p>
 * Coordinates are Blockbench's: 0..16 per axis, y up. {@link #WORLD} converts to
 * the space a {@link de.cas_ual_ty.dueldimension.compat.SpecialModelRenderer}
 * draws in, where the item spans -0.5..0.5 and 8 is the middle.
 */
public final class DiskCardSlots
{
    /** Where the generated table lives, inside the mod's own assets. */
    private static final String PATH =
        "/assets/dueldimension/models/item/duel_disk_slots.json";

    /**
     * Blockbench units to item-model units.
     * <p>
     * A block model's 0..16 box becomes the unit cube centred on the origin, so
     * a coordinate {@code p} lands at {@code (p - 8) / 16}. {@link
     * CardSpecialRenderer} draws its card across -0.5..0.5 on the same
     * reasoning; this is that conversion with the offset written down.
     */
    public static final float WORLD = 1F / 16F;

    /**
     * One slot: the plane its card lies in, how big the card is, and how far
     * round the plate the slot is turned.
     * <p>
     * {@code y} is the placeholder box's TOP face rather than its middle. These
     * boxes are 0.05 thick and the model textures their {@code up} face with
     * {@code card_front} — the top face IS the card, so a quad anywhere else is
     * a quad the author never saw.
     *
     * @param angle degrees about the vertical axis, already separated from the
     *              centre (the script spins the centre itself, so this is purely
     *              the turn the quad needs)
     */
    public record Point(float x, float y, float z)
    {
    }

    public record Slot(float x, float y, float z, float width, float length, float angle,
        Point topLeft, Point topRight, Point bottomRight, Point bottomLeft)
    {
    }

    /** Monster zones, sequence 0–4, in the order they are numbered in the model. */
    public static final List<Slot> MONSTERS;
    /** Spell and trap zones, sequence 0–4. */
    public static final List<Slot> SPELLS;

    static
    {
        JsonObject root = read();
        MONSTERS = slots(root, "monsters");
        SPELLS = slots(root, "spells");
    }

    private DiskCardSlots()
    {
    }

    private static JsonObject read()
    {
        try(InputStream stream = DiskCardSlots.class.getResourceAsStream(PATH))
        {
            if(stream == null)
            {
                // Read off the classpath rather than through the resource
                // manager on purpose: this table is generated alongside the
                // model and never changes at runtime, so there is no reload to
                // wait for and no pack that should be overriding it. Missing
                // means the script was not run, which is worth saying plainly.
                de.cas_ual_ty.dueldimension.DuelDimension.warn("no " + PATH + " — run tools/gen_disk_cards.py; "
                    + "the duel disk will draw no cards");
                return new JsonObject();
            }
            return JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch(Exception e)
        {
            de.cas_ual_ty.dueldimension.DuelDimension.warn("could not read " + PATH + ": " + e);
            return new JsonObject();
        }
    }

    private static List<Slot> slots(JsonObject root, String row)
    {
        List<Slot> out = new ArrayList<>();
        if(!root.has(row))
        {
            return List.copyOf(out);
        }
        for(var entry : root.getAsJsonArray(row))
        {
            JsonObject slot = entry.getAsJsonObject();
            JsonArray plane = slot.getAsJsonArray("plane");
            JsonArray corners = slot.getAsJsonArray("corners");
            out.add(new Slot(
                plane.get(0).getAsFloat(), plane.get(1).getAsFloat(), plane.get(2).getAsFloat(),
                slot.get("width").getAsFloat(), slot.get("length").getAsFloat(),
                slot.get("angle").getAsFloat(), point(corners, 0), point(corners, 1),
                point(corners, 2), point(corners, 3)));
        }
        return List.copyOf(out);
    }

    private static Point point(JsonArray corners, int index)
    {
        JsonArray point = corners.get(index).getAsJsonArray();
        return new Point(point.get(0).getAsFloat(), point.get(1).getAsFloat(),
            point.get(2).getAsFloat());
    }
}
