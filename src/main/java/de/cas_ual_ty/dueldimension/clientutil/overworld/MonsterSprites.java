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
import java.io.InputStreamReader;
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
 * The mod ships a list at {@code assets/dueldimension/monster_sprites.json}.
 * Over it sits a file the player can edit --
 * {@code config/dueldimension/monster_sprites.json} -- which adds new monsters
 * and replaces shipped ones by passcode. That layering is the whole point: an
 * edit survives an update, a shipped definition nobody touched improves with
 * one, and deleting the file puts everything back exactly as it came.
 * <p>
 * <b>Both layers are the same shape, and both are data.</b> The shipped list
 * used to be Java, which meant the editor could author a monster it had no way
 * to hand back: everything anybody built lived in one config folder on one
 * machine, and shipping it meant retyping it as source. One format and one
 * reader means the editor can write either.
 * <p>
 * The shipped list is read from the CLASSPATH rather than through the resource
 * manager, because it is wanted at client-init time, before packs have loaded --
 * and because it is mod data rather than an asset a pack should be reskinning.
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
        PING_PONG,
        /**
         * One picture, moved rather than redrawn.
         * <p>
         * Plenty of monsters are worth putting on a board and are not worth
         * drawing four times, and a sprite that holds perfectly still beside
         * ones that do not reads as broken rather than as still. A slow rise
         * and fall costs a single cell of art and buys what the frames were
         * really for, which is looking alive.
         * <p>
         * The frame count stops meaning a number of pictures and starts meaning
         * the length of the bob -- how many steps it takes to come round again.
         */
        BOB
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
        // A bobbing layer's frame count is a period, not a run of pictures, so
        // it holds the first cell however high that count goes.
        if(layer == null || layer.frames() <= 1 || layer.loop() == Loop.BOB)
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

    /**
     * How far a bobbing sprite rises above and falls below where it stands, as
     * a fraction of its own height.
     * <p>
     * Small on purpose. This is a monster hovering over its card, not one
     * jumping off it: a few percent reads as breathing, and much more reads as
     * a mistake in the placement.
     */
    public static final float BOB_RISE = 0.04F;

    /**
     * How high a bobbing sprite sits this tick, as a fraction of its height.
     * <p>
     * A sine sampled at whole steps rather than a continuous one, so the
     * movement lands on the same beat as the animated sprites beside it -- and
     * because these are pixel drawings, where stepping between positions looks
     * more right than gliding between them. Zero for every other kind of layer,
     * so a caller may add it without asking first.
     */
    public static float bobAt(SpriteLayer layer, long gameTime)
    {
        if(layer == null || layer.loop() != Loop.BOB || layer.frames() < 2)
        {
            return 0F;
        }
        int steps = layer.frames();
        long step = Math.floorDiv(gameTime, Math.max(1, layer.ticks()));
        return BOB_RISE * (float)Math.sin(2D * Math.PI * Math.floorMod(step, steps) / steps);
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

    /** Where the mod's own list sits inside the jar. */
    private static final String SHIPPED_FILE = "/assets/dueldimension/monster_sprites.json";

    /**
     * The mod's own list, kept apart from the live one.
     * <p>
     * Kept rather than rebuilt on demand, because {@link #save} needs to know
     * what shipped in order to write only what differs -- and it used to find
     * out by clearing the live map, refilling it, and putting it back. That is
     * four lines during which a render thread asking which sprite a card has
     * gets a map that does not have it, and the editor calls save on every
     * movement of every slider.
     */
    private static final Map<Long, Definition> SHIPPED = new LinkedHashMap<>();

    /**
     * Reads the list the mod ships with.
     * <p>
     * Package-visible so a test can rebuild it without a game around it.
     */
    static void loadShipped()
    {
        SHIPPED.clear();
        try(InputStream stream = MonsterSprites.class.getResourceAsStream(SHIPPED_FILE))
        {
            if(stream == null)
            {
                DuelDimension.warn("no shipped monster sprite list at " + SHIPPED_FILE
                    + " -- no card will have a sprite of its own");
                return;
            }
            JsonElement root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            if(!root.isJsonArray())
            {
                DuelDimension.warn(SHIPPED_FILE + " is not a list of sprites; ignoring it");
                return;
            }
            for(JsonElement element : root.getAsJsonArray())
            {
                Definition definition = readDefinition(element);
                if(definition != null)
                {
                    SHIPPED.put(definition.code(), definition);
                }
            }
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not read " + SHIPPED_FILE + ": " + unreadable);
        }
    }

    /** The shipped list as it would be written to the jar. */
    public static String shippedJson()
    {
        JsonArray root = new JsonArray();
        for(Definition definition : SHIPPED.values())
        {
            root.add(writeDefinition(definition));
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * Puts a definition into the SHIPPED list, for an export that has just
     * written it into the mod's own file. Without this the export would keep
     * being written to the player's config too, as a difference from a list it
     * is now part of.
     */
    public static void ship(Definition definition)
    {
        if(definition != null)
        {
            SHIPPED.put(definition.code(), definition);
        }
    }

    /** How many monsters the mod itself knows about. */
    public static int shippedCount()
    {
        return SHIPPED.size();
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
        loadShipped();
        BY_CODE.clear();
        BY_CODE.putAll(SHIPPED);
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
                // A deletion has to be written down, because the shipped list
                // is rebuilt from scratch every launch and would otherwise put
                // back every monster anybody had ever removed.
                if(element.isJsonObject() && element.getAsJsonObject().has("removed")
                    && element.getAsJsonObject().get("removed").getAsBoolean())
                {
                    JsonObject gone = element.getAsJsonObject();
                    if(gone.has("card"))
                    {
                        BY_CODE.remove(gone.get("card").getAsLong());
                        read++;
                    }
                    continue;
                }
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
        JsonArray root = new JsonArray();
        for(Definition definition : BY_CODE.values())
        {
            if(!definition.equals(SHIPPED.get(definition.code())))
            {
                root.add(writeDefinition(definition));
            }
        }
        // A shipped monster somebody deleted has to be recorded as deleted.
        // Writing only what is present says nothing about what is absent, and
        // the next launch rebuilds the shipped list and hands it straight back.
        for(Long code : SHIPPED.keySet())
        {
            if(!BY_CODE.containsKey(code))
            {
                JsonObject gone = new JsonObject();
                gone.addProperty("card", code);
                gone.addProperty("removed", true);
                root.add(gone);
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

    // The list itself is data now, in assets/dueldimension/monster_sprites.json
    // and read by loadShipped() above. It used to be eighty lines of Java right
    // here, which is what made a monster built in the editor unshippable until
    // somebody retyped it as source.
}