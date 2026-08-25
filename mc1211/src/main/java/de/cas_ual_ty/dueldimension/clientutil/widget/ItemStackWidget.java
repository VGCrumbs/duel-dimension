package de.cas_ual_ty.dueldimension.clientutil.widget;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.clientutil.CardRenderUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A square that shows either a card's art, a real item, or a fallback texture.
 * <p>
 * The non-card branch was the reason this class carried an {@code ItemRenderer}
 * and a page of {@code RenderSystem}/{@code PoseStack} setup: it hand-drove
 * {@code ItemRenderer#render} to draw a GUI item, because there was no one-line
 * way to. In 26.2 there is — {@code extractor.item(stack, x, y)} — so all of
 * that collapses to one call and the {@code ItemRenderer} is no longer needed.
 * The constructor keeps its {@code ItemRenderer} parameter so the call sites do
 * not change; the field is simply unused now. The card and fallback branches
 * become {@link DdBlitUtil} blits with the disabled alpha carried in the tint.
 */
public class ItemStackWidget extends AbstractWidget
{
    public ItemStack itemStack;
    public ResourceLocation replacement;

    public ItemStackWidget(int xIn, int yIn, int size, ResourceLocation replacement)
    {
        super(xIn, yIn, size, size, Component.empty());
        itemStack = ItemStack.EMPTY;
        this.replacement = replacement;
    }

    public ItemStackWidget setItemStack(ItemStack itemStack)
    {
        this.itemStack = itemStack;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partial)
    {
        ResourceLocation rl = replacement;

        if(!itemStack.isEmpty())
        {
            if(itemStack.getItem() == DdItems.CARD)
            {
                CardHolder c = DdItems.CARD.getCardHolder(itemStack);

                if(c.getCard() != null)
                {
                    rl = CardRenderUtil.bindMainResourceLocation(c);
                }
            }
            else
            {
                // A real item, drawn by the GUI item path and returned so the
                // fallback blit below is not run.
                extractor.item(itemStack, getX(), getY());
                return;
            }
        }

        DdBlitUtil.fullBlit(extractor, rl, getX(), getY(), getWidth(), getHeight(), DdBlitUtil.alpha(alpha));
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {

    }
}
