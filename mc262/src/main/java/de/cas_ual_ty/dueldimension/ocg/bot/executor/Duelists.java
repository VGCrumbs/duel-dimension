package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import java.util.function.Supplier;

/**
 * The duelists, and the registry that picks one by profile id.
 * <p>
 * This is WindBot's {@code DecksManager} with the guesswork removed. There, an
 * unrecognised deck name loads a <em>random</em> executor for some other deck:
 * <pre>
 * do { infos = _list[_rand.Next(_list.Count)]; }
 * while (infos.Level != "Normal");
 * Logger.WriteLine("Deck not found, loading random: " + infos.Deck);
 * </pre>
 * That is reasonable for a bot that must field 43 known decks and never sees a
 * stranger, and disastrous here: it would play a starter deck with rules keyed
 * to passcodes it does not contain. An unknown profile gets {@link Generic}
 * instead, which is the same rules minus the personality.
 */
public final class Duelists
{
    /**
     * The house duelist: every reference card rule, and generic play. This is
     * what an unrecognised profile gets, and the baseline a named duelist
     * differs from.
     */
    public static final class Generic extends DuelistExecutor
    {
        public Generic()
        {
            addReferenceCardRules();
            addHouseCardRules();
            addGenericPlay();
        }
    }

    /**
     * Yugi: spellcasters and control. Registers the reference rules first, so
     * his removal resolves before he commits a body.
     */
    public static final class Yugi extends DuelistExecutor
    {
        public Yugi()
        {
            addReferenceCardRules();
            addHouseCardRules();
            addGenericPlay();
        }
    }

    /**
     * Kaiba: beatdown. Same rules, but the generic clause comes first for
     * summons, so he puts a body down before he spends removal — the
     * personality lives in the ORDER, which is the only thing WindBot uses to
     * express priority.
     */
    public static final class Kaiba extends DuelistExecutor
    {
        public Kaiba()
        {
            addExecutor(ExecutorType.SUMMON_OR_SET, this::defaultMonsterSummon);
            addReferenceCardRules();
            addHouseCardRules();
            addGenericPlay();
        }
    }

    /**
     * Joey: warriors and swarm. Sets his backrow before committing monsters,
     * so his traps are live earlier.
     */
    public static final class Joey extends DuelistExecutor
    {
        private static final int TIME_WIZARD = 71625222;
        private static final int DESPERATION_LIFE_POINTS = 2000;

        public Joey()
        {
            // Joey's signature gamble. These precede generic play so he both
            // puts Time Wizard face-up and uses it before choosing a safer,
            // ordinary summon when the duel has turned against him.
            addExecutor(ExecutorType.ACTIVATE, TIME_WIZARD, this::timeWizardDesperation);
            addExecutor(ExecutorType.SUMMON_OR_SET, TIME_WIZARD, this::timeWizardDesperation);
            addExecutor(ExecutorType.SPELL_SET, this::defaultSpellSet);
            addReferenceCardRules();
            addHouseCardRules();
            addGenericPlay();
        }

        /**
         * Take Time Wizard's coin toss when Joey has no favourable board and
         * at least one concrete sign that ordinary play is unlikely to save
         * him: apparent lethal damage, quarter life, or a two-monster deficit.
         * The stronger-enemy requirement keeps him from gambling away a board
         * he can already contest.
         */
        private boolean timeWizardDesperation()
        {
            if(duelPlayer() != 0 || enemy().getMonsters().isEmpty()
                || util.getBestPower(enemy()) <= util.getBestPower(bot()))
            {
                return false;
            }

            boolean facingLethal = util.getTotalAttackingMonsterAttack(enemy()) >= bot().lifePoints;
            boolean lowLife = bot().lifePoints <= DESPERATION_LIFE_POINTS;
            boolean overwhelmed = enemy().getMonsterCount() >= bot().getMonsterCount() + 2;
            return facingLethal || lowLife || overwhelmed;
        }

        /** Time Wizard must be face-up to turn the duel into a coin toss. */
        @Override
        public boolean onSelectMonsterSummonOrSet(BotCard card)
        {
            if(card != null && card.isCode(TIME_WIZARD) && timeWizardDesperation())
            {
                return false;
            }
            return super.onSelectMonsterSummonOrSet(card);
        }
    }

    private Duelists()
    {
    }

    /**
     * The executor for a duelist profile. Unknown ids get {@link Generic}
     * rather than a random other duelist's rules.
     */
    public static Executor forProfile(String profileId)
    {
        Supplier<Executor> supplier = switch(profileId == null ? "" : profileId)
        {
            case "yugi" -> Yugi::new;
            case "kaiba" -> Kaiba::new;
            case "joey" -> Joey::new;
            default -> Generic::new;
        };
        return supplier.get();
    }
}
