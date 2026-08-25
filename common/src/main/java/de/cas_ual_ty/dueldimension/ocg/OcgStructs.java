package de.cas_ual_ty.dueldimension.ocg;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA mappings of the structs and callback types in ocgapi_types.h
 * (edo9300/ygopro-core, API version 11.0).
 * <p>
 * Unsigned C fields are mapped to the same-width signed Java type;
 * treat them as unsigned when interpreting values.
 */
public final class OcgStructs
{
    private OcgStructs()
    {
    }

    /**
     * OCG_CardData. Filled by the {@link CardReader} callback with static
     * card data from the database. {@code setcodes} points to a
     * 0-terminated uint16 array which must remain valid until
     * {@link CardReaderDone} is invoked for this struct.
     */
    @Structure.FieldOrder({"code", "alias", "setcodes", "type", "level", "attribute", "race", "attack", "defense", "lscale", "rscale", "link_marker"})
    public static class OcgCardData extends Structure
    {
        public int code;
        public int alias;
        public Pointer setcodes;
        public int type;
        public int level;
        public int attribute;
        public long race;
        public int attack;
        public int defense;
        public int lscale;
        public int rscale;
        public int link_marker;

        public OcgCardData()
        {
        }

        public OcgCardData(Pointer p)
        {
            super(p);
            read();
        }
    }

    /** OCG_Player: starting conditions of one team. */
    @Structure.FieldOrder({"startingLP", "startingDrawCount", "drawCountPerTurn"})
    public static class OcgPlayer extends Structure
    {
        public int startingLP;
        public int startingDrawCount;
        public int drawCountPerTurn;
    }

    /**
     * OCG_DataReader: called by the core whenever it needs static data for a
     * card code. Implementations write into {@code data} (and must keep any
     * {@code setcodes} buffer alive until {@link CardReaderDone}).
     */
    public interface CardReader extends Callback
    {
        void invoke(Pointer payload, int code, OcgCardData data);
    }

    /** OCG_DataReaderDone: called when the core is done with a card data struct. */
    public interface CardReaderDone extends Callback
    {
        void invoke(Pointer payload, OcgCardData data);
    }

    /**
     * OCG_ScriptReader: called when the core needs a Lua script (e.g.
     * "c12345678.lua"). The implementation must itself call
     * {@code OCG_LoadScript} with the script contents and return 1 on
     * success, 0 on failure.
     */
    public interface ScriptReader extends Callback
    {
        int invoke(Pointer payload, Pointer duel, String name);
    }

    /** OCG_LogHandler: receives log/error messages from the core and scripts. */
    public interface LogHandler extends Callback
    {
        void invoke(Pointer payload, String message, int type);
    }

    /**
     * OCG_DuelOptions passed to OCG_CreateDuel.
     * The seed must not be all zeroes (OCG_DUEL_CREATION_NULL_RNG_SEED).
     */
    @Structure.FieldOrder({"seed", "flags", "team1", "team2", "cardReader", "payload1", "scriptReader", "payload2", "logHandler", "payload3", "cardReaderDone", "payload4", "enableUnsafeLibraries"})
    public static class OcgDuelOptions extends Structure
    {
        public long[] seed = new long[4];
        public long flags;
        public OcgPlayer team1;
        public OcgPlayer team2;
        public CardReader cardReader;
        public Pointer payload1;
        public ScriptReader scriptReader;
        public Pointer payload2;
        public LogHandler logHandler;
        public Pointer payload3;
        public CardReaderDone cardReaderDone;
        public Pointer payload4;
        public byte enableUnsafeLibraries;
    }

    /** OCG_NewCardInfo passed to OCG_DuelNewCard. */
    @Structure.FieldOrder({"team", "duelist", "code", "con", "loc", "seq", "pos"})
    public static class OcgNewCardInfo extends Structure
    {
        public byte team;
        public byte duelist;
        public int code;
        public byte con;
        public int loc;
        public int seq;
        public int pos;
    }

    /** OCG_QueryInfo passed to the OCG_DuelQuery* functions. */
    @Structure.FieldOrder({"flags", "con", "loc", "seq", "overlay_seq"})
    public static class OcgQueryInfo extends Structure
    {
        public int flags;
        public byte con;
        public int loc;
        public int seq;
        public int overlay_seq;
    }
}
