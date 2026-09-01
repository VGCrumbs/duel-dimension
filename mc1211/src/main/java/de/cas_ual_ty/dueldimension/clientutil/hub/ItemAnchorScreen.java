package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.character.ItemAnchor;
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
 * Placing a held item in a character's hand, while holding one.
 *
 * <h2>Ten numbers, four grips, one hand</h2>
 * The anchor is symmetrical — see {@link ItemAnchor} — so there is nothing here
 * for a left hand and a right hand separately. Everything moves both at once,
 * mirrored, which is also why there is no way to get them out of step.
 * <p>
 * There are four sets of those numbers, because a sword, a bucket, a bow and a
 * block are not held alike. The tabs across the top choose which set the sliders
 * are pointed at, and while this screen is open WHATEVER IS IN THE HAND is drawn
 * at that set — so a sword stands in for a block if a sword is what you happen
 * to be carrying, and the sliders always move something visible.
 *
 * <h2>Live, and off to one side</h2>
 * The same principle the duel camera's editor is built on: the thing being tuned
 * is behind the panel and changes as the slider moves, because these are numbers
 * nobody can reason about and everybody can see. So the panel takes one edge and
 * nothing dims the world — a sword that cannot be seen cannot be placed.
 *
 * <h2>Undo is the point of a tuning screen</h2>
 * Tuning is not a sequence of decisions, it is a search: you go too far, and the
 * value you want is the one you had two drags ago. Without a way back the only
 * way to find it again is to remember the number, which nobody does. So every
 * change goes on a stack and {@code Ctrl+Z} walks back down it.
 */
public class ItemAnchorScreen extends Screen
{
    private static final int PANEL_W = 200;
    private static final int ROW_H = 16;
    private static final int GAP = 3;
    private static final int PAD = 8;

    private String report = "";

    /**
     * Which of the four grips the sliders are pointed at.
     * <p>
     * Static, and deliberately: the gizmo drawn on the model has to know which
     * one it is showing, and it is drawn from the renderer rather than from
     * here. One screen exists at a time, so there is nothing for a second
     * instance to disagree with.
     */
    private static ItemAnchor.Kind editing = ItemAnchor.Kind.TOOL;

    /** What the gizmo should be drawing, asked from the renderer. */
    public static ItemAnchor.Kind editing()
    {
        return editing;
    }

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
     * Whole SETS rather than individual edits, and the set rather than the one
     * grip being edited: a step that only remembered the current tab would go
     * back to the wrong grip if the tab had moved in between. Keeping the lot is
     * simpler than describing a change, and it cannot get out of step with what
     * it claims to undo.
     */
    private final Deque<java.util.Map<ItemAnchor.Kind, ItemAnchor.Grip>> undo =
        new ArrayDeque<>();
    private final Deque<java.util.Map<ItemAnchor.Kind, ItemAnchor.Grip>> redo =
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
    private java.util.Map<ItemAnchor.Kind, ItemAnchor.Grip> beforeDrag;

    public ItemAnchorScreen()
    {
        super(Component.literal("Item anchor"));
    }

    /** One tunable: a slider over a range, applied the instant it moves. */
    private record Field(String label, float min, float max,
        java.util.function.Function<ItemAnchor.Grip, Float> get,
        java.util.function.BiFunction<ItemAnchor.Grip, Float, ItemAnchor.Grip> set)
    {
    }

    /**
     * Every number, in the order they are reached for.
     * <p>
     * The three turns first, because an item in roughly the right place pointing
     * the wrong way reads as broken while one pointing the right way slightly out
     * of place reads as nearly done. Scale last: it is the one that is obvious
     * the moment it is wrong.
     */
    private static final Field[] FIELDS = {
        new Field("Pitch", -180F, 180F, ItemAnchor.Grip::pitch,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), v, g.yaw(), g.roll(), g.scale(),
                g.localPitch(), g.localYaw(), g.localRoll())),
        new Field("Yaw", -180F, 180F, ItemAnchor.Grip::yaw,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), g.pitch(), v, g.roll(), g.scale(),
                g.localPitch(), g.localYaw(), g.localRoll())),
        new Field("Roll", -180F, 180F, ItemAnchor.Grip::roll,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), g.pitch(), g.yaw(), v, g.scale(),
                g.localPitch(), g.localYaw(), g.localRoll())),
        new Field("Across", -0.3F, 0.3F, ItemAnchor.Grip::x,
            (g, v) -> new ItemAnchor.Grip(v, g.y(), g.z(), g.pitch(), g.yaw(), g.roll(), g.scale(),
                g.localPitch(), g.localYaw(), g.localRoll())),
        new Field("Up", -0.3F, 0.3F, ItemAnchor.Grip::y,
            (g, v) -> new ItemAnchor.Grip(g.x(), v, g.z(), g.pitch(), g.yaw(), g.roll(), g.scale(),
                g.localPitch(), g.localYaw(), g.localRoll())),
        new Field("Forward", -0.3F, 0.3F, ItemAnchor.Grip::z,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), v, g.pitch(), g.yaw(), g.roll(), g.scale(),
                g.localPitch(), g.localYaw(), g.localRoll())),
        new Field("Scale", 0.1F, 2F, ItemAnchor.Grip::scale,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), g.pitch(), g.yaw(), g.roll(), v,
                g.localPitch(), g.localYaw(), g.localRoll())),
        // The second turn, about the item rather than the model. Last on the
        // panel because it is last in the hand: these are the degrees you reach
        // for once the coarse ones have it facing the right way.
        new Field("Local pitch", -180F, 180F, ItemAnchor.Grip::localPitch,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), g.pitch(), g.yaw(), g.roll(),
                g.scale(), v, g.localYaw(), g.localRoll())),
        new Field("Local yaw", -180F, 180F, ItemAnchor.Grip::localYaw,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), g.pitch(), g.yaw(), g.roll(),
                g.scale(), g.localPitch(), v, g.localRoll())),
        new Field("Local roll", -180F, 180F, ItemAnchor.Grip::localRoll,
            (g, v) -> new ItemAnchor.Grip(g.x(), g.y(), g.z(), g.pitch(), g.yaw(), g.roll(),
                g.scale(), g.localPitch(), g.localYaw(), v)),
    };

    /** Remembers where a change started from, so it can be walked back. */
    private void remember(java.util.Map<ItemAnchor.Kind, ItemAnchor.Grip> was)
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

        // Which grip, before which numbers. A slider row that does not say what
        // it is editing is four screens wearing one face.
        ItemAnchor.Kind[] kinds = ItemAnchor.Kind.values();
        int cell = (PANEL_W - PAD * 2 - GAP * (kinds.length - 1)) / kinds.length;
        for(int i = 0; i < kinds.length; i++)
        {
            ItemAnchor.Kind kind = kinds[i];
            addRenderableWidget(new HubWidgets.TabButton(x + i * (cell + GAP), y, cell, ROW_H,
                Component.literal(kind.label()), () -> editing == kind, pressed ->
            {
                editing = kind;
                rebuildWidgets();
            }));
        }
        y += ROW_H + GAP * 2;

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
                    field.get().apply(ItemAnchor.grip(editing))));
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
            remember(ItemAnchor.all());
            ItemAnchor.setAll(ItemAnchor.shipped());
            report = "back to the shipped grips";
            rebuildWidgets();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(x + half + GAP, y, half, 18,
            Component.literal("Save"), pressed ->
        {
            ItemAnchor.save();
            report = "saved for this install";
        }));
        y += 18 + GAP;

        // Only where there is a tree to write into, which is the whole of what
        // makes this screen development-only.
        if(ItemAnchor.sourceFile() != null)
        {
            addRenderableWidget(new HubWidgets.TextureButton(x, y, PANEL_W - PAD * 2, 18,
                Component.literal("Bake into source"), pressed ->
            {
                Path written = ItemAnchor.bake();
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
                remember(ItemAnchor.all());
                ItemAnchor.set(editing, typingField.set().apply(ItemAnchor.grip(editing),
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
    private void step(Deque<java.util.Map<ItemAnchor.Kind, ItemAnchor.Grip>> from,
        Deque<java.util.Map<ItemAnchor.Kind, ItemAnchor.Grip>> to, String what)
    {
        if(from.isEmpty())
        {
            report = "nothing to " + what;
            return;
        }
        to.push(ItemAnchor.all());
        ItemAnchor.setAll(from.pop());
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
                (field.get().apply(ItemAnchor.grip(editing)) - field.min())
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
                beforeDrag = ItemAnchor.all();
            }
            ItemAnchor.set(editing, field.set().apply(ItemAnchor.grip(editing), current()));
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
        graphics.text(font, "Item anchor  " + editing.label(), PAD, PAD,
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
