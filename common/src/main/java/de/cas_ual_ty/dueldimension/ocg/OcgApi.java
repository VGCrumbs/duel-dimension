package de.cas_ual_ty.dueldimension.ocg;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.file.Path;
import java.util.Map;

import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgDuelOptions;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgNewCardInfo;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs.OcgQueryInfo;

/**
 * Direct JNA mapping of ocgapi.h (edo9300/ygopro-core, API version 11.0).
 * <p>
 * Prefer the high-level {@link OcgDuel} wrapper over calling this
 * interface directly; it handles callback lifetimes and buffer copying.
 */
public interface OcgApi extends Library
{
    /** Load the native core from an explicit file path. */
    static OcgApi load(Path libraryFile)
    {
        return Native.load(libraryFile.toAbsolutePath().toString(), OcgApi.class,
            Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
    }

    /** Load the native core by name from the system library path ("ocgcore"). */
    static OcgApi load()
    {
        return Native.load("ocgcore", OcgApi.class,
            Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
    }

    /* --- Core information --- */

    void OCG_GetVersion(IntByReference major, IntByReference minor);

    /* --- Duel creation and destruction --- */

    /** @return an {@code OCG_DuelCreationStatus} value (see OcgConstants.DUEL_CREATION_*). */
    int OCG_CreateDuel(PointerByReference outDuel, OcgDuelOptions options);

    void OCG_DestroyDuel(Pointer duel);

    void OCG_DuelNewCard(Pointer duel, OcgNewCardInfo info);

    void OCG_StartDuel(Pointer duel);

    /* --- Duel processing and querying --- */

    /** @return an {@code OCG_DuelStatus} value (see OcgConstants.DUEL_STATUS_*). */
    int OCG_DuelProcess(Pointer duel);

    /**
     * Returns the accumulated message buffer: a sequence of
     * {@code [uint32 length][message bytes]} entries, each message starting
     * with a uint8 message type. The buffer is only valid until the next
     * core call — copy it out immediately.
     */
    Pointer OCG_DuelGetMessage(Pointer duel, IntByReference length);

    void OCG_DuelSetResponse(Pointer duel, byte[] buffer, int length);

    /** @return nonzero on success. */
    int OCG_LoadScript(Pointer duel, byte[] buffer, int length, String name);

    int OCG_DuelQueryCount(Pointer duel, byte team, int loc);

    /** Buffer valid until the next query call — copy it out immediately. */
    Pointer OCG_DuelQuery(Pointer duel, IntByReference length, OcgQueryInfo info);

    /** Buffer valid until the next query call — copy it out immediately. */
    Pointer OCG_DuelQueryLocation(Pointer duel, IntByReference length, OcgQueryInfo info);

    /** Buffer valid until the next query call — copy it out immediately. */
    Pointer OCG_DuelQueryField(Pointer duel, IntByReference length);
}
