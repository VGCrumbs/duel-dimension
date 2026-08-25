package de.cas_ual_ty.dueldimension.clientutil.widget;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A red or blue button plate.
 * <p>
 * The Forge version stitched {@code colored_button.png} into a button shape in
 * {@code renderButton(PoseStack)} and drew its own centred label and tooltip on
 * top. In 26.2 a button describes itself through the abstract
 * {@code extractContents}, and the plate's four blits become {@link DdBlitUtil}
 * calls with the disabled alpha carried in the tint. {@code Button}'s public
 * constructor is gone; the protected one takes a narration supplier, so the mod
 * passes {@link Button#DEFAULT_NARRATION}. The label is centred by hand here to
 * keep the exact placement the Forge plate used.
 * <p>
 * The {@code ITooltip} constructor survives for the same reason
 * {@link TextureButton}'s does: vanilla's replacement {@code setTooltip(Tooltip)}
 * fixes the text when the widget is built, but the duel screen's phase arrows
 * name the phase they would move to, which changes with every turn.
 */
public class ColoredButton extends Button
{
    public static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/colored_button.png");

    public int offset;

    /** Asked, while hovered, what to show. Null for a button that says nothing. */
    private ITooltip onTooltip;

    public ColoredButton(int x, int y, int width, int height, Component title, OnPress pressedAction)
    {
        super(x, y, width, height, title, pressedAction, DEFAULT_NARRATION);
        offset = 0;
    }

    public ColoredButton(int x, int y, int width, int height, Component title,
        OnPress pressedAction,
        ITooltip onTooltip)
    {
        this(x, y, width, height, title, pressedAction);
        this.onTooltip = onTooltip;
    }

    /**
     * Lets the tooltip callback speak, if the mouse is here.
     * <p>
     * Called from {@code extractContents}, which sounds like the wrong place for
     * something that must appear above everything else -- and would be, if it
     * drew. It does not: a tooltip is set for the <em>next</em> frame, so where
     * in this frame's order it was asked for makes no difference.
     */
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if(onTooltip != null && isHovered())
        {
            onTooltip.onTooltip(this, graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks)
    {
        extractTooltip(extractor, mouseX, mouseY);

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

    private int getFGColor()
    {
        return 16777215;
    }

    public ColoredButton setBlue()
    {
        offset = 0;
        return this;
    }

    public ColoredButton setRed()
    {
        offset = 60;
        return this;
    }
}
