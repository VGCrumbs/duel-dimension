package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PromptOptions;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The blue glow around a card the engine is currently offering.
 * <p>
 * Answering the question a duellist actually has -- "what can I do right now?"
 * -- without them having to sweep the cursor over everything to find out. It
 * marks whatever {@link PromptOptions} finds an option for, which means it can
 * never disagree with what clicking would do: the same filter draws the glow and
 * decides the click.
 * <p>
 * A texture, not a rectangle drawn in code: an outline PNG with a soft inner
 * falloff, tinted and pulsed, so it reads as a card glowing rather than as a box
 * around one.
 */
public final class DuelHighlight
{
    private DuelHighlight()
    {
    }

    public static final Identifier OUTLINE = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/duel/overworld/card_outline.png");

    /** The blue an activatable card wears. */
    private static final int ACTIVE_BLUE = 0x63C8FF;

    /** Ticks for one full breath of the pulse. */
    private static final float PULSE_TICKS = 26F;

    /** How far the glow spills past the card it is around, in pixels. */
    private static final int SPILL = 3;

    /**
     * The pulse, as an alpha between a floor and full.
     * <p>
     * Driven by the game's own tick count so it holds still with the game -- a
     * paused singleplayer world should not have a card breathing on it.
     */
    public static float pulse(float ticks)
    {
        return 0.55F + 0.45F * (0.5F + 0.5F * Mth.sin(ticks / PULSE_TICKS * Mth.TWO_PI));
    }

    public static int tint(float alpha)
    {
        return Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24 | ACTIVE_BLUE;
    }

    /** Draws the glow around a rectangle on the screen. */
    public static void around(GuiGraphicsExtractor extractor, int x, int y, int width, int height,
        float ticks)
    {
        DdBlitUtil.blit(extractor, OUTLINE, x - SPILL, y - SPILL, width + SPILL * 2,
            height + SPILL * 2, 0F, 0F, 1F, 1F, tint(pulse(ticks)));
    }

    /**
     * Is the engine offering anything for this card in hand?
     * <p>
     * Asked through the same filter the click uses, so a card that glows is a
     * card that will do something when clicked, and one that does not is one
     * that will not.
     */
    public static boolean handCardIsOffered(BoardSnapshot board, int index)
    {
        List<BoardSnapshot.Slot> hand = board == null || board.self() == null ? null
            : board.self().hand();
        if(hand == null || index < 0 || index >= hand.size())
        {
            return false;
        }
        BoardSnapshot.Slot card = hand.get(index);
        // Controller 0: the engine numbers from the seat it is asking, and this
        // is that seat's own hand.
        return PromptOptions.actionable(DuelClientState.prompt, false,
            new BoardTarget(card.code(), 0, OcgConstants.LOCATION_HAND, index, -1, "Hand", 1,
                card.art()));
    }
}
