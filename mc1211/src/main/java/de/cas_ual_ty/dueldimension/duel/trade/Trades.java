package de.cas_ual_ty.dueldimension.duel.trade;

import de.cas_ual_ty.dueldimension.DdSounds;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;
import de.cas_ual_ty.dueldimension.shop.DuelPoints;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every trade in progress, and the rules that move cards between two players.
 *
 * <h2>Why the commit is written the way it is</h2>
 * A trade is the only place in the mod where one player's collection is changed
 * by another player's click, so it is the one place where a half-finished
 * operation would be a duplication bug rather than a cosmetic one. The commit
 * therefore does all of its taking before any of its giving, and checks both
 * sides can deliver immediately before it starts: if anything is missing,
 * nothing moves at all.
 * <p>
 * The gap the second check closes is real. A player can open the deck editor
 * and spend the card they are offering, or buy a pack and spend the DP, while
 * the trade window sits open on the other screen.
 */
public final class Trades
{
    private Trades()
    {
    }

    /** Keyed by BOTH players, so either side's messages find the same session. */
    private static final Map<UUID, TradeSession> SESSIONS = new LinkedHashMap<>();

    /** Outstanding invitations: who was asked, by whom. */
    private static final Map<UUID, UUID> INVITES = new LinkedHashMap<>();

    public static TradeSession of(ServerPlayer player)
    {
        return player == null ? null : SESSIONS.get(player.getUUID());
    }

    public static boolean isTrading(ServerPlayer player)
    {
        return of(player) != null;
    }

    /**
     * Asks another player to trade.
     *
     * @return why not, or null if the invitation went out
     */
    public static String invite(ServerPlayer from, ServerPlayer to)
    {
        if(from == null || to == null || from == to)
        {
            return "There is nobody there to trade with.";
        }
        if(isTrading(from))
        {
            return "You are already trading.";
        }
        if(isTrading(to))
        {
            return to.getGameProfile().getName() + " is already trading.";
        }
        // Their invitation to us outranks ours to them, so two players who both
        // reach for each other end up in one trade rather than two crossed
        // invitations -- the same rule the duel side follows.
        if(to.getUUID().equals(INVITES.get(from.getUUID())))
        {
            return accept(from, to);
        }
        INVITES.put(to.getUUID(), from.getUUID());
        to.sendSystemMessage(Component.literal(
            from.getGameProfile().getName() + " wants to trade. Right-click them to accept.")
            .withStyle(ChatFormatting.AQUA));
        from.sendSystemMessage(Component.literal(
            "Asked " + to.getGameProfile().getName() + " to trade.")
            .withStyle(ChatFormatting.GRAY));
        return null;
    }

    /** Whether this player has been asked to trade by that one. */
    public static boolean hasInviteFrom(ServerPlayer me, ServerPlayer them)
    {
        return them != null && them.getUUID().equals(INVITES.get(me.getUUID()));
    }

    /** Takes up an invitation, opening the table for both. */
    public static String accept(ServerPlayer me, ServerPlayer them)
    {
        if(isTrading(me) || isTrading(them))
        {
            return "One of you is already trading.";
        }
        INVITES.remove(me.getUUID());
        INVITES.remove(them.getUUID());
        TradeSession session = new TradeSession(me.getUUID(), them.getUUID(), null);
        SESSIONS.put(me.getUUID(), session);
        SESSIONS.put(them.getUUID(), session);
        return null;
    }

    /**
     * Opens a trade against a stand-in that is always ready.
     * <p>
     * Development only, and the reason it exists is that the interface needs
     * two sides to be worth looking at and a second player is not always to
     * hand. The stand-in offers one card and never changes its mind, so every
     * state the screen can be in is reachable by one person.
     */
    public static TradeSession openDebug(ServerPlayer player, String name,
        TradeSession.Offer offer)
    {
        close(player, null);
        TradeSession session = new TradeSession(player.getUUID(), UUID.randomUUID(), name);
        session.b.offers[0] = offer;
        session.b.ready = true;
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    /**
     * Ends a session, telling whoever is left why.
     *
     * @param reason shown to the other side; null when they already know
     */
    public static void close(ServerPlayer player, String reason)
    {
        TradeSession session = of(player);
        if(session == null)
        {
            return;
        }
        SESSIONS.remove(session.first);
        SESSIONS.remove(session.second);
        if(reason != null && !session.isNpc())
        {
            ServerPlayer other = otherPlayer(player, session);
            if(other != null)
            {
                other.sendSystemMessage(Component.literal(reason)
                    .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /** The other real player in a session, or null for a debug trade. */
    public static ServerPlayer otherPlayer(ServerPlayer player, TradeSession session)
    {
        if(session == null || session.isNpc() || player.level().getServer() == null)
        {
            return null;
        }
        return player.level().getServer().getPlayerList().getPlayer(session.otherThan(player.getUUID()));
    }

    /**
     * Runs the countdown on every session, and commits the ones that reach zero.
     * <p>
     * On the server tick, which is also the only thread that touches
     * {@link #SESSIONS}.
     */
    public static void tick(net.minecraft.server.MinecraftServer server)
    {
        if(SESSIONS.isEmpty())
        {
            return;
        }
        List<TradeSession> due = new ArrayList<>();
        for(TradeSession session : new java.util.LinkedHashSet<>(SESSIONS.values()))
        {
            if(session.finished)
            {
                continue;
            }
            if(!session.bothReady())
            {
                session.countdown = -1;
                continue;
            }
            if(session.countdown < 0)
            {
                session.countdown = TradeSession.COUNTDOWN_TICKS;
            }
            else if(--session.countdown <= 0)
            {
                due.add(session);
            }
        }
        for(TradeSession session : due)
        {
            commit(server, session);
        }
    }

    /**
     * Moves everything, or nothing.
     *
     * @return null on success, or why it was refused
     */
    public static String commit(net.minecraft.server.MinecraftServer server,
        TradeSession session)
    {
        if(session.finished)
        {
            return null;
        }
        ServerPlayer one = server.getPlayerList().getPlayer(session.first);
        ServerPlayer two = session.isNpc() ? null
            : server.getPlayerList().getPlayer(session.second);
        if(one == null || (!session.isNpc() && two == null))
        {
            fail(session, one, two, "The other player left.");
            return "The other player left.";
        }

        // Both sides checked BEFORE either is touched. Checking one and then
        // taking from it would leave a player short if the second check failed.
        if(!session.canDeliver(one, session.a) || !session.canDeliver(two, session.b))
        {
            fail(session, one, two, "The trade was refused: something on the table is gone.");
            return "Something on the table is no longer available.";
        }

        take(one, session.a);
        take(two, session.b);
        give(one, session.b);
        give(two, session.a);

        session.finished = true;
        SESSIONS.remove(session.first);
        SESSIONS.remove(session.second);

        for(ServerPlayer player : new ServerPlayer[] {one, two})
        {
            if(player == null)
            {
                continue;
            }
            DuelProfiles.saveAndSync(player);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints(
                    DuelPoints.get(player)));
            player.level().playSound(null, player.blockPosition(), DdSounds.TRADE_SUCCESS,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1F);
        }
        return null;
    }

    private static void fail(TradeSession session, ServerPlayer one, ServerPlayer two,
        String reason)
    {
        session.finished = true;
        SESSIONS.remove(session.first);
        SESSIONS.remove(session.second);
        for(ServerPlayer player : new ServerPlayer[] {one, two})
        {
            if(player != null)
            {
                player.sendSystemMessage(Component.literal(reason)
                    .withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Takes one side's offer off them. The NPC has nothing to take from. */
    private static void take(ServerPlayer player, TradeSession.Side side)
    {
        if(player == null)
        {
            return;
        }
        Trunk trunk = DuelProfiles.get(player).trunk();
        for(TradeSession.Offer offer : side.filled())
        {
            // By exact printing: the card handed over is the card that was
            // shown, not whichever copy the trunk would rather part with.
            trunk.remove(offer.passcode(), offer.rarity(), offer.art(), 1);
        }
        if(side.points > 0)
        {
            DuelPoints.spend(player, side.points);
        }
    }

    /** Gives one side's offer to the player across the table. */
    private static void give(ServerPlayer player, TradeSession.Side side)
    {
        if(player == null)
        {
            return;
        }
        Trunk trunk = DuelProfiles.get(player).trunk();
        for(TradeSession.Offer offer : side.filled())
        {
            trunk.add(offer.passcode(), offer.rarity(), offer.art(), 1);
        }
        if(side.points > 0)
        {
            DuelPoints.award(player, side.points);
        }
    }

    /** A player leaving takes their trade with them. */
    public static void forget(ServerPlayer player)
    {
        close(player, player.getGameProfile().getName() + " left; the trade is off.");
    }
}
