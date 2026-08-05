package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.query.BoardState;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's half of the table — WindBot's {@code ClientField}, the object
 * its rules call {@code Bot} and {@code Enemy}.
 * <p>
 * Zone lists are positional and may contain nulls, exactly as
 * {@code MonsterZone[j]} does, because several ported rules index them
 * directly: {@code DefaultField} is literally {@code Bot.SpellZone[5] == null}
 * and {@code DefaultMonsterSummon} walks {@code MonsterZone[0..6]}.
 */
public final class BotField
{
    /** {@code ClientField.MonsterZone}: 5 main zones plus 2 extra monster zones. */
    public final List<BotCard> monsterZone;
    /** {@code ClientField.SpellZone}: 5 backrow, index 5 the field zone, 6-7 pendulum. */
    public final List<BotCard> spellZone;
    public final List<BotCard> hand;
    public final List<BotCard> graveyard;
    public final List<BotCard> banished;
    public final List<BotCard> extra;
    public final int lifePoints;
    public final int deckCount;

    private BotField(List<BotCard> monsterZone, List<BotCard> spellZone, List<BotCard> hand,
        List<BotCard> graveyard, List<BotCard> banished, List<BotCard> extra, int lifePoints, int deckCount)
    {
        this.monsterZone = monsterZone;
        this.spellZone = spellZone;
        this.hand = hand;
        this.graveyard = graveyard;
        this.banished = banished;
        this.extra = extra;
        this.lifePoints = lifePoints;
        this.deckCount = deckCount;
    }

    /**
     * Builds a field from the honest per-player view. Static card data (type,
     * level) is filled from the database, because the core's board query
     * reports position and stats but the rules also ask "is this a trap?".
     */
    public static BotField of(BoardState.PlayerBoard board, int controller, OcgDuel.CardProvider cards)
    {
        return new BotField(
            zone(board.monsters(), controller, de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE, cards),
            zone(board.spells(), controller, de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE, cards),
            zone(board.hand(), controller, de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_HAND, cards),
            zone(board.grave(), controller, de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE, cards),
            zone(board.banished(), controller, de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_REMOVED, cards),
            zone(board.extra(), controller, de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_EXTRA, cards),
            board.lifePoints(), board.deckCount());
    }

    private static List<BotCard> zone(List<CardView> views, int controller, int location,
        OcgDuel.CardProvider cards)
    {
        List<BotCard> result = new ArrayList<>(views.size());
        for(int i = 0; i < views.size(); i++)
        {
            CardView view = views.get(i);
            if(view == null)
            {
                result.add(null);
                continue;
            }
            // The query gives live ATK/DEF and position; the database gives
            // type and level, which the query does not carry for every zone.
            OcgCard data = view.code() == 0 ? null : cards.get(view.code());
            int type = view.type() != 0 ? view.type() : data != null ? data.type() : 0;
            int level = view.level() != 0 ? view.level() : data != null ? data.level() : 0;
            result.add(new BotCard(view.code(), view.position(), type, level,
                view.attack(), view.defense(), controller, location, i));
        }
        return result;
    }

    /** {@code public List<ClientCard> GetMonsters() { return GetCards(MonsterZone); }} */
    public List<BotCard> getMonsters()
    {
        return present(monsterZone);
    }

    public List<BotCard> getSpells()
    {
        return present(spellZone);
    }

    public List<BotCard> getHand()
    {
        return present(hand);
    }

    public List<BotCard> getGraveyard()
    {
        return present(graveyard);
    }

    public int getMonsterCount()
    {
        return getMonsters().size();
    }

    /**
     * {@code GetSpellCountWithoutField}: only the five backrow zones, because
     * the field zone does not compete for space with a set card. This is the
     * number {@code DefaultSpellSet} caps at 4.
     */
    public int getSpellCountWithoutField()
    {
        int count = 0;
        for(int i = 0; i < 5 && i < spellZone.size(); i++)
        {
            if(spellZone.get(i) != null)
            {
                count++;
            }
        }
        return count;
    }

    /**
     * {@code public bool HasAttackingMonster() { return GetMonsters().Any(card => card.IsAttack()); }}
     * <p>
     * The whole of WindBot's rule for entering the battle phase.
     */
    public boolean hasAttackingMonster()
    {
        for(BotCard card : getMonsters())
        {
            if(card.isAttack())
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasInMonstersZone(int cardId)
    {
        for(BotCard card : getMonsters())
        {
            if(card.isCode(cardId))
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasInSpellZone(int cardId)
    {
        for(BotCard card : getSpells())
        {
            if(card.isCode(cardId))
            {
                return true;
            }
        }
        return false;
    }

    private static List<BotCard> present(List<BotCard> zone)
    {
        List<BotCard> result = new ArrayList<>();
        for(BotCard card : zone)
        {
            if(card != null)
            {
                result.add(card);
            }
        }
        return result;
    }
}
