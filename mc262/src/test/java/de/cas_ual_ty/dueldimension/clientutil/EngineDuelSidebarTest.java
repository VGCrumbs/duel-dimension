package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineDuelSidebarTest
{
    @Test
    void monsterHeaderIncludesRaceAlongsideItsClassification()
    {
        LevelMonsterProperties elf = new LevelMonsterProperties();
        elf.type = Type.MONSTER;
        elf.species = "Spellcaster";
        elf.attribute = "LIGHT";
        elf.level = 4;
        elf.atk = 800;
        elf.def = 2_000;
        elf.ability = "";
        elf.hasEffect = false;

        List<String> lines = EngineDuelScreen.sidebarHeader(elf).stream()
            .map(component -> component.getString()).toList();

        assertEquals(List.of("Spellcaster / Normal", "LIGHT / Level 4",
            "800 ATK / 2000 DEF"), lines);
    }

    /**
     * The species line reaches every preview, not just the duel's.
     * <p>
     * The deck editor and the card info page each built their own facts from
     * {@code addHeader}, which omits it, so a monster read as no more than
     * "Effect Monster" in both. They all go through {@code addFacts} now, and
     * this is the guard that they still agree: the sidebar's own expectation
     * above and this one are the same list from the same method.
     */
    @Test
    void everyPreviewAsksTheCardWhatItIs()
    {
        LevelMonsterProperties elf = new LevelMonsterProperties();
        elf.type = Type.MONSTER;
        elf.species = "Spellcaster";
        elf.attribute = "LIGHT";
        elf.level = 4;
        elf.atk = 800;
        elf.def = 2_000;
        elf.ability = "";
        elf.hasEffect = false;

        List<net.minecraft.network.chat.Component> facts = new java.util.ArrayList<>();
        CardPresentation.addFacts(elf, facts);

        assertEquals(EngineDuelScreen.sidebarHeader(elf).stream()
                .map(component -> component.getString()).toList(),
            facts.stream().map(component -> component.getString()).toList());
    }

    /**
     * A card that is not a monster still says what it is.
     * <p>
     * {@code addFacts} is overridden for monsters to replace the plain card
     * type with the species line; everything else must keep the plain one
     * rather than falling through to nothing.
     */
    @Test
    void aSpellStillNamesItsCardType()
    {
        de.cas_ual_ty.dueldimension.card.properties.Properties spell =
            new de.cas_ual_ty.dueldimension.card.properties.Properties();
        spell.type = Type.SPELL;

        List<net.minecraft.network.chat.Component> facts = new java.util.ArrayList<>();
        CardPresentation.addFacts(spell, facts);

        assertEquals(List.of("Spell"),
            facts.stream().map(component -> component.getString()).toList());
    }
}
