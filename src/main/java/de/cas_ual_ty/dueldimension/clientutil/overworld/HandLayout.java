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

    /**
     * How tall a card is drawn, as a share of the screen.
     * <p>
     * A share rather than a count of pixels, because a gui-scaled viewport is
     * anything from about 200 to 900 units tall depending on the window and the
     * player's gui scale, and a fixed 61 is a comfortable hand on one of those
     * and a postage stamp or a wall of cards on the others. The fraction is set
     * so that the reference 920p at gui scale 3 comes out at the size this was
     * hand-tuned to.
     */
    private static final float HEIGHT_SHARE = 0.20F;
    private static final int MIN_CARD_H = 34;
    private static final int MAX_CARD_H = 140;

    /** How tall a card is drawn on a viewport of this height. */
    public static int cardHeight(int screenH)
    {
        return Math.clamp(Math.round(screenH * HEIGHT_SHARE), MIN_CARD_H, MAX_CARD_H);
    }

    /** A card's width follows its height, because a card has a shape. */
    public static int cardWidth(int screenH)
    {
        return Math.round(cardHeight(screenH) * DuelTextures.CARD_ASPECT);
    }
    /**
     * How far the fan sits off the bottom edge.
     * <p>
     * Almost nothing: the hotbar is hidden for the length of a duel, so the
     * bottom of the screen is the hand's, and a hand floating above an empty
     * strip looks like it is avoiding something that is not there.
     */
    private static final int ABOVE_HOTBAR = 2;

    /** How far the card under the cursor rises out of the fan, at that size. */
    public static int hoverLift(int screenH)
    {
        return Math.max(4, cardHeight(screenH) / 6);
    }

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
        int cardW = cardWidth(screenH);
        int cardH = cardHeight(screenH);
        int usable = Math.max(cardW, screenW - 40);
        // The gap and the minimum sliver scale too, or a hand of fifteen at a
        // small gui scale overlaps to nothing while the cards themselves shrank.
        int gap = Math.max(1, cardW / 15);
        int step = cardW + gap;
        if(cards * step > usable)
        {
            step = Math.max(Math.max(6, cardW / 4), (usable - cardW) / Math.max(1, cards - 1));
        }
        int spread = cardW + step * (cards - 1);
        int x = (screenW - spread) / 2;
        int y = screenH - ABOVE_HOTBAR - cardH;

        Slot[] slots = new Slot[cards];
        for(int card = 0; card < cards; card++)
        {
            slots[card] = new Slot(x + step * card, y, cardW, cardH);
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
