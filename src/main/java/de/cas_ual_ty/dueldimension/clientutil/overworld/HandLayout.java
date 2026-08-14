package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;

/**
 * Where the cards in your hand are drawn along the bottom of the screen.
 * <p>
 * One definition with two consumers: {@link HandHud} draws them and
 * {@link BoardPointerScreen} lets a freed cursor click them. That is the whole
 * reason it exists as its own class -- a hand whose cards are drawn in one place
 * and hit-tested from another is a hand you cannot reliably click, and the fault
 * looks like a broken cursor rather than like two functions disagreeing.
 * <p>
 * Pure arithmetic on a screen size and a card count, so both callers get the
 * same rectangles and a test can check them.
 */
public final class HandLayout
{
    private HandLayout()
    {
    }

    /** How tall a card is drawn, in pixels; its width follows the card's shape. */
    public static final int CARD_H = 81;
    public static final int CARD_W = Math.round(CARD_H * DuelTextures.CARD_ASPECT);
    /** How much of a card stays visible when the hand is too wide to lay flat. */
    private static final int MIN_STEP = 12;
    private static final int GAP = 3;
    /** Clear of the hotbar, the experience bar and the preload bar above them. */
    private static final int ABOVE_HOTBAR = 76;

    /** How far the card under the cursor rises out of the fan. */
    public static final int HOVER_LIFT = 10;

    /** One card's place on screen. */
    public record Slot(int x, int y, int width, int height)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /**
     * Every card's rectangle, left to right.
     * <p>
     * Cards overlap when there are too many to lay side by side, the way a hand
     * of cards actually overlaps -- rather than shrinking them until they are
     * unreadable, which is the other way a hand of fifteen could be made to fit.
     */
    public static Slot[] slots(int screenW, int screenH, int cards)
    {
        if(cards <= 0)
        {
            return new Slot[0];
        }
        int usable = Math.max(CARD_W, screenW - 40);
        int step = CARD_W + GAP;
        if(cards * step > usable)
        {
            step = Math.max(MIN_STEP, (usable - CARD_W) / Math.max(1, cards - 1));
        }
        int spread = CARD_W + step * (cards - 1);
        int x = (screenW - spread) / 2;
        int y = screenH - ABOVE_HOTBAR - CARD_H;

        Slot[] slots = new Slot[cards];
        for(int card = 0; card < cards; card++)
        {
            slots[card] = new Slot(x + step * card, y, CARD_W, CARD_H);
        }
        return slots;
    }

    /**
     * Which card is under the cursor, or -1.
     * <p>
     * Searched from the RIGHT, because overlapping cards are drawn left to
     * right and so the rightmost of any overlapping pair is the one on top --
     * which is the one a player believes they are clicking.
     */
    public static int at(Slot[] slots, double mouseX, double mouseY)
    {
        for(int card = slots.length - 1; card >= 0; card--)
        {
            if(slots[card].contains(mouseX, mouseY))
            {
                return card;
            }
        }
        return -1;
    }
}
