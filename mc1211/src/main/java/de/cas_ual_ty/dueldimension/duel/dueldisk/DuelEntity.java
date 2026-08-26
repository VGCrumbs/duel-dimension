package de.cas_ual_ty.dueldimension.duel.dueldisk;

import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.duel.DuelManager;
import de.cas_ual_ty.dueldimension.duel.DuelState;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeader;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeaders;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class DuelEntity extends Entity implements MenuProvider
{
    public static final int MAX_TIMEOUT = 20 * 8;
    
    public DuelManager duelManager;
    
    public UUID player1UUID;
    public UUID player2UUID;
    
    public DuelEntity(EntityType<?> pType, Level level)
    {
        super(pType, level);
        duelManager = new DuelManager(level().isClientSide(), this::createHeader);
    }
    
    public DuelMessageHeader createHeader()
    {
        return new DuelMessageHeader.EntityHeader(DuelMessageHeaders.ENTITY, getId());
    }
    
    private int timeout = 0;
    private boolean hasEverStarted = false;
    
    @Override
    public void tick()
    {
        super.tick();
        
        if(!level().isClientSide())
        {
            if(!hasEverStarted)
            {
                hasEverStarted = duelManager.duelState == DuelState.DUELING;
            }
            
            if(duelManager.duelState == DuelState.IDLE && (duelManager.player1 != null || duelManager.player2 != null))
            {
                // 1 player left during deck selection
                duelManager.kickAllPlayers();
                discard();
            }
            else if(duelManager.player1 == null && duelManager.player2 == null)
            {
                if(hasEverStarted)
                {
                    // both players left after the duel was started
                    duelManager.kickAllPlayers();
                    discard();
                }
                else
                {
                    // players have not agreed to duel yet (only request sent out)
                    timeout++;
                    if(timeout >= MAX_TIMEOUT)
                    {
                        duelManager.kickAllPlayers();
                        discard();
                    }
                }
            }
            else if(duelManager.player1 != null && duelManager.player2 != null)
            {
                timeout = 0;
            }
        }
    }
    
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        // Nothing is synced: everything a client needs about a duel arrives
        // through the duel messages, not through entity data.
    }
    
    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag pCompound)
    {
    }
    
    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag pCompound)
    {
    }
    /*
     * getAddEntityPacket is gone. Forge needed NetworkHooks because vanilla's
     * spawn packet could not carry a modded entity's extra data; vanilla's own
     * ClientboundAddEntityPacket does that now, and the default implementation
     * is the right one -- a duel entity carries nothing at spawn time beyond
     * its position, which the vanilla packet already sends.
     */
    
    
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player)
    {
        return new DuelEntityContainer(DdContainerTypes.DUEL_ENTITY_CONTAINER, id, playerInv, getId(), false);
    }

    /**
     * Nothing can hurt it.
     * <p>
     * {@code hurt} became {@code hurtServer} and is abstract, so a class that
     * simply never takes damage has to say so rather than inherit it. A duel is
     * not a creature; there is nothing to damage.
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel serverLevel,
        net.minecraft.world.damagesource.DamageSource source, float amount)
    {
        return false;
    }
}
