package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Buttons and tabs drawn from the hub's own textures.
 * <p>
 * Vanilla's {@code Button} paints itself from the widgets atlas and fills a
 * rectangle behind its label. Both are overridden here so the only thing the
 * game draws is the text; every pixel of surface comes from a PNG under
 * {@code textures/gui}.
 * <p>
 * Three things moved since the Forge version, and all three are the GUI going
 * retained-mode. {@code renderButton(PoseStack, ...)} became
 * {@code extractContents(GuiGraphicsExtractor, ...)}: a widget no longer draws
 * itself, it <em>describes</em> itself and the game draws everything later in
 * one pass. {@code x}, {@code y} and {@code width} are private, so the
 * accessors are used. And {@code Button}'s constructor gained a narration
 * argument, which is worth having — a button that says nothing to a screen
 * reader is a button some players cannot use.
 */
public final class HubWidgets
{
    private HubWidgets()
    {
    }

    /** A button whose surface is {@link HubTextures#BUTTON}. */
    public static class TextureButton extends Button
    {
        /** Overrides the label's colour, for a row that is reporting a state. */
        private Integer labelColour;
        /** Shown while hovered, when a button needs to explain itself. */
        private java.util.List<String> tooltip = java.util.List.of();

        public TextureButton(int x, int y, int width, int height, Component label, OnPress onPress)
        {
            // DEFAULT_NARRATION reads the label, which is what these buttons
            // want: their surface is decoration and the text is the meaning.
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        }

        public void setLabelColour(int colour)
        {
            labelColour = colour;
        }

        public void setTooltipLines(java.util.List<String> lines)
        {
            tooltip = lines == null ? java.util.List.of() : lines;
        }

        public java.util.List<String> tooltipLines()
        {
            return tooltip;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            int row = !active ? NineSlice.DISABLED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.BUTTON, getX(), getY(), getWidth(), getHeight(),
                row, 3);
            int colour = labelColour != null ? labelColour
                : active ? isHoveredOrFocused() ? 0xFFF4D089 : 0xFFE6EAF2 : 0xFF6A7080;
            drawLabel(graphics, colour);
        }

        void drawLabel(GuiGraphicsExtractor graphics, int colour)
        {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            String text = getMessage().getString();
            graphics.text(font, text, getX() + (getWidth() - font.width(text)) / 2,
                getY() + (getHeight() - 8) / 2, colour, true);
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
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            boolean on = selected.getAsBoolean();
            int row = on ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.TAB, getX(), getY(), getWidth(), getHeight(),
                row, 3);
            drawLabel(graphics, on ? 0xFFF4D089 : 0xFFC2C9D6);
        }
    }
}
