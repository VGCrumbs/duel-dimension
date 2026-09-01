package de.cas_ual_ty.dueldimension.clientutil.character;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where a held item sits in a character's hand.
 *
 * <h2>Four grips, because four things are held four ways</h2>
 * It was one, on the reasoning that Minecraft items are all authored to the same
 * hold and each item's own {@code display} settings do the rest. That is true of
 * the pose vanilla uses and not of a pose anyone would choose: a sword is
 * gripped by a handle at one end, a bucket is carried near its middle, a bow is
 * held across the body and drawn, and a block is not gripped at all — it is
 * balanced on the palm. One number that suits a sword has a bucket hovering off
 * the fingertips.
 * <p>
 * Which grip an item gets is READ OFF THE ITEM rather than listed — see
 * {@link #kindOf}. A grip nobody has tuned is the same as the others until
 * somebody tunes it, so having four cost nothing to begin with.
 *
 * <h2>Each is symmetrical</h2>
 * The left hand takes the same numbers with x, yaw and roll negated. Those three
 * and no others, because that is the conjugation {@code X G X} — exactly what
 * vanilla's own {@code ItemTransform.apply} does for a left hand, and a proper
 * rotation, so nothing ends up inside out. Reflecting the frame outright would
 * place the item just as well and invert every normal on it.
 *
 * <h2>Why it is a file and not a constant</h2>
 * The same reason {@link de.cas_ual_ty.dueldimension.clientutil.DuelCamera} is:
 * the right numbers are found by looking at a sword in a hand and moving it
 * until it sits there, and a value found that way is worth keeping where it can
 * be found again. Shipped as a default in the jar, overridden by a file in the
 * config directory, and written back into the source tree by {@link #bake}.
 */
public final class ItemAnchor
{
    /**
     * What an item is held like.
     * <p>
     * Four, not one per item. The distinction that matters is how a thing is
     * gripped, and there are only a few of those; a table per item would be a
     * table nobody could finish.
     */
    public enum Kind
    {
        /** Anything with a handle: swords, picks, axes, rods. */
        TOOL("Tool"),
        /** Buckets, bottles, food — carried rather than wielded. */
        FLAT("Flat"),
        /** Bows and crossbows, which are held across the body and drawn. */
        BOW("Bow"),
        /** Balanced on the palm rather than gripped. */
        BLOCK("Block");

        private final String label;

        Kind(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    /**
     * @param x      across the hand; the left hand negates this, and the yaw
     *               and the roll with it
     * @param y      along the hand, positive up
     * @param z      out of the hand, positive forward
     * @param pitch  degrees about the model's side axis
     * @param yaw    degrees about its up axis
     * @param roll   degrees about the direction it points
     * @param scale  how big the item is drawn, 1 being the size a vanilla hand
     *               holds it at
     * @param localPitch a SECOND turn, and the reason there are two
     * @param localYaw   ...
     * @param localRoll  ...
     *               <p>
     *               The first three are global: they are read in the model's own
     *               axes, so "pitch" always means the same thing and the two
     *               hands can share one number. That is what makes them
     *               editable. It is also what makes them clumsy for the last few
     *               degrees, because once an item has been turned to face the
     *               right way, the next adjustment anybody wants is about the
     *               ITEM — roll the blade a little, tip the haft — and in global
     *               axes that is three coupled numbers instead of one.
     *               <p>
     *               So these three are applied afterwards, in the frame the
     *               first three left behind. Coarse then fine, global then
     *               local, which is the order the hand reaches for them in.
     */
    public record Grip(float x, float y, float z, float pitch, float yaw, float roll,
        float scale, float localPitch, float localYaw, float localRoll)
    {
    }

    /**
     * Where an item sits when nothing has said otherwise.
     * <p>
     * A starting point for the editor rather than a measurement: the DS hands
     * were never asked to hold anything, so there is no authored grip to recover
     * and nothing to be faithful to. All four start the same and are told apart
     * by being tuned.
     */
    private static final Grip DEFAULT =
        new Grip(0F, 0.02F, 0.06F, -80F, 0F, 0F, 0.55F, 0F, 0F, 0F);

    private static final String SHIPPED = "item_anchor.json";
    private static final String SHIPPED_PATH = "/assets/" + DuelDimension.MOD_ID
        + "/item_anchor.json";

    private static final java.util.Map<Kind, Grip> GRIPS =
        new java.util.EnumMap<>(Kind.class);

    static
    {
        for(Kind kind : Kind.values())
        {
            GRIPS.put(kind, DEFAULT);
        }
    }

    private ItemAnchor()
    {
    }

    public static Grip grip(Kind kind)
    {
        Grip got = GRIPS.get(kind == null ? Kind.TOOL : kind);
        return got == null ? DEFAULT : got;
    }

    public static void set(Kind kind, Grip to)
    {
        GRIPS.put(kind == null ? Kind.TOOL : kind, to == null ? DEFAULT : to);
    }

    /**
     * Which grip an item is held with.
     *
     * <h2>Read off the item, not listed</h2>
     * A list of item ids would be wrong the moment anybody added a mod, and this
     * has to answer for every stack a duellist can pick up. So it asks the four
     * questions the categories actually are:
     * <ul>
     * <li>is it a block — {@code BlockItem}, which is what "placeable" means;
     * <li>is it drawn — the use animation says {@code BOW} or {@code CROSSBOW},
     *     compared BY NAME because 26.2 renamed the enum and not its constants;
     * <li>does it have a handle — the {@code TOOL} data component, which is what
     *     vanilla itself uses to mean "this digs things";
     * <li>otherwise it is carried.
     * </ul>
     */
    public static Kind kindOf(ItemStack stack)
    {
        if(stack == null || stack.isEmpty())
        {
            return Kind.TOOL;
        }
        if(stack.getItem() instanceof net.minecraft.world.item.BlockItem)
        {
            return Kind.BLOCK;
        }
        String drawn = stack.getUseAnimation() == null ? "" : stack.getUseAnimation().name();
        if("BOW".equals(drawn) || "CROSSBOW".equals(drawn) || "SPEAR".equals(drawn))
        {
            return Kind.BOW;
        }
        if(stack.get(net.minecraft.core.component.DataComponents.TOOL) != null)
        {
            return Kind.TOOL;
        }
        return Kind.FLAT;
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir().resolve(DuelDimension.MOD_ID)
            .resolve(SHIPPED);
    }

    /** The whole set, for saving and for the editor's undo. */
    public static java.util.Map<Kind, Grip> all()
    {
        return new java.util.EnumMap<>(GRIPS);
    }

    public static void setAll(java.util.Map<Kind, Grip> grips)
    {
        for(Kind kind : Kind.values())
        {
            Grip got = grips == null ? null : grips.get(kind);
            GRIPS.put(kind, got == null ? DEFAULT : got);
        }
    }

    /** The file's shape: one grip per kind, by the enum's own lower-case name. */
    private static java.util.Map<Kind, Grip> parse(String json)
    {
        java.util.Map<String, Grip> raw = new Gson().fromJson(json,
            new com.google.gson.reflect.TypeToken<java.util.Map<String, Grip>>() { }.getType());
        if(raw == null)
        {
            return null;
        }
        java.util.Map<Kind, Grip> out = new java.util.EnumMap<>(Kind.class);
        for(Kind kind : Kind.values())
        {
            Grip got = raw.get(kind.name().toLowerCase(java.util.Locale.ROOT));
            out.put(kind, got == null ? DEFAULT : got);
        }
        return out;
    }

    private static String write(java.util.Map<Kind, Grip> grips)
    {
        java.util.Map<String, Grip> out = new java.util.LinkedHashMap<>();
        for(Kind kind : Kind.values())
        {
            out.put(kind.name().toLowerCase(java.util.Locale.ROOT), grips.get(kind));
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(out);
    }

    /** The local file if there is one, otherwise whatever the jar shipped. */
    public static void load()
    {
        setAll(shipped());
        Path local = file();
        if(!Files.isRegularFile(local))
        {
            return;
        }
        try
        {
            java.util.Map<Kind, Grip> got = parse(Files.readString(local,
                StandardCharsets.UTF_8));
            if(got != null)
            {
                setAll(got);
            }
        }
        catch(Exception unreadable)
        {
            // Including a file in the ONE-GRIP shape this used to write: its
            // fields are floats where a grip is expected, so Gson refuses it.
            // Read as nothing rather than as a crash, and the shipped set
            // stands -- one screen of retuning against a client that will not
            // start.
            DuelDimension.warn("the item anchor could not be read: " + unreadable);
        }
    }

    /**
     * What the jar ships, which is what Reset goes back to.
     * <p>
     * The SHIPPED set and not {@link #DEFAULT}: once somebody has baked good
     * ones into the source, those are the answer, and resetting to the constant
     * would throw their work away rather than the current session's fiddling.
     */
    public static java.util.Map<Kind, Grip> shipped()
    {
        try(InputStream in = ItemAnchor.class.getResourceAsStream(SHIPPED_PATH))
        {
            if(in != null)
            {
                java.util.Map<Kind, Grip> got =
                    parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if(got != null)
                {
                    return got;
                }
            }
        }
        catch(Exception unreadable)
        {
            DuelDimension.warn("the shipped item anchor could not be read: " + unreadable);
        }
        java.util.Map<Kind, Grip> out = new java.util.EnumMap<>(Kind.class);
        for(Kind kind : Kind.values())
        {
            out.put(kind, DEFAULT);
        }
        return out;
    }

    /**
     * The source asset, if this is a development client standing in the repo.
     * <p>
     * A dev client runs with its working directory at {@code <module>/run}, so
     * the tree is a few levels up. Returning null anywhere else is what keeps
     * the editor's Bake button dev-only in fact and not merely by a flag. The
     * same walk {@link de.cas_ual_ty.dueldimension.clientutil.DuelCamera} does,
     * for the same reason.
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
     * Writes the grips into the SOURCE tree, so they reach everybody.
     * <p>
     * This is the point of the editor rather than an extra on it. A tuning
     * screen whose output lands in one player's config directory is a screen
     * whose numbers nobody else will ever see. The local file is deleted
     * afterwards, because it would otherwise sit on top of what was just baked
     * and hide it at the next launch.
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
            Files.writeString(path, write(GRIPS) + System.lineSeparator(),
                StandardCharsets.UTF_8);
            Files.deleteIfExists(file());
            return path;
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not bake the item anchor: " + unwritable);
            return null;
        }
    }

    /** Remembers the grips for this installation only. See {@link #bake}. */
    public static void save()
    {
        try
        {
            Path local = file();
            Files.createDirectories(local.getParent());
            Files.writeString(local, write(GRIPS), StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("the item anchor could not be saved: " + unwritable);
        }
    }
}
