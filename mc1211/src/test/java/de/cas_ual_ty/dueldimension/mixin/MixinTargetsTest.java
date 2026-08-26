package de.cas_ual_ty.dueldimension.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every mixin names methods that exist, on classes that have them, with the
 * signatures it claims.
 *
 * <h2>Why this test matters more here than on 26.2</h2>
 *
 * mc262 has a version of this, and its reason is a subtle one: two mixins were
 * dead for their whole life because {@code LivingEntity} overrode the
 * {@code Entity} method they injected into, so the injection applied cleanly and
 * could not run. That failure mode exists here too and is checked the same way.
 * <p>
 * But this module has a blunter problem on top of it. <b>Every one of these files
 * was copied from a codebase written against a different Minecraft.</b> A mixin
 * is strings: {@code method = "extractPlayerHealth"} compiles perfectly against
 * 1.21.1, where the method is called {@code renderPlayerHealth} and the class it
 * names has no {@code extract} anything. Nothing in {@code compileJava} looks at
 * those strings. The first thing that does is the game, at launch, and
 * {@code dueldimension.mixins.json} sets {@code defaultRequire: 1} — so the first
 * stale name is not a warning, it is a crash before the title screen.
 * <p>
 * Three things are checked, and the third is the one that catches a version port:
 *
 * <ol>
 * <li><b>The name exists.</b> {@code @Inject}/{@code @Redirect}'s {@code method}
 * names something the target class or a supertype declares.
 * <li><b>The right class answers.</b> Where the body narrows with an
 * {@code instanceof}, the target must be the class THAT type would inherit the
 * method from — the mc262 rule, and the reason this test exists at all.
 * <li><b>The call being redirected is real.</b> An {@code @At(value = "INVOKE")}
 * names an owner, a method and a full JVM descriptor. All three are checked
 * against the loaded class, so a redirect aimed at a method whose parameters
 * changed between versions fails here rather than at launch.
 * </ol>
 *
 * Reads the sources rather than the compiled mixins, because that is where the
 * annotation is legible without a Mixin runtime; and loads target classes without
 * initialising them, so no Minecraft bootstrap is needed.
 *
 * <h2>What it deliberately does not check</h2>
 *
 * Whether an injection point is REACHABLE — that an {@code @At("INVOKE")} call
 * really occurs inside the named method, and how many times. That needs the
 * bytecode of the target method, not its signature. A mixin can still pass here
 * and fail at launch with "injection point not found"; what it cannot do any more
 * is fail because a name or a signature moved between 26.2 and 1.21.1, which is
 * the mistake this port makes by the dozen.
 */
class MixinTargetsTest
{
    private static final Path MIXINS =
        Path.of("src/main/java/de/cas_ual_ty/dueldimension/mixin");

    private static final Pattern TARGET = Pattern.compile("@Mixin\\(\\s*([\\w.]+)\\.class");

    /** {@code @Inject} and {@code @Redirect} both carry a {@code method}. */
    private static final Pattern INJECTION = Pattern.compile(
        "@(?:Inject|Redirect|ModifyArg|ModifyVariable)\\s*\\(\\s*method\\s*=\\s*\"([^\"]+)\"");

    private static final Pattern IMPORT =
        Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);

    /**
     * An INVOKE injection point: {@code Lowner/Name;method(Lparam;)Lreturn;}.
     * <p>
     * Written across lines with string concatenation in most of these files, so
     * the source is stripped of {@code " + "} joins before this is applied.
     */
    private static final Pattern INVOKE_TARGET = Pattern.compile(
        "target\\s*=\\s*\"L([\\w/$]+);([\\w$<>]+)(\\([^)]*\\)[\\w/$;\\[]+)\"");

    /**
     * The type a mixin's guard actually cares about.
     * <p>
     * {@code (Object)this instanceof ServerPlayer player} — the cast to Object
     * is the standard mixin idiom for talking about the target instance, and the
     * type after {@code instanceof} is who the injection is really for.
     */
    private static final Pattern SUBJECT = Pattern.compile("instanceof\\s+([A-Z][\\w.]*)");

    /** One injection: where it was aimed, at what, and for whom. */
    private record Injection(Path file, String target, String method, String descriptor,
        String subject)
    {
    }

    /** One redirected call: the owner, the method and its exact signature. */
    private record Invoke(Path file, String owner, String method, String descriptor)
    {
    }

    @Test
    void everyInjectedMethodIsDeclaredByItsTarget()
    {
        List<Injection> injections = injections();
        assertFalse(injections.isEmpty(),
            "found no @Inject at all under " + MIXINS + " — the scan is broken, "
                + "and a test that silently checks nothing is worse than no test");

        List<String> wrong = new ArrayList<>();
        for(Injection injection : injections)
        {
            Class<?> target = load(injection.target());
            if(target == null)
            {
                // Not resolvable from the test classpath. Say so rather than
                // pass: a target that cannot be loaded is not a target that
                // was checked.
                wrong.add(injection.file().getFileName() + ": cannot load target "
                    + injection.target());
                continue;
            }
            if(!declares(target, injection.method()) && findDeclaringSupertype(target,
                injection.method()) == null)
            {
                wrong.add(injection.file().getFileName() + ": method = \""
                    + injection.method() + "\" names a method no supertype of "
                    + target.getSimpleName() + " declares — is the name right for 1.21.1?");
                continue;
            }

            // A method= that spells out its descriptor is making a second claim,
            // and a stale descriptor fails at launch exactly like a stale name.
            if(injection.descriptor() != null
                && !hasSignature(target, injection.method(), injection.descriptor()))
            {
                wrong.add(injection.file().getFileName() + ": method = \""
                    + injection.method() + injection.descriptor() + "\" — "
                    + target.getSimpleName() + " has no such overload. It declares: "
                    + signatures(target, injection.method()));
                continue;
            }

            // The real check: for the type this injection is actually about,
            // which class answers the method?
            if(injection.subject() == null)
            {
                continue;
            }
            Class<?> subject = load(injection.subject());
            if(subject == null || !target.isAssignableFrom(subject))
            {
                continue;
            }
            Class<?> answers = declaringFor(subject, injection.method());
            if(answers != null && !answers.equals(target))
            {
                wrong.add(injection.file().getFileName() + ": method = \""
                    + injection.method() + "\" targets " + target.getSimpleName()
                    + ", but a " + subject.getSimpleName() + " gets that method from "
                    + answers.getSimpleName() + " — which overrides it. Unless that "
                    + "override calls super, this injection never fires. Target "
                    + answers.getSimpleName() + " instead.");
            }
        }

        assertTrue(wrong.isEmpty(), "mixins aimed at a method their target does not "
            + "declare:\n  " + String.join("\n  ", wrong));
    }

    @Test
    void everyRedirectedCallExists()
    {
        List<Invoke> invokes = invokes();
        assertFalse(invokes.isEmpty(),
            "found no @At(value = \"INVOKE\") target at all — the scan is broken");

        List<String> wrong = new ArrayList<>();
        for(Invoke invoke : invokes)
        {
            Class<?> owner = load(invoke.owner().replace('/', '.'));
            if(owner == null)
            {
                wrong.add(invoke.file().getFileName() + ": cannot load INVOKE owner "
                    + invoke.owner());
                continue;
            }
            if(!hasSignature(owner, invoke.method(), invoke.descriptor()))
            {
                wrong.add(invoke.file().getFileName() + ": redirects "
                    + owner.getSimpleName() + "." + invoke.method() + invoke.descriptor()
                    + ", which does not exist on 1.21.1. It declares: "
                    + signatures(owner, invoke.method()));
            }
        }

        assertTrue(wrong.isEmpty(), "mixins redirecting a call that is not there:\n  "
            + String.join("\n  ", wrong));
    }

    private static List<Injection> injections()
    {
        List<Injection> out = new ArrayList<>();
        forEachMixin((file, source) ->
        {
            Matcher target = TARGET.matcher(source);
            if(!target.find())
            {
                return;
            }
            String qualified = qualify(source, target.group(1));
            Matcher subject = SUBJECT.matcher(source);
            String narrowed = subject.find() ? qualify(source, subject.group(1)) : null;
            Matcher injection = INJECTION.matcher(source);
            while(injection.find())
            {
                String spec = injection.group(1).trim();
                int paren = spec.indexOf('(');
                out.add(new Injection(file, qualified,
                    paren < 0 ? spec : spec.substring(0, paren),
                    paren < 0 ? null : spec.substring(paren), narrowed));
            }
        });
        return out;
    }

    private static List<Invoke> invokes()
    {
        List<Invoke> out = new ArrayList<>();
        forEachMixin((file, source) ->
        {
            Matcher matcher = INVOKE_TARGET.matcher(source);
            while(matcher.find())
            {
                out.add(new Invoke(file, matcher.group(1), matcher.group(2), matcher.group(3)));
            }
        });
        return out;
    }

    private interface Visitor
    {
        void accept(Path file, String source);
    }

    /**
     * Every mixin source, with its string concatenation folded away first.
     * <p>
     * A descriptor is long enough that these are all written as
     * {@code "Lnet/minecraft/..." + ";method()V"}, so the annotation's real value
     * only exists after the joins are removed. Without this the INVOKE pattern
     * matches nothing and both tests pass by finding no work.
     */
    private static void forEachMixin(Visitor visitor)
    {
        try(Stream<Path> files = Files.walk(MIXINS))
        {
            for(Path file : files.filter(p -> p.toString().endsWith(".java")).toList())
            {
                String source = Files.readString(file, StandardCharsets.UTF_8)
                    .replaceAll("\"\\s*\\+\\s*\"", "");
                visitor.accept(file, source);
            }
        }
        catch(IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /** A simple name resolved through the file's own imports. */
    private static String qualify(String source, String name)
    {
        if(name.contains("."))
        {
            return name;
        }
        Map<String, String> imports = new HashMap<>();
        Matcher matcher = IMPORT.matcher(source);
        while(matcher.find())
        {
            String imported = matcher.group(1);
            imports.put(imported.substring(imported.lastIndexOf('.') + 1), imported);
        }
        return imports.getOrDefault(name, name);
    }

    /**
     * Loaded WITHOUT initialising it.
     * <p>
     * Minecraft classes run static setup that expects a game; this test only
     * needs the method table, and {@code initialize = false} gets it without
     * asking for a bootstrap.
     */
    private static Class<?> load(String name)
    {
        try
        {
            return Class.forName(name, false, MixinTargetsTest.class.getClassLoader());
        }
        catch(Throwable notHere)
        {
            return null;
        }
    }

    /**
     * A constructor, which a mixin names {@code <init>} and reflection does not
     * call a method at all.
     * <p>
     * Not a special case anybody would guess: {@code getDeclaredMethods} does not
     * list constructors, so without this every {@code method = "<init>"} reads as
     * a name that does not exist. The mod has one -- {@code MinecraftPackMixin}
     * injects into {@code Minecraft}'s constructor -- and the first version of
     * this test failed it.
     */
    private static final String CONSTRUCTOR = "<init>";

    private static boolean declares(Class<?> type, String method)
    {
        if(CONSTRUCTOR.equals(method))
        {
            return type.getDeclaredConstructors().length > 0;
        }
        for(Method declared : type.getDeclaredMethods())
        {
            if(declared.getName().equals(method))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether the type, or anything above it, has that exact overload. */
    private static boolean hasSignature(Class<?> type, String method, String descriptor)
    {
        if(CONSTRUCTOR.equals(method))
        {
            // Not inherited, so this asks the named class and only it.
            for(var declared : type.getDeclaredConstructors())
            {
                if(descriptor(declared.getParameterTypes(), void.class).equals(descriptor))
                {
                    return true;
                }
            }
            return false;
        }
        for(Class<?> here = type; here != null; here = here.getSuperclass())
        {
            for(Method declared : here.getDeclaredMethods())
            {
                if(declared.getName().equals(method)
                    && descriptor(declared).equals(descriptor))
                {
                    return true;
                }
            }
        }
        // Interfaces too: an INVOKE can name one, and their methods are not on
        // the superclass chain.
        for(Class<?> face : type.getInterfaces())
        {
            if(hasSignature(face, method, descriptor))
            {
                return true;
            }
        }
        return false;
    }

    /** Everything that class calls by that name, for a failure worth reading. */
    private static String signatures(Class<?> type, String method)
    {
        List<String> found = new ArrayList<>();
        if(CONSTRUCTOR.equals(method))
        {
            for(var declared : type.getDeclaredConstructors())
            {
                found.add(descriptor(declared.getParameterTypes(), void.class));
            }
            return found.isEmpty() ? "no constructors" : String.join(", ", found);
        }
        for(Class<?> here = type; here != null; here = here.getSuperclass())
        {
            for(Method declared : here.getDeclaredMethods())
            {
                if(declared.getName().equals(method))
                {
                    found.add(descriptor(declared));
                }
            }
        }
        return found.isEmpty() ? "nothing by that name" : String.join(", ", found);
    }

    private static String descriptor(Method method)
    {
        return descriptor(method.getParameterTypes(), method.getReturnType());
    }

    private static String descriptor(Class<?>[] parameters, Class<?> returns)
    {
        StringBuilder out = new StringBuilder("(");
        for(Class<?> parameter : parameters)
        {
            out.append(descriptor(parameter));
        }
        return out.append(')').append(descriptor(returns)).toString();
    }

    private static String descriptor(Class<?> type)
    {
        if(type.isArray())
        {
            return "[" + descriptor(type.getComponentType());
        }
        if(!type.isPrimitive())
        {
            return "L" + type.getName().replace('.', '/') + ";";
        }
        return switch(type.getName())
        {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            default -> "D";
        };
    }

    /**
     * The class a given type would actually inherit this method from.
     * <p>
     * Walked from the subject UPWARDS, so the first hit is the one that wins at
     * runtime — which is the whole question.
     */
    private static Class<?> declaringFor(Class<?> subject, String method)
    {
        for(Class<?> here = subject; here != null; here = here.getSuperclass())
        {
            if(declares(here, method))
            {
                return here;
            }
        }
        return null;
    }

    /** The nearest supertype that does declare it, for a useful failure. */
    private static String findDeclaringSupertype(Class<?> type, String method)
    {
        for(Class<?> above = type.getSuperclass(); above != null; above = above.getSuperclass())
        {
            if(declares(above, method))
            {
                return above.getSimpleName();
            }
        }
        return null;
    }
}
