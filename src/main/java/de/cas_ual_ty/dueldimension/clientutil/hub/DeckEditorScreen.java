package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.duel.profile.CardQuery;
import de.cas_ual_ty.dueldimension.duel.profile.DeckLimits;
import de.cas_ual_ty.dueldimension.clientutil.layout.Layout;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The deck editor, laid out the way Tag Force does it: the deck you are
 * building down the left, the collection you are building it from down the
 * right.
 * <p>
 * Left is a workspace of three grids stacked in play order — Main, then Extra,
 * then Side — so the whole deck is visible at once rather than behind tabs.
 * Right is the trunk under a search field, filter chips and a sort control.
 * <p>
 * Cards move by the inventory gestures a Minecraft player already has: pick up
 * with a click and drop where it goes, or shift-click to send a card to the
 * place it obviously belongs. Every add is checked by {@link DeckLimits}, and a
 * refusal says which rule refused so the player knows whether to collect
 * another copy or free a slot.
 */
public class DeckEditorScreen extends Screen
{
    /** The tunables, read fresh every layout pass so a hot reload takes effect. */
    private static final String LAYOUT = "deck_editor";

    private final Screen parent;

    // Resolved once per init() from the layout file and the window size.
    private int cardW;
    private int cardH;
    private int gap;
    private int pad;
    private int mainColumns;
    private int extraColumns;
    private int trunkColumns;
    private int titleH;
    private int headerH;
    private int sectionGap;
    private int mainRows;

    private EditBox search;
    private int leftX;
    private int rightX;
    private int panelTop;
    private int leftW;
    private int rightW;
    private int panelH;

    /** The card currently held by the cursor, and where it came from. */
    private Properties carried;
    private DeckList.Part carriedFrom;
    private int carriedIndex = -1;

    /** Why the last attempted add was refused; cleared on the next action. */
    private String refusal = "";
    private int trunkScroll;

    public DeckEditorScreen(Screen parent)
    {
        super(Component.literal("Deck Editor"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        Layout layout = Layout.of(LAYOUT);
        pad = layout.i("panel.pad", 8);
        gap = layout.i("card.gap", 2);
        mainColumns = Math.max(1, layout.i("deck.mainColumns", 10));
        extraColumns = Math.max(1, layout.i("deck.extraColumns", 15));
        trunkColumns = Math.max(1, layout.i("trunk.columns", 8));
        titleH = layout.i("deck.titleHeight", 14);
        headerH = layout.i("deck.headerHeight", 12);
        sectionGap = layout.i("deck.sectionGap", 6);

        panelTop = layout.i("panel.top", 34);
        panelH = height - panelTop - layout.i("panel.bottom", 30);
        leftX = pad;
        leftW = (width - pad * 3) * Math.max(20, Math.min(80, layout.i("panel.leftPercent", 58))) / 100;
        rightX = leftX + leftW + layout.i("panel.gap", 8);
        rightW = width - rightX - pad;

        // Card size is DERIVED, not fixed. The width that makes the main deck
        // columns fill the panel decides it, and the height follows from the
        // card's own aspect -- so art is never stretched, whatever the window.
        int cellFromWidth = Math.max(6, (leftW - pad * 2) / mainColumns - gap);
        int cellFromTrunk = Math.max(6, (rightW - pad * 2) / trunkColumns - gap);
        float aspect = layout.f("card.aspect", 480F / 700F);
        cardW = Math.min(layout.i("card.width", 30), Math.min(cellFromWidth, cellFromTrunk));
        cardH = Math.max(8, Math.round(cardW / aspect));

        // Then the main deck takes whatever rows are left after Extra, Side
        // and their headings, so nothing can run off the bottom.
        int deckSpace = panelH - titleH - pad * 2
            - (headerH + sectionGap) * 3
            - (cardH + gap) * 2;
        mainRows = Math.max(1, deckSpace / (cardH + gap));
        // If even one row will not fit, shrink the card until it does.
        while(mainRows * (cardH + gap) + (cardH + gap) * 2 + titleH + (headerH + sectionGap) * 3
            + pad * 2 > panelH && cardH > 10)
        {
            cardW = Math.max(6, cardW - 2);
            cardH = Math.max(8, Math.round(cardW / aspect));
        }

        search = new EditBox(font, rightX + pad + 2, panelTop + pad + 2,
            Math.max(40, rightW - pad * 2 - layout.i("trunk.sortWidth", 56)
                - layout.i("trunk.dirWidth", 30) - 12), 14, Component.literal("Search"));
        search.setResponder(value ->
        {
            EditorState.query().setText(value);
            EditorState.invalidate();
            trunkScroll = 0;
        });
        search.setValue(EditorState.query().text());
        addWidget(search);

        rebuildControls();
    }

    private void rebuildControls()
    {
        clearWidgets();
        addWidget(search);

        Layout layout = Layout.of(LAYOUT);
        int rowH = layout.i("trunk.searchHeight", 16) + 4;
        int chipY = panelTop + pad + rowH;
        int chipX = rightX + pad;
        int chipW = layout.i("trunk.chipWidth", 42);
        int chipH = layout.i("trunk.chipHeight", 14);
        // Kind chips. A chip is on or off, never disabled, so its third atlas
        // row is the lit state rather than a greyed one.
        for(CardQuery.Kind kind : CardQuery.Kind.values())
        {
            CardQuery.Kind target = kind;
            addRenderableWidget(new ChipButton(chipX, chipY, chipW, chipH,
                Component.literal(label(kind)),
                () -> EditorState.query().kinds().contains(target), pressed ->
            {
                EditorState.query().toggleKind(target);
                EditorState.invalidate();
                trunkScroll = 0;
            }));
            chipX += chipW + 2;
        }

        // Sort cycles through the offered orders; the arrow flips direction.
        int sortW = layout.i("trunk.sortWidth", 56);
        int dirW = layout.i("trunk.dirWidth", 30);
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - sortW - dirW - 2,
            panelTop + pad, sortW, 16, Component.literal(EditorState.query().sort().label()), pressed ->
        {
            EditorState.query().setSort(EditorState.query().sort().next());
            EditorState.invalidate();
            rebuildControls();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - dirW, panelTop + pad,
            dirW, 16, Component.literal(EditorState.query().descending() ? "DESC" : "ASC"), pressed ->
        {
            EditorState.query().setDescending(!EditorState.query().descending());
            EditorState.invalidate();
            rebuildControls();
        }));

        // Clear Filters: greyed out when there is nothing to clear, so the
        // button itself reports whether anything is narrowing.
        HubWidgets.TextureButton clear = new HubWidgets.TextureButton(
            rightX + pad, chipY + chipH + 3, Math.min(92, rightW - pad * 2), 16,
            Component.literal("Clear Filters"), pressed ->
        {
            EditorState.query().clear();
            search.setValue("");
            EditorState.invalidate();
            trunkScroll = 0;
            rebuildControls();
        });
        clear.active = !EditorState.query().isClear();
        addRenderableWidget(clear);

        addRenderableWidget(new HubWidgets.TextureButton(width - pad - 80, height - 26, 80, 20,
            Component.literal("Done"), pressed -> onClose()));
    }

    private static String label(CardQuery.Kind kind)
    {
        return switch(kind)
        {
            case MONSTER -> "Monster";
            case SPELL -> "Spell";
            case TRAP -> "Trap";
        };
    }

    // ---- layout ----

    private int mainTop()
    {
        // Below the deck NAME as well as the section heading; these two used to
        // be drawn at the same y and overlapped.
        return panelTop + pad + titleH + headerH + sectionGap;
    }

    private int mainRows()
    {
        return mainRows;
    }

    private int extraTop()
    {
        return mainTop() + mainRows * (cardH + gap) + headerH + sectionGap;
    }

    private int sideTop()
    {
        return extraTop() + (cardH + gap) + headerH + sectionGap;
    }

    private int partTop(DeckList.Part part)
    {
        return switch(part)
        {
            case MAIN -> mainTop();
            case EXTRA -> extraTop();
            case SIDE -> sideTop();
        };
    }

    private int partColumns(DeckList.Part part)
    {
        return part == DeckList.Part.MAIN ? mainColumns : extraColumns;
    }

    /** Which slot of which part a point falls in, or null. */
    private DeckList.Part partAt(double mouseX, double mouseY)
    {
        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            int rows = part == DeckList.Part.MAIN ? mainRows : 1;
            if(mouseX >= leftX + pad && mouseX < leftX + leftW - pad
                && mouseY >= top && mouseY < top + rows * (cardH + gap))
            {
                return part;
            }
        }
        return null;
    }

    private int slotIndexAt(DeckList.Part part, double mouseX, double mouseY)
    {
        int columns = partColumns(part);
        int cellW = Math.max(8, (leftW - pad * 2) / columns);
        int column = (int)((mouseX - (leftX + pad)) / cellW);
        int row = (int)((mouseY - partTop(part)) / (cardH + gap));
        if(column < 0 || column >= columns || row < 0)
        {
            return -1;
        }
        return row * columns + column;
    }

    /** Below the search row, the chip row and the Clear row. */
    private int trunkGridTop()
    {
        Layout layout = Layout.of(LAYOUT);
        int rowH = layout.i("trunk.searchHeight", 16) + 4;
        return panelTop + pad + rowH + layout.i("trunk.chipHeight", 14) + 3 + 16 + 6;
    }

    private int trunkIndexAt(double mouseX, double mouseY)
    {
        int gridTop = trunkGridTop();
        int cellW = Math.max(8, (rightW - pad * 2) / trunkColumns);
        if(mouseX < rightX + pad || mouseX >= rightX + rightW - pad || mouseY < gridTop)
        {
            return -1;
        }
        int column = (int)((mouseX - (rightX + pad)) / cellW);
        int row = (int)((mouseY - gridTop) / (cardH + gap));
        if(column < 0 || column >= trunkColumns || row < 0)
        {
            return -1;
        }
        return (row + trunkScroll) * trunkColumns + column;
    }

    // ---- interaction ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        refusal = "";
        boolean shift = hasShiftDown();

        DeckList.Part part = partAt(mouseX, mouseY);
        if(part != null)
        {
            int index = slotIndexAt(part, mouseX, mouseY);
            List<Integer> cards = EditorState.deck().partFor(part);
            if(carried != null)
            {
                place(part);
                return true;
            }
            if(index >= 0 && index < cards.size())
            {
                if(shift)
                {
                    // Shift-click in the deck sends a card back to the trunk,
                    // which is simply removing it: the trunk never lost it.
                    cards.remove(index);
                    return true;
                }
                carried = card(cards.remove(index));
                carriedFrom = part;
                carriedIndex = index;
                return true;
            }
            return true;
        }

        int trunkIndex = trunkIndexAt(mouseX, mouseY);
        if(trunkIndex >= 0)
        {
            List<Properties> shown = EditorState.visible();
            if(trunkIndex < shown.size())
            {
                Properties picked = shown.get(trunkIndex);
                if(shift)
                {
                    // The obvious destination: extra-deck monsters go to the
                    // Extra grid, everything else to Main.
                    add(picked, picked.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN);
                }
                else
                {
                    carried = picked;
                    carriedFrom = null;
                    carriedIndex = -1;
                }
                return true;
            }
        }

        if(carried != null)
        {
            // Dropped on nothing: put it back where it came from rather than
            // losing it.
            returnCarried();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void place(DeckList.Part part)
    {
        Properties held = carried;
        if(held == null)
        {
            return;
        }
        carried = null;
        if(carriedFrom == part)
        {
            // Moved within the same grid: it was already removed, so this is
            // just putting it back.
            EditorState.deck().partFor(part).add((int)held.getId());
            carriedFrom = null;
            return;
        }
        if(!add(held, part))
        {
            returnCarriedTo(held);
        }
        carriedFrom = null;
    }

    private void returnCarried()
    {
        Properties held = carried;
        carried = null;
        returnCarriedTo(held);
    }

    private void returnCarriedTo(Properties held)
    {
        if(held != null && carriedFrom != null)
        {
            List<Integer> cards = EditorState.deck().partFor(carriedFrom);
            int at = Math.min(Math.max(0, carriedIndex), cards.size());
            cards.add(at, (int)held.getId());
        }
        carriedFrom = null;
        carriedIndex = -1;
    }

    /** Adds a card if every rule allows it, else records why not. */
    private boolean add(Properties card, DeckList.Part part)
    {
        DeckLimits.Verdict verdict = DeckLimits.canAdd(EditorState.deck(), part,
            (int)card.getId(), EditorState.trunk(), EditorState.banlist());
        if(!verdict.allowed())
        {
            refusal = verdict.reason();
            return false;
        }
        EditorState.deck().partFor(part).add((int)card.getId());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if(mouseX >= rightX)
        {
            int rows = (EditorState.visible().size() + trunkColumns - 1) / trunkColumns;
            int visibleRows = Math.max(1, (panelH - 48) / (cardH + gap));
            trunkScroll = Math.max(0, Math.min(Math.max(0, rows - visibleRows),
                trunkScroll - (int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if(search != null && search.isFocused() && search.keyPressed(key, scan, modifiers))
        {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers)
    {
        if(search != null && search.isFocused() && search.charTyped(typed, modifiers))
        {
            return true;
        }
        return super.charTyped(typed, modifiers);
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        NineSlice.draw(poseStack, HubTextures.PANEL, leftX, panelTop, leftW, panelH);
        NineSlice.draw(poseStack, HubTextures.PANEL, rightX, panelTop, rightW, panelH);

        renderDeckSide(poseStack, mouseX, mouseY);
        renderTrunkSide(poseStack, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);
        search.render(poseStack, mouseX, mouseY, partialTick);

        if(!refusal.isEmpty())
        {
            int textWidth = font.width(refusal);
            NineSlice.draw(poseStack, HubTextures.PANEL, width / 2 - textWidth / 2 - 8,
                height - 52, textWidth + 16, 18);
            font.drawShadow(poseStack, refusal, width / 2F - textWidth / 2F, height - 47, 0xFFFF8A80);
        }

        // The carried card rides the cursor, as an inventory stack does.
        if(carried != null)
        {
            drawCard(poseStack, carried, mouseX - cardW / 2, mouseY - cardH / 2, 1F);
        }
    }

    private void renderDeckSide(PoseStack poseStack, int mouseX, int mouseY)
    {
        DeckList deck = EditorState.deck();
        font.drawShadow(poseStack, deck.name(), leftX + pad, panelTop + pad, 0xFFF4D089);

        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            List<Integer> cards = deck.partFor(part);
            String heading = switch(part)
            {
                case MAIN -> "Main Deck";
                case EXTRA -> "Extra Deck";
                case SIDE -> "Side Deck";
            };
            // The count doubles as the legality hint: a main deck under 40 is
            // not playable, so the number is the thing to watch.
            boolean ok = part != DeckList.Part.MAIN
                || (cards.size() >= 40 && cards.size() <= 60);
            font.drawShadow(poseStack, heading + "  " + cards.size() + " / " + part.capacity(),
                leftX + pad, top - headerH + 3, ok ? 0xFFC2C9D6 : 0xFFFF8A80);

            int columns = partColumns(part);
            int cellW = Math.max(8, (leftW - pad * 2) / columns);
            int rows = part == DeckList.Part.MAIN ? mainRows : 1;
            for(int row = 0; row < rows; row++)
            {
                for(int column = 0; column < columns; column++)
                {
                    int index = row * columns + column;
                    int x = leftX + pad + column * cellW;
                    int y = top + row * (cardH + gap);
                    NineSlice.draw(poseStack, HubTextures.SLOT, x, y, cardW, cardH);
                    if(index < cards.size())
                    {
                        Properties card = card(cards.get(index));
                        if(card != null)
                        {
                            drawCard(poseStack, card, x, y, 1F);
                        }
                    }
                }
            }
        }
    }

    private void renderTrunkSide(PoseStack poseStack, int mouseX, int mouseY)
    {
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, rightX + pad, panelTop + pad,
            search.getWidth() + 6, Layout.of(LAYOUT).i("trunk.searchHeight", 16) + 2);

        List<Properties> shown = EditorState.visible();
        int gridTop = trunkGridTop();
        int cellW = Math.max(8, (rightW - pad * 2) / trunkColumns);
        int visibleRows = Math.max(1, (panelTop + panelH - gridTop - pad) / (cardH + gap));

        for(int row = 0; row < visibleRows; row++)
        {
            for(int column = 0; column < trunkColumns; column++)
            {
                int index = (row + trunkScroll) * trunkColumns + column;
                int x = rightX + pad + column * cellW;
                int y = gridTop + row * (cardH + gap);
                NineSlice.draw(poseStack, HubTextures.SLOT, x, y, cardW, cardH);
                if(index >= shown.size())
                {
                    continue;
                }
                Properties card = shown.get(index);
                int inDeck = EditorState.deck().copiesOf((int)card.getId());
                int max = DeckLimits.maxCopies((int)card.getId(), EditorState.trunk(),
                    EditorState.banlist());
                // A card already at its limit is dimmed, so the trunk shows
                // what is still available at a glance rather than on refusal.
                drawCard(poseStack, card, x, y, inDeck >= max ? 0.35F : 1F);
                if(inDeck > 0)
                {
                    String count = inDeck + "/" + max;
                    font.drawShadow(poseStack, count, x + cardW - font.width(count),
                        y + cardH - 8, inDeck >= max ? 0xFFFF8A80 : 0xFFF4D089);
                }
            }
        }

        font.drawShadow(poseStack, shown.size() + " cards", rightX + pad,
            panelTop + panelH - 12, 0xFF7A8090);
    }

    private static Properties card(int code)
    {
        return de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
    }

    private void drawCard(PoseStack poseStack, Properties card, int x, int y, float alpha)
    {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        CardRenderUtil.bindMainResourceLocation(card, (byte)0);
        DdBlitUtil.fullBlit(poseStack, x, y, cardW, cardH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** A filter chip: lit when its filter is on. */
    private static class ChipButton extends HubWidgets.TextureButton
    {
        private final java.util.function.BooleanSupplier lit;

        ChipButton(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier lit, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.lit = lit;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            int row = lit.getAsBoolean() ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.CHIP, x, y, width, height, row, 3);
            drawLabel(poseStack, lit.getAsBoolean() ? 0xFFFFE9B0 : 0xFFC2C9D6);
        }
    }
}
