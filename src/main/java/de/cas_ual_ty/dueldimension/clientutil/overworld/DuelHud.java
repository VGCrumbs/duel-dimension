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
    /** Clear of the window's own edge, as the screen's bars are. */
    private static final int EDGE = 6;
    /** Between a bar and the turn counter. */
    private static final int GAP = 4;
    private static final int PHASE_GAP = 3;

    /**
     * The case art's own proportions, from the duel screen: a bay is 50 by 10
     * and the housing stands 8 by 3 proud of it. Every measurement below is
     * derived from the cell width through these, so the case cannot be
     * stretched into a shape the art was never drawn at -- which is what
     * happens the moment a height is picked independently of a width.
     */
    private static final int CELL_W_BASE = 50;
    private static final int CELL_H_BASE = 10;
    private static final int PAD_X_BASE = 8;
    private static final int PAD_Y_BASE = 3;

    /** The first line of text that is clear of the instruments above it. */
    public static int below(int screenW)
    {
        return barTop() + cellHeight(screenW) + padY(screenW) + 12;
    }

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
        // Laid out as the duel screen lays it out: two bars running from the
        // edges to a small turn counter in the middle, and the phase case
        // centred under them. The screen has a sidebar to leave room for and
        // this does not, so the bars take the width the sidebar was using
        // rather than a third of the screen apiece.
        int turnW = Math.max(22, screenW / 22);
        int barW = (screenW - turnW - EDGE * 2 - GAP * 2) / 2;

        // Through the animation, not straight from the board. A life total
        // that jumps says a number changed; one that runs down says how much
        // was taken and by what, which is the whole reason the duel screen
        // animates it. The state is already being ticked -- tickPlayback does
        // it on the client tick -- so this is a read, not a second animator.
        long now = System.currentTimeMillis();
        drawLifeBar(extractor, font, EDGE, TOP, barW, seat < 0 ? "Seat 1" : "You",
            DuelClientState.animations.lifePointState(0, board.self().lifePoints(), now),
            0xFF3FA34D);
        drawLifeBar(extractor, font, screenW - EDGE - barW, TOP, barW,
            seat < 0 ? "Seat 2" : "Opponent",
            DuelClientState.animations.lifePointState(1,
                board.opponent() == null ? 0 : board.opponent().lifePoints(), now),
            0xFFB03636);

        // The turn number between them, in its own frame: the same three
        // elements in the same order as the screen's top bar.
        int turnX = EDGE + barW + GAP;
        DdBlitUtil.fullBlit(extractor, DuelTextures.LP_FRAME, turnX, TOP, turnW, BAR_H);
        String turn = Integer.toString(board.turn());
        extractor.centeredText(font, turn, turnX + turnW / 2, TOP + 3, 0xFFFFFFFF);

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
        int cellW = cellWidth(screenW);
        int cellH = cellHeight(screenW);
        int width = PHASE_NAMES.length * cellW;
        int x = barLeft(screenW);
        int y = barTop();

        DdBlitUtil.fullBlit(extractor, DuelTextures.PHASE_CASE, x - padX(screenW),
            y - padY(screenW), width + padX(screenW) * 2, cellH + padY(screenW) * 2);

        for(int phase = 0; phase < PHASE_NAMES.length; phase++)
        {
            drawPhaseCell(extractor, x + phase * cellW, y, cellW, cellH, phase,
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

    private static int cellWidth(int screenW)
    {
        return Math.max(20, Math.min(CELL_W_BASE, Math.round(screenW * 0.075F)));
    }

    private static int cellHeight(int screenW)
    {
        return Math.max(5, Math.round(cellWidth(screenW) * (float)CELL_H_BASE / CELL_W_BASE));
    }

    private static int padX(int screenW)
    {
        return Math.round(cellWidth(screenW) * (float)PAD_X_BASE / CELL_W_BASE);
    }

    private static int padY(int screenW)
    {
        return Math.max(2, Math.round(cellHeight(screenW) * (float)PAD_Y_BASE / CELL_H_BASE));
    }

    private static int barLeft(int screenW)
    {
        return (screenW - PHASE_NAMES.length * cellWidth(screenW)) / 2;
    }

    private static int barTop()
    {
        return TOP + BAR_H + PHASE_GAP;
    }

    /**
     * Which phase bay a point is over, or -1.
     * <p>
     * Ending a turn IS the phase bar, so it has to be clickable rather than
     * merely readable. The hit test measures the same cells the draw does, from
     * the same helpers, so the bay that lights up is the bay that answers.
     */
    public static int phaseAt(int screenW, double mouseX, double mouseY)
    {
        int cellW = cellWidth(screenW);
        int y = barTop();
        if(mouseY < y || mouseY >= y + cellHeight(screenW))
        {
            return -1;
        }
        int phase = (int)Math.floor((mouseX - barLeft(screenW)) / (double)cellW);
        return phase >= 0 && phase < PHASE_NAMES.length ? phase : -1;
    }

    /**
     * The engine option that jumps to this bay's phase, or -1 when the engine
     * is not offering it. Matched on the option's COMMAND, as the duel screen
     * does: a phase is offered as "go to battle" or "end turn", never as a
     * phase number, and only three of the six can be jumped to at all.
     */
    public static int optionForPhase(int phase)
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        if(prompt == null || phase < 0 || phase >= PHASE_VALUES.length)
        {
            return -1;
        }
        int wanted = commandFor(PHASE_VALUES[phase]);
        if(wanted == 0)
        {
            return -1;
        }
        for(int i = 0; i < prompt.options().size(); i++)
        {
            if(prompt.options().get(i).command() == wanted)
            {
                return i;
            }
        }
        return -1;
    }

    private static int commandFor(int phase)
    {
        return switch(phase)
        {
            case OcgConstants.PHASE_BATTLE ->
                de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_TO_BATTLE;
            case OcgConstants.PHASE_MAIN2 ->
                de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_TO_MAIN2;
            case OcgConstants.PHASE_END ->
                de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_END_TURN;
            default -> 0;
        };
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
        return offered(index) ? PHASE_IDLE : PHASE_DISABLED;
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
    private static boolean offered(int index)
    {
        return optionForPhase(index) >= 0;
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
        extractor.centeredText(font, clock, screenW / 2, below(screenW) - 11,
            left <= CLOCK_WARN_MS ? 0xFFFF6B6B : 0xFFC2C9D6);
    }
}
