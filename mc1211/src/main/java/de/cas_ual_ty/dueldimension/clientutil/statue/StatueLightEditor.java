package de.cas_ual_ty.dueldimension.clientutil.statue;

import de.cas_ual_ty.dueldimension.clientutil.hub.MenuInk;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A live editor for the reward screen's light rig, and a way to write the
 * result back into the source.
 *
 * <h2>Why this exists</h2>
 * The rig in {@link StatueScene} is read out of the disc, but two things about
 * it are not: what the light object's vtable slots actually mean, and how a
 * fixed-function result maps onto software Blinn-Phong computed against a fixed
 * view direction. Both have been wrong once already, and each correction cost a
 * build, a launch and a look. Turning a number and seeing it immediately is
 * worth more than another round of reasoning about it.
 *
 * <h2>Development clients only</h2>
 * Gated on {@code FabricLoader.isDevelopmentEnvironment()}. A shipped jar has
 * no source tree to write to, and an editor that silently could not save would
 * be worse than no editor -- so it is not offered there at all.
 *
 * <h2>Baking</h2>
 * {@code B} rewrites {@code StatueScene.java} in place, in BOTH modules, so the
 * values live in source rather than in a config the next person has to find.
 * The source is located by walking up from the run directory looking for the
 * module layout; if it is not found, nothing is written and the panel says so
 * rather than reporting a success it did not have.
 */
public final class StatueLightEditor
{
    /** One editable number. */
    private interface Get
    {
        float get();
    }

    private interface Set
    {
        void set(float value);
    }

    private record Field(String label, Get getter, Set setter, float step)
    {
    }

    private final List<Field> fields = new ArrayList<>();
    private int selected;
    private String status = "";

    public StatueLightEditor()
    {
        // Read off StatueScene.LIGHTS rather than written out, so a light added
        // there appears here without this class being touched.
        for(int i = 0; i < StatueScene.LIGHTS.length; i++)
        {
            StatueScene.Light light = StatueScene.LIGHTS[i];
            axis("L" + (i + 1) + " dir", light.direction(), 0.05F);
            axis("L" + (i + 1) + " amb", light.ambient(), 0.02F);
            axis("L" + (i + 1) + " dif", light.diffuse(), 0.02F);
            axis("L" + (i + 1) + " spc", light.specular(), 0.02F);
        }
        fields.add(new Field("ambient scale",
            () -> StatueScene.ambientScale, v -> StatueScene.ambientScale = v, 0.02F));
        fields.add(new Field("specular boost",
            () -> StatueScene.specularBoost, v -> StatueScene.specularBoost = v, 0.1F));
        // The plates the gods stand on, which are polished where the podium
        // under them is not. Judgement values, so they belong here.
        fields.add(new Field("plate shine",
            () -> StatueScene.crownShine, v -> StatueScene.crownShine = v, 0.1F));
        fields.add(new Field("plate gloss",
            () -> StatueScene.crownShininess, v -> StatueScene.crownShininess = v, 2F));
    }

    /** The three components of one vector, as three fields over the same array. */
    private void axis(String label, float[] vector, float step)
    {
        for(int c = 0; c < 3; c++)
        {
            int index = c;
            fields.add(new Field(label + " " + "xyz".charAt(c),
                () -> vector[index], v -> vector[index] = v, step));
        }
    }

    /** Whether the editor may be opened at all. */
    public static boolean available()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /**
     * @return true when the key was the editor's
     */
    public boolean keyPressed(int key)
    {
        int count = fields.size();
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_S)
        {
            selected = (selected + 1) % count;
            return true;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_W)
        {
            selected = (selected + count - 1) % count;
            return true;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_A)
        {
            nudge(-1);
            return true;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_D)
        {
            nudge(1);
            return true;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_B)
        {
            status = bake();
            return true;
        }
        return false;
    }

    /**
     * Moves the selected value.
     * <p>
     * Shift multiplies the step by ten and Alt divides it by ten, so the same
     * two keys cover a coarse sweep and a final nudge without a mode.
     */
    private void nudge(int direction)
    {
        Field field = fields.get(selected);
        float step = field.step();
        if(down(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || down(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT))
        {
            step *= 10F;
        }
        if(down(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
            || down(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT))
        {
            step /= 10F;
        }
        // Rounded to the step so a value nudged up and back down returns to what
        // it was instead of drifting on float error.
        float next = field.getter().get() + direction * step;
        field.setter().set(Math.round(next / step) * step);
        status = "";
    }

    private static boolean down(int key)
    {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), key);
    }

    /**
     * Half size, because the list is long.
     * <p>
     * Fourteen fields per light plus the scalars is well over thirty rows, and at
     * the font's own size that is taller than the screen -- the panel ran off the
     * bottom and the rows that fell outside could be selected but not read. The
     * whole panel is drawn under a matrix scale rather than with a second font,
     * so every measurement below is in HALF-pixels and the arithmetic stays in
     * one unit.
     */
    private static final float SCALE = 0.5F;

    private static final int ROW_H = 10;
    private static final int HEADER_H = 18;
    private static final int FOOTER_H = 16;
    private static final int PANEL_W = 210;
    private static final int MARGIN = 8;

    /** First visible row, moved only as far as it must be to show the cursor. */
    private int scroll;

    public void draw(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight)
    {
        // Everything from here is in half-pixels; the scale at the end brings it
        // back to screen units.
        int spaceW = Math.round(screenWidth / SCALE);
        int spaceH = Math.round(screenHeight / SCALE);

        int roomForRows = spaceH - MARGIN * 2 - HEADER_H - FOOTER_H;
        int visible = Math.max(1, Math.min(fields.size(), roomForRows / ROW_H));

        // Keep the cursor in view without jumping the list around: it scrolls
        // only when the selection would otherwise fall outside.
        if(selected < scroll)
        {
            scroll = selected;
        }
        else if(selected >= scroll + visible)
        {
            scroll = selected - visible + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, fields.size() - visible)));

        int panelH = HEADER_H + visible * ROW_H + FOOTER_H;
        int left = spaceW - PANEL_W - MARGIN;
        int top = MARGIN;

        graphics.pose().pushMatrix();
        graphics.pose().scale(SCALE, SCALE);
        NineSlice.draw(graphics, HubTextures.PANEL, left, top, PANEL_W, panelH);

        String title = fields.size() > visible
            ? String.format(Locale.ROOT, "Light rig  %d/%d", selected + 1, fields.size())
            : "Light rig";
        graphics.text(font, title, left + 7, top + 5, MenuInk.title(), MenuInk.shadow());

        for(int row = 0; row < visible; row++)
        {
            int i = scroll + row;
            if(i >= fields.size())
            {
                break;
            }
            Field field = fields.get(i);
            int y = top + HEADER_H + row * ROW_H;
            boolean on = i == selected;
            int colour = on ? 0xFFFFE9B0 : 0xFFB9C0CC;
            graphics.text(font, (on ? "> " : "  ") + field.label(), left + 7, y, colour, true);
            String value = String.format(Locale.ROOT, "%.3f", field.getter().get());
            graphics.text(font, value, left + PANEL_W - 7 - font.width(value), y, colour, true);
        }

        String hint = status.isEmpty() ? "arrows adjust  B bakes to src" : status;
        graphics.text(font, hint, left + 7, top + panelH - FOOTER_H + 4, MenuInk.dim(), MenuInk.shadow());
        graphics.pose().popMatrix();
    }

    // ---- writing it back ----

    /**
     * Rewrites the rig in {@code StatueScene.java}, in every module that has one.
     *
     * @return a line to show in the panel, whether it worked or not
     */
    private String bake()
    {
        Path root = repositoryRoot();
        if(root == null)
        {
            return "no source tree found";
        }
        int written = 0;
        for(String module : new String[] {"mc262", "mc1211"})
        {
            Path file = root.resolve(module).resolve(
                "src/main/java/de/cas_ual_ty/dueldimension/clientutil/statue/StatueScene.java");
            if(!Files.isRegularFile(file))
            {
                continue;
            }
            try
            {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String next = replaceRig(source);
                if(next != null)
                {
                    Files.writeString(file, next, StandardCharsets.UTF_8);
                    written++;
                }
            }
            catch(IOException e)
            {
                return "write failed: " + e.getMessage();
            }
        }
        return written == 0 ? "nothing written" : "baked into " + written + " module(s)";
    }

    /**
     * The repository root, found by walking up from the working directory.
     * <p>
     * A dev client runs in {@code <module>/run}, so the root is two or three
     * levels up depending on how it was launched. Rather than count, this looks
     * for the directory that actually contains both modules.
     */
    private static Path repositoryRoot()
    {
        Path at = Path.of("").toAbsolutePath();
        for(int i = 0; i < 6 && at != null; i++)
        {
            if(Files.isDirectory(at.resolve("mc262")) && Files.isDirectory(at.resolve("common")))
            {
                return at;
            }
            at = at.getParent();
        }
        return null;
    }

    /** @return the edited source, or null when the anchors are not where expected */
    private String replaceRig(String source)
    {
        int start = source.indexOf("    public static final Light[] LIGHTS = {");
        if(start < 0)
        {
            return null;
        }
        int end = source.indexOf("\n    };", start);
        if(end < 0)
        {
            return null;
        }
        StringBuilder rig = new StringBuilder("    public static final Light[] LIGHTS = {");
        for(int i = 0; i < StatueScene.LIGHTS.length; i++)
        {
            StatueScene.Light light = StatueScene.LIGHTS[i];
            rig.append("\n        // ").append(i + 1)
                .append(" -- edited in the in-game light editor.");
            rig.append("\n        new Light(").append(vector(light.direction())).append(",");
            rig.append("\n            ").append(vector(light.ambient())).append(",");
            rig.append("\n            ").append(vector(light.diffuse())).append(",");
            rig.append("\n            ").append(vector(light.specular())).append("),");
        }
        String next = source.substring(0, start) + rig + source.substring(end);

        next = replaceFloat(next, "    public static float ambientScale = ",
            StatueScene.ambientScale);
        next = replaceFloat(next, "    public static float specularBoost = ",
            StatueScene.specularBoost);
        next = replaceFloat(next, "    public static float crownShine = ",
            StatueScene.crownShine);
        next = replaceFloat(next, "    public static float crownShininess = ",
            StatueScene.crownShininess);
        return next;
    }

    private static String vector(float[] v)
    {
        return String.format(Locale.ROOT, "new float[] {%.3fF, %.3fF, %.3fF}", v[0], v[1], v[2]);
    }

    private static String replaceFloat(String source, String prefix, float value)
    {
        int at = source.indexOf(prefix);
        if(at < 0)
        {
            return source;
        }
        int end = source.indexOf(';', at);
        return source.substring(0, at) + prefix
            + String.format(Locale.ROOT, "%.3fF", value) + source.substring(end);
    }
}
