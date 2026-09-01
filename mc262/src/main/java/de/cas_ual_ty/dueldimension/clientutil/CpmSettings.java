package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Putting Customizable Player Models back the way we found it.
 *
 * <h2>The bug, exactly</h2>
 * CPM's {@code PlayerRendererMixinFabric} injects into the same method this mod
 * cancels — {@code PlayerRenderer.render} — as a matched pair:
 * <ul>
 * <li>{@code playerRenderPre} at {@code HEAD}, which BINDS the player's custom
 * model onto the renderer's {@code PlayerModel};</li>
 * <li>{@code playerRenderPost} at {@code RETURN}, which unbinds it again.</li>
 * </ul>
 * A {@code PlayerRenderer} and its model are SHARED by every player on screen —
 * there is one per skin type, not one per player — so that pair is not a
 * courtesy, it is the only thing keeping one player's custom model from being
 * left switched on for the next.
 * <p>
 * {@code CharacterRendererMixin} cancels at HEAD, and a cancelled method never
 * reaches its RETURN. So when a duellist is drawn as their character, CPM binds
 * and is never told to unbind, and the model stays bound for whoever is drawn
 * next. Which is why the report is so specific: the person seeing it is not the
 * person wearing the duelist. Their client draws MY character, our cancel eats
 * CPM's unbind, and THEIR own model — drawn moments later from the same shared
 * renderer — comes out wearing the leftovers.
 *
 * <h2>Why calling post on its own is safe</h2>
 * It bottoms out in {@code ModelRenderManager.unbindModel}, which looks the
 * model up in a map, null-checks the result and returns if there is nothing
 * there. So it is "unbind if bound" and idempotent, which is what lets this be
 * called unconditionally rather than only when CPM's pre is known to have run.
 * That matters, because whether it ran depends on which mixin was applied
 * first — an ordering neither mod controls. Balancing the pair from our side
 * is correct either way round; relying on the ordering would only be correct
 * one way round.
 *
 * <h2>Reflection, and no dependency</h2>
 * CPM is not a dependency of this mod and must not become one. Everything is
 * looked up once by name, and if any part is missing — CPM not installed, or a
 * version that renders differently — the lookup fails once, is remembered as
 * failed, and this becomes a no-op forever. A mod that is not there cannot be
 * broken by us and does not need fixing.
 * <p>
 * Resolved once rather than per call because this sits on the render path, and
 * a reflective lookup per player per frame is a real cost for a question whose
 * answer cannot change while the game is running.
 */
public final class CpmSettings
{
    /**
     * Whether the fix is applied. ON, because the glitch is a glitch.
     * <p>
     * It is a setting at all because turning it off is harmless — the leak
     * corrupts other mods' rendering and nothing of ours — and because somebody
     * who wants to look at it should be able to.
     */
    private static boolean fix = true;

    /** Null until {@link #bridge()} has tried; see {@link #looked}. */
    private static Method post;
    private static Object client;
    private static boolean looked;

    private CpmSettings()
    {
    }

    public static boolean fix()
    {
        return fix;
    }

    public static void setFix(boolean value)
    {
        if(fix != value)
        {
            fix = value;
            save();
        }
    }

    /**
     * Tells CPM the render it was expecting is not going to happen.
     * <p>
     * Called from {@code CharacterRendererMixin} immediately before it cancels,
     * so the unbind lands in the same place CPM's own would have.
     *
     * @param buffers the render's buffer source, which CPM flushes
     * @param model   the SHARED player model, which is the thing being unbound
     */
    public static void renderCancelled(Object buffers, Object model)
    {
        if(!fix || model == null)
        {
            return;
        }
        Method bridge = bridge();
        if(bridge == null)
        {
            return;
        }
        try
        {
            bridge.invoke(client, buffers, model);
        }
        catch(ReflectiveOperationException | RuntimeException failed)
        {
            // Once, then never again: a throwing bridge is a broken bridge, and
            // a warning per player per frame would be the worse bug.
            post = null;
            client = null;
            DuelDimension.warn("Customizable Player Models compatibility is off: " + failed);
        }
    }

    private static Method bridge()
    {
        if(looked)
        {
            return post;
        }
        looked = true;
        try
        {
            Class<?> type = Class.forName("com.tom.cpm.client.CustomPlayerModelsClient");
            client = type.getField("INSTANCE").get(null);
            if(client == null)
            {
                return null;
            }
            // Found by NAME AND ARITY rather than by parameter type, and that
            // is what lets this one file be identical in both trees: the
            // signature is (MultiBufferSource, PlayerModel), and neither of
            // those classes exists on 26.2 at all. Naming them would not
            // compile there. There is exactly one playerRenderPost, so an
            // arity check is as precise as a type check would have been.
            for(Method candidate : client.getClass().getMethods())
            {
                if(candidate.getName().equals("playerRenderPost")
                    && candidate.getParameterCount() == 2)
                {
                    post = candidate;
                    break;
                }
            }
            if(post == null)
            {
                DuelDimension.warn("Customizable Player Models is installed but its render"
                    + " hook could not be found; its models may glitch while a duelist is"
                    + " on screen.");
            }
        }
        catch(ClassNotFoundException absent)
        {
            // The overwhelmingly normal case: CPM is not installed.
            post = null;
        }
        catch(ReflectiveOperationException | RuntimeException changed)
        {
            post = null;
            DuelDimension.warn("Customizable Player Models is installed but its render hook"
                + " could not be found; its models may glitch while a duelist is on screen: "
                + changed);
        }
        return post;
    }

    private static Path file()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("dueldimension-cpm-fix.txt");
    }

    private static void save()
    {
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Boolean.toString(fix), StandardCharsets.UTF_8);
        }
        catch(IOException unwritable)
        {
            DuelDimension.warn("could not save the CPM fix setting: " + unwritable);
        }
    }

    /**
     * Read when the class is first touched, as every other setting in this mod
     * is. No initialiser to remember to call, and therefore none to forget.
     */
    static
    {
        try
        {
            if(Files.isRegularFile(file()))
            {
                fix = !Files.readString(file(), StandardCharsets.UTF_8)
                    .trim().equalsIgnoreCase("false");
            }
        }
        catch(IOException unreadable)
        {
            DuelDimension.warn("Could not read " + file() + ": " + unreadable.getMessage());
        }
    }
}
