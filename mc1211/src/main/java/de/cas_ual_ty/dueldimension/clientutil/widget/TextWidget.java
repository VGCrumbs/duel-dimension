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
 * A centred label that is not a button.
 * <p>
 * On Forge this hand-drew the 1.19 {@code WIDGETS_LOCATION} button sheet behind
 * the text in {@code render(PoseStack, ...)} — the standard button's own look,
 * with immediate-mode blits and a {@code drawCenteredString} on top. The sheet
 * and immediate-mode drawing are both gone; what the widget was actually for is
 * the label, so this describes a centred string and nothing else through
 * {@link GuiGraphicsExtractor}. The Forge {@code getFGColor}/{@code getYImage}
 * helpers went with the sheet.
 */
public class TextWidget extends AbstractWidget
{
    public Supplier<Component> msgGetter;
    public ITooltip tooltip;

    public TextWidget(int xIn, int yIn, int widthIn, int heightIn, Supplier<Component> msgGetter, ITooltip tooltip)
    {
        super(xIn, yIn, widthIn, heightIn, Component.empty());
        this.msgGetter = msgGetter;
        active = false;
        this.tooltip = tooltip;
    }

    public TextWidget(int xIn, int yIn, int widthIn, int heightIn, Supplier<Component> msgGetter)
    {
        this(xIn, yIn, widthIn, heightIn, msgGetter, null);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;
        int j = getFGColor();
        extractor.centeredText(fontrenderer, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, j | Mth.ceil(alpha * 255.0F) << 24);

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
