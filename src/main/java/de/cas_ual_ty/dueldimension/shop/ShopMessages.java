package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.set.CardSet;
import de.cas_ual_ty.dueldimension.set.PackMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The shop's traffic: the stock going out, a purchase coming in.
 * <p>
 * The client is told prices and shows a balance, but decides neither. A
 * purchase names a pack and nothing else — no price, no contents — so a client
 * that asks for a pack it cannot afford, or claims a discount, is simply
 * refused by {@link Buy}.
 */
public final class ShopMessages
{
    private ShopMessages()
    {
    }

    /** Server to client: open the shop with this stock and balance. */
    public record OpenShop(int points, List<ShopStock.Pack> packs) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OpenShop> TYPE =
            DdNetwork.type("shop_open_shop");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenShop> CODEC =
            CustomPacketPayload.codec(OpenShop::encode, OpenShop::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpenShop message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.points());
            buffer.writeVarInt(message.packs().size());
            message.packs().forEach(pack -> pack.write(buffer));
        }

        public static OpenShop decode(FriendlyByteBuf buffer)
        {
            int points = buffer.readVarInt();
            int count = buffer.readVarInt();
            List<ShopStock.Pack> packs = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                packs.add(ShopStock.Pack.read(buffer));
            }
            return new OpenShop(points, packs);
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(OpenShop message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() ->
         * DuelDimension.proxy.openCardShop(message.points(), message.packs()));
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /** Server to client: your balance changed. */
    public record SyncPoints(int points) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<SyncPoints> TYPE =
            DdNetwork.type("shop_sync_points");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPoints> CODEC =
            CustomPacketPayload.codec(SyncPoints::encode, SyncPoints::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SyncPoints message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.points());
        }

        public static SyncPoints decode(FriendlyByteBuf buffer)
        {
            return new SyncPoints(buffer.readVarInt());
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(SyncPoints message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() -> DuelDimension.proxy.setDuelPoints(message.points()));
         * context.get().setPacketHandled(true);
         * }
         */
    }

    /**
     * Client to server: buy this pack.
     * <p>
     * Carries only which pack. Everything that decides whether the sale happens
     * — the price, the balance, the contents — is looked up here, so the packet
     * cannot be used to buy cheaply or to choose what comes out.
     */
    public record Buy(String code, int count) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<Buy> TYPE =
            DdNetwork.type("shop_buy");

        /**
         * Built from the encode/decode pair below rather than rewritten
         * as a composite: those two methods ARE the wire format, and
         * retyping a format is how a port quietly changes one.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Buy> CODEC =
            CustomPacketPayload.codec(Buy::encode, Buy::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        /**
         * The most packs one press can buy. A limit at all matters because the
         * count arrives from the client: without one, a single packet could ask
         * for two billion pack rolls on the server thread.
         */
        public static final int MAX_AT_ONCE = 10;

        public static void encode(Buy message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.code(), 32);
            buffer.writeVarInt(message.count());
        }

        public static Buy decode(FriendlyByteBuf buffer)
        {
            return new Buy(buffer.readUtf(32), buffer.readVarInt());
        }

        /*
         * The Forge handler. Fabric registers a receiver by direction
         * and hands it the payload and the sender, so this body belongs
         * at the registration site. Kept here until it moves, because
         * it is the record of what this message is for.
         * public static void handle(Buy message, Supplier<NetworkEvent.Context> context)
         * {
         * context.get().enqueueWork(() ->
         * {
         * ServerPlayer player = context.get().getSender();
         * if(player == null)
         * {
         * return;
         * }
         * sell(player, message.code(), message.count());
         * });
         * context.get().setPacketHandled(true);
         * }
         */

        // Public for the receiver in DdNetwork; the checks inside are what
        // keep it safe, not the visibility.
        public static void sell(ServerPlayer player, String code, int requested)
        {
            CardSet set = ShopStock.setOf(code);
            if(set == null || !set.isIndependentAndItem())
            {
                return;
            }
            // Clamped rather than refused: a count outside the range is a
            // client sending nonsense, and the sane reading of "buy 0" or "buy
            // a million" is one pack and ten respectively.
            int count = Math.max(1, Math.min(MAX_AT_ONCE, requested));

            // Creative mode already hands out anything for nothing, so a shop
            // that still charged would be the one place in the game where it
            // did not. Checked on the SERVER against the player's real game
            // mode rather than taken from the client, which could simply claim
            // to be creative.
            boolean free = player.isCreative();
            int each = free ? 0 : ShopStock.priceOf(set);
            int price = each * count;
            if(!free && !DuelPoints.spend(player, price))
            {
                player.sendSystemMessage(Component.literal("Not enough DP.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            // Every pack rolled separately, so ten packs are ten independent
            // pulls rather than one pull shown ten times.
            List<ItemStack> pulled = new ArrayList<>();
            Random random = new Random();
            for(int i = 0; i < count; i++)
            {
                List<ItemStack> one = set.open(random);
                if(one != null)
                {
                    pulled.addAll(one);
                }
            }
            if(pulled.isEmpty())
            {
                // Nothing came out, so nothing is charged. Refunding rather
                // than failing silently means a broken set costs the player
                // nothing. A creative player was never charged, so there is
                // nothing to give back.
                if(!free)
                {
                    DuelPoints.award(player, price);
                }
                player.sendSystemMessage(Component.literal("That pack is empty; you were not charged.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            List<Integer> codes = new ArrayList<>();
            List<String> rarities = new ArrayList<>();
            for(ItemStack card : pulled)
            {
                if(card.isEmpty() || !(card.getItem() instanceof de.cas_ual_ty.dueldimension.card.CardItem item))
                {
                    continue;
                }
                de.cas_ual_ty.dueldimension.card.CardHolder holder = item.getCardHolder(card);
                if(holder == null || holder.getCard() == null)
                {
                    continue;
                }
                codes.add((int)holder.getCard().getId());
                rarities.add(holder.getRarity() == null ? "" : holder.getRarity());
            }

            // Into the collection, and only there. The cards used to be dropped
            // into the player's inventory as items as well, which was how the
            // mod tracked ownership before there was a trunk; now that the
            // server holds one, the items are a second copy of the same fact
            // that fills a hotbar and can be thrown away by accident.
            codes.forEach(id -> DuelProfiles.get(player).trunk().add(id, 1));
            DuelProfiles.saveAndSync(player);

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SyncPoints(DuelPoints.get(player)));
            if(!codes.isEmpty())
            {
                // The same reveal a physical pack uses, so buying and opening
                // look the same rather than being two different ceremonies.
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new PackMessages.OpenPack(set.name, codes, rarities));
            }
        }
    }
}
