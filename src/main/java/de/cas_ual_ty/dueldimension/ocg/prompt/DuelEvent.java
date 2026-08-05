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
    public enum Kind
    {
        MOVE,
        SUMMON,
        SPECIAL_SUMMON,
        SET,
        FLIP,
        ACTIVATE,
        ATTACK,
        DAMAGE,
        RECOVER,
        DESTROY,
        DRAW,
        SHUFFLE,
        /** An effect went onto the chain: show it activating. */
        CHAINING,
        /** An effect picked this card as a target. */
        BECOME_TARGET,
        PHASE,
        NEW_TURN,
        WIN
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
