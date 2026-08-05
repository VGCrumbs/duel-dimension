package de.cas_ual_ty.ydm.ocg;

import com.sun.jna.ptr.IntByReference;

import java.nio.file.Path;

/**
 * Standalone smoke test for the ocgcore binding — no Minecraft involved.
 * <p>
 * Usage: OcgSpike &lt;path-to-ocgcore-library&gt; [path-to-scripts-dir]
 * <p>
 * The scripts dir is a checkout of ProjectIgnis/CardScripts (contains
 * constant.lua, utility.lua, official/c*.lua). Without it, the spike still
 * verifies that the library loads, reports its version, and that a duel can
 * be created and destroyed — which exercises the entire struct layout and
 * callback wiring.
 */
public class OcgSpike
{
    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.err.println("Usage: OcgSpike <path-to-ocgcore-library> [path-to-scripts-dir]");
            System.exit(2);
        }

        Path libraryFile = Path.of(args[0]);
        Path scriptsDir = args.length > 1 ? Path.of(args[1]) : null;

        System.out.println("Loading native core: " + libraryFile.toAbsolutePath());
        OcgApi api = OcgApi.load(libraryFile);

        IntByReference major = new IntByReference();
        IntByReference minor = new IntByReference();
        api.OCG_GetVersion(major, minor);
        System.out.println("ocgcore API version: " + major.getValue() + "." + minor.getValue()
            + " (binding written against " + OcgConstants.VERSION_MAJOR + "." + OcgConstants.VERSION_MINOR + ")");
        if(major.getValue() != OcgConstants.VERSION_MAJOR)
        {
            System.err.println("WARNING: major version mismatch, expect broken struct layouts!");
        }

        // Aborts at the first prompt (no bot yet).
        ResponseSource aborting = prompt ->
        {
            System.out.println("Prompted: " + prompt.name() + " for player " + prompt.promptedPlayer() + " — no responder, aborting run.");
            return null;
        };

        // Same, but also logs every observed message (registered for player 0
        // only, so the shared stream is printed once).
        ResponseSource abortingLogged = new ResponseSource()
        {
            @Override
            public void observe(RawMessage message)
            {
                System.out.println("  " + message.name() + " (" + message.size() + " bytes)");
            }

            @Override
            public byte[] respond(RawMessage prompt)
            {
                return aborting.respond(prompt);
            }
        };

        System.out.println("Running empty-deck duel (MR5, 8000 LP)...");
        HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
            .seed(new long[] {0x1234567890ABCDEFL, 0xFEDCBA0987654321L, 0xDEADBEEFDEADBEEFL, 0xCAFEBABECAFEBABEL})
            .flags(OcgConstants.DUEL_MODE_MR5)
            .scripts(scriptsDir != null ? HeadlessDuelRunner.cardScriptsDirectory(scriptsDir) : name -> null)
            .log((message, type) -> System.out.println("[core:" + type + "] " + message))
            .responder(0, abortingLogged)
            .responder(1, aborting)
            .build()
            .run(64);

        System.out.println("Steps: " + trace.steps
            + ", messages: " + trace.messages.size()
            + ", completed: " + trace.completed
            + ", result: " + (trace.result != null
                ? "winner=" + trace.result.winner() + " reason=" + trace.result.reason()
                : "none"));
        System.out.println("Spike complete.");
    }
}
