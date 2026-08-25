package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.MonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.util.GameDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Moving a monster built in the editor into the mod itself.
 * <p>
 * The editor writes to {@code config/}, which is one folder on one machine.
 * That is right for a player's own tweaks and wrong for work meant to ship:
 * a sprite authored here reached nobody, and the only way to publish one was
 * for a person to retype it as source. This copies the sheet into the mod's
 * resources, files it under the card's type, and writes the definition into the
 * shipped list -- after which it is in the jar like any other.
 * <p>
 * Only in a development tree. A packaged install has no {@code src/} to write
 * to, and the button that calls this says so rather than appearing to work.
 * <p>
 * <b>The type folder is asked of the database, not of the person.</b> The
 * README says a sheet goes in the folder for the card's type, which means the
 * answer is already written down on the card -- and a folder chosen by hand is
 * a folder that disagrees with the next person's choice for the same monster.
 */
public final class SpriteSource
{
    private SpriteSource()
    {
    }

    /**
     * The mod's own asset folder in the source tree, or null when there is none.
     * <p>
     * Found from the game directory rather than from a configured path: in a
     * development client the game runs in {@code run/}, so the tree is its
     * parent. Anywhere else this returns null and every caller is expected to
     * treat that as "not available" rather than as an error.
     */
    public static Path resources()
    {
        try
        {
            Path assets = GameDir.path().getParent()
                .resolve("src").resolve("main").resolve("resources")
                .resolve("assets").resolve(DuelDimension.MOD_ID);
            return Files.isDirectory(assets) ? assets : null;
        }
        catch(Throwable outsideATree)
        {
            return null;
        }
    }

    public static boolean available()
    {
        return resources() != null;
    }

    /**
     * The folder a card's sheet belongs in: its monster type, lowercased.
     * <p>
     * "Beast-Warrior" becomes {@code beast_warrior} and "Winged Beast" becomes
     * {@code winged_beast}, which is the convention the shipped folders already
     * follow. A card the database does not know, or one that is not a monster,
     * gets {@code other} rather than nothing -- a sheet in the wrong folder is
     * a thing somebody can move, and a sheet in no folder is a crash.
     */
    public static String folderFor(long code)
    {
        Properties card = DdDatabase.PROPERTIES_LIST.get(code);
        if(card instanceof MonsterProperties monster && monster.species != null
            && !monster.species.isBlank())
        {
            String folder = monster.species.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if(!folder.isBlank())
            {
                return folder;
            }
        }
        return "other";
    }

    /**
     * What a promotion did, and whether it worked.
     * <p>
     * Two fields rather than one string, because the caller shows this in a
     * colour and had no other way to tell "saved to mod" from "could not write
     * the list" than by reading the English and guessing.
     */
    public record Outcome(boolean ok, String message)
    {
        static Outcome bad(String message)
        {
            return new Outcome(false, message);
        }
    }

    /**
     * Writes a card's definition and its sheets into the mod's own source.
     *
     * @return a line to show the person who pressed the button, always, and
     *         whether it is good news
     */
    public static Outcome promote(long code)
    {
        Path assets = resources();
        if(assets == null)
        {
            return Outcome.bad("no source tree here");
        }
        MonsterSprites.Definition definition = MonsterSprites.of(code);
        if(definition == null)
        {
            return Outcome.bad("nothing to save yet");
        }

        String folder = folderFor(code);
        Path monsters = assets.resolve("textures").resolve("duel").resolve("monsters")
            .resolve(folder);

        // Distinct, because a body and its wings usually share one file and
        // copying it twice is a way of copying it wrong once.
        Set<String> sheets = new LinkedHashSet<>();
        // Absent for a monster that is a model and no sprite, which is a
        // definition with nothing here to copy rather than a broken one.
        if(definition.body() != null)
        {
            sheets.add(definition.body().sheet());
        }
        if(definition.defence() != null)
        {
            sheets.add(definition.defence().sheet());
        }
        if(definition.wings() != null && definition.wings().layer() != null)
        {
            sheets.add(definition.wings().layer().sheet());
        }

        int copied = 0;
        for(String sheet : sheets)
        {
            // A name with a folder in it is already filed where it belongs --
            // it came from the shipped set, not from an import.
            if(sheet.contains("/"))
            {
                continue;
            }
            Path from = MonsterSheets.folder().resolve(sheet + ".png");
            if(!Files.isRegularFile(from))
            {
                return Outcome.bad("cannot find " + sheet + ".png to copy");
            }
            try
            {
                Files.createDirectories(monsters);
                Files.copy(from, monsters.resolve(sheet + ".png"),
                    StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            catch(Exception unwritable)
            {
                DuelDimension.warn("could not copy " + from + ": " + unwritable);
                return Outcome.bad("could not copy " + sheet + ".png");
            }
        }

        MonsterSprites.Definition filed = refile(definition, folder);
        MonsterSprites.ship(filed);
        MonsterSprites.put(filed);

        try
        {
            Files.writeString(assets.resolve("monster_sprites.json"),
                MonsterSprites.shippedJson() + "\n", StandardCharsets.UTF_8);
        }
        catch(Exception unwritable)
        {
            DuelDimension.warn("could not write the shipped sprite list: " + unwritable);
            return Outcome.bad("could not write the list");
        }

        // Now that it is part of the shipped list it is no longer a difference
        // from it, so this rewrites the player's file without it.
        MonsterSprites.save();
        return new Outcome(true, "saved to mod: " + folder + " (" + copied + " sheet"
            + (copied == 1 ? "" : "s") + ") -- rebuild to see it");
    }

    /** The same definition with every imported sheet name moved into a folder. */
    private static MonsterSprites.Definition refile(MonsterSprites.Definition definition,
        String folder)
    {
        return new MonsterSprites.Definition(definition.code(),
            refile(definition.body(), folder),
            definition.defence() == null ? null : refile(definition.defence(), folder),
            definition.wings() == null ? null : new Wings(refile(definition.wings().layer(),
                folder), definition.wings().anchor(), definition.wings().spacing(),
                definition.wings().scale()),
            definition.scale(),
            // Carried through: this moves SHEETS into a folder, and a model is
            // not a sheet. Dropping it here would quietly un-model a monster on
            // the way to the mod's own list, and the sprite it fell back to
            // would look like the model had simply failed to load. The same goes
            // for where the model stands -- rebuilding a definition field by
            // field means every field added later has to be added here too, and
            // the ones that are forgotten are the ones that default to a number
            // that looks deliberate.
            definition.model(), definition.animation(),
            definition.elevation(), definition.turn(),
            definition.offsetX(), definition.offsetZ());
    }

    private static SpriteLayer refile(SpriteLayer layer, String folder)
    {
        if(layer == null || layer.sheet().contains("/"))
        {
            return layer;
        }
        return layer.withSheet(folder + "/" + layer.sheet());
    }
}
