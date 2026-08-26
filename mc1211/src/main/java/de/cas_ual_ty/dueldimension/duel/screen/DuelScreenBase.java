package de.cas_ual_ty.dueldimension.duel.screen;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.duel.DuelContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DuelScreenBase<E extends DuelContainer> extends DuelContainerScreen<E>
{
    public DuelScreenBase(E screenContainer, Inventory inv, Component titleIn)
    {
        super(screenContainer, inv, titleIn);
    }
    
    @Override
    protected void renderLabels(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int x, int y)
    {
        // 26.2 describes a screen into a render state; 1.21.1 draws it now. The
        // body below is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor ms = new de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor(vanillaGraphics);
        ms.text(font, "Waiting for server...", 8, 6, 0xFF404040, false);
    }
}
