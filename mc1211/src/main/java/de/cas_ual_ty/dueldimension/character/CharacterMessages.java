package de.cas_ual_ty.dueldimension.character;

import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * A character on the wire: what somebody looks like, and whether they are
 * wearing it.
 * <p>
 * <b>The look is eight small numbers, so this scales to a whole server.</b> The
 * models are in everyone's jar, so nothing is transferred but the choices — a
 * hundred players cost a hundred packets of fourteen bytes, once each, rather
 * than a hundred skins. That is why {@link CharacterLook} is shaped the way it
 * is.
 * <p>
 * An outfit pair very like this used to exist and was removed; the note in
 * {@code DdNetwork.register} still points at where it was. This is the same
 * idea done again, with the model rather than a texture swap behind it.
 */
public final class CharacterMessages
{
    private CharacterMessages()
    {
    }

    /**
     * Server to every client: this player looks like this.
     * <p>
     * Sent on join for everyone already present, and again whenever one person
     * changes — not per tick and not per frame. A client that never hears about
     * a player draws them the vanilla way, which is the right thing to do about
     * somebody who has not made a character.
     *
     * @param shown whether they have the custom model turned ON; a player may
     *              have made one and still be walking around as themselves
     */
    public record WornCharacter(UUID player, CharacterLook look, boolean shown)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<WornCharacter> TYPE =
            DdNetwork.type("character_worn");

        public static final StreamCodec<RegistryFriendlyByteBuf, WornCharacter> CODEC =
            CustomPacketPayload.codec(WornCharacter::encode, WornCharacter::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(WornCharacter message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeUUID(message.player());
            write(buffer, message.look());
            buffer.writeBoolean(message.shown());
        }

        public static WornCharacter decode(RegistryFriendlyByteBuf buffer)
        {
            UUID who = buffer.readUUID();
            CharacterLook look = read(buffer);
            return new WornCharacter(who, look, buffer.readBoolean());
        }
    }

    /**
     * Client to server: this is my character now.
     * <p>
     * The server does not trust it beyond clamping — {@link CharacterLook}'s
     * constructor does that — because there is nothing here worth cheating at.
     * A look decides what somebody looks like and nothing else.
     */
    public record SetCharacter(CharacterLook look, boolean shown)
        implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetCharacter> TYPE =
            DdNetwork.type("character_set");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetCharacter> CODEC =
            CustomPacketPayload.codec(SetCharacter::encode, SetCharacter::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SetCharacter message, RegistryFriendlyByteBuf buffer)
        {
            write(buffer, message.look());
            buffer.writeBoolean(message.shown());
        }

        public static SetCharacter decode(RegistryFriendlyByteBuf buffer)
        {
            CharacterLook look = read(buffer);
            return new SetCharacter(look, buffer.readBoolean());
        }
    }

    /**
     * The look itself, as bytes.
     * <p>
     * Written by hand rather than through a Codec because every field is one
     * byte or three, and the whole thing is eleven — a generic encoding would
     * cost more in field names than the character does in data.
     */
    private static void write(RegistryFriendlyByteBuf buffer, CharacterLook look)
    {
        buffer.writeByte(look.gender());
        buffer.writeByte(look.face());
        buffer.writeByte(look.hair());
        buffer.writeByte(look.wear());
        buffer.writeByte(look.disc());
        buffer.writeByte(look.tone());
        buffer.writeMedium(look.hairRgb());
        buffer.writeMedium(look.wearRgb());
        buffer.writeMedium(look.skinRgb());
        buffer.writeMedium(look.eyeRgb());
    }

    private static CharacterLook read(RegistryFriendlyByteBuf buffer)
    {
        char gender = (char) buffer.readByte();
        int face = buffer.readByte();
        int hair = buffer.readByte();
        int wear = buffer.readByte();
        int disc = buffer.readByte();
        int tone = buffer.readByte();
        int hairRgb = buffer.readMedium();
        int wearRgb = buffer.readMedium();
        int skinRgb = buffer.readMedium();
        int eyeRgb = buffer.readMedium();
        // The constructor clamps every one of these, so a malformed or hostile
        // packet produces an ordinary character rather than an exception on the
        // network thread.
        return new CharacterLook(gender, face, hair, wear, disc, tone, hairRgb, wearRgb,
            skinRgb, eyeRgb);
    }

    public static void register()
    {
        DdNetwork.clientbound(WornCharacter.TYPE, WornCharacter.CODEC);
        DdNetwork.serverbound(SetCharacter.TYPE, SetCharacter.CODEC);
    }
}
