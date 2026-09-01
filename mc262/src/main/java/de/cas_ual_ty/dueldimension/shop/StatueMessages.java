package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Choosing a god on the Dawn of Destiny reward screen, and the cards it pays.
 *
 * <h2>Why the server rolls</h2>
 * The client says only WHICH of the three was picked. Everything else -- what
 * the pool is, what comes out of it, and what lands in the trunk -- happens
 * here. A client that could name its own cards is a client that can grant
 * itself any card, which is the same rule the shop already follows.
 */
public final class StatueMessages
{
    /**
     * How many cards a choice pays. Always this many, never fewer.
     * <p>
     * The reward used to be one pack, which meant the count varied by set and a
     * generous set overflowed the screen. Five is a row that fits at a size
     * worth looking at, and a fixed number means the layout is a layout rather
     * than a guess.
     */
    private static final int MAX_CARDS = 5;

    /**
     * Setcode to the card codes carrying it, built once.
     * <p>
     * Guarded by this class's monitor and not mutated after publication.
     * Building it walks the whole card database against the engine's cdb, which
     * is not something to do per reward.
     */
    private static Map<Integer, List<Integer>> archetypes;

    private StatueMessages()
    {
    }

    /**
     * Every archetype with at least {@link #MAX_CARDS} cards in it.
     *
     * <h2>Where an archetype comes from</h2>
     * <b>Not from the card name.</b> The mod's own {@code Properties} has no
     * archetype field at all -- name, id, text, type and artwork, and nothing
     * that groups cards -- so grouping by a name prefix would be inventing a
     * rule, and it would get "Blue-Eyes White Dragon" right while getting most
     * of the interesting cases wrong.
     * <p>
     * It comes from the ENGINE. Every card in {@code cards.cdb} carries up to
     * four 16-bit setcodes packed into one 64-bit column, which
     * {@link CdbCardProvider} already unpacks into {@link OcgCard#setcodes()}.
     * That is the game's own definition of an archetype, so the groups here are
     * exactly the groups a card effect means when it names one.
     * <p>
     * Empty when the engine is not loaded, which is a real case -- the duel
     * engine is a native library and a server without it still runs everything
     * else. {@link Choose#rollArchetype} falls back rather than failing.
     */
    private static synchronized Map<Integer, List<Integer>> archetypeIndex()
    {
        if(archetypes != null)
        {
            return archetypes;
        }
        Map<Integer, List<Integer>> out = new HashMap<>();
        EngineRuntime engine = EngineRuntime.isLoaded()
            ? EngineRuntime.get(EngineRuntime.Paths.defaults()) : null;
        if(engine == null)
        {
            // Deliberately NOT cached as empty: the engine may finish loading
            // later, and a cached empty map would mean this server never saw an
            // archetype again.
            return out;
        }
        CdbCardProvider cards = engine.cards();
        for(Properties card : DdDatabase.PROPERTIES_LIST.getList())
        {
            if(card == null || card.isIllegal || card.getId() <= 0L)
            {
                continue;
            }
            OcgCard data = cards.get((int)card.getId());
            if(data == null)
            {
                continue;
            }
            for(int setcode : data.setcodes())
            {
                if(setcode != 0)
                {
                    out.computeIfAbsent(setcode, k -> new ArrayList<>())
                        .add((int)card.getId());
                }
            }
        }
        // An archetype that cannot fill the row is not one this screen can offer.
        out.values().removeIf(list -> list.size() < MAX_CARDS);
        archetypes = out;
        return archetypes;
    }

    /** Client to server: the player picked the god at this carousel index. */
    public record Choose(int god) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Choose> TYPE =
            DdNetwork.type("statue_choose");
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC =
            CustomPacketPayload.codec(Choose::encode, Choose::decode);

        public Choose
        {
            // Clamped rather than refused. An index outside 0..2 is a client
            // sending nonsense, and there is nothing to gain by dropping the
            // packet when the reward does not depend on which one was picked.
            god = Math.max(0, Math.min(2, god));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        private static void encode(Choose message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.god());
        }

        private static Choose decode(FriendlyByteBuf buffer)
        {
            return new Choose(buffer.readVarInt());
        }

        /**
         * Rolls the reward and puts it in the player's trunk.
         * <p>
         * One archetype, five of its cards. Which god was chosen does not change
         * the pool: in the original the three statues are a presentation of one
         * reward rather than three different ones, and nothing on the disc ties a
         * god to a card list -- so tying them here would be inventing a rule. The
         * parameter is kept because the choice is real, and the day that mapping
         * IS found, this is where it goes.
         */
        public static void award(ServerPlayer player, int god)
        {
            // Affordability first, and the charge only once the roll has
            // actually produced something. Spending up front would take the
            // player's DE for a reward that then came up empty.
            // Creative already hands out anything for nothing, so a monument
            // that still charged would be the one place in the game where it
            // did not. Checked on the SERVER against the player's real game
            // mode rather than taken from the client, which could simply claim
            // to be creative -- the same rule, and the same reasoning, as
            // ShopMessages.Buy.
            boolean free = player.isCreative();
            if(!free && DuelEnergy.get(player) < DuelEnergy.MONUMENT_COST)
            {
                // Told to the SCREEN, not just the chat. The player is looking at
                // a full-screen menu with the chat behind it; a refusal that only
                // went to chat is a refusal nobody sees, and the screen sat there
                // claiming the offering had been accepted.
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    Granted.refused("You need " + DuelEnergy.MONUMENT_COST + " DE."
                        + "  You have " + DuelEnergy.get(player) + "."));
                return;
            }

            Random random = new Random();
            List<Integer> codes = rollArchetype(random);
            if(codes.isEmpty())
            {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    Granted.refused("No cards are available."));
                return;
            }

            if(!free)
            {
                DuelEnergy.spend(player, DuelEnergy.MONUMENT_COST);
                StatsMessages.sync(player);
            }

            // Into the collection and only there, the same as a shop purchase:
            // the trunk is where ownership lives, and items in a hotbar are a
            // second copy of the same fact that can be thrown away by accident.
            de.cas_ual_ty.dueldimension.duel.profile.Trunk trunk =
                DuelProfiles.get(player).trunk();
            List<Integer> arts = new ArrayList<>(codes.size());
            // Before the loop that adds them, for the reason freshAmong states:
            // once a card is in the trunk, nothing can tell it was not.
            List<Boolean> fresh = freshAmong(trunk, codes);
            for(int code : codes)
            {
                // Artwork 0: the reward is a CARD, not a particular printing of
                // one. Buying a specific printing is what the shop is for.
                byte art = 0;
                arts.add((int)art);
                trunk.add(code, "", art, 1);
            }

            // No chat line. The screen shows the cards and the trunk keeps them,
            // so a message listing them again was noise.
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new Granted(codes, arts, fresh, ""));
        }

        /**
         * Five cards from one randomly chosen archetype.
         * <p>
         * Falls back to five random legal cards when the engine is not loaded and
         * there are therefore no archetypes to choose from. That is a worse
         * reward than the one intended, and it is still a reward; refusing to pay
         * out because a native library is missing would be the wrong trade.
         */
        private static List<Integer> rollArchetype(Random random)
        {
            Map<Integer, List<Integer>> index = archetypeIndex();
            if(!index.isEmpty())
            {
                List<Integer> keys = new ArrayList<>(index.keySet());
                List<Integer> pool = new ArrayList<>(
                    index.get(keys.get(random.nextInt(keys.size()))));
                Collections.shuffle(pool, random);
                return new ArrayList<>(pool.subList(0, MAX_CARDS));
            }

            List<Integer> legal = new ArrayList<>();
            for(Properties card : DdDatabase.PROPERTIES_LIST.getList())
            {
                if(card != null && !card.isIllegal && card.getId() > 0L)
                {
                    legal.add((int)card.getId());
                }
            }
            if(legal.size() < MAX_CARDS)
            {
                return List.of();
            }
            Collections.shuffle(legal, random);
            return new ArrayList<>(legal.subList(0, MAX_CARDS));
        }
    }

    /**
     * Server to client: what the choice paid, so the screen can show it.
     * <p>
     * Sent only to the player who chose, and only after the cards are already in
     * their trunk -- this is a picture of a decision the server has committed,
     * not a proposal the client confirms.
     */
    public record Granted(List<Integer> codes, List<Integer> arts, List<Boolean> fresh,
        String reason) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Granted> TYPE =
            DdNetwork.type("statue_granted");
        public static final StreamCodec<RegistryFriendlyByteBuf, Granted> CODEC =
            CustomPacketPayload.codec(Granted::encode, Granted::decode);

        public Granted
        {
            codes = codes == null ? List.of() : List.copyOf(codes);
            arts = arts == null ? List.of() : List.copyOf(arts);
            fresh = fresh == null ? List.of() : List.copyOf(fresh);
            reason = reason == null ? "" : reason;
        }

        /** A refusal: no cards, and why. */
        public static Granted refused(String reason)
        {
            return new Granted(List.of(), List.of(), List.of(), reason);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        private static void encode(Granted message, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.codes().size());
            for(int i = 0; i < message.codes().size(); i++)
            {
                buffer.writeVarInt(message.codes().get(i));
                buffer.writeVarInt(i < message.arts().size() ? message.arts().get(i) : 0);
                buffer.writeBoolean(i < message.fresh().size() && message.fresh().get(i));
            }
            buffer.writeUtf(message.reason(), 128);
        }

        private static Granted decode(FriendlyByteBuf buffer)
        {
            // Bounded on read. A reward is a handful of cards; a length claiming
            // thousands is a malformed or hostile packet, not a big reward.
            int n = Math.max(0, Math.min(64, buffer.readVarInt()));
            List<Integer> codes = new ArrayList<>(n);
            List<Integer> arts = new ArrayList<>(n);
            List<Boolean> fresh = new ArrayList<>(n);
            for(int i = 0; i < n; i++)
            {
                codes.add(buffer.readVarInt());
                arts.add(buffer.readVarInt());
                fresh.add(buffer.readBoolean());
            }
            return new Granted(codes, arts, fresh, buffer.readUtf(128));
        }
    }

    /**
     * Which of these codes the trunk does not already hold.
     * <p>
     * Asked BEFORE anything is added, and each code counted once: the second
     * copy of a card in one payout is not new, even though the first was.
     */
    private static java.util.List<Boolean> freshAmong(
        de.cas_ual_ty.dueldimension.duel.profile.Trunk trunk, java.util.List<Integer> codes)
    {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.List<Boolean> fresh = new java.util.ArrayList<>(codes.size());
        for(int code : codes)
        {
            fresh.add(!trunk.has(code) && seen.add(code));
        }
        return fresh;
    }
}
