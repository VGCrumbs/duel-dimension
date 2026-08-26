package de.cas_ual_ty.dueldimension.clientutil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.duel.profile.DuelDisks;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a duel disk puts on its plate.
 * <p>
 * The three rules the user asked for, each of which is invisible until it is
 * wrong on somebody's arm: an empty zone draws nothing, a monster set or in
 * defence lies sideways, and a spell or trap never does.
 * <p>
 * The face policy is stubbed. Resolving a real one reaches the card database and
 * the texture manager, and neither exists outside a client — but the policy is
 * {@link CardFaces}, already tested where it lives. What is untested without
 * this is the zone bookkeeping, which is the part that silently puts a card in
 * the wrong box.
 */
class DiskRackTest
{
    private static final ResourceLocation FACE =
        ResourceLocation.fromNamespaceAndPath("dueldimension", "face");

    /** Five boxes per row, which is what the model has. */
    private static final int BOXES = 5;

    private static final DiskCardsRenderer.Faces FACES = slot -> FACE;

    /** The generated frame model, as the game will read it. */
    private static JsonObject frame()
    {
        try(InputStream stream = DiskRackTest.class.getResourceAsStream(
            "/assets/dueldimension/models/item/duel_disk_frame.json"))
        {
            assertTrue(stream != null, "no duel_disk_frame.json — run tools/gen_disk_cards.py");
            return JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch(java.io.IOException e)
        {
            throw new AssertionError(e);
        }
    }

    /**
     * The authored Blockbench model, which does not live in the asset tree here.
     * <p>
     * On 26.2 it is {@code models/item/duel_disk.json}. On 1.21.1 that path is
     * the builtin/entity marker, so the authored file is copied into TEST
     * resources instead -- by {@code tools/port_resources_1211.py}, which
     * explains the two reasons it cannot stay under {@code models/}: the bakery
     * parses every file there whether it is referenced or not, and this is the
     * one with the 42.5 degree rotation 1.21.1 rejects.
     */
    private static final String AUTHORED = "/authored/duel_disk.json";

    private static JsonObject resource(String path)
    {
        try(InputStream stream = DiskRackTest.class.getResourceAsStream(path))
        {
            assertTrue(stream != null, "missing resource " + path);
            return JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch(java.io.IOException e)
        {
            throw new AssertionError(e);
        }
    }

    /** An occupied zone. */
    private static BoardSnapshot.Slot card(boolean faceDown, boolean defence)
    {
        return new BoardSnapshot.Slot(true, 46986414, faceDown, defence, 1800, 1600,
            1800, 1600, -1, -1, 0, null);
    }

    private static BoardSnapshot.Side side(List<BoardSnapshot.Slot> monsters,
        List<BoardSnapshot.Slot> spells)
    {
        return new BoardSnapshot.Side(8000, 8000, monsters, spells,
            List.of(), List.of(), List.of(), List.of(), 40);
    }

    /** A row of {@code count} zones with cards only where {@code at} says. */
    private static List<BoardSnapshot.Slot> row(int count, boolean faceDown, boolean defence,
        int... at)
    {
        List<BoardSnapshot.Slot> zones = new ArrayList<>();
        for(int zone = 0; zone < count; zone++)
        {
            zones.add(BoardSnapshot.Slot.EMPTY);
        }
        for(int zone : at)
        {
            zones.set(zone, card(faceDown, defence));
        }
        return zones;
    }

    private static DiskCardsRenderer.Rack rack(BoardSnapshot.Side side)
    {
        return DiskCardsRenderer.rackFor(side, BOXES, BOXES, FACES);
    }

    @Test
    void anEmptyBoardPutsNothingOnThePlate()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(5, false, false), row(5, false, false)));
        assertTrue(rack.isEmpty(), "five empty zones each side is a bare disk");
    }

    @Test
    void noBoardAtAllIsAlsoABarePlate()
    {
        assertSame(DiskCardsRenderer.Rack.EMPTY,
            DiskCardsRenderer.rackFor(null, BOXES, BOXES, FACES));
        assertTrue(rack(BoardSnapshot.Side.empty()).isEmpty(),
            "a player not in a duel wears a disk with nothing on it");
    }

    /**
     * The gaps are gaps, not a shuffle along.
     * <p>
     * The failure this pins: listing only the occupied zones and then drawing
     * them in list order would slide a card in zone 4 into box 1. The zone has
     * to ride along with the card.
     */
    @Test
    void occupiedZonesKeepTheirOwnBoxes()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(5, false, false, 0, 4),
            row(5, false, false, 2)));

        assertEquals(2, rack.monsters().size(), "two monsters out, three zones empty");
        assertEquals(List.of(0, 4), rack.monsters().stream()
            .map(DiskCardsRenderer.Placed::zone).toList());
        assertEquals(List.of(2), rack.spells().stream()
            .map(DiskCardsRenderer.Placed::zone).toList());
    }

    @Test
    void aMonsterInDefenceLiesSideways()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(5, false, true, 1), List.of()));
        assertTrue(rack.monsters().get(0).turned(), "face-up defence is turned");
    }

    /** Set IS face-down defence, so it is the same answer and not a second rule. */
    @Test
    void aSetMonsterLiesSideways()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(5, true, true, 1), List.of()));
        assertTrue(rack.monsters().get(0).turned());
    }

    @Test
    void aMonsterInAttackStandsUpright()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(5, false, false, 1), List.of()));
        assertFalse(rack.monsters().get(0).turned());
    }

    /**
     * A spell or trap is never sideways, whatever the slot says.
     * <p>
     * Asserted against a slot with {@code defence} SET, which is the case that
     * matters: {@code POS_FACEDOWN} and {@code POS_DEFENSE} overlap in the core
     * (0xA against 0x8), so a set spell can arrive claiming to be in defence. It
     * cannot be — spells and traps have no defence position — and drawing one
     * horizontal was a real bug on the field board before it was gated.
     */
    @Test
    void aSpellIsNeverSideways()
    {
        DiskCardsRenderer.Rack rack = rack(side(List.of(), row(5, true, true, 0, 1, 2, 3, 4)));
        assertEquals(5, rack.spells().size());
        for(DiskCardsRenderer.Placed placed : rack.spells())
        {
            assertFalse(placed.turned(),
                "spell in zone " + placed.zone() + " drawn horizontal");
        }
    }

    /**
     * A duel has more zones than the disk has boxes, and the extras are dropped
     * rather than wrapped.
     * <p>
     * Seven monster zones (five plus two extra monster zones) and eight spell
     * zones (five plus the field zone and two pendulum zones) against five boxes
     * a row. A card in one of those has nowhere to go; putting it somewhere
     * would put it in another card's box.
     */
    @Test
    void zonesWithNoBoxAreNotDrawn()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(7, false, false, 5, 6),
            row(8, false, false, 5, 6, 7)));
        assertTrue(rack.isEmpty(),
            "the extra monster zones, the field zone and the pendulum zones have no box");
    }

    @Test
    void aFullBoardFillsEveryBox()
    {
        DiskCardsRenderer.Rack rack = rack(side(row(7, false, false, 0, 1, 2, 3, 4),
            row(8, false, false, 0, 1, 2, 3, 4)));
        assertEquals(5, rack.monsters().size());
        assertEquals(5, rack.spells().size());
        assertSame(FACE, rack.monsters().get(0).face());
    }

    /**
     * Every selectable disk must actually invoke the dynamic card layer.
     * <p>
     * The first implementation wired only the free disk to the composite item
     * model. Choosing any shop disk therefore made the renderer unreachable:
     * the plate appeared on the arm, but no board state could ever add cards.
     */
    @Test
    void everyEquippableDiskIncludesItsCardLayer()
    {
        for(String disk : DuelDisks.ALL)
        {
            // The marker. builtin/entity is the whole of what makes
            // BakedModel.isCustomRenderer() true, and without it vanilla draws
            // the model's quads and never asks DiskCardsItemModel for anything.
            JsonObject marker = resource(
                "/assets/dueldimension/models/item/" + disk + ".json");
            assertEquals("minecraft:builtin/entity", marker.get("parent").getAsString(),
                disk + " bypasses the dynamic disk-card model");
            // The transforms, which the marker is the only remaining owner of.
            // A builtin/entity model with no display block is drawn at block
            // scale in every hand and every slot.
            assertTrue(marker.has("display"),
                disk + " marker has no display block; it will draw at block scale");

            // And the plate, under the name DiskCardsItemModel.frameModel
            // derives. The two rules -- the resource port's and the renderer's
            // -- are written in different languages in different files, so this
            // is the only place they are checked against each other.
            JsonObject frame = resource(
                "/assets/dueldimension/models/item/" + disk + "_frame.json");
            assertTrue(frame.has("elements"),
                disk + "_frame.json has no geometry; the plate would be invisible");
        }
    }

    /** The runtime frame must be a current split of the authored Blockbench model. */
    @Test
    void generatedFrameUsesTheAuthoredDisplayTransforms()
    {
        JsonObject authored = resource(
            AUTHORED);
        assertEquals(authored.get("display"), frame().get("display"),
            "duel_disk_frame.json is stale; run tools/gen_disk_cards.py");
    }

    /**
     * The generated table has a box for every zone the rack can name.
     * <p>
     * This is the one assertion that reads the real model rather than a number
     * chosen here: re-running the split script after deleting a slot in
     * Blockbench would leave the renderer indexing a list that had got shorter.
     */
    @Test
    void theModelHasFiveBoxesInEachRow()
    {
        assertEquals(BOXES, DiskCardSlots.MONSTERS.size(),
            "card_top elements should hold the five monster zones");
        assertEquals(BOXES, DiskCardSlots.SPELLS.size(),
            "card_bottom elements should hold the five spell and trap zones");
    }

    /** Inverted group labels must not exchange the authored monster and spell rows. */
    @Test
    void generatedRowsFollowTheAuthoredElementNames()
    {
        JsonObject slots = resource(
            "/assets/dueldimension/models/item/duel_disk_slots.json");
        for(JsonElement entry : slots.getAsJsonArray("monsters"))
        {
            assertTrue(entry.getAsJsonObject().get("source").getAsString()
                .startsWith("card_top"));
        }
        for(JsonElement entry : slots.getAsJsonArray("spells"))
        {
            assertTrue(entry.getAsJsonObject().get("source").getAsString()
                .startsWith("card_bottom"));
        }
    }

    /**
     * Every box is a portrait card lying flat on the disk and not beside it.
     * <p>
     * Measured against the FRAME's own extent rather than against numbers
     * written here — a bound guessed in a test is a bound that fails when the
     * model legitimately grows, which is what the first version of this did.
     * <p>
     * What it catches is the split going wrong in the way that would be hardest
     * to see: a slot whose centre was not spun about its rotation origin comes
     * out at a plausible-looking coordinate that is simply the wrong one. Off
     * the disk entirely is not plausible.
     */
    @Test
    void everyBoxIsOnTheDisk()
    {
        float[] disk = frameBounds();
        List<DiskCardSlots.Slot> all = new ArrayList<>(DiskCardSlots.MONSTERS);
        all.addAll(DiskCardSlots.SPELLS);
        for(DiskCardSlots.Slot box : all)
        {
            assertTrue(box.length() > box.width(),
                "a card is taller than it is wide before it is turned");
            assertTrue(box.x() >= disk[0] && box.x() <= disk[1],
                "box x " + box.x() + " outside the disk's " + disk[0] + ".." + disk[1]);
            assertTrue(box.y() >= disk[2] && box.y() <= disk[3],
                "box y " + box.y() + " outside the disk's " + disk[2] + ".." + disk[3]);
            assertTrue(box.z() >= disk[4] && box.z() <= disk[5],
                "box z " + box.z() + " outside the disk's " + disk[4] + ".." + disk[5]);
        }
    }

    /** Every generated vertex is the corresponding authored element's exact top face. */
    @Test
    void generatedCornersAreTheAuthoredTopFaces()
    {
        JsonObject authored = resource(
            AUTHORED);
        JsonArray elements = authored.getAsJsonArray("elements");
        JsonObject slots = resource(
            "/assets/dueldimension/models/item/duel_disk_slots.json");
        for(String row : List.of("monsters", "spells"))
        {
            for(JsonElement entry : slots.getAsJsonArray(row))
            {
                JsonObject generated = entry.getAsJsonObject();
                JsonObject element = named(elements, generated.get("source").getAsString());
                JsonArray from = element.getAsJsonArray("from");
                JsonArray to = element.getAsJsonArray("to");
                JsonObject rotation = element.getAsJsonObject("rotation");
                JsonArray origin = rotation.getAsJsonArray("origin");
                float angle = rotation.get("angle").getAsFloat();
                float y = to.get(1).getAsFloat();
                float[][] raw = {
                    {from.get(0).getAsFloat(), y, from.get(2).getAsFloat()},
                    {to.get(0).getAsFloat(), y, from.get(2).getAsFloat()},
                    {to.get(0).getAsFloat(), y, to.get(2).getAsFloat()},
                    {from.get(0).getAsFloat(), y, to.get(2).getAsFloat()}
                };
                JsonArray corners = generated.getAsJsonArray("corners");
                for(int i = 0; i < 4; i++)
                {
                    float[] expected = spin(raw[i], origin, angle);
                    JsonArray actual = corners.get(i).getAsJsonArray();
                    assertEquals(expected[0], actual.get(0).getAsFloat(), 0.0001F);
                    assertEquals(expected[1], actual.get(1).getAsFloat(), 0.0001F);
                    assertEquals(expected[2], actual.get(2).getAsFloat(), 0.0001F);
                }
            }
        }
    }

    /** Custom geometry must use the same native 0..1 coordinates as baked JSON quads. */
    @Test
    void blockbenchCoordinatesAreNotRecentred()
    {
        DiskCardSlots.Point origin = DiskCardsRenderer.modelPoint(0F, 0F, 0F);
        DiskCardSlots.Point farCorner = DiskCardsRenderer.modelPoint(16F, 16F, 16F);
        assertEquals(0F, origin.x());
        assertEquals(0F, origin.y());
        assertEquals(0F, origin.z());
        assertEquals(1F, farCorner.x());
        assertEquals(1F, farCorner.y());
        assertEquals(1F, farCorner.z());
    }

    private static float[] spin(float[] point, JsonArray origin, float angle)
    {
        double radians = Math.toRadians(angle);
        float ox = origin.get(0).getAsFloat();
        float oz = origin.get(2).getAsFloat();
        float dx = point[0] - ox;
        float dz = point[2] - oz;
        return new float[] {
            ox + (float)(dx * Math.cos(radians) + dz * Math.sin(radians)),
            point[1],
            oz + (float)(-dx * Math.sin(radians) + dz * Math.cos(radians))
        };
    }

    private static JsonObject named(JsonArray elements, String name)
    {
        for(JsonElement entry : elements)
        {
            JsonObject element = entry.getAsJsonObject();
            if(element.has("name") && name.equals(element.get("name").getAsString()))
            {
                return element;
            }
        }
        throw new AssertionError("no authored element named " + name);
    }


    /**
     * The frame draws from one texture and declares it.
     * <p>
     * This is the failure that cost the whole item once, and it is invisible in
     * the file: Blockbench writes {@code "#missing"} for a face nobody assigned
     * a texture to, {@code #missing} resolves to a sprite on the BLOCK atlas,
     * and 26.2 will not bake an item model that touches two atlases — so one
     * unassigned face on an interior box loses the entire duel disk and leaves
     * a purple cube on the player's arm.
     * <p>
     * The generator fills those faces in, and this is what says so afterwards.
     * A face referencing a slot the model does not declare fails the same way
     * and is caught by the same assertion.
     */
    @Test
    void theFrameDeclaresEveryTextureItUses()
    {
        JsonObject model = frame();
        JsonObject textures = model.getAsJsonObject("textures");
        for(var entry : model.getAsJsonArray("elements"))
        {
            JsonObject element = entry.getAsJsonObject();
            String name = element.has("name") ? element.get("name").getAsString() : "?";
            JsonObject faces = element.getAsJsonObject("faces");
            for(String side : faces.keySet())
            {
                String texture = faces.getAsJsonObject(side).get("texture").getAsString();
                assertTrue(texture.startsWith("#"),
                    "'" + name + "' " + side + " names a texture directly: " + texture);
                assertTrue(textures.has(texture.substring(1)),
                    "'" + name + "' " + side + " uses " + texture
                        + ", which the model does not declare — an item model that reaches "
                        + "outside the item atlas does not bake at all");
            }
        }
    }

    /** No card texture rides along on the frame; those belong to the slots. */
    @Test
    void theFrameCarriesOnlyTheDisksOwnTexture()
    {
        JsonObject textures = frame().getAsJsonObject("textures");
        for(String slot : textures.keySet())
        {
            String texture = textures.get(slot).getAsString();
            assertTrue(texture.startsWith("dueldimension:"),
                "texture '" + slot + "' is " + texture + "; a bare name like 'card_back' is "
                    + "a Blockbench placeholder and resolves to another atlas");
        }
    }

    /**
     * The frame model's extent, as {@code minX maxX minY maxY minZ maxZ}.
     * <p>
     * The union of the elements' unrotated corners, widened by a card's own
     * reach. Element rotations can push a corner past the unrotated union, so
     * this is an envelope rather than a tight bound — which is all the test
     * above wants, and being honest about that is better than a tight bound
     * that is quietly wrong.
     */
    private static float[] frameBounds()
    {
        JsonObject model = frame();
        float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
            Float.MAX_VALUE, -Float.MAX_VALUE};
        for(var entry : model.getAsJsonArray("elements"))
        {
            JsonObject element = entry.getAsJsonObject();
            for(String corner : new String[] {"from", "to"})
            {
                var point = element.getAsJsonArray(corner);
                for(int axis = 0; axis < 3; axis++)
                {
                    float value = point.get(axis).getAsFloat();
                    bounds[axis * 2] = Math.min(bounds[axis * 2], value);
                    bounds[axis * 2 + 1] = Math.max(bounds[axis * 2 + 1], value);
                }
            }
        }

        // A card is 3 long, so its centre can sit up to half that past an edge
        // and still be lying on the plate it overhangs.
        float slack = 2F;
        for(int axis = 0; axis < 3; axis++)
        {
            bounds[axis * 2] -= slack;
            bounds[axis * 2 + 1] += slack;
        }
        return bounds;
    }
}
