package de.cas_ual_ty.dueldimension.duel.outfit;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every shipped outfit skin is on the layout the game can read.
 * <p>
 * A 64x32 skin is the old format, where both arms and both legs were drawn from
 * one mirrored half. The 1.19 player model reads the left limbs from regions
 * that format does not have, so such a skin renders with empty texture down one
 * side. {@code tools/convert_skin.py} converts one; this catches the case where
 * somebody drops a file in and forgets.
 * <p>
 * Deliberately NOT checked here: whether a skin is slim or classic. It looks
 * checkable — a slim skin's front and back arm faces are three pixels wide, so
 * the last two columns of each arm band belong to no face and should be empty —
 * and that is exactly the reasoning that got it wrong. A slim skin exported from
 * a classic template keeps whatever was in that dead space, and Kaiba's has
 * paint there. Measuring it called a slim skin classic, which rendered it on the
 * wrong body and put those leftovers back on the arm as a stray column that
 * could not be erased, because it was not in any face the artist was editing.
 * The skin's author knows which body they drew for; the pixels only look like
 * they do.
 */
class OutfitSkinFormatTest
{
    private static final Path FOLDER = Path.of("src", "main", "resources", "assets",
        "dueldimension", "textures", "entity", "outfit");

    @Test
    void everyOutfitSkinIsOnTheModernLayout() throws Exception
    {
        List<String> checked = new ArrayList<>();
        try(var files = Files.list(FOLDER))
        {
            for(Path file : files.toList())
            {
                String name = file.getFileName().toString();
                if(!name.endsWith(".png"))
                {
                    continue;
                }
                BufferedImage skin = ImageIO.read(file.toFile());
                assertEquals(64, skin.getWidth(), name + " must be a 64-wide skin");
                assertEquals(64, skin.getHeight(),
                    name + " is on the old 64x32 layout, which has no left arm or leg of its"
                        + " own. Convert it: python tools/convert_skin.py in.png out.png");
                checked.add(name);
            }
        }
        assertFalse(checked.isEmpty(), "no outfit skins found under " + FOLDER);
    }
}
