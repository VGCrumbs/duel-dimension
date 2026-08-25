package de.cas_ual_ty.dueldimension.ocg;

/**
 * The engine's race bits, said in the words printed on a card.
 * <p>
 * The core answers {@code QUERY_RACE} with a 64-bit mask rather than a name,
 * and the mask is what changes when an effect makes a monster something else.
 * Turning it back into "Insect" is the only way to put an engine answer next to
 * a database one and see whether they agree.
 * <p>
 * A mask, not an index, and the plural is not hypothetical: an effect may ADD a
 * race without removing the old one, and a card that is both is a card whose
 * description should say both. They are joined the way the card text joins
 * them.
 * <p>
 * The names match {@code Species} exactly where the two overlap, because the
 * whole point is to compare them as strings -- one comes from the engine and
 * one from the card database, and a spelling difference would read as a type
 * change on every monster in the game. The few races with no Species of their
 * own are the Rush and anime ones, named here so an unknown bit is still
 * printed rather than silently dropped.
 */
public final class Races
{
    private Races()
    {
    }

    private static final long[] BITS = {
        OcgConstants.RACE_WARRIOR, OcgConstants.RACE_SPELLCASTER, OcgConstants.RACE_FAIRY,
        OcgConstants.RACE_FIEND, OcgConstants.RACE_ZOMBIE, OcgConstants.RACE_MACHINE,
        OcgConstants.RACE_AQUA, OcgConstants.RACE_PYRO, OcgConstants.RACE_ROCK,
        OcgConstants.RACE_WINGEDBEAST, OcgConstants.RACE_PLANT, OcgConstants.RACE_INSECT,
        OcgConstants.RACE_THUNDER, OcgConstants.RACE_DRAGON, OcgConstants.RACE_BEAST,
        OcgConstants.RACE_BEASTWARRIOR, OcgConstants.RACE_DINOSAUR, OcgConstants.RACE_FISH,
        OcgConstants.RACE_SEASERPENT, OcgConstants.RACE_REPTILE, OcgConstants.RACE_PSYCHIC,
        OcgConstants.RACE_DIVINE, OcgConstants.RACE_CREATORGOD, OcgConstants.RACE_WYRM,
        OcgConstants.RACE_CYBERSE, OcgConstants.RACE_ILLUSION, OcgConstants.RACE_CYBORG,
        OcgConstants.RACE_MAGICALKNIGHT, OcgConstants.RACE_HIGHDRAGON,
        OcgConstants.RACE_OMEGAPSYCHIC, OcgConstants.RACE_CELESTIALWARRIOR,
        OcgConstants.RACE_GALAXY, OcgConstants.RACE_YOKAI};

    private static final String[] NAMES = {
        "Warrior", "Spellcaster", "Fairy", "Fiend", "Zombie", "Machine", "Aqua", "Pyro", "Rock",
        "Winged Beast", "Plant", "Insect", "Thunder", "Dragon", "Beast", "Beast-Warrior",
        "Dinosaur", "Fish", "Sea Serpent", "Reptile", "Psychic", "Divine-Beast", "Creator-God",
        "Wyrm", "Cyberse", "Illusion", "Cyborg", "Magical Knight", "High Dragon",
        "Omega Psychic", "Celestial Warrior", "Galaxy", "Yokai"};

    /**
     * What this mask is called, or an empty string for none.
     *
     * @param race the engine's mask; 0 for a card with no race, which is every
     *             spell, every trap, and every card nobody may identify
     */
    public static String name(long race)
    {
        if(race == 0L)
        {
            return "";
        }
        StringBuilder said = new StringBuilder();
        for(int bit = 0; bit < BITS.length; bit++)
        {
            if((race & BITS[bit]) == 0L)
            {
                continue;
            }
            if(said.length() > 0)
            {
                said.append(" / ");
            }
            said.append(NAMES[bit]);
        }
        return said.toString();
    }
}
