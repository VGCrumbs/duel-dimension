package de.cas_ual_ty.dueldimension.clientutil.model;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The test model, and whether this machine has it.
 * <p>
 * Both platforms' model tests pose the same {@code .glb}, so both need to agree
 * about where it is and whether to skip. That agreement used to live in {@code
 * GlbModelTest} as package-visible fields, which worked while everything was one
 * source set and stopped working the moment the tree split: a test source set is
 * not shared by a project dependency, so a platform test reaching for it did not
 * compile.
 * <p>
 * <b>A fixture and not a test class, deliberately.</b> The alternative was to
 * publish {@code common}'s whole test source set through a custom configuration,
 * and that does not survive what comes next — the platforms are separate Gradle
 * builds in a composite, and a composite substitutes module coordinates and test
 * fixtures, not arbitrary configurations. This is the shape that keeps working.
 */
public final class GlbFixtures
{
    /**
     * Where the model is.
     * <p>
     * It lives beside the sprite sheets it is an alternative to, in the game's
     * config folder — so a duellist who wants a 3D hologram drops a {@code .glb}
     * next to the {@code .png}s rather than learning a second place for it.
     * <p>
     * The override is looked at first, so a build on another machine can point
     * at its own copy; then the developer instance, which is where it actually
     * is.
     */
    public static final Path DRAGON = dragon();

    private GlbFixtures()
    {
    }

    private static Path dragon()
    {
        String override = System.getProperty("dueldimension.testModel");
        if(override != null && !override.isBlank())
        {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), "AppData", "Roaming", "ModrinthApp",
            "profiles", "Duel", "config", "dueldimension", "models", "curse_of_dragon.glb");
    }

    /**
     * Whether the model-dependent tests can run.
     * <p>
     * Absent the file they skip and the container checks still run — a test that
     * cannot see what it examines should say nothing rather than fail.
     */
    public static boolean dragonPresent()
    {
        return Files.isRegularFile(DRAGON);
    }
}
