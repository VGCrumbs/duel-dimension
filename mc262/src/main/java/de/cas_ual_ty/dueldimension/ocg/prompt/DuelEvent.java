package de.cas_ual_ty.dueldimension.ocg.prompt;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Something that happened in the duel, in a form the client can animate and
 * sound.
 * <p>
 * Board snapshots say what the field looks like now; they cannot say that a
 * card <em>travelled</em> from a hand to a zone, which is what an animation
 * needs. These events carry that, decoded server-side from the engine's
 * message stream so the client still never sees the raw protocol.
 *
 * @param kind        what happened
 * @param code        the card involved, 0 when there is none or it is hidden
 * @param fromZone    packed zone reference, or -1
 * @param toZone      packed zone reference, or -1
 * @param amount      life points, counters, or a card count
 * @param player      whose side it concerns, from the viewer's seat
 */
public record DuelEvent(Kind kind, int code, int fromZone, int toZone, int amount, int player)
{
    /**
     * {@link Kind#POSITION}'s {@code amount}: the posture the card ends in.
     * <p>
     * Two bits, because a position change is two independent questions and the
     * animation is different for each. Turning face-up is a card being turned
     * OVER; switching to defence is a card lying DOWN; a flip summon into
     * defence is both at once, and reading only the facing made all three look
     * like the first.
     * <p>
     * Face-up is bit 0 so that the old {@code amount != 0} reading still means
     * face-up for every event that predates the second bit.
     */
    public static final int POSITION_FACE_UP = 1;
    public static final int POSITION_DEFENCE = 2;
    /** And where it came from, so the client knows which half actually moved. */
    public static final int POSITION_WAS_FACE_UP = 4;
    public static final int POSITION_WAS_DEFENCE = 8;

    /** Packs both ends of a position change into {@code amount}. */
    public static int posture(boolean faceUp, boolean defence,
        boolean wasFaceUp, boolean wasDefence)
    {
        return (faceUp ? POSITION_FACE_UP : 0)
            | (defence ? POSITION_DEFENCE : 0)
            | (wasFaceUp ? POSITION_WAS_FACE_UP : 0)
            | (wasDefence ? POSITION_WAS_DEFENCE : 0);
    }

    /** Did the card turn over, or only lie down? */
    public static boolean turnsOver(DuelEvent event)
    {
        return endsFaceUp(event) != ((event.amount() & POSITION_WAS_FACE_UP) != 0);
    }

    /** Did it change posture, or only turn over? */
    public static boolean liesDown(DuelEvent event)
    {
        return endsInDefence(event) != ((event.amount() & POSITION_WAS_DEFENCE) != 0);
    }

    /** Whether a {@link Kind#POSITION} or {@link Kind#FLIP} ends face-up. */
    public static boolean endsFaceUp(DuelEvent event)
    {
        return (event.amount() & POSITION_FACE_UP) != 0;
    }

    /** And whether it ends lying down. */
    public static boolean endsInDefence(DuelEvent event)
    {
        return (event.amount() & POSITION_DEFENCE) != 0;
    }

    public enum Kind
    {
        MOVE,
        SUMMON,
        SPECIAL_SUMMON,
        SET,
        FLIP,
        /** A battle-position change: to defence, to attack, or flipped up. */
        POSITION,
        ACTIVATE,
        ATTACK,
        DAMAGE,
        RECOVER,
        DESTROY,
        /**
         * A card released as a tribute, which is NOT a destruction.
         * <p>
         * Both end up in the graveyard, and until now both were told apart by
         * exactly that -- so a monster given up to summon a bigger one
         * shattered like glass, which is the wrong story. The engine has always
         * said which is which and nothing was reading it: MSG_MOVE carries a
         * REASON, and a tribute sets {@code REASON_RELEASE}.
         */
        TRIBUTE,
        DRAW,
        SHUFFLE,
        /** A coin toss; amount packs one bit per coin, low bit first. */
        COIN,
        /** A dice roll; amount packs one die per six bits, low first. */
        DICE,
        /** An effect went onto the chain: show it activating. */
        CHAINING,
        /** An effect picked this card as a target. */
        BECOME_TARGET,
        /**
         * A card an effect is showing to this player. Sent only to the seat
         * being shown it: a reveal the opponent was not entitled to see is
         * hidden information, and this is the one message whose whole purpose
         * is to hand information to exactly one side.
         */
        REVEAL,
        PHASE,
        NEW_TURN,
        WIN,
        /**
         * The blow landing, as distinct from {@link #ATTACK} declaring it.
         * <p>
         * MSG_BATTLE, which the engine sends once the damage step resolves and
         * which carries both cards. A defender flinches on THIS rather than on
         * the declaration: an attack that is negated never reaches it, and one
         * that is answered by a trap reaches it late, so firing the reaction on
         * the declaration made monsters recoil from blows that never landed.
         * <p>
         * Appended rather than slotted in beside ATTACK. The kind is serialised
         * by ordinal, so inserting one would renumber every kind after it and a
         * client a version behind would read summons as sets.
         */
        BATTLE
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeEnum(kind);
        buffer.writeVarInt(code);
        buffer.writeVarInt(fromZone + 1);
        buffer.writeVarInt(toZone + 1);
        buffer.writeVarInt(amount);
        buffer.writeVarInt(player);
    }

    public static DuelEvent read(FriendlyByteBuf buffer)
    {
        return new DuelEvent(buffer.readEnum(Kind.class), buffer.readVarInt(),
            buffer.readVarInt() - 1, buffer.readVarInt() - 1, buffer.readVarInt(), buffer.readVarInt());
    }

    public static void writeList(FriendlyByteBuf buffer, List<DuelEvent> events)
    {
        buffer.writeVarInt(events.size());
        events.forEach(event -> event.write(buffer));
    }

    public static List<DuelEvent> readList(FriendlyByteBuf buffer)
    {
        int count = buffer.readVarInt();
        List<DuelEvent> events = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            events.add(read(buffer));
        }
        return events;
    }

    /** Packs a location the way {@link EnginePrompt#zoneRef} does. */
    public static int zoneOf(int controller, int location, int sequence, int viewerSeat)
    {
        boolean opponent = controller != viewerSeat;
        boolean monsterZone = location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE;
        if(location != de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE
            && location != de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE)
        {
            return -1; // piles and hands are animated from their own anchors
        }
        return EnginePrompt.zoneRef(opponent, monsterZone, sequence);
    }
}
