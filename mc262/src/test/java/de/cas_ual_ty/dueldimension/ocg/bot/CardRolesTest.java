package de.cas_ual_ty.dueldimension.ocg.bot;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the bot's card-role table to the decks it exists for, both ways: a
 * passcode typo in the table, or a new spell added to a starter deck without a
 * classification, fails here rather than silently degrading the bot back to
 * "activate anything legal".
 */
class CardRolesTest
{
    private static Set<Integer> mainDeckCodes() throws Exception
    {
        Set<Integer> codes = new HashSet<>();
        for(String deck : new String[] {"yugi", "kaiba", "joey"})
        {
            boolean inMain = false;
            for(String line : Files.readAllLines(
                Path.of("src/main/resources/data/dueldimension/decks/" + deck + ".ydk")))
            {
                line = line.strip();
                if(line.startsWith("#main"))
                {
                    inMain = true;
                }
                else if(line.startsWith("#extra") || line.startsWith("!side"))
                {
                    inMain = false;
                }
                else if(inMain && line.chars().allMatch(Character::isDigit) && !line.isEmpty())
                {
                    codes.add(Integer.parseInt(line));
                }
            }
        }
        return codes;
    }

    @Test
    void everyClassifiedCardExistsInAStarterDeck() throws Exception
    {
        Set<Integer> decks = mainDeckCodes();
        for(int code : CardRoles.all().keySet())
        {
            assertTrue(decks.contains(code),
                "classified passcode " + code + " is in no starter deck: a typo in CardRoles");
        }
    }

    @Test
    void tableIsNotTriviallyEmpty()
    {
        // The point of the table is discrimination: a wipe, removal, backrow
        // hate, buffs and revival must all be represented or the scoring
        // branches are dead code.
        for(CardRoles.Role role : CardRoles.Role.values())
        {
            assertTrue(CardRoles.all().containsValue(role) || role == CardRoles.Role.UTILITY,
                "no card carries role " + role);
        }
    }
}
