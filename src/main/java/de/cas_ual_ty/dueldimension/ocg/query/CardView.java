package de.cas_ual_ty.dueldimension.ocg.query;

/**
 * One card as a player sees it. When {@code hidden} is true the identity
 * fields are deliberately blank — the card exists and occupies its zone, but
 * this viewer is not entitled to know what it is.
 *
 * @param code     passcode, or 0 when hidden
 * @param position POS_* bits
 * @param type     TYPE_* bits, 0 when hidden
 * @param level    level/rank, 0 when hidden
 * @param attack   current ATK, -1 when hidden or not a monster
 * @param defense  current DEF, -1 when hidden or not a monster
 * @param leftScale  current blue scale, -1 when hidden or not a pendulum card
 * @param rightScale current red scale, -1 when hidden or not a pendulum card
 * @param isPublic whether the core considers this card public knowledge
 * @param hidden   whether this view had its identity stripped for the viewer
 * @param equip    the card this one is equipped TO, or null
 */
public record CardView(int code, int position, int type, int level, int attack, int defense,
    int baseAttack, int baseDefense, int leftScale, int rightScale,
    boolean isPublic, boolean hidden, Equip equip)
{
    public static final CardView HIDDEN =
        new CardView(0, 0, 0, 0, -1, -1, -1, -1, -1, -1, false, true, null);

    /** Whether the engine gave this card a scale, i.e. it is a pendulum card. */
    public boolean hasScale()
    {
        return leftScale >= 0 && rightScale >= 0;
    }

    /**
     * Where an equip card's target sits, straight from QUERY_EQUIP_CARD.
     * <p>
     * The core reports only this direction — {@code card::equiping_target} —
     * and EDOPro builds the reverse set from it ({@code client_card.cpp}:
     * {@code equipTarget = ecard; ecard->equipped.insert(this);}), so a
     * monster's list of equips is derived rather than stored.
     */
    public record Equip(int controller, int location, int sequence)
    {
    }

    public boolean isFaceUp()
    {
        return (position & 0x5) != 0; // POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE
    }

    public boolean isAttackPosition()
    {
        return (position & 0x3) != 0; // POS_FACEUP_ATTACK | POS_FACEDOWN_ATTACK
    }

    public boolean isMonster()
    {
        return (type & 0x1) != 0;
    }
}
