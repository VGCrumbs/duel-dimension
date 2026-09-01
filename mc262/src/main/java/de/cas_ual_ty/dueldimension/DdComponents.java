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

    /**
     * The card slots a container-item (deck box, card set, simple binder) holds.
     * <p>
     * On Forge these lived in a {@code CARD_ITEM_INVENTORY} capability attached
     * to the stack — live storage, so a menu mutating the handler persisted
     * automatically, and {@code getShareTag}/{@code readShareTag} carried it to
     * the client. Neither exists here: the slots are a component (a plain list
     * of stacks), it syncs like every other component with no share-tag
     * override, and because a component is immutable the mutation has to be
     * written back. {@link de.cas_ual_ty.dueldimension.util.YDMItemHandler#boundTo}
     * does exactly that — it seeds a handler from this component and writes the
     * handler back into the stack on every change, restoring the "the handler is
     * the storage" feel the capability had.
     */
    public static final DataComponentType<java.util.List<net.minecraft.world.item.ItemStack>>
        CARD_INVENTORY = DataComponentType
            .<java.util.List<net.minecraft.world.item.ItemStack>>builder()
            .persistent(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf())
            .networkSynchronized(net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC)
            .build();

    /**
     * Which card set a set-item (sealed or opened pack) is.
     * <p>
     * The Forge build wrote a single string — the set's code — into the stack's
     * tag under {@link JsonKeys#CODE}. {@link net.minecraft.world.item.ItemStack}
     * has no tag, so that one string is its own component now: a plain
     * {@code String}, defaulting to empty so a stack with no set reads as the
     * dummy set, exactly as an empty tag key did.
     */
    public static final DataComponentType<String> SET_CODE = DataComponentType.<String>builder()
        .persistent(Codec.STRING)
        .networkSynchronized(ByteBufCodecs.STRING_UTF8.cast())
        .build();

    /**
     * Which set each card in an opened pack came out of, parallel to
     * {@link #CARD_INVENTORY}.
     * <p>
     * A tin is several real booster packs in a box, and the reveal shows its
     * cards under the pack each came from. The pull that knows this happens
     * when the pack is UNSEALED, and the cards then sit on the stack until the
     * player opens the screen — so the answer has to be written down at the
     * same moment the cards are, or it is gone. A card carries no record of the
     * pack it was in, and the client cannot reconstruct one.
     * <p>
     * Absent on every pack rolled before this existed, and on every ordinary
     * pack, where it would only repeat the product's own code. The reveal reads
     * a missing or short list as "the product itself", which is the ungrouped
     * layout it always had.
     */
    public static final DataComponentType<java.util.List<String>> PACK_SOURCES =
        DataComponentType.<java.util.List<String>>builder()
            .persistent(Codec.STRING.listOf())
            .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
            .build();

    /**
     * A card binder's id.
     * <p>
     * The binder's cards are a server-side collection keyed by this id — never
     * on the stack — so this one value is the whole of what the stack carries.
     * On Forge it lived in a {@code UUID_HOLDER} capability with a
     * {@code getShareTag}/{@code readShareTag} pair to sync it; a component syncs
     * on its own, so it replaces both. Persisted with
     * {@link net.minecraft.core.UUIDUtil#CODEC} and synced with
     * {@link net.minecraft.core.UUIDUtil#STREAM_CODEC}, the same encodings the
     * card-inventory managers already use for a UUID.
     */
    public static final DataComponentType<java.util.UUID> BINDER_UUID =
        DataComponentType.<java.util.UUID>builder()
            .persistent(net.minecraft.core.UUIDUtil.CODEC)
            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
            .build();

    /**
     * Who a duel disk has challenged, and which duel it belongs to.
     * <p>
     * Two UUIDs the disk used to keep in its item tag. An {@code ItemStack} has
     * no tag any more, so each is its own component -- which is better than the
     * tag was: a component is typed, so "duel_player2" cannot quietly be read
     * back as something else, and it syncs to the client without the disk
     * having to say so.
     */
    public static final DataComponentType<java.util.UUID> DUEL_PLAYER2 =
        DataComponentType.<java.util.UUID>builder()
            .persistent(net.minecraft.core.UUIDUtil.CODEC)
            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
            .build();

    public static final DataComponentType<java.util.UUID> DUEL_MANAGER =
        DataComponentType.<java.util.UUID>builder()
            .persistent(net.minecraft.core.UUIDUtil.CODEC)
            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
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
    /**
     * Which program a picked-up Duel Bot was running, and which deck within it.
     * <p>
     * Two plain strings, in the shape {@link #SET_CODE} already uses. They live
     * on the stack so that picking a bot up and putting it down again is not a
     * reset: a bot set to a particular structure deck, pocketed and placed in
     * another room is still running that deck.
     * <p>
     * A custom deck is stored by NAME rather than by its cards. The deck belongs
     * to a player and they may edit it between duels; a copy taken at pickup
     * would be a deck that silently stopped matching the one in their editor.
     */
    public static final DataComponentType<String> BOT_PROGRAM =
        DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8.cast())
            .build();

    public static final DataComponentType<String> BOT_DECK =
        DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8.cast())
            .build();

    public static void register()
    {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "card"), CARD);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "bot_program"), BOT_PROGRAM);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "bot_deck"), BOT_DECK);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "card_inventory"), CARD_INVENTORY);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "set_code"), SET_CODE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "pack_sources"), PACK_SOURCES);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "binder_uuid"), BINDER_UUID);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "duel_player2"), DUEL_PLAYER2);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "duel_manager"), DUEL_MANAGER);
    }
}
