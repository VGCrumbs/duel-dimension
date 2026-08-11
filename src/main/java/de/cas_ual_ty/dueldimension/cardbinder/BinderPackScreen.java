package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.DuelTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.EditorState;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One pack, card by card: what has been pulled and what is still missing.
 * <p>
 * Every printing in the set gets a tile. Ones held are drawn in full colour;
 * ones still missing are drawn desaturated through the resource pack's existing
 * "unowned" path — the same treatment the deck editor uses for a card the player
 * does not have, so the two screens agree about what "you do not own this" looks
 * like without a second piece of art.
 * <p>
 * A card appears once per RARITY it was printed at, because that is what the
 * collection counts. Two tiles of the same art with different rarity labels are
 * two different things to find, and the labels say which.
 */
public class BinderPackScreen extends Screen
{
    /**
     * How much screen is left around the panel.
     * <p>
     * Small, because this is a page of a collection and the collection is the
     * only thing the player came here to look at. It fills the height and takes
     * whatever width it can use for whole columns.
     */
    private static final int MARGIN = 6;
    /** Never so wide that a row of cards becomes a line to read along. */
    private static final int MAX_WIDTH = 520;
    private static final int PAD = 10;
    /**
     * A card tile, 15% off the old 38.
     * <p>
     * Small enough that the extra height buys rows rather than white space, and
     * still large enough to recognise art by -- which is the whole job of this
     * page, since the name only appears on hover.
     */
    private static final int CARD_W = 32;
    private static final int GAP = 4;
    /** Room under each card for its rarity, which is the whole point of the tile. */
    private static final int LABEL_H = 9;

    /** One printing: the card, the rarity it was printed at, and whether it is held. */
    private record Tile(CardHolder card, String rarity, boolean held)
    {
    }

    private final Screen parent;
    private final CollectionProgress.Pack pack;
    private final List<Tile> tiles = new ArrayList<>();

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int columns;
    private int visibleRows;
    private int cardH;
    private int scroll;
    private int maxScroll;

    public BinderPackScreen(Screen parent, CollectionProgress.Pack pack)
    {
        super(Component.literal(pack.set().name));
        this.parent = parent;
        this.pack = pack;
    }

    @Override
    protected void init()
    {
        // Fill the height; take a width that holds whole columns and no more,
        // so the grid is never trailed by a strip of empty panel.
        panelH = Math.max(120, height - MARGIN * 2);
        int usable = Math.min(MAX_WIDTH, width - MARGIN * 2);
        cardH = Math.round(CARD_W / DuelTextures.CARD_ASPECT);
        columns = Math.max(1, (usable - PAD * 2 + GAP) / (CARD_W + GAP));
        panelW = columns * (CARD_W + GAP) - GAP + PAD * 2;
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        tiles.clear();
        // Through the progress model, not a second implementation of it: the
        // grouping, the de-duplication and the rule for what counts as held all
        // live there, so the tiles and the percentage above them cannot drift
        // apart. That matters most for cards whose rarity was never recorded,
        // which are credited against a printing rather than counting as missing.
        var trunk = EditorState.isSynced() ? EditorState.trunk() : null;
        var byCard = CollectionProgress.printingsByCard(pack.set());
        Set<String> placed = new HashSet<>();
        for(CardHolder held : pack.set().cards)
        {
            if(held == null || held.getCard() == null)
            {
                continue;
            }
            int passcode = (int)held.getCard().getId();
            String rarity = held.rarity == null ? "" : held.rarity;
            if(!placed.add(passcode + "|" + rarity))
            {
                continue;
            }
            List<String> rarities = byCard.getOrDefault(passcode, List.of());
            tiles.add(new Tile(held, rarity,
                CollectionProgress.heldPrintings(trunk, passcode, rarities).contains(rarity)));
        }
        // Held first, so a player sees what they have before what they lack.
        tiles.sort((left, right) -> Boolean.compare(right.held(), left.held()));

        visibleRows = Math.max(1, (panelH - PAD * 2 - 46) / (cardH + LABEL_H + GAP));
        int rows = (tiles.size() + columns - 1) / columns;
        maxScroll = Math.max(0, rows - visibleRows);
        scroll = Math.clamp(scroll, 0, maxScroll);

        addRenderableWidget(Button.builder(Component.literal("Back"), pressed -> onClose())
            .bounds(left + PAD, top + panelH - PAD - 20, 60, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
            pressed -> minecraft.setScreenAndShow(null))
            .bounds(left + panelW - PAD - 60, top + panelH - PAD - 20, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY,
        float partialTick)
    {
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, panelW, panelH);

        int y = top + PAD;
        String name = font.plainSubstrByWidth(pack.set().name, panelW - PAD * 2 - 90);
        poseStack.text(font, name, left + PAD, y, 0xFFF4D089, true);

        String progress = pack.held() + " / " + pack.total() + "   "
            + (int)Math.floor(pack.fraction() * 100F) + "%";
        poseStack.text(font, progress, left + panelW - PAD - font.width(progress), y,
            pack.complete() ? 0xFF8AD98A : 0xFFC2C9D6, true);
        y += 12;

        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, left + PAD, y, panelW - PAD * 2, 7, 0, 2);
        int filled = Math.round((panelW - PAD * 2) * pack.fraction());
        if(filled > 0)
        {
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, left + PAD, y,
                Math.max(4, filled), 7, 1, 2);
        }
        y += 13;

        int gridTop = y;
        Tile hovered = null;
        for(int cell = 0; cell < columns * visibleRows; cell++)
        {
            int index = cell + scroll * columns;
            if(index >= tiles.size())
            {
                break;
            }
            Tile tile = tiles.get(index);
            int x = left + PAD + (cell % columns) * (CARD_W + GAP);
            int cardY = gridTop + (cell / columns) * (cardH + LABEL_H + GAP);

            boolean over = mouseX >= x && mouseX < x + CARD_W
                && mouseY >= cardY && mouseY < cardY + cardH;
            if(over)
            {
                hovered = tile;
                NineSlice.draw(poseStack, HubTextures.PANEL, x - 2, cardY - 2,
                    CARD_W + 4, cardH + 4, NineSlice.HOVER, 3, 0.9F);
            }

            // The unowned path desaturates in the resource pack, so a missing
            // card still shows WHICH card it is -- that is the point of a
            // collection page -- while reading instantly as not yet found.
            DdBlitUtil.blit(poseStack, tile.held()
                    ? DuelTextures.card(tile.card().getCard(), tile.card().imageIndex,
                        DuelTextures.ICON_CARD_SIZE)
                    : DuelTextures.cardUnowned(tile.card().getCard(), tile.card().imageIndex,
                        DuelTextures.ICON_CARD_SIZE),
                x, cardY, CARD_W, cardH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

            String rarity = font.plainSubstrByWidth(shortRarity(tile.rarity()), CARD_W);
            poseStack.text(font, rarity, x + (CARD_W - font.width(rarity)) / 2,
                cardY + cardH + 1, tile.held() ? 0xFFE6EAF2 : 0xFF6A7080, true);
        }

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);

        if(hovered != null)
        {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.card().getCard().getName()));
            lines.add(Component.literal(hovered.rarity().isEmpty() ? "No rarity recorded"
                : hovered.rarity()));
            lines.add(Component.literal(hovered.held() ? "In your collection" : "Not yet pulled"));
            // setComponentTooltipForNextFrame, not setTooltipForNextFrame: the
            // latter's List overloads take FormattedCharSequence, and these are
            // Components.
            poseStack.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /**
     * Rarity names are long and a tile is 38 pixels wide.
     * <p>
     * Initials of each word, which is how players say them anyway -- "UR" for
     * Ultra Rare, "ScR" for Secret Rare -- with the full name in the tooltip.
     */
    private static String shortRarity(String rarity)
    {
        if(rarity == null || rarity.isBlank())
        {
            return "-";
        }
        StringBuilder shortened = new StringBuilder();
        for(String word : rarity.split("\\s+"))
        {
            if(!word.isEmpty())
            {
                shortened.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return shortened.toString();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        if(event.button() == 0)
        {
            Tile tile = tileAt(event.x(), event.y());
            if(tile != null)
            {
                minecraft.setScreenAndShow(
                    new CardPreviewScreen(this, tile.card(), tile.rarity(), tile.held()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Which tile is under the cursor, using the grid the renderer lays out. */
    private Tile tileAt(double mouseX, double mouseY)
    {
        int gridTop = top + PAD + 12 + 13;
        for(int cell = 0; cell < columns * visibleRows; cell++)
        {
            int index = cell + scroll * columns;
            if(index >= tiles.size())
            {
                break;
            }
            int x = left + PAD + (cell % columns) * (CARD_W + GAP);
            int cardY = gridTop + (cell / columns) * (cardH + LABEL_H + GAP);
            if(mouseX >= x && mouseX < x + CARD_W && mouseY >= cardY && mouseY < cardY + cardH)
            {
                return tiles.get(index);
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if(maxScroll > 0)
        {
            scroll = Math.clamp(scroll - (int)Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public void onClose()
    {
        minecraft.setScreenAndShow(parent);
    }
}
