package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A monster described by a model and no sprite sheet survives being saved.
 * <p>
 * It did not, and the way it failed was silent in both directions. The editor
 * treated a blank sheet as "no billboard" and deleted the whole entry, so
 * importing a model for a card that had never had a sprite named the model,
 * saved, and threw it away on the way out — which looks exactly like an import
 * that quietly did not work. And had anything got past that, the writer would
 * have thrown on the absent body and the reader would have discarded the entry
 * as malformed.
 * <p>
 * Both halves are pinned here, because they fail independently: a definition
 * that writes but does not read back is a monster that disappears on restart
 * rather than one that never appeared.
 */
class ModelOnlyDefinitionTest
{
    private static final long CODE = 28279543L;

    private static SpriteLayer sheet()
    {
        return new SpriteLayer("curse_of_dragon", 0, 0, 0, 0, 4, 1, 0, 4, 5,
            MonsterSprites.Loop.PING_PONG, 0, 0);
    }

    @Test
    void aModelWithNoSheetWritesAndReadsBack()
    {
        MonsterSprites.Definition only = new MonsterSprites.Definition(CODE, null, null, null,
            1.4F, "curse_of_dragon", "slot_2");

        JsonObject written = MonsterSprites.writeDefinition(only);
        // Absent rather than a null literal: the reader asks has("body"), and a
        // null would have to be special-cased in both directions.
        assertFalse(written.has("body"), "a model-only definition wrote a body anyway");
        assertEquals("curse_of_dragon", written.get("model").getAsString());
        assertEquals("slot_2", written.get("animation").getAsString());

        MonsterSprites.Definition back = MonsterSprites.readDefinition(written);
        assertNotNull(back, "a model-only definition was read back as malformed");
        assertNull(back.body());
        assertEquals(CODE, back.code());
        assertEquals("curse_of_dragon", back.model());
        assertEquals("slot_2", back.animation());
        assertEquals(1.4F, back.scale(), 1e-6F);
        assertEquals(only, back, "the round trip changed the definition");
    }

    @Test
    void aSheetWithNoModelStillRoundTrips()
    {
        // The other half of the same change: making body optional must not make
        // it optional in practice for the thirty-odd monsters that have one.
        MonsterSprites.Definition sprite = new MonsterSprites.Definition(CODE, sheet(), null, null,
            1F, null, null);

        JsonObject written = MonsterSprites.writeDefinition(sprite);
        assertTrue(written.has("body"));
        assertFalse(written.has("model"));
        assertFalse(written.has("animation"));

        MonsterSprites.Definition back = MonsterSprites.readDefinition(written);
        assertNotNull(back);
        assertNotNull(back.body());
        assertEquals("curse_of_dragon", back.body().sheet());
        assertEquals(sprite, back);
    }

    @Test
    void bothTogetherRoundTrip()
    {
        MonsterSprites.Definition both = new MonsterSprites.Definition(CODE, sheet(), null, null,
            1.379F, "curse_of_dragon", null);

        MonsterSprites.Definition back =
            MonsterSprites.readDefinition(MonsterSprites.writeDefinition(both));
        assertNotNull(back);
        assertEquals(both, back);
        // An animation is written only alongside a model, and there is none.
        assertNull(back.animation());
    }

    @Test
    void whereTheModelStandsRoundTrips()
    {
        MonsterSprites.Definition placed = new MonsterSprites.Definition(CODE, sheet(), null,
            null, 1F, "curse_of_dragon", "slot_2", 0.75F, 90F);

        JsonObject written = MonsterSprites.writeDefinition(placed);
        assertEquals(0.75F, written.get("elevation").getAsFloat(), 1e-6F);
        assertEquals(90F, written.get("turn").getAsFloat(), 1e-6F);

        MonsterSprites.Definition back = MonsterSprites.readDefinition(written);
        assertNotNull(back);
        assertEquals(placed, back);
    }

    @Test
    void standingWhereItWasAuthoredWritesNothingExtra()
    {
        // Zero is the default and means "as exported", so it is left out rather
        // than written on every entry as a number to be interpreted later.
        MonsterSprites.Definition plain = new MonsterSprites.Definition(CODE, sheet(), null,
            null, 1F, "curse_of_dragon", null, 0F, 0F);

        JsonObject written = MonsterSprites.writeDefinition(plain);
        assertFalse(written.has("elevation"));
        assertFalse(written.has("turn"));
        assertEquals(plain, MonsterSprites.readDefinition(written));
    }

    @Test
    void whereTheModelStandsOnItsCardRoundTrips()
    {
        MonsterSprites.Definition nudged = new MonsterSprites.Definition(CODE, sheet(), null,
            null, 1F, "curse_of_dragon", "slot_2", 0.75F, 90F, -0.4F, 0.25F);

        JsonObject written = MonsterSprites.writeDefinition(nudged);
        assertEquals(-0.4F, written.get("offsetX").getAsFloat(), 1e-6F);
        assertEquals(0.25F, written.get("offsetZ").getAsFloat(), 1e-6F);

        MonsterSprites.Definition back = MonsterSprites.readDefinition(written);
        assertNotNull(back);
        assertEquals(nudged, back);
    }

    @Test
    void standingOverTheMiddleWritesNoOffsets()
    {
        // Zero means "over its card", which is where a model starts, so it is
        // left out rather than written on all 661 entries as a pair of noughts.
        MonsterSprites.Definition centred = new MonsterSprites.Definition(CODE, sheet(), null,
            null, 1F, "curse_of_dragon", null, 0F, 0F, 0F, 0F);

        JsonObject written = MonsterSprites.writeDefinition(centred);
        assertFalse(written.has("offsetX"));
        assertFalse(written.has("offsetZ"));
        assertEquals(centred, MonsterSprites.readDefinition(written));
    }

    @Test
    void anEntryWithNeitherIsStillReadable()
    {
        // Not something the editor can now produce -- it removes the definition
        // instead -- but a hand-edited file can, and it must not take the rest
        // of the list down with it.
        MonsterSprites.Definition empty = new MonsterSprites.Definition(CODE, null, null, null,
            1F, null, null);
        MonsterSprites.Definition back =
            MonsterSprites.readDefinition(MonsterSprites.writeDefinition(empty));
        assertNotNull(back, "an entry with no body and no model was read as malformed");
        assertNull(back.body());
        assertNull(back.model());
    }
}
