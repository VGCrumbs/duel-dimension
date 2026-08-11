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
            List<Integer> arts = new ArrayList<>();
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
                // The artwork this printing specifies, off the same holder as
                // the rarity and gathered in the same pass so the three lists
                // stay in step.
                arts.add((int)holder.getImageIndex());
            }

            // Into the collection, and only there. The cards used to be dropped
            // into the player's inventory as items as well, which was how the
            // mod tracked ownership before there was a trunk; now that the
            // server holds one, the items are a second copy of the same fact
            // that fills a hotbar and can be thrown away by accident.
            // With the rarity, which was already gathered a few lines up for
            // the opening animation and then thrown away here. The collection
            // records which printing was pulled, so a set can be completed
            // printing by printing -- and which artwork that printing prints,
            // so a copy bought as MVP1-SV5 is remembered as the one wearing
            // image 2 rather than as a generic Obelisk.
            de.cas_ual_ty.dueldimension.duel.profile.Trunk trunk =
                DuelProfiles.get(player).trunk();
            for(int i = 0; i < codes.size(); i++)
            {
                trunk.add(codes.get(i), rarities.get(i), arts.get(i), 1);
            }
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

    /**
     * Server to client: open the sleeve shop with this stock and balance.
     * <p>
     * Its own message rather than a flag on {@link OpenShop}: the two shops sell
     * different things and a payload whose meaning depends on a boolean is a
     * payload that can be sent meaninglessly. The stock is built server side and
     * sent, for the reason {@link OpenShop} does it — the shop a player sees has
     * to be the shop the server will sell from.
     */
    public record OpenSleeveShop(int points, List<ShopStock.SleeveOffer> sleeves)
        implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OpenSleeveShop> TYPE =
            DdNetwork.type("shop_open_sleeves");

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenSleeveShop> CODEC =
            CustomPacketPayload.codec(OpenSleeveShop::encode, OpenSleeveShop::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OpenSleeveShop message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.points());
            buffer.writeVarInt(message.sleeves().size());
            message.sleeves().forEach(offer -> offer.write(buffer));
        }

        public static OpenSleeveShop decode(FriendlyByteBuf buffer)
        {
            int points = buffer.readVarInt();
            int count = buffer.readVarInt();
            List<ShopStock.SleeveOffer> offers = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                offers.add(ShopStock.SleeveOffer.read(buffer));
            }
            return new OpenSleeveShop(points, offers);
        }
    }

    /**
     * Client to server: buy this sleeve.
     * <p>
     * Carries the sleeve's id and <b>nothing else</b> — no price, no count, no
     * claim about what is already owned. Everything that decides whether the
     * sale happens is looked up in {@link #sell}, so a client cannot assert what
     * it paid, and the count is absent rather than clamped because a sleeve is
     * owned or not owned; there is no second one to buy.
     */
    public record BuySleeve(String sleeve) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<BuySleeve> TYPE =
            DdNetwork.type("shop_buy_sleeve");

        public static final StreamCodec<RegistryFriendlyByteBuf, BuySleeve> CODEC =
            CustomPacketPayload.codec(BuySleeve::encode, BuySleeve::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        /** Matches the field width {@code Sleeves} ids are written at elsewhere. */
        public static final int NAME_LIMIT = 64;

        public static void encode(BuySleeve message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.sleeve(), NAME_LIMIT);
        }

        public static BuySleeve decode(FriendlyByteBuf buffer)
        {
            return new BuySleeve(buffer.readUtf(NAME_LIMIT));
        }

        /**
         * Sells a sleeve, or refuses to.
         * <p>
         * The order of the four steps is the whole safety of it:
         * <ol>
         * <li><b>Resolve the id.</b> {@code Sleeves.byName} returns null for an
         *     id this build does not have, which is a refusal rather than a
         *     substitution — the strict half of that class's deliberate
         *     lenient-on-disk/strict-on-the-wire split.</li>
         * <li><b>Check it is stock at all.</b> A free dye colour has nothing to
         *     sell and a patron's sleeve is not a product, so neither can be
         *     bought however the packet is spelt.</li>
         * <li><b>Check ownership BEFORE charging.</b> This is what stops a
         *     second click on a bought sleeve costing another 500. It has to be
         *     {@code ownsSleeve} and not the return of {@code grantSleeve},
         *     because that method answers false for a free sleeve as well as for
         *     one already held — reading its false as "already paid" would let a
         *     free sleeve take the money.</li>
         * <li><b>Charge, then grant.</b> {@link DuelPoints#spend} is the same
         *     balance check {@link Buy#sell} makes ({@code if(!free &&
         *     !DuelPoints.spend(player, price))}), and it only deducts when the
         *     balance covers the price. Granting after paying means a grant that
         *     somehow fails can be refunded; granting first would give the
         *     sleeve away when the charge failed.</li>
         * </ol>
         * The price is {@link ShopStock#priceOfSleeve}, re-derived here from the
         * sleeve alone. The number the client was shown never comes back.
         */
        public static void sell(ServerPlayer player, String name)
        {
            de.cas_ual_ty.dueldimension.card.CardSleevesType sleeve =
                de.cas_ual_ty.dueldimension.duel.profile.Sleeves.byName(name);
            if(sleeve == null
                || !de.cas_ual_ty.dueldimension.duel.profile.Sleeves.isPurchasable(sleeve))
            {
                player.sendSystemMessage(Component.literal("Those sleeves are not for sale.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            de.cas_ual_ty.dueldimension.duel.profile.DuelProfile profile =
                DuelProfiles.get(player);
            if(profile.ownsSleeve(sleeve))
            {
                // Refused before a single point moves. A client whose screen is
                // a purchase behind -- two clicks before the profile sync landed
                // -- lands here, which is exactly the case that must not charge.
                player.sendSystemMessage(Component.literal("You already own those sleeves.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            // Creative pays nothing, as it does for packs: the shop is not the
            // one place in the game where creative mode still costs something.
            // Read from the player's real game mode on the SERVER, never taken
            // from a client that could simply claim it.
            boolean free = player.isCreative();
            int price = free ? 0 : ShopStock.priceOfSleeve(sleeve);
            if(!free && !DuelPoints.spend(player, price))
            {
                player.sendSystemMessage(Component.literal("Not enough DP.")
                    .withStyle(ChatFormatting.RED));
                return;
            }

            if(!profile.grantSleeve(sleeve))
            {
                // Unreachable through the checks above -- ownership was tested a
                // few lines up and this is the server thread, so nothing has run
                // in between. Handled anyway because the alternative to an
                // impossible refund is an impossible theft.
                if(!free)
                {
                    DuelPoints.award(player, price);
                }
                player.sendSystemMessage(Component.literal("Those sleeves could not be added; "
                    + "you were not charged.").withStyle(ChatFormatting.RED));
                return;
            }

            // The grant is on the profile, so it is the profile that has to be
            // written and re-sent; the picker in the deck editor reads its owned
            // set, which is how the new sleeve becomes selectable there without
            // a message of its own.
            DuelProfiles.saveAndSync(player);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new SyncPoints(DuelPoints.get(player)));
        }
    }
}
