package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardPresentation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * One card, written out: its name, its classifications as plates, and its text.
 *
 * <h2>Why this is a class and not a method on a screen</h2>
 * Because four screens were drawing the same thing four ways. The pack opening
 * had the good version -- fact plates on one row, a Pendulum card's scales in
 * their own box with the effect starting beside them, a scroll bar when the
 * text runs long -- and the deck editor, the 2D duel sidebar and the 3D duel
 * each had an older one. Every improvement to the reading panel had to be made
 * up to four times or it made the four disagree, and they did.
 * <p>
 * So the layout lives here once. A caller says WHERE and HOW BIG, and gets back
 * what it drew so it can clamp its own scrolling; everything about what a card
 * looks like is decided in this file.
 *
 * <h2>What a caller still owns</h2>
 * Placement, and only placement. The pack opening anchors this to the focused
 * card's own text box; the deck editor and the 3D duel put it by the cursor;
 * the 2D duel gives it the sidebar's column. None of them says anything about
 * what goes inside it.
 */
public final class CardInfoPanel
{
    private CardInfoPanel() {}

    /**
     * A fact plate's proportions, which FOLLOW ITS TEXT rather than being fixed.
     * <p>
     * They were constants, and that is what made the halved text look smaller
     * than it is: three-pixel glyphs floating in a twelve-pixel plate with five
     * pixels of air each side reads as a mistake, however legible the letters
     * are. The plate is now the height of its own line plus a border, so the
     * compact panels get a compact plate and the pack opening's is unchanged.
     */
    private static final int PILL_LEADING = 6;
    private static final int PILL_GAP = 2;
    /** Text inset each side, at the body scale; smaller text gets less. */
    private static final int PILL_PAD = 5;

    /**
     * The scales' plate, taller than a line of body text.
     * <p>
     * A plate squeezed to the line pitch has no room for its own border and
     * overlaps the line beneath it; this gives it a band of its own, which the
     * first line of the Pendulum Effect shares.
     */
    private static final int PEND_PLATE_H = 13;
    /** How far in from the panel edge the scales plate sits. */
    private static final int PEND_PLATE_X = 7;
    /**
     * Air between the Pendulum box and the effect below it. The blank LINE that
     * used to sit there doubled a separation the tinted plate already makes;
     * nothing at all left the lower box hard against the plate's border.
     */
    private static final int PEND_GAP = 3;
    /** How far the Pendulum text is inset from the margin the lower box uses. */
    private static final int PEND_INSET = 2;

    /** The colour of the Pendulum half of a card. */
    private static final int PENDULUM_TINT = 0xFF4FD8C4;
    /** Minecraft's own blue and red, which is what the scales' arrows are printed in. */
    private static final int PEND_BLUE = 0xFF5555FF;
    private static final int PEND_RED = 0xFFFF5555;

    private static final int BAR_W = 4;

    /**
     * What a panel came to.
     *
     * @param height  what it actually drew, which is its content capped by the
     *                room it was given
     * @param lines   how many lines of body text the card has in total
     * @param visible how many of them fitted; the difference is what scrolls
     */
    public record Layout(int x, int y, int width, int height, int lines, int visible)
    {
        /** The furthest a caller's scroll offset may go. */
        public int maxScroll()
        {
            return Math.max(0, lines - visible);
        }

        public boolean scrolls()
        {
            return maxScroll() > 0;
        }
    }

    /**
     * How tall this card comes to, so a caller that places by the cursor can
     * decide where to put the panel before it draws one.
     *
     * @param artH how much room the card's picture takes above the text, or 0
     *             for a panel that shows no picture
     */
    public static int height(Font font, Properties card, int width, int maxHeight, int artH,
        boolean compact)
    {
        if(card == null)
        {
            return 0;
        }
        Body body = body(font, card, width, compact);
        return panelHeight(body, maxHeight, artH, visibleLines(body, maxHeight, artH));
    }

    private static int artBand(int artH)
    {
        return artH > 0 ? artH + 4 : 0;
    }

    private static int panelHeight(Body body, int maxHeight, int artH, int rows)
    {
        return artBand(artH) + 14 + body.factRows * (body.pillH + PILL_GAP) + 3
            + body.band + rows * body.lineH + 8;
    }

    private static int visibleLines(Body body, int maxHeight, int artH)
    {
        int header = artBand(artH) + 14 + body.factRows * (body.pillH + PILL_GAP) + 3;
        int room = Math.max(body.lineH, maxHeight - header - 8);
        int fits = Math.max(1, (room - body.band) / body.lineH);
        return Math.min(body.lines.size(), fits);
    }

    /**
     * One fact line, abbreviated to fit a plate.
     * <p>
     * "1200 ATK" becomes "A: 1200" and "800 DEF" becomes "D: 800": the label
     * moves in front of the number and loses all but its first letter, because
     * at this size the word costs more room than the number it introduces and
     * the number is the part anybody is reading. "Level 4" is "LV 4" for the
     * same reason.
     * <p>
     * Split on the separator the card itself used, so a Link monster's
     * "800 ATK / LINK-2" keeps its second half untouched -- a Link rating is
     * not a defence, and labelling it "D:" would say something untrue.
     */
    private static net.minecraft.network.chat.Component factLine(String line)
    {
        StringBuilder out = new StringBuilder();
        String[] tokens = line.split(" / ");
        for(int i = 0; i < tokens.length; i++)
        {
            if(i > 0)
            {
                out.append(" / ");
            }
            String token = tokens[i].trim();
            if(token.endsWith(" ATK"))
            {
                out.append("A: ").append(token, 0, token.length() - 4);
            }
            else if(token.endsWith(" DEF"))
            {
                out.append("D: ").append(token, 0, token.length() - 4);
            }
            else
            {
                out.append(token.replace("Level ", "LV "));
            }
        }
        return bold(out.toString());
    }

    private static net.minecraft.network.chat.Component bold(String text)
    {
        // BOLD, and this is what makes the small size readable rather than
        // merely small. At the compact scale a glyph is three pixels tall, and
        // at three pixels a stroke either exists or it does not.
        return net.minecraft.network.chat.Component.literal(text)
            .withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    /** No panel gets narrower than this, whatever the card is called. */
    private static final int MIN_WIDTH = 120;

    /**
     * The width this card's HEADER needs: the name, or the row of fact plates,
     * whichever is longer.
     *
     * <h2>Why the header and not the description</h2>
     * A description always fills whatever it is given -- it wraps -- so it can
     * never ask for a width, and a panel sized to it is really a panel sized to
     * a number somebody picked. The name and the plates are the two things that
     * have a natural width and get clipped or cramped without it, so they are
     * what the panel is measured against and the description wraps to whatever
     * they come to.
     * <p>
     * <b>This can widen a panel as well as narrow one.</b> A card whose name
     * fills the window gets the room to print it; a card called "Kuriboh" stops
     * getting the same slab as one that does not.
     *
     * @param maxWidth the most the caller can spare -- the result never exceeds
     *                 it, so a long name is still clipped rather than hung off
     *                 the side of the window
     */
    public static int preferredWidth(Font font, Properties card, int maxWidth, boolean compact)
    {
        float scale = MenuText.oneStepSmaller();
        float factScale = compact ? MenuText.smaller(2) : scale;
        int pad = Math.max(2, Math.round(PILL_PAD * (factScale / scale)));

        String name = card.getName() == null ? "" : card.getName();
        int wanted = Math.round(font.width(name) * scale);

        // The plates as they would be on ONE row, which is the arrangement the
        // layout already tries hardest to reach. Measured from the same
        // factLine the draw uses, so an abbreviation that saves room here saves
        // it there too.
        List<net.minecraft.network.chat.Component> facts = new ArrayList<>();
        CardPresentation.addFacts(card, facts);
        int row = 0;
        int plates = 0;
        for(net.minecraft.network.chat.Component fact : facts)
        {
            String text = fact.getString();
            if(text.isBlank())
            {
                continue;
            }
            row += Math.round(font.width(factLine(text)) * factScale) + pad * 2;
            plates++;
        }
        if(plates > 1)
        {
            row += PILL_GAP * (plates - 1);
        }
        // Six a side, which is where the name starts and where the text wraps.
        return Math.max(MIN_WIDTH, Math.min(maxWidth, Math.max(wanted, row) + 12));
    }

    /** Everything measuring and drawing both need, worked out once. */
    private static final class Body
    {
        List<net.minecraft.network.chat.Component> groups = new ArrayList<>();
        List<int[]> pills = new ArrayList<>();
        int factRows;
        float scale;
        /** The plates' own scale, which is the body's unless they had to shrink. */
        float factScale;
        /** And the plate that holds it, measured from that scale. */
        int pillH;
        int pillPad;
        int lineH;
        int band;
        String scaleText;
        int scalePlateW;
        CardPresentation.PeekBody peek;
        List<FormattedCharSequence> lines;
    }

    private static Body body(Font font, Properties card, int width, boolean compact)
    {
        Body b = new Body();
        List<net.minecraft.network.chat.Component> facts = new ArrayList<>();
        CardPresentation.addFacts(card, facts);
        for(net.minecraft.network.chat.Component fact : facts)
        {
            String text = fact.getString();
            if(!text.isBlank())
            {
                b.groups.add(factLine(text));
            }
        }
        // Everything but the NAME is a step smaller, and the name is a step
        // smaller than it was: this panel's job is the description.
        b.scale = MenuText.oneStepSmaller();
        // HALF SIZE EVERYWHERE BUT THE PACK OPENING.
        //
        // Tried making this adaptive -- halve them only when it saves a row --
        // and it did nothing where it was wanted: on a narrow panel three
        // groups do not fit on one line at half size either, so the rule kept
        // the larger text and stacked them anyway. The caller knows which panel
        // it is; the panel does not, and guessing from the width was a worse
        // version of being told.
        // TWO steps down, not crispScale(0.5). crispScale snaps to whole
        // device pixels, so at GUI scale 3 it rounds 0.5 back up to 2/3 -- the
        // body scale itself, which is why asking for half changed nothing.
        b.factScale = compact ? MenuText.smaller(2) : b.scale;
        b.pillH = Math.max(6, Math.round(font.lineHeight * b.factScale) + PILL_LEADING);
        // Halved text gets halved padding, or the plate is mostly margin.
        b.pillPad = Math.max(2, Math.round(PILL_PAD * (b.factScale / b.scale)));
        b.factRows = layOutPills(font, b.groups, width, b.factScale, b.pillH, b.pillPad, b.pills);

        // ONE ROW IF IT CAN POSSIBLY BE ONE ROW.
        //
        // The three groups miss a single line by a few pixels of padding on the
        // narrow panels -- and a second row costs a line of the description to
        // hold three words that were nearly there. So the padding is spent
        // down, a pixel at a time, before the layout gives up and wraps.
        //
        // Padding first, because margin is the cheapest thing on the plate to
        // lose -- a pixel of it costs nothing anybody can read.
        for(int pad = b.pillPad - 1; b.factRows > 1 && pad >= 1; pad--)
        {
            List<int[]> tighter = new ArrayList<>();
            int rows = layOutPills(font, b.groups, width, b.factScale, b.pillH, pad, tighter);
            if(rows < b.factRows)
            {
                b.pillPad = pad;
                b.factRows = rows;
                b.pills = tighter;
            }
        }
        // AND THEN THE TEXT, which the padding alone cannot always save.
        //
        // This used to stop above, on the reasoning that the letters were
        // already at the smallest size landing on whole pixels. That is true of
        // the compact panels and NOT of the wide ones, where the plates run at
        // the body's own scale -- so a long attribute line wrapped onto a second
        // row while there was still room to shrink into, and the second row cost
        // the description a line to hold three words that nearly fitted.
        //
        // Two steps at most, and only ever to reach ONE row. Past that the row
        // really is cheaper than the reading, which is what the old comment here
        // was right about.
        int from = compact ? 2 : 1;
        for(int steps = from + 1; b.factRows > 1 && steps <= from + 2; steps++)
        {
            float scale = MenuText.smaller(steps);
            if(scale >= b.factScale)
            {
                break;
            }
            int pillH = Math.max(6, Math.round(font.lineHeight * scale) + PILL_LEADING);
            int pad = Math.max(2, Math.round(PILL_PAD * (scale / b.scale)));
            List<int[]> shrunk = new ArrayList<>();
            int rows = layOutPills(font, b.groups, width, scale, pillH, pad, shrunk);
            if(rows < b.factRows)
            {
                b.factScale = scale;
                b.pillH = pillH;
                b.pillPad = pad;
                b.factRows = rows;
                b.pills = shrunk;
            }
        }
        b.lineH = Math.max(1, Math.round(10 * b.scale));

        b.scaleText = card.pendulumScaleText();
        b.scalePlateW = b.scaleText == null ? 0
            : Math.round(font.width(b.scaleText) * b.scale) + PILL_PAD * 2 + 3;
        int wrap = Math.max(1, Math.round((width - 12) / b.scale));
        b.peek = CardPresentation.peekBody(font, card, wrap,
            Math.round(b.scalePlateW / b.scale));
        b.lines = b.peek.lines();
        // The scales sit in a band of their own. Counted from whether the CARD
        // has them rather than from whether they are on screen, so the layout
        // does not shuffle as it scrolls.
        b.band = b.scaleText == null ? 0 : PEND_PLATE_H + 1 - b.lineH + PEND_GAP;
        return b;
    }

    /**
     * Draws the panel.
     *
     * @param maxHeight the most it may take; it uses less when the card is short
     * @param scroll    the caller's scroll offset, in lines
     * @param barX      where to put the scroll bar, or -1 to put it inside the
     *                  panel's right edge. The pack opening pins it to the
     *                  window edge; a panel that follows a cursor cannot.
     */
    public static Layout draw(GuiGraphicsExtractor g, Font font, Properties card,
        int x, int y, int width, int maxHeight, int scroll, int barX, boolean compact)
    {
        return draw(g, font, card, x, y, width, maxHeight, scroll, barX, null, 0, 0, 1F, compact);
    }

    /**
     * As above, with the card's picture above the text.
     *
     * @param art     the texture to blit, or null for no picture
     * @param artW    how wide to draw it; the height follows the card aspect
     * @param artH    that height, worked out by the caller so it can measure
     *                the panel before placing it
     * @param opacity the whole panel's alpha -- the deck editor's preview sits
     *                over the cards it is describing and stays translucent
     */
    public static Layout draw(GuiGraphicsExtractor g, Font font, Properties card,
        int x, int y, int width, int maxHeight, int scroll, int barX,
        net.minecraft.resources.Identifier art, int artW, int artH, float opacity,
        boolean compact)
    {
        if(card == null)
        {
            return new Layout(x, y, width, 0, 0, 0);
        }
        Body b = body(font, card, width, compact);
        int rows = visibleLines(b, maxHeight, artH);
        int panelH = panelHeight(b, maxHeight, artH, rows);
        int offset = Math.max(0, Math.min(scroll, Math.max(0, b.lines.size() - rows)));

        NineSlice.draw(g, HubTextures.PANEL, x, y, width, panelH,
            NineSlice.IDLE, 1, opacity);

        if(art != null && artH > 0)
        {
            de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(g, art,
                x + (width - artW) / 2, y + 5, artW, artH,
                de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_U0,
                de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_V0,
                de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_U1,
                de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_V1,
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.NO_TINT);
        }
        int lift = artBand(artH);

        String name = card.getName() == null ? "" : card.getName();
        g.pose().pushMatrix();
        g.pose().scale(b.scale, b.scale);
        g.text(font, name, Math.round((x + 6) / b.scale),
            Math.round((y + lift + 5) / b.scale), MenuInk.title(), MenuInk.shadow());
        g.pose().popMatrix();

        int top = y + lift + 14;
        for(int i = 0; i < b.groups.size() && i < b.pills.size(); i++)
        {
            int[] pill = b.pills.get(i);
            int px = x + 6 + pill[0];
            int py = top + pill[1];
            // ROW 0 OF 3, stated. HubTextures.BUTTON is a 24x72 sheet -- normal,
            // hover, selected -- and the four-argument draw() assumes ONE row,
            // so it maps the whole file into one tile and stacks all three
            // states on top of each other.
            NineSlice.draw(g, HubTextures.BUTTON, px, py, pill[2], b.pillH, 0, 3);
            net.minecraft.network.chat.Component group = b.groups.get(i);
            int textW = Math.round(font.width(group) * b.factScale);
            int textH = Math.round(font.lineHeight * b.factScale);
            g.pose().pushMatrix();
            g.pose().scale(b.factScale, b.factScale);
            g.text(font, group,
                Math.round((px + (pill[2] - textW) / 2) / b.factScale),
                Math.round((py + (b.pillH - textH) / 2) / b.factScale),
                0xFFC8D0DE, MenuInk.shadow());
            g.pose().popMatrix();
        }
        int bodyY = top + b.factRows * (b.pillH + PILL_GAP) + 3;

        int pendulum = b.peek.pendulumLines();
        int shownPendulum = Math.max(0, Math.min(offset + rows, pendulum) - offset);
        int glyph = Math.max(1, Math.round(font.lineHeight * b.scale));
        int scaleTextY = bodyY + (PEND_PLATE_H - glyph) / 2;
        boolean scaleShowing = b.scaleText != null && offset == 0 && rows > 0;

        if(shownPendulum > 0)
        {
            // Measured from where the LAST Pendulum line lands rather than from
            // a count of lines, because the first sits in the scales' taller
            // band and the rest do not.
            int lastY = shownPendulum <= 1 ? scaleTextY
                : bodyY + (PEND_PLATE_H + 1 - b.lineH) + (shownPendulum - 1) * b.lineH;
            NineSlice.tintedPanel(g, HubTextures.PANEL_INSET, x + 3, bodyY - 2,
                width - 6, lastY + glyph + 2 - (bodyY - 2), PENDULUM_TINT);
        }
        if(scaleShowing)
        {
            NineSlice.draw(g, HubTextures.BUTTON, x + PEND_PLATE_X, bodyY,
                b.scalePlateW, PEND_PLATE_H, 0, 3);
        }

        g.pose().pushMatrix();
        g.pose().scale(b.scale, b.scale);
        if(scaleShowing)
        {
            // COLOURED, as they are printed: blue on the left, red on the right.
            // Five pieces rather than one string, because a string handed over
            // whole cannot have half of it coloured.
            String[] pieces = {String.valueOf(card.pendulumScaleLeft()), " ◀", " / ",
                "▶ ", String.valueOf(card.pendulumScaleRight())};
            int[] inks = {MenuInk.title(), PEND_BLUE, MenuInk.dim(), PEND_RED, MenuInk.title()};
            int textW = 0;
            for(String piece : pieces)
            {
                textW += font.width(piece);
            }
            int penX = Math.round((x + PEND_PLATE_X
                + (b.scalePlateW - Math.round(textW * b.scale)) / 2) / b.scale);
            for(int i = 0; i < pieces.length; i++)
            {
                g.text(font, pieces[i], penX, Math.round(scaleTextY / b.scale),
                    inks[i], MenuInk.shadow());
                penX += font.width(pieces[i]);
            }
        }
        for(int i = 0; i < rows; i++)
        {
            boolean inPendulum = b.scaleText != null && offset + i < pendulum;
            int indent = (scaleShowing && i == 0 ? b.scalePlateW + 2 : 0)
                + (inPendulum ? PEND_INSET : 0);
            int below = b.scaleText != null && offset + i >= pendulum ? PEND_GAP : 0;
            int lineY = b.scaleText == null ? bodyY + i * b.lineH
                : i == 0 ? scaleTextY
                : bodyY + (PEND_PLATE_H + 1 - b.lineH) + i * b.lineH + below;
            g.text(font, b.lines.get(offset + i),
                Math.round((x + 6 + indent) / b.scale), Math.round(lineY / b.scale),
                MenuInk.body(), MenuInk.shadow());
        }
        g.pose().popMatrix();

        if(b.lines.size() > rows && rows > 0)
        {
            // Tinted with MenuInk.label(), the mod's existing answer to "as far
            // from the surface as it can get": near-white on every dark theme,
            // near-black above MenuTheme's LIGHT threshold.
            // Against the panel's OUTER edge, not inset from it. Inside, the
            // bar sat in the text column and the words wrapped around a gap;
            // on the edge it is where a scroll bar is looked for and the line
            // keeps its full width.
            int bar = barX >= 0 ? barX : x + width - BAR_W;
            int track = b.band + rows * b.lineH;
            int tint = MenuInk.label();
            NineSlice.tintedPanel(g, HubTextures.SCROLLBAR, bar, bodyY, BAR_W, track, 0, 2, tint);
            int thumb = Math.max(8, track * rows / b.lines.size());
            int span = Math.max(1, b.lines.size() - rows);
            NineSlice.tintedPanel(g, HubTextures.SCROLLBAR, bar,
                bodyY + (track - thumb) * offset / span, BAR_W, thumb, 1, 2, tint);
        }
        return new Layout(x, y, width, panelH, b.lines.size(), rows);
    }

    /**
     * Places the fact plates across the panel, wrapping when a row runs out.
     * <p>
     * Wrapping rather than shrinking or clipping: a monster's middle group is
     * the long one, shrinking further would make the smallest unreadable to fix
     * the largest, and clipping would drop the part that says what kind of
     * monster it is.
     *
     * @param out one {@code {x, y, width}} per group, relative to the row's left
     *            edge and top
     * @return how many rows the plates came to
     */
    private static int layOutPills(Font font, List<net.minecraft.network.chat.Component> groups,
        int width, float scale, int pillH, int pad, List<int[]> out)
    {
        int usable = width - 12;
        int x = 0;
        int row = 0;
        for(net.minecraft.network.chat.Component group : groups)
        {
            int w = Math.min(usable, Math.round(font.width(group) * scale) + pad * 2);
            if(x > 0 && x + w > usable)
            {
                row++;
                x = 0;
            }
            out.add(new int[] {x, row * (pillH + PILL_GAP), w});
            x += w + PILL_GAP;
        }
        return row + 1;
    }
}
