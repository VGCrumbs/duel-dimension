package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.duel.screen.animation.Animation;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.LinkedList;
import java.util.Queue;

public class AnimationsWidget extends AbstractWidget
{
    public Queue<Animation> animations;
    
    public AnimationsWidget(int x, int y, int width, int height)
    {
        super(x, y, width, height, Component.empty());
        animations = new LinkedList<>();
    }
    
    public void addAnimation(Animation animation)
    {
        animations.add(animation);
    }
    
    public void forceFinish()
    {
        Animation a;
        while(!animations.isEmpty())
        {
            a = animations.poll();
            
            while(!a.ended())
            {
                a.tick();
            }
        }
    }
    
    /** The widget's fade-in, as the tint every draw of it carries. */
    protected int fadeTint()
    {
        return DdBlitUtil.alpha(alpha);
    }
    
    @Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 describes a widget into a render state; 1.21.1 draws it now. The
        // body below is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor ms = new de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor(vanillaGraphics);
        // PORT-NOTE: the fade does not reach the animations. On Forge the
        // setShaderColor(1F, 1F, 1F, alpha) here tinted everything they drew
        // afterwards; Animation.extractRenderState takes no tint, and each
        // subclass now chooses its own colour per draw, so there is nowhere to
        // hand fadeTint() to. Nothing is lost today -- no caller ever calls
        // setAlpha on this widget, so alpha is always 1 and the old line was
        // only resetting the global colour a previous widget had left behind.
        // Giving this widget a real fade means giving Animation a tint
        // parameter and threading it through all eleven subclasses.
        if(visible)
        {
            for(Animation a : animations)
            {
                a.render(ms.vanilla(), mouseX, mouseY, partialTicks);
            }
        }
    }
    
    public void tick()
    {
        if(!animations.isEmpty())
        {
            Animation a = animations.element();
            
            a.tick();
            
            if(a.ended())
            {
                animations.poll();
            }
        }
    }
    
    public void onInit()
    {
        Animation a;
        
        while(animations.size() > 0)
        {
            a = animations.poll();
            
            while(!a.ended())
            {
                a.tick();
            }
        }
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {
    
    }
}
