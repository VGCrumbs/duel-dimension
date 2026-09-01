package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.character.RideAnchor;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.compat.InputEvents;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Settling a character into a saddle, while sitting in one.
 *
 * <h2>The same key, and that is the point</h2>
 * Numpad {@code .} opens whichever of these two is the one you can see: this
 * while riding, {@link ItemAnchorScreen} otherwise. A key that opened the wrong
 * editor would be a key you had to think about, and the thing being tuned is
 * always the thing in front of you -- which is the argument the live-preview
 * layout is built on as well.
 *
 * <h2>Live, and off to one side</h2>
 * The panel takes one edge and nothing dims the world, because a duellist who
 * cannot be seen cannot be seated. See {@link RideAnchor} for what each number
 * does and why none of them needed new machinery to apply.
 */
public class RideAnchorScreen extends Screen
{
    private static final int PANEL_W = 200;
    private static final int ROW_H = 16;
    private static final int GAP = 3;
    private static final int PAD = 8;

    private String report = "";

    /**
     * Which row is being typed into, by label, or null while every row is a
     * slider.
     * <p>
     * By LABEL and not by widget, because a rebuild replaces every widget on the
     * screen and a reference to one of them would point at a control that is no
     * longer on it.
     */
    private String typing;
    private EditBox typingBox;
    private Field typingField;

    /**
     * Where {@code Ctrl+Z} goes back to, and where {@code Ctrl+Shift+Z} goes
     * forward to.
     * <p>
     * Whole seats rather than individual edits. A seat is five floats; keeping
     * the lot is simpler than describing a change, and it cannot get out of step
     * with what it claims to undo.
     */
    private final Deque<RideAnchor.Seat> undo =
        new ArrayDeque<>();
    private final Deque<RideAnchor.Seat> redo =
        new ArrayDeque<>();

    /**
     * What the last drag started from, so a drag is one undo step and not
     * sixty.
     * <p>
     * A slider fires on every pixel it passes. Pushing each one would make
     * {@code Ctrl+Z} undo a fraction of a drag, which is not what anybody means
     * by it -- so the value before a drag begins is held here and pushed once,
     * when the drag ends.
     */
    private RideAnchor.Seat beforeDrag;

    public RideAnchorScreen()
    {
        super(Component.literal("Ride anchor"));
    }

    /** One tunable: a slider over a range, applied the instant it moves. */
    private record Field(String label, float min, float max,
        java.util.function.Function<RideAnchor.Seat, Float> get,
        java.util.function.BiFunction<RideAnchor.Seat, Float, RideAnchor.Seat> set)
    {
    }

    /**
     * Every number, in the order they are reached for.
     * <p>
     * Up first, because a duellist sunk into a horse or floating above it is the
     * thing anybody notices before anything else, and the rest cannot be judged
     * until it is fixed. Lean last: it is the refinement, and it reads as wrong
     * only once the seat itself is right.
     */
    private static final Field[] FIELDS = {
        new Field("Up", -1F, 1F, RideAnchor.Seat::y,
            (s, v) -> new RideAnchor.Seat(s.x(), v, s.z(), s.yaw(), s.lean())),
        new Field("Forward", -1F, 1F, RideAnchor.Seat::z,
            (s, v) -> new RideAnchor.Seat(s.x(), s.y(), v, s.yaw(), s.lean())),
        new Field("Across", -1F, 1F, RideAnchor.Seat::x,
            (s, v) -> new RideAnchor.Seat(v, s.y(), s.z(), s.yaw(), s.lean())),
        new Field("Yaw", -180F, 180F, RideAnchor.Seat::yaw,
            (s, v) -> new RideAnchor.Seat(s.x(), s.y(), s.z(), v, s.lean())),
        new Field("Lean", -90F, 90F, RideAnchor.Seat::lean,
            (s, v) -> new RideAnchor.Seat(s.x(), s.y(), s.z(), s.yaw(), v)),
    };

    /** Remembers where a change started from, so it can be walked back. */
    private void remember(RideAnchor.Seat was)
    {
        undo.push(was);
        // A new branch: what was undone is no longer ahead of anything.
        redo.clear();
    }

    @Override
    protected void init()
    {
        int x = PAD;
        int y = PAD + 14;

        typingBox = null;
        typingField = null;
        for(Field field : FIELDS)
        {
            if(field.label().equals(typing))
            {
                typingField = field;
                typingBox = new EditBox(font, x, y, PANEL_W - PAD * 2, ROW_H - 2,
                    Component.literal(field.label()));
                typingBox.setValue(String.format("%.4f",
                    field.get().apply(RideAnchor.seat())));
                typingBox.setFocused(true);
                setFocused(typingBox);
                addRenderableWidget(typingBox);
            }
            else
            {
                addRenderableWidget(new Tuner(x, y, PANEL_W - PAD * 2, ROW_H - 2, field));
            }
            y += ROW_H + GAP;
        }

        y += GAP;
        int half = (PANEL_W - PAD * 2 - GAP) / 2;
        addRenderableWidget(new HubWidgets.TextureButton(x, y, half, 18,
            Component.literal("Reset"), pressed ->
        {
            remember(RideAnchor.seat());
            RideAnchor.set(RideAnchor.shipped());
            report = "back to the shipped seat";
            rebuildWidgets();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(x + half + GAP, y, half, 18,
            Component.literal("Save"), pressed ->
        {
            RideAnchor.save();
            report = "saved for this install";
        }));
        y += 18 + GAP;

        // Only where there is a tree to write into, which is the whole of what
        // makes this screen development-only.
        if(RideAnchor.sourceFile() != null)
        {
            addRenderableWidget(new HubWidgets.TextureButton(x, y, PANEL_W - PAD * 2, 18,
                Component.literal("Bake into source"), pressed ->
            {
                Path written = RideAnchor.bake();
                report = written == null ? "could not write the source"
                    : "baked -- it ships now";
            }));
            y += 18 + GAP;
        }

        addRenderableWidget(new HubWidgets.TextureButton(x, y, PANEL_W - PAD * 2, 18,
            Component.literal("Close"), pressed -> onClose()));
    }

    /** Takes what was typed, or leaves the number alone if it was not one. */
    private void commitTyped()
    {
        if(typingField != null && typingBox != null)
        {
            try
            {
                remember(RideAnchor.seat());
                RideAnchor.set(typingField.set().apply(RideAnchor.seat(),
                    Float.parseFloat(typingBox.getValue().trim())));
            }
            catch(NumberFormatException notANumber)
            {
                report = "not a number";
            }
        }
        typing = null;
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        InputEvents.KeyEvent event =
            new InputEvents.KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);

        if(typingBox != null && (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
            || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER))
        {
            commitTyped();
            return true;
        }
        if(typingBox != null && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            typing = null;
            rebuildWidgets();
            return true;
        }
        // Ctrl+Z back, Ctrl+Shift+Z forward. Checked before the edit box gets a
        // look in, because an undo while typing is still an undo of the grip.
        if(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && hasControlDown())
        {
            if(hasShiftDown())
            {
                step(redo, undo, "redo");
            }
            else
            {
                step(undo, redo, "undo");
            }
            return true;
        }
        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
    }

    /** Moves one grip from one stack to the other, through the live anchor. */
    private void step(Deque<RideAnchor.Seat> from,
        Deque<RideAnchor.Seat> to, String what)
    {
        if(from.isEmpty())
        {
            report = "nothing to " + what;
            return;
        }
        to.push(RideAnchor.seat());
        RideAnchor.set(from.pop());
        report = what + " (" + from.size() + " left)";
        rebuildWidgets();
    }

    @Override
    public boolean mouseReleased(double vanillaX, double vanillaY, int vanillaButton)
    {
        // The end of a drag is one undo step. See beforeDrag.
        if(beforeDrag != null)
        {
            remember(beforeDrag);
            beforeDrag = null;
        }
        return super.mouseReleased(vanillaX, vanillaY, vanillaButton);
    }

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        InputEvents.MouseButtonEvent event =
            new InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);

        // Clicking away takes the number rather than throwing it away: it is
        // visible in the box the whole time it is being typed, so leaving is a
        // more natural "yes, that one" than reaching for enter.
        if(typingBox != null && !typingBox.isMouseOver(event.x(), event.y()))
        {
            commitTyped();
            return true;
        }
        return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
    }

    /** A slider that writes straight through to the live anchor. */
    private class Tuner extends AbstractSliderButton
    {
        private final Field field;

        Tuner(int x, int y, int width, int height, Field field)
        {
            super(x, y, width, height, Component.empty(),
                (field.get().apply(RideAnchor.seat()) - field.min())
                    / (field.max() - field.min()));
            this.field = field;
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal(String.format("%s  %.4f", field.label(), current())));
        }

        private float current()
        {
            return field.min() + (float) value * (field.max() - field.min());
        }

        @Override
        protected void applyValue()
        {
            // The first move of a drag is what an undo comes back to.
            if(beforeDrag == null)
            {
                beforeDrag = RideAnchor.seat();
            }
            RideAnchor.set(field.set().apply(RideAnchor.seat(), current()));
        }

        /**
         * Right-click types the number instead of dragging for it.
         * <p>
         * Dragging is for finding a value by eye, which is what most of these
         * are for. It is hopeless for setting one you already know -- a slider
         * this wide over a 360 degree range has several degrees under every
         * pixel -- and it is the only way to ask for a number outside the range
         * the slider offers.
         */
        @Override
        public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
        {
            if(vanillaButton == 1 && isMouseOver(vanillaX, vanillaY))
            {
                typing = field.label();
                rebuildWidgets();
                return true;
            }
            return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
        }
    }

    /**
     * No background from vanilla, because this screen draws before
     * {@code super.render} and vanilla draws the background from inside it.
     * <p>
     * In a level that background is the BLUR and nothing else, so leaving it in
     * place would blur the very hand this screen exists to look at.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX,
        int mouseY, float partialTick)
    {
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);
        NineSlice.draw(graphics, HubTextures.PANEL, 0, 0, PANEL_W, height);
        graphics.text(font, "Ride anchor", PAD, PAD,
            MenuInk.title(), MenuInk.shadow());
        graphics.text(font, "Right-click a row to type it", PAD, height - 46,
            MenuInk.dim(), MenuInk.shadow());
        graphics.text(font, "Ctrl+Z undo, Ctrl+Shift+Z redo", PAD, height - 34,
            MenuInk.dim(), MenuInk.shadow());
        if(!report.isEmpty())
        {
            graphics.text(font, report, PAD, height - 22, MenuInk.body(), MenuInk.shadow());
        }
        super.render(vanillaGraphics, mouseX, mouseY, partialTick);
    }

    /** The world carries on behind it; this is a viewfinder, not a pause. */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
