package de.cas_ual_ty.dueldimension.clientutil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The overhead view of the duel board, on {@code V}.
 *
 * <h2>Its default IS the 2D board</h2>
 * The flat duel screen shows the field from directly above, square on, framed so
 * the whole mat is in shot. That is a camera placement, not a drawing style, so
 * this reproduces it rather than approximating it: straight down over the middle
 * of the mat, turned so the opponent's half is at the top of the screen, and
 * lifted to exactly the distance at which the mat fills the frame at the
 * player's own field of view.
 * <p>
 * Everything below is an OFFSET from that. With the whole settings file at zero
 * the view is the 2D board, which is what makes the numbers legible: a pitch of
 * -12 means twelve degrees off square-on, and not "twelve degrees from wherever
 * the default happened to land".
 *
 * <h2>Framed at the field of view, not at a written-down height</h2>
 * A height that frames the mat at 70 degrees crops it at 90 and strands it in
 * the middle at 30. The distance is therefore derived from the FOV in force and
 * the window's aspect, taking whichever of the mat's two dimensions binds -- so
 * the shot holds whatever the player has done to their settings or their window.
 */
public final class DuelCamera
{
    private DuelCamera()
    {
    }

    /** Where the camera is put, in world space and Minecraft's own angles. */
    public record Placement(double x, double y, double z, float yaw, float pitch)
    {
    }

    /**
     * The tunables, every one an offset from the 2D board's own placement.
     *
     * @param pitch      degrees off straight-down; negative leans the camera
     *                   back towards the player's side of the table
     * @param yaw        degrees turned about the board's middle
     * @param distance   multiplies the framing distance: 1 is the mat exactly
     *                   filling the frame, above 1 pulls back
     * @param across     blocks sideways, in the board's own frame
     * @param along      blocks towards the opponent, in the board's own frame
     * @param height     blocks straight up, after everything else
     */
    public record Settings(float pitch, float yaw, float distance, float across, float along,
        float height)
    {
        public static final Settings DEFAULT = new Settings(0F, 0F, 1F, 0F, 0F, 0F);
    }

    /** Room around the mat, so its edge is not flush with the window's. */
    private static final float MARGIN = 1.06F;

    /**
     * The field of view the framing is worked out at, and the shape of the
     * window it assumes: Minecraft's own defaults.
     * <p>
     * Fixed on purpose -- see {@link #placement()}. A placement that moved with
     * the player's settings could not be tuned, because the numbers it is tuned
     * with are blocks and the thing they are measured from was sliding.
     */
    private static final double REFERENCE_FOV = 70D;

    private static final double REFERENCE_ASPECT = 16D / 9D;

    private static Settings settings = Settings.DEFAULT;
    private static boolean active;

    /**
     * Which view a duel OPENS in, as against the one it is currently showing.
     * <p>
     * Remembered separately from {@link #active} on purpose: a player who
     * prefers the overhead shot wants every duel to start there, and a player
     * who toggles into it for one look does not want that answer changed for
     * them. The key moves the view; this setting moves the default.
     */
    private static boolean defaultOverhead;

    public static boolean defaultOverhead()
    {
        return defaultOverhead;
    }

    public static void setDefaultOverhead(boolean value)
    {
        defaultOverhead = value;
    }

    /** Called as a duel starts, to open it in whichever view is preferred. */
    public static void beginDuel()
    {
        active = defaultOverhead;
    }

    public static Settings settings()
    {
        return settings;
    }

    public static void setSettings(Settings value)
    {
        settings = value == null ? Settings.DEFAULT : value;
    }

    public static boolean active()
    {
        return active;
    }

    /** Toggled by the key, and dropped the moment the duel is not on. */
    public static void toggle()
    {
        active = !active;
    }

    public static void off()
    {
        active = false;
    }

    /**
     * Where the camera goes this frame, or null to leave it alone.
     * <p>
     * Null whenever there is no board to look at, so the view cannot strand a
     * player overhead after a duel ends -- the toggle is deliberately not
     * cleared here, because a duel that ends and another that begins should
     * come back to the view the player last chose.
     */
    public static Placement placement()
    {
        if(!active)
        {
            return null;
        }
        FieldSiting siting = ClientDuelField.siting();
        if(siting == null || !ClientDuelField.running())
        {
            return null;
        }
        FieldTransform transform = new FieldTransform(siting);
        Minecraft client = Minecraft.getInstance();
        if(client.player == null)
        {
            return null;
        }

        Vec3 middle = transform.at(FieldTransform.CENTRE_X, FieldTransform.CENTRE_Y, 0D);
        float scale = transform.scale();
        double matWide = (FieldLayout.FIELD_MAX_X - FieldLayout.FIELD_MIN_X) * scale;
        double matDeep = (FieldLayout.FIELD_MAX_Y - FieldLayout.FIELD_MIN_Y) * scale;

        // The distance at which the whole mat is in frame.
        //
        // AT A FIXED FIELD OF VIEW, NOT THE PLAYER'S. Deriving it from
        // options.fov() looked obvious -- a wide lens wants a nearer camera to
        // hold the same framing -- and it made the view behave strangely the
        // moment anyone touched the setting: every offset below is in BLOCKS,
        // so a tuned shot kept its across, along and height while the distance
        // those were tuned against moved out from under them. It also meant a
        // baked placement described the shot only for whoever baked it.
        //
        // So the camera stands where it stands, and field of view does here
        // what it does everywhere else in the game: changes how much of the
        // world is in frame, without moving the player.
        double half = Math.tan(Math.toRadians(REFERENCE_FOV) / 2D);
        double byDepth = matDeep / (2D * half);
        // Width is asked at the NARROWER of the reference shape and the actual
        // window, and never the wider: a window narrower than 16:9 would crop
        // the mat's sides, and one wider than it simply shows more table.
        double aspect = Math.min(REFERENCE_ASPECT, Math.max(0.5D,
            (double)client.getWindow().getWidth()
                / Math.max(1, client.getWindow().getHeight())));
        double byWidth = matWide / (2D * half * aspect);
        double away = Math.max(byDepth, byWidth) * MARGIN * Math.max(0.05F, settings.distance());

        // The board's own frame, so an offset means the same thing whichever
        // way round the table has been built.
        Direction facing = siting.facing();
        Direction right = transform.right();
        double x = middle.x + right.getStepX() * settings.across()
            + facing.getStepX() * settings.along();
        double z = middle.z + right.getStepZ() * settings.across()
            + facing.getStepZ() * settings.along();

        // Straight down is pitch 90. Leaning back off that swings the camera up
        // and over towards the player, so the height and the pull-back both
        // come out of the same angle rather than being tuned against each other.
        float pitch = 90F + settings.pitch();
        double lean = Math.toRadians(90F - pitch);
        double lift = away * Math.cos(lean) + settings.height();
        double back = away * Math.sin(lean);
        x -= facing.getStepX() * back;
        z -= facing.getStepZ() * back;

        // Turned so the opponent's half is at the top of the screen, which is
        // where the 2D board puts it: `facing` runs from the viewer's seat
        // towards theirs.
        float yaw = facing.toYRot() + settings.yaw();
        return new Placement(x, transform.surfaceY() + lift, z, yaw, pitch);
    }

    // ---- where the numbers live ------------------------------------------

    private static final String SHIPPED = "/assets/dueldimension/duel_camera.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Read once at client start: the shipped placement, then any local edit. */
    public static void load()
    {
        settings = Settings.DEFAULT;
        try(InputStream stream = DuelCamera.class.getResourceAsStream(SHIPPED))
        {
            if(stream != null)
            {
                read(GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                    JsonObject.class));
            }
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("could not read the shipped duel camera: " + unreadable);
        }
        Path local = configFile();
        if(Files.isRegularFile(local))
        {
            try
            {
                read(GSON.fromJson(Files.readString(local, StandardCharsets.UTF_8),
                    JsonObject.class));
            }
            catch(Exception unreadable)
            {
                DuelDimension.warn("could not read the local duel camera: " + unreadable);
            }
        }
    }

    private static void read(JsonObject json)
    {
        if(json == null)
        {
            return;
        }
        settings = new Settings(
            number(json, "pitch", settings.pitch()),
            number(json, "yaw", settings.yaw()),
            number(json, "distance", settings.distance()),
            number(json, "across", settings.across()),
            number(json, "along", settings.along()),
            number(json, "height", settings.height()));
        if(json.has("defaultOverhead"))
        {
            defaultOverhead = json.get("defaultOverhead").getAsBoolean();
        }
    }

    private static float number(JsonObject json, String key, float fallback)
    {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static Path configFile()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension").resolve("duel_camera.json");
    }

    /**
     * The source asset, if this is a development client standing in the repo.
     * <p>
     * A dev client runs with its working directory at {@code <module>/run}, so
     * the tree is two levels up. Returning null anywhere else is what keeps the
     * editor's Bake button dev-only in fact and not merely by a flag.
     */
    public static Path sourceFile()
    {
        Path shared = Path.of("").toAbsolutePath().getParent();
        for(int up = 0; up < 4 && shared != null; up++)
        {
            Path candidate = shared.resolve("shared").resolve("resources").resolve("assets")
                .resolve("dueldimension").resolve("duel_camera.json");
            if(Files.isDirectory(candidate.getParent()))
            {
                return candidate;
            }
            shared = shared.getParent();
        }
        return null;
    }

    private static JsonObject write()
    {
        JsonObject json = new JsonObject();
        json.addProperty("pitch", settings.pitch());
        json.addProperty("yaw", settings.yaw());
        json.addProperty("distance", settings.distance());
        json.addProperty("across", settings.across());
        json.addProperty("along", settings.along());
        json.addProperty("height", settings.height());
        json.addProperty("defaultOverhead", defaultOverhead);
        return json;
    }

    /** Remembers the current placement for this installation only. */
    public static void save()
    {
        try
        {
            Path path = configFile();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(write()), StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not save the duel camera: " + unwritable);
        }
    }

    /**
     * Writes the current placement into the SOURCE TREE, so it ships.
     * <p>
     * This is the whole point of the editor being dev-only. The billboard
     * editor's lesson is written up in {@code tools/promote_monster_sprites.py}:
     * a tuning screen that saves to the player's config is a tuning screen whose
     * output never reaches anybody, and nobody notices until months of work is
     * sitting in a directory that is not in the repository.
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
            Files.writeString(path, GSON.toJson(write()) + "\n", StandardCharsets.UTF_8);
            // The local file would otherwise sit on top of what was just baked
            // and hide it at the next launch.
            Files.deleteIfExists(configFile());
            return path;
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not bake the duel camera: " + unwritable);
            return null;
        }
    }
}
