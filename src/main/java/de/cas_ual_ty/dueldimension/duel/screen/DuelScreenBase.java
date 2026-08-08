package de.cas_ual_ty.dueldimension.duel.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    protected void extractLabels(GuiGraphicsExtractor ms, int x, int y)
    {
        ms.text(font, "Waiting for server...", 8, 6, 0xFF404040, false);
    }
}
