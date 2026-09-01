package de.cas_ual_ty.dueldimension.clientutil.character;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where a character sits when Minecraft has put them on something.
 *
 * <h2>Why this needs tuning at all</h2>
 * The seated clip (`05a`) is the DS game's, authored for the DS game's Duel
 * Runner, and it animates no root translation — see {@code ModelSkeleton} and
 * `FINDINGS.md`. So the pose is a shape and nothing more: the legs come apart
 * and the back settles, and WHERE that shape ends up is entirely Minecraft's
 * doing. Minecraft puts a passenger at the seat of whatever they are riding,
 * measured for a vanilla player's proportions, and a DS duellist is not those
 * proportions. The result is close and not right, and no amount of reading the
 * files will say by how much — that is a thing to be looked at.
 *
 * <h2>Five numbers, and each already had a home</h2>
 * Nothing new was built to apply these. {@link Seat#x}, {@link Seat#y} and
 * {@link Seat#z} are the {@code offsetX}, {@code elevation} and {@code offsetZ}
 * the hologram renderer already takes — applied after the model's turn and
 * before its scale, so they are in the character's own frame and stay meaningful
 * whichever way the horse is pointing. {@link Seat#yaw} is its {@code turn}.
 * And {@link Seat#lean} is a {@code ModelSkeleton.Turn} on the waist, the same
 * mechanism the crouch bends with, which is why it folds the back over the
 * handlebars rather than tipping the whole duellist off the saddle.
 *
 * <h2>One seat, not one per vehicle</h2>
 * A horse, a boat and a minecart do seat a rider differently. This is one set of
 * numbers all the same, because the DS has ONE seated clip and it was drawn for
 * a bike — so a second set would be the same pose at a second offset, which is a
 * thing worth having only once somebody has looked at both and found the first
 * one wrong. {@link ItemAnchor} went the other way and split into four; the
 * difference is that four different SHAPES of grip were already visible in the
 * hand, and here there is one shape.
 *
 * <h2>Shipped, overridable, bakeable</h2>
 * Exactly as {@link ItemAnchor} and {@code DuelCamera} are, and for the reason
 * they are: a number found by moving a slider until it looks right is worth
 * keeping where it can be found again, and worth putting somewhere everybody
 * gets it rather than in one player's config directory. See {@link #bake}.
 */
public final class RideAnchor
{
    /**
     * @param x    sideways, in blocks, in the character's own frame
     * @param y    up, in blocks — the one that settles them into the saddle
     * @param z    forward, in blocks
     * @param yaw  degrees the whole seated character is turned by, for a mount
     *             the duellist should not be facing square along
     * @param lean degrees the back folds forward from the waist, positive
     *             forward. A rider on a bike is folded over it and a rider on a
     *             horse is upright, and one seated clip has to serve both.
     */
    public record Seat(float x, float y, float z, float yaw, float lean)
    {
    }

    /**
     * Where a rider sits before anybody has said otherwise: exactly where
     * Minecraft put them, in the pose the DS drew.
     * <p>
     * All zero, and deliberately so rather than a guess at a better starting
     * point. Zero is the one value that is not an opinion — it is the seated
     * clip placed where the game says the seat is — so it is both the honest
     * default and the reading against which every adjustment made in the editor
     * is a visible, deliberate change.
     */
    private static final Seat DEFAULT = new Seat(0F, 0F, 0F, 0F, 0F);

    private static final String SHIPPED = "ride_anchor.json";
    private static final String SHIPPED_PATH = "/assets/" + DuelDimension.MOD_ID
        + "/ride_anchor.json";

    private static Seat seat = DEFAULT;

    private RideAnchor()
    {
    }

    public static Seat seat()
    {
        return seat;
    }

    public static void set(Seat to)
    {
        seat = to == null ? DEFAULT : to;
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir().resolve(DuelDimension.MOD_ID)
            .resolve(SHIPPED);
    }

    /** The local file if there is one, otherwise whatever the jar shipped. */
    public static void load()
    {
        set(shipped());
        Path local = file();
        if(!Files.isRegularFile(local))
        {
            return;
        }
        try
        {
            Seat got = new Gson().fromJson(Files.readString(local, StandardCharsets.UTF_8),
                Seat.class);
            if(got != null)
            {
                set(got);
            }
        }
        catch(Exception unreadable)
        {
            // Read as nothing rather than as a crash: one screen of retuning
            // beats a client that will not start.
            DuelDimension.warn("the ride anchor could not be read: " + unreadable);
        }
    }

    /**
     * What the jar ships, which is what Reset goes back to.
     * <p>
     * The SHIPPED seat and not {@link #DEFAULT}: once somebody has baked a good
     * one into the source, that is the answer, and resetting to the constant
     * would throw their work away rather than the current session's fiddling.
     */
    public static Seat shipped()
    {
        try(InputStream in = RideAnchor.class.getResourceAsStream(SHIPPED_PATH))
        {
            if(in != null)
            {
                Seat got = new Gson().fromJson(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), Seat.class);
                if(got != null)
                {
                    return got;
                }
            }
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("the shipped ride anchor could not be read: " + unreadable);
        }
        return DEFAULT;
    }

    /**
     * The source asset, if this is a development client standing in the repo.
     * <p>
     * A dev client runs with its working directory at {@code <module>/run}, so
     * the tree is a few levels up. Returning null anywhere else is what keeps
     * the editor's Bake button dev-only in fact and not merely by a flag.
     */
    public static Path sourceFile()
    {
        Path shared = Path.of("").toAbsolutePath().getParent();
        for(int up = 0; up < 4 && shared != null; up++)
        {
            Path candidate = shared.resolve("shared").resolve("resources").resolve("assets")
                .resolve(DuelDimension.MOD_ID).resolve(SHIPPED);
            if(Files.isDirectory(candidate.getParent()))
            {
                return candidate;
            }
            shared = shared.getParent();
        }
        return null;
    }

    /**
     * Writes the seat into the SOURCE tree, so it reaches everybody.
     * <p>
     * This is the point of the editor rather than an extra on it. The local file
     * is deleted afterwards, because it would otherwise sit on top of what was
     * just baked and hide it at the next launch.
     *
     * @return where it was written, or null if this is not a dev client
     */
    public static Path bake()
    {
        Path path = sourceFile();
        if(path == null)
        {
            return null;
        }
        try
        {
            Files.createDirectories(path.getParent());
            Files.writeString(path,
                new GsonBuilder().setPrettyPrinting().create().toJson(seat)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.deleteIfExists(file());
            return path;
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not bake the ride anchor: " + unwritable);
            return null;
        }
    }

    /** Remembers the seat for this installation only. See {@link #bake}. */
    public static void save()
    {
        try
        {
            Path local = file();
            Files.createDirectories(local.getParent());
            Files.writeString(local,
                new GsonBuilder().setPrettyPrinting().create().toJson(seat),
                StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("the ride anchor could not be saved: " + unwritable);
        }
    }
}
