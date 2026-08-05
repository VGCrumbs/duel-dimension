package de.cas_ual_ty.ydm.ocg.msg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Little-endian reader over one message payload. All multi-byte reads match
 * the core's duel_message::write widths; decoding a known message must end
 * exactly at the payload's end ({@link #expectEnd()}) — leftover bytes mean
 * our layout knowledge is wrong, which must fail loudly, not silently.
 */
public class MsgReader
{
    private final ByteBuffer buffer;

    public MsgReader(byte[] payload)
    {
        buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    }

    public int u8()
    {
        return buffer.get() & 0xFF;
    }

    public boolean flag()
    {
        return u8() != 0;
    }

    public int u16()
    {
        return buffer.getShort() & 0xFFFF;
    }

    /** u32 read into an int; card codes, counts, sequences and positions all fit. */
    public int u32()
    {
        return buffer.getInt();
    }

    public long u64()
    {
        return buffer.getLong();
    }

    /** The 10-byte loc_info. */
    public CardLocation loc()
    {
        return new CardLocation(u8(), u8(), u32(), u32());
    }

    public int remaining()
    {
        return buffer.remaining();
    }

    public void expectEnd()
    {
        if(buffer.hasRemaining())
        {
            throw new MsgFormatException(buffer.remaining() + " undecoded bytes left — message layout mismatch");
        }
    }

    public static class MsgFormatException extends RuntimeException
    {
        public MsgFormatException(String message)
        {
            super(message);
        }

        public MsgFormatException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
