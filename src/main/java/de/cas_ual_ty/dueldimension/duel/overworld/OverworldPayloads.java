package de.cas_ual_ty.dueldimension.duel.overworld;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

/**
 * What the server tells a client about a duel field standing in the world.
 * <p>
 * The whole {@link FieldSiting} travels, spec included, rather than the client
 * assuming {@link FieldSpec#DEFAULT}: the field a player is walking to must be
 * the field that was validated for them, and a default that changed between two
 * builds would otherwise draw markers somewhere the server never checked.
 * <p>
 * Deliberately carries nothing about the duel itself. Cards, hands and life
 * points arrive through the existing duel packets, which already redact per
 * seat; this message is only about geometry, and geometry is public -- anyone
 * standing nearby can see where the board is.
 */
public final class OverworldPayloads
{
    private OverworldPayloads()
    {
    }

    /**
     * There is a field here, and this is your place at it.
     *
     * @param level  which world it stands in; a board drawn from memory in the
     *               wrong dimension is a board floating over unrelated ground,
     *               and the server had exactly this bug before the client did
     * @param seat   which end of the board this player belongs at
     * @param locked true once the duel has them in position, false while they
     *               still have to walk to their mark
     */
    public record ShowField(FieldSiting siting, ResourceKey<Level> level, int seat, boolean locked)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ShowField> TYPE =
            DdNetwork.type("overworld_show_field");

        public static final StreamCodec<RegistryFriendlyByteBuf, ShowField> CODEC =
            CustomPacketPayload.codec(ShowField::encode, ShowField::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(ShowField message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeBlockPos(message.siting().anchor());
            buffer.writeEnum(message.siting().facing());
            writeSpec(message.siting().spec(), buffer);
            buffer.writeResourceKey(message.level());
            buffer.writeVarInt(message.seat());
            buffer.writeBoolean(message.locked());
        }

        public static ShowField decode(RegistryFriendlyByteBuf buffer)
        {
            FieldSiting siting = new FieldSiting(buffer.readBlockPos(),
                buffer.readEnum(Direction.class), readSpec(buffer));
            ResourceKey<Level> level = buffer.readResourceKey(Registries.DIMENSION);
            // Clamped rather than trusted, like every other seat index that
            // crosses the wire: a two-seat board has seats 0 and 1, and -1 for
            // somebody who is only watching.
            int seat = Math.clamp(buffer.readVarInt(), SPECTATOR, 1);
            return new ShowField(siting, level, seat, buffer.readBoolean());
        }
    }

    /** The seat of somebody who is watching rather than playing. */
    public static final int SPECTATOR = -1;

    /**
     * The duel as a bystander may see it.
     * <p>
     * A separate message from the duellists' own {@code DuelUpdate}, carrying a
     * separately redacted board, because sending either seat's copy to a third
     * player hands a bystander that duellist's hand. The redaction is
     * {@link de.cas_ual_ty.dueldimension.ocg.prompt.StrangerView}'s job and
     * happens before this is built -- never here, where a wire format would be
     * a poor place to keep a secret.
     */
    public record SpectatorBoard(
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot board)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SpectatorBoard> TYPE =
            DdNetwork.type("overworld_spectator_board");

        public static final StreamCodec<RegistryFriendlyByteBuf, SpectatorBoard> CODEC =
            CustomPacketPayload.codec(SpectatorBoard::encode, SpectatorBoard::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SpectatorBoard message, RegistryFriendlyByteBuf buffer)
        {
            message.board().write(buffer);
        }

        public static SpectatorBoard decode(RegistryFriendlyByteBuf buffer)
        {
            return new SpectatorBoard(
                de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.read(buffer));
        }
    }

    /**
     * The field is gone: the duel ended, the board was disturbed, or it fell
     * back to the screen. Markers down, lock off, board undrawn.
     */
    public record HideField() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<HideField> TYPE =
            DdNetwork.type("overworld_hide_field");

        public static final StreamCodec<RegistryFriendlyByteBuf, HideField> CODEC =
            CustomPacketPayload.codec(HideField::encode, HideField::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(HideField message, RegistryFriendlyByteBuf buffer)
        {
        }

        public static HideField decode(RegistryFriendlyByteBuf buffer)
        {
            return new HideField();
        }
    }

    private static void writeSpec(FieldSpec spec, RegistryFriendlyByteBuf buffer)
    {
        buffer.writeVarInt(spec.areaWidth());
        buffer.writeVarInt(spec.areaDepth());
        buffer.writeVarInt(spec.clearance());
        buffer.writeFloat(spec.matScale());
        buffer.writeVarInt(spec.lateralTolerance());
        buffer.writeVarInt(spec.elevationTolerance());
        buffer.writeVarInt(spec.maxSeparation());
        buffer.writeVarInt(spec.searchRadius());
        buffer.writeVarInt(spec.verticalSearch());
    }

    /**
     * Read back and handed to the record, which clamps every field itself --
     * spans forced odd, scale capped so the board still fits the ground. A
     * packet is data, not an instruction, and the clamping lives in one place
     * so the wire cannot express a field the server could not have made.
     */
    private static FieldSpec readSpec(RegistryFriendlyByteBuf buffer)
    {
        int width = buffer.readVarInt();
        int depth = buffer.readVarInt();
        int clearance = Math.clamp(buffer.readVarInt(), 0, 63);
        float matScale = buffer.readFloat();
        int lateral = Math.clamp(buffer.readVarInt(), 0, 63);
        int elevation = Math.clamp(buffer.readVarInt(), 0, 63);
        int separation = Math.clamp(buffer.readVarInt(), 1, 255);
        int radius = Math.clamp(buffer.readVarInt(), 0, 63);
        int vertical = Math.clamp(buffer.readVarInt(), 0, 63);
        // The record clamps everything, including a NaN or an absurd scale:
        // a packet is data, and a board ten thousand blocks wide would take the
        // renderer down rather than merely look wrong.
        return new FieldSpec(width, depth, clearance,
            Float.isFinite(matScale) ? matScale : FieldSpec.DEFAULT.matScale(),
            lateral, elevation, separation, radius, vertical);
    }

}
