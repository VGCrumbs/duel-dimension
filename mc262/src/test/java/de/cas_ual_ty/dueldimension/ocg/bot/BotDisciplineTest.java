package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.session.DuelSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Watches the bot play and counts the specific misplays that were reported:
 * spending a card whose only legal use hurts the bot, and pointing a boost at
 * the wrong monster.
 * <p>
 * These were possible because activation was scored on legality plus a flat
 * "unknown card" value, so anything the roles table did not recognise was
 * fired the moment the engine allowed it — and the engine allows plenty that
 * no player would do. The reference AI, WindBot, never activates a card it has
 * no rule for ({@code GameAI.ShouldExecute} requires a matching executor), and
 * {@link CardRoles} now reproduces that.
 * <p>
 * Everything here is read off the core's own message stream rather than out of
 * the bot's internals, so it measures what actually happened at the table.
 */
class BotDisciplineTest
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

    /** One duel's worth of message stream, replayed into the counters below. */
    private record Report(int activations, List<String> selfHarming, List<String> misaimedBuffs,
        int buffedTurns, int buffedTurnsWithoutBattle)
    {
    }

    @Test
    void theBotNeverSpendsACardItCannotUseWell() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));

        int activations = 0;
        int buffedTurns = 0;
        int buffedTurnsWithoutBattle = 0;
        List<String> selfHarming = new ArrayList<>();
        List<String> misaimedBuffs = new ArrayList<>();

        // Several seeds, because a single duel may never draw the cards that
        // caused the complaint in the first place.
        long[] seeds = {5150L, 271828L, 31415L, 16180L, 9001L, 4242L};
        for(long seed : seeds)
        {
            Report report = play(api, cards, seed);
            activations += report.activations();
            buffedTurns += report.buffedTurns();
            buffedTurnsWithoutBattle += report.buffedTurnsWithoutBattle();
            selfHarming.addAll(report.selfHarming());
            misaimedBuffs.addAll(report.misaimedBuffs());
        }

        System.out.println("discipline: " + activations + " activations across " + seeds.length + " duels");
        System.out.println("  cards spent against their own side: " + selfHarming.size() + " " + selfHarming);
        System.out.println("  boosts landed on the opponent:      " + misaimedBuffs.size() + " " + misaimedBuffs);

        System.out.println("  turns a boost was spent in:          " + buffedTurns
            + ", of which none reached the battle phase: " + buffedTurnsWithoutBattle);

        assumeTrue(activations > 0, "no card was activated in any duel; nothing to judge");
        // "Using spells to boost its attack just to not attack, wasting the
        // card." The main phase used to refuse to enter battle unless it
        // already held a winning attack, which guaranteed a boost played to
        // create one was thrown away. WindBot enters on HasAttackingMonster
        // alone and lets the battle phase judge.
        assertTrue(buffedTurnsWithoutBattle == 0,
            buffedTurnsWithoutBattle + " turns spent a boost and then never entered the battle phase");
        assertTrue(selfHarming.isEmpty(),
            "the bot activated cards it has no good use for: " + selfHarming);
        assertTrue(misaimedBuffs.isEmpty(),
            "the bot pointed a boost at the opponent's monster: " + misaimedBuffs);
    }

    private Report play(OcgApi api, CdbCardProvider cards, long seed) throws Exception
    {
        DuelSession session = DuelSession.create("discipline-" + seed, api, OcgConstants.DUEL_MODE_MR5,
            new long[] {seed, seed ^ 0x9E3779B9L, seed * 31 + 7, seed * 17 + 3},
            cards, HeadlessDuelRunner.cardScriptsDirectory(scripts()),
            StarterDecks.YUGI.load().toRunnerDeck(), StarterDecks.JOEY.load().toRunnerDeck(),
            new HeuristicBot(seed + 1, cards, cards.all()),
            new HeuristicBot(seed + 2, cards, cards.all()));

        session.start();
        List<DuelMessage> stream = new ArrayList<>();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while(session.isRunning() && System.nanoTime() < deadline)
        {
            session.drainEvents(event -> collect(event, stream));
            Thread.sleep(5);
        }
        session.drainEvents(event -> collect(event, stream));

        int activations = 0;
        int buffedTurns = 0;
        int buffedTurnsWithoutBattle = 0;
        boolean buffedThisTurn = false;
        boolean battledThisTurn = false;
        // Whose turn it is, because the misplay being counted is "boosted my
        // own attacker and then did not swing". A boost by the DEFENDING
        // player during the opponent's turn is a battle trick, and whether the
        // turn player entered battle says nothing about it -- counting those
        // reported four misplays that were nobody's.
        int turnPlayer = -1;
        List<String> selfHarming = new ArrayList<>();
        List<String> misaimedBuffs = new ArrayList<>();
        // Who activated what, so a later target message can be attributed to
        // the effect that caused it. Keyed by the activating card's zone.
        Map<String, Integer> activatorOf = new HashMap<>();

        for(DuelMessage message : stream)
        {
            if(message instanceof DuelMessage.NewTurn newTurn)
            {
                if(buffedThisTurn)
                {
                    buffedTurns++;
                    if(!battledThisTurn)
                    {
                        buffedTurnsWithoutBattle++;
                    }
                }
                buffedThisTurn = false;
                battledThisTurn = false;
                turnPlayer = newTurn.player();
            }
            else if(message instanceof DuelMessage.NewPhase phase)
            {
                if((phase.phase() & OcgConstants.PHASE_BATTLE_START) != 0
                    || (phase.phase() & OcgConstants.PHASE_BATTLE) != 0)
                {
                    battledThisTurn = true;
                }
            }

            if(message instanceof DuelMessage.Chaining chaining)
            {
                activations++;
                if(CardRoles.of(chaining.code()) == CardRoles.Role.BUFFS_OWN_MONSTER
                    && chaining.card().controller() == turnPlayer)
                {
                    buffedThisTurn = true;
                }
                CardRoles.Role role = CardRoles.of(chaining.code());
                // Only spells and traps are judged here. A monster's effect
                // costs no card -- the body stays on the field -- so declining
                // one is the loss, and the roles table deliberately does not
                // cover them.
                if(isSpellOrTrap(cards, chaining.code())
                    && (role == CardRoles.Role.SELF_HARMING || role == CardRoles.Role.UTILITY))
                {
                    selfHarming.add(chaining.code() + "/" + role);
                }
                activatorOf.put(zone(chaining.card().controller(), chaining.card().location(),
                    chaining.card().sequence()), chaining.code());
                // A boost is aimed by the same message that equips it, below;
                // Reinforcements and friends target instead of equipping.
            }
            else if(message instanceof DuelMessage.Equip equip)
            {
                Integer code = activatorOf.get(zone(equip.equipCard().controller(),
                    equip.equipCard().location(), equip.equipCard().sequence()));
                misaimed(code, equip.equipCard().controller(), equip.target().controller(), misaimedBuffs);
            }
            else if(message instanceof DuelMessage.CardTarget target)
            {
                Integer code = activatorOf.get(zone(target.source().controller(),
                    target.source().location(), target.source().sequence()));
                misaimed(code, target.source().controller(), target.target().controller(), misaimedBuffs);
            }
        }
        return new Report(activations, selfHarming, misaimedBuffs,
            buffedTurns, buffedTurnsWithoutBattle);
    }

    /**
     * A boost that ends up on a monster the activator does not control is the
     * Reinforcements misplay exactly: legal, and a gift to the opponent.
     */
    private static void misaimed(Integer code, int activator, int targetController, List<String> into)
    {
        if(code != null && CardRoles.of(code) == CardRoles.Role.BUFFS_OWN_MONSTER
            && targetController != activator)
        {
            into.add(code + " -> opponent's monster");
        }
    }

    private static boolean isSpellOrTrap(CdbCardProvider cards, int code)
    {
        var card = cards.get(code);
        return card != null
            && (card.type() & (OcgConstants.TYPE_SPELL | OcgConstants.TYPE_TRAP)) != 0;
    }

    private static String zone(int controller, int location, int sequence)
    {
        return controller + ":" + location + ":" + sequence;
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
