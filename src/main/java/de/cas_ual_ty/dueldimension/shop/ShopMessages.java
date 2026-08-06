package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.set.CardSet;
import de.cas_ual_ty.dueldimension.set.PackMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

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
    public record OpenShop(int points, List<ShopStock.Pack> packs)
    {
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

        public static void handle(OpenShop message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() ->
                DuelDimension.proxy.openCardShop(message.points(), message.packs()));
            context.get().setPacketHandled(true);
        }
    }

    /** Server to client: your balance changed. */
    public record SyncPoints(int points)
    {
        public static void encode(SyncPoints message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.points());
        }

        public static SyncPoints decode(FriendlyByteBuf buffer)
        {
            return new SyncPoints(buffer.readVarInt());
        }

        public static void handle(SyncPoints message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() -> DuelDimension.proxy.setDuelPoints(message.points()));
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Client to server: buy this pack.
     * <p>
     * Carries only which pack. Everything that decides whether the sale happens
     * — the price, the balance, the contents — is looked up here, so the packet
     * cannot be used to buy cheaply or to choose what comes out.
     */
    public record Buy(String code)
    {
        public static void encode(Buy message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.code(), 32);
        }

        public static Buy decode(FriendlyByteBuf buffer)
        {
            return new Buy(buffer.readUtf(32));
        }

        public static void handle(Buy message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() ->
            {
                ServerPlayer player = context.get().getSender();
                if(player == null)
                {
                    return;
                }
                sell(player, message.code());
            });
            context.get().setPacketHandled(true);
        }

        private static void sell(ServerPlayer player, String code)
        {
            CardSet set = ShopStock.setOf(code);
            if(set == null || !set.isIndependentAndItem())
            {
                return;
            }
            // Creative mode already hands out anything for nothing, so a shop
            // that still charged would be the one place in the game where it
            // did not. Checked on the SERVER against the player's real game
            // mode rather than taken from the client, which could simply claim
            // to be creative.
            boolean free = player.isCreative();
            int price = free ? 0 : ShopStock.priceOf(set);
            if(!free && !DuelPoints.spend(player, price))
            {
                player.sendSystemMessage(Component.literal("Not enough DP.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            List<ItemStack> pulled = set.open(new Random());
            if(pulled == null || pulled.isEmpty())
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
                player.getInventory().placeItemBackInInventory(card.copy());
            }

            DuelDimension.channel.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncPoints(DuelPoints.get(player)));
            if(!codes.isEmpty())
            {
                // The same reveal a physical pack uses, so buying and opening
                // look the same rather than being two different ceremonies.
                DuelDimension.channel.send(PacketDistributor.PLAYER.with(() -> player),
                    new PackMessages.OpenPack(set.name, codes, rarities));
            }
        }
    }
}
