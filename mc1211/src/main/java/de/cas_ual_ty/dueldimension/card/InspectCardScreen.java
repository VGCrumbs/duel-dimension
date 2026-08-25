package de.cas_ual_ty.dueldimension.card;

import com.mojang.blaze3d.platform.InputConstants;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A full-screen look at a single card.
 * <p>
 * The Forge {@code render(PoseStack, ...)} became {@code extractRenderState}:
 * the dim goes down with {@code extractBackground}, then the card is described.
 * <p>
 * TODO(M3): the Forge original drew the card through
 * {@code CardRenderUtil.renderInfoCardWithRarity}, which composited the rarity
 * foil against the art with a colour mask and a second pass. That path is not
 * ported (it belongs with the duel field, see PORTING.md), so the card is drawn
 * flat here as a placeholder until the foil compositing lands.
 */
public class InspectCardScreen extends Screen
{
    public final CardHolder cardHolder;

    public InspectCardScreen(Component pTitle, CardHolder cardHolder)
    {
        super(pTitle);
        this.cardHolder = cardHolder;
    }

    public InspectCardScreen(CardHolder cardHolder)
    {
        this(Component.empty(), cardHolder);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int pMouseX, int pMouseY, float pPartialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, the blur is once-per-frame, and the frame a screen
        // opens over another that already asked for it took the client down.
        // Same decision as EngineDuelScreen, for the same crash.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        if(cardHolder != null && cardHolder.getCard() != null)
        {
            int w = 128;
            int h = 128;
            int x = width / 2 - w / 2;
            int y = height / 2 - h / 2;
            // TODO(M3): renderInfoCardWithRarity — draw the rarity foil composited
            // over the art. Flat placeholder for now.
            DdBlitUtil.fullBlit(extractor, CardRenderUtil.bindInfoResourceLocation(cardHolder), x, y, w, h);
        }

        super.render(extractor.vanilla(), pMouseX, pMouseY, pPartialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent)
    {
        InputConstants.Key mouseKey = InputConstants.getKey(keyEvent);

        if(minecraft.options.keyInventory.matches(mouseKey))
        {
            onClose();
            return true;
        }

        return super.keyPressed(keyEvent);
    }

    @Override
    public void handleDelayedNarration()
    {
    }

    @Override
    public void triggerImmediateNarration(boolean p_169408_)
    {
    }
}
