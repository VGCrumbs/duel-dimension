package de.cas_ual_ty.ydm.ocg.query;

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
 * @param isPublic whether the core considers this card public knowledge
 * @param hidden   whether this view had its identity stripped for the viewer
 */
public record CardView(int code, int position, int type, int level, int attack, int defense,
    boolean isPublic, boolean hidden)
{
    public static final CardView HIDDEN = new CardView(0, 0, 0, 0, -1, -1, false, true);

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
