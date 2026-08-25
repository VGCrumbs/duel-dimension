package de.cas_ual_ty.dueldimension.duel.screen.animation;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DummyAnimation extends Animation
{
    public DummyAnimation()
    {
        super(1);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
    }
}
