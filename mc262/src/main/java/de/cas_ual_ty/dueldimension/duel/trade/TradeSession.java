package de.cas_ual_ty.dueldimension.duel.trade;

import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;
import de.cas_ual_ty.dueldimension.shop.DuelPoints;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * One trade between two players: what each is offering, and how far along the
 * agreement is.
 *
 * <h2>The server owns all of it</h2>
 * Every field here is the server's. A client says "put this card in slot 3" and
 * is told what the trade now looks like; it never says what the trade IS. That
 * is the same rule the duel side follows and it matters more here, because the
 * thing being decided is who ends up owning what.
 *
 * <h2>Editing clears both ready flags</h2>
 * The oldest trick in trading is to wait until the other side has agreed and
 * then swap the good card for a worse one. {@link #touch()} runs on every edit
 * and puts both sides back to not-ready, cancelling any countdown — so the
 * thing a player pressed Ready on is always the thing they saw. This is not a
 * nicety; without it the countdown is an attack window rather than a safeguard.
 *
 * <h2>Ownership is checked twice</h2>
 * Once when a card is offered, so the interface cannot show a card that is not
 * there, and again at the moment of commit. The gap between them is real: a
 * player can spend the offered card in the deck editor, or spend their DP in
 * the shop, while the trade window sits open. The second check is the one that
 * decides, and a trade that fails it moves nothing at all.
 */
public final class TradeSession
{
    /** How many cards one side may put up. */
    public static final int SLOTS = 9;

    /**
     * How long both sides must stay agreed before the trade goes through.
     * <p>
     * Twenty ticks to the second, so five seconds. Long enough to read what is
     * on the table and take it back, short enough not to be a delay.
     */
    public static final int COUNTDOWN_TICKS = 5 * 20;

    /** One offered card: a printing, which is what the trunk moves. */
    public record Offer(int passcode, String rarity, int art)
    {
        public Offer
        {
            rarity = rarity == null ? Trunk.UNKNOWN_RARITY : rarity;
            art = Math.max(0, art);
        }
    }

    /** One side of the table. */
    public static final class Side
    {
        /** Null in a slot means an empty slot, which is why this is not a list. */
        public final Offer[] offers = new Offer[SLOTS];
        public int points;
        public boolean ready;

        public List<Offer> filled()
        {
            List<Offer> out = new ArrayList<>(SLOTS);
            for(Offer offer : offers)
            {
                if(offer != null)
                {
                    out.add(offer);
                }
            }
            return out;
        }
    }

    public final java.util.UUID first;
    public final java.util.UUID second;
    public final Side a = new Side();
    public final Side b = new Side();

    /**
     * The other side of a debug trade, or null for a trade between two players.
     * <p>
     * A named NPC rather than a flag, so the screen has something to put in the
     * other player's caption and the commit path has one place to ask whether
     * there is anybody to take cards FROM.
     */
    public final String npc;

    /** Ticks left before the trade goes through, or -1 when not counting. */
    public int countdown = -1;

    /** Set once, so a finished trade is cleaned up rather than run twice. */
    public boolean finished;

    public TradeSession(java.util.UUID first, java.util.UUID second, String npc)
    {
        this.first = first;
        this.second = second;
        this.npc = npc;
    }

    public boolean isNpc()
    {
        return npc != null;
    }

    public Side sideOf(java.util.UUID player)
    {
        return player.equals(first) ? a : b;
    }

    public Side otherSideOf(java.util.UUID player)
    {
        return player.equals(first) ? b : a;
    }

    public java.util.UUID otherThan(java.util.UUID player)
    {
        return player.equals(first) ? second : first;
    }

    /**
     * Called after ANY change to what is on the table.
     * <p>
     * See the class note: both sides go back to not-ready and the countdown is
     * abandoned, so nobody can agree to one trade and receive another.
     */
    public void touch()
    {
        a.ready = false;
        b.ready = false;
        countdown = -1;
    }

    /** Whether both sides have agreed to what is currently on the table. */
    public boolean bothReady()
    {
        return a.ready && b.ready;
    }

    /**
     * Whether this side could actually deliver what it is offering, right now.
     * <p>
     * The NPC is exempt: it is not backed by a profile and its side of the
     * table is whatever the debug session put there.
     */
    public boolean canDeliver(ServerPlayer player, Side side)
    {
        if(player == null)
        {
            return true;
        }
        if(side.points < 0 || DuelPoints.get(player) < side.points)
        {
            return false;
        }
        Trunk trunk = DuelProfiles.get(player).trunk();
        // Counted, not merely "does the player have one": two slots holding the
        // same printing need two copies, and asking per slot would let one copy
        // satisfy both.
        java.util.Map<Offer, Integer> wanted = new java.util.LinkedHashMap<>();
        for(Offer offer : side.filled())
        {
            wanted.merge(offer, 1, Integer::sum);
        }
        for(java.util.Map.Entry<Offer, Integer> entry : wanted.entrySet())
        {
            Offer offer = entry.getKey();
            if(trunk.countOf(offer.passcode(), offer.rarity(), offer.art()) < entry.getValue())
            {
                return false;
            }
        }
        return true;
    }
}
