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

    /**
     * The life frame's own proportions, so it is never drawn at a shape it was
     * not drawn at: 256 wide by 32 tall in the file.
     */
    private static final int BAR_W_BASE = 256;
    private static final int BAR_H_BASE = 32;
    /** How much of the window one life bar takes, at any window size. */
    private static final float BAR_W_SHARE = 0.30F;
    private static final int TOP = 4;
    /** The frame's raised border, which no text belongs on. */
    private static final int INSET = 6;
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
        return barTop(barHeight(screenW)) + cellHeight(screenW) + padY(screenW) + 12;
    }

    /** The life frame's drawn height at this width, for anything measuring off it. */
    public static int barHeight(int screenW)
    {
        return Math.max(9, Math.round(barWidth(screenW) * (float)BAR_H_BASE / BAR_W_BASE));
    }

    /**
     * A share of the width, not a cap on it.
     * <p>
     * It was a cap, which had it backwards: capping the width makes the bars a
     * small part of a large screen and the WHOLE of a small one, so the smaller
     * the window the more of it the instruments ate. A fraction gives them the
     * same share of any window, which is what "the same size" means when the
     * window can be any size at all.
     */
    private static int barWidth(int screenW)
    {
        int room = (screenW - EDGE * 2 - GAP * 2) / 2 - 12;
        return Math.max(70, Math.min(room, Math.round(screenW * BAR_W_SHARE)));
    }

    /** Half again, so the countdown reads without looking for it. */
    private static final float CLOCK_SCALE = 1.5F;

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
        // Sized from the art's own shape rather than stretched to whatever is
        // left over. The frame is a picture of a bar, and a picture pulled to
        // three times its height stops looking like one -- so the height comes
        // from the width through the art's ratio, and the width is capped.
        int barW = barWidth(screenW);
        int barH = barHeight(screenW);
        int turnW = Math.max(16, Math.round(barH * 1.3F));

        // Through the animation, not straight from the board. A life total
        // that jumps says a number changed; one that runs down says how much
        // was taken and by what, which is the whole reason the duel screen
        // animates it. The state is already being ticked -- tickPlayback does
        // it on the client tick -- so this is a read, not a second animator.
        long now = System.currentTimeMillis();
        drawLifeBar(extractor, font, EDGE, TOP, barW, barH,
            seat < 0 ? "Seat 1" : DuelClientState.selfName,
            DuelClientState.animations.lifePointState(0, board.self().lifePoints(), now),
            0xFF3FA34D);
        drawLifeBar(extractor, font, screenW - EDGE - barW, TOP, barW, barH,
            seat < 0 ? "Seat 2" : DuelClientState.opponentName,
            DuelClientState.animations.lifePointState(1,
                board.opponent() == null ? 0 : board.opponent().lifePoints(), now),
            0xFFB03636);

        // The turn number between them, in its own frame: the same three
        // elements in the same order as the screen's top bar.
        int turnX = (screenW - turnW) / 2;
        DdBlitUtil.fullBlit(extractor, DuelTextures.LP_FRAME, turnX, TOP, turnW, barH);
        String turn = Integer.toString(board.turn());
        extractor.centeredText(font, turn, turnX + turnW / 2, TOP + (barH - 8) / 2, 0xFFFFFFFF);

        drawPhaseBar(extractor, board, screenW, barH);
        drawClock(extractor, font, screenW, barH);
    }

    /**
     * One life bar: a coloured fill under the shipped frame.
     * <p>
     * The fill is procedural because EDOPro's is -- the colour is chosen at
     * runtime and no PNG can carry it -- and the frame over it is the same
     * single shipped texture the duel screen uses.
     */
    private static void drawLifeBar(GuiGraphicsExtractor extractor, Font font, int x, int y,
        int barW, int barH, String name,
        de.cas_ual_ty.dueldimension.clientutil.DuelAnimations.LifePointState change, int colour)
    {
        int lifePoints = change.displayedLifePoints();
        int filled = fill(barW, lifePoints);
        int target = fill(barW, change.targetLifePoints());
        extractor.fillGradient(x + 2, y + 2, x + 2 + filled, y + barH - 2,
            shade(colour, 1.25F), shade(colour, 0.75F));
        // The stretch between where the bar was and where it is going, flashed
        // white: the part being lost is shown being lost.
        if(change.whiteAlpha() > 0F && filled != target)
        {
            int alpha = Math.round(change.whiteAlpha() * 255F) << 24;
            extractor.fill(x + 2 + Math.min(filled, target), y + 2,
                x + 2 + Math.max(filled, target), y + barH - 2, alpha | 0xFFFFFF);
        }
        DdBlitUtil.fullBlit(extractor, DuelTextures.LP_FRAME, x, y, barW, barH);

        // Inside the coloured well, not on the frame around it. The frame is
        // a picture with a raised border, and text laid at its edge sits half
        // on the metal -- so both ends start where the fill starts, and a long
        // name gives way to the number rather than growing under it.
        String value = Integer.toString(lifePoints);
        int valueW = font.width(value);
        int room = barW - INSET * 2 - valueW - 4;
        String shown = name;
        while(font.width(shown) > room && shown.length() > 1)
        {
            shown = shown.substring(0, shown.length() - 1);
        }
        int textY = y + (barH - font.lineHeight) / 2 + 1;
        extractor.text(font, shown, x + INSET, textY, 0xFFFFFFFF, false);
        extractor.text(font, value, x + barW - INSET - valueW, textY, 0xFFFFFFFF, false);
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
        int screenW, int barH)
    {
        boolean yourTurn = board.turnPlayer() == 0;
        int cellW = cellWidth(screenW);
        int cellH = cellHeight(screenW);
        int width = PHASE_NAMES.length * cellW;
        int x = barLeft(screenW);
        int y = barTop(barH);

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
        return Math.max(15, Math.min(CELL_W_BASE, Math.round(screenW * 0.062F)));
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

    private static int barTop(int barH)
    {
        return TOP + barH + PHASE_GAP;
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
        int y = barTop(barHeight(screenW));
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
    private static void drawClock(GuiGraphicsExtractor extractor, Font font, int screenW,
        int barH)
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
        // Below the case rather than tucked under it, and drawn larger: it is
        // a countdown to losing the turn, which is worth reading at a glance.
        int colour = left <= CLOCK_WARN_MS ? 0xFFFF6B6B : 0xFFC2C9D6;
        extractor.pose().pushMatrix();
        extractor.pose().scale(CLOCK_SCALE, CLOCK_SCALE);
        extractor.centeredText(font, clock,
            Math.round(screenW / 2F / CLOCK_SCALE),
            Math.round((barTop(barH) + cellHeight(screenW) + padY(screenW) + 4) / CLOCK_SCALE),
            colour);
        extractor.pose().popMatrix();
    }
}
