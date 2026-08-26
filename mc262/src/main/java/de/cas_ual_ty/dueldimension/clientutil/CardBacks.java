package de.cas_ual_ty.dueldimension.clientutil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The back a duelist's cards are printed on.
 * <p>
 * Client-side and personal, like the mat colour and the duel music beside it in
 * the settings tab: a card back is what <em>you</em> look at for an hour, so it
 * is decided here and never travels. Both halves of the table currently show the
 * chosen back — there is no per-side choice yet — which is why
 * {@link DuelTextures#setCovers} is handed the same texture twice rather than
 * one; the day a duelist can print their opponent's cards differently, that call
 * gains a second argument and nothing else moves.
 * <p>
 * <b>Not in {@link ClientConfig}.</b> That class is deliberately immutable —
 * every field is {@code final}, {@code Value} is a record, and its {@code write}
 * runs exactly once, in the branch of {@code load()} where the file does not
 * exist yet. A preference changed from a button has to be written when it
 * changes, so it lives in its own file, which is what
 * {@code DuelMusic.save()} and {@code DuelClientState.savePlayMat()} already do
 * for the two preferences either side of it.
 */
public final class CardBacks
{
    /**
     * A back the player can choose.
     * <p>
     * A record rather than an enum for the reason {@code DuelMusic.Track} is
     * one: adding a back is a line here plus a PNG, and the id that gets
     * written to disk is stated rather than being whatever {@code name()}
     * happened to return.
     * <p>
     * <b>Sleeves are not backs and never join this list.</b> A back is free,
     * client-local and personal — it never travels the wire. A sleeve is
     * bought, recorded on the server's {@code DuelProfile} and stored per
     * deck ({@code DeckList.sleeve()}), so the server is the only thing that
     * may say which one a duelist is entitled to. The two also differ in the
     * art: a back is a 480x700 card-shaped PNG drawn through the full UV
     * range, a sleeve is a SQUARE file whose art is letterboxed at
     * {@link DuelTextures#CARD_U0}..{@code CARD_V1} and must be sampled
     * through that window.
     */
    public record Back(String id, String label, Identifier texture)
    {
    }

    /**
     * Every back, in the order the settings tab offers them.
     * <p>
     * The first is the default, which is what a fresh install and an
     * unrecognised stored id both fall back to. That is the ANIME back, and the
     * order here is the only thing that says so -- a duellist who has already
     * chosen keeps their choice, because the stored id is read back by name. Every file is 480x700, the
     * shape {@link DuelTextures#CARD_ASPECT} describes, so a back is drawn
     * through the full 0..1 UV range rather than the letterbox window — which
     * is what the {@code texture.equals(COVER)} tests across the duel screen
     * are asking.
     */
    public static final List<Back> ALL = List.of(
        new Back("anime", "Anime", texture("anime")),
        new Back("tcg", "TCG", texture("tcg")));

    /**
     * The key the id is stored under. Named rather than positional so a second
     * client-local preference can be stored beside it without either having to
     * know the other exists. A sleeve is not one of those — it lives on the
     * profile the server owns, not in this file.
     */
    private static final String KEY = "back";

    private static Back back = ALL.get(0);

    private CardBacks()
    {
    }

    private static Identifier texture(String id)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/duel/backs/" + id + ".png");
    }

    /** Where the choice lives between sessions. */
    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve(DuelDimension.MOD_ID + "-cardback.json");
    }

    public static Back back()
    {
        return back;
    }

    /** The back with this id, or the default — never null, and never a guess. */
    public static Back byId(String id)
    {
        for(Back candidate : ALL)
        {
            if(candidate.id().equals(id))
            {
                return candidate;
            }
        }
        return ALL.get(0);
    }

    /**
     * Chooses a back, and it is on the table the next frame.
     * <p>
     * Nothing has to be told: every card the duel screen draws resolves its
     * texture from {@link DuelTextures#COVER} inside the draw itself, so a back
     * changed mid-duel is the one the next face-down card is printed on. No
     * restart, and no duel restart.
     */
    public static void set(Back chosen)
    {
        if(chosen == null || chosen == back)
        {
            return;
        }
        back = chosen;
        apply();
        save();
    }

    /**
     * Points the duel screen's two card-back fields at the chosen back.
     * <p>
     * Called at client-config load as well as on change: the stored choice is
     * read by the static initialiser below, and a static initialiser only runs
     * when something first touches the class. Without this call from a startup
     * path, a saved back would not take effect until the player next opened the
     * settings tab.
     */
    public static void apply()
    {
        DuelTextures.setCovers(back.texture(), back.texture());
    }

    private static void save()
    {
        try
        {
            JsonObject json = new JsonObject();
            json.addProperty(KEY, back.id());
            Path stored = file();
            Files.createDirectories(stored.getParent());
            Files.writeString(stored,
                new GsonBuilder().setPrettyPrinting().create().toJson(json),
                StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            // A cosmetic preference is not worth crashing over, and the next
            // launch simply starts from the default again.
        }
    }

    static
    {
        try
        {
            Path stored = file();
            if(Files.isRegularFile(stored))
            {
                JsonObject json = new Gson().fromJson(
                    Files.readString(stored, StandardCharsets.UTF_8), JsonObject.class);
                if(json != null && json.has(KEY))
                {
                    // byId, so a file written by a build that ships a back this
                    // one does not have degrades to the default rather than
                    // leaving the duel screen pointed at a texture that is not
                    // there -- which draws magenta, not nothing.
                    back = byId(json.get(KEY).getAsString());
                }
            }
        }
        catch(IOException | RuntimeException unreadable)
        {
            // Unreadable, or holding something that is not a string. Either way
            // the default is what the file would have said.
        }
    }
}
