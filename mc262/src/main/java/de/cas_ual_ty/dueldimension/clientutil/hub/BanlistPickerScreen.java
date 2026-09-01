package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Choosing which forbidden/limited list a deck is built to.
 *
 * <h2>A list, not a carousel</h2>
 * The duel lobby cycles its banlist with one button, and that is right there:
 * a room is configured once and the control sits among five others. Here it
 * would not be. EDOPro ships one {@code .conf} per format going back to 2004,
 * so a server with a reference install offers dozens — cycling to "2015.11 TCG
 * Goat Format" would mean twenty presses, and the name is long enough that a
 * button labelled with the current one would re-flow the deck panel's row every
 * time it changed. So this is a scrolling list, and the button that opens it
 * keeps a fixed label.
 *
 * <h2>What it decides, and what it does not</h2>
 * Which list the deck EDITOR checks against: copy limits, the dimming of a card
 * already at its maximum, and the legality of the finished deck. It does not
 * decide what a duel is played under — a room does that, and the lobby has its
 * own control for it. A deck built to the current TCG list can still be taken
 * into a room running no list at all, which is the point of the two being
 * separate.
 *
 * <p>The choice is a request. {@code DeckEdits.setDeckBanlist} checks the id
 * against what the server actually offers, because a client naming a list is
 * naming a set of copy limits.
 */
public class BanlistPickerScreen extends Screen
{
    private final Screen parent;

    /** Everything offered, "No Banlist" first. Read once: it cannot change while this is open. */
    private final List<Banlist> lists;

    /** First visible row. */
    private int scroll;

    private static final int ROW_H = 16;
    private static final int PANEL_PAD = 8;
    private static final int TITLE_H = 14;
    private static final int FOOTER_H = 26;
    /** The panel's share of the window, so it fits at any size the game allows. */
    private static final float PANEL_W_FRACTION = 0.55F;
    private static final float PANEL_H_FRACTION = 0.72F;
    private static final int PANEL_W_MIN = 180;
    private static final int PANEL_W_MAX = 320;

    public BanlistPickerScreen(Screen parent)
    {
        super(Component.literal("Banlist"));
        this.parent = parent;
        this.lists = EditorState.banlists();
    }

    // ---- geometry ----

    private int panelW()
    {
        return Math.max(Math.min(PANEL_W_MIN, width - 8),
            Math.min(PANEL_W_MAX, Math.round(width * PANEL_W_FRACTION)));
    }

    private int panelH()
    {
        return Math.min(height - 8, Math.round(height * PANEL_H_FRACTION));
    }

    private int panelX()
    {
        return (width - panelW()) / 2;
    }

    private int panelY()
    {
        return (height - panelH()) / 2;
    }

    private int listTop()
    {
        return panelY() + PANEL_PAD + TITLE_H;
    }

    private int listHeight()
    {
        return Math.max(ROW_H, panelH() - PANEL_PAD * 2 - TITLE_H - FOOTER_H);
    }

    private int visibleRows()
    {
        return Math.max(1, listHeight() / ROW_H);
    }

    private int maxScroll()
    {
        return Math.max(0, lists.size() - visibleRows());
    }

    @Override
    protected void init()
    {
        // Opened on the row the deck is already built to, so a player with
        // thirty lists does not have to find their own selection before they can
        // see it. Clamped, because a list shorter than the view has no scroll.
        int selected = indexOfCurrent();
        scroll = Math.max(0, Math.min(maxScroll(), selected - visibleRows() / 2));

        int buttonW = Math.min(80, panelW() - PANEL_PAD * 2);
        addRenderableWidget(new HubWidgets.TextureButton(
            panelX() + panelW() - PANEL_PAD - buttonW,
            panelY() + panelH() - PANEL_PAD - 20, buttonW, 20,
            Component.literal("Back"), pressed -> onClose()));
    }

    /**
     * The id of the list actually in force, with the default sentinel resolved.
     * <p>
     * The deck may carry {@link Banlist#DEFAULT_ID}, which is no row in this
     * list. Matching on the raw id would highlight nothing -- and, because the
     * search falls back to 0, would quietly highlight "No Banlist", which is the
     * one answer a defaulted deck definitely is not.
     * <p>
     * Choosing a row stores that row's OWN id, so picking the list a deck was
     * already following pins it there: it stops tracking the server's current
     * TCG list and becomes a deck built to that format. That is the intended
     * difference between a default and a choice.
     */
    private String resolvedId()
    {
        return EditorState.banlist().id();
    }

    private int indexOfCurrent()
    {
        String current = resolvedId();
        for(int i = 0; i < lists.size(); i++)
        {
            if(lists.get(i).id().equals(current))
            {
                return i;
            }
        }
        return 0;
    }

    // ---- input ----

    /** Which row is under the cursor, or -1. */
    private int rowAt(double mouseX, double mouseY)
    {
        if(mouseX < panelX() + PANEL_PAD || mouseX > panelX() + panelW() - PANEL_PAD)
        {
            return -1;
        }
        int offset = (int)((mouseY - listTop()) / ROW_H);
        if(offset < 0 || offset >= visibleRows())
        {
            return -1;
        }
        int index = scroll + offset;
        return index < lists.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        int row = rowAt(event.x(), event.y());
        if(row >= 0)
        {
            EditorState.setDeckBanlist(lists.get(row).id());
            // Closed on the choice. This screen exists to answer one question,
            // and staying open after it has been answered would leave the
            // player to work out that the highlight moving WAS the answer.
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if(maxScroll() > 0)
        {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        // fillGradient rather than extractBackground, for the reason
        // DeckEditorScreen gives: the vanilla one blurs, the blur is
        // once-per-frame, and opening this over a screen that already asked for
        // one has taken the client down before.
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int x = panelX();
        int y = panelY();
        NineSlice.draw(graphics, HubTextures.PANEL, x, y, panelW(), panelH());

        String title = "Banlist";
        graphics.text(font, title, x + PANEL_PAD, y + PANEL_PAD, MenuInk.title(),
            MenuInk.shadow());
        // What is in force now, on the same line and to the right. The list
        // below says it too, by highlighting a row -- but a row can be scrolled
        // out of view, and the answer to "what is this deck built to" should not
        // depend on where the list happens to be sitting.
        String current = EditorState.banlist().displayName();
        graphics.text(font, current, x + panelW() - PANEL_PAD - font.width(current),
            y + PANEL_PAD, MenuInk.body(), MenuInk.shadow());

        int top = listTop();
        int rows = visibleRows();
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, x + PANEL_PAD - 2, top - 2,
            panelW() - PANEL_PAD * 2 + 4, rows * ROW_H + 4);

        String selectedId = resolvedId();
        for(int offset = 0; offset < rows && scroll + offset < lists.size(); offset++)
        {
            Banlist list = lists.get(scroll + offset);
            int rowY = top + offset * ROW_H;
            boolean over = mouseX >= x + PANEL_PAD && mouseX <= x + panelW() - PANEL_PAD
                && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean chosen = list.id().equals(selectedId);
            // The chosen row is drawn on the button texture's SELECTED state
            // rather than in a different ink, so "which one is it" survives
            // being read at a glance and survives a resource pack recolouring
            // the ink. Same decision as TabButton's.
            if(chosen || over)
            {
                NineSlice.draw(graphics, HubTextures.BUTTON, x + PANEL_PAD, rowY,
                    panelW() - PANEL_PAD * 2, ROW_H,
                    chosen ? NineSlice.SELECTED : NineSlice.HOVER, 3);
            }
            // Trimmed to the row rather than allowed to run under the scrollbar
            // lane: EDOPro's longest names are wider than this panel at the
            // smallest window the game allows.
            String name = font.plainSubstrByWidth(list.displayName(),
                panelW() - PANEL_PAD * 2 - 8);
            graphics.text(font, name, x + PANEL_PAD + 4,
                rowY + (ROW_H - 8) / 2,
                chosen ? MenuInk.title() : over ? MenuInk.label() : MenuInk.body(),
                MenuInk.shadow());
        }

        // How many are out of view, said in words rather than drawn as a bar.
        // A scrollbar here would be four pixels of furniture beside a number
        // that fits in the space it would have taken.
        if(maxScroll() > 0)
        {
            String more = (scroll + rows) + " / " + lists.size();
            graphics.text(font, more, x + PANEL_PAD,
                y + panelH() - PANEL_PAD - 20 + (20 - 8) / 2, MenuInk.dim(), MenuInk.shadow());
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreenAndShow(parent);
        }
    }
}
