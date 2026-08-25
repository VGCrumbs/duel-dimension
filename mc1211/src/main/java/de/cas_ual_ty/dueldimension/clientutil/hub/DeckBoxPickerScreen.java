package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.net.ProfilePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Visual choice of owned deck cases; shop cases remain visible but locked. */
public class DeckBoxPickerScreen extends Screen
{
    private static final int PAD = 10;
    private static final int TILE_W = 66;
    private static final int TILE_H = 60;
    private static final int GAP = 6;
    private static final int TILE_Y = 24;
    private static final int COLS = 3;
    private static final int ROWS = (DeckBoxStyle.values().length + COLS - 1) / COLS;
    private static final int PANEL_W = PAD * 2 + COLS * TILE_W + (COLS - 1) * GAP;
    private static final int PANEL_H = TILE_Y + ROWS * TILE_H + (ROWS - 1) * GAP + PAD;

    private final Screen parent;
    private int left;
    private int top;

    public DeckBoxPickerScreen(Screen parent)
    {
        super(Component.literal("Deck Box"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        addRenderableWidget(new HubWidgets.TextureButton(left + PANEL_W - PAD - 38, top + 5,
            38, 14, Component.literal("Back"), pressed -> onClose()));
    }

    public static void drawBox(GuiGraphicsExtractor graphics, DeckBoxStyle style,
        int x, int y, int width, int height)
    {
        DdBlitUtil.fullBlit(graphics, HubTextures.deckBox(style), x, y, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        NineSlice.draw(graphics, HubTextures.PANEL, left, top, PANEL_W, PANEL_H);
        graphics.text(font, "Deck Box", left + PAD, top + 8, 0xFFF4D089, true);

        DeckBoxStyle worn = EditorState.deck().deckBox();
        for(int index = 0; index < DeckBoxStyle.values().length; index++)
        {
            DeckBoxStyle style = DeckBoxStyle.values()[index];
            int x = left + PAD + (index % COLS) * (TILE_W + GAP);
            int y = top + TILE_Y + (index / COLS) * (TILE_H + GAP);
            boolean over = mouseX >= x && mouseX < x + TILE_W
                && mouseY >= y && mouseY < y + TILE_H;
            int row = style == worn ? NineSlice.SELECTED
                : over ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.CHIP, x, y, TILE_W, TILE_H, row, 3);

            int boxH = 39;
            int boxW = Math.round(boxH * 0.75F);
            drawBox(graphics, style, x + (TILE_W - boxW) / 2, y + 3, boxW, boxH);
            boolean owned = EditorState.profile().ownsDeckBox(style);
            if(!owned)
            {
                graphics.fill(x + 2, y + 2, x + TILE_W - 2, y + TILE_H - 2, 0x76000000);
            }
            String label = shortLabel(style);
            graphics.text(font, label, x + (TILE_W - font.width(label)) / 2,
                y + TILE_H - 12, !owned ? 0xFFFF6B6B
                    : style == worn ? 0xFFF4D089 : 0xFFE8E8E8, true);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static String shortLabel(DeckBoxStyle style)
    {
        return switch(style)
        {
            case VORTEX_OF_MAGIC -> "Vortex";
            case BLUE_EYES_MAX -> "Blue-Eyes";
            case DARK_MAGICAL_BLAST -> "Dark Blast";
            case RAGE_OF_DEEP_BLUE -> "Deep Blue";
            case CYBER_KAISER -> "Cyber Kaiser";
            case THE_MILLENNIUM_PUZZLE -> "Millennium";
            default -> style.label();
        };
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        if(event.button() == 0)
        {
            DeckBoxStyle style = styleAt(event.x(), event.y());
            if(style != null && EditorState.profile().ownsDeckBox(style))
            {
                DeckList deck = EditorState.deck();
                deck.setDeckBox(style);
                ClientPlayNetworking.send(new ProfilePayloads.SetDeckBox(deck.name(), style.name()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private DeckBoxStyle styleAt(double mouseX, double mouseY)
    {
        for(int index = 0; index < DeckBoxStyle.values().length; index++)
        {
            int x = left + PAD + (index % COLS) * (TILE_W + GAP);
            int y = top + TILE_Y + (index / COLS) * (TILE_H + GAP);
            if(mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H)
            {
                return DeckBoxStyle.values()[index];
            }
        }
        return null;
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
