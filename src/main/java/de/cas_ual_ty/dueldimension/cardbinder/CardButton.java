package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A binder slot: a card's art with a hover highlight, calling back on click.
 * <p>
 * The Forge version bound the card texture with a {@code RenderSystem} colour
 * reset ({@code ScreenUtil.white()}) and blitted it, then lightened the slot on
 * hover with the {@code colorMask(RGB only) + fillGradient} trick. In 26.2 the
 * texture is bound and returned in one call, the blit is a {@link DdBlitUtil}
 * one, and the colour-mask hover — which existed only to lighten without
 * touching alpha — becomes a single translucent-white ARGB {@code fill}, exactly
 * as {@code ScreenUtil} documents. {@code AbstractButton.onPress} now takes the
 * input that caused it.
 */
public class CardButton extends AbstractButton
{
    public final int index;
    private Function<Integer, CardHolder> cardHolder;
    private BiConsumer<CardButton, Integer> onPress;

    public CardButton(int posX, int posY, int width, int height, int index, BiConsumer<CardButton, Integer> onPress, Function<Integer, CardHolder> cardHolder)
    {
        super(posX, posY, width, height, Component.empty());
        this.index = index;
        this.cardHolder = cardHolder;
        this.onPress = onPress;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick)
    {
        CardHolder card = getCard();
        if(card != null)
        {
            Identifier rl = CardRenderUtil.bindMainResourceLocation(card);
            DdBlitUtil.fullBlit(extractor, rl, getX() + 1, getY() + 1, 16, 16);

            if(isHoveredOrFocused())
            {
                drawHover(extractor);
            }
        }
    }

    protected void drawHover(GuiGraphicsExtractor extractor)
    {
        int x = getX() + 1;
        int y = getY() + 1;
        int slotColor = -2130706433; // From ContainerScreen::slotColor
        extractor.fill(x, y, x + 16, y + 16, slotColor);
    }

    @Override
    public void onPress(InputWithModifiers input)
    {
        onPress.accept(this, index);
    }

    public CardHolder getCard()
    {
        return cardHolder.apply(index);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {

    }
}
