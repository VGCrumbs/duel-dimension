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
        /**
         * Whether this button is offered, asked every frame rather than set.
         * <p>
         * For a button whose answer can change without the screen rebuilding.
         * Clear Filters is the case that named this: the deck editor's search
         * box gives it something to clear on every keystroke, and its responder
         * deliberately does NOT rebuild the widgets -- a rebuild there drops the
         * field's focus mid-word -- so the button stayed greyed out over a
         * filter that was plainly on. Same shape as {@code ChipButton}'s lit.
         */
        private java.util.function.BooleanSupplier activeSupplier;

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

        public void setActiveSupplier(java.util.function.BooleanSupplier supplier)
        {
            activeSupplier = supplier;
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
            // Answered before the surface is chosen, and written back to
            // `active` so the CLICK agrees with the picture rather than only
            // the paint doing.
            if(activeSupplier != null)
            {
                active = activeSupplier.getAsBoolean();
            }
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
     * A button whose label is a picture.
     * <p>
     * The same surface, the same three states and the same tooltip as its
     * parent -- only the middle is a texture instead of a word. That is what
     * lets an action shrink to a square without becoming a mystery: the icon
     * says what it is at a glance and the tooltip says it in words on hover,
     * where a truncated label would say neither.
     * <p>
     * The icon is tinted with the colour the LABEL would have taken, which is
     * why the files are white. So idle, hovered and disabled read exactly as
     * they do across the rest of the hub, from one texture.
     */
    public static class IconButton extends TextureButton
    {
        private final net.minecraft.resources.Identifier icon;
        /** How much of the button's shorter side the icon covers. */
        private static final float FILL = 0.62F;

        public IconButton(int x, int y, int width, int height,
            net.minecraft.resources.Identifier icon, Component narration, OnPress onPress)
        {
            // The narration is still a word: a screen reader cannot read a
            // texture, and DEFAULT_NARRATION reads getMessage().
            super(x, y, width, height, narration, onPress);
            this.icon = icon;
        }

        @Override
        void drawLabel(GuiGraphicsExtractor graphics, int colour)
        {
            int size = Math.round(Math.min(getWidth(), getHeight()) * FILL);
            de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(graphics, icon,
                getX() + (getWidth() - size) / 2, getY() + (getHeight() - size) / 2,
                size, size, 0F, 0F, 1F, 1F, 0xFF000000 | (colour & 0xFFFFFF));
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
