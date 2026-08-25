package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;


public class DisplayChatWidget extends AbstractWidget
{
    public Supplier<List<Component>> textSupplier;
    
    public DisplayChatWidget(int x, int y, int width, int height, Component title)
    {
        super(x, y, width, height, title);
        textSupplier = null;
    }
    
    /** The widget's fade-in, as the tint every draw of it carries. */
    protected int fadeTint()
    {
        return DdBlitUtil.alpha(alpha);
    }
    
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        // Forge split this in two: render() refused to draw a widget with no
        // supplier yet, and renderButton() drew it. There is one hook now, so
        // the refusal is the early return.
        if(textSupplier == null)
        {
            return;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;
        // getFGColor() is gone; it was the widget label colour, which is all
        // this ever wanted from it.
        int color = active ? 0xFFFFFF : 0xA0A0A0;
        DisplayChatWidget.drawLines(ms, fontrenderer, textSupplier.get(), getX(), getY(), getWidth(), getHeight(), (color & 0x00FFFFFF) | (fadeTint() & 0xFF000000), (float) ClientProxy.duelChatSize);
    }
    
    public DisplayChatWidget setTextSupplier(Supplier<List<Component>> textSupplier)
    {
        this.textSupplier = textSupplier;
        return this;
    }
    
    /**
     * @param color ARGB -- the fade lives in its alpha byte, so a colour without
     *              one draws nothing
     */
    public static void drawLines(GuiGraphicsExtractor ms, Font fontRenderer, List<Component> list, float x, float y, int maxWidth, float maxHeight, int color, final float downScale)
    {
        final float upScale = 1F / downScale;
        
        ms.pose().pushMatrix();
        
        ms.pose().scale(downScale, downScale);
        
        x *= upScale;
        y *= upScale;
        maxWidth = Math.round(maxWidth * upScale);
        maxHeight *= upScale;
        
        Component t;
        List<FormattedCharSequence> ps;
        FormattedCharSequence p;
        int i, j;
        
        float minY = y;
        float maxY = y + maxHeight;
        
        y = maxY - fontRenderer.lineHeight; // were in position of the last line
        
        for(i = list.size() - 1; y >= minY && i >= 0; --i)
        {
            t = list.get(i);
            
            if(t.getString().isEmpty() && t.getSiblings().isEmpty())
            {
                y -= fontRenderer.lineHeight;
            }
            else
            {
                ps = fontRenderer.split(t, maxWidth);
                
                for(j = ps.size() - 1; y >= minY && j >= 0; --j)
                {
                    p = ps.get(j);
                    // Whole pixels of the scaled space, which is finer than a
                    // screen pixel whenever the chat is shrunk.
                    ms.text(fontRenderer, p, Math.round(x), Math.round(y), color, true);
                    y -= fontRenderer.lineHeight;
                }
            }
        }
        
        ms.pose().popMatrix();
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {
    
    }
}
