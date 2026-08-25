package de.cas_ual_ty.dueldimension.clientutil.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * A centred label drawn at half size.
 * <p>
 * Like {@link TextWidget}, the Forge version hand-drew the vanilla button sheet
 * behind the text; that sheet and its immediate-mode blits are gone. What is
 * kept is the half-scale text — the {@code PoseStack.scale(0.5F)} became a
 * {@code pose().scale(0.5F)} on the extractor's 2D matrix, with the same
 * doubled coordinates the Forge code drew at.
 */
public class SmallTextWidget extends AbstractWidget
{
    public Supplier<Component> msgGetter;
    public ITooltip tooltip;

    public SmallTextWidget(int xIn, int yIn, int widthIn, int heightIn, Supplier<Component> msgGetter, ITooltip tooltip)
    {
        super(xIn, yIn, widthIn, heightIn, Component.empty());
        this.msgGetter = msgGetter;
        active = false;
        this.tooltip = tooltip;
    }

    public SmallTextWidget(int xIn, int yIn, int widthIn, int heightIn, Supplier<Component> msgGetter)
    {
        this(xIn, yIn, widthIn, heightIn, msgGetter, null);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;

        int x = getX() + getWidth() / 2;
        int y = getY() + getHeight() / 2;

        extractor.pose().pushMatrix();
        extractor.pose().scale(0.5F, 0.5F);

        int j = getFGColor();
        extractor.centeredText(fontrenderer, getMessage(), x * 2, y * 2 - fontrenderer.lineHeight / 2, j | Mth.ceil(alpha * 255.0F) << 24);

        extractor.pose().popMatrix();

        if(isHoveredOrFocused() && tooltip != null)
        {
            tooltip.onTooltip(this, extractor, mouseX, mouseY);
        }
    }

    public int getFGColor()
    {
        return 16777215; //From super
    }

    @Override
    public Component getMessage()
    {
        return msgGetter.get();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {

    }
}
