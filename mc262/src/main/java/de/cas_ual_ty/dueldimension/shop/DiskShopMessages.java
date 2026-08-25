package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem;
import de.cas_ual_ty.dueldimension.duel.profile.DuelDisks;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel disk shop's traffic, and the hub's way in to all three shops.
 * <p>
 * Shaped exactly like the sleeve shop's: the server sends the stock and the
 * balance together, and the client can only ask to buy something by name. What
 * a disk costs and whether it is owned are answered on the side that holds the
 * money, so this screen cannot ask for a discount.
 */
public final class DiskShopMessages
{
    private static final int NAME_LIMIT = 64;

    private DiskShopMessages()
    {
    }

    /**
     * Client to server: open one of the shops from the Duel Hub.
     * <p>
     * The shops were reachable only by standing at their blocks, which is fine
     * for a shop you walk to and useless for a hub tab. The kinds are named
     * rather than numbered for the usual reason -- a number is a promise about
     * an order that will change.
     */
    public record RequestShop(String kind) implements CustomPacketPayload
    {
        public static final String CARDS = "cards";
        public static final String SLEEVES = "sleeves";
        public static final String DECK_BOXES = "deck_boxes";
        public static final String DISKS = "disks";

        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<RequestShop> TYPE =
            DdNetwork.type("shop_request");

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestShop> CODEC =
            CustomPacketPayload.codec(RequestShop::encode, RequestShop::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(RequestShop message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.kind(), 16);
        }

        public static RequestShop decode(FriendlyByteBuf buffer)
        {
            return new RequestShop(buffer.readUtf(16));
        }

        /** Answers with the stock for that shop, or ignores an unknown kind. */
        public static void open(ServerPlayer player, String kind)
        {
            if(player == null || kind == null)
            {
                return;
            }
            switch(kind)
            {
                case CARDS -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new ShopMessages.OpenShop(DuelPoints.get(player),
                        ShopStock.available()));
                case SLEEVES -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new ShopMessages.OpenSleeveShop(DuelPoints.get(player),
                        ShopStock.sleeves()));
                case DECK_BOXES -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new ShopMessages.OpenDeckBoxShop(DuelPoints.get(player),
                        ShopStock.deckBoxes()));
                case DISKS -> send(player);
                default ->
                {
                    // A kind this build does not have is a client asserting
                    // something; refused silently rather than opening the wrong
                    // shop, which would be worse than opening none.
                }
            }
        }
    }

    /** Sends the disk shop's stock, the balance, and what this player owns. */
    public static void send(ServerPlayer player)
    {
        DuelProfile profile = DuelProfiles.get(player);
        List<String> owned = new ArrayList<>(profile.ownedDisks());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new OpenDiskShop(DuelPoints.get(player), ShopStock.disks(), owned,
                profile.activeDisk()));
    }

    /**
     * Server to client: the disk shop.
     *
     * @param owned  what this player already has, so the grid can mark it
     *               without guessing from the price
     * @param active which one is worn
     */
    public record OpenDiskShop(int points, List<ShopStock.DiskOffer> disks, List<String> owned,
        String active) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OpenDiskShop> TYPE =
            DdNetwork.type("shop_open_disks");

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDiskShop> CODEC =
            CustomPacketPayload.codec(OpenDiskShop::encode, OpenDiskShop::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpenDiskShop message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.points());
            buffer.writeVarInt(message.disks().size());
            message.disks().forEach(offer -> offer.write(buffer));
            buffer.writeVarInt(message.owned().size());
            message.owned().forEach(name -> buffer.writeUtf(name, NAME_LIMIT));
            buffer.writeUtf(message.active(), NAME_LIMIT);
        }

        public static OpenDiskShop decode(FriendlyByteBuf buffer)
        {
            int points = buffer.readVarInt();
            int count = buffer.readVarInt();
            List<ShopStock.DiskOffer> offers = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                offers.add(ShopStock.DiskOffer.read(buffer));
            }
            int ownedCount = buffer.readVarInt();
            List<String> owned = new ArrayList<>(ownedCount);
            for(int i = 0; i < ownedCount; i++)
            {
                owned.add(buffer.readUtf(NAME_LIMIT));
            }
            return new OpenDiskShop(points, offers, owned, buffer.readUtf(NAME_LIMIT));
        }
    }

    /** Client to server: buy this disk. */
    public record BuyDisk(String disk) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<BuyDisk> TYPE =
            DdNetwork.type("shop_buy_disk");

        public static final StreamCodec<RegistryFriendlyByteBuf, BuyDisk> CODEC =
            CustomPacketPayload.codec(BuyDisk::encode, BuyDisk::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(BuyDisk message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.disk(), NAME_LIMIT);
        }

        public static BuyDisk decode(FriendlyByteBuf buffer)
        {
            return new BuyDisk(buffer.readUtf(NAME_LIMIT));
        }

        /**
         * Sells a disk, or refuses to. The order of the steps is the safety of
         * it, and it is the sleeve shop's order for the same reasons: resolve
         * the id strictly, refuse what is not stock, refuse what is already
         * owned BEFORE any points move, then charge, then grant -- refunding if
         * the grant somehow fails, because the alternative to an impossible
         * refund is an impossible theft.
         */
        public static void sell(ServerPlayer player, String name)
        {
            if(!DuelDisks.isPurchasable(name))
            {
                player.sendSystemMessage(Component.literal("That disk is not for sale.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            DuelProfile profile = DuelProfiles.get(player);
            if(profile.ownsDisk(name))
            {
                player.sendSystemMessage(Component.literal("You already own that disk.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            boolean free = player.isCreative();
            int price = free ? 0 : ShopStock.priceOfDisk(name);
            if(!free && !DuelPoints.spend(player, price))
            {
                player.sendSystemMessage(Component.literal("Not enough DP.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            if(!profile.grantDisk(name))
            {
                if(!free)
                {
                    DuelPoints.award(player, price);
                }
                player.sendSystemMessage(Component.literal(
                    "That disk could not be added; you were not charged.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            // Bought disks are worn immediately: nobody buys a disk to leave it
            // in a drawer, and it saves a second click on the one screen where
            // the player has already said what they want.
            profile.setActiveDisk(name);
            DuelProfiles.save(player);
            de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player);
            send(player);
        }
    }

    /**
     * Client to server: buy a starter deck.
     * <p>
     * Lives here beside the disk shop rather than in a file of its own because
     * it is the same three-line transaction against the same balance, and the
     * order of its checks is the safety of it -- see {@link BuyDisk#sell}.
     */
    public record BuyStarter(String starter) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<BuyStarter> TYPE =
            DdNetwork.type("shop_buy_starter");

        public static final StreamCodec<RegistryFriendlyByteBuf, BuyStarter> CODEC =
            CustomPacketPayload.codec(BuyStarter::encode, BuyStarter::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(BuyStarter message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.starter(), NAME_LIMIT);
        }

        public static BuyStarter decode(FriendlyByteBuf buffer)
        {
            return new BuyStarter(buffer.readUtf(NAME_LIMIT));
        }

        /** What a starter deck costs. One figure, so shown and charged agree. */
        public static int price(String id)
        {
            return de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.isPurchasable(id)
                ? 2000 : 0;
        }

        public static void sell(ServerPlayer player, String id)
        {
            if(!de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.isPurchasable(id))
            {
                player.sendSystemMessage(Component.literal("That deck is not for sale.")
                    .withStyle(ChatFormatting.RED));
                return;
            }
            DuelProfile profile = DuelProfiles.get(player);
            if(profile.unlockedStructures().contains(id))
            {
                player.sendSystemMessage(Component.literal("You already own that deck.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            boolean free = player.isCreative();
            int cost = free ? 0 : price(id);
            if(!free && !DuelPoints.spend(player, cost))
            {
                player.sendSystemMessage(Component.literal("Not enough DP.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            boolean granted;
            try
            {
                de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.Entry entry =
                    de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.byId(id);
                de.cas_ual_ty.dueldimension.ocg.deck.YdkDeck deck = entry.load();
                granted = profile.unlockStarterDeck(entry.id(), entry.displayName(),
                    deck.main(), deck.extra(), deck.side());
            }
            catch(Exception unavailable)
            {
                granted = false;
            }
            if(!granted)
            {
                // Refunded rather than swallowed: the alternative to an
                // impossible refund is an impossible theft.
                if(!free)
                {
                    DuelPoints.award(player, cost);
                }
                player.sendSystemMessage(Component.literal(
                    "That deck could not be added; you were not charged.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            DuelProfiles.save(player);
            de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player);
            player.sendSystemMessage(Component.literal("Unlocked "
                + de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.byId(id).displayName() + ".")
                .withStyle(ChatFormatting.GREEN));
        }
    }

    /** Client to server: wear this disk. Believed only if they own it. */
    public record SetActiveDisk(String disk) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<SetActiveDisk> TYPE =
            DdNetwork.type("shop_active_disk");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetActiveDisk> CODEC =
            CustomPacketPayload.codec(SetActiveDisk::encode, SetActiveDisk::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(SetActiveDisk message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.disk(), NAME_LIMIT);
        }

        public static SetActiveDisk decode(FriendlyByteBuf buffer)
        {
            return new SetActiveDisk(buffer.readUtf(NAME_LIMIT));
        }

        public static void apply(ServerPlayer player, String name)
        {
            DuelProfile profile = DuelProfiles.get(player);
            if(!profile.setActiveDisk(name))
            {
                // Not owned, or not a disk at all. Refused, and the shop is
                // re-sent so the client's idea of what is worn is corrected
                // rather than left showing a choice the server did not make.
                send(player);
                return;
            }
            // Nothing to swap: the slot holds a NAME, so choosing a different
            // disk is already the whole change. What the player is wearing
            // cannot disagree with what they chose.
            DuelProfiles.save(player);
            de.cas_ual_ty.dueldimension.net.ProfilePayloads.sync(player);
            send(player);
        }
    }
}
