package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Choosing between cards nobody can see, at the board.
 * <p>
 * Some questions have no answer on the board at all. "Send a card from your
 * Extra Deck to the graveyard" names six monsters that are inside a stack, and
 * pointing at the stack says nothing about which -- six identical backs is not
 * a choice. Those used to be handed to the duel screen, which meant an ordinary
 * fusion pulled a duellist out of the world and put them back a second later.
 * <p>
 * So the board grew the one thing it was missing. The same shape as the duel
 * screen's picker and for the same reasons -- art, a name, a dim behind so the
 * board is still readable underneath -- but drawn where a duellist already is.
 * <p>
 * The names are the reason this needed care. A card is sixty-odd pixels wide
 * and "Elemental HERO Shining Flare Wingman" is not, so the duel screen cuts it
 * to "Elemental HE" and two different cards can come out reading the same. Here
 * a name that does not fit SCROLLS while the cursor is on it: out to the end,
 * a pause to read it, back to the beginning, a longer pause, and again. Nothing
 * is hidden and nothing has to be guessed at from a prefix.
 */
public final class CardChooser
{
    private CardChooser()
    {
    }

    /** The most cards this will take on before the duel screen is the better tool. */
    public static final int MAX_CARDS = 20;

    /**
     * What the panel is called, whatever the engine calls it.
     * <p>
     * The engine's own titles are instructions written for a screen with room
     * for them -- "Select the card(s) to place on the field(1-1)" -- and a
     * panel three cards wide has no such room. It ran straight out of the frame
     * and past the edge of it. The panel is already unmistakably a row of cards
     * waiting to be clicked; the header only has to name it.
     */
    private static final String TITLE = "Card Select";

    private static final int GAP = 6;
    private static final int PAD = 8;
    private static final int HEADER = 16;
    private static final int NAME_LINE = 11;
    private static final int CARD_W = 62;
    private static final int MIN_CARD_W = 34;
    /** Room for the one glyph a scroll can be halfway through at either end. */
    private static final int PART_CHAR = 8;

    /** How fast a name that does not fit travels, in pixels a second. */
    private static final float SCROLL_SPEED = 22F;
    /** How long it rests at the end, having shown everything. */
    private static final float END_HOLD = 1.5F;
    /** And at the beginning, before setting off again. */
    private static final float START_HOLD = 2F;

    /** Which cell the cursor is on, and when it arrived: the marquee's whole clock. */
    private static int marqueeCell = -1;
    private static long marqueeSince;

    /** The picker's geometry, sized to the room this window actually has. */
    public record Layout(int x, int y, int width, int height, int cardW, int cardH, int columns,
        int rows, int gridX, int gridY)
    {
    }

    /**
     * Laid out to fit between the instruments and the hand, shrinking the cards
     * rather than spilling over either.
     * <p>
     * The duel screen's picker learned this the hard way: a fixed card size and
     * a fixed row count take no notice of the window, and at a gui scale of
     * three there is far less room than the numbers suggest.
     */
    public static Layout layout(int count, int screenW, int screenH)
    {
        int ceiling = DuelHud.below(screenW, screenH);
        int floor = HandLayout.topEdge(screenH) - 6;
        int roomW = screenW - 40;
        int roomH = Math.max(60, floor - ceiling - 8);

        int cardW = CARD_W;
        int cardH;
        int columns;
        int rows;
        while(true)
        {
            cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
            int perRow = Math.max(1, (roomW + GAP) / (cardW + GAP));
            columns = Math.max(1, Math.min(perRow, Math.min(count, 8)));
            rows = (count + columns - 1) / columns;
            int needed = PAD * 2 + HEADER + rows * (cardH + NAME_LINE) + (rows - 1) * GAP;
            if(needed <= roomH || cardW - 4 < MIN_CARD_W)
            {
                break;
            }
            cardW -= 4;
        }

        int grid = columns * cardW + (columns - 1) * GAP;
        // Wide enough for its own header, not only for its cards. A panel sized
        // to the grid alone is a panel whose title hangs out of it, which is
        // exactly what it did.
        int width = Math.max(grid, titleWidth()) + PAD * 2;
        int height = PAD * 2 + HEADER + rows * (cardH + NAME_LINE) + (rows - 1) * GAP;
        int x = (screenW - width) / 2;
        int y = Math.max(ceiling + 4, ceiling + (roomH - height) / 2);
        // And the grid centred inside whatever that came to, rather than left
        // against the padding: one card in a panel widened by its title would
        // otherwise sit off to one side of it.
        return new Layout(x, y, width, height, cardW, cardH, columns, rows,
            x + (width - grid) / 2, y + PAD + HEADER);
    }

    /**
     * The option under the cursor, or -1.
     * <p>
     * Measured off the same layout that draws them, so a cell can never be
     * somewhere other than where it is clicked.
     */
    public static int at(List<Integer> options, int screenW, int screenH, double mouseX,
        double mouseY)
    {
        Layout layout = layout(options.size(), screenW, screenH);
        for(int cell = 0; cell < options.size(); cell++)
        {
            int cardX = cellX(layout, cell);
            int cardY = cellY(layout, cell);
            if(mouseX >= cardX && mouseX < cardX + layout.cardW()
                && mouseY >= cardY && mouseY < cardY + layout.cardH() + NAME_LINE)
            {
                return cell;
            }
        }
        return -1;
    }

    private static int titleWidth()
    {
        return net.minecraft.client.Minecraft.getInstance().font.width(TITLE);
    }

    private static int cellX(Layout layout, int cell)
    {
        return layout.gridX() + (cell % layout.columns()) * (layout.cardW() + GAP);
    }

    private static int cellY(Layout layout, int cell)
    {
        return layout.gridY() + (cell / layout.columns())
            * (layout.cardH() + NAME_LINE + GAP);
    }

    public static void draw(GuiGraphicsExtractor extractor, Font font, EnginePrompt prompt,
        List<Integer> options, int mouseX, int mouseY)
    {
        if(prompt == null || options.isEmpty())
        {
            return;
        }
        int screenW = extractor.guiWidth();
        int screenH = extractor.guiHeight();
        Layout layout = layout(options.size(), screenW, screenH);

        // Dimmed rather than hidden. The question is about the duel, and a
        // duellist deciding which card to send wants to see the board they are
        // sending it from.
        extractor.fill(0, 0, screenW, screenH, 0x90000000);
        NineSlice.draw(extractor, HubTextures.PANEL, layout.x(), layout.y(), layout.width(),
            layout.height());

        extractor.text(font, TITLE, layout.x() + (layout.width() - font.width(TITLE)) / 2,
            layout.y() + 5, 0xFFF4D089, true);

        int hovered = at(options, screenW, screenH, mouseX, mouseY);
        if(hovered != marqueeCell)
        {
            // A new cell starts its own clock, so a name always begins from the
            // beginning rather than halfway through the last one's journey.
            marqueeCell = hovered;
            marqueeSince = System.currentTimeMillis();
        }

        for(int cell = 0; cell < options.size(); cell++)
        {
            EnginePrompt.Option option = prompt.options().get(options.get(cell));
            int cardX = cellX(layout, cell);
            int cardY = cellY(layout, cell);
            boolean over = cell == hovered;

            if(over)
            {
                NineSlice.draw(extractor, HubTextures.PANEL, cardX - 3, cardY - 3,
                    layout.cardW() + 6, layout.cardH() + 6, NineSlice.HOVER, 3, 0.9F);
            }

            Properties card = DdDatabase.PROPERTIES_LIST.get((long)option.cardCode());
            Identifier texture = card == null ? DuelTextures.COVER
                : DuelTextures.cardSmooth(card, (byte)option.art(),
                    DuelTextures.PREVIEW_CARD_SIZE);
            DdBlitUtil.fullBlit(extractor, texture, cardX, cardY, layout.cardW(), layout.cardH());

            String name = card == null ? option.label() : card.getName();
            drawName(extractor, font, name == null ? "" : name, cardX, cardY + layout.cardH() + 1,
                layout.cardW(), over, System.currentTimeMillis());
        }
    }

    /**
     * A card's name under its cell, scrolling if it does not fit and the cursor
     * is on it.
     * <p>
     * Clipped to the cell rather than shortened, so what travels past is the
     * whole name and not an abbreviation of it. A name that fits is simply
     * centred and never moves -- most do, and a caption that drifts for no
     * reason is worse than one that sits still.
     */
    private static void drawName(GuiGraphicsExtractor extractor, Font font, String name, int x,
        int y, int room, boolean over, long now)
    {
        int width = font.width(name);
        if(width <= room)
        {
            extractor.text(font, name, x + (room - width) / 2, y, over ? 0xFFFFE9B0 : 0xFFC2C9D6,
                true);
            return;
        }
        int offset = over ? scrolled(width - room, now - marqueeSince) : 0;

        // Clipped by MEASUREMENT rather than by the scissor alone: whatever is
        // already past the left edge is not handed to the font at all, and the
        // tail is cut to the room that is left. A name is not allowed to run
        // into its neighbour or out of the panel even for the frame it takes a
        // clip to catch up.
        int skipped = 0;
        int start = 0;
        while(start < name.length())
        {
            int step = font.width(name.substring(start, start + 1));
            if(skipped + step > offset)
            {
                break;
            }
            skipped += step;
            start++;
        }
        String shown = font.plainSubstrByWidth(name.substring(start), room + PART_CHAR);

        extractor.enableScissor(x, y - 1, x + room, y + NAME_LINE);
        extractor.text(font, shown, x - (offset - skipped), y,
            over ? 0xFFFFE9B0 : 0xFFC2C9D6, true);
        extractor.disableScissor();
    }

    /**
     * How far along a name is: out at a readable pace, a rest at the end, back
     * to the start, a longer rest, and round again.
     * <p>
     * The rest at the END is what makes the last word readable -- a marquee
     * that snaps back the instant it arrives shows the end of a name for a
     * single frame. The longer rest at the START is so a player who looked away
     * finds the beginning waiting rather than the middle going past.
     */
    private static int scrolled(int overflow, long heldMs)
    {
        float travel = overflow / SCROLL_SPEED;
        float cycle = travel + END_HOLD + START_HOLD;
        float phase = (heldMs / 1000F) % cycle;
        if(phase < travel)
        {
            return Math.round(phase * SCROLL_SPEED);
        }
        return phase < travel + END_HOLD ? overflow : 0;
    }

    /** Forgets which cell was hovered, so the next picker starts clean. */
    public static void reset()
    {
        marqueeCell = -1;
        marqueeSince = 0L;
    }

    /** Every option of the open prompt, in the order the engine gave them. */
    public static List<Integer> optionsOf(EnginePrompt prompt)
    {
        List<Integer> indices = new java.util.ArrayList<>();
        if(prompt != null)
        {
            for(int index = 0; index < prompt.options().size(); index++)
            {
                indices.add(index);
            }
        }
        return indices;
    }

    /** True while this client is being asked one of these. */
    public static boolean open()
    {
        return de.cas_ual_ty.dueldimension.clientutil.PromptOptions.needsPicker(
            DuelClientState.prompt);
    }
}
