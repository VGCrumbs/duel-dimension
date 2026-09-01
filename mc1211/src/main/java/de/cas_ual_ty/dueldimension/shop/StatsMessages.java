package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The numbers the profile panel shows that are not DP.
 *
 * <h2>Why not on the profile sync</h2>
 * {@code DuelProfile} travels whole whenever any part of it changes, and these
 * three change at the end of every duel. Sending a trunk of several thousand
 * cards because a win counter went up would be a strange way to spend a packet.
 * <p>
 * DP is not here either: it already has {@link ShopMessages.SyncPoints}, sent
 * from every place that moves a balance, and folding it in would mean two
 * messages that both claim to be the truth about it.
 */
public final class StatsMessages
{
    private StatsMessages()
    {
    }

    /**
     * Server to client: DE and BOTH win/loss records.
     * <p>
     * Two records rather than one, because a duel against a person and a duel
     * against a bot answer different questions -- see {@link DuelRecord}. Both
     * travel together because they change together, at the end of a duel, and a
     * panel that could show one updated and the other stale would be worse than
     * one extra pair of varints.
     */
    public record Sync(int duelEnergy, int wins, int losses, int npcWins, int npcLosses)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Sync> TYPE =
            DdNetwork.type("stats_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC =
            CustomPacketPayload.codec(Sync::encode, Sync::decode);

        public Sync
        {
            duelEnergy = Math.max(0, duelEnergy);
            wins = Math.max(0, wins);
            losses = Math.max(0, losses);
            npcWins = Math.max(0, npcWins);
            npcLosses = Math.max(0, npcLosses);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        private static void encode(Sync message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.duelEnergy());
            buffer.writeVarInt(message.wins());
            buffer.writeVarInt(message.losses());
            buffer.writeVarInt(message.npcWins());
            buffer.writeVarInt(message.npcLosses());
        }

        private static Sync decode(FriendlyByteBuf buffer)
        {
            return new Sync(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
        }
    }

    /**
     * Sends a player their own numbers.
     * <p>
     * Called from every place that changes one, rather than on a timer: these
     * only move at the end of a duel and at the Monuments, and a panel showing a
     * stale balance next to a button that spends it is worse than one that
     * updates late.
     */
    public static void sync(ServerPlayer player)
    {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new Sync(DuelEnergy.get(player), DuelRecord.wins(player),
                DuelRecord.losses(player), DuelRecord.npcWins(player),
                DuelRecord.npcLosses(player)));
    }
}
