package de.cas_ual_ty.dueldimension.ocg.bot.executor;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;

/**
 * One card on the field as a rule sees it — WindBot's {@code ClientCard}.
 * <p>
 * The predicates ported from {@code DefaultExecutor} are written against this
 * vocabulary ({@code IsAttack()}, {@code GetDefensePower()}, {@code IsCode()}),
 * so providing the same vocabulary is what lets those rules be transliterated
 * rather than reinterpreted.
 * <p>
 * Values come from the core's own query via {@code BoardState}, so ATK and DEF
 * are the CURRENT values including every continuous effect — not the printed
 * ones. A card the viewer may not identify arrives with code 0.
 */
public final class BotCard
{
    private final int code;
    private final int position;
    private final int type;
    private final int level;
    private final int attack;
    private final int defense;
    private final int controller;
    private final int location;
    private final int sequence;

    /**
     * {@code ClientCard.RealPower} — scratch space that
     * {@code OnPreBattleBetween} adjusts before the comparison in
     * {@code OnSelectAttackTarget}, so a defender's effective size can differ
     * from its printed one.
     */
    public int realPower;

    public BotCard(int code, int position, int type, int level, int attack, int defense,
        int controller, int location, int sequence)
    {
        this.code = code;
        this.position = position;
        this.type = type;
        this.level = level;
        this.attack = attack;
        this.defense = defense;
        this.controller = controller;
        this.location = location;
        this.sequence = sequence;
    }

    public int code()
    {
        return code;
    }

    public int position()
    {
        return position;
    }

    public int type()
    {
        return type;
    }

    public int level()
    {
        return level;
    }

    /** Current ATK. The core's -1 ("you may not know") reads as 0, as WindBot's unknown cards do. */
    public int attack()
    {
        return Math.max(attack, 0);
    }

    public int defense()
    {
        return Math.max(defense, 0);
    }

    public int controller()
    {
        return controller;
    }

    public int location()
    {
        return location;
    }

    public int sequence()
    {
        return sequence;
    }

    /** {@code public bool IsFaceup() { return (Position & (int)CardPosition.FaceUp) > 0; }} */
    public boolean isFaceUp()
    {
        return (position & (OcgConstants.POS_FACEUP_ATTACK | OcgConstants.POS_FACEUP_DEFENSE)) != 0;
    }

    public boolean isFaceDown()
    {
        return !isFaceUp();
    }

    /** {@code public bool IsAttack() { return (Position & (int)CardPosition.Attack) > 0; }} */
    public boolean isAttack()
    {
        return (position & (OcgConstants.POS_FACEUP_ATTACK | OcgConstants.POS_FACEDOWN_ATTACK)) != 0;
    }

    public boolean isDefense()
    {
        return !isAttack();
    }

    public boolean isMonster()
    {
        return (type & OcgConstants.TYPE_MONSTER) != 0;
    }

    public boolean isSpell()
    {
        return (type & OcgConstants.TYPE_SPELL) != 0;
    }

    public boolean isTrap()
    {
        return (type & OcgConstants.TYPE_TRAP) != 0;
    }

    public boolean hasType(int mask)
    {
        return (type & mask) != 0;
    }

    /**
     * {@code public bool IsCode(long id) { return Id == id || Alias != 0 && Alias == id; }}
     * <p>
     * Note this is NOT what the activation gate uses. {@code ShouldExecute}
     * compares {@code exec.CardId == card.Id} raw, without alias resolution —
     * a distinction that matters for reprinted cards, so rules written against
     * a passcode must use the same passcode the core reports.
     */
    public boolean isCode(int id)
    {
        return code == id;
    }

    public boolean isCode(int... ids)
    {
        for(int id : ids)
        {
            if(code == id)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code public int GetDefensePower() { return IsAttack() ? Attack : Defense; }}
     * <p>
     * The single most load-bearing helper in the ported rules: every
     * "is the enemy better" test is expressed in these terms.
     */
    public int getDefensePower()
    {
        return isAttack() ? attack() : defense();
    }
}
