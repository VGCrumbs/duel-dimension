package de.cas_ual_ty.dueldimension.clientutil.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A standard button whose label is drawn at half size.
 * <p>
 * On Forge this hand-drew the vanilla {@code WIDGETS_LOCATION} sheet and then a
 * half-scale label on top. In 26.2 the standard sprite is
 * {@code extractDefaultSprite}, and the half-scale label is drawn with a
 * {@code pose().scale(0.5F)} on the extractor's 2D matrix at the same doubled
 * coordinates the Forge code used. The base's own default label is deliberately
 * not used — it would draw the label at full size — so this overrides
 * {@link #extractContents} outright rather than adding to it. The
 * {@code ITooltip} constructor is dropped ({@code setTooltip(Tooltip)} now),
 * and {@code Button}'s protected constructor takes {@link Button#DEFAULT_NARRATION}.
 */
public class SmallTextButton extends Button
{
    public SmallTextButton(int x, int y, int width, int height, Component title, OnPress pressedAction)
    {
        super(x, y, width, height, title, pressedAction, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;
        extractDefaultSprite(extractor);

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        extractor.pose().pushMatrix();
        extractor.pose().scale(0.5F, 0.5F);
        int j = getFGColor();
        extractor.centeredText(fontrenderer, getMessage(), (x + width / 2) * 2, (y + height / 2) * 2 - fontrenderer.lineHeight / 2, j | Mth.ceil(alpha * 255.0F) << 24);
        extractor.pose().popMatrix();
    }

    private int getFGColor()
    {
        return 16777215;
    }
}
