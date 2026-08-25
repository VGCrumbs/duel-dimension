package de.cas_ual_ty.dueldimension.ocg;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgCardData;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgDuelOptions;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgNewCardInfo;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgPlayer;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgQueryInfo;

/**
 * High-level wrapper around one ocgcore duel instance.
 * <p>
 * Not thread safe: all calls for one duel (including the callbacks the core
 * makes back into Java) happen on whichever thread calls into the core, so
 * drive a duel from a single thread.
 * <p>
 * Owns the JNA callback objects for the lifetime of the duel — the core holds
 * raw function pointers to them, so they must not be garbage collected while
 * the duel exists. Also owns the native memory backing the setcodes arrays
 * handed to the core via the card reader, freeing each when the core signals
 * it is done with it.
 */
public class OcgDuel implements AutoCloseable
{
    /** Provides static card data by passcode. Return null for unknown codes. */
    @FunctionalInterface
    public interface CardProvider
    {
        OcgCard get(int code);
    }

    /** Provides Lua script contents by name (e.g. "c12345678.lua"). Return null if not found. */
    @FunctionalInterface
    public interface ScriptProvider
    {
        byte[] load(String name);
    }

    /** Receives log messages from the core (type is one of OcgConstants.LOG_TYPE_*). */
    @FunctionalInterface
    public interface LogSink
    {
        void log(String message, int type);
    }

    /** Starting conditions of one team. */
    public record PlayerConfig(int startingLP, int startingDrawCount, int drawCountPerTurn)
    {
        public static final PlayerConfig DEFAULT = new PlayerConfig(8000, 5, 1);
    }

    private final OcgApi api;
    private Pointer duel;

    // Strong references: the native side only holds raw function pointers.
    private final OcgDuelOptions options;

    // setcodes buffers currently lent to the core, keyed by native address.
    private final Map<Long, Memory> outstandingSetcodes = new HashMap<>();

    private OcgDuel(OcgApi api, OcgDuelOptions options)
    {
        this.api = api;
        this.options = options;
    }

    /**
     * Creates a new duel. The seed must not be all zeroes.
     *
     * @throws OcgException if the core rejects the duel creation
     */
    public static OcgDuel create(OcgApi api, long[] seed, long flags, PlayerConfig team1, PlayerConfig team2,
        CardProvider cards, ScriptProvider scripts, LogSink log)
    {
        OcgDuelOptions options = new OcgDuelOptions();
        System.arraycopy(seed, 0, options.seed, 0, Math.min(seed.length, 4));
        options.flags = flags;
        options.team1 = playerStruct(team1);
        options.team2 = playerStruct(team2);
        options.enableUnsafeLibraries = 0;

        OcgDuel wrapper = new OcgDuel(api, options);

        options.cardReader = (payload, code, data) ->
        {
            OcgCard card = cards.get(code);
            if(card != null)
            {
                wrapper.fillCardData(data, card);
            }
            else
            {
                log.log("Card reader: unknown card code " + Integer.toUnsignedString(code), OcgConstants.LOG_TYPE_ERROR);
                data.code = 0;
            }
            data.write();
        };

        options.cardReaderDone = (payload, data) ->
        {
            if(data.setcodes != null)
            {
                wrapper.outstandingSetcodes.remove(Pointer.nativeValue(data.setcodes));
            }
        };

        options.scriptReader = (payload, duelPtr, name) ->
        {
            byte[] content = scripts.load(name);
            if(content == null)
            {
                log.log("Script not found: " + name, OcgConstants.LOG_TYPE_ERROR);
                return 0;
            }
            return api.OCG_LoadScript(duelPtr, content, content.length, name);
        };

        options.logHandler = (payload, message, type) -> log.log(message, type);

        PointerByReference outDuel = new PointerByReference();
        int status = api.OCG_CreateDuel(outDuel, options);
        if(status != OcgConstants.DUEL_CREATION_SUCCESS)
        {
            throw new OcgException("OCG_CreateDuel failed with status " + status);
        }

        wrapper.duel = outDuel.getValue();
        return wrapper;
    }

    private static OcgPlayer playerStruct(PlayerConfig config)
    {
        OcgPlayer player = new OcgPlayer();
        player.startingLP = config.startingLP();
        player.startingDrawCount = config.startingDrawCount();
        player.drawCountPerTurn = config.drawCountPerTurn();
        return player;
    }

    private void fillCardData(OcgCardData data, OcgCard card)
    {
        data.code = card.code();
        data.alias = card.alias();
        data.type = card.type();
        data.level = card.level();
        data.attribute = card.attribute();
        data.race = card.race();
        data.attack = card.attack();
        data.defense = card.defense();
        data.lscale = card.lscale();
        data.rscale = card.rscale();
        data.link_marker = card.linkMarker();

        int[] setcodes = card.setcodes() != null ? card.setcodes() : OcgCard.NO_SETCODES;
        // 0-terminated uint16 array; must stay valid until cardReaderDone.
        Memory memory = new Memory((setcodes.length + 1) * 2L);
        for(int i = 0; i < setcodes.length; i++)
        {
            memory.setShort(i * 2L, (short)setcodes[i]);
        }
        memory.setShort(setcodes.length * 2L, (short)0);
        outstandingSetcodes.put(Pointer.nativeValue(memory), memory);
        data.setcodes = memory;
    }

    /** Adds a card to the duel. Call between create() and start(). */
    public void newCard(int team, int duelist, int code, int controller, int location, int sequence, int position)
    {
        checkOpen();
        OcgNewCardInfo info = new OcgNewCardInfo();
        info.team = (byte)team;
        info.duelist = (byte)duelist;
        info.code = code;
        info.con = (byte)controller;
        info.loc = location;
        info.seq = sequence;
        info.pos = position;
        api.OCG_DuelNewCard(duel, info);
    }

    /** Loads a script into this duel directly (outside a script reader callback). */
    public boolean loadScript(String name, byte[] content)
    {
        checkOpen();
        return api.OCG_LoadScript(duel, content, content.length, name) != 0;
    }

    public void start()
    {
        checkOpen();
        api.OCG_StartDuel(duel);
    }

    /** @return one of OcgConstants.DUEL_STATUS_END / _AWAITING / _CONTINUE */
    public int process()
    {
        checkOpen();
        return api.OCG_DuelProcess(duel);
    }

    /**
     * Drains the core's message buffer, already split into individual
     * messages. Each message's first byte is its type (OcgConstants.MSG_*).
     */
    public List<byte[]> getMessages()
    {
        checkOpen();
        IntByReference length = new IntByReference();
        Pointer buffer = api.OCG_DuelGetMessage(duel, length);
        if(buffer == null || length.getValue() <= 0)
        {
            return List.of();
        }
        return splitMessages(buffer.getByteArray(0, length.getValue()));
    }

    /** Sends the player response the core is AWAITING. */
    public void setResponse(byte[] response)
    {
        checkOpen();
        api.OCG_DuelSetResponse(duel, response, response.length);
    }

    public int queryCount(int team, int location)
    {
        checkOpen();
        return api.OCG_DuelQueryCount(duel, (byte)team, location);
    }

    public byte[] query(OcgQueryInfo info)
    {
        checkOpen();
        IntByReference length = new IntByReference();
        Pointer buffer = api.OCG_DuelQuery(duel, length, info);
        return copyOut(buffer, length.getValue());
    }

    public byte[] queryLocation(OcgQueryInfo info)
    {
        checkOpen();
        IntByReference length = new IntByReference();
        Pointer buffer = api.OCG_DuelQueryLocation(duel, length, info);
        return copyOut(buffer, length.getValue());
    }

    public byte[] queryField()
    {
        checkOpen();
        IntByReference length = new IntByReference();
        Pointer buffer = api.OCG_DuelQueryField(duel, length);
        return copyOut(buffer, length.getValue());
    }

    @Override
    public void close()
    {
        if(duel != null)
        {
            api.OCG_DestroyDuel(duel);
            duel = null;
            outstandingSetcodes.clear();
        }
    }

    public boolean isOpen()
    {
        return duel != null;
    }

    private void checkOpen()
    {
        if(duel == null)
        {
            throw new IllegalStateException("Duel already destroyed");
        }
    }

    private static byte[] copyOut(Pointer buffer, int length)
    {
        return buffer == null || length <= 0 ? new byte[0] : buffer.getByteArray(0, length);
    }

    /** Splits a raw core buffer of [uint32 length][message] entries. */
    public static List<byte[]> splitMessages(byte[] raw)
    {
        List<byte[]> messages = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        while(buffer.remaining() >= 4)
        {
            int length = buffer.getInt();
            if(length < 0 || length > buffer.remaining())
            {
                throw new OcgException("Corrupt message buffer: length " + length + " with " + buffer.remaining() + " bytes remaining");
            }
            byte[] message = new byte[length];
            buffer.get(message);
            messages.add(message);
        }
        return messages;
    }

    public static class OcgException extends RuntimeException
    {
        public OcgException(String message)
        {
            super(message);
        }
    }
}
