package de.cas_ual_ty.dueldimension.duel.network;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;


public abstract class DuelMessage
{
    /*
     * Forge dispatched these through a SimpleChannel: nineteen classes
     * registered by index, each carrying its own encode, decode and handle.
     * Fabric registers a payload TYPE and a receiver for it, so the index
     * scheme moved to DuelPayloads -- which is the only thing that changed.
     * The header, the per-message encoding and handleMessage are untouched,
     * because they are the protocol and the protocol did not change.
     */
    // Server -> Client
    public abstract static class ClientBaseMessage extends DuelMessage
    {
        public ClientBaseMessage(DuelMessageHeader header)
        {
            super(header);
        }
        
        public ClientBaseMessage(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
    }
    
    // Client -> Server
    public abstract static class ServerBaseMessage extends DuelMessage
    {
        public ServerBaseMessage(DuelMessageHeader header)
        {
            super(header);
        }
        
        public ServerBaseMessage(RegistryFriendlyByteBuf buf)
        {
            super(buf);
        }
        
    }
    
    private DuelMessageHeader header;
    private DuelMessageHeader decodedHeader;

    /**
     * The header this message arrived with, which says what it is addressed to.
     * <p>
     * Forge's handler read it from the message's own field inside
     * {@code handle}. The handler lives outside the message now -- Fabric
     * registers a receiver per payload type rather than a method per message --
     * so it has to be reachable from there.
     */
    public DuelMessageHeader getDecodedHeader()
    {
        return decodedHeader;
    }
    
    public DuelMessage(DuelMessageHeader header)
    {
        this.header = header;
    }
    
    public DuelMessage(RegistryFriendlyByteBuf buf)
    {
        decodedHeader = DuelMessageUtility.decodeHeader(buf);
        decodeMessage(buf);
    }
    
    public void encode(RegistryFriendlyByteBuf buf)
    {
        DuelMessageUtility.encodeHeader(header, buf);
        encodeMessage(buf);
    }
    
    public abstract void encodeMessage(RegistryFriendlyByteBuf buf);
    
    public abstract void decodeMessage(RegistryFriendlyByteBuf buf);
    
    public abstract void handleMessage(Player player, IDuelManagerProvider provider);
    
}