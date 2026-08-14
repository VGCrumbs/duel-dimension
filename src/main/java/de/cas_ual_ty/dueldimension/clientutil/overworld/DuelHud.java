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

    /**
     * Rows of the phase atlas: idle, lit, greyed -- in the atlas's own order.
     * <p>
     * Which was upside down. The first two were named the other way round here
     * than on the duel screen, so every bay wore the wrong face: the phase you
     * are IN was drawn in the flat idle plate and the ones merely available lit
     * up. Same picture, same numbers, opposite meanings -- and the only way to
     * see it was to have both boards open at once.
     */
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_LIT = 1;
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
    /** The turn badge's edge, in the duel screen's own two colours. */
    private static final int TURN_YOURS = 0x4CD964;
    private static final int TURN_THEIRS = 0xFF453A;
    /**
     * The badge's own shape, from the duel screen: 34 across by 17 down, twice
     * as wide as it is tall, with the turn number filling it.
     */
    private static final int TURN_W_BASE = 34;
    private static final int TURN_H_BASE = 17;

    /** How much of the window one life bar takes, at any window size. */
    private static final float BAR_W_SHARE = 0.30F;
    /** And never taller than this much of the window, whatever the width says. */
    private static final float BAR_H_SHARE = 0.055F;
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
    /**
     * How much of the window one phase bay takes. Six of them, so the case is
     * about half the width at any size -- which is the share it has on the duel
     * screen, where it runs most of the way across the table.
     */
    private static final float CELL_W_SHARE = 0.085F;
    private static final int CELL_W_MAX = 64;
    private static final int CELL_H_BASE = 10;
    private static final int PAD_X_BASE = 8;
    private static final int PAD_Y_BASE = 3;

    /** The first line of text that is clear of the instruments above it. */
    public static int below(int screenW, int screenH)
    {
        return barTop(barHeight(screenW, screenH)) + cellHeight(screenW, screenH) + padY(screenW, screenH) + 12;
    }

    /** The life frame's drawn height at this width, for anything measuring off it. */
    public static int barHeight(int screenW, int screenH)
    {
        return Math.max(9, Math.round(barWidth(screenW, screenH) * (float)BAR_H_BASE / BAR_W_BASE));
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
    private static int barWidth(int screenW, int screenH)
    {
        int room = (screenW - EDGE * 2 - GAP * 2) / 2 - 12;
        // Three limits, and the smallest wins: a share of the reference width,
        // a share of the HEIGHT, and whatever room there actually is.
        //
        // The height limit is the one an ultrawide needs. A window that is
        // twice as wide is not a window a player is sitting twice as far from,
        // so the instruments have no business being twice the size -- and a
        // life frame that grows to a seventh of the screen's height stops being
        // a readout and becomes a banner.
        int byWidth = Math.round(reference(screenW, screenH) * BAR_W_SHARE);
        int byHeight = Math.round(screenH * BAR_H_SHARE * BAR_W_BASE / BAR_H_BASE);
        return Math.max(70, Math.min(room, Math.min(byWidth, byHeight)));
    }

    /**
     * The width this window would have if it were the usual shape.
     * <p>
     * An ultrawide screen is wider without being any bigger to look at: the
     * player has not moved back from it, and the extra width is peripheral. So
     * every share below is taken of the width a 16:9 window of this HEIGHT
     * would have, and a wider window simply has more room around the same
     * instruments rather than larger ones.
     */
    private static int reference(int screenW, int screenH)
    {
        return Math.min(screenW, Math.round(screenH * 16F / 9F));
    }

    /**
     * How much larger than the font the countdown is drawn.
     * <p>
     * Proportional rather than a flat half-again. A small viewport has already
     * made the font large relative to everything else -- that is what a gui
     * scale does -- so multiplying it by a constant on top compounds the two
     * and the clock ends up shouting on exactly the windows with least room
     * for it.
     */
    private static float clockScale(int screenW, int screenH)
    {
        return Math.clamp(reference(screenW, screenH) / 420F, 1F, 1.6F);
    }

    /** Under this much left, the clock is a warning rather than a fact. */
    private static final long CLOCK_WARN_MS = 60_000L;

    /** The duel screen's own footer button, at the size it is drawn there. */
    private static final int CANCEL_W = 74;
    private static final int CANCEL_H = 16;

    /**
     * The viewport this mod's layouts are measured against: 1634 by 920 at gui
     * scale 3 is 545 across. Chrome drawn at a scale of one here is chrome
     * drawn at the size it was designed at.
     */
    private static final float REFERENCE_BASE = 545F;

    /** How much larger or smaller than designed a piece of chrome is drawn. */
    private static float chromeScale(int screenW, int screenH)
    {
        return Math.clamp(reference(screenW, screenH) / REFERENCE_BASE, 0.8F, 1.6F);
    }

    /**
     * Where the way out sits, as {x, y, width, height}, or null when there is
     * nothing to back out of.
     * <p>
     * Bottom left, which is the one corner of a duel that holds nothing: the
     * hand is centred along the bottom, the instruments are along the top, and
     * a card's own menu opens over the card. Drawn and hit-tested from the same
     * rectangle, so the button cannot be somewhere other than where it is
     * clicked.
     */
    public static int[] cancelBounds(int screenW, int screenH, boolean menuOpen)
    {
        if(DuelClientState.over)
        {
            return null;
        }
        // Two different things to back out of, and a player means both by
        // "cancel". A menu open over a card is one step in -- the card has been
        // picked and nothing has been done with it yet -- and that step can
        // always be taken back, whatever the engine thinks. Answering the whole
        // prompt with nothing is the other, and only some prompts take it: a
        // place selection is not one, which is not a gap here but the rule.
        // EDOPro reads the cancel flag on MSG_SELECT_PLACE and ignores it, and
        // the core refuses an empty answer, so a button offered there would
        // park the duel on a question it had just refused to drop.
        if(!menuOpen && !de.cas_ual_ty.dueldimension.clientutil.PromptOptions.canDecline(
            DuelClientState.prompt))
        {
            return null;
        }
        float scale = chromeScale(screenW, screenH);
        int cancelW = Math.round(CANCEL_W * scale);
        int cancelH = Math.round(CANCEL_H * scale);
        return new int[] {EDGE, screenH - EDGE - cancelH, cancelW, cancelH};
    }

    /**
     * Where "that is all of them" sits, or null when nothing is being counted.
     * <p>
     * Beside the way out rather than across the screen from it, because the two
     * are the same decision asked twice -- yes I am done, no I am not -- and a
     * player looking for one has found the other.
     */
    public static int[] confirmBounds(int screenW, int screenH)
    {
        if(DuelClientState.over
            || !de.cas_ual_ty.dueldimension.clientutil.DuelSelection.wantsSeveral(
                DuelClientState.prompt))
        {
            return null;
        }
        float scale = chromeScale(screenW, screenH);
        int confirmW = Math.round(CANCEL_W * scale);
        int confirmH = Math.round(CANCEL_H * scale);
        return new int[] {EDGE + confirmW + 4, screenH - EDGE - confirmH, confirmW, confirmH};
    }

    /**
     * The button that ends a selection, and the count that says whether it can.
     * <p>
     * Greyed until the engine's own minimum is met, and carrying the running
     * total on its face: "which three monsters" is a question a player answers
     * over several clicks, and without a number on screen the only way to know
     * how far along they are is to count the cards they have lit up.
     */
    public static void drawConfirm(GuiGraphicsExtractor extractor, Font font, int mouseX,
        int mouseY)
    {
        int[] at = confirmBounds(extractor.guiWidth(), extractor.guiHeight());
        if(at == null)
        {
            return;
        }
        boolean ready = de.cas_ual_ty.dueldimension.clientutil.DuelSelection.ready(
            DuelClientState.prompt);
        boolean over = mouseX >= at[0] && mouseX < at[0] + at[2]
            && mouseY >= at[1] && mouseY < at[1] + at[3];
        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(extractor,
            de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures.BUTTON,
            at[0], at[1], at[2], at[3], !ready ? 2 : over ? 1 : 0, 3);
        String label = "Confirm " + de.cas_ual_ty.dueldimension.clientutil.DuelSelection.count()
            + "/" + DuelClientState.prompt.maxSelect();
        extractor.text(font, label, at[0] + (at[2] - font.width(label)) / 2,
            at[1] + (at[3] - font.lineHeight) / 2 + 1, ready ? 0xFFE6EAF2 : 0xFF6A7080, true);
    }

    /**
     * The way out of an action, in the shipped button art the duel screen uses
     * for the very same word.
     * <p>
     * Right-click has always declined, and a right-click is not something a
     * board teaches you. Backing out of a half-made summon is common enough --
     * wrong card, wrong zone, changed your mind about the chain -- that it
     * belongs on the screen where it can be seen, and it is only drawn while
     * the engine will actually accept it.
     */
    public static void drawCancel(GuiGraphicsExtractor extractor, Font font, int mouseX,
        int mouseY, boolean menuOpen)
    {
        int[] at = cancelBounds(extractor.guiWidth(), extractor.guiHeight(), menuOpen);
        if(at == null)
        {
            return;
        }
        boolean over = mouseX >= at[0] && mouseX < at[0] + at[2]
            && mouseY >= at[1] && mouseY < at[1] + at[3];
        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(extractor,
            de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures.BUTTON,
            at[0], at[1], at[2], at[3], over ? 1 : 0, 3);
        String label = cancelLabel(menuOpen);
        extractor.text(font, label, at[0] + (at[2] - font.width(label)) / 2,
            at[1] + (at[3] - font.lineHeight) / 2 + 1, 0xFFE6EAF2, true);
    }

    /**
     * A sort prompt is not cancelled, it is accepted as it stands -- so the
     * duel screen calls the very same button "Keep order" there, and so does
     * this.
     */
    private static String cancelLabel(boolean menuOpen)
    {
        de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt = DuelClientState.prompt;
        return !menuOpen && prompt != null
            && prompt.kind() == de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt.Kind.SORT
            ? "Keep order" : "Cancel";
    }

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
        int screenH = extractor.guiHeight();
        // Laid out as the duel screen lays it out: two bars running from the
        // edges to a small turn counter in the middle, and the phase case
        // centred under them. The screen has a sidebar to leave room for and
        // this does not, so the bars take the width the sidebar was using
        // rather than a third of the screen apiece.
        // Sized from the art's own shape rather than stretched to whatever is
        // left over. The frame is a picture of a bar, and a picture pulled to
        // three times its height stops looking like one -- so the height comes
        // from the width through the art's ratio, and the width is capped.
        int barW = barWidth(screenW, screenH);
        int barH = barHeight(screenW, screenH);
        // The badge stands exactly as tall as the bars either side of it, so
        // the three read as one band, and exactly twice as wide as it is tall,
        // which is the shape the duel screen draws it at. Both were being taken
        // separately before -- a height from the bars and a width from a bare
        // multiplier -- so every window size gave it a different shape.
        int turnH = barH;
        int turnW = Math.round(barH * (TURN_W_BASE / (float)TURN_H_BASE));
        // And the number grows with the badge instead of sitting in the middle
        // of it at whatever size the font happens to be. A badge three times
        // the size with the same small digit in it is what looked stretched.
        float turnScale = Math.max(0.75F, barH / (float)TURN_H_BASE);

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
        // A badge, not a squashed life frame. The frame is a 256 by 32 picture
        // of a long bar; forcing it into a small square stretched its end caps
        // across the whole thing, which is what made the counter look wonky.
        // The duel screen draws this as a plain badge edged in the colour of
        // whoever holds the turn, and so does this.
        int turnX = (screenW - turnW) / 2;
        int turnColour = board.turnPlayer() == 0 ? TURN_YOURS : TURN_THEIRS;
        int edge = Math.max(1, Math.round(turnScale));
        extractor.fill(turnX, TOP, turnX + turnW, TOP + turnH, 0xC0101014);
        extractor.fill(turnX, TOP, turnX + turnW, TOP + edge, 0xC0000000 | turnColour);
        extractor.fill(turnX, TOP + turnH - edge, turnX + turnW, TOP + turnH,
            0xC0000000 | turnColour);
        // Turn 0 is the engine mid-setup; the screen shows 1 there and so does
        // this. Coloured rather than white, the same as the screen's badge:
        // whose turn it is is said with colour and not with words.
        extractor.pose().pushMatrix();
        extractor.pose().scale(turnScale, turnScale);
        extractor.centeredText(font, Integer.toString(Math.max(1, board.turn())),
            Math.round((turnX + turnW / 2F) / turnScale),
            Math.round((TOP + (turnH - font.lineHeight * turnScale) / 2F + 1) / turnScale),
            0xFF000000 | turnColour);
        extractor.pose().popMatrix();

        drawPhaseBar(extractor, board, screenW, screenH, barH);
        drawClock(extractor, font, screenW, screenH, barH);
        // Held upright over the cards they belong to, rather than lying on
        // them: see StatOverlay. Drawn from here so the cursor and the bare
        // camera both get them, and drawn BEFORE the outcome so a duel that has
        // just been decided is not captioned over its own result.
        StatOverlay.draw(extractor, font, board, seat);
        drawOutcome(extractor, font, screenW, screenH);
    }

    /**
     * Who won, over the board that decided it.
     * <p>
     * Drawn from here rather than from either of the two things that call this,
     * so it appears whether the cursor is up or the camera is the player's --
     * the duel ending is not a moment to be told different things depending on
     * which key you happened to be holding.
     * <p>
     * It fades with the board, on the same clock, because it is part of the
     * same goodbye rather than a notice pinned over it.
     */
    private static void drawOutcome(GuiGraphicsExtractor extractor, Font font, int screenW,
        int screenH)
    {
        if(!de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.ending())
        {
            return;
        }
        float alpha = de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField
            .endingAlpha();
        if(alpha <= 0F)
        {
            return;
        }
        // Read from the RESULT, exactly as the duel screen reads it, and not
        // from the won flag -- which is the server's word about a seat rather
        // than this viewer's, and had the board congratulating the loser. One
        // string, one test, and the two boards cannot disagree about who won.
        String outcome = DuelClientState.result == null ? "" : DuelClientState.result.trim();
        boolean won = outcome.equalsIgnoreCase("Victory");
        boolean drew = outcome.equalsIgnoreCase("Draw");
        String headline = won ? "VICTORY" : drew ? "DRAW" : "DEFEAT";
        int colour = won ? 0xFFD700 : drew ? 0xFFC2C9D6 : 0xFF4C4C;
        // And nothing under it. The duel screen used to carry a second line and
        // dropped it; repeating the engine's own wording beneath a word that
        // already says it is the line it dropped.

        int shade = Math.round(alpha * 255F) << 24;
        float scale = Math.max(2F, chromeScale(screenW, screenH) * 3F);
        int bandH = Math.round(font.lineHeight * scale) + 18;
        int bandTop = Math.round(screenH * 0.36F);

        // The band, but not the duel screen's full-screen dim over it: that
        // screen is covering a board it is finished with, and this one is
        // watching a board fade. Blacking it out would hide the very thing
        // being said goodbye to.
        extractor.fill(0, bandTop, screenW, bandTop + bandH, Math.round(alpha * 176F) << 24);
        extractor.fill(0, bandTop, screenW, bandTop + 1, shade | colour);
        extractor.fill(0, bandTop + bandH - 1, screenW, bandTop + bandH, shade | colour);

        extractor.pose().pushMatrix();
        extractor.pose().scale(scale, scale);
        extractor.centeredText(font, headline, Math.round(screenW / 2F / scale),
            Math.round((bandTop + (bandH - font.lineHeight * scale) / 2F) / scale),
            shade | colour);
        extractor.pose().popMatrix();
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
        int screenW, int screenH, int barH)
    {
        boolean yourTurn = board.turnPlayer() == 0;
        int cellW = cellWidth(screenW, screenH);
        int cellH = cellHeight(screenW, screenH);
        int width = PHASE_NAMES.length * cellW;
        int x = barLeft(screenW, screenH);
        int y = barTop(barH);

        DdBlitUtil.fullBlit(extractor, DuelTextures.PHASE_CASE, x - padX(screenW, screenH),
            y - padY(screenW, screenH), width + padX(screenW, screenH) * 2, cellH + padY(screenW, screenH) * 2);

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

    private static int cellWidth(int screenW, int screenH)
    {
        return Math.max(18,
            Math.min(CELL_W_MAX, Math.round(reference(screenW, screenH) * CELL_W_SHARE)));
    }

    private static int cellHeight(int screenW, int screenH)
    {
        return Math.max(5, Math.round(cellWidth(screenW, screenH) * (float)CELL_H_BASE / CELL_W_BASE));
    }

    private static int padX(int screenW, int screenH)
    {
        return Math.round(cellWidth(screenW, screenH) * (float)PAD_X_BASE / CELL_W_BASE);
    }

    private static int padY(int screenW, int screenH)
    {
        return Math.max(2, Math.round(cellHeight(screenW, screenH) * (float)PAD_Y_BASE / CELL_H_BASE));
    }

    private static int barLeft(int screenW, int screenH)
    {
        return (screenW - PHASE_NAMES.length * cellWidth(screenW, screenH)) / 2;
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
    public static int phaseAt(int screenW, int screenH, double mouseX, double mouseY)
    {
        int cellW = cellWidth(screenW, screenH);
        int y = barTop(barHeight(screenW, screenH));
        if(mouseY < y || mouseY >= y + cellHeight(screenW, screenH))
        {
            return -1;
        }
        int phase = (int)Math.floor((mouseX - barLeft(screenW, screenH)) / (double)cellW);
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
        int screenH, int barH)
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
        float scale = clockScale(screenW, screenH);
        extractor.pose().scale(scale, scale);
        extractor.centeredText(font, clock,
            Math.round(screenW / 2F / scale),
            Math.round((barTop(barH) + cellHeight(screenW, screenH) + padY(screenW, screenH) + 4) / scale),
            colour);
        extractor.pose().popMatrix();
    }
}
