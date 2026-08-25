package de.cas_ual_ty.dueldimension.cardsupply;

import de.cas_ual_ty.dueldimension.clientutil.CardPresentation;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.cardbinder.CardButton;
import de.cas_ual_ty.dueldimension.cardinventory.CardInventory;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.widget.ImprovedButton;
import de.cas_ual_ty.dueldimension.rarity.Rarities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CardSupplyScreen extends AbstractContainerScreen<CardSupplyContainer>
{
    private static final ResourceLocation CARD_SUPPLY_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "textures/gui/card_supply.png");

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int PAGE = CardSupplyScreen.ROWS * CardSupplyScreen.COLUMNS;

    public List<CardHolder> cardsList;
    public EditBox textField;
    protected Button prevButton;
    protected Button nextButton;
    public int page;

    public CardButton[] cardButtons;

    public CardSupplyScreen(CardSupplyContainer screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn, 176, 114 + 6 * 18); //222
        cardsList = new ArrayList<>(DdDatabase.getTotalCardsAndVariants());
    }

    @Override
    protected void init()
    {
        super.init();

        addRenderableWidget(textField = new EditBox(font, leftPos + imageWidth - 80 - 8 - 1, topPos + 6 - 1, 80 + 2, font.lineHeight + 2, Component.empty()));

        int index;
        CardButton button;
        cardButtons = new CardButton[CardSupplyScreen.PAGE];

        for(int y = 0; y < CardInventory.DEFAULT_PAGE_ROWS; ++y)
        {
            for(int x = 0; x < CardInventory.DEFAULT_PAGE_COLUMNS; ++x)
            {
                index = x + y * 9;
                button = new CardButton(leftPos + 7 + x * 18, topPos + 17 + y * 18, 18, 18, index, this::onCardClicked, this::getCard);
                cardButtons[index] = button;
                addRenderableWidget(button);
            }
        }

        addRenderableWidget(prevButton = new ImprovedButton(leftPos + imageWidth - 80 - 8, topPos + imageHeight - 96, 40, 12, Component.translatable("container.dueldimension.card_supply.prev"), this::onButtonClicked));
        addRenderableWidget(nextButton = new ImprovedButton(leftPos + imageWidth - 40 - 8, topPos + imageHeight - 96, 40, 12, Component.translatable("container.dueldimension.card_supply.next"), this::onButtonClicked));

        applyName();
        updateCards();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        super.extractBackground(ms, mouseX, mouseY, partialTicks);
        DdBlitUtil.blit(ms, CardSupplyScreen.CARD_SUPPLY_GUI_TEXTURE, leftPos, topPos, imageWidth, imageHeight,
            0F, 0F, imageWidth / 256F, imageHeight / 256F, DdBlitUtil.NO_TINT);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

        super.render(ms.vanilla(), mouseX, mouseY, partialTicks);

        for(CardButton button : cardButtons)
        {
            if(button.isHoveredOrFocused())
            {
                if(button.getCard() != null)
                {
                    CardRenderUtil.renderCardInfo(ms, button.getCard(), leftPos);

                    List<Component> list = new LinkedList<>();
                    CardPresentation.addInformation(button.getCard(), list);

                    List<Component> tooltip = new ArrayList<>(list.size());
                    for(Component t : list)
                    {
                        tooltip.add(t);
                    }

                    //renderTooltip
                    ms.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }

                break;
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ms, int mouseX, int mouseY)
    {
        ms.text(font, title, 8, 6, 0xFF404040);
        ms.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040);
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        KeyEvent keyEvent = new KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);

        if(textField != null && textField.isFocused())
        {
            if(keyEvent.key() == GLFW.GLFW_KEY_ENTER)
            {
                applyName();
                return true;
            }
            else
            {
                return textField.keyPressed(keyEvent);
            }
        }
        else
        {
            return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
        }
    }

    protected void onButtonClicked(Button button)
    {
        int minPage = 0;
        int maxPage = cardsList.size() / CardSupplyScreen.PAGE + 1;

        if(button == prevButton)
        {
            --page;

            if(page < minPage)
            {
                page = maxPage;
            }
        }
        else if(button == nextButton)
        {
            ++page;

            if(page > maxPage)
            {
                page = minPage;
            }
        }
    }

    public void applyName()
    {
        String name = textField.getValue().toLowerCase();

        cardsList.clear();
        page = 0;

        DdDatabase.forAllCardVariants((card, imageIndex) ->
        {
            if(card.getName().toLowerCase().contains(name))
            {
                cardsList.add(new CardHolder(card, imageIndex, Rarities.SUPPLY.name));
            }
        });
    }

    public void updateCards()
    {
        page = 0;
        cardsList.clear();

        DdDatabase.forAllCardVariants((card, imageIndex) ->
        {
            cardsList.add(new CardHolder(card, imageIndex, Rarities.SUPPLY.name));
        });
    }

    protected void onCardClicked(CardButton button, int index)
    {
        if(button.getCard() != null && button.getCard().getCard() != null)
        {
            ClientPlayNetworking.send(new CardSupplyMessages.RequestCard(button.getCard().getCard().getId(), button.getCard().getImageIndex()));
        }
    }

    protected CardHolder getCard(int index0)
    {
        int index = scopeIndex(index0);
        return index < cardsList.size() ? cardsList.get(index) : null;
    }

    protected int scopeIndex(int cardButtonIndex)
    {
        return page * CardSupplyScreen.PAGE + cardButtonIndex;
    }
}
