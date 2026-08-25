package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;

/**
 * The duel mat colour picker: an HSV wheel, a brightness slider, and a live
 * preview of the mat itself.
 * <p>
 * Hue and saturation are read straight off the wheel's geometry rather than
 * sampled from the texture, so a click always resolves even on the feathered
 * rim, and the answer does not depend on the art. The texture and this maths
 * are generated from the same formula, so what is drawn is what is picked.
 * <p>
 * The preview is the real mat texture tinted by the chosen colour, not a
 * coloured square: the whole point of a greyscale source is that the preview
 * and the table end up identical.
 */
public final class MatColourPicker
{
    private final int x;
    private final int y;
    private final int wheelSize;
    private final int sliderWidth;

    private float hue;
    private float saturation;
    private float value = 1F;

    /** Which control the mouse grabbed, so a drag off the widget still tracks. */
    private enum Drag
    {
        NONE, WHEEL, VALUE
    }

    private Drag dragging = Drag.NONE;

    public MatColourPicker(int x, int y, int wheelSize, int sliderWidth, int startColour)
    {
        this.x = x;
        this.y = y;
        this.wheelSize = wheelSize;
        this.sliderWidth = sliderWidth;
        setColour(startColour);
    }

    public int colour()
    {
        return hsvToRgb(hue, saturation, value);
    }

    public void setColour(int rgb)
    {
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max <= 0F ? 0F : delta / max;
        if(delta <= 0F)
        {
            hue = 0F;
        }
        else if(max == r)
        {
            hue = (60F * (((g - b) / delta) % 6F) + 360F) % 360F;
        }
        else if(max == g)
        {
            hue = (60F * ((b - r) / delta + 2F) + 360F) % 360F;
        }
        else
        {
            hue = (60F * ((r - g) / delta + 4F) + 360F) % 360F;
        }
    }

    private int sliderX()
    {
        return x + wheelSize + 10;
    }

    public void render(GuiGraphicsExtractor poseStack)
    {
        NineSlice.image(poseStack, HubTextures.COLOUR_WHEEL, x, y, wheelSize, wheelSize);
        NineSlice.image(poseStack, HubTextures.VALUE_SLIDER, sliderX(), y, sliderWidth, wheelSize);

        // Where the current colour sits on each control.
        float radius = wheelSize / 2F;
        double angle = Math.toRadians(hue);
        int cursorX = Math.round(x + radius + (float)Math.cos(angle) * radius * saturation);
        int cursorY = Math.round(y + radius + (float)Math.sin(angle) * radius * saturation);
        NineSlice.image(poseStack, HubTextures.PICKER_CURSOR, cursorX - 5, cursorY - 5, 11, 11);

        int valueY = Math.round(y + (1F - value) * (wheelSize - 1));
        NineSlice.image(poseStack, HubTextures.PICKER_CURSOR,
            sliderX() + sliderWidth / 2 - 5, valueY - 5, 11, 11);
    }

    /** Draws the mat as it will actually appear, tinted by the chosen colour. */
    public void renderPreview(GuiGraphicsExtractor poseStack, int px, int py, int width, int height)
    {
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, px - 3, py - 3, width + 6, height + 6);
        NineSlice.tinted(poseStack, HubTextures.CUSTOM_MAT, px, py, width, height, colour());
    }

    public boolean mouseClicked(double mouseX, double mouseY)
    {
        if(inWheel(mouseX, mouseY))
        {
            dragging = Drag.WHEEL;
            applyWheel(mouseX, mouseY);
            return true;
        }
        if(inSlider(mouseX, mouseY))
        {
            dragging = Drag.VALUE;
            applyValue(mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY)
    {
        if(dragging == Drag.WHEEL)
        {
            applyWheel(mouseX, mouseY);
            return true;
        }
        if(dragging == Drag.VALUE)
        {
            applyValue(mouseY);
            return true;
        }
        return false;
    }

    public void mouseReleased()
    {
        dragging = Drag.NONE;
    }

    private boolean inWheel(double mouseX, double mouseY)
    {
        float radius = wheelSize / 2F;
        double dx = mouseX - (x + radius);
        double dy = mouseY - (y + radius);
        return dx * dx + dy * dy <= radius * radius;
    }

    private boolean inSlider(double mouseX, double mouseY)
    {
        return mouseX >= sliderX() && mouseX < sliderX() + sliderWidth
            && mouseY >= y && mouseY < y + wheelSize;
    }

    private void applyWheel(double mouseX, double mouseY)
    {
        float radius = wheelSize / 2F;
        double dx = mouseX - (x + radius);
        double dy = mouseY - (y + radius);
        hue = (float)((Math.toDegrees(Math.atan2(dy, dx)) + 360.0) % 360.0);
        // Clamped rather than ignored, so dragging past the rim pins to full
        // saturation instead of dropping the drag.
        saturation = (float)Math.min(1.0, Math.hypot(dx, dy) / radius);
    }

    private void applyValue(double mouseY)
    {
        value = 1F - (float)Math.min(1.0, Math.max(0.0, (mouseY - y) / (wheelSize - 1.0)));
    }

    /** The same conversion the generator uses, so texture and maths agree. */
    public static int hsvToRgb(float h, float s, float v)
    {
        float c = v * s;
        float x = c * (1F - Math.abs((h / 60F) % 2F - 1F));
        float m = v - c;
        float r;
        float g;
        float b;
        if(h < 60)
        {
            r = c;
            g = x;
            b = 0;
        }
        else if(h < 120)
        {
            r = x;
            g = c;
            b = 0;
        }
        else if(h < 180)
        {
            r = 0;
            g = c;
            b = x;
        }
        else if(h < 240)
        {
            r = 0;
            g = x;
            b = c;
        }
        else if(h < 300)
        {
            r = x;
            g = 0;
            b = c;
        }
        else
        {
            r = c;
            g = 0;
            b = x;
        }
        return (Math.round((r + m) * 255) << 16)
            | (Math.round((g + m) * 255) << 8)
            | Math.round((b + m) * 255);
    }
}
