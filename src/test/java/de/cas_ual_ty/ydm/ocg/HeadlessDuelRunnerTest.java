package de.cas_ual_ty.ydm.ocg;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Engine-backed tests skip (via assumptions) when the native library or the
 * CardScripts checkout is absent — paths come from the ocg.lib / ocg.scripts
 * system properties, forwarded from -PocgLib / -PocgScripts by build.gradle.
 */
class HeadlessDuelRunnerTest
{
    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    @Test
    void corruptMessageBufferThrows()
    {
        // Claims 5 bytes, provides 1.
        byte[] corrupt = {5, 0, 0, 0, 42};
        assertThrows(OcgDuel.OcgException.class, () -> OcgDuel.splitMessages(corrupt));
    }

    @Test
    void rawMessageSplitsTypeAndPayload()
    {
        RawMessage message = RawMessage.of(new byte[] {(byte)OcgConstants.MSG_WIN, 1, 2});
        assertEquals(OcgConstants.MSG_WIN, message.type());
        assertEquals(2, message.payload().length);
        assertEquals(3, message.size());
        assertEquals(1, message.promptedPlayer());
    }

    @Test
    void nativeCoreHasExpectedVersion()
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present: " + lib());

        OcgApi api = OcgApi.load(lib());
        com.sun.jna.ptr.IntByReference major = new com.sun.jna.ptr.IntByReference();
        com.sun.jna.ptr.IntByReference minor = new com.sun.jna.ptr.IntByReference();
        api.OCG_GetVersion(major, minor);
        assertEquals(OcgConstants.VERSION_MAJOR, major.getValue(), "struct layouts differ across major versions");
    }

    @Test
    void emptyDeckDuelProducesTraceAndDeckoutWin()
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present: " + lib());
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present: " + scripts());

        OcgApi api = OcgApi.load(lib());
        ResponseSource aborting = prompt -> null;

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {0xA11CE5EEDL, 2, 3, 4})
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .responder(0, aborting)
            .responder(1, aborting)
            .build()
            .run(64);

        assertFalse(trace.messages.isEmpty(), "no messages at all — engine did not run");
        assertTrue(trace.sawMessage(OcgConstants.MSG_NEW_TURN), "duel never started a turn");
        assertTrue(trace.sawMessage(OcgConstants.MSG_WIN), "empty decks must deck-out");
        assertNotNull(trace.result, "MSG_WIN payload was not decoded");
    }

    @Test
    void observersReceiveTheFullStream()
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present: " + lib());
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present: " + scripts());

        OcgApi api = OcgApi.load(lib());

        var counter = new ResponseSource()
        {
            int observed;
            boolean started;
            boolean ended;

            @Override
            public void onDuelStart(int playerIndex)
            {
                started = true;
            }

            @Override
            public void observe(RawMessage message)
            {
                observed++;
            }

            @Override
            public byte[] respond(RawMessage prompt)
            {
                return null;
            }

            @Override
            public void onDuelEnd(HeadlessDuelRunner.DuelResult result)
            {
                ended = true;
            }
        };

        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {5, 6, 7, 8})
            .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
            .responder(0, counter)
            .responder(1, prompt -> null)
            .build()
            .run(64);

        assertTrue(counter.started, "onDuelStart not called");
        assertTrue(counter.ended, "onDuelEnd not called");
        assertEquals(trace.messages.size(), counter.observed, "observer missed messages");
    }
}
