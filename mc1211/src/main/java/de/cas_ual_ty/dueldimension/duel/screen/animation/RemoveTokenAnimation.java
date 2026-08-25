package de.cas_ual_ty.dueldimension.duel.screen.animation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.ClientProxy;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.resources.ResourceLocation;

public class RemoveTokenAnimation extends Animation
{
    public float centerPosX;
    public float centerPosY;
    public int size;
    public int endSize;
    
    public RemoveTokenAnimation(float centerPosX, float centerPosY, int size, int endSize)
    {
        super(ClientProxy.specialAnimationLength);
        
        this.centerPosX = centerPosX;
        this.centerPosY = centerPosY;
        this.size = size;
        this.endSize = endSize;
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        double relativeTickTime = (tickTime + partialTicks) / maxTickTime;
        
        // [0, 1/2pi]
        double cosTime1 = 0.5D * Math.PI * relativeTickTime;
        // [0, 1]
        float alpha = (float) (Math.cos(cosTime1));
        
        float size = (float) relativeTickTime * (endSize - this.size) + this.size;
        float halfSize = 0.5F * size;
        
        ms.pose().pushMatrix();
        
        ms.pose().translate(centerPosX, centerPosY);
        
        // The fade was a shader colour set before the draw and the blend was
        // the default translucent one. Both are the blit's own business now:
        // the alpha is its tint, and the pipeline already blends this way.
        DdBlitUtil.fullBlit(ms, getTexture(), Math.round(-halfSize), Math.round(-halfSize),
            Math.round(size), Math.round(size), DdBlitUtil.alpha(alpha));
        
        
        ms.pose().popMatrix();
    }
    
    public ResourceLocation getTexture()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/action_animations/remove_token.png");
    }
}
