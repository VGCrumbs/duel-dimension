package de.cas_ual_ty.dueldimension.compat;

import org.lwjgl.glfw.GLFW;

/**
 * 26.2's input event records, for 1.21.1 which passes loose ints.
 *
 * <h2>Why</h2>
 *
 * 26.2 wrapped GUI input in records — {@code mouseClicked(MouseButtonEvent,
 * boolean)}, {@code keyPressed(KeyEvent)} — where 1.21.1 passes
 * {@code (double, double, int)} and {@code (int, int, int)}. That difference
 * shows up in over a hundred overrides across every screen and widget in the
 * mod, and the bodies are otherwise identical.
 * <p>
 * So the events exist here and the overrides are bridged: each screen keeps its
 * 26.2-shaped method and gains a 1.21.1-shaped one that builds an event and
 * calls it. Same trade as {@link GuiGraphicsExtractor} — one file, and the trees
 * stay textually parallel.
 *
 * <h2>What is deliberately missing</h2>
 *
 * {@code InputWithModifiers}' convenience predicates — {@code isSelection},
 * {@code isEscape}, {@code isCycleFocus} and the arrow-key pair. Those encode
 * 26.2's own keybinding policy, and reimplementing them here would be inventing
 * what a key MEANS rather than translating what was pressed. Nothing in this mod
 * calls them; if something starts to, it should be written against 1.21.1's own
 * answer rather than a guess at 26.2's.
 */
public final class InputEvents
{
    private InputEvents()
    {
    }

    /**
     * A mouse button, where and which.
     *
     * @param x      cursor position when it was pressed, in GUI pixels
     * @param y      the same
     * @param button GLFW button number; 0 is left
     */
    public record MouseButtonEvent(double x, double y, int button)
    {
        /**
         * The modifier keys held at the time.
         * <p>
         * 1.21.1 does not pass them to {@code mouseClicked}, so they are read
         * from the window instead. That is a real difference and not a
         * translation: the value is correct at the moment it is ASKED for rather
         * than at the moment of the click. For a shift-click that is the same
         * thing, which is all this mod uses it for.
         */
        public int modifiers()
        {
            long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
            int held = 0;
            if(down(window, GLFW.GLFW_KEY_LEFT_SHIFT) || down(window, GLFW.GLFW_KEY_RIGHT_SHIFT))
            {
                held |= GLFW.GLFW_MOD_SHIFT;
            }
            if(down(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || down(window, GLFW.GLFW_KEY_RIGHT_CONTROL))
            {
                held |= GLFW.GLFW_MOD_CONTROL;
            }
            if(down(window, GLFW.GLFW_KEY_LEFT_ALT) || down(window, GLFW.GLFW_KEY_RIGHT_ALT))
            {
                held |= GLFW.GLFW_MOD_ALT;
            }
            return held;
        }

        public boolean hasShiftDown()
        {
            return (modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        }

        public boolean hasControlDown()
        {
            return (modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        }

        private static boolean down(long window, int key)
        {
            return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        }
    }

    /**
     * A key press.
     *
     * @param key       GLFW key code
     * @param scancode  platform scancode
     * @param modifiers GLFW modifier bits, which 1.21.1 does pass for keys
     */
    public record KeyEvent(int key, int scancode, int modifiers)
    {
        public boolean hasShiftDown()
        {
            return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        }

        public boolean hasControlDown()
        {
            return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        }
    }

    /** A typed character. */
    public record CharacterEvent(int codepoint)
    {
        public String codepointAsString()
        {
            return Character.toString(codepoint);
        }
    }
}
