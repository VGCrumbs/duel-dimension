package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.platform.NativeImage;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which monsters have a sprite, and what it looks like.
 * <p>
 * <b>Adding one is a file and a line.</b> Drop the sheet in
 * {@code textures/duel/monsters/} and write one {@link #monster} call below.
 * Nothing else: not the pixel dimensions, not the aspect ratio, not a model, not
 * a registry entry somewhere else that has to agree with this one. Everything
 * that can be measured IS measured, at the moment the sprite is first drawn,
 * because a number a person has to type is a number that can be wrong.
 * <p>
 * The sheet's shape is the only contract, and it is short:
 * <ul>
 * <li>one row, frames left to right, every cell the same width;</li>
 * <li>the monster's feet on the bottom edge of every cell -- the billboard
 *     stands the sprite on its card, so padding under the feet makes it
 *     hover;</li>
 * <li>transparent background, and no {@code .mcmeta} -- the game's own
 *     animation would fight this one.</li>
 * </ul>
 * <p>
 * A monster may have a different sprite standing up and lying down. Most will
 * not, and a card with only one sprite uses it for both: that is the common
 * case, so it is the short call.
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
     * One animation, taken from a grid of cells.
     * <p>
     * A grid rather than a row, because seven frames in a row would be a sheet
     * seven cells wide and one tall -- a very long, very thin file that wastes
     * most of a texture. Four by two is how a spritesheet is actually drawn,
     * and a run of frames is then just a start and a count reading across and
     * then down. A single-row sheet is the same thing with one row.
     * <p>
     * That start and count are also what lets ONE file hold two animations. A
     * monster whose eighth cell is its defence pose declares seven frames from
     * cell zero and one frame from cell seven, and neither has to know the
     * other exists.
     *
     * @param columns       cells across the file
     * @param rows          cells down it
     * @param first         which cell this animation begins at, reading across
     *                      and then down
     * @param frames        how many cells it runs for
     * @param ticksPerFrame how long each is held
     * @param loop          how the frames follow one another
     * @param heightInCards how tall the monster stands, measured in card
     *                      lengths, so it scales with the board it is on
     */
    public record Sheet(Identifier texture, int columns, int rows, int first, int frames,
        int ticksPerFrame, Loop loop, float heightInCards)
    {
        /**
         * How wide one cell is against its height, measured from the file.
         * <p>
         * Measured rather than declared, and cached, so a sheet drawn at any
         * size stands at the right width without anybody writing its
         * proportions down twice.
         */
        public float aspect()
        {
            return aspectOf(this);
        }

        /** Which cell of the file a given frame of this animation is. */
        public int cell(int frame)
        {
            return first + Math.clamp(frame, 0, frames - 1);
        }
    }

    /** What a card has: a sprite standing, and one lying down. */
    public record Entry(Sheet attack, Sheet defence)
    {
    }

    private static final Map<Long, Entry> BY_CODE = new HashMap<>();

    /** How tall a monster stands by default, in card lengths. */
    public static final float DEFAULT_HEIGHT = 2.2F;
    /** And how long it holds each frame. Five ticks gives four frames a second. */
    public static final int DEFAULT_TICKS = 5;

    // ================================================================= the list
    //
    // One line per monster. Everything below this comment is data.
    static
    {
        monster(46986414L, "dark_magician", 4, Loop.PING_PONG);
        monster(38033121L, "dark_magician_girl", 4, Loop.PING_PONG);
        monster(70781052L, "summoned_skull", 4, Loop.PING_PONG);
        // Four across and two down: seven frames of animation, and the eighth
        // cell is the pose it holds while lying in defence.
        posed(26202165L, "sangan", 4, 2, 7, Loop.LOOP);
        posed(36262024L, "red_eyes_b_chick", 4, 2, 7, Loop.LOOP);
        // The whole grid is the flap; nothing left over, so nothing to hold
        // while lying down -- it uses the same animation either way.
        monster(28279543L, "curse_of_dragon", 4, 2, Loop.LOOP);
        posed(102380L, "lava_golem", 4, 2, 7, Loop.LOOP);
    }
    // =========================================================================

    /** A monster with one sprite, used whichever way its card is lying. */
    public static void monster(long code, String sheet, int frames, Loop loop)
    {
        monster(code, sheet(sheet, frames, loop), null);
    }

    /** A monster with a different sprite for each battle position. */
    public static void monster(long code, Sheet attack, Sheet defence)
    {
        BY_CODE.put(code, new Entry(attack, defence));
    }

    /**
     * A monster whose sheet ends with a defence pose.
     * <p>
     * The animation runs from the first cell for as many frames as it has, and
     * whatever cells are left over are the pose it holds lying down -- which is
     * one cell in every case so far, and does not have to be.
     */
    public static void posed(long code, String name, int columns, int rows, int frames, Loop loop)
    {
        int cells = columns * rows;
        monster(code, grid(name, columns, rows, 0, frames, loop),
            grid(name, columns, rows, frames, Math.max(1, cells - frames), Loop.LOOP));
    }

    /**
     * A monster whose whole grid is one animation, with no pose left over.
     * <p>
     * The counterpart to {@link #posed}: same grid, but every cell is a frame,
     * so the card uses that one animation whichever way it is lying.
     */
    public static void monster(long code, String name, int columns, int rows, Loop loop)
    {
        monster(code, grid(name, columns, rows, 0, columns * rows, loop), null);
    }

    /** A sheet of one row, which is what most of them are. */
    public static Sheet sheet(String name, int frames, Loop loop)
    {
        return grid(name, Math.max(1, frames), 1, 0, frames, loop);
    }

    /** A run of cells out of a grid, at the default height and pace. */
    public static Sheet grid(String name, int columns, int rows, int first, int frames, Loop loop)
    {
        return new Sheet(Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/duel/monsters/" + name + ".png"), Math.max(1, columns), Math.max(1, rows),
            Math.max(0, first), Math.max(1, frames), DEFAULT_TICKS, loop, DEFAULT_HEIGHT);
    }

    /**
     * The sprite for a card in a given position, or null if it has none.
     * <p>
     * A card with only an attack sprite uses it lying down as well. That is not
     * a compromise: most monsters are drawn once, and a defence sprite is the
     * exception a card opts into rather than a hole every card has to fill.
     */
    public static Sheet sheetFor(long code, boolean defence)
    {
        Entry entry = BY_CODE.get(code);
        if(entry == null)
        {
            return null;
        }
        if(defence && entry.defence() != null)
        {
            return entry.defence();
        }
        return entry.attack();
    }

    public static boolean has(long code)
    {
        return BY_CODE.containsKey(code);
    }

    /**
     * Which cell is showing, from the world's own clock.
     * <p>
     * Whole ticks, and no partial tick: a sheet is a stepped animation, and
     * interpolating between two cells does nothing but risk a torn frame at
     * high frame rates. Driven by the LEVEL's game time rather than by the
     * duel's animator, because that animator is a playback queue that waits on
     * prompts -- an idle sprite tied to it would freeze every time a duellist
     * was asked a question.
     */
    public static int frameAt(Sheet sheet, long gameTime)
    {
        if(sheet.frames() <= 1)
        {
            return 0;
        }
        long step = Math.floorDiv(gameTime, Math.max(1, sheet.ticksPerFrame()));
        if(sheet.loop() == Loop.LOOP)
        {
            return (int)Math.floorMod(step, sheet.frames());
        }
        // There and back again: 0 1 2 3 2 1, which is 2n-2 long rather than n.
        // The two ends are NOT repeated -- holding the first and last cell for
        // two frames each is a stutter at both ends of every sweep.
        int span = sheet.frames() * 2 - 2;
        int at = (int)Math.floorMod(step, span);
        return at < sheet.frames() ? at : span - at;
    }

    // ------------------------------------------------------------- measuring --

    private static final Map<Identifier, Float> ASPECTS = new HashMap<>();

    /**
     * One cell's width over its height, read from the file itself.
     * <p>
     * Read once and remembered. A sheet that cannot be read -- a typo in a
     * name, a resource pack that dropped it -- falls back to a cell twice as
     * tall as it is wide, which is the shape a standing figure is drawn at, so
     * a mistake is a slightly wrong sprite rather than no sprite at all.
     */
    private static float aspectOf(Sheet sheet)
    {
        Float known = ASPECTS.get(sheet.texture());
        if(known != null)
        {
            return known;
        }
        float aspect = 0.5F;
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
            .getResource(sheet.texture());
        if(resource.isPresent())
        {
            try(InputStream stream = resource.get().open();
                NativeImage image = NativeImage.read(stream))
            {
                aspect = image.getWidth() / (float)sheet.columns()
                    / (image.getHeight() / (float)sheet.rows());
            }
            catch(Exception failed)
            {
                DuelDimension.warn("could not measure the monster sheet " + sheet.texture()
                    + ", assuming a cell twice as tall as it is wide: " + failed);
            }
        }
        else
        {
            DuelDimension.warn("no monster sheet at " + sheet.texture());
        }
        ASPECTS.put(sheet.texture(), aspect);
        return aspect;
    }

    /** Forgets the measurements, for a resource reload. */
    public static void clearMeasurements()
    {
        ASPECTS.clear();
    }
}
