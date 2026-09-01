package de.cas_ual_ty.dueldimension.duel.trade;

import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;
import de.cas_ual_ty.dueldimension.net.DdNetwork;
import de.cas_ual_ty.dueldimension.shop.DuelPoints;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the trade messages and answers them.
 *
 * <h2>Every edit is validated, and every edit re-sends the table</h2>
 * A client asking for something it may not have is not an error to report; it
 * is simply a request the server does not carry out, after which both sides are
 * told what the table actually looks like. That is what keeps a modified client
 * from being able to show its victim a different trade from the one it is
 * about to make.
 */
public final class TradeNetwork
{
    private TradeNetwork()
    {
    }

    public static void register()
    {
        DdNetwork.serverbound(TradeMessages.PlayerAction.TYPE, TradeMessages.PlayerAction.CODEC);
        DdNetwork.serverbound(TradeMessages.SetSlot.TYPE, TradeMessages.SetSlot.CODEC);
        DdNetwork.serverbound(TradeMessages.SetPoints.TYPE, TradeMessages.SetPoints.CODEC);
        DdNetwork.serverbound(TradeMessages.SetReady.TYPE, TradeMessages.SetReady.CODEC);
        DdNetwork.serverbound(TradeMessages.Cancel.TYPE, TradeMessages.Cancel.CODEC);
        DdNetwork.serverbound(TradeMessages.OpenDebug.TYPE, TradeMessages.OpenDebug.CODEC);
        DdNetwork.clientbound(TradeMessages.OfferMenu.TYPE, TradeMessages.OfferMenu.CODEC);
        DdNetwork.clientbound(TradeMessages.State.TYPE, TradeMessages.State.CODEC);

        DdNetwork.onServer(TradeMessages.PlayerAction.TYPE, TradeNetwork::onPlayerAction);
        DdNetwork.onServer(TradeMessages.SetSlot.TYPE, TradeNetwork::onSetSlot);
        DdNetwork.onServer(TradeMessages.SetPoints.TYPE, TradeNetwork::onSetPoints);
        DdNetwork.onServer(TradeMessages.SetReady.TYPE, TradeNetwork::onSetReady);
        DdNetwork.onServer(TradeMessages.Cancel.TYPE, (message, player) ->
        {
            Trades.close(player, player.getGameProfile().name() + " called off the trade.");
            sendClosed(player);
        });
        DdNetwork.onServer(TradeMessages.OpenDebug.TYPE, TradeNetwork::onOpenDebug);
    }

    private static void onPlayerAction(TradeMessages.PlayerAction message, ServerPlayer player)
    {
        if(player.level().getServer() == null)
        {
            return;
        }
        ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(message.target());
        if(target == null || target == player)
        {
            return;
        }
        // Range is re-checked here and not taken from the click: the menu is a
        // client screen and could be answered a minute later, from anywhere.
        if(player.distanceToSqr(target) > 64.0D)
        {
            player.sendSystemMessage(Component.literal("They are too far away.")
                .withStyle(ChatFormatting.RED));
            return;
        }
        String error;
        if(message.trade())
        {
            error = Trades.hasInviteFrom(player, target)
                ? Trades.accept(player, target)
                : Trades.invite(player, target);
            if(error == null && Trades.isTrading(player))
            {
                sendState(player);
                sendState(target);
            }
        }
        else
        {
            error = de.cas_ual_ty.dueldimension.duel.match.DuelInvites
                .hasInviteFrom(player, target)
                ? de.cas_ual_ty.dueldimension.duel.match.DuelInvites
                    .accept(player, target.getGameProfile().name())
                : de.cas_ual_ty.dueldimension.duel.match.DuelInvites.invite(player, target);
        }
        if(error != null)
        {
            player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
        }
    }

    private static void onSetSlot(TradeMessages.SetSlot message, ServerPlayer player)
    {
        TradeSession session = Trades.of(player);
        if(session == null || message.slot() < 0 || message.slot() >= TradeSession.SLOTS)
        {
            return;
        }
        TradeSession.Side side = session.sideOf(player.getUUID());
        if(message.passcode() == 0)
        {
            side.offers[message.slot()] = null;
        }
        else
        {
            TradeSession.Offer offer = new TradeSession.Offer(
                message.passcode(), message.rarity(), message.art());
            side.offers[message.slot()] = offer;
            // Checked here as well as at commit, so the table can never show a
            // card the player does not hold -- including one the OTHER side is
            // looking at while deciding.
            Trunk trunk = DuelProfiles.get(player).trunk();
            int wanted = 0;
            for(TradeSession.Offer each : side.filled())
            {
                if(each.equals(offer))
                {
                    wanted++;
                }
            }
            if(trunk.countOf(offer.passcode(), offer.rarity(), offer.art()) < wanted)
            {
                side.offers[message.slot()] = null;
            }
        }
        session.touch();
        broadcast(player, session);
    }

    private static void onSetPoints(TradeMessages.SetPoints message, ServerPlayer player)
    {
        TradeSession session = Trades.of(player);
        if(session == null)
        {
            return;
        }
        // Clamped to what the player actually has, so the number on the table is
        // always a number they could pay.
        session.sideOf(player.getUUID()).points =
            Math.max(0, Math.min(message.points(), DuelPoints.get(player)));
        session.touch();
        broadcast(player, session);
    }

    private static void onSetReady(TradeMessages.SetReady message, ServerPlayer player)
    {
        TradeSession session = Trades.of(player);
        if(session == null)
        {
            return;
        }
        session.sideOf(player.getUUID()).ready = message.ready();
        if(!message.ready())
        {
            // Taking back agreement stops the clock at once rather than at the
            // next tick, which is the whole point of the countdown.
            session.countdown = -1;
        }
        broadcast(player, session);
    }

    private static void onOpenDebug(TradeMessages.OpenDebug message, ServerPlayer player)
    {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment())
        {
            return;
        }
        Trades.openDebug(player, "Test Trader",
            new TradeSession.Offer(SCAPEGOAT, Trunk.UNKNOWN_RARITY, 0));
        sendState(player);
    }

    /** The stand-in's one card. */
    private static final int SCAPEGOAT = 73915051;

    /** Sends the table to both sides, whoever they are. */
    public static void broadcast(ServerPlayer player, TradeSession session)
    {
        sendState(player);
        ServerPlayer other = Trades.otherPlayer(player, session);
        if(other != null)
        {
            sendState(other);
        }
    }

    /** Tells one player what their side of the table looks like. */
    public static void sendState(ServerPlayer player)
    {
        TradeSession session = Trades.of(player);
        if(session == null)
        {
            sendClosed(player);
            return;
        }
        TradeSession.Side mine = session.sideOf(player.getUUID());
        TradeSession.Side theirs = session.otherSideOf(player.getUUID());
        ServerPlayer other = Trades.otherPlayer(player, session);
        String name = session.isNpc() ? session.npc
            : other == null ? "?" : other.getGameProfile().name();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new TradeMessages.State(name, slotsOf(mine), slotsOf(theirs),
                mine.points, theirs.points, mine.ready, theirs.ready,
                session.countdown, true));
    }

    /** Tells a player their trade is over, so the screen closes itself. */
    public static void sendClosed(ServerPlayer player)
    {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new TradeMessages.State("", empty(), empty(), 0, 0, false, false, -1, false));
    }

    private static List<TradeMessages.State.Slot> slotsOf(TradeSession.Side side)
    {
        List<TradeMessages.State.Slot> slots = new ArrayList<>(TradeSession.SLOTS);
        for(TradeSession.Offer offer : side.offers)
        {
            slots.add(offer == null ? new TradeMessages.State.Slot(0, "", 0)
                : new TradeMessages.State.Slot(offer.passcode(), offer.rarity(), offer.art()));
        }
        return slots;
    }

    private static List<TradeMessages.State.Slot> empty()
    {
        List<TradeMessages.State.Slot> slots = new ArrayList<>(TradeSession.SLOTS);
        for(int i = 0; i < TradeSession.SLOTS; i++)
        {
            slots.add(new TradeMessages.State.Slot(0, "", 0));
        }
        return slots;
    }
}
