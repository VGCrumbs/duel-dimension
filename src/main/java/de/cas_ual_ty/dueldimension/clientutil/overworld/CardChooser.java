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

    /**
     * Is this pile one a duellist may look through?
     * <p>
     * A graveyard and a banished pile are public knowledge -- both duellists
     * may read either at any time, and the duel screen has always let them. An
     * Extra Deck is its owner's alone, and a Deck is nobody's: the client is
     * only ever told how many cards are in it, so there is nothing to show.
     */
    public static boolean viewable(int location, int controller)
    {
        return location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE
            || location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_REMOVED
            || (location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_EXTRA
                && controller == 0);
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

    /**
     * The first row shown, when there are more than fit.
     * <p>
     * A graveyard is not a fixed size and neither is a deck, so a grid that
     * lays out however many rows the count comes to will eventually lay them
     * out past the bottom edge of the window -- where they cannot be clicked,
     * cannot be read, and cannot be got at by any means at all. The duel screen
     * has had exactly this since it was written.
     * <p>
     * Static because the panel is: one picker is open at a time, and the grid
     * is a drawing of whatever is currently being asked rather than an object
     * anybody holds.
     */
    private static int scroll;

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

        // However many rows the room actually holds, once the cards are as
        // small as they are allowed to get. Past that point shrinking further
        // would make the art unreadable to fit a graveyard that has no upper
        // size, so the rest is scrolled to rather than squeezed in.
        int fits = Math.max(1, (roomH - PAD * 2 - HEADER + GAP) / (cardH + NAME_LINE + GAP));
        rows = Math.min(rows, fits);

        int grid = columns * cardW + (columns - 1) * GAP;
        // Wide enough for its own header, not only for its cards. A panel sized
        // to the grid alone is a panel whose title hangs out of it, which is
        // exactly what it did. A scrolled panel's header carries a range as
        // well, and only then -- widening every picker for a count most of them
        // never show would be the same bug wearing the other face.
        boolean scrolled = (count + columns - 1) / columns > rows;
        int width = Math.max(grid, titleWidth(scrolled)) + PAD * 2;
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
    public static int at(int count, int screenW, int screenH, double mouseX, double mouseY)
    {
        Layout layout = layout(count, screenW, screenH);
        for(int cell = 0; cell < count; cell++)
        {
            if(!shown(layout, cell))
            {
                continue;
            }
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

    private static int titleWidth(boolean withRange)
    {
        return net.minecraft.client.Minecraft.getInstance().font.width(
            withRange ? TITLE + "   00 - 00 / 00" : TITLE);
    }

    private static int cellX(Layout layout, int cell)
    {
        return layout.gridX() + (cell % layout.columns()) * (layout.cardW() + GAP);
    }

    private static int cellY(Layout layout, int cell)
    {
        return layout.gridY() + (cell / layout.columns() - scroll)
            * (layout.cardH() + NAME_LINE + GAP);
    }

    /** Is this cell one of the rows currently on screen? */
    private static boolean shown(Layout layout, int cell)
    {
        int row = cell / layout.columns();
        return row >= scroll && row < scroll + layout.rows();
    }

    /**
     * The furthest down the wheel may go: the last row, sitting at the bottom.
     * <p>
     * Not the last row sitting at the TOP, which would scroll a full grid of
     * cards off into blank panel below them.
     */
    public static int maxScroll(int count, int screenW, int screenH)
    {
        Layout layout = layout(count, screenW, screenH);
        int total = (count + layout.columns() - 1) / layout.columns();
        return Math.max(0, total - layout.rows());
    }

    /**
     * Turns the wheel, and says whether there was anywhere to turn it.
     * <p>
     * The caller needs the answer: a wheel that changed nothing should fall
     * through to whatever else wants it rather than being swallowed by a panel
     * that is already showing everything it has.
     */
    public static boolean wheel(int count, int screenW, int screenH, double delta)
    {
        int ceiling = maxScroll(count, screenW, screenH);
        if(ceiling <= 0)
        {
            return false;
        }
        scroll = Math.clamp(scroll - (int)Math.signum(delta), 0, ceiling);
        return true;
    }

    public static void draw(GuiGraphicsExtractor extractor, Font font, EnginePrompt prompt,
        List<Integer> options, int mouseX, int mouseY)
    {
        if(prompt == null || options.isEmpty())
        {
            return;
        }
        List<Identifier> faces = new java.util.ArrayList<>(options.size());
        List<String> names = new java.util.ArrayList<>(options.size());
        List<Integer> codes = new java.util.ArrayList<>(options.size());
        for(int option : options)
        {
            EnginePrompt.Option offered = prompt.options().get(option);
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)offered.cardCode());
            faces.add(card == null ? DuelTextures.COVER
                : DuelTextures.cardSmooth(card, (byte)offered.art(),
                    DuelTextures.PREVIEW_CARD_SIZE));
            String name = card == null ? offered.label() : card.getName();
            names.add(name == null ? "" : name);
            codes.add(card == null ? 0 : offered.cardCode());
        }
        drawGrid(extractor, font, TITLE, faces, names, codes, mouseX, mouseY, true);
    }

    /**
     * The grid itself: a panel of cards with their names under them.
     * <p>
     * Shared by the picker and by the pile viewer, which are the same object
     * asked two different questions -- "which of these" and "what is in here".
     * A second grid would be a second set of layout bugs.
     *
     * @param pick true when a cell can be clicked, which is what decides
     *             whether cells light up under the cursor
     */
    public static void drawGrid(GuiGraphicsExtractor extractor, Font font, String title,
        List<Identifier> faces, List<String> names, List<Integer> codes, int mouseX, int mouseY,
        boolean pick)
    {
        int screenW = extractor.guiWidth();
        int screenH = extractor.guiHeight();
        Layout layout = layout(faces.size(), screenW, screenH);

        // Dimmed rather than hidden. The question is about the duel, and a
        // duellist deciding which card to send wants to see the board they are
        // sending it from.
        extractor.fill(0, 0, screenW, screenH, 0x90000000);
        NineSlice.draw(extractor, HubTextures.PANEL, layout.x(), layout.y(), layout.width(),
            layout.height());
        // A pile too tall to show at once says so, in the duel screen's own
        // words. Without it a scrolled panel is indistinguishable from a pile
        // that happens to hold what is on screen, and a duellist reading a
        // graveyard for a combo has no way to know there is more of it.
        String heading = title;
        if(maxScroll(faces.size(), screenW, screenH) > 0)
        {
            heading = title + "   " + (scroll * layout.columns() + 1) + " - "
                + Math.min(faces.size(), (scroll + layout.rows()) * layout.columns())
                + " / " + faces.size();
        }
        extractor.text(font, heading, layout.x() + (layout.width() - font.width(heading)) / 2,
            layout.y() + 5, 0xFFF4D089, true);

        // Clamped here rather than only where the wheel turns: the pile behind
        // this panel can shrink under it -- a graveyard is banished, a card is
        // drawn -- and a scroll position left pointing past the end would draw
        // an empty panel over a duel with no way to get back to the cards.
        scroll = Math.clamp(scroll, 0, maxScroll(faces.size(), screenW, screenH));

        int hovered = at(faces.size(), screenW, screenH, mouseX, mouseY);
        if(hovered != marqueeCell)
        {
            // A new cell starts its own clock, so a name always begins from the
            // beginning rather than halfway through the last one's journey.
            marqueeCell = hovered;
            marqueeSince = System.currentTimeMillis();
        }

        for(int cell = 0; cell < faces.size(); cell++)
        {
            if(!shown(layout, cell))
            {
                continue;
            }
            int cardX = cellX(layout, cell);
            int cardY = cellY(layout, cell);
            boolean over = cell == hovered;

            if(over && pick)
            {
                NineSlice.draw(extractor, HubTextures.PANEL, cardX - 3, cardY - 3,
                    layout.cardW() + 6, layout.cardH() + 6, NineSlice.HOVER, 3, 0.9F);
            }
            // The card's own window, not the whole sheet. A shipped card
            // texture is letterboxed -- the picture sits in the middle of a
            // square file with transparent margins -- so blitting all of it
            // into the cell drew the card at six tenths of the width and seven
            // eighths of the height it was given, which is exactly the skinny
            // card this produced. The same window CardRenderer samples for the
            // board, asked the same way.
            Identifier face = faces.get(cell);
            boolean whole = de.cas_ual_ty.dueldimension.clientutil.CardFaces.isCardShaped(face);
            DdBlitUtil.blit(extractor, face, cardX, cardY, layout.cardW(), layout.cardH(),
                whole ? 0F : DuelTextures.CARD_U0, whole ? 0F : DuelTextures.CARD_V0,
                whole ? 1F : DuelTextures.CARD_U1, whole ? 1F : DuelTextures.CARD_V1,
                DdBlitUtil.NO_TINT);
            drawName(extractor, font, names.get(cell), cardX, cardY + layout.cardH() + 1,
                layout.cardW(), over, System.currentTimeMillis());
        }

        // Shift reads the card, the same as it does on the board and in the
        // deck builder. A name alone says which card it is; a duellist choosing
        // between three effects needs to know what they DO, and a picker that
        // covers the board has covered the only other place to find out.
        if(hovered >= 0 && hovered < codes.size() && codes.get(hovered) != 0
            && ClientDuelField.shiftHeld())
        {
            CardBubble.draw(extractor, font, codes.get(hovered), mouseX, mouseY, screenW, screenH);
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

    /** Forgets which cell was hovered and how far down, so the next picker starts clean. */
    public static void reset()
    {
        marqueeCell = -1;
        marqueeSince = 0L;
        scroll = 0;
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
