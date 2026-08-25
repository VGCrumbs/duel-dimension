package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Network representation of the server's committed post-duel award. */
public final class DuelRewardMessages
{
    private DuelRewardMessages()
    {
    }

    public record Result(UUID rewardId, DuelReward.Outcome outcome, List<DuelReward.Line> lines,
        int total, int previousBalance, int newBalance, int games, int myWins, int theirWins,
        boolean npcDuel) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Result> TYPE =
            DdNetwork.type("duel_reward_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC =
            CustomPacketPayload.codec(Result::encode, Result::decode);

        public Result
        {
            rewardId = rewardId == null ? new UUID(0, 0) : rewardId;
            outcome = outcome == null ? DuelReward.Outcome.DRAW : outcome;
            lines = lines == null ? List.of() : List.copyOf(lines);
            total = Math.max(0, total);
            previousBalance = Math.max(0, previousBalance);
            newBalance = Math.max(0, newBalance);
            games = Math.max(1, games);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        private static void encode(Result message, FriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.rewardId());
            buffer.writeEnum(message.outcome());
            buffer.writeVarInt(message.lines().size());
            for(DuelReward.Line line : message.lines())
            {
                buffer.writeUtf(line.id(), 64);
                buffer.writeUtf(line.label(), 96);
                buffer.writeVarInt(line.amount());
            }
            buffer.writeVarInt(message.total());
            buffer.writeVarInt(message.previousBalance());
            buffer.writeVarInt(message.newBalance());
            buffer.writeVarInt(message.games());
            buffer.writeVarInt(message.myWins());
            buffer.writeVarInt(message.theirWins());
            buffer.writeBoolean(message.npcDuel());
        }

        private static Result decode(FriendlyByteBuf buffer)
        {
            UUID id = buffer.readUUID();
            DuelReward.Outcome outcome = buffer.readEnum(DuelReward.Outcome.class);
            int count = Math.max(0, Math.min(128, buffer.readVarInt()));
            List<DuelReward.Line> lines = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                lines.add(new DuelReward.Line(buffer.readUtf(64), buffer.readUtf(96),
                    buffer.readVarInt()));
            }
            return new Result(id, outcome, lines, buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean());
        }
    }
}
