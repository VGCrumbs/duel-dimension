package de.cas_ual_ty.dueldimension.clientutil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.CommonConfig.Value;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The client's own settings: image sizes, animation lengths, and a few
 * conveniences.
 * <p>
 * The same treatment {@code CommonConfig} got. Forge supplied
 * {@code ForgeConfigSpec} with its {@code push}/{@code pop} sections and
 * {@code defineInRange}; Fabric supplies nothing, so this is a JSON file in the
 * config directory with the defaults built in.
 * <p>
 * The fields are still {@link Value}s answering {@code get()}, which is what
 * {@code ForgeConfigSpec.IntValue} and friends did — so every call site reads
 * the same as it did on Forge.
 * <p>
 * <b>The ranges are enforced here rather than declared.</b> {@code defineInRange}
 * clamped a value and explained itself in the file's comments; JSON has no
 * comments, so each setting clamps on read. That is the part worth keeping: an
 * image size of zero or a million is not a preference, it is a way to break the
 * game, and the bound is what stopped it.
 */
public class ClientConfig
{
    public final Value<Integer> activeCardInfoImageSize;
    public final Value<Integer> activeCardItemImageSize;
    public final Value<Integer> activeCardMainImageSize;
    public final Value<Integer> activeSetInfoImageSize;
    public final Value<Integer> activeSetItemImageSize;
    public final Value<Boolean> keepCachedImages;
    public final Value<Boolean> itemsUseCardImages;
    public final Value<Boolean> itemsUseSetImages;
    public final Value<Boolean> showBinderId;
    public final Value<Integer> maxInfoImages;
    public final Value<Integer> maxMainImages;
    public final Value<Double> duelChatSize;
    public final Value<Integer> moveAnimationLength;
    public final Value<Integer> specialAnimationLength;
    public final Value<Integer> attackAnimationLength;
    public final Value<Integer> announcementAnimationLength;

    private ClientConfig(JsonObject json)
    {
        // card_images
        activeCardInfoImageSize = ranged(json, "cardInfoImageSize", 256, 16, 1024);
        activeCardItemImageSize = ranged(json, "cardItemImageSize", 16, 16, 256);
        activeCardMainImageSize = ranged(json, "cardMainImageSize", 64, 16, 256);
        itemsUseCardImages = bool(json, "cardItemsUseImages", false);
        maxInfoImages = ranged(json, "maxInfoImages", 64, 1, 256);
        maxMainImages = ranged(json, "maxMainImages", 256, 64, 1024);

        // set_images
        activeSetInfoImageSize = ranged(json, "setInfoImageSize", 256, 16, 1024);
        activeSetItemImageSize = ranged(json, "setItemImageSize", 16, 16, 256);
        itemsUseSetImages = bool(json, "setItemsUseImages", false);

        // duel
        duelChatSize = ranged(json, "duelChatSize", 1D, 0.5D, 1D);
        moveAnimationLength = ranged(json, "moveAnimationLength", 10, 8, 40);
        specialAnimationLength = ranged(json, "specialAnimationLength", 10, 8, 40);
        attackAnimationLength = ranged(json, "attackAnimationLength", 12, 8, 40);
        announcementAnimationLength = ranged(json, "announcementAnimationLength", 16, 8, 40);

        // misc
        showBinderId = bool(json, "showBinderId", true);
        keepCachedImages = bool(json, "keepCachedImages", true);
    }

    private static Value<Boolean> bool(JsonObject json, String key, boolean fallback)
    {
        return new Value<>(json.has(key) ? json.get(key).getAsBoolean() : fallback);
    }

    private static Value<Integer> ranged(JsonObject json, String key, int fallback,
        int min, int max)
    {
        int value = json.has(key) ? json.get(key).getAsInt() : fallback;
        return new Value<>(Math.max(min, Math.min(max, value)));
    }

    private static Value<Double> ranged(JsonObject json, String key, double fallback,
        double min, double max)
    {
        double value = json.has(key) ? json.get(key).getAsDouble() : fallback;
        return new Value<>(Math.max(min, Math.min(max, value)));
    }

    /** Everything, so a fresh file shows a player what there is to change. */
    private String write()
    {
        JsonObject json = new JsonObject();
        json.addProperty("cardInfoImageSize", activeCardInfoImageSize.get());
        json.addProperty("cardItemImageSize", activeCardItemImageSize.get());
        json.addProperty("cardMainImageSize", activeCardMainImageSize.get());
        json.addProperty("cardItemsUseImages", itemsUseCardImages.get());
        json.addProperty("maxInfoImages", maxInfoImages.get());
        json.addProperty("maxMainImages", maxMainImages.get());
        json.addProperty("setInfoImageSize", activeSetInfoImageSize.get());
        json.addProperty("setItemImageSize", activeSetItemImageSize.get());
        json.addProperty("setItemsUseImages", itemsUseSetImages.get());
        json.addProperty("duelChatSize", duelChatSize.get());
        json.addProperty("moveAnimationLength", moveAnimationLength.get());
        json.addProperty("specialAnimationLength", specialAnimationLength.get());
        json.addProperty("attackAnimationLength", attackAnimationLength.get());
        json.addProperty("announcementAnimationLength", announcementAnimationLength.get());
        json.addProperty("showBinderId", showBinderId.get());
        json.addProperty("keepCachedImages", keepCachedImages.get());
        return new GsonBuilder().setPrettyPrinting().create().toJson(json);
    }

    /**
     * Reads the config, writing the defaults out if there is not one yet.
     * <p>
     * A failure to read is not a failure to start, for the same reason it is
     * not in {@code CommonConfig}: the defaults are what the file would have
     * held anyway.
     */
    public static ClientConfig load()
    {
        // The chosen card back, applied here because this is the one client
        // setting that is not read from this file -- it changes from a button
        // and this class cannot write, so it keeps its own (see CardBacks).
        // It still has to be APPLIED at config load: the choice is read by a
        // static initialiser, and a static initialiser only runs when something
        // touches the class, so without this the saved back would not reach the
        // duel screen until the player next opened the settings tab.
        CardBacks.apply();

        Path file = FabricLoader.getInstance().getConfigDir()
            .resolve(DuelDimension.MOD_ID + "-client.json");
        try
        {
            if(Files.isRegularFile(file))
            {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject json = new Gson().fromJson(text, JsonObject.class);
                return new ClientConfig(json == null ? new JsonObject() : json);
            }
            ClientConfig defaults = new ClientConfig(new JsonObject());
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaults.write(), StandardCharsets.UTF_8);
            return defaults;
        }
        catch(IOException | RuntimeException unreadable)
        {
            DuelDimension.warn("Could not read " + file + " (" + unreadable.getMessage()
                + "); using defaults.");
            return new ClientConfig(new JsonObject());
        }
    }
}
