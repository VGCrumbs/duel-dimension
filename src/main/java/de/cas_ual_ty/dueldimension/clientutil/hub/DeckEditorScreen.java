package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.duel.profile.CardQuery;
import de.cas_ual_ty.dueldimension.duel.profile.DeckLimits;
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
    /** Card slots, at the printed card's ratio. */
    private static final int CARD_W = 30;
    private static final int CARD_H = 44;
    private static final int GAP = 2;
    private static final int MAIN_COLUMNS = 10;
    private static final int TRUNK_COLUMNS = 8;
    private static final int PAD = 8;
    private static final int HEADER_H = 14;

    private final Screen parent;

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
        panelTop = 40;
        panelH = height - panelTop - 34;
        leftX = PAD;
        leftW = (width - PAD * 3) * 58 / 100;
        rightX = leftX + leftW + PAD;
        rightW = width - rightX - PAD;

        search = new EditBox(font, rightX + PAD + 2, panelTop + PAD + 2,
            rightW - PAD * 2 - 74, 14, Component.literal("Search"));
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

        int chipY = panelTop + PAD + 20;
        int chipX = rightX + PAD;
        // Kind chips. A chip is on or off, never disabled, so its third atlas
        // row is the lit state rather than a greyed one.
        for(CardQuery.Kind kind : CardQuery.Kind.values())
        {
            CardQuery.Kind target = kind;
            addRenderableWidget(new ChipButton(chipX, chipY, 44, 14,
                Component.literal(label(kind)),
                () -> EditorState.query().kinds().contains(target), pressed ->
            {
                EditorState.query().toggleKind(target);
                EditorState.invalidate();
                trunkScroll = 0;
            }));
            chipX += 46;
        }

        // Sort cycles through the offered orders; the arrow flips direction.
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - PAD - 92, panelTop + PAD,
            64, 16, Component.literal(EditorState.query().sort().label()), pressed ->
        {
            EditorState.query().setSort(EditorState.query().sort().next());
            EditorState.invalidate();
            rebuildControls();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - PAD - 26, panelTop + PAD,
            26, 16, Component.literal(EditorState.query().descending() ? "DESC" : "ASC"), pressed ->
        {
            EditorState.query().setDescending(!EditorState.query().descending());
            EditorState.invalidate();
            rebuildControls();
        }));

        // Clear Filters: greyed out when there is nothing to clear, so the
        // button itself reports whether anything is narrowing.
        HubWidgets.TextureButton clear = new HubWidgets.TextureButton(
            rightX + rightW - PAD - 92, chipY - 2, 92, 16,
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

        addRenderableWidget(new HubWidgets.TextureButton(width - PAD - 80, height - 26, 80, 20,
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
        return panelTop + PAD + HEADER_H;
    }

    private int mainRows()
    {
        return 6;
    }

    private int extraTop()
    {
        return mainTop() + mainRows() * (CARD_H + GAP) + HEADER_H + 4;
    }

    private int sideTop()
    {
        return extraTop() + (CARD_H + GAP) + HEADER_H + 4;
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
        return part == DeckList.Part.MAIN ? MAIN_COLUMNS : 15;
    }

    /** Which slot of which part a point falls in, or null. */
    private DeckList.Part partAt(double mouseX, double mouseY)
    {
        for(DeckList.Part part : DeckList.Part.values())
        {
            int top = partTop(part);
            int rows = part == DeckList.Part.MAIN ? mainRows() : 1;
            if(mouseX >= leftX + PAD && mouseX < leftX + leftW - PAD
                && mouseY >= top && mouseY < top + rows * (CARD_H + GAP))
            {
                return part;
            }
        }
        return null;
    }

    private int slotIndexAt(DeckList.Part part, double mouseX, double mouseY)
    {
        int columns = partColumns(part);
        int cellW = Math.max(8, (leftW - PAD * 2) / columns);
        int column = (int)((mouseX - (leftX + PAD)) / cellW);
        int row = (int)((mouseY - partTop(part)) / (CARD_H + GAP));
        if(column < 0 || column >= columns || row < 0)
        {
            return -1;
        }
        return row * columns + column;
    }

    private int trunkIndexAt(double mouseX, double mouseY)
    {
        int gridTop = panelTop + PAD + 40;
        int cellW = Math.max(8, (rightW - PAD * 2) / TRUNK_COLUMNS);
        if(mouseX < rightX + PAD || mouseX >= rightX + rightW - PAD || mouseY < gridTop)
        {
            return -1;
        }
        int column = (int)((mouseX - (rightX + PAD)) / cellW);
        int row = (int)((mouseY - gridTop) / (CARD_H + GAP));
        if(column < 0 || column >= TRUNK_COLUMNS || row < 0)
        {
            return -1;
        }
        return (row + trunkScroll) * TRUNK_COLUMNS + column;
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
            int rows = (EditorState.visible().size() + TRUNK_COLUMNS - 1) / TRUNK_COLUMNS;
            int visibleRows = Math.max(1, (panelH - 48) / (CARD_H + GAP));
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
            drawCard(poseStack, carried, mouseX - CARD_W / 2, mouseY - CARD_H / 2, 1F);
        }
    }

    private void renderDeckSide(PoseStack poseStack, int mouseX, int mouseY)
    {
        DeckList deck = EditorState.deck();
        font.drawShadow(poseStack, deck.name(), leftX + PAD, panelTop + PAD, 0xFFF4D089);

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
                leftX + PAD, top - HEADER_H + 3, ok ? 0xFFC2C9D6 : 0xFFFF8A80);

            int columns = partColumns(part);
            int cellW = Math.max(8, (leftW - PAD * 2) / columns);
            int rows = part == DeckList.Part.MAIN ? mainRows() : 1;
            for(int row = 0; row < rows; row++)
            {
                for(int column = 0; column < columns; column++)
                {
                    int index = row * columns + column;
                    int x = leftX + PAD + column * cellW;
                    int y = top + row * (CARD_H + GAP);
                    NineSlice.image(poseStack, HubTextures.SLOT, x, y, CARD_W, CARD_H);
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
        NineSlice.draw(poseStack, HubTextures.SEARCH_FIELD, rightX + PAD, panelTop + PAD,
            rightW - PAD * 2 - 96, 18);

        List<Properties> shown = EditorState.visible();
        int gridTop = panelTop + PAD + 40;
        int cellW = Math.max(8, (rightW - PAD * 2) / TRUNK_COLUMNS);
        int visibleRows = Math.max(1, (panelTop + panelH - gridTop - PAD) / (CARD_H + GAP));

        for(int row = 0; row < visibleRows; row++)
        {
            for(int column = 0; column < TRUNK_COLUMNS; column++)
            {
                int index = (row + trunkScroll) * TRUNK_COLUMNS + column;
                int x = rightX + PAD + column * cellW;
                int y = gridTop + row * (CARD_H + GAP);
                NineSlice.image(poseStack, HubTextures.SLOT, x, y, CARD_W, CARD_H);
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
                    font.drawShadow(poseStack, count, x + CARD_W - font.width(count),
                        y + CARD_H - 8, inDeck >= max ? 0xFFFF8A80 : 0xFFF4D089);
                }
            }
        }

        font.drawShadow(poseStack, shown.size() + " cards", rightX + PAD,
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
        DdBlitUtil.fullBlit(poseStack, x, y, CARD_W, CARD_H);
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
