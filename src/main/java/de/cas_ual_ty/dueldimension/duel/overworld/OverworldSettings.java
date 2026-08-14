package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The size and shape of an overworld duel field, adjustable in game.
 * <p>
 * Sizing a board that is drawn in world space is a thing you get right by
 * looking at it, not by reasoning about it, and a recompile between every look
 * makes that a slow loop. So every number lives in a file that can be edited
 * from the mod's own settings screen and takes effect on the next duel -- no
 * restart, no rebuild.
 * <p>
 * <b>This is the server's setting, not a client preference.</b> The server sites
 * duels with it and sends the resulting {@link FieldSpec} to both clients with
 * the field, so a client with a different file still draws exactly the board the
 * server validated. In singleplayer and on a LAN host the two are the same JVM,
 * which is what makes the settings screen able to change it at all.
 * <p>
 * Its own file rather than a {@code ClientConfig} key, for the same reason as
 * {@code HoverPreviewSettings}: that config is read once at startup and has no
 * public writer, and a setting changed from a menu has to persist the moment it
 * changes.
 */
public final class OverworldSettings
{
    private OverworldSettings()
    {
    }

    private static FieldSpec spec = FieldSpec.DEFAULT;

    /** The spec duels are currently sited with. */
    public static FieldSpec spec()
    {
        return spec;
    }

    /**
     * Replaces the spec and writes it out. Clamping is the record's own job, so
     * whatever is passed here comes back safe -- including from a settings
     * screen with a slider someone dragged to the end.
     */
    public static void set(FieldSpec value)
    {
        spec = value == null ? FieldSpec.DEFAULT : value;
        save();
    }

    /** Back to the shipped nine by nine. */
    public static void reset()
    {
        set(FieldSpec.DEFAULT);
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir().resolve("dueldimension-overworld.txt");
    }

    private static final String AREA_WIDTH = "areaWidth";
    private static final String AREA_DEPTH = "areaDepth";
    private static final String CLEARANCE = "clearance";
    private static final String MAT_SCALE = "matScale";
    private static final String LATERAL_TOLERANCE = "lateralTolerance";
    private static final String ELEVATION_TOLERANCE = "elevationTolerance";
    private static final String MAX_SEPARATION = "maxSeparation";
    private static final String SEARCH_RADIUS = "searchRadius";
    private static final String VERTICAL_SEARCH = "verticalSearch";

    private static void save()
    {
        StringBuilder text = new StringBuilder();
        text.append("# Overworld duel field. Edited in game from the mod's settings screen,\n");
        text.append("# or by hand here; a new duel picks up whatever is in this file.\n");
        text.append("# areaWidth/areaDepth are the ground that must be clear, in blocks, and\n");
        text.append("# are forced odd because the field is centred on a block. matScale is the\n");
        text.append("# board's size in blocks per card-field unit; it is capped so the board\n");
        text.append("# can never be bigger than the ground checked for it.\n");
        text.append(AREA_WIDTH).append('=').append(spec.areaWidth()).append('\n');
        text.append(AREA_DEPTH).append('=').append(spec.areaDepth()).append('\n');
        text.append(CLEARANCE).append('=').append(spec.clearance()).append('\n');
        text.append(MAT_SCALE).append('=').append(spec.matScale()).append('\n');
        text.append(LATERAL_TOLERANCE).append('=').append(spec.lateralTolerance()).append('\n');
        text.append(ELEVATION_TOLERANCE).append('=').append(spec.elevationTolerance()).append('\n');
        text.append(MAX_SEPARATION).append('=').append(spec.maxSeparation()).append('\n');
        text.append(SEARCH_RADIUS).append('=').append(spec.searchRadius()).append('\n');
        text.append(VERTICAL_SEARCH).append('=').append(spec.verticalSearch()).append('\n');
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), text.toString(), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            // A setting that will not save is still a setting for this session.
            DuelDimension.warn("Could not save " + file() + ": " + unwritable.getMessage());
        }
    }

    /**
     * Reads the file, falling back field by field rather than all or nothing: a
     * file with one bad line still contributes its other eight.
     */
    public static void load()
    {
        Path path = file();
        if(!Files.isRegularFile(path))
        {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        try
        {
            for(String line : Files.readAllLines(path, StandardCharsets.UTF_8))
            {
                String trimmed = line.trim();
                int equals = trimmed.indexOf('=');
                if(trimmed.isEmpty() || trimmed.startsWith("#") || equals <= 0)
                {
                    continue;
                }
                values.put(trimmed.substring(0, equals).trim(),
                    trimmed.substring(equals + 1).trim());
            }
        }
        catch(IOException unreadable)
        {
            DuelDimension.warn("Could not read " + path + ": " + unreadable.getMessage());
            return;
        }

        FieldSpec fallback = FieldSpec.DEFAULT;
        spec = new FieldSpec(
            integer(values, AREA_WIDTH, fallback.areaWidth()),
            integer(values, AREA_DEPTH, fallback.areaDepth()),
            integer(values, CLEARANCE, fallback.clearance()),
            decimal(values, MAT_SCALE, fallback.matScale()),
            integer(values, LATERAL_TOLERANCE, fallback.lateralTolerance()),
            integer(values, ELEVATION_TOLERANCE, fallback.elevationTolerance()),
            integer(values, MAX_SEPARATION, fallback.maxSeparation()),
            integer(values, SEARCH_RADIUS, fallback.searchRadius()),
            integer(values, VERTICAL_SEARCH, fallback.verticalSearch()));
    }

    private static int integer(Map<String, String> values, String key, int fallback)
    {
        try
        {
            String value = values.get(key);
            return value == null ? fallback : Integer.parseInt(value);
        }
        catch(NumberFormatException notANumber)
        {
            return fallback;
        }
    }

    private static float decimal(Map<String, String> values, String key, float fallback)
    {
        try
        {
            String value = values.get(key);
            float parsed = value == null ? fallback : Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : fallback;
        }
        catch(NumberFormatException notANumber)
        {
            return fallback;
        }
    }
}
