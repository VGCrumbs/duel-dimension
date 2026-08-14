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
 * lies. Deliberately its own screen rather than the deck editor with something
 * switched off: the editor is about building a legal deck -- counts, limits,
 * the extra deck's own rules -- and none of that has anything to say about a
 * card on a pedestal. Sharing it would mean teaching it a mode in which every
 * one of its rules is suspended.
 * <p>
 * Every choice is sent as it is made rather than gathered up behind an OK. A
 * display block is something a builder adjusts while looking at it, and the
 * useful thing is seeing the change land, not confirming it.
 */
public class CardDisplayScreen extends Screen
{
    private final BlockPos pos;
    private long code;
    private byte art;
    private int position;

    private EditBox search;
    private final List<Properties> results = new ArrayList<>();
    private int scroll;

    private static final int COLUMNS = 8;
    private static final int ROWS = 3;
    private static final int CARD_W = 52;
    private static final int GAP = 5;
    private static final int PANEL_W = COLUMNS * CARD_W + (COLUMNS + 1) * GAP;

    public CardDisplayScreen(BlockPos pos, long code, byte art, int position)
    {
        super(Component.translatable("gui.dueldimension.card_display.title"));
        this.pos = pos;
        this.code = code;
        this.art = art;
        this.position = position;
    }

    private static int cardH()
    {
        return Math.round(CARD_W / DuelTextures.CARD_ASPECT);
    }

    private int panelH()
    {
        return 58 + ROWS * (cardH() + 12) + GAP;
    }

    private int left()
    {
        return (width - PANEL_W) / 2;
    }

    private int top()
    {
        return (height - panelH()) / 2;
    }

    @Override
    protected void init()
    {
        search = new EditBox(font, left() + GAP, top() + 20, PANEL_W - GAP * 2, 16,
            Component.literal("Search"));
        search.setResponder(text -> refresh());
        addRenderableWidget(search);
        setInitialFocus(search);

        int buttonY = top() + panelH() - 24;
        int buttonW = (PANEL_W - GAP * 4) / 3;
        addRenderableWidget(Button.builder(Component.literal("Attack"),
                pressed -> choosePosition(OcgConstants.POS_FACEUP_ATTACK))
            .bounds(left() + GAP, buttonY, buttonW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Defence"),
                pressed -> choosePosition(OcgConstants.POS_FACEUP_DEFENSE))
            .bounds(left() + GAP * 2 + buttonW, buttonY, buttonW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Set"),
                pressed -> choosePosition(OcgConstants.POS_FACEDOWN_DEFENSE))
            .bounds(left() + GAP * 3 + buttonW * 2, buttonY, buttonW, 18).build());

        refresh();
    }

    /**
     * The cards matching what has been typed.
     * <p>
     * Name only, and case-insensitive. A passcode typed in full matches too,
     * because a builder placing a specific card usually has its number rather
     * than its spelling -- and card names are exactly the kind of thing nobody
     * spells right the first time.
     */
    private void refresh()
    {
        results.clear();
        scroll = 0;
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for(Properties card : DdDatabase.PROPERTIES_LIST.getList())
        {
            if(card == null || card.getName() == null)
            {
                continue;
            }
            if(query.isEmpty() || card.getName().toLowerCase(Locale.ROOT).contains(query)
                || Long.toString(card.getId()).equals(query))
            {
                results.add(card);
            }
            if(results.size() >= 512)
            {
                // Enough to choose from and few enough to page through. A
                // builder who has not narrowed it down yet is still typing.
                break;
            }
        }
    }

    private void choosePosition(int chosen)
    {
        position = chosen;
        send();
    }

    private void send()
    {
        ClientPlayNetworking.send(new CardDisplayMessages.SetCard(pos, code, art, position));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled)
    {
        int cell = cellAt(event.x(), event.y());
        if(cell >= 0)
        {
            code = results.get(cell).getId();
            art = 0;
            send();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        int rows = (results.size() + COLUMNS - 1) / COLUMNS;
        int maxScroll = Math.max(0, rows - ROWS);
        scroll = Math.clamp(scroll - (int)Math.signum(scrollY), 0, maxScroll);
        return true;
    }

    private int cellAt(double mouseX, double mouseY)
    {
        for(int cell = 0; cell < COLUMNS * ROWS; cell++)
        {
            int index = cell + scroll * COLUMNS;
            if(index >= results.size())
            {
                break;
            }
            int x = cellX(cell);
            int y = cellY(cell);
            if(mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + cardH())
            {
                return index;
            }
        }
        return -1;
    }

    private int cellX(int cell)
    {
        return left() + GAP + (cell % COLUMNS) * (CARD_W + GAP);
    }

    private int cellY(int cell)
    {
        return top() + 42 + (cell / COLUMNS) * (cardH() + 12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        extractor.fillGradient(0, 0, width, height, 0xC0101014, 0xD0101014);
        NineSlice.draw(extractor, HubTextures.PANEL, left(), top(), PANEL_W, panelH());
        extractor.text(font, title.getString(), left() + GAP, top() + 6, 0xFFF4D089, true);

        for(int cell = 0; cell < COLUMNS * ROWS; cell++)
        {
            int index = cell + scroll * COLUMNS;
            if(index >= results.size())
            {
                break;
            }
            Properties card = results.get(index);
            int x = cellX(cell);
            int y = cellY(cell);
            boolean over = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + cardH();
            boolean chosen = card.getId() == code;

            if(over || chosen)
            {
                NineSlice.draw(extractor, HubTextures.PANEL, x - 3, y - 3, CARD_W + 6,
                    cardH() + 6, chosen ? NineSlice.SELECTED : NineSlice.HOVER, 3, 0.9F);
            }
            // The card's own window, not the whole file: a shipped card texture
            // is letterboxed inside a square canvas.
            DdBlitUtil.blit(extractor,
                DuelTextures.cardSmooth(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
                x, y, CARD_W, cardH(), DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

            String name = font.plainSubstrByWidth(card.getName(), CARD_W);
            extractor.text(font, name, x + (CARD_W - font.width(name)) / 2, y + cardH() + 2,
                chosen ? 0xFFFFE9B0 : 0xFFC2C9D6, true);
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
}
