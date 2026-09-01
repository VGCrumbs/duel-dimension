package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayMessages;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Choosing what a display block shows.
 * <p>
 * A search box, a page of results, and three buttons for which way the card
 * lies. Its own screen rather than the deck editor with something switched off:
 * the editor is about building a legal deck -- counts, limits, the extra deck's
 * own rules -- and none of that has anything to say about a card on a pedestal.
 * <p>
 * <b>Everything is measured against the window.</b> A panel with a fixed number
 * of columns at a fixed card size is a panel that fits one window: at a gui
 * scale of three a 1634-wide client is only 545 layout pixels across, and a
 * grid sized for a bare pixel count runs off both edges with its buttons past
 * the bottom. So the card size comes from the room there is, and the grid comes
 * from the card size.
 * <p>
 * Every choice is sent as it is made rather than gathered behind an OK. A
 * display block is something a builder adjusts while looking at it, and the
 * useful thing is seeing the change land.
 */
public class CardDisplayScreen extends Screen
{
    private final BlockPos pos;
    private long code;
    private byte art;
    private int position;

    private EditBox search;
    private final List<Properties> results = new ArrayList<>();

    /**
     * Whether the list is narrowed to cards that have a 3D model installed.
     * <p>
     * Off by default, because most cards do not have one and a search that
     * silently hid nine tenths of the database would read as a broken search.
     */
    private boolean modelsOnly;
    private int scroll;

    private static final int GAP = 4;
    private static final int PAD = 6;
    private static final int HEADER = 34;
    private static final int FOOTER = 26;
    private static final int NAME_LINE = 10;
    private static final int CARD_W_MAX = 54;
    private static final int CARD_W_MIN = 28;
    private static final int MARGIN = 12;

    // Worked out in init and whenever the window changes, so nothing measures
    // twice and disagrees with itself.
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int cardW;
    private int cardH;
    private int columns;
    private int rows;

    public CardDisplayScreen(BlockPos pos, long code, byte art, int position)
    {
        super(Component.translatable("gui.dueldimension.card_display.title"));
        this.pos = pos;
        this.code = code;
        this.art = art;
        this.position = position;
    }

    /**
     * Fits the grid to the window.
     * <p>
     * The card shrinks until a useful number of rows fit, and stops at a size
     * below which the artwork stops being recognisable -- past that point a
     * bigger grid of unreadable cards is worse than a smaller one of legible
     * ones, and the list scrolls anyway.
     */
    private void measure()
    {
        panelW = Math.min(width - MARGIN * 2, 440);
        panelH = Math.min(height - MARGIN * 2, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int roomW = panelW - PAD * 2;
        int roomH = panelH - HEADER - FOOTER;

        cardW = CARD_W_MAX;
        while(true)
        {
            cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
            columns = Math.max(1, (roomW + GAP) / (cardW + GAP));
            rows = Math.max(1, (roomH + GAP) / (cardH + NAME_LINE + GAP));
            if(rows >= 2 || cardW - 4 < CARD_W_MIN)
            {
                break;
            }
            cardW -= 4;
        }
    }

    @Override
    protected void init()
    {
        measure();

        String kept = search == null ? "" : search.getValue();
        search = new EditBox(font, panelX + PAD, panelY + 18, panelW - PAD * 2, 14,
            Component.literal("Search"));
        search.setBordered(false);
        search.setY(search.getY() + (search.getHeight() - 8) / 2);
        search.setValue(kept);
        search.setResponder(text -> refresh());
        addRenderableWidget(search);
        setInitialFocus(search);

        int buttonY = panelY + panelH - FOOTER + 4;
        int buttonW = (panelW - PAD * 2 - GAP * 2) / 3;
        addRenderableWidget(Button.builder(label("Attack", OcgConstants.POS_FACEUP_ATTACK),
                pressed -> choosePosition(OcgConstants.POS_FACEUP_ATTACK))
            .bounds(panelX + PAD, buttonY, buttonW, 18).build());
        addRenderableWidget(Button.builder(label("Defence", OcgConstants.POS_FACEUP_DEFENSE),
                pressed -> choosePosition(OcgConstants.POS_FACEUP_DEFENSE))
            .bounds(panelX + PAD + buttonW + GAP, buttonY, buttonW, 18).build());
        addRenderableWidget(Button.builder(label("Set", OcgConstants.POS_FACEDOWN_DEFENSE),
                pressed -> choosePosition(OcgConstants.POS_FACEDOWN_DEFENSE))
            .bounds(panelX + PAD + (buttonW + GAP) * 2, buttonY, buttonW, 18).build());

        // Narrow the list to cards a model exists for. Beside the search rather
        // than in the footer, because it is part of the QUESTION being asked --
        // the footer's three buttons are what happens to the card once chosen.
        addRenderableWidget(Button.builder(
                Component.literal(modelsOnly ? "[x] Models" : "[ ] Models"), pressed ->
        {
            modelsOnly = !modelsOnly;
            refresh();
            // The label carries the state, so it is rebuilt with it.
            rebuildWidgets();
        }).bounds(panelX + panelW - PAD - 70 - GAP - 62, panelY + 3, 62, 14).build());

        // The billboard editor, for whatever card is on the block. Reached from
        // here because the card is chosen here: put a card down, then build its
        // monster while looking at it.
        addRenderableWidget(Button.builder(Component.literal("Billboard..."), pressed ->
                minecraft.gui.setScreen(new BillboardEditorScreen(this, code)))
            .bounds(panelX + panelW - PAD - 70, panelY + 3, 70, 14).build());

        refresh();
    }

    /** The chosen position wears a mark, so the three buttons say which is on. */
    private Component label(String name, int which)
    {
        return Component.literal(position == which ? "▸ " + name : name);
    }

    private void choosePosition(int chosen)
    {
        position = chosen;
        send();
        // The labels carry the state, so they are rebuilt with it.
        rebuildWidgets();
    }

    /**
     * The cards matching what has been typed.
     * <p>
     * Name only, case-insensitive, plus a whole passcode -- a builder placing a
     * specific card usually has its number, and card names are exactly the kind
     * of thing nobody spells right the first time.
     */
    private void refresh()
    {
        results.clear();
        scroll = 0;
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        // Read ONCE, not per card. MonsterModels.names() lists the models
        // folder off the disk every time it is called -- deliberately, so a file
        // dropped in a moment ago is seen -- and asking it fourteen thousand
        // times would be fourteen thousand directory listings per keystroke.
        java.util.Set<String> installed = modelsOnly
            ? new java.util.HashSet<>(
                de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.names())
            : java.util.Set.of();
        for(Properties card : DdDatabase.PROPERTIES_LIST.getList())
        {
            if(card == null || card.getName() == null)
            {
                continue;
            }
            if(modelsOnly && !hasModel(card, installed))
            {
                continue;
            }
            if(query.isEmpty() || card.getName().toLowerCase(Locale.ROOT).contains(query)
                || Long.toString(card.getId()).equals(query))
            {
                results.add(card);
            }
            if(results.size() >= 4096)
            {
                break;
            }
        }
    }

    /**
     * Whether this card has a 3D model that is actually on disk.
     *
     * <h2>Two conditions, and both are needed</h2>
     *
     * A definition NAMES a model; the models folder is what decides whether that
     * file is there. Filtering on the name alone would offer a duellist who has
     * never installed the models a list of cards whose blocks then show a flat
     * sprite -- a filter that promises something it cannot deliver.
     * <p>
     * {@code hasModel} and not {@code usesModel}: this asks what the card HAS,
     * not what the board is currently choosing to draw. Someone who has turned
     * 3D models off in the Misc tab is still entitled to find the cards that
     * have one.
     */
    private static boolean hasModel(Properties card, java.util.Set<String> installed)
    {
        de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites.Definition definition =
            de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites.of(card.getId());
        return definition != null && definition.hasModel()
            && installed.contains(definition.model());
    }

    private void send()
    {
        ClientPlayNetworking.send(new CardDisplayMessages.SetCard(pos, code, art, position));
    }

    private int maxScroll()
    {
        return Math.max(0, (results.size() + columns - 1) / columns - rows);
    }

    private int cellX(int cell)
    {
        return panelX + PAD + (cell % columns) * (cardW + GAP);
    }

    private int cellY(int cell)
    {
        return panelY + HEADER + (cell / columns) * (cardH + NAME_LINE + GAP);
    }

    /** Which result the cursor is over, or -1. */
    private int cellAt(double mouseX, double mouseY)
    {
        for(int cell = 0; cell < columns * rows; cell++)
        {
            int index = cell + scroll * columns;
            if(index >= results.size())
            {
                break;
            }
            int x = cellX(cell);
            int y = cellY(cell);
            if(mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH + NAME_LINE)
            {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled)
    {
        // The grid first. Its cells are drawn by this screen rather than being
        // widgets, so nothing else is going to claim them -- and asking super
        // first would hand the click to the search box, which covers none of
        // them but is focused.
        int chosen = cellAt(event.x(), event.y());
        if(chosen >= 0 && event.button() == 0)
        {
            code = results.get(chosen).getId();
            art = 0;
            send();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        scroll = Math.clamp(scroll - (int)Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        extractor.fillGradient(0, 0, width, height, 0xC0101014, 0xD0101014);
        NineSlice.draw(extractor, HubTextures.PANEL, panelX, panelY, panelW, panelH);

        // What is on the block right now, beside the title. A builder adjusting
        // a display wants to know what they are adjusting.
        Properties current = code == 0L ? null : DdDatabase.PROPERTIES_LIST.get(code);
        String heading = title.getString()
            + (current == null ? " - empty" : " - " + current.getName());
        extractor.text(font, font.plainSubstrByWidth(heading, panelW - PAD * 2),
            panelX + PAD, panelY + 5, MenuInk.title(), MenuInk.shadow());

        for(int cell = 0; cell < columns * rows; cell++)
        {
            int index = cell + scroll * columns;
            if(index >= results.size())
            {
                break;
            }
            Properties card = results.get(index);
            int x = cellX(cell);
            int y = cellY(cell);
            boolean over = mouseX >= x && mouseX < x + cardW
                && mouseY >= y && mouseY < y + cardH + NAME_LINE;
            boolean chosen = card.getId() == code;

            if(over || chosen)
            {
                NineSlice.draw(extractor, HubTextures.PANEL, x - 2, y - 2, cardW + 4, cardH + 4,
                    chosen ? NineSlice.SELECTED : NineSlice.HOVER, 3, 0.9F);
            }
            // The card's own window, not the whole file: a shipped card texture
            // is letterboxed inside a square canvas, and blitting all of it
            // draws the card at six tenths of the width it was given.
            DdBlitUtil.blit(extractor,
                DuelTextures.cardSmooth(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
                x, y, cardW, cardH, DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

            String name = font.plainSubstrByWidth(card.getName(), cardW);
            extractor.text(font, name, x + (cardW - font.width(name)) / 2, y + cardH + 1,
                chosen ? 0xFFFFE9B0 : MenuInk.body(), MenuInk.shadow());
        }

        if(results.isEmpty())
        {
            String none = "No cards match that";
            extractor.text(font, none, panelX + (panelW - font.width(none)) / 2,
                panelY + HEADER + 8, 0xFF9A9A9A, true);
        }
        else if(maxScroll() > 0)
        {
            String more = (scroll + 1) + " / " + (maxScroll() + 1);
            extractor.text(font, more, panelX + panelW - PAD - font.width(more),
                panelY + panelH - FOOTER - 9, MenuInk.dim(), MenuInk.shadow());
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    /** No blur: the block being edited is behind this, and worth seeing. */
    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
    /**
     * The hub screen this was opened from, or null. See {@link HubReturn}.
     * <p>
     * Read at construction, because that is the one moment the screen it is
     * replacing is still on show.
     */
    private final net.minecraft.client.gui.screens.Screen dueldimension$parent =
        HubReturn.parent();

    /** Back to the hub if that is where this came from, otherwise to the world. */
    @Override
    public void onClose()
    {
        if(!HubReturn.back(dueldimension$parent))
        {
            super.onClose();
        }
    }
}

