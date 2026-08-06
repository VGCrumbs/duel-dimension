package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Buttons and tabs drawn from the hub's own textures.
 * <p>
 * Vanilla's {@code Button} paints itself from the widgets atlas and fills a
 * rectangle behind its label. Both are overridden here so the only thing the
 * game draws is the text; every pixel of surface comes from a PNG under
 * {@code textures/gui}.
 */
public final class HubWidgets
{
    private HubWidgets()
    {
    }

    /** A button whose surface is {@link HubTextures#BUTTON}. */
    public static class TextureButton extends Button
    {
        public TextureButton(int x, int y, int width, int height, Component label, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            int row = !active ? NineSlice.DISABLED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.BUTTON, x, y, width, height, row, 3);
            drawLabel(poseStack, active ? isHoveredOrFocused() ? 0xFFF4D089 : 0xFFE6EAF2 : 0xFF6A7080);
        }

        void drawLabel(PoseStack poseStack, int colour)
        {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            String text = getMessage().getString();
            font.drawShadow(poseStack, text, x + (width - font.width(text)) / 2F,
                y + (height - 8) / 2F, colour);
        }
    }

    /**
     * A tab. Selection is a state of the texture rather than a different
     * colour, so the whole strip can be reskinned from one file.
     */
    public static class TabButton extends TextureButton
    {
        private final java.util.function.BooleanSupplier selected;

        public TabButton(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier selected, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.selected = selected;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            boolean on = selected.getAsBoolean();
            int row = on ? NineSlice.SELECTED : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(poseStack, HubTextures.TAB, x, y, width, height, row, 3);
            drawLabel(poseStack, on ? 0xFFF4D089 : 0xFFC2C9D6);
        }
    }
}
