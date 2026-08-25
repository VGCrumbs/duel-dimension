package de.cas_ual_ty.dueldimension.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Every {@code @Inject} names a method its target class actually declares.
 * <p>
 * <b>This exists because two mixins were dead for their whole life and nothing
 * said so.</b> {@code DuelPushMixin} and {@code LocalDuelPushMixin} injected at
 * the head of {@code Entity.isPushable()} to stop duellists being shoved off
 * their mark. {@code LivingEntity} overrides {@code isPushable} and computes its
 * own answer — {@code isAlive() && !isSpectator() && !onClimbable()} — without
 * ever calling {@code super}. So the injection applied to {@code Entity}
 * cleanly, loaded without a warning, and could not run for anything alive.
 * <p>
 * That failure is invisible from every direction. The mixin compiles. Mixin
 * itself is happy, because the method IS on the target. The game starts. The
 * only symptom is a feature quietly not working, and "players still get pushed"
 * reads like a tuning problem rather than a mixin that never fires.
 * <p>
 * <b>The obvious rule does not catch it, and the first version of this test used
 * the obvious rule and passed.</b> "The target must declare the method" is
 * satisfied here: {@code Entity} does declare {@code isPushable}. That test was
 * green against the bug, which makes it worse than nothing.
 * <p>
 * The rule that works comes from the mixin's own body. Each of these asks
 * {@code this instanceof ServerPlayer} — so the injection is only ever meant to
 * fire for a {@code ServerPlayer}, and the question is which class actually
 * answers {@code isPushable} FOR one. Walking up from {@code ServerPlayer}, the
 * first declaration found is {@code LivingEntity}'s. Target anything above that
 * and the override wins and the injection is dead.
 * <p>
 * So: <b>the target must be the class the injection's own subject would inherit
 * the method from.</b> That is checkable, and it fails on {@code Entity}.
 * <p>
 * Reads the sources rather than the compiled mixins, because that is where the
 * annotation is legible without a Mixin runtime; and loads target classes
 * without initialising them, so no Minecraft bootstrap is needed.
 */
class MixinTargetsTest
{
    private static final Path MIXINS =
        Path.of("src/main/java/de/cas_ual_ty/dueldimension/mixin");

    private static final Pattern TARGET = Pattern.compile("@Mixin\\(\\s*([\\w.]+)\\.class");
    private static final Pattern INJECT =
        Pattern.compile("@Inject\\s*\\(\\s*method\\s*=\\s*\"([^\"(]+)");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);

    /**
     * The type a mixin's guard actually cares about.
     * <p>
     * {@code (Object)this instanceof ServerPlayer player} — the cast to Object
     * is the standard mixin idiom for talking about the target instance, and the
     * type after {@code instanceof} is who the injection is really for.
     */
    private static final Pattern SUBJECT =
        Pattern.compile("instanceof\\s+([A-Z][\\w.]*)");

    /**
     * One {@code @Inject}: where it was aimed, at what, and for whom.
     *
     * @param subject the type the body's {@code instanceof} narrows to, or null
     *                if it does not narrow — an injection meant for everything
     *                has no more specific class to be checked against
     */
    private record Injection(Path file, String target, String method, String subject)
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
                wrong.add(injection.file().getFileName() + ": @Inject(method = \""
                    + injection.method() + "\") names a method no supertype of "
                    + target.getSimpleName() + " declares — is the name right?");
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
                wrong.add(injection.file().getFileName() + ": @Inject(method = \""
                    + injection.method() + "\") targets " + target.getSimpleName()
                    + ", but a " + subject.getSimpleName() + " gets that method from "
                    + answers.getSimpleName() + " — which overrides it. Unless that "
                    + "override calls super, this injection never fires. Target "
                    + answers.getSimpleName() + " instead.");
            }
        }

        assertTrue(wrong.isEmpty(), "mixins aimed at a method their target does not "
            + "declare:\n  " + String.join("\n  ", wrong));
    }

    private static List<Injection> injections()
    {
        List<Injection> out = new ArrayList<>();
        try(Stream<Path> files = Files.walk(MIXINS))
        {
            for(Path file : files.filter(p -> p.toString().endsWith(".java")).toList())
            {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher target = TARGET.matcher(source);
                if(!target.find())
                {
                    continue;
                }
                String qualified = qualify(source, target.group(1));
                Matcher subject = SUBJECT.matcher(source);
                String narrowed = subject.find() ? qualify(source, subject.group(1)) : null;
                Matcher inject = INJECT.matcher(source);
                while(inject.find())
                {
                    out.add(new Injection(file, qualified, inject.group(1).trim(), narrowed));
                }
            }
        }
        catch(IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return out;
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

    private static boolean declares(Class<?> type, String method)
    {
        for(var declared : type.getDeclaredMethods())
        {
            if(declared.getName().equals(method))
            {
                return true;
            }
        }
        return false;
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
