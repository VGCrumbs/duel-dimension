package de.cas_ual_ty.dueldimension.duel.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.widget.ImprovedButton;
import de.cas_ual_ty.dueldimension.clientutil.widget.ItemStackWidget;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.duel.DeckSource;
import de.cas_ual_ty.dueldimension.duel.DuelContainer;
import de.cas_ual_ty.dueldimension.duel.PlayerRole;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessages;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DuelScreenPreparing<E extends DuelContainer> extends DuelContainerScreen<E>
{
    protected AbstractButton prevDeckButton;
    protected AbstractButton nextDeckButton;
    protected AbstractButton chooseDeckButton;
    
    protected ItemStackWidget prevDeckWidget;
    protected ItemStackWidget activeDeckWidget;
    protected ItemStackWidget nextDeckWidget;
    
    protected List<DeckWrapper> deckWrappers;
    protected int activeDeckWrapperIdx;
    protected boolean deckChosen;
    
    public DuelScreenPreparing(E screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn);
        activeDeckWrapperIdx = 0;
        deckChosen = false;
    }
    
    @Override
    protected void init()
    {
        super.init();
        
        int x = width / 2;
        
        if(!deckChosen && getZoneOwner() != ZoneOwner.NONE)
        {
            // Faking this to make the chat smaller on the right side.
            // PORT-NOTE: imageWidth is final in 26.2, so it can no longer be
            // swapped out from under initDefaultChat and put back. The same
            // arithmetic runs here against the faked 284 instead, which
            // duplicates DuelContainerScreen's margin and button height and
            // will drift if those change; the tidier fix is a panel-width
            // parameter on initDefaultChat, which is not this file's to add.
            final int chatMargin = 4;
            final int chatButtonHeight = 20;

            int fakeImageWidth = 284;
            int fakeLeftPos = (width - fakeImageWidth) / 2;

            int chatX = fakeLeftPos + fakeImageWidth + chatMargin;
            int chatY = topPos + chatMargin;
            int chatMaxWidth = Math.min(160, (width - fakeImageWidth) / 2 - 2 * chatMargin);
            int chatHeight = imageHeight - 4 * (chatButtonHeight + chatMargin) - 2 * chatMargin;

            initChat(width, height, chatX, chatY, chatMaxWidth, imageHeight, chatMaxWidth, chatHeight, chatMargin, chatButtonHeight);

            //without x+1 its technically not centered, i dont get why :(
            int chooseWidth = imageWidth - 20;
            addRenderableWidget(prevDeckButton = new ImprovedButton(x - 16 - 16 - 10 - 5 - 10, topPos + imageHeight - 20 - 10 - 5 - 16 - 10, 20, 20, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.left_arrow"), (button) -> prevDeckClicked()));
            addRenderableWidget(nextDeckButton = new ImprovedButton(x - 16 + 32 + 16 + 5, topPos + imageHeight - 20 - 10 - 5 - 16 - 10, 20, 20, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.right_arrow"), (button) -> nextDeckClicked()));
            addRenderableWidget(chooseDeckButton = new ImprovedButton(x - chooseWidth / 2, topPos + imageHeight - 20 - 10, chooseWidth, 20, Component.translatable("container." + DuelDimension.MOD_ID + ".duel.choose_deck"), (button) -> chooseDeckClicked()));
            // ImprovedButton dropped the ITooltip constructor argument, and
            // this tooltip never varied with what the screen was doing, so it
            // is a property of the finished widget.
            chooseDeckButton.setTooltip(Tooltip.create(Component.translatable("container.dueldimension.duel.choose_deck")));

            addRenderableWidget(prevDeckWidget = new ItemStackWidget(x - 16 - 16, topPos + imageHeight - 20 - 10 - 5 - 16 - 8, 16, CardRenderUtil.getInfoCardBack()));
            addRenderableWidget(activeDeckWidget = new ItemStackWidget(x - 16, topPos + imageHeight - 20 - 10 - 5 - 32, 32, CardRenderUtil.getInfoCardBack()));
            addRenderableWidget(nextDeckWidget = new ItemStackWidget(x - 16 + 32, topPos + imageHeight - 20 - 10 - 5 - 16 - 8, 16, CardRenderUtil.getInfoCardBack()));
            
            prevDeckWidget.visible = false;
            activeDeckWidget.visible = false;
            nextDeckWidget.visible = false;
            
            setActiveDeckWrapper(activeDeckWrapperIdx);
        }
        else
        {
            initDefaultChat(width, height);
        }
    }
    
    /**
     * PORT-NOTE: this was {@code renderLabels}, which 26.2 renamed to
     * {@code extractLabels} -- same contract, same panel-local coordinates. But
     * the base only reaches it from {@code AbstractContainerScreen
     * .extractContents}, and {@link DuelContainerScreen#extractContents}
     * currently draws the duel panel and returns without chaining, so as things
     * stand nothing below is drawn. Unresolved because the fix belongs in that
     * file: its panel and this screen's deck panel have to stay in one hook and
     * in that order (Forge drew both in {@code renderBg}, labels afterwards), so
     * splitting one of them off into {@code extractBackground} would put the
     * deck panel in an earlier stratum and hide it behind the duel panel.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        ms.text(font, "Choose your decks...", 8, 6, 0xFF404040, false);

        PlayerRole role = getPlayerRole();

        if(role == PlayerRole.PLAYER1 || role == PlayerRole.PLAYER2)
        {
            if(renderDeckChoosing())
            {
                drawActiveDeckForeground(ms, mouseX, mouseY);
            }
            else
            {
                String text = "Waiting for other player...";
                int width = font.width(text);
                int height = font.lineHeight;
                ms.text(font, text, (imageWidth - width) / 2, (this.height - height) / 2, 0xFF404040, false);
            }
        }
        else
        {
            String text = "Waiting for players...";
            int width = font.width(text);
            int height = font.lineHeight;
            ms.text(font, text, (imageWidth - width) / 2, (imageHeight - height) / 2, 0xFF404040, false);
        }
    }
    
    @Override
    public void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY,
        float partialTicks)
    {
        super.extractContents(ms, mouseX, mouseY, partialTicks);
        
        if(renderDeckChoosing())
        {
            drawActiveDeckBackground(ms, partialTicks, mouseX, mouseY);
        }
    }
    
    protected void drawActiveDeckForeground(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        DeckWrapper h = getActiveDeckWrapper();
        
        if(h != DeckWrapper.DUMMY)
        {
            DeckHolder d = h.deck;
            
            if(d != null && d != DeckHolder.DUMMY)
            {
                // coordinates from #drawActiveDeckBackground
                int xSize = 284;
                //                int ySize = 153;
                int actualGuiLeft = (width - xSize) / 2;
                int guiLeft = actualGuiLeft - leftPos;
                int guiTop = topPos + 6 + 5 + font.lineHeight - topPos;
                
                // from DeckBoxScreen#drawGuiContainerForegroundLayer
                
                mouseX -= (leftPos + guiLeft) - 1;
                mouseY -= (topPos + guiTop) - 1;
                
                // main deck
                //drawString
                ms.text(font, Component.translatable("container.dueldimension.deck_box.main").append(" " + d.getMainDeckSize() + "/" + DeckHolder.MAIN_DECK_SIZE), guiLeft + 8, guiTop + 6, 0xFF404040, false);
                
                // extra deck
                //drawString
                ms.text(font, Component.translatable("container.dueldimension.deck_box.extra").append(" " + d.getExtraDeckSize() + "/" + DeckHolder.EXTRA_DECK_SIZE), guiLeft + 8, guiTop + 92, 0xFF404040, false);
                
                // side deck
                //drawString
                ms.text(font, Component.translatable("container.dueldimension.deck_box.side").append(" " + d.getSideDeckSize() + "/" + DeckHolder.SIDE_DECK_SIZE), guiLeft + 8, guiTop + 124, 0xFF404040, false);
                
                int size = 18;
                CardHolder c;
                
                //following code from DeckBoxContainer#<init>
                
                final int itemsPerRow = 15;
                
                // main deck
                boolean broken = false;
                int offX = 8;
                int offY = 18;
                for(int y = 0; y < DeckHolder.MAIN_DECK_SIZE / itemsPerRow; ++y)
                {
                    for(int x = 0; x < itemsPerRow && x + y * itemsPerRow < DeckHolder.MAIN_DECK_SIZE; ++x)
                    {
                        if(d.getMainDeck().size() <= x + y * itemsPerRow)
                        {
                            broken = true;
                            break;
                        }
                        
                        c = d.getMainDeck().get(x + y * itemsPerRow);
                        
                        if(c != null && c.getCard() != null)
                        {
                            DdBlitUtil.fullBlit(ms, CardRenderUtil.bindMainResourceLocation(c), guiLeft + offX, guiTop + offY, 16, 16);

                            if(mouseX >= offX && mouseX < offX + size && mouseY >= offY && mouseY < offY + size)
                            {
                                ScreenUtil.renderHoverRect(ms, guiLeft + offX, guiTop + offY, 16, 16);
                                renderCardInfoForeground(ms, c, actualGuiLeft);
                            }
                        }
                        
                        offX += size;
                    }
                    
                    if(broken)
                    {
                        break;
                    }
                    
                    offX = 8;
                    offY += size;
                }
                
                // extra deck
                offX = 8;
                offY = 104;
                for(int x = 0; x < DeckHolder.EXTRA_DECK_SIZE; ++x)
                {
                    if(d.getExtraDeck().size() <= x)
                    {
                        break;
                    }
                    
                    c = d.getExtraDeck().get(x);

                    if(c != null && c.getCard() != null)
                    {
                        DdBlitUtil.fullBlit(ms, CardRenderUtil.bindMainResourceLocation(c), guiLeft + offX, guiTop + offY, 16, 16);

                        if(mouseX >= offX && mouseX < offX + size && mouseY >= offY && mouseY < offY + size)
                        {
                            ScreenUtil.renderHoverRect(ms, guiLeft + offX, guiTop + offY, 16, 16);
                            renderCardInfoForeground(ms, c, actualGuiLeft);
                        }
                    }
                    
                    offX += size;
                }
                
                // side deck
                offX = 8;
                offY = 136;
                for(int x = 0; x < DeckHolder.SIDE_DECK_SIZE; ++x)
                {
                    if(d.getSideDeck().size() <= x)
                    {
                        break;
                    }
                    
                    c = d.getSideDeck().get(x);

                    if(c != null && c.getCard() != null)
                    {
                        DdBlitUtil.fullBlit(ms, CardRenderUtil.bindMainResourceLocation(c), guiLeft + offX, guiTop + offY, 16, 16);

                        if(mouseX >= offX && mouseX < offX + size && mouseY >= offY && mouseY < offY + size)
                        {
                            ScreenUtil.renderHoverRect(ms, guiLeft + offX, guiTop + offY, 16, 16);
                            renderCardInfoForeground(ms, c, actualGuiLeft);
                        }
                    }
                    
                    offX += size;
                }
            }
        }
    }
    
    protected void drawActiveDeckBackground(GuiGraphicsExtractor ms, float partialTicks, int mouseX, int mouseY)
    {
        DeckWrapper h = getActiveDeckWrapper();
        
        if(h != DeckWrapper.DUMMY)
        {
            DeckHolder d = h.deck;
            
            if(d != null && d != DeckHolder.DUMMY)
            {
                int xSize = 284;
                int ySize = 153;
                int guiLeft = (width - xSize) / 2;
                int guiTop = topPos + 6 + 5 + font.lineHeight;
                
                // The deck box panel and, under it, the 7-pixel strip at v=243
                // that closes it off -- both cut from the same 512x256 file.
                DdBlitUtil.blit(ms, DuelContainerScreen.DECK_BACKGROUND_GUI_TEXTURE, guiLeft, guiTop, xSize, ySize,
                    0F, 0F, xSize / 512F, ySize / 256F, DdBlitUtil.NO_TINT);
                DdBlitUtil.blit(ms, DuelContainerScreen.DECK_BACKGROUND_GUI_TEXTURE, guiLeft, guiTop + ySize, xSize, 7,
                    0F, 243 / 256F, xSize / 512F, 250 / 256F, DdBlitUtil.NO_TINT);
            }
        }
    }
    
    @Override
    public void populateDeckSources(List<DeckSource> deckSources)
    {
        deckWrappers = new ArrayList<>(deckSources.size());
        activeDeckWrapperIdx = 0;
        
        for(int index = 0; index < deckSources.size(); ++index)
        {
            deckWrappers.add(new DeckWrapper(deckSources.get(index), index));
        }
        
        setActiveDeckWrapper(0);
    }
    
    @Override
    public void receiveDeck(int index, DeckHolder deck)
    {
        if(index >= 0 && index < deckWrappers.size())
        {
            deckWrappers.get(index).deck = deck;
            setActiveDeckWrapper(activeDeckWrapperIdx);
        }
    }
    
    @Override
    public void deckAccepted(PlayerRole role)
    {
        if(role == getPlayerRole())
        {
            deckChosen = true;
            reInit();
        }
    }
    
    public void setActiveDeckWrapper(int index)
    {
        if(deckWrappers == null)
        {
            return;
        }
        
        if(index >= deckWrappers.size())
        {
            index = 0;
        }
        else if(index < 0)
        {
            index = deckWrappers.size() - 1;
        }
        
        int prev = index - 1;
        
        if(prev < 0)
        {
            prev = deckWrappers.size() - 1;
        }
        
        int next = index + 1;
        
        if(next >= deckWrappers.size())
        {
            next = 0;
        }
        
        activeDeckWrapperIdx = index;
        
        DeckWrapper dPrev = deckWrappers.get(prev);
        dPrev.index = prev;
        prevDeckWidget.setItemStack(dPrev.source);
        
        DeckWrapper dActive = deckWrappers.get(index);
        dActive.index = index;
        activeDeckWidget.setItemStack(dActive.source);
        
        DeckWrapper dNext = deckWrappers.get(next);
        dNext.index = next;
        nextDeckWidget.setItemStack(dNext.source);
        
        prevDeckWidget.visible = true;
        activeDeckWidget.visible = true;
        nextDeckWidget.visible = true;
        
        if(!dActive.hasDeck())
        {
            requestDeck(dActive.index);
        }
        
        chooseDeckButton.setMessage(dActive.name);
    }
    
    // when true, deck choosing must be rendered, otherwise dont render it
    public boolean renderDeckChoosing()
    {
        return getPlayerRole() == PlayerRole.PLAYER1 ? getDuelManager().player1Deck == null : (getPlayerRole() == PlayerRole.PLAYER2 ? getDuelManager().player2Deck == null : false);
    }
    
    public void renderCardInfoForeground(GuiGraphicsExtractor ms, CardHolder c)
    {
        renderCardInfoForeground(ms, c, leftPos);
    }
    
    public void renderCardInfoForeground(GuiGraphicsExtractor ms, CardHolder c, int width)
    {
        ms.pose().pushMatrix();
        
        ms.pose().translate(-leftPos, -topPos);
        CardRenderUtil.renderCardInfo(ms, c, width);
        
        ms.pose().popMatrix();
    }
    
    protected void prevDeckClicked()
    {
        setActiveDeckWrapper(activeDeckWrapperIdx - 1);
    }
    
    protected void nextDeckClicked()
    {
        setActiveDeckWrapper(activeDeckWrapperIdx + 1);
    }
    
    public DeckWrapper getActiveDeckWrapper()
    {
        if(deckWrappers == null || deckWrappers.size() <= activeDeckWrapperIdx)
        {
            return DeckWrapper.DUMMY;
        }
        else
        {
            return deckWrappers.get(activeDeckWrapperIdx);
        }
    }
    
    protected void chooseDeckClicked()
    {
        DuelDimension.proxy.sendDuelMessage(new DuelMessages.ChooseDeck(getHeader(), getActiveDeckWrapper().index));
    }
    
    public void requestDeck(int index)
    {
        DuelDimension.proxy.sendDuelMessage(new DuelMessages.RequestDeck(getHeader(), index));
    }
    
    protected static class DeckWrapper
    {
        public static final DeckWrapper DUMMY = new DeckWrapper(new DeckSource(DeckHolder.DUMMY, new ItemStack(DdItems.BLANC_CARD)), -1);
        
        public ItemStack source;
        public Component name;
        public DeckHolder deck;
        public int index;
        
        public DeckWrapper(DeckSource source, int index)
        {
            this.source = source.source;
            name = source.name;
            deck = source.deck; //should be null
        }
        
        public boolean hasDeck()
        {
            return deck != null;
        }
        
        public ItemStack getShownItemStack()
        {
            return source;
        }
    }
}
