package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.card.CardHolderNbt;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The card binder's traffic.
 * <p>
 * Each Forge {@code static encode/decode/handle} class became a
 * {@link CustomPacketPayload} record, as the message→payload pattern prescribes;
 * the {@code handle} bodies moved to {@link DdNetwork} where a receiver is
 * registered by direction.
 * <p>
 * {@link UpdateList} is the one that needed thought: it carried
 * {@code List<CardHolder>} through {@code DuelMessageUtility.encodeCardHolder},
 * and that helper lives in {@code duel/network} which is not ported yet. Rather
 * than pull the whole helper across for one method, the same wire shape — a
 * present flag and the holder's NBT — is inlined here, using
 * {@link CardHolderNbt#write} and the {@code CompoundTag}
 * constructor it already has.
 */
public class CardBinderMessages
{
    public static void doForBinderContainer(Player player, Consumer<CardBinderContainer> consumer)
    {
        if(player != null && player.containerMenu instanceof CardBinderContainer)
        {
            consumer.accept((CardBinderContainer) player.containerMenu);
        }
    }

    private static void encodeCardHolder(CardHolder card, FriendlyByteBuf buf)
    {
        if(card == null)
        {
            buf.writeBoolean(false);
        }
        else
        {
            buf.writeBoolean(true);
            CompoundTag nbt = new CompoundTag();
            CardHolderNbt.write(card, nbt);
            buf.writeNbt(nbt);
        }
    }

    private static CardHolder decodeCardHolder(FriendlyByteBuf buf)
    {
        return buf.readBoolean() ? CardHolderNbt.read(buf.readNbt()) : null;
    }

    // client changes page, tells server
    public record ChangePage(boolean nextPage) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ChangePage> TYPE =
            DdNetwork.type("binder_change_page");

        public static final StreamCodec<RegistryFriendlyByteBuf, ChangePage> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.BOOL.cast(), ChangePage::nextPage,
                ChangePage::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // client changes search, tells server
    public record ChangeSearch(String search) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ChangeSearch> TYPE =
            DdNetwork.type("binder_change_search");

        public static final StreamCodec<RegistryFriendlyByteBuf, ChangeSearch> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.cast(), ChangeSearch::search,
                ChangeSearch::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // update pages to client
    public record UpdatePage(int page, int maxPage) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<UpdatePage> TYPE =
            DdNetwork.type("binder_update_page");

        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePage> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.cast(), UpdatePage::page,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.cast(), UpdatePage::maxPage,
                UpdatePage::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // update cards list to client
    public record UpdateList(int page, List<CardHolder> list) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<UpdateList> TYPE =
            DdNetwork.type("binder_update_list");

        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateList> CODEC =
            CustomPacketPayload.codec(UpdateList::encode, UpdateList::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(UpdateList msg, RegistryFriendlyByteBuf buf)
        {
            buf.writeInt(msg.page());
            buf.writeInt(msg.list().size());
            for(CardHolder card : msg.list())
            {
                encodeCardHolder(card, buf);
            }
        }

        public static UpdateList decode(RegistryFriendlyByteBuf buf)
        {
            int page = buf.readInt();
            int count = buf.readInt();
            List<CardHolder> list = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                list.add(decodeCardHolder(buf));
            }
            return new UpdateList(page, list);
        }
    }

    // client clicks index, tells server
    public record IndexClicked(int index) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<IndexClicked> TYPE =
            DdNetwork.type("binder_index_clicked");

        public static final StreamCodec<RegistryFriendlyByteBuf, IndexClicked> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.cast(), IndexClicked::index,
                IndexClicked::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record IndexDropped(int index) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<IndexDropped> TYPE =
            DdNetwork.type("binder_index_dropped");

        public static final StreamCodec<RegistryFriendlyByteBuf, IndexDropped> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.cast(), IndexDropped::index,
                IndexDropped::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
