package de.cas_ual_ty.ydm.ocg;

import com.sun.jna.ptr.IntByReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    public static void main(String[] args) throws Exception
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

        OcgDuel.CardProvider cards = code -> null; // no card database yet

        OcgDuel.ScriptProvider scripts = name ->
        {
            if(scriptsDir == null)
            {
                return null;
            }
            // CardScripts layout: constant.lua/utility.lua at the root, card scripts in official/
            for(Path candidate : new Path[] {scriptsDir.resolve(name), scriptsDir.resolve("official").resolve(name)})
            {
                if(Files.isRegularFile(candidate))
                {
                    try
                    {
                        return Files.readAllBytes(candidate);
                    }
                    catch(Exception e)
                    {
                        System.err.println("Failed reading " + candidate + ": " + e);
                        return null;
                    }
                }
            }
            return null;
        };

        OcgDuel.LogSink log = (message, type) -> System.out.println("[core:" + type + "] " + message);

        long[] seed = {0x1234567890ABCDEFL, 0xFEDCBA0987654321L, 0xDEADBEEFDEADBEEFL, 0xCAFEBABECAFEBABEL};

        System.out.println("Creating duel (MR5, 8000 LP)...");
        try(OcgDuel duel = OcgDuel.create(api, seed, OcgConstants.DUEL_MODE_MR5,
            OcgDuel.PlayerConfig.DEFAULT, OcgDuel.PlayerConfig.DEFAULT, cards, scripts, log))
        {
            System.out.println("Duel created.");

            if(scriptsDir != null)
            {
                // Load the base scripts the card scripts depend on.
                for(String name : new String[] {"constant.lua", "utility.lua"})
                {
                    byte[] content = scripts.load(name);
                    if(content == null)
                    {
                        System.err.println("Base script missing from " + scriptsDir + ": " + name);
                    }
                    else
                    {
                        System.out.println("Loaded " + name + ": " + duel.loadScript(name, content));
                    }
                }
            }

            System.out.println("Starting duel (empty decks)...");
            duel.start();

            for(int i = 0; i < 16; i++)
            {
                int status = duel.process();
                List<byte[]> messages = duel.getMessages();
                System.out.println("process() -> " + statusName(status) + ", " + messages.size() + " message(s)");
                for(byte[] message : messages)
                {
                    int type = message.length > 0 ? message[0] & 0xFF : -1;
                    System.out.println("  " + OcgConstants.msgName(type) + " (" + message.length + " bytes)");
                }
                if(status != OcgConstants.DUEL_STATUS_CONTINUE)
                {
                    break;
                }
            }
        }

        System.out.println("Duel destroyed. Spike complete.");
    }

    private static String statusName(int status)
    {
        return switch(status)
        {
            case OcgConstants.DUEL_STATUS_END -> "END";
            case OcgConstants.DUEL_STATUS_AWAITING -> "AWAITING";
            case OcgConstants.DUEL_STATUS_CONTINUE -> "CONTINUE";
            default -> "UNKNOWN(" + status + ")";
        };
    }
}
