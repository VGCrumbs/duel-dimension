package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the ported command bitmask and its captions to the EDOPro source they
 * came from (gframe/game.h COMMAND_*, gframe/event_handler.cpp ShowMenu).
 */
class CardCommandsTest
{
    private static Path stringsConf()
    {
        return Path.of(System.getProperty("ocg.strings", "C:/ProjectIgnis/config/strings.conf"));
    }

    @Test
    void bitValuesMatchGameHeader()
    {
        assertEquals(0x0001, CardCommands.COMMAND_ACTIVATE);
        assertEquals(0x0002, CardCommands.COMMAND_SUMMON);
        assertEquals(0x0004, CardCommands.COMMAND_SPSUMMON);
        assertEquals(0x0008, CardCommands.COMMAND_MSET);
        assertEquals(0x0010, CardCommands.COMMAND_SSET);
        assertEquals(0x0020, CardCommands.COMMAND_REPOS);
        assertEquals(0x0040, CardCommands.COMMAND_ATTACK);
        assertEquals(0x0080, CardCommands.COMMAND_LIST);
        assertEquals(0x0100, CardCommands.COMMAND_OPERATION);
        assertEquals(0x0200, CardCommands.COMMAND_RESET);
    }

    /** ShowMenu adds the buttons in this order, top to bottom. */
    @Test
    void menuOrderMatchesShowMenu()
    {
        assertEquals(0, CardCommands.menuIndex(CardCommands.COMMAND_ACTIVATE));
        assertEquals(1, CardCommands.menuIndex(CardCommands.COMMAND_SUMMON));
        assertEquals(2, CardCommands.menuIndex(CardCommands.COMMAND_SPSUMMON));
        assertEquals(3, CardCommands.menuIndex(CardCommands.COMMAND_MSET));
        assertEquals(4, CardCommands.menuIndex(CardCommands.COMMAND_SSET));
        assertEquals(5, CardCommands.menuIndex(CardCommands.COMMAND_REPOS));
        assertEquals(6, CardCommands.menuIndex(CardCommands.COMMAND_ATTACK));
        assertEquals(21, CardCommands.MENU_ROW_HEIGHT, "ShowMenu steps by Scale(21)");
    }

    @Test
    void captionsMatchTheReferenceStrings() throws Exception
    {
        assumeTrue(Files.isRegularFile(stringsConf()), "strings.conf not present");
        DescriptionTable text = new DescriptionTable(stringsConf(), List.of());
        int monster = 0x1;
        int spell = 0x2;

        assertEquals("Activate", CardCommands.label(CardCommands.COMMAND_ACTIVATE, monster, 0, text));
        assertEquals("Normal Summon", CardCommands.label(CardCommands.COMMAND_SUMMON, monster, 0, text));
        assertEquals("Special Summon", CardCommands.label(CardCommands.COMMAND_SPSUMMON, monster, 0, text));
        assertEquals("Set", CardCommands.label(CardCommands.COMMAND_MSET, monster, 0, text));

        // ShowMenu: non-monsters read "Set", monsters "S/T Set".
        assertEquals("Set", CardCommands.label(CardCommands.COMMAND_SSET, spell, 0, text));
        assertEquals("S/T Set", CardCommands.label(CardCommands.COMMAND_SSET, monster, 0, text));

        // Reposition caption depends on the card's current position.
        assertEquals("Flip Summon", CardCommands.label(CardCommands.COMMAND_REPOS, monster,
            OcgConstants.POS_FACEDOWN_DEFENSE, text));
        assertEquals("To Defense", CardCommands.label(CardCommands.COMMAND_REPOS, monster,
            OcgConstants.POS_FACEUP_ATTACK, text));
        assertEquals("To Attack", CardCommands.label(CardCommands.COMMAND_REPOS, monster,
            OcgConstants.POS_FACEUP_DEFENSE, text));

        assertEquals("Attack", CardCommands.label(CardCommands.COMMAND_ATTACK, monster, 0, text));
        assertEquals("View", CardCommands.label(CardCommands.COMMAND_LIST, 0, 0, text));
        assertTrue(CardCommands.label(CardCommands.COMMAND_OPERATION, 0, 0, text).startsWith("Resolve"));
    }
}
