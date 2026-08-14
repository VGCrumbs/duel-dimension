package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.HumanResponseSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.concurrent.TimeUnit;

/**
 * The duel's own instruments, over a board in the world.
 * <p>
 * Life points, the phase bar and the answer clock, in the art the duel screen
 * uses -- the same three PNGs, sampled the same way -- because a duel read from
 * a board still has to be read, and a duellist who cannot see their life total
 * or which phase they are in is looking at scenery rather than playing.
 * <p>
 * Deliberately NOT lifted wholesale out of {@code EngineDuelScreen}. What that
 * class draws is bound to a screen with a sidebar, a log and a card panel, and
 * every measurement is relative to those. What is copied is the part that is
 * about the game rather than about the layout: which atlas cell a phase takes,
 * how a life bar fills, and what the clock counts.
 */
public final class DuelHud
{
    private DuelHud()
    {
    }

    /** EDOPro's own six, in EDOPro's order. */
    private static final String[] PHASE_NAMES = {"DP", "SP", "M1", "BP", "M2", "EP"};
    private static final int[] PHASE_VALUES = {OcgConstants.PHASE_DRAW, OcgConstants.PHASE_STANDBY,
        OcgConstants.PHASE_MAIN1, OcgConstants.PHASE_BATTLE, OcgConstants.PHASE_MAIN2,
        OcgConstants.PHASE_END};

    /** Rows of the phase atlas: lit, idle, greyed. */
    private static final int PHASE_LIT = 0;
    private static final int PHASE_IDLE = 1;
    private static final int PHASE_DISABLED = 2;

    /** The atlas is six cells across and three rows down. */
    private static final float ATLAS_W = 1200F;
    private static final float ATLAS_H = 120F;

    private static final int BAR_H = 14;
    private static final int TOP = 4;
    private static final int PHASE_H = 13;
    private static final int PHASE_GAP = 3;

    /** The first line of text that is clear of the instruments above it. */
    public static final int BELOW = TOP + BAR_H + PHASE_GAP + PHASE_H + 15;

    /** Under this much left, the clock is a warning rather than a fact. */
    private static final long CLOCK_WARN_MS = 60_000L;

    /**
     * Draws the lot along the top of the screen.
     *
     * @param seat which seat this client holds, or negative for a spectator
     */
    public static void draw(GuiGraphicsExtractor extractor, Font font, BoardSnapshot board,
        int seat)
    {
        if(board == null || board.self() == null)
        {
            return;
        }
        int screenW = extractor.guiWidth();
        // A third of the width each, so two bars and the phase case between
        // them fit at any size rather than at one authored one.
        int barW = Math.max(90, Math.min(230, screenW / 3 - 12));

        // Through the animation, not straight from the board. A life total
        // that jumps says a number changed; one that runs down says how much
        // was taken and by what, which is the whole reason the duel screen
        // animates it. The state is already being ticked -- tickPlayback does
        // it on the client tick -- so this is a read, not a second animator.
        long now = System.currentTimeMillis();
        drawLifeBar(extractor, font, 6, TOP, barW, seat < 0 ? "Seat 1" : "You",
            DuelClientState.animations.lifePointState(0, board.self().lifePoints(), now),
            0xFF3FA34D);
        drawLifeBar(extractor, font, screenW - barW - 6, TOP, barW,
            seat < 0 ? "Seat 2" : "Opponent",
            DuelClientState.animations.lifePointState(1,
                board.opponent() == null ? 0 : board.opponent().lifePoints(), now),
            0xFFB03636);

        drawPhaseBar(extractor, board, screenW);
        drawClock(extractor, font, screenW);
    }

    /**
     * One life bar: a coloured fill under the shipped frame.
     * <p>
     * The fill is procedural because EDOPro's is -- the colour is chosen at
     * runtime and no PNG can carry it -- and the frame over it is the same
     * single shipped texture the duel screen uses.
     */
    private static void drawLifeBar(GuiGraphicsExtractor extractor, Font font, int x, int y,
        int barW, String name,
        de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.LifePointState change, int colour)
    {
        int lifePoints = change.displayedLifePoints();
        int filled = fill(barW, lifePoints);
        int target = fill(barW, change.targetLifePoints());
        extractor.fillGradient(x + 2, y + 2, x + 2 + filled, y + BAR_H - 2,
            shade(colour, 1.25F), shade(colour, 0.75F));
        // The stretch between where the bar was and where it is going, flashed
        // white: the part being lost is shown being lost.
        if(change.whiteAlpha() > 0F && filled != target)
        {
            int alpha = Math.round(change.whiteAlpha() * 255F) << 24;
            extractor.fill(x + 2 + Math.min(filled, target), y + 2,
                x + 2 + Math.max(filled, target), y + BAR_H - 2, alpha | 0xFFFFFF);
        }
        DdBlitUtil.fullBlit(extractor, DuelTextures.LP_FRAME, x, y, barW, BAR_H);

        extractor.text(font, name, x + 5, y + 3, 0xFFFFFFFF, false);
        String value = Integer.toString(lifePoints);
        extractor.text(font, value, x + barW - font.width(value) - 5, y + 3, 0xFFFFFFFF, false);
    }

    private static int fill(int barW, int lifePoints)
    {
        return Math.max(0, Math.min(barW - 4, Math.round((barW - 4) * lifePoints / 8000F)));
    }

    /** Lit from above, so the bar reads as a rounded surface and not a block. */
    private static int shade(int colour, float factor)
    {
        int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((colour >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((colour & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * The chrome case with its six bays, blue while the turn is yours and red
     * while it is not.
     * <p>
     * {@code turnPlayer} is already in the viewer's numbering -- the server
     * writes 0 for the seat being served -- so 0 IS "yours" and no comparison
     * against a seat index belongs here.
     */
    private static void drawPhaseBar(GuiGraphicsExtractor extractor, BoardSnapshot board,
        int screenW)
    {
        boolean yourTurn = board.turnPlayer() == 0;
        int cellW = Math.max(16, Math.min(38, screenW / 22));
        int width = PHASE_NAMES.length * cellW;
        int x = (screenW - width) / 2;
        int y = TOP + BAR_H + PHASE_GAP;

        DdBlitUtil.fullBlit(extractor, DuelTextures.PHASE_CASE, x - 4, y - 3, width + 8,
            PHASE_H + 6);

        for(int phase = 0; phase < PHASE_NAMES.length; phase++)
        {
            drawPhaseCell(extractor, x + phase * cellW, y, cellW, PHASE_H, phase,
                stateOf(board, phase), yourTurn);
        }
    }

    /**
     * One bay of the phase case.
     * <p>
     * The sample points are kept half a source texel inside the cell: sampling
     * exactly on a shared edge borrows the bright outline from the next phase
     * while the cell is scaled, which leaves a white pixel in the bay. The 2D
     * board learned that; there is no reason to learn it twice.
     */
    private static void drawPhaseCell(GuiGraphicsExtractor extractor, int x, int y, int w, int h,
        int index, int state, boolean yourTurn)
    {
        float uInset = 0.5F / ATLAS_W;
        float vInset = 0.5F / ATLAS_H;
        DdBlitUtil.blit(extractor, yourTurn ? DuelTextures.PHASE_BLUE : DuelTextures.PHASE_RED,
            x, y, w, h,
            index / (float)PHASE_NAMES.length + uInset, state / 3F + vInset,
            (index + 1) / (float)PHASE_NAMES.length - uInset, (state + 1) / 3F - vInset,
            DdBlitUtil.NO_TINT);
    }

    /**
     * Lit for the phase the duel is in, idle for one the engine is offering to
     * jump to, greyed otherwise. Being current wins over being offered, because
     * the core never offers a jump to the phase you are already in and reading
     * "no option" as unreachable would grey out the live phase.
     */
    private static int stateOf(BoardSnapshot board, int index)
    {
        if(isCurrent(board, index))
        {
            return PHASE_LIT;
        }
        return offered(PHASE_VALUES[index]) ? PHASE_IDLE : PHASE_DISABLED;
    }

    private static boolean isCurrent(BoardSnapshot board, int index)
    {
        return board.phase() == PHASE_VALUES[index]
            || (PHASE_VALUES[index] == OcgConstants.PHASE_BATTLE
                && board.phase() > OcgConstants.PHASE_MAIN1
                && board.phase() < OcgConstants.PHASE_MAIN2);
    }

    /**
     * Is the engine currently offering a jump to this phase?
     * <p>
     * Matched on the option's COMMAND, exactly as the duel screen does: a phase
     * is offered as "go to battle" or "end turn", not as a phase number, and
     * only three of the six can ever be jumped to.
     */
    private static boolean offered(int phase)
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        int wanted = switch(phase)
        {
            case OcgConstants.PHASE_BATTLE ->
                de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_TO_BATTLE;
            case OcgConstants.PHASE_MAIN2 ->
                de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_TO_MAIN2;
            case OcgConstants.PHASE_END ->
                de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_END_TURN;
            default -> 0;
        };
        if(prompt == null || wanted == 0)
        {
            return false;
        }
        for(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.Option option : prompt.options())
        {
            if(option.command() == wanted)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The countdown to the answer deadline.
     * <p>
     * It counts the clock the duel actually keeps: a player has
     * {@code TIMEOUT_MINUTES} to answer the question in front of them, so it
     * restarts with each question. That is what a player watching it sees, and
     * it is not a budget for the whole turn.
     */
    private static void drawClock(GuiGraphicsExtractor extractor, Font font, int screenW)
    {
        if(DuelClientState.prompt == null || DuelClientState.promptShownAt == 0
            || DuelClientState.over)
        {
            return;
        }
        long limit = TimeUnit.MINUTES.toMillis(HumanResponseSource.TIMEOUT_MINUTES);
        long left = limit - (System.currentTimeMillis() - DuelClientState.promptShownAt);
        long seconds = Math.max(0, (left + 999) / 1000);
        String clock = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        extractor.centeredText(font, clock, screenW / 2, TOP + BAR_H + PHASE_GAP + PHASE_H + 3,
            left <= CLOCK_WARN_MS ? 0xFFFF6B6B : 0xFFC2C9D6);
    }
}
