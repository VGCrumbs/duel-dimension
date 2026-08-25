package de.cas_ual_ty.dueldimension.duel;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessages;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public abstract class DuelContainer extends AbstractContainerMenu
{
    protected DuelManager duelManager;
    
    public DuelContainer(MenuType<?> type, int id, Player player, DuelManager duelManager)
    {
        super(type, id);
        this.duelManager = duelManager;
        onContainerOpened(player);
    }
    
    @Override
    public abstract boolean stillValid(Player player);
    
    public DuelManager getDuelManager()
    {
        return duelManager;
    }
    
    public void onContainerOpened(Player player)
    {
        if(player.level().isClientSide())
        {
            requestFullUpdate();
        }
        else
        {
            getDuelManager().playerOpenContainer(player);
        }
    }
    
    // make sure to only call this on client
    public void requestFullUpdate()
    {
        getDuelManager().reset();
        DuelDimension.proxy.sendDuelMessage(
            new DuelMessages.RequestFullUpdate(getDuelManager().getHeader()));
    }
    
    @Override
    public void removed(Player player)
    {
        getDuelManager().playerCloseContainer(player);
        super.removed(player);
    }
}
