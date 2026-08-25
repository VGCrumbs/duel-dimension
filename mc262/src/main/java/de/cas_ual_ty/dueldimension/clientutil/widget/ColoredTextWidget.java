package de.cas_ual_ty.dueldimension.clientutil.widget;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * A coloured button-look that is not clickable — a red or blue label plate.
 * <p>
 * The Forge version bound {@code colored_button.png} (its own copy of the
 * vanilla button sheet's 200-wide layout) and stitched four blits into a
 * button shape, tinting them with {@code RenderSystem.setShaderColor} for the
 * disabled alpha and picking a hover row by {@code getYImage}. Immediate-mode
 * drawing and {@code getYImage} are both gone: the four blits become four
 * {@link DdBlitUtil} calls against the same 256-file layout, the alpha rides in
 * the blit's tint, and the row index is computed inline the way the old helper
 * did (disabled 0, normal 1, hover 2).
 */
public class ColoredTextWidget extends AbstractWidget
{
    public static final Identifier RESOURCE = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/colored_button.png");

    public Supplier<Component> msgGetter;
    public ITooltip tooltip;
    public int offset;

    public ColoredTextWidget(int xIn, int yIn, int widthIn, int heightIn, Supplier<Component> msgGetter, ITooltip tooltip)
    {
        super(xIn, yIn, widthIn, heightIn, Component.empty());
        this.msgGetter = msgGetter;
        active = false;
        this.tooltip = tooltip;
        offset = 0;
    }

    public ColoredTextWidget(int xIn, int yIn, int widthIn, int heightIn, Supplier<Component> msgGetter)
    {
        this(xIn, yIn, widthIn, heightIn, msgGetter, null);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int i = getYImage(isHoveredOrFocused());
        int tint = DdBlitUtil.alpha(alpha);
        blitPiece(extractor, x, y, 0, offset + i * 20, width / 2, height / 2, tint);
        blitPiece(extractor, x + width / 2, y, 200 - width / 2, offset + i * 20, width / 2, height / 2, tint);
        blitPiece(extractor, x, y + height / 2, 0, offset + (i + 1) * 20 - height / 2, width / 2, height / 2, tint);
        blitPiece(extractor, x + width / 2, y + height / 2, 200 - width / 2, offset + (i + 1) * 20 - height / 2, width / 2, height / 2, tint);
        int j = getFGColor();
        extractor.centeredText(fontrenderer, getMessage(), x + width / 2, y + (height - 8) / 2, j | Mth.ceil(alpha * 255.0F) << 24);

        if(isHoveredOrFocused() && tooltip != null)
        {
            tooltip.onTooltip(this, extractor, mouseX, mouseY);
        }
    }

    /** One piece of the button plate, sampled against the 256-file the sheet is drawn on. */
    private void blitPiece(GuiGraphicsExtractor extractor, int x, int y, int u, int v, int w, int h, int tint)
    {
        DdBlitUtil.blit(extractor, RESOURCE, x, y, w, h,
            u / 256F, v / 256F, (u + w) / 256F, (v + h) / 256F, tint);
    }

    /** The Forge {@code AbstractWidget.getYImage}: disabled 0, hovered 2, otherwise 1. */
    private int getYImage(boolean isHovered)
    {
        int i = 1;
        if(!active)
        {
            i = 0;
        }
        else if(isHovered)
        {
            i = 2;
        }
        return i;
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

    public ColoredTextWidget setBlue()
    {
        offset = 0;
        return this;
    }

    public ColoredTextWidget setRed()
    {
        offset = 60;
        return this;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {

    }
}
