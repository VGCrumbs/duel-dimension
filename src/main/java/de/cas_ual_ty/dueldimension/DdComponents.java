package de.cas_ual_ty.dueldimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * What this mod's items carry.
 * <p>
 * {@link net.minecraft.world.item.ItemStack} has no NBT any more. Where the
 * Forge build called {@code stack.getOrCreateTag()} and wrote four keys into
 * it, a stack now holds typed <em>components</em>, each with a Codec for the
 * disk and a stream codec for the wire.
 * <p>
 * That is more work to declare and better in every other way: the shape is
 * stated once instead of being implied by whichever code happened to write the
 * tag, a stack with the wrong contents is a compile error rather than a
 * silently missing key, and the client and server agree on the format by
 * construction because they share the codec.
 */
public final class DdComponents
{
    /**
     * A card on a stack: which card, which artwork, what rarity, what print.
     * <p>
     * The same four values the Forge build put in the item's tag, under the
     * same names, so the format is recognisable even though the mechanism is
     * not. The card is stored by passcode rather than as a
     * {@link de.cas_ual_ty.dueldimension.card.properties.Properties}: the
     * database is loaded from disk after the game starts, and a stack read
     * before then still has to know which card it is.
     */
    public record Card(long id, byte imageIndex, String rarity, String code)
    {
        public static final Card EMPTY = new Card(0L, (byte)0, "", "");

        public static final Codec<Card> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.LONG.optionalFieldOf(JsonKeys.ID, 0L).forGetter(Card::id),
                Codec.BYTE.optionalFieldOf(JsonKeys.IMAGE_INDEX, (byte)0)
                    .forGetter(Card::imageIndex),
                Codec.STRING.optionalFieldOf(JsonKeys.RARITY, "").forGetter(Card::rarity),
                Codec.STRING.optionalFieldOf(JsonKeys.CODE, "").forGetter(Card::code)
            ).apply(instance, Card::new));

        /**
         * The wire form. Written by hand rather than derived from the Codec
         * because this travels on every inventory update, and four primitives
         * are cheaper to send as themselves than as a tag.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Card::id,
                ByteBufCodecs.BYTE, Card::imageIndex,
                ByteBufCodecs.STRING_UTF8, Card::rarity,
                ByteBufCodecs.STRING_UTF8, Card::code,
                Card::new);

        /** The holder's four values, as a component. */
        public static Card of(CardHolder holder)
        {
            return new Card(holder.getCard() == null ? 0L : holder.getCard().getId(),
                holder.getImageIndex(), holder.getRarity(), holder.getCode());
        }
    }

    public static final DataComponentType<Card> CARD = DataComponentType.<Card>builder()
        .persistent(Card.CODEC)
        .networkSynchronized(Card.STREAM_CODEC)
        .build();

    private DdComponents()
    {
    }

    /**
     * Registered from the mod's initialiser.
     * <p>
     * Fabric has no {@code DeferredRegister}: registries are open when a mod
     * initialiser runs, so a component is registered by calling
     * {@link Registry#register} and that is the whole of it.
     */
    public static void register()
    {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "card"), CARD);
    }
}
