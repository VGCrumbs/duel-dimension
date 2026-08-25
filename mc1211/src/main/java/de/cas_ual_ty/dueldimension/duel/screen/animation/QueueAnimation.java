package de.cas_ual_ty.dueldimension.duel.screen.animation;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;

import java.util.Queue;

public class QueueAnimation extends Animation
{
    public final Queue<Animation> animations;
    
    private Runnable replacementOnStart;
    private Runnable replacementOnEnd;
    
    public QueueAnimation(Queue<Animation> animations)
    {
        super(1);
        
        this.animations = animations;
        
        maxTickTime = 0;
        
        for(Animation a : this.animations)
        {
            maxTickTime += a.maxTickTime;
        }
        
        onStart = () ->
        {
            throw new RuntimeException();
        };
        onEnd = () ->
        {
            throw new RuntimeException();
        };
    }
    
    public QueueAnimation setOnStartAlt(Runnable onStart)
    {
        replacementOnStart = onStart;
        return this;
    }
    
    public QueueAnimation setOnEndAlt(Runnable onEnd)
    {
        replacementOnEnd = onEnd;
        return this;
    }
    
    @Override
    public Animation setOnStart(Runnable onStart)
    {
        throw new RuntimeException();
    }
    
    @Override
    public Animation setOnEnd(Runnable onEnd)
    {
        throw new RuntimeException();
    }
    
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

        if(!animations.isEmpty())
        {
            Animation a = animations.peek();
            a.extractRenderState(ms, mouseX, mouseY, partialTicks);
        }
    }
    
    @Override
    public void tick()
    {
        if(ended())
        {
            return;
        }
        
        if(tickTime == 0 && replacementOnStart != null)
        {
            replacementOnStart.run();
        }
        
        ++tickTime;
        
        if(!animations.isEmpty())
        {
            Animation a = animations.peek();
            a.tick();
            
            if(a.ended())
            {
                animations.poll();
            }
        }
        
        if(tickTime == maxTickTime && replacementOnEnd != null)
        {
            replacementOnEnd.run();
        }
    }
    
    @Override
    public boolean worksInParallel()
    {
        return false;
    }
}
