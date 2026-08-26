package de.cas_ual_ty.dueldimension.clientutil.widget;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A plain button.
 * <p>
 * On Forge this hand-drew the 1.19 {@code WIDGETS_LOCATION} button sheet in
 * {@code renderButton(PoseStack, ...)} — which only reproduced the vanilla
 * button's own look, and immediate-mode drawing is gone anyway. In 26.2 a
 * button renders itself through {@code extractWidgetRenderState}, so there is
 * nothing left to reproduce: this is a {@link Button} with the mod's own
 * constructor shape. The Forge {@code ITooltip} constructor is dropped — a
 * tooltip is now a {@code setTooltip(Tooltip)} on the finished widget, not a
 * constructor argument.
 * <p>
 * {@link Button}'s public constructor is gone; the protected one takes a
 * narration supplier, so the mod passes {@link Button#DEFAULT_NARRATION}.
 */
public class ImprovedButton extends Button
{
    public ImprovedButton(int x, int y, int width, int height, Component title, OnPress pressedAction)
    {
        super(x, y, width, height, title, pressedAction, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        // The standard button sprite; the label is added by the base's
        // extractDefaultLabel around this call. Button is abstract in 26.2, so
        // the drawing has to be stated even when it is the default one.
        super.renderWidget(vanillaGraphics, mouseX, mouseY, partialTick);
    }
}
