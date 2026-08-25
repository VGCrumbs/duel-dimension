package de.cas_ual_ty.dueldimension.clientutil.widget;

import net.minecraft.client.Minecraft;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * A ready-check box: the standard button plate with a tick drawn when checked.
 * <p>
 * The Forge class overrode {@code render} only to set {@code active} from a
 * supplier before drawing, then hand-drew two pieces of the vanilla button
 * sheet in {@code renderButton} and a tick on top. In 26.2 {@code render} is
 * gone and {@code extractRenderState} is final, so the {@code active} toggle
 * moves to the top of {@code extractContents} — it runs in the same frame, so
 * the effect is identical. The plate is the standard {@code extractDefaultSprite}
 * (the two-piece sheet blit reproduced that same look), and the base's default
 * label is skipped because the title is empty and the tick is drawn by hand.
 * {@code Button}'s protected constructor takes {@link Button#DEFAULT_NARRATION}.
 */
public class ReadyCheckboxWidget extends Button
{
    public Supplier<Boolean> isChecked;
    public Supplier<Boolean> isActive;

    public ReadyCheckboxWidget(int xIn, int yIn, int widthIn, int heightIn, String msg, OnPress onPress, Supplier<Boolean> isChecked, Supplier<Boolean> isActive)
    {
        super(xIn, yIn, widthIn, heightIn, Component.empty(), onPress, DEFAULT_NARRATION);
        this.isChecked = isChecked;
        this.isActive = isActive;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partial)
    {
        active = isActive.get();
        Minecraft minecraft = Minecraft.getInstance();
        extractDefaultSprite(extractor);

        if(isChecked.get())
        {
            int j = getFGColor();
            extractor.centeredText(minecraft.font, Component.literal("✔"), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, j | Mth.ceil(alpha * 255.0F) << 24);
        }
    }

    private int getFGColor()
    {
        return 16777215;
    }
}
