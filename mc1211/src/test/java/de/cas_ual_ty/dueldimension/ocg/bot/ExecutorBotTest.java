package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.CardExecutor;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.Duelists;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.Executor;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.ExecutorBot;
import de.cas_ual_ty.dueldimension.ocg.bot.executor.ExecutorType;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.session.DuelSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the executor architecture to the property that makes it trustworthy: a
 * card is played only if a duelist registered a rule for it.
 * <p>
 * The scoring bot this replaces could not offer that guarantee, because a
 * score is a number and every card had one. Here the guarantee is structural —
 * {@code ExecutorBot.shouldExecute} needs a matching entry to exist — so it can
 * be checked directly against what each duelist registered.
 */
class ExecutorBotTest
{
    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    private static Path cdb()
    {
        return Path.of(System.getProperty("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"));
    }

    /** Reverse Trap: the card that prompted this rebuild. WindBot has no rule for it. */
    private static final int REVERSE_TRAP = 77622396;
    private static final int REINFORCEMENTS = 17814387;
    private static final int SHIELD_AND_SWORD = 52097679;
    private static final int TWO_PRONGED_ATTACK = 83887306;

    @Test
    void noDuelistRegistersACardTheReferenceHasNoRuleFor()
    {
        // A pure structural check: no engine needed, just the registrations.
        for(String profile : new String[] {"yugi", "kaiba", "joey", "unknown-profile"})
        {
            Executor executor = Duelists.forProfile(profile);
            Set<Integer> activatable = new HashSet<>();
            for(CardExecutor exec : executor.executors())
            {
                if(exec.type() == ExecutorType.ACTIVATE && exec.cardId() != CardExecutor.ANY)
                {
                    activatable.add(exec.cardId());
                }
            }
            // Reverse Trap is the card that started this: neither the
            // reference nor any house rule covers it, so nothing may fire it.
            assertTrue(!activatable.contains(REVERSE_TRAP),
                profile + " registered an activation rule for Reverse Trap, which nothing has a rule for");
            // These three DO have house rules now, deliberately and separately
            // from the ported ones. Pinned so the distinction stays visible.
            for(int code : new int[] {REINFORCEMENTS, SHIELD_AND_SWORD, TWO_PRONGED_ATTACK})
            {
                assertTrue(activatable.contains(code),
                    profile + " lost its house rule for " + code);
            }
            assertTrue(!executor.executors().isEmpty(), profile + " registered nothing at all");
        }
    }

    @Test
    void anUnknownProfileGetsTheHouseDuelistRatherThanARandomOne()
    {
        // WindBot's DecksManager loads a RANDOM other deck's executor when the
        // deck name is unknown. That is the one piece of it we deliberately do
        // not copy, so this pins the divergence.
        Executor first = Duelists.forProfile("no-such-duelist");
        Executor second = Duelists.forProfile("also-no-such-duelist");
        assertEquals(first.getClass(), second.getClass(),
            "an unknown profile must resolve deterministically, not to a random duelist");
        assertEquals(Duelists.Generic.class, first.getClass());
    }

    @Test
    void theDuelistsActuallyPlayADuelThrough() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        int totalActivations = 0;
        int totalAttacks = 0;
        int totalSummons = 0;
        List<Integer> unregistered = new ArrayList<>();

        long[] seeds = {5150L, 271828L, 31415L, 16180L, 9001L, 4242L};
        for(long seed : seeds)
        {
            DuelSession session = DuelSession.create("executor-" + seed, api, OcgConstants.DUEL_MODE_MR5,
                new long[] {seed, seed ^ 0x9E3779B9L, seed * 31 + 7, seed * 17 + 3},
                cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
                StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
                new ExecutorBot(seed + 1, Duelists.forProfile("yugi"), cards, cards.all()),
                new ExecutorBot(seed + 2, Duelists.forProfile("joey"), cards, cards.all()));

            Set<Integer> registered = registeredActivations();

            session.start();
            List<DuelMessage> stream = new ArrayList<>();
            long deadline = System.nanoTime() + 60_000_000_000L;
            while(session.isRunning() && System.nanoTime() < deadline)
            {
                session.drainEvents(event -> collect(event, stream));
                Thread.sleep(5);
            }
            session.drainEvents(event -> collect(event, stream));

            for(DuelMessage message : stream)
            {
                if(message instanceof DuelMessage.Chaining chaining)
                {
                    totalActivations++;
                    // Monsters are exempt: their effects are not registered by
                    // passcode, and the core offers them through prompts the
                    // executor list does not gate.
                    var data = cards.get(chaining.code());
                    boolean spellOrTrap = data != null
                        && (data.type() & (OcgConstants.TYPE_SPELL | OcgConstants.TYPE_TRAP)) != 0;
                    if(spellOrTrap && !registered.contains(chaining.code()))
                    {
                        unregistered.add(chaining.code());
                    }
                }
                else if(message instanceof DuelMessage.Attack)
                {
                    totalAttacks++;
                }
                else if(message instanceof DuelMessage.Summoning
                    || message instanceof DuelMessage.SpSummoning)
                {
                    totalSummons++;
                }
            }
        }

        System.out.println("executor bots over " + seeds.length + " duels:");
        System.out.println("  summons=" + totalSummons + " attacks=" + totalAttacks
            + " activations=" + totalActivations);
        System.out.println("  spells/traps fired with no registered rule: "
            + unregistered.size() + " " + unregistered);

        // The duelists must actually PLAY. A bot that registers nothing would
        // pass the discipline check trivially by doing nothing at all, so this
        // is the guard against "fixed it by making it passive".
        assertTrue(totalSummons > 0, "the duelists never summoned anything");
        assertTrue(totalAttacks > 0, "the duelists never attacked");
        assertTrue(unregistered.isEmpty(),
            "spells/traps were activated without a registered rule: " + unregistered);
    }

    private static Set<Integer> registeredActivations()
    {
        Set<Integer> codes = new HashSet<>();
        for(String profile : new String[] {"yugi", "joey"})
        {
            for(CardExecutor exec : Duelists.forProfile(profile).executors())
            {
                if(exec.type() == ExecutorType.ACTIVATE && exec.cardId() != CardExecutor.ANY)
                {
                    codes.add(exec.cardId());
                }
            }
        }
        return codes;
    }

    private static void collect(DuelSession.Event event, List<DuelMessage> into)
    {
        if(event instanceof DuelSession.Event.Message message)
        {
            DuelMessage decoded = DuelMessage.decode(message.message());
            if(decoded != null)
            {
                into.add(decoded);
            }
        }
    }
}
