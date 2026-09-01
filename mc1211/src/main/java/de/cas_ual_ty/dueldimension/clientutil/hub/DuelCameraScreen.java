package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.DuelCamera;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.compat.InputEvents;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * Placing the overhead camera while looking through it.
 *
 * <h2>Development only, and it says so on the button</h2>
 * Reached with {@code \} while the overhead view is on. Everything it changes is
 * live, so the shot behind the panel IS the shot being tuned -- which is the
 * same principle {@code BillboardEditorScreen} is built on and for the same
 * reason: these are numbers nobody can reason about and everybody can see.
 *
 * <h2>Bake writes the SOURCE, not the config</h2>
 * That is the point of it. A tuning screen that saves to the player's config is
 * one whose output never reaches anybody -- which is exactly what happened to
 * 481 billboards before {@code tools/promote_monster_sprites.py} went looking
 * for them. Bake is offered only when {@link DuelCamera#sourceFile()} finds a
 * tree to write into, so on a normal install the button is simply not there.
 *
 * <h2>A panel down one side, and nothing over the middle</h2>
 * The board is the thing being framed, so the screen's first duty is to stay out
 * of the way of it. No dim, no background.
 */
public class DuelCameraScreen extends Screen
{
    private static final int PANEL_W = 190;
    private static final int ROW_H = 20;
    private static final int GAP = 4;
    private static final int PAD = 8;

    /** What the last Bake or Save reported, drawn under the buttons. */
    private String report = "";

    /**
     * Which row is being typed into, by label, or null while all six are
     * sliders.
     * <p>
     * By LABEL and not by widget, because a rebuild replaces every widget on the
     * screen and a reference to one of them would be pointing at a control that
     * is no longer on it.
     */
    private String typing;

    private EditBox typingBox;

    /** The field behind {@link #typingBox}, so a commit knows where to put it. */
    private Field typingField;

    public DuelCameraScreen()
    {
        super(Component.literal("Duel camera"));
    }

    /** One tunable: a slider over a range, applied the instant it moves. */
    private record Field(String label, float min, float max,
        java.util.function.Function<DuelCamera.Settings, Float> get,
        java.util.function.BiFunction<DuelCamera.Settings, Float, DuelCamera.Settings> set)
    {
    }

    /**
     * Every number, in the order they are reached for.
     * <p>
     * Pitch first because it is the one that changes the shot rather than
     * nudging it, and every range is centred on zero -- which is the 2D board --
     * so a slider at its middle is the projection the view starts from.
     */
    private static final Field[] FIELDS = {
        new Field("Pitch", -75F, 15F, DuelCamera.Settings::pitch,
            (s, v) -> new DuelCamera.Settings(v, s.yaw(), s.distance(), s.across(), s.along(),
                s.height())),
        new Field("Yaw", -180F, 180F, DuelCamera.Settings::yaw,
            (s, v) -> new DuelCamera.Settings(s.pitch(), v, s.distance(), s.across(), s.along(),
                s.height())),
        new Field("Distance", 0.4F, 2.5F, DuelCamera.Settings::distance,
            (s, v) -> new DuelCamera.Settings(s.pitch(), s.yaw(), v, s.across(), s.along(),
                s.height())),
        new Field("Across", -8F, 8F, DuelCamera.Settings::across,
            (s, v) -> new DuelCamera.Settings(s.pitch(), s.yaw(), s.distance(), v, s.along(),
                s.height())),
        new Field("Along", -8F, 8F, DuelCamera.Settings::along,
            (s, v) -> new DuelCamera.Settings(s.pitch(), s.yaw(), s.distance(), s.across(), v,
                s.height())),
        new Field("Height", -8F, 8F, DuelCamera.Settings::height,
            (s, v) -> new DuelCamera.Settings(s.pitch(), s.yaw(), s.distance(), s.across(),
                s.along(), v)),
    };

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
                typingBox.setValue(String.format("%.3f",
                    field.get().apply(DuelCamera.settings())));
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
            DuelCamera.setSettings(DuelCamera.Settings.DEFAULT);
            report = "back to the 2D board";
            rebuildWidgets();
        }));
        addRenderableWidget(new HubWidgets.TextureButton(x + half + GAP, y, half, 18,
            Component.literal("Save"), pressed ->
        {
            DuelCamera.save();
            report = "saved for this install";
        }));
        y += 18 + GAP;

        // Only where there is a tree to write into, which is the whole of what
        // makes this screen development-only.
        if(DuelCamera.sourceFile() != null)
        {
            addRenderableWidget(new HubWidgets.TextureButton(x, y, PANEL_W - PAD * 2, 18,
                Component.literal("Bake into source"), pressed ->
            {
                Path written = DuelCamera.bake();
                report = written == null ? "could not write the source"
                    : "baked -- it ships now";
            }));
            y += 18 + GAP;
        }

        addRenderableWidget(new HubWidgets.TextureButton(x, y, PANEL_W - PAD * 2, 18,
            Component.literal("Close"), pressed -> onClose()));
    }

    /**
     * Takes what was typed, or leaves the number alone if it was not one.
     * <p>
     * Deliberately NOT clamped to the slider's range. The range is what is
     * comfortable to drag through, not what is legal -- a shot that wants the
     * camera nine blocks back is a shot the editor should be able to state, and
     * the slider is the thing in the way of saying so.
     */
    private void commitTyped()
    {
        if(typingField != null && typingBox != null)
        {
            try
            {
                DuelCamera.setSettings(typingField.set().apply(DuelCamera.settings(),
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
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values. Built
        // here so the body below is the 26.2 one, unchanged.
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
        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
    }

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        InputEvents.MouseButtonEvent event =
            new InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);

        // Clicking away takes the number rather than throwing it away: it is
        // visible in the box the whole time it is being typed, so leaving is a
        // more natural "yes, that one" than reaching for enter. Same choice the
        // billboard editor made, for the same reason.
        if(typingBox != null && !typingBox.isMouseOver(event.x(), event.y()))
        {
            commitTyped();
            return true;
        }
        return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
    }

    /** A slider that writes straight through to the live camera. */
    private class Tuner extends AbstractSliderButton
    {
        private final Field field;

        Tuner(int x, int y, int width, int height, Field field)
        {
            super(x, y, width, height, Component.empty(),
                (field.get().apply(DuelCamera.settings()) - field.min())
                    / (field.max() - field.min()));
            this.field = field;
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal(String.format("%s  %.2f", field.label(), current())));
        }

        private float current()
        {
            return field.min() + (float)value * (field.max() - field.min());
        }

        @Override
        protected void applyValue()
        {
            DuelCamera.setSettings(field.set().apply(DuelCamera.settings(), current()));
        }

        /**
         * Right-click types the number instead of dragging for it.
         * <p>
         * Dragging is for finding a value by eye, which is what most of these
         * are for. It is hopeless for setting one you already know -- a slider
         * this wide over a 360 degree range has several degrees under every
         * pixel -- and it is the only way to ask for a number outside the range
         * the slider offers. The billboard editor reached the same conclusion
         * about the same problem.
         */
        @Override
        public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
        {
            InputEvents.MouseButtonEvent event =
                new InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);

            if(event.button() == 1 && isMouseOver(event.x(), event.y()))
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
     * In a level that background is the BLUR and nothing else -- the panorama
     * and {@code renderMenuBackground} are both gated on there being no level --
     * so leaving it in place blurs everything this screen has already put down
     * while the widgets drawn afterwards stay sharp. 26.2 refuses it too, in the
     * same words:
     * <blockquote>fillGradient, not extractBackground: that one blurs.</blockquote>
     * The dim, where this screen wants one, is its own and goes down first.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }

    /**
     * 26.2 describes itself into a render state; 1.21.1 draws now. The body is
     * unchanged -- it is handed the compatibility surface over the real
     * {@code GuiGraphics}, which is what every other ported screen does.
     */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX,
        int mouseY, float partialTick)
    {
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);
        NineSlice.draw(extractor, HubTextures.PANEL, 0, 0, PANEL_W, height);
        extractor.text(font, "Duel camera", PAD, PAD, MenuInk.title(), MenuInk.shadow());
        if(typingBox != null)
        {
            extractor.text(font, typingField.label() + " -- enter to set",
                PAD, height - 34, MenuInk.body(), MenuInk.shadow());
        }
        if(!report.isEmpty())
        {
            extractor.text(font, report, PAD, height - 22, MenuInk.body(), MenuInk.shadow());
        }
        super.render(vanillaGraphics, mouseX, mouseY, partialTick);
    }

    /** The board carries on behind it; this is a viewfinder, not a pause. */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
