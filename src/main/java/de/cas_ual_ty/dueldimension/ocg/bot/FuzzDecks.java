package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed decks for fuzzing and tests. Deliberately hand-picked rather than
 * generated: each one targets a family of prompts, and staying fixed keeps
 * fuzz seeds comparable across runs.
 * <p>
 * Real duelist decks (Joey, Kaiba) arrive in Phase 4 as .ydk files.
 */
public final class FuzzDecks
{
    // Vanilla beatdown: summon / set / position / attack prompts only.
    private static final int GIANT_SOLDIER_OF_STONE = 13039848;
    private static final int MYSTICAL_ELF = 15025844;
    private static final int FERAL_IMP = 41392891;
    private static final int BATTLE_OX = 5053103;

    // Ritual + Synchro: forces MSG_SELECT_SUM (tribute/material totals).
    private static final int BLACK_LUSTER_RITUAL = 55761792;
    private static final int BLACK_LUSTER_SOLDIER = 5405694;
    private static final int JUNK_SYNCHRON = 63977008;
    private static final int DOPPELWARRIOR = 53855409;
    private static final int SONIC_CHICK = 36472900;
    private static final int JUNK_WARRIOR = 60800381;

    private static volatile Path scriptsDir = Path.of("C:/ProjectIgnis/script");

    private FuzzDecks()
    {
    }

    public static void setScriptsDir(Path dir)
    {
        scriptsDir = dir;
    }

    public static Path scriptsDir()
    {
        return scriptsDir;
    }

    public static HeadlessDuelRunner.Deck vanillaBeatdown()
    {
        List<Integer> main = new ArrayList<>(40);
        add(main, GIANT_SOLDIER_OF_STONE, 10);
        add(main, MYSTICAL_ELF, 10);
        add(main, FERAL_IMP, 10);
        add(main, BATTLE_OX, 10);
        return new HeadlessDuelRunner.Deck(main, List.of());
    }

    public static HeadlessDuelRunner.Deck ritualSynchro()
    {
        List<Integer> main = new ArrayList<>(40);
        add(main, BLACK_LUSTER_RITUAL, 3);
        add(main, BLACK_LUSTER_SOLDIER, 3);
        add(main, JUNK_SYNCHRON, 3);
        add(main, DOPPELWARRIOR, 3);
        add(main, SONIC_CHICK, 3);
        add(main, GIANT_SOLDIER_OF_STONE, 13);
        add(main, MYSTICAL_ELF, 12);
        return new HeadlessDuelRunner.Deck(main, List.of(JUNK_WARRIOR, JUNK_WARRIOR, JUNK_WARRIOR));
    }

    private static void add(List<Integer> deck, int code, int copies)
    {
        for(int i = 0; i < copies; i++)
        {
            deck.add(code);
        }
    }
}
