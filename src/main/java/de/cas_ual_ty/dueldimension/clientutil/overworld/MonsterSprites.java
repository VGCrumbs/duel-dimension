package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which monsters have a sprite, and what it looks like.
 * <p>
 * Thirty definitions ship with the mod, one line each in the block below. Over
 * them sits a file the player can edit --
 * {@code config/dueldimension/monster_sprites.json} -- which adds new monsters
 * and replaces shipped ones by passcode. That layering is the whole point: an
 * edit survives an update, a shipped definition nobody touched improves with
 * one, and deleting the file puts everything back exactly as it came.
 * <p>
 * A definition has a body, an optional pose for lying down, optional wings, and
 * a size. Each layer names its own region of a sheet, so one file can carry a
 * body and a pair of wings at different cell sizes -- which is how sheets are
 * actually drawn, and what a single grid over the whole file cannot describe.
 */
public final class MonsterSprites
{
    private MonsterSprites()
    {
    }

    /**
     * How a sheet's frames follow one another.
     * <p>
     * {@link #LOOP} runs 0 1 2 3 0 1 2 3. {@link #PING_PONG} runs 0 1 2 3 2 1
     * and repeats, which is what a sprite drawn as a single sweep needs -- the
     * Dark Magician's four frames are him drifting downwards, so looping them
     * would snap him back to the top every second, while playing them back
     * again is the float they were drawn to be.
     */
    public enum Loop
    {
        LOOP,
        PING_PONG
    }

    /**
     * Everything about one card's monster.
     *
     * @param defence the pose held lying down, or null to use the body's own
     *                animation in either position
     * @param wings   a second layer drawn behind and mirrored, or null
     * @param scale   a multiple of the standard height, so a hatchling is 0.5
     */
    public record Definition(long code, SpriteLayer body, SpriteLayer defence, Wings wings,
        float scale)
    {
    }

    /** How tall a monster stands by default, in card lengths. */
    public static final float DEFAULT_HEIGHT = 2.2F;
    /** And how long it holds each frame. Five ticks gives four frames a second. */
    public static final int DEFAULT_TICKS = 5;

    private static final Map<Long, Definition> BY_CODE = new LinkedHashMap<>();

    // ------------------------------------------------------------- reading --

    public static Definition of(long code)
    {
        return BY_CODE.get(code);
    }

    /**
     * The layer to draw for a card in a given position, or null if it has none.
     * <p>
     * A card with only a body uses it lying down as well. That is not a
     * compromise: most monsters are drawn once, and a defence pose is the
     * exception a card opts into rather than a hole every card has to fill.
     */
    public static SpriteLayer layerFor(long code, boolean defence)
    {
        Definition definition = BY_CODE.get(code);
        if(definition == null)
        {
            return null;
        }
        return defence && definition.defence() != null ? definition.defence() : definition.body();
    }

    public static Wings wingsFor(long code)
    {
        Definition definition = BY_CODE.get(code);
        return definition == null ? null : definition.wings();
    }

    /** The body's drawn height, in card lengths. */
    public static float heightFor(long code)
    {
        Definition definition = BY_CODE.get(code);
        return DEFAULT_HEIGHT * (definition == null ? 1F : definition.scale());
    }

    public static boolean has(long code)
    {
        return BY_CODE.containsKey(code);
    }

    /** Every definition, in the order they were declared. */
    public static Collection<Definition> all()
    {
        return List.copyOf(BY_CODE.values());
    }

    /**
     * Which frame of a run is showing, from the world's own clock.
     * <p>
     * Whole ticks, and no partial tick: a sheet is a stepped animation, and
     * interpolating between two cells does nothing but risk a torn frame at
     * high frame rates. Driven by the LEVEL's game time rather than by the
     * duel's animator, because that animator is a playback queue that waits on
     * prompts -- an idle sprite tied to it would freeze every time a duellist
     * was asked a question.
     */
    public static int frameAt(SpriteLayer layer, long gameTime)
    {
        if(layer == null || layer.frames() <= 1)
        {
            return 0;
        }
        long step = Math.floorDiv(gameTime, Math.max(1, layer.ticks()));
        if(layer.loop() == Loop.LOOP)
        {
            return (int)Math.floorMod(step, layer.frames());
        }
        // There and back again: 0 1 2 3 2 1, which is 2n-2 long rather than n.
        // The two ends are NOT repeated -- holding the first and last cell for
        // two frames each is a stutter at both ends of every sweep.
        int span = layer.frames() * 2 - 2;
        int at = (int)Math.floorMod(step, span);
        return at < layer.frames() ? at : span - at;
    }

    // ------------------------------------------------------------- editing --

    /** Adds a definition, or replaces the one already held for that card. */
    public static void put(Definition definition)
    {
        if(definition != null)
        {
            BY_CODE.put(definition.code(), definition);
        }
    }

    public static void remove(long code)
    {
        BY_CODE.remove(code);
    }

    // ------------------------------------------------------------ measuring --

    private static final Map<Identifier, int[]> SIZES = new HashMap<>();

    /**
     * A sheet's size in pixels, read from the file itself and remembered.
     * <p>
     * The FILE's size, not a cell's. This used to cache a CELL's proportions
     * against the texture, which was correct while one file held one animation
     * and silently wrong the moment wings shared a sheet with a body at a
     * different cell width: whichever drew first taught the cache its shape and
     * the other inherited it. A file has exactly one size, so caching that
     * cannot be wrong, and each layer works its own cell out from its own
     * region.
     */
    public static int[] sizeOf(Identifier texture)
    {
        int[] known = SIZES.get(texture);
        if(known != null)
        {
            return known;
        }
        // An imported sheet knows its own size from the moment it was read.
        // Asking the resource manager for one would find nothing and report a
        // sheet that plainly exists as missing.
        int[] carried = MonsterSheets.size(texture);
        if(carried != null)
        {
            SIZES.put(texture, carried);
            return carried;
        }
        int[] size = {512, 256};
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
            .getResource(texture);
        if(resource.isPresent())
        {
            try(InputStream stream = resource.get().open();
                NativeImage image = NativeImage.read(stream))
            {
                size = new int[] {image.getWidth(), image.getHeight()};
            }
            catch(Exception failed)
            {
                DuelDimension.warn("could not measure the monster sheet " + texture + ": "
                    + failed);
            }
        }
        else
        {
            DuelDimension.warn("no monster sheet at " + texture);
        }
        SIZES.put(texture, size);
        return size;
    }

    /** Forgets the measurements, for a resource reload or a redrawn sheet. */
    public static void clearMeasurements()
    {
        SIZES.clear();
    }

    // ----------------------------------------------------------- persisting --

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension").resolve("monster_sprites.json");
    }

    /**
     * Builds the shipped list, then lays the player's file over it.
     * <p>
     * Called from the client initialiser rather than from a static block. The
     * old list was built the first time anything touched this class, which was
     * in the middle of drawing a frame -- fine for a constant, and no place at
     * all to be opening files.
     */
    public static void load()
    {
        BY_CODE.clear();
        defaults();
        Path path = file();
        if(!Files.isRegularFile(path))
        {
            return;
        }
        try(Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            JsonElement root = JsonParser.parseReader(reader);
            if(!root.isJsonArray())
            {
                DuelDimension.warn(path + " is not a list of sprites; ignoring it");
                return;
            }
            int read = 0;
            for(JsonElement element : root.getAsJsonArray())
            {
                // One bad entry costs one sprite rather than the whole file. A
                // hand-edited list is a hand-edited list.
                Definition definition = readDefinition(element);
                if(definition != null)
                {
                    BY_CODE.put(definition.code(), definition);
                    read++;
                }
            }
            DuelDimension.log("monster sprites: " + read + " read from " + path);
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not read " + path + ": " + unreadable);
        }
    }

    /**
     * Writes out every definition that differs from the shipped one.
     * <p>
     * Only the differences. A file that restated all thirty shipped definitions
     * would freeze them at today's values and quietly refuse every later
     * improvement -- the player would have pinned the whole list by editing one
     * monster.
     */
    public static void save()
    {
        Map<Long, Definition> current = new LinkedHashMap<>(BY_CODE);
        BY_CODE.clear();
        defaults();
        Map<Long, Definition> shipped = new LinkedHashMap<>(BY_CODE);
        BY_CODE.clear();
        BY_CODE.putAll(current);

        JsonArray root = new JsonArray();
        for(Definition definition : BY_CODE.values())
        {
            if(!definition.equals(shipped.get(definition.code())))
            {
                root.add(writeDefinition(definition));
            }
        }
        try
        {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not save the monster sprites: " + unwritable);
        }
    }

    private static Definition readDefinition(JsonElement element)
    {
        try
        {
            JsonObject object = element.getAsJsonObject();
            long code = object.get("card").getAsLong();
            SpriteLayer body = readLayer(object.getAsJsonObject("body"));
            SpriteLayer defence = object.has("defence")
                ? readLayer(object.getAsJsonObject("defence")) : null;
            Wings wings = null;
            if(object.has("wings"))
            {
                JsonObject carried = object.getAsJsonObject("wings");
                wings = new Wings(readLayer(carried.getAsJsonObject("layer")),
                    carried.get("anchor").getAsFloat(), carried.get("spacing").getAsFloat(),
                    carried.get("scale").getAsFloat());
            }
            float scale = object.has("scale") ? object.get("scale").getAsFloat() : 1F;
            return new Definition(code, body, defence, wings, Math.max(0.05F, scale));
        }
        catch(Exception malformed)
        {
            DuelDimension.warn("skipping a malformed monster sprite: " + malformed);
            return null;
        }
    }

    private static SpriteLayer readLayer(JsonObject object)
    {
        return new SpriteLayer(object.get("sheet").getAsString(),
            optional(object, "x", 0), optional(object, "y", 0),
            optional(object, "w", 0), optional(object, "h", 0),
            Math.max(1, optional(object, "columns", 1)),
            Math.max(1, optional(object, "rows", 1)),
            Math.max(0, optional(object, "first", 0)),
            Math.max(1, optional(object, "frames", 1)),
            Math.max(1, optional(object, "ticks", DEFAULT_TICKS)),
            object.has("loop") ? Loop.valueOf(object.get("loop").getAsString()) : Loop.LOOP,
            Math.max(0, optional(object, "trimX", 0)),
            Math.max(0, optional(object, "trimY", 0)));
    }

    private static int optional(JsonObject object, String key, int fallback)
    {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static JsonObject writeDefinition(Definition definition)
    {
        JsonObject object = new JsonObject();
        object.addProperty("card", definition.code());
        object.add("body", writeLayer(definition.body()));
        if(definition.defence() != null)
        {
            object.add("defence", writeLayer(definition.defence()));
        }
        if(definition.wings() != null)
        {
            JsonObject wings = new JsonObject();
            wings.add("layer", writeLayer(definition.wings().layer()));
            wings.addProperty("anchor", definition.wings().anchor());
            wings.addProperty("spacing", definition.wings().spacing());
            wings.addProperty("scale", definition.wings().scale());
            object.add("wings", wings);
        }
        object.addProperty("scale", definition.scale());
        return object;
    }

    private static JsonObject writeLayer(SpriteLayer layer)
    {
        JsonObject object = new JsonObject();
        object.addProperty("sheet", layer.sheet());
        object.addProperty("x", layer.x());
        object.addProperty("y", layer.y());
        object.addProperty("w", layer.w());
        object.addProperty("h", layer.h());
        object.addProperty("columns", layer.columns());
        object.addProperty("rows", layer.rows());
        object.addProperty("first", layer.first());
        object.addProperty("frames", layer.frames());
        object.addProperty("ticks", layer.ticks());
        object.addProperty("loop", layer.loop().name());
        object.addProperty("trimX", layer.trimX());
        object.addProperty("trimY", layer.trimY());
        return object;
    }

    // ================================================================= the list
    //
    // One line per monster. Everything below this comment is data.
    //
    // The passcodes are LOOKED UP, not remembered. A wrong one fails in the
    // quietest possible way -- no error, no warning, just a card that never
    // grows a monster -- so every number here was read out of the shipped card
    // database by name rather than typed from memory.
    private static void defaults()
    {
        row(46986414L, "spellcaster/dark_magician", 4, Loop.PING_PONG);
        row(38033121L, "spellcaster/dark_magician_girl", 4, Loop.PING_PONG);
        row(70781052L, "fiend/summoned_skull", 4, Loop.PING_PONG);
        posed(26202165L, "fiend/sangan", 4, 2, 7, Loop.LOOP);
        posed(36262024L, "dragon/red_eyes_b_chick", 4, 2, 7, Loop.PING_PONG, 0.5F);
        whole(28279543L, "dragon/curse_of_dragon", 4, 2, Loop.LOOP);
        posed(102380L, "fiend/lava_golem", 4, 2, 7, Loop.LOOP);
        posed(32274490L, "zombie/skull_servant", 4, 2, 7, Loop.LOOP);
        posed(25833572L, "warrior/gate_guardian", 4, 2, 7, Loop.LOOP);
        posed(6368038L, "warrior/gaia_the_fierce_knight", 4, 2, 7, Loop.LOOP);
        posed(30243636L, "warrior/hungry_burger", 4, 2, 7, Loop.LOOP);
        posed(60482781L, "warrior/mystic_swordsman_lv6", 4, 2, 7, Loop.LOOP);
        posed(74591968L, "warrior/mystic_swordsman_lv4", 4, 2, 7, Loop.LOOP);
        posed(47507260L, "warrior/mystic_swordsman_lv2", 4, 2, 7, Loop.LOOP);
        posed(50005633L, "warrior/swordstalker", 4, 2, 7, Loop.LOOP);
        posed(20394040L, "warrior/lava_battleguard", 4, 2, 7, Loop.LOOP);
        posed(40453765L, "warrior/swamp_battleguard", 4, 2, 7, Loop.LOOP);
        posed(34627841L, "warrior/kaibaman", 4, 2, 7, Loop.LOOP);
        posed(81383947L, "spellcaster/white_magician_pikeru", 4, 2, 6, Loop.LOOP);
        posed(46128076L, "spellcaster/ebon_magician_curran", 4, 2, 5, Loop.LOOP);
        row(8124921L, "spellcaster/right_leg_of_the_forbidden_one", 4, Loop.PING_PONG);
        row(70903634L, "spellcaster/right_arm_of_the_forbidden_one", 4, Loop.PING_PONG);
        row(44519536L, "spellcaster/left_leg_of_the_forbidden_one", 4, Loop.PING_PONG);
        row(7902349L, "spellcaster/left_arm_of_the_forbidden_one", 4, Loop.PING_PONG);
        row(13893596L, "spellcaster/exodius_the_ultimate_forbidden_lord", 4, Loop.PING_PONG);
        row(12600382L, "spellcaster/exodia_necross", 4, Loop.PING_PONG);
        row(92377303L, "spellcaster/dark_sage", 4, Loop.PING_PONG);
        row(98502113L, "spellcaster/dark_paladin", 4, Loop.PING_PONG);
        row(30208479L, "spellcaster/magician_of_black_chaos", 4, Loop.PING_PONG);
        row(80304126L, "spellcaster/magicians_valkyria", 4, Loop.PING_PONG);

        // The first winged one, and the reason a layer owns a region rather
        // than a whole file: this sheet holds SIX wing frames across the top
        // and FIVE body frames below them, at different cell widths. One grid
        // over the file cannot describe that; two regions can.
        put(new Definition(89631139L,
            new SpriteLayer("dragon/blue_eyes_white_dragon", 0, 104, 0, 152, 5, 1, 0, 5,
                DEFAULT_TICKS, Loop.PING_PONG),
            null,
            Wings.of(new SpriteLayer("dragon/blue_eyes_white_dragon", 0, 0, 0, 104, 6, 1, 0, 6,
                DEFAULT_TICKS, Loop.PING_PONG)),
            1.4F));
    }
    // =========================================================================

    /** One row of frames, used in either battle position. */
    private static void row(long code, String sheet, int frames, Loop loop)
    {
        put(new Definition(code, SpriteLayer.row(sheet, frames, loop), null, null, 1F));
    }

    /** A whole grid of frames, used in either battle position. */
    private static void whole(long code, String sheet, int columns, int rows, Loop loop)
    {
        put(new Definition(code,
            SpriteLayer.grid(sheet, columns, rows, 0, columns * rows, loop), null, null, 1F));
    }

    /** A grid whose LAST cell is the pose held lying down. */
    private static void posed(long code, String sheet, int columns, int rows, int frames,
        Loop loop)
    {
        posed(code, sheet, columns, rows, frames, loop, 1F);
    }

    private static void posed(long code, String sheet, int columns, int rows, int frames,
        Loop loop, float scale)
    {
        put(new Definition(code, SpriteLayer.grid(sheet, columns, rows, 0, frames, loop),
            SpriteLayer.grid(sheet, columns, rows, columns * rows - 1, 1, Loop.LOOP), null,
            scale));
    }
}
