package de.cas_ual_ty.ydm.ocg;

import java.util.Arrays;

/**
 * One undecoded core message: type byte plus its payload (the bytes after the
 * type). Typed decoders (Phase 1) will wrap this; until then consumers peek at
 * the raw payload.
 */
public record RawMessage(int type, byte[] payload)
{
    public static RawMessage of(byte[] message)
    {
        int type = message.length > 0 ? message[0] & 0xFF : -1;
        return new RawMessage(type, Arrays.copyOfRange(message, Math.min(1, message.length), message.length));
    }

    public String name()
    {
        return OcgConstants.msgName(type);
    }

    /** Total encoded size including the type byte. */
    public int size()
    {
        return payload.length + 1;
    }

    /**
     * The player a prompt is directed at.
     * <p>
     * Phase-0 heuristic: the interactive MSG_SELECT / MSG_ANNOUNCE family
     * carries the prompted player as the first payload byte. Replaced by the
     * typed decoders in Phase 1 (a few message types deviate, e.g.
     * MSG_SELECT_SUM needs verifying against the core source).
     */
    public int promptedPlayer()
    {
        return payload.length > 0 ? payload[0] & 0xFF : 0;
    }
}
