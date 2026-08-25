package de.cas_ual_ty.dueldimension.duel.screen.animation;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.resources.ResourceLocation;

public class SpecialSummonOverlayAnimation extends SpecialSummonAnimation
{
    public SpecialSummonOverlayAnimation(float centerPosX, float centerPosY, int size, int endSize)
    {
        super(centerPosX, centerPosY, size, endSize);
    }
    
    @Override
    public ResourceLocation getTexture()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/action_animations/special_summon_overlay.png");
    }
}
