package de.cas_ual_ty.dueldimension.ocg.msg;

import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Decoder fixtures are hand-built to the layouts in ygopro-core's writers;
 * if a fixture disagrees with the real stream, the live test at the bottom
 * catches it via MSG_RETRY / decode failures.
 */
class DuelMessageTest
{
    private static RawMessage raw(int type, byte[] payload)
    {
        return new RawMessage(type, payload);
    }

    private static byte[] bytes(java.util.function.Consumer<ByteBuffer> writer)
    {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        writer.accept(buffer);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    @Test
    void decodesSelectIdleCmd()
    {
        byte[] payload = bytes(b ->
        {
            b.put((byte)1); // player
            b.putInt(1).putInt(4007).put((byte)1).put((byte)0x02).putInt(3); // summonable ×1
            b.putInt(0); // spsummonable
            b.putInt(1).putInt(4008).put((byte)1).put((byte)0x04).put((byte)2); // repositionable ×1 (u8 seq!)
            b.putInt(0); // msetable
            b.putInt(0); // ssetable
            b.putInt(1).putInt(4009).put((byte)1).put((byte)0x08).putInt(0).putLong(1160L).put((byte)1); // activatable ×1
            b.put((byte)1).put((byte)1).put((byte)0); // toBp, toEp, shuffle
        });

        DuelMessage.SelectIdleCmd cmd = (DuelMessage.SelectIdleCmd)DuelMessage.decode(raw(OcgConstants.MSG_SELECT_IDLECMD, payload));
        assertEquals(1, cmd.player());
        assertEquals(4007, cmd.summonable().get(0).code());
        assertEquals(3, cmd.summonable().get(0).sequence());
        assertTrue(cmd.spSummonable().isEmpty());
        assertEquals(2, cmd.repositionable().get(0).sequence());
        assertEquals(1160L, cmd.activatable().get(0).description());
        assertTrue(cmd.toBattle());
        assertTrue(cmd.toEnd());
        assertFalse(cmd.canShuffle());
    }

    @Test
    void decodesSelectCard()
    {
        byte[] payload = bytes(b ->
        {
            b.put((byte)0).put((byte)1); // player, cancelable
            b.putInt(1).putInt(2); // min, max
            b.putInt(2); // count
            b.putInt(1001).put((byte)0).put((byte)0x02).putInt(0).putInt(0); // card + loc_info
            b.putInt(1002).put((byte)1).put((byte)0x04).putInt(4).putInt(5); // card + loc_info
        });

        DuelMessage.SelectCard select = (DuelMessage.SelectCard)DuelMessage.decode(raw(OcgConstants.MSG_SELECT_CARD, payload));
        assertEquals(0, select.player());
        assertTrue(select.cancelable());
        assertEquals(1, select.min());
        assertEquals(2, select.max());
        assertEquals(2, select.cards().size());
        assertEquals(new CardLocation(1, 0x04, 4, 5), select.cards().get(1).loc());
    }

    @Test
    void decodesSelectChainAndPlace()
    {
        byte[] chainPayload = bytes(b ->
        {
            b.put((byte)0).put((byte)0).put((byte)1); // player, spe_count, forced
            b.putInt(0x100).putInt(0x200); // hint timings
            b.putInt(1); // count
            b.putInt(53129443).put((byte)0).put((byte)0x08).putInt(2).putInt(0x8).putLong(300L).put((byte)0);
        });
        DuelMessage.SelectChain chain = (DuelMessage.SelectChain)DuelMessage.decode(raw(OcgConstants.MSG_SELECT_CHAIN, chainPayload));
        assertTrue(chain.forced());
        assertEquals(53129443, chain.chains().get(0).code());
        assertEquals(0x100L, chain.hintTimingSelf());

        byte[] placePayload = bytes(b -> b.put((byte)1).put((byte)1).putInt(0xFF00FF));
        DuelMessage.SelectPlace place = (DuelMessage.SelectPlace)DuelMessage.decode(raw(OcgConstants.MSG_SELECT_PLACE, placePayload));
        assertEquals(1, place.player());
        assertEquals(1, place.count());
        assertEquals(0xFF00FFL, place.forbiddenMask());
        assertFalse(place.disfield());
    }

    @Test
    void decodesInfoMessages()
    {
        DuelMessage.Move move = (DuelMessage.Move)DuelMessage.decode(raw(OcgConstants.MSG_MOVE, bytes(b ->
        {
            b.putInt(46986414); // Dark Magician
            b.put((byte)0).put((byte)0x02).putInt(4).putInt(0); // from hand
            b.put((byte)0).put((byte)0x04).putInt(2).putInt(0x1); // to mzone faceup atk
            b.putInt(0);
        })));
        assertEquals(46986414, move.code());
        assertEquals(0x04, move.to().location());

        DuelMessage.Draw draw = (DuelMessage.Draw)DuelMessage.decode(raw(OcgConstants.MSG_DRAW, bytes(b ->
        {
            b.put((byte)1).putInt(2);
            b.putInt(1111).putInt(0x8);
            b.putInt(2222).putInt(0x8);
        })));
        assertEquals(1, draw.player());
        assertEquals(2222, draw.cards().get(1).code());

        assertEquals(3, ((DuelMessage.NewTurn)DuelMessage.decode(raw(OcgConstants.MSG_NEW_TURN, bytes(b -> b.put((byte)3))))).player());
        assertEquals(0x4, ((DuelMessage.NewPhase)DuelMessage.decode(raw(OcgConstants.MSG_NEW_PHASE, bytes(b -> b.putShort((short)0x4))))).phase());
        assertEquals(800, ((DuelMessage.Damage)DuelMessage.decode(raw(OcgConstants.MSG_DAMAGE, bytes(b -> b.put((byte)0).putInt(800))))).amount());
    }

    @Test
    void strictDecodingRejectsTrailingBytes()
    {
        byte[] payload = bytes(b -> b.put((byte)0).put((byte)0xFF)); // NewTurn + 1 junk byte
        assertThrows(MsgReader.MsgFormatException.class,
            () -> DuelMessage.decode(raw(OcgConstants.MSG_NEW_TURN, payload)));
    }

    @Test
    void unknownTypesArePreservedNotRejected()
    {
        RawMessage odd = raw(250, new byte[] {1, 2, 3});
        assertInstanceOf(DuelMessage.Unknown.class, DuelMessage.decode(odd));
    }

    @Test
    void responseEncodings()
    {
        assertArrayEquals(new byte[] {7, 0, 0, 0}, Responses.idleToEnd());
        assertArrayEquals(new byte[] {3, 0, 0, 0}, Responses.battleToEnd());
        assertArrayEquals(new byte[] {2, 0, 2, 0}, Responses.idleReposition(2));
        assertArrayEquals(new byte[] {-1, -1, -1, -1}, Responses.chainDecline());
        assertArrayEquals(new byte[] {2, 0, 0, 0, 2, 0, 0, 0, 0, 3}, Responses.selectCards(0, 3));
        assertArrayEquals(new byte[] {0, 4, 2, 1, 4, 3}, Responses.places(
            new Responses.Place(0, 0x04, 2), new Responses.Place(1, 0x04, 3)));
    }

    // ---- live: the engine itself validates our encodings (RETRY = failure) ----

    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    @Test
    void liveStreamDecodesAndIdleResponseIsAccepted()
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");

        var abortedOn = new java.util.concurrent.atomic.AtomicReference<DuelMessage>();
        ResponseSource endTurner = prompt ->
        {
            DuelMessage decoded = DuelMessage.decode(prompt);
            if(decoded instanceof DuelMessage.SelectIdleCmd idle && idle.toEnd())
            {
                return Responses.idleToEnd();
            }
            if(decoded instanceof DuelMessage.SelectChain chain && !chain.forced())
            {
                return Responses.chainDecline();
            }
            abortedOn.set(decoded);
            return null;
        };

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(OcgApi.load(lib()))
            .seed(new long[] {11, 22, 33, 44})
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .responder(0, endTurner)
            .responder(1, endTurner)
            .stopOnWin(false) // empty decks: WIN fires immediately, zombie turns are the prompt source
            .build()
            .run(64);

        // Every message decodes without a format exception, none as garbage.
        for(RawMessage message : trace.messages)
        {
            DuelMessage.decode(message);
        }

        assertFalse(trace.sawMessage(OcgConstants.MSG_RETRY),
            "core rejected a response we encoded — layout bug");
        assertTrue(trace.responses.size() >= 2,
            "expected several accepted end-turn responses, got " + trace.responses.size()
                + " (aborted on: " + abortedOn.get() + ")");
        assertTrue(trace.messages.stream().filter(m -> m.type() == OcgConstants.MSG_NEW_TURN).count() >= 2,
            "turns should pass once prompts are answered");
    }
}
