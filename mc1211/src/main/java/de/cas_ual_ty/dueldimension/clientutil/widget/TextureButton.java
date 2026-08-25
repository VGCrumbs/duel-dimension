package de.cas_ual_ty.dueldimension.clientutil.widget;

import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A standard button with an icon blitted over it.
 * <p>
 * On Forge this drew the vanilla {@code WIDGETS_LOCATION} sheet, then a region
 * of a supplied texture on top. In 26.2 the standard sprite is
 * {@code extractDefaultSprite}, and the icon becomes a {@link DdBlitUtil} blit:
 * the old texel-region arguments {@code (texX, texY, texW, texH, 256, 256)}
 * become the normalised UV window {@code texX/256 .. (texX+texW)/256}. The
 * {@code Button}'s protected constructor takes {@link Button#DEFAULT_NARRATION}.
 * <p>
 * The {@code ITooltip} constructor survives, against first instinct.
 * {@code setTooltip(Tooltip)} replaced it in vanilla and suits a button whose
 * tooltip is fixed when it is built -- but a duel screen's is not: a scroll
 * arrow explains itself differently depending on how much chat is above it, and
 * a zone's depends on what the player is dragging. So the callback stays, and
 * the widget invokes it while hovered.
 */
public class TextureButton extends Button
{
    public ResourceLocation textureLocation;

    public int texX;
    public int texY;
    public int texW;
    public int texH;

    /** Asked, while hovered, what to show. Null for a button that says nothing. */
    private ITooltip onTooltip;

    public TextureButton(int x, int y, int width, int height, Component title, OnPress pressedAction)
    {
        super(x, y, width, height, title, pressedAction, DEFAULT_NARRATION);
        textureLocation = null;
    }

    public TextureButton(int x, int y, int width, int height, Component title,
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

    public TextureButton setTexture(ResourceLocation textureLocation, int texX, int texY, int texW, int texH)
    {
        this.textureLocation = textureLocation;
        this.texX = texX;
        this.texY = texY;
        this.texW = texW;
        this.texH = texH;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks)
    {
        extractTooltip(extractor, mouseX, mouseY);
        extractDefaultSprite(extractor);

        if(textureLocation != null)
        {
            DdBlitUtil.blit(extractor, textureLocation, getX(), getY(), getWidth(), getHeight(),
                texX / 256F, texY / 256F, (texX + texW) / 256F, (texY + texH) / 256F, DdBlitUtil.NO_TINT);
        }
    }
}
