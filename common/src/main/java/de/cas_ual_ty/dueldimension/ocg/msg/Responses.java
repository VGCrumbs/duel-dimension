package de.cas_ual_ty.dueldimension.ocg.msg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builders for the byte responses the core's AWAITING states parse.
 * Formats transcribed from ygopro-core playerop.cpp — the response
 * readers there are the authoritative spec; anything they reject
 * produces MSG_RETRY.
 */
public final class Responses
{
    private Responses()
    {
    }

    /** Most prompts read a single little-endian int32. */
    public static byte[] int32(int value)
    {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static byte[] indexed(int action, int index)
    {
        return int32(action | (index << 16));
    }

    // ---- MSG_SELECT_IDLECMD: int32 = command | (listIndex << 16) ----

    public static byte[] idleSummon(int index)
    {
        return indexed(0, index);
    }

    public static byte[] idleSpSummon(int index)
    {
        return indexed(1, index);
    }

    public static byte[] idleReposition(int index)
    {
        return indexed(2, index);
    }

    public static byte[] idleMonsterSet(int index)
    {
        return indexed(3, index);
    }

    public static byte[] idleSpellSet(int index)
    {
        return indexed(4, index);
    }

    public static byte[] idleActivate(int index)
    {
        return indexed(5, index);
    }

    public static byte[] idleToBattle()
    {
        return int32(6);
    }

    public static byte[] idleToEnd()
    {
        return int32(7);
    }

    public static byte[] idleShuffleHand()
    {
        return int32(8);
    }

    // ---- MSG_SELECT_BATTLECMD: int32 = command | (listIndex << 16) ----

    public static byte[] battleActivate(int index)
    {
        return indexed(0, index);
    }

    public static byte[] battleAttack(int index)
    {
        return indexed(1, index);
    }

    public static byte[] battleToMain2()
    {
        return int32(2);
    }

    public static byte[] battleToEnd()
    {
        return int32(3);
    }

    // ---- MSG_SELECT_YESNO / MSG_SELECT_EFFECTYN: int32 1/0 ----

    public static byte[] yes()
    {
        return int32(1);
    }

    public static byte[] no()
    {
        return int32(0);
    }

    // ---- MSG_SELECT_OPTION: int32 option index ----

    public static byte[] option(int index)
    {
        return int32(index);
    }

    // ---- MSG_SELECT_CHAIN: int32 chain index, or -1 to decline ----

    public static byte[] chain(int index)
    {
        return int32(index);
    }

    public static byte[] chainDecline()
    {
        return int32(-1);
    }

    // ---- MSG_SELECT_POSITION: int32 position bit (POS_*) ----

    public static byte[] position(int position)
    {
        return int32(position);
    }

    // ---- MSG_SELECT_CARD: ProgressiveBuffer [i32 mode][u32 count][u8 index...] ----

    /**
     * Selects cards by their indices into the prompt's card list, using
     * response mode 2 (u8 indices from byte offset 8; parse_response_cards).
     * Duplicate indices are rejected by the core.
     */
    public static byte[] selectCards(int... indices)
    {
        ByteBuffer buffer = ByteBuffer.allocate(8 + indices.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(2);
        buffer.putInt(indices.length);
        for(int index : indices)
        {
            buffer.put((byte)index);
        }
        return buffer.array();
    }

    /** Cancels a cancelable card selection. */
    public static byte[] selectCardsCancel()
    {
        return int32(-1);
    }

    // ---- MSG_SELECT_PLACE / MSG_SELECT_DISFIELD: count × [u8 player][u8 location][u8 sequence] ----

    public record Place(int player, int location, int sequence)
    {
    }

    public static byte[] places(Place... places)
    {
        ByteBuffer buffer = ByteBuffer.allocate(3 * places.length).order(ByteOrder.LITTLE_ENDIAN);
        for(Place place : places)
        {
            buffer.put((byte)place.player());
            buffer.put((byte)place.location());
            buffer.put((byte)place.sequence());
        }
        return buffer.array();
    }

    // ---- MSG_SELECT_TRIBUTE / MSG_SELECT_SUM: same card-index buffer as SELECT_CARD ----

    public static byte[] selectTribute(int... indices)
    {
        return selectCards(indices);
    }

    /** Indices into the prompt's <em>selectable</em> list; must-select cards are implicit. */
    public static byte[] selectSum(int... indices)
    {
        return selectCards(indices);
    }

    // ---- MSG_SELECT_UNSELECT_CARD: [i32 1][i32 index] one at a time, or -1 to finish/cancel ----

    public static byte[] selectUnselect(int index)
    {
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);
        buffer.putInt(index);
        return buffer.array();
    }

    public static byte[] selectUnselectFinish()
    {
        return int32(-1);
    }

    // ---- MSG_SELECT_COUNTER: one i16 per offered card ----

    public static byte[] counters(int... perCard)
    {
        ByteBuffer buffer = ByteBuffer.allocate(2 * perCard.length).order(ByteOrder.LITTLE_ENDIAN);
        for(int amount : perCard)
        {
            buffer.putShort((short)amount);
        }
        return buffer.array();
    }

    // ---- MSG_SORT_CARD / MSG_SORT_CHAIN: one i8 destination per card, or -1 to decline ----

    public static byte[] sort(int... order)
    {
        ByteBuffer buffer = ByteBuffer.allocate(order.length).order(ByteOrder.LITTLE_ENDIAN);
        for(int position : order)
        {
            buffer.put((byte)position);
        }
        return buffer.array();
    }

    public static byte[] sortDecline()
    {
        return new byte[] {-1};
    }

    // ---- MSG_ANNOUNCE_*: bit masks / indices / card codes ----

    /** MSG_ANNOUNCE_RACE: u64 mask with exactly {@code count} bits set. */
    public static byte[] announceRace(long mask)
    {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(mask).array();
    }

    /** MSG_ANNOUNCE_ATTRIB: u32 mask with exactly {@code count} bits set. */
    public static byte[] announceAttribute(int mask)
    {
        return int32(mask);
    }

    /** MSG_ANNOUNCE_CARD: the declared passcode (must satisfy the prompt's filter). */
    public static byte[] announceCard(int code)
    {
        return int32(code);
    }

    /** MSG_ANNOUNCE_NUMBER: index into the offered options. */
    public static byte[] announceNumber(int index)
    {
        return int32(index);
    }

    /** MSG_ROCK_PAPER_SCISSORS: 1 = rock, 2 = paper, 3 = scissors. */
    public static byte[] rockPaperScissors(int hand)
    {
        return int32(hand);
    }
}
