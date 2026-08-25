package de.cas_ual_ty.dueldimension.duel.screen.animation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;

public abstract class Animation implements Renderable
{
    public Runnable onStart;
    public Runnable onEnd;
    
    public int tickTime;
    public int maxTickTime;
    
    public Animation(int maxTickTime)
    {
        tickTime = 0;
        this.maxTickTime = maxTickTime;
    }
    
    public Animation setOnStart(Runnable onStart)
    {
        this.onStart = onStart;
        return this;
    }
    
    public Animation setOnEnd(Runnable onEnd)
    {
        this.onEnd = onEnd;
        return this;
    }
    
    public void tick()
    {
        if(ended())
        {
            return;
        }
        
        if(tickTime == 0 && onStart != null)
        {
            onStart.run();
        }
        
        ++tickTime;
        
        if(tickTime == maxTickTime && onEnd != null)
        {
            onEnd.run();
        }
    }
    
    public boolean ended()
    {
        return tickTime >= maxTickTime;
    }
    
    @Override
    public abstract void extractRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks);
    
    /**
     * @return true if this animation works in parallel to other animations
     * @see ParallelListAnimation
     */
    public boolean worksInParallel()
    {
        return true;
    }
}