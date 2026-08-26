package de.cas_ual_ty.dueldimension.duel.screen.animation;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;

import java.util.LinkedList;
import java.util.List;

public class ParallelListAnimation extends Animation
{
    public final List<Animation> animations;
    public final List<Runnable> runnablesOnStart;
    public final List<Runnable> runnablesOnEnd;
    
    public ParallelListAnimation(List<Animation> animations)
    {
        super(1);
        
        this.animations = animations;
        
        runnablesOnStart = new LinkedList<>();
        runnablesOnEnd = new LinkedList<>();
        
        maxTickTime = 1;
        
        for(Animation a : this.animations)
        {
            if(!a.worksInParallel())
            {
                throw new IllegalArgumentException("ParallelListAnimation cannot accept the following animation: " + a.getClass() + " " + a.toString());
            }
            
            maxTickTime = Math.max(maxTickTime, a.maxTickTime);
            
            if(a.onStart != null)
            {
                runnablesOnStart.add(a.onStart);
                a.onStart = null;
            }
            
            if(a.onEnd != null)
            {
                runnablesOnEnd.add(a.onEnd);
                a.onEnd = null;
            }
        }
        
        onStart = () ->
        {
            for(Runnable r : runnablesOnStart)
            {
                r.run();
            }
        };
        
        onEnd = () ->
        {
            for(Runnable r : runnablesOnEnd)
            {
                r.run();
            }
        };
    }
    
    @Override
    public Animation setOnStart(Runnable onStart)
    {
        runnablesOnStart.add(onStart);
        return this;
    }
    
    @Override
    public Animation setOnEnd(Runnable onEnd)
    {
        runnablesOnEnd.add(onEnd);
        return this;
    }
    
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

        for(Animation a : animations)
        {
            if(!a.ended())
            {
                a.render(ms.vanilla(), mouseX, mouseY, partialTicks);
            }
        }
    }
    
    @Override
    public void tick()
    {
        super.tick();
        
        for(Animation a : animations)
        {
            a.tick();
        }
    }
}
