package de.cas_ual_ty.dueldimension.ocg.query;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the core's query buffers.
 * <p>
 * A card is serialised as a run of TLV entries — {@code [u16 size][u32 flag]
 * [payload of size-4 bytes]} — terminated by a QUERY_END entry. An empty zone
 * is just {@code [u16 0]}. Source: card.cpp get_infos, ocgapi.cpp
 * OCG_DuelQueryLocation.
 * <p>
 * Note the core <em>always</em> emits QUERY_IS_PUBLIC whether or not it was
 * requested; that flag is what lets us build an honest per-player view
 * instead of guessing what an opponent may know.
 */
public final class QueryParser
{
    /** Flags worth asking for when building a board snapshot. */
    public static final int BOARD_FLAGS = OcgConstants.QUERY_CODE | OcgConstants.QUERY_POSITION
        | OcgConstants.QUERY_TYPE | OcgConstants.QUERY_LEVEL | OcgConstants.QUERY_ATTACK
        | OcgConstants.QUERY_DEFENSE | OcgConstants.QUERY_BASE_ATTACK
        | OcgConstants.QUERY_BASE_DEFENSE | OcgConstants.QUERY_IS_PUBLIC
        | OcgConstants.QUERY_EQUIP_CARD
        // A pendulum card's CURRENT scale, which is not its printed one
        // once an effect has moved it.
        | OcgConstants.QUERY_LSCALE | OcgConstants.QUERY_RSCALE;

    private QueryParser()
    {
    }

    /**
     * Parses an OCG_DuelQueryLocation buffer: a u32 byte-count followed by one
     * entry per zone slot (empty slots included, as {@code [u16 0]}).
     *
     * @return one element per slot; null where the slot is empty
     */
    public static List<CardView> parseLocation(byte[] raw)
    {
        List<CardView> cards = new ArrayList<>();
        if(raw.length < 4)
        {
            return cards;
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int size = buffer.getInt();
        if(size < 0 || size > buffer.remaining())
        {
            throw new IllegalStateException("Query buffer claims " + size + " bytes, has " + buffer.remaining());
        }
        while(buffer.remaining() >= 2)
        {
            cards.add(parseCard(buffer));
        }
        return cards;
    }

    /** Parses a single-card OCG_DuelQuery buffer (no length prefix, no empty-slot marker). */
    public static CardView parseSingle(byte[] raw)
    {
        if(raw.length < 2)
        {
            return null;
        }
        return parseCard(ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN));
    }

    private static CardView parseCard(ByteBuffer buffer)
    {
        int code = 0;
        int position = 0;
        int type = 0;
        int level = 0;
        int attack = -1;
        int defense = -1;
        // Base values, so the board can tell a boosted or weakened stat from a
        // printed one the way EDOPro's card info does.
        int baseAttack = -1;
        int baseDefense = -1;
        // -1 for "not a pendulum card", as with the stats.
        int leftScale = -1;
        int rightScale = -1;
        boolean isPublic = false;
        CardView.Equip equip = null;

        while(true)
        {
            if(buffer.remaining() < 2)
            {
                return null;
            }
            int size = buffer.getShort() & 0xFFFF;
            if(size == 0)
            {
                return null; // empty zone slot
            }
            if(size < 4 || size - 4 > buffer.remaining())
            {
                throw new IllegalStateException("Malformed query entry: size " + size);
            }
            int flag = buffer.getInt();
            int payload = size - 4;
            int end = buffer.position() + payload;

            switch(flag)
            {
                case OcgConstants.QUERY_CODE -> code = buffer.getInt();
                case OcgConstants.QUERY_POSITION -> position = buffer.getInt();
                case OcgConstants.QUERY_TYPE -> type = buffer.getInt();
                case OcgConstants.QUERY_LEVEL -> level = buffer.getInt();
                case OcgConstants.QUERY_ATTACK -> attack = buffer.getInt();
                case OcgConstants.QUERY_DEFENSE -> defense = buffer.getInt();
                case OcgConstants.QUERY_BASE_ATTACK -> baseAttack = buffer.getInt();
                case OcgConstants.QUERY_BASE_DEFENSE -> baseDefense = buffer.getInt();
                case OcgConstants.QUERY_LSCALE -> leftScale = buffer.getInt();
                case OcgConstants.QUERY_RSCALE -> rightScale = buffer.getInt();
                case OcgConstants.QUERY_IS_PUBLIC -> isPublic = buffer.get() != 0;
                case OcgConstants.QUERY_EQUIP_CARD ->
                {
                    // card.cpp:149 -- always emitted when asked for, as a
                    // 10-byte loc_info, zero-filled when nothing is equipped.
                    // Location 0 is no zone, so it doubles as "no target".
                    int controller = buffer.get() & 0xFF;
                    int location = buffer.get() & 0xFF;
                    int sequence = buffer.getInt();
                    if(location != 0)
                    {
                        equip = new CardView.Equip(controller, location, sequence);
                    }
                }
                case OcgConstants.QUERY_END ->
                {
                    return new CardView(code, position, type, level, attack, defense,
                        baseAttack, baseDefense, leftScale, rightScale, isPublic, false, equip);
                }
                default ->
                {
                    // Flag we didn't ask about (or don't model): skip its payload.
                }
            }
            buffer.position(end);
        }
    }
}
