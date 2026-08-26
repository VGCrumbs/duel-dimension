package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.clientutil.CardPresentation;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.cardinventory.CardInventory;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.widget.ImprovedButton;
import de.cas_ual_ty.dueldimension.clientutil.widget.TextureButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * The card binder.
 * <p>
 * One port wrinkle: the Forge screen set {@code imageWidth = 176}, ran
 * {@code super.init()} so the panel centres at that width, then widened
 * {@code imageWidth += 27} for the insertion slot that hangs off the right — the
 * GUI is deliberately not centred around the full panel. {@code imageWidth} is
 * final in 26.2, so 176 is passed to {@code super} (keeping the same centring)
 * and the widened value lives in {@link #panelWidth}, which every layout and
 * background reference uses exactly where the Forge code read the post-{@code +=}
 * {@code imageWidth}.
 */
public class CardBinderScreen extends AbstractContainerScreen<CardBinderContainer>
{
    private static final ResourceLocation CARD_BINDER_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/card_binder.png");

    // https://www.glfw.org/docs/latest/group__keys.html
    private static final int LEFT_SHIFT = 340;
    private static final int Q = 81;

    /** The Forge {@code imageWidth} after its post-init {@code += 27}. */
    protected final int panelWidth = 176 + 27;

    protected CardButton[] cardButtons;

    protected Button reloadButton;
    protected Button prevButton;
    protected Button nextButton;

    protected int centerX;
    protected int centerY;

    protected EditBox cardSearch;

    public CardBinderScreen(CardBinderContainer screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn);
        // 26.2 passed the panel size to super; here the fields are assigned.
        this.imageWidth = 176;
        this.imageHeight = 114 + CardInventory.DEFAULT_PAGE_ROWS * 18;
    }

    @Override
    protected void init()
    {
        super.init();

        int index;
        CardButton button;
        cardButtons = new CardButton[CardInventory.DEFAULT_CARDS_PER_PAGE];

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

        addRenderableWidget(prevButton = new ImprovedButton(leftPos + panelWidth - 24 - 8 - 27, topPos + 4, 12, 12, Component.translatable("generic.dueldimension.left_arrow"), this::onButtonClicked));
        addRenderableWidget(nextButton = new ImprovedButton(leftPos + panelWidth - 12 - 8 - 27, topPos + 4, 12, 12, Component.translatable("generic.dueldimension.right_arrow"), this::onButtonClicked));

        addRenderableWidget(reloadButton = new TextureButton(leftPos + panelWidth - 12 - 8 - 27, topPos + imageHeight - 96, 12, 12, Component.empty(), this::onButtonClicked)
                .setTexture(ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/duel_widgets.png"), 64, 0, 16, 16));
        addRenderableWidget(cardSearch = new EditBox(font, leftPos + panelWidth - 12 - 8 - 27 - 82, topPos + imageHeight - 96, 80, 12, Component.empty()));
    }

    @Override
    public void renderBg(net.minecraft.client.gui.GuiGraphics vanillaGraphics, float partialTicks, int mouseX, int mouseY)
    {
        // 26.2 describes a screen into a render state; 1.21.1 draws it now. The
        // body below is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor ms = new de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor(vanillaGraphics);
        // The dim was super.extractBackground. In 1.21.1 AbstractContainerScreen.renderBackground
        // draws it and then calls this, so it still happens, and once.
        DdBlitUtil.blit(ms, CardBinderScreen.CARD_BINDER_GUI_TEXTURE, leftPos, topPos, panelWidth, imageHeight,
            0F, 0F, panelWidth / 256F, imageHeight / 256F, DdBlitUtil.NO_TINT);
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
    protected void renderLabels(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY)
    {
        // 26.2 describes a screen into a render state; 1.21.1 draws it now. The
        // body below is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor ms = new de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor(vanillaGraphics);
        MutableComponent title = Component.literal(this.title.getString());

        if(!getMenu().loaded)
        {
            title = title.append(" ").append(Component.translatable("container.dueldimension.card_binder.loading"));
        }
        else
        {
            title = title.append(" ").append(Component.literal(menu.page + "/" + menu.clientMaxPage));
        }

        ms.text(font, title, 8, 6, 0xFF404040);

        ms.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040);
    }

    protected void onButtonClicked(Button button)
    {
        if(!getMenu().loaded)
        {
            return;
        }

        if(button == prevButton)
        {
            ClientPlayNetworking.send(new CardBinderMessages.ChangePage(false));
        }
        else if(button == nextButton)
        {
            ClientPlayNetworking.send(new CardBinderMessages.ChangePage(true));
        }
        else if(button == reloadButton)
        {
            ClientPlayNetworking.send(new CardBinderMessages.ChangeSearch(cardSearch.getValue()));
        }
    }

    protected void onCardClicked(CardButton button, int index)
    {
        if(!getMenu().loaded)
        {
            return;
        }

        if(button.getCard() != null)
        {
            ClientPlayNetworking.send(new CardBinderMessages.IndexClicked(index));
        }
    }

    protected CardHolder getCard(int index)
    {
        return index < getMenu().clientList.size() ? getMenu().clientList.get(index) : null;
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
        KeyEvent keyEvent = new KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);

        if(cardSearch != null && cardSearch.isFocused())
        {
            return cardSearch.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
        }
        else if(getMenu().loaded)
        {
            if(keyEvent.key() == CardBinderScreen.Q)
            {
                for(CardButton button : cardButtons)
                {
                    if(button.isHoveredOrFocused() && button.getCard() != null)
                    {
                        ClientPlayNetworking.send(new CardBinderMessages.IndexDropped(button.index));
                        break;
                    }
                }
            }
        }

        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
    }
}
