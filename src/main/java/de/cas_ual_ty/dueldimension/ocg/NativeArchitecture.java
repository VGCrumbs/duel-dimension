package de.cas_ual_ty.dueldimension.ocg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What architecture a native library was built for, read from its own header.
 * <p>
 * <b>Why this is needed.</b> The engine is chosen by looking for a file, and a
 * file that exists is not a file that loads. EDOPro ships a 32-bit
 * {@code ocgcore.dll} in its default Windows build; Minecraft 26.2 requires a
 * 64-bit Java 25. Picking that copy because it is next to the scripts produced
 * an {@code UnsatisfiedLinkError: %1 is not a valid Win32 application} at the
 * moment a player right-clicked a duelist -- and because that is an
 * {@link Error} rather than an exception, it went straight past the guard that
 * was supposed to turn engine trouble into a polite message and took the server
 * thread down with it.
 * <p>
 * So the choice is made before the load: a core whose header disagrees with
 * this JVM is not offered, and the player is told which one they have rather
 * than shown a crash.
 * <p>
 * <b>Unknown means yes.</b> Everything here fails open -- an unreadable file, a
 * format not listed, a machine value not in the table -- because refusing to
 * load a perfectly good library because this could not parse it would be a
 * worse failure than the one it is preventing. The only answer that stops a
 * load is a positively identified mismatch.
 */
public final class NativeArchitecture
{
    /** What a header said, or null for "could not tell". */
    public record Arch(int bits, String family)
    {
        @Override
        public String toString()
        {
            return bits + "-bit" + (family == null ? "" : " " + family);
        }
    }

    /**
     * Read once per file. Discovery runs on every duel request and on every
     * status query, and this is otherwise a disk read each time.
     */
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    private static final Object UNKNOWN = new Object();

    private NativeArchitecture()
    {
    }

    /** This JVM's own architecture, which is what a library has to match. */
    public static Arch jvm()
    {
        String model = System.getProperty("sun.arch.data.model", "");
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        int bits = model.equals("32") ? 32
            : model.equals("64") ? 64
            : osArch.contains("64") ? 64 : 32;
        return new Arch(bits, family(osArch));
    }

    private static String family(String osArch)
    {
        if(osArch.startsWith("aarch64") || osArch.startsWith("arm64"))
        {
            return "arm";
        }
        if(osArch.startsWith("arm"))
        {
            return "arm";
        }
        if(osArch.contains("86") || osArch.contains("amd64"))
        {
            return "x86";
        }
        return null;
    }

    /** What the file says it is, or null when it cannot be told. */
    public static Arch of(Path library)
    {
        if(library == null)
        {
            return null;
        }
        Object cached = CACHE.computeIfAbsent(library.toAbsolutePath().toString(), key ->
        {
            Arch read = readHeader(library);
            return read == null ? UNKNOWN : read;
        });
        return cached == UNKNOWN ? null : (Arch)cached;
    }

    /**
     * @return null when this library can be loaded here (or cannot be judged),
     *         else a sentence naming the mismatch
     */
    public static String mismatch(Path library)
    {
        Arch file = of(library);
        if(file == null)
        {
            return null;
        }
        Arch jvm = jvm();
        if(file.bits() != jvm.bits())
        {
            return "the rules engine at " + library.toAbsolutePath() + " is " + file
                + " but this game runs " + jvm + " Java";
        }
        if(file.family() != null && jvm.family() != null && !file.family().equals(jvm.family()))
        {
            return "the rules engine at " + library.toAbsolutePath() + " is built for "
                + file.family() + " but this game runs on " + jvm.family();
        }
        return null;
    }

    /** Whether this library is worth offering to the loader at all. */
    public static boolean loadableHere(Path library)
    {
        return mismatch(library) == null;
    }

    private static Arch readHeader(Path library)
    {
        if(library == null || !Files.isRegularFile(library))
        {
            return null;
        }

        byte[] head = new byte[4096];
        int read;
        try(InputStream in = Files.newInputStream(library))
        {
            read = in.readNBytes(head, 0, head.length);
        }
        catch(IOException | RuntimeException unreadable)
        {
            return null;
        }

        if(read < 64)
        {
            return null;
        }

        // Windows PE: "MZ", then a file offset at 0x3C pointing at "PE\0\0",
        // whose Machine field is two bytes further on.
        if(head[0] == 'M' && head[1] == 'Z')
        {
            int peOffset = intAt(head, 0x3C, true);
            if(peOffset > 0 && peOffset + 6 <= read
                && head[peOffset] == 'P' && head[peOffset + 1] == 'E'
                && head[peOffset + 2] == 0 && head[peOffset + 3] == 0)
            {
                int machine = shortAt(head, peOffset + 4, true);
                return switch(machine)
                {
                    case 0x014C -> new Arch(32, "x86");
                    case 0x8664 -> new Arch(64, "x86");
                    case 0xAA64 -> new Arch(64, "arm");
                    case 0x01C0, 0x01C4 -> new Arch(32, "arm");
                    default -> null;
                };
            }
            return null;
        }

        // ELF: magic, then EI_CLASS (32/64) and EI_DATA (endianness), then
        // e_machine sixteen bytes in.
        if(head[0] == 0x7F && head[1] == 'E' && head[2] == 'L' && head[3] == 'F')
        {
            int bits = head[4] == 2 ? 64 : head[4] == 1 ? 32 : 0;
            if(bits == 0)
            {
                return null;
            }
            boolean little = head[5] == 1;
            int machine = shortAt(head, 18, little);
            String family = switch(machine)
            {
                case 3, 62 -> "x86";
                case 40, 183 -> "arm";
                default -> null;
            };
            return new Arch(bits, family);
        }

        // Mach-O, both byte orders. A universal binary (0xCAFEBABE) holds
        // several architectures at once and is deliberately not judged.
        int magic = intAt(head, 0, false);
        return switch(magic)
        {
            case 0xFEEDFACE -> new Arch(32, null);
            case 0xFEEDFACF -> new Arch(64, null);
            case 0xCEFAEDFE -> new Arch(32, null);
            case 0xCFFAEDFE -> new Arch(64, null);
            default -> null;
        };
    }

    private static int shortAt(byte[] data, int offset, boolean little)
    {
        int low = data[offset + (little ? 0 : 1)] & 0xFF;
        int high = data[offset + (little ? 1 : 0)] & 0xFF;
        return (high << 8) | low;
    }

    private static int intAt(byte[] data, int offset, boolean little)
    {
        int value = 0;
        for(int i = 0; i < 4; i++)
        {
            int b = data[offset + (little ? 3 - i : i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }
}
