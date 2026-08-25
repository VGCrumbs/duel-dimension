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

    /** The blue an offered card wears when the offer is a move, not an effect. */
    private static final int ACTIVE_BLUE = 0x63C8FF;

    /**
     * And the gold it wears when its EFFECT can be activated.
     * <p>
     * The two are worth telling apart because they are different kinds of
     * moment. Setting a card or turning one to defence is housekeeping; a card
     * whose effect can go on the chain is a play, and it is the one a duellist
     * scans the board for. One blue glow for both meant reading every lit card
     * to find out which was which.
     * <p>
     * The hub's own accent, so it reads as the same "this matters" the rest of
     * the mod uses gold for.
     */
    private static final int ACTIVATABLE_GOLD = 0xF4D089;

    /** Ticks for one full breath of the pulse. */
    private static final float PULSE_TICKS = 26F;

    /**
     * How far the glow spills past the card it is around, in pixels.
     * <p>
     * One, not three. The outline texture is already opaque at its very edge
     * and falls off INWARD, so the ring lands on the card's border on its own —
     * the spill was pushing that ring out into the gap between cards, where a
     * hand of five read as one lit blur rather than five lit cards.
     */
    private static final int SPILL = 1;

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
        return tinted(ACTIVE_BLUE, alpha);
    }

    /**
     * Whether any option offered for this card is its effect going on the
     * chain, rather than a summon, a set or a repositioning.
     * <p>
     * Asked of the PROMPT and not of the card: whether an effect can be
     * activated depends on the board, the chain and the phase, all of which the
     * engine has already weighed in deciding what to offer. Reading the card's
     * own text for "you can" would be inventing a second opinion, and a glow
     * that disagrees with the menu under it is worse than no glow.
     */
    public static boolean canActivate(BoardTarget target)
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        if(prompt == null || target == null)
        {
            return false;
        }
        for(int index : PromptOptions.optionsFor(prompt, false, target))
        {
            if(prompt.options().get(index).command()
                == de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_ACTIVATE)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The colour this card's glow should be, pulsed.
     * <p>
     * One place decides it, so the hand, the field and the piles cannot drift
     * into disagreeing about what gold means.
     */
    public static int tintFor(BoardTarget target, float ticks)
    {
        return tinted(canActivate(target) ? ACTIVATABLE_GOLD : ACTIVE_BLUE, pulse(ticks));
    }

    /** The green a card wears once it has been picked for a selection. */
    public static final int CHOSEN_GREEN = 0x7CE38B;

    public static int tinted(int rgb, float alpha)
    {
        return Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24 | rgb;
    }

    /** Draws the glow around a rectangle on the screen. */
    public static void around(GuiGraphicsExtractor extractor, int x, int y, int width, int height,
        float ticks)
    {
        around(extractor, x, y, width, height, ticks, null);
    }

    /**
     * The same, in the colour this card has earned.
     *
     * @param target what the glow is around, or null to keep the plain blue
     */
    public static void around(GuiGraphicsExtractor extractor, int x, int y, int width, int height,
        float ticks, BoardTarget target)
    {
        DdBlitUtil.blit(extractor, OUTLINE, x - SPILL, y - SPILL, width + SPILL * 2,
            height + SPILL * 2, 0F, 0F, 1F, 1F,
            target == null ? tint(pulse(ticks)) : tintFor(target, ticks));
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
        return PromptOptions.actionable(DuelClientState.prompt, false, handTarget(board, index));
    }

    /**
     * The target a card in hand is, or null when there is no such card.
     * <p>
     * Controller 0: the engine numbers from the seat it is asking, and this is
     * that seat's own hand. Built once here rather than at each caller, so the
     * thing the glow asks about and the thing the click acts on are the same
     * object rather than two constructions of it.
     */
    public static BoardTarget handTarget(BoardSnapshot board, int index)
    {
        List<BoardSnapshot.Slot> hand = board == null || board.self() == null ? null
            : board.self().hand();
        if(hand == null || index < 0 || index >= hand.size())
        {
            return null;
        }
        BoardSnapshot.Slot card = hand.get(index);
        return new BoardTarget(card.code(), 0, OcgConstants.LOCATION_HAND, index, -1, "Hand", 1,
            card.art());
    }
}
