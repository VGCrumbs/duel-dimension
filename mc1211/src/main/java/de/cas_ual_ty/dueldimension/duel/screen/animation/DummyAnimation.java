package de.cas_ual_ty.dueldimension.duel.screen.animation;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;

public class DummyAnimation extends Animation
{
    public DummyAnimation()
    {
        super(1);
    }
    
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

    }
}
