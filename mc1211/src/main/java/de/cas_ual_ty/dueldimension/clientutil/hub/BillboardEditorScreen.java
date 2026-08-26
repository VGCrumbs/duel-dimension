package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.overworld.BillboardOutline;
import de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSheets;
import de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites;
import de.cas_ual_ty.dueldimension.clientutil.overworld.SpriteSource;
import de.cas_ual_ty.dueldimension.clientutil.overworld.SpriteLayer;
import de.cas_ual_ty.dueldimension.clientutil.overworld.Wings;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Building a monster's billboard while looking at it.
 * <p>
 * A panel down one side of the screen and nothing else: no dim, no background,
 * nothing over the middle. That is the whole design. Every number in it -- how
 * far out a wing sits, how tall the body stands, which cells are frames -- is
 * one you cannot reason about and can only look at, so the screen's first duty
 * is to stay out of the way of the pedestal it is editing. Changes apply and
 * save as they are made, and there is nothing to confirm because a change you
 * can see is one you have already approved or already undone.
 * <p>
 * <b>Everything on a tab fits without scrolling.</b> There is no scrolling here
 * and nothing clips, so a control that does not fit is not a control that is
 * hard to reach — it is one that is not on the screen at all. The tab strip and
 * both button strips therefore divide the panel by how many buttons they hold
 * rather than by a number written down when they were built. Numbers
 * that belong together share a row -- columns beside rows, x beside y, width
 * beside height -- because they are read as pairs and adjusted as pairs, and a
 * pair split across two rows is two things to find instead of one.
 * <p>
 * <b>Every control carries its own reset.</b> Tuning a billboard is a matter of
 * dragging something too far to find out where too far is, and without a way
 * back that costs a reload to undo. One arrow beside each control puts that one
 * number back where it started and leaves everything else alone -- which is the
 * difference between experimenting and committing.
 */
public class BillboardEditorScreen extends Screen
{
    private final Screen parent;
    private final long code;

    private static final int PANEL_W = 232;
    private static final int ROW_H = 15;
    private static final int RESET_W = 14;
    private static final int GAP = 2;
    private static final int PAD = 7;
    private static final int HEADER = 46;

    /**
     * How wide each of {@code count} buttons is when they share one strip across
     * the panel, with a gap between neighbours.
     * <p>
     * One formula, because the panel has three of these strips — the tabs, the
     * file buttons and the footer — and each used to carry its own copy with the
     * count written into it as a literal. Adding a fourth tab to an enum then
     * left the tab strip still dividing by three, which does not fail to
     * compile, does not throw, and does not even look wrong on the two tabs you
     * can see: it lays the last one out past the edge of the screen. Derived
     * from the count rather than agreeing with it.
     */
    static int share(int full, int gap, int count)
    {
        return count <= 0 ? full : (full - gap * (count - 1)) / count;
    }

    /** The width available inside the panel's padding. */
    static int content()
    {
        return PANEL_W - PAD * 2;
    }

    enum Tab
    {
        BODY("Body"),
        POSE("Frames"),
        WINGS("Wings"),
        MODEL("Model");

        private final String label;

        Tab(String label)
        {
            this.label = label;
        }
    }

    private Tab tab = Tab.BODY;

    /** A control and the one thing that puts it back. */
    private record Field(AbstractWidget widget, Runnable restore)
    {
    }

    /**
     * A slider whose value can also be typed.
     * <p>
     * Dragging is for finding a number by eye, which is what most of these are
     * for. It is hopeless for setting a number you already know: a slider two
     * hundred pixels wide covering a thousand values has several of them under
     * every pixel, and the wheel takes a notch per step. Right-click puts the
     * number in directly — which is also the only way to leave the range the
     * slider offers, and models turn out to need that.
     */
    private interface Typed
    {
        /** Doubles as the identity, since a rebuild replaces the widget. */
        String label();

        String text();

        void accept(String typed);
    }

    /** Which control is being typed into, by label, or null for none. */
    private String typing;
    /** The live control behind {@link #typingBox}, refreshed on every rebuild. */
    private Typed typingTarget;
    private EditBox typingBox;

    // The definition in pieces, because a record cannot be edited a field at a
    // time and this screen edits nothing else.
    private String sheet = "";
    private int bx;
    private int by;
    private int bw;
    private int bh;
    private int bcolumns = 4;
    private int brows = 1;
    private int bfirst;
    private int bframes = 4;
    private int bticks = MonsterSprites.DEFAULT_TICKS;
    private MonsterSprites.Loop bloop = MonsterSprites.Loop.PING_PONG;
    private int bbob;
    private int btrimX;
    private int btrimY;
    private float scale = 1F;

    private boolean pose;
    private int dfirst;

    /**
     * The 3D alternative to the sheet above.
     * <p>
     * The sprite fields are deliberately NOT cleared when a model is named. A
     * model can fail to load — a missing file, an export using a corner of glTF
     * the reader will not guess at — and the sprite is what the monster looks
     * like when it does. Naming a model is choosing a preference, not throwing
     * the other one away.
     */
    private String model = "";
    /** Which of the model's animations to play, by name; blank for none. */
    private String modelAnimation = "";
    /**
     * How far off the card the model floats, and which way it faces.
     * <p>
     * Both are about a model rather than about a sprite, so they live here
     * rather than beside the sheet's own numbers. Elevation exists because
     * standing a monster on the card is right for the ones with feet; a great
     * many of them hover. Turn exists because a model has an authored forward
     * and nothing guarantees it is the one this mod assumes.
     */
    private float elevation;
    private float turn;
    /**
     * Sideways and forward, in the model's own frame.
     * <p>
     * The third and fourth ways a model can be moved, after how tall it stands
     * and which way it looks. A monster whose origin is not over its own feet —
     * and plenty are not, these being rips rather than assets authored for a
     * card — otherwise stands beside its card rather than on it, with nothing
     * to be done about it.
     */
    private float offsetX;
    private float offsetZ;

    /**
     * One nudge per cell, and which cell the two nudge controls are pointed at.
     * <p>
     * A picker rather than a control per cell, because a sheet can hold sixteen
     * of them and a panel cannot. Kept as a list the whole time and handed to
     * the definition on every change, exactly like every other field here.
     */
    private final java.util.List<SpriteLayer.Offset> boffsets = new java.util.ArrayList<>();
    private int cellPick;

    private boolean winged;
    private int wx;
    private int wy;
    private int ww;
    private int wh;
    private int wcolumns = 4;
    private int wrows = 1;
    private int wfirst;
    private int wframes = 4;
    private int wticks = MonsterSprites.DEFAULT_TICKS;
    private MonsterSprites.Loop wloop = MonsterSprites.Loop.LOOP;
    private float anchor = Wings.DEFAULT_ANCHOR;
    private float spacing = Wings.DEFAULT_SPACING;
    private int wtrimX;
    private int wtrimY;
    private float wscale = Wings.DEFAULT_SCALE;

    /** What the last export said, shown in place of the subtitle. */
    private String notice;
    /**
     * Whether {@link #notice} is good news.
     * <p>
     * It was drawn green whatever it said, so "could not copy that file" and
     * "4 parts, 5 anims" arrived in the same colour — and green is the colour
     * this panel uses for a thing that worked.
     */
    private boolean noticeGood;

    private int row;
    /** Where the controls stopped, which is where the preview begins. */
    private int contentBottom;

    public BillboardEditorScreen(Screen parent, long code)
    {
        super(Component.literal("Billboard"));
        this.parent = parent;
        this.code = code;
        read();
        // The hologram standing in the world is the thing being cropped, so it
        // is the thing that gets the box drawn round it.
        BillboardOutline.watch(code);
    }

    /** Loads the card's current definition, or sensible starting values. */
    private void read()
    {
        MonsterSprites.Definition definition = MonsterSprites.of(code);
        if(definition == null)
        {
            return;
        }
        // The scale, the model and the animation belong to the definition rather
        // than to the sheet, so they are read whether or not there is one.
        scale = definition.scale();
        model = definition.model() == null ? "" : definition.model();
        modelAnimation = definition.animation() == null ? "" : definition.animation();
        elevation = definition.elevation();
        turn = definition.turn();
        offsetX = definition.offsetX();
        offsetZ = definition.offsetZ();

        SpriteLayer body = definition.body();
        if(body == null)
        {
            // A monster that is a model and no sprite. The sprite fields keep
            // the starting values the constructor gave them, which is what the
            // Body tab needs to show if a duellist decides to add one.
            return;
        }
        sheet = body.sheet();
        bx = body.x();
        by = body.y();
        bw = body.w();
        bh = body.h();
        bcolumns = body.columns();
        brows = body.rows();
        bfirst = body.first();
        bframes = body.frames();
        bticks = body.ticks();
        bloop = body.loop();
        bbob = body.bob();
        btrimX = body.trimX();
        btrimY = body.trimY();

        boffsets.clear();
        boffsets.addAll(body.offsets());

        pose = definition.defence() != null;
        dfirst = pose ? definition.defence().first() : bcolumns * brows - 1;

        Wings wings = definition.wings();
        winged = wings != null;
        if(winged)
        {
            SpriteLayer layer = wings.layer();
            wx = layer.x();
            wy = layer.y();
            ww = layer.w();
            wh = layer.h();
            wcolumns = layer.columns();
            wrows = layer.rows();
            wfirst = layer.first();
            wframes = layer.frames();
            wticks = layer.ticks();
            wloop = layer.loop();
            wtrimX = layer.trimX();
            wtrimY = layer.trimY();
            anchor = wings.anchor();
            spacing = wings.spacing();
            wscale = wings.scale();
        }
    }

    /** Writes the definition back and saves it, on every change. */
    /** Opens a control for typing, in place of dragging it. */
    private void type(Typed control)
    {
        typing = control.label();
        rebuildWidgets();
    }

    /** Takes what was typed, or leaves the value alone if it was not a number. */
    private void commitTyped()
    {
        if(typingTarget != null && typingBox != null)
        {
            typingTarget.accept(typingBox.getValue().trim());
            apply();
        }
        typing = null;
        typingTarget = null;
        typingBox = null;
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int vanillaKey, int vanillaScancode, int vanillaModifiers)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.KeyEvent(vanillaKey, vanillaScancode, vanillaModifiers);
        if(typing != null)
        {
            int key = event.key();
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
            {
                commitTyped();
                return true;
            }
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            {
                // Answers the box rather than leaving the editor, which is the
                // safe reading of escape while a question is up -- and the same
                // one the deck editor takes.
                typing = null;
                typingTarget = null;
                typingBox = null;
                rebuildWidgets();
                return true;
            }
        }
        return super.keyPressed(vanillaKey, vanillaScancode, vanillaModifiers);
    }

    @Override
    public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
    {
        // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
        de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
        boolean doubled = false;
        // Clicking away takes the number rather than discarding it: the value is
        // visible in the box while it is being typed, so leaving it is a much
        // more natural way to say "yes, that one" than reaching for enter.
        if(typing != null && typingBox != null && !typingBox.isMouseOver(event.x(), event.y()))
        {
            commitTyped();
            return true;
        }
        return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
    }

    /**
     * Says something in the subtitle, in place of "editing", until something
     * changes what it was about.
     * <p>
     * Kept until then rather than timed out: the messages here are the answer to
     * a button that was just pressed, and an answer that fades is one you can
     * miss by looking at the model instead of at the panel.
     */
    private void say(String what, boolean good)
    {
        notice = what;
        noticeGood = good;
    }

    /** Takes the subtitle back to saying what this screen is doing. */
    private void hush()
    {
        notice = null;
        noticeGood = false;
    }

    private void apply()
    {
        // A monster is described by a sprite, or by a model, or by both. Nothing
        // named at all is the only thing that means "no billboard".
        //
        // This used to ask only about the sheet, which was the whole truth when
        // a sheet was the only thing a definition could hold. It stopped being
        // true when models arrived, and the way it failed was quiet: importing a
        // model for a card that never had a sprite named the model, saved, and
        // deleted the entry on the way out -- indistinguishable from an import
        // that had not worked.
        if(sheet.isBlank() && model.isBlank())
        {
            MonsterSprites.remove(code);
            MonsterSprites.save();
            return;
        }
        SpriteLayer body = sheet.isBlank() ? null
            : new SpriteLayer(sheet, bx, by, bw, bh, bcolumns, brows, bfirst,
                bframes, bticks, bloop, btrimX, btrimY, bbob, boffsets);
        // The pose shares the nudges, because it shares the cells: a defence
        // cell is one of the same grid, and its entry in the list is its own.
        SpriteLayer defence = pose && !sheet.isBlank()
            ? new SpriteLayer(sheet, bx, by, bw, bh, bcolumns, brows, dfirst, 1, bticks,
                MonsterSprites.Loop.LOOP, btrimX, btrimY, bbob, boffsets)
            : null;
        Wings wings = winged && !sheet.isBlank()
            ? new Wings(new SpriteLayer(sheet, wx, wy, ww, wh, wcolumns, wrows, wfirst, wframes,
                wticks, wloop, wtrimX, wtrimY), anchor, spacing, wscale)
            : null;
        MonsterSprites.put(new MonsterSprites.Definition(code, body, defence, wings, scale,
            model.isBlank() ? null : model,
            modelAnimation.isBlank() ? null : modelAnimation, elevation, turn,
            offsetX, offsetZ));
        MonsterSprites.save();
    }

    // ------------------------------------------------------------- layout --

    private int panelX()
    {
        return width - PANEL_W - 4;
    }

    private int left()
    {
        return panelX() + PAD;
    }

    private int full()
    {
        return content();
    }

    private int rowY()
    {
        return HEADER + row * (ROW_H + GAP);
    }

    /** One control across the panel, its reset beside it, then down a row. */
    private void wide(Field field)
    {
        place(field, left(), full());
        row++;
    }

    /** Two controls sharing a row, each with its own reset. */
    private void pair(Field first, Field second)
    {
        int half = (full() - GAP) / 2;
        place(first, left(), half);
        place(second, left() + half + GAP, half);
        row++;
    }

    private void place(Field field, int x, int w)
    {
        int control = w - RESET_W - GAP;
        if(field.widget() instanceof Typed typed && typed.label().equals(typing))
        {
            // In the slider's own place, at its own size, so the panel does not
            // reflow under the mouse that opened it.
            typingTarget = typed;
            EditBox box = new EditBox(font, x, rowY(), control, ROW_H,
                Component.literal(typed.label()));
            box.setBordered(false);
            box.setY(box.getY() + (box.getHeight() - 8) / 2);
            box.setMaxLength(12);
            box.setValue(typed.text());
            typingBox = box;
            addRenderableWidget(box);
            setInitialFocus(box);
        }
        else
        {
            field.widget().setX(x);
            field.widget().setY(rowY());
            field.widget().setWidth(control);
            addRenderableWidget(field.widget());
        }

        // The arrow puts THIS number back and touches nothing else. Rebuilding
        // afterwards is what makes the control redraw at its restored value --
        // a slider knows its own position and will not learn a new one from the
        // outside.
        addRenderableWidget(Button.builder(Component.literal("↺"), pressed ->
        {
            field.restore().run();
            apply();
            rebuildWidgets();
        }).bounds(x + control + GAP, rowY(), RESET_W, ROW_H).build());
    }

    @Override
    protected void init()
    {
        row = 0;

        // Divided by however many tabs there ARE. This said 3 while the enum had
        // grown to four, so Model was laid out past the panel's own edge and off
        // the side of the screen: it could be reached only by the sliver of its
        // left edge, and once reached nothing said it was the live tab, because
        // the brackets that say so were drawn off-screen too.
        int tabW = share(full(), GAP, Tab.values().length);
        int at = 0;
        for(Tab which : Tab.values())
        {
            Tab chosen = which;
            addRenderableWidget(Button.builder(
                Component.literal(which == tab ? "[" + which.label + "]" : which.label),
                pressed ->
                {
                    tab = chosen;
                    // The last message was about the tab being left.
                    hush();
                    rebuildWidgets();
                })
                .bounds(left() + at * (tabW + GAP), HEADER - 22, tabW, 16).build());
            at++;
        }

        switch(tab)
        {
            case BODY -> body();
            case POSE -> poseTab();
            case WINGS -> wings();
            case MODEL -> modelTab();
        }
        contentBottom = rowY();

        int sheets = height - 46;
        int third = share(full(), GAP, 3);
        int quarter = share(full(), GAP, 4);
        // The same three gestures whichever tab is in front -- bring a file in,
        // show me where they live, read them again -- pointed at the kind of
        // file that tab is about. They used to be pointed at sheets always, so
        // on the Model tab "Import" asked for a PNG and "Folder" opened a folder
        // with no models in it: the right question, answered about the wrong
        // thing, which reads as the feature being missing.
        boolean models = tab == Tab.MODEL;
        addRenderableWidget(Button.builder(Component.literal("Import"), pressed ->
        {
            if(models)
            {
                importModel();
            }
            else
            {
                importSheet();
            }
            rebuildWidgets();
        }).bounds(left(), sheets, quarter, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Folder"), pressed ->
        {
            if(models)
            {
                de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.open();
            }
            else
            {
                MonsterSheets.open();
            }
        }).bounds(left() + quarter + GAP, sheets, quarter, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Reload"), pressed ->
            {
                if(models)
                {
                    // No scan to redo: models are read on demand, so forgetting
                    // what was read IS the reload.
                    de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.clear();
                }
                else
                {
                    MonsterSheets.reload();
                }
                rebuildWidgets();
            }).bounds(left() + (quarter + GAP) * 2, sheets, quarter, 18).build());
        // The one button that puts this work somewhere other people can get it.
        // Disabled rather than hidden when there is no source tree, so that its
        // absence is a fact about the install rather than a missing feature.
        Button toMod = Button.builder(Component.literal("To mod"), pressed ->
        {
            SpriteSource.Outcome outcome = SpriteSource.promote(code);
            say(outcome.message(), outcome.ok());
            rebuildWidgets();
        }).bounds(left() + (quarter + GAP) * 3, sheets, quarter, 18).build();
        toMod.active = SpriteSource.available() && MonsterSprites.has(code);
        addRenderableWidget(toMod);

        int footer = height - 24;
        // One switch for both boxes -- the cuts over the sheet and the box round
        // the hologram are two views of the same rectangle, and a pair of
        // toggles for one idea is a thing to get out of step. Bracketed for on,
        // the same way the tabs above say which of them is the live one.
        addRenderableWidget(Button.builder(Component.literal(outlineLabel()), pressed ->
        {
            BillboardOutline.show(!BillboardOutline.shown());
            pressed.setMessage(Component.literal(outlineLabel()));
        }).bounds(left(), footer, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), pressed ->
        {
            MonsterSprites.remove(code);
            MonsterSprites.save();
            // Everything that names a billboard, not just the sheet. Clearing
            // the sheet alone was enough while apply() removed on a blank sheet;
            // now that a model keeps a definition alive, a leftover model name
            // means the very next slider nudge writes the entry straight back
            // and Remove looks like it did not take.
            sheet = "";
            model = "";
            modelAnimation = "";
            elevation = 0F;
            turn = 0F;
            offsetX = 0F;
            offsetZ = 0F;
            // The last message described a billboard that is now gone.
            hush();
            rebuildWidgets();
        }).bounds(left() + third + GAP, footer, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), pressed -> onClose())
            .bounds(left() + (third + GAP) * 2, footer, third, 18).build());
    }

    /** A sheet chosen from disk becomes the sheet being edited. */
    private void importSheet()
    {
        String taken = MonsterSheets.take(MonsterSheets.choose());
        if(taken != null)
        {
            sheet = taken;
            apply();
        }
    }

    /**
     * The same for a model, but it says what happened.
     * <p>
     * A sheet either appears in the preview underneath or it does not, so an
     * import that went wrong is visible immediately. A model has nowhere to show
     * itself on this screen — it stands in the world behind the panel — and it
     * can fail for reasons that are about the FILE rather than about the import:
     * a .glb using a corner of glTF the reader will not guess at looks exactly
     * like a monster that was never given a model. So it is loaded here, while
     * the duellist is still looking at the button they pressed, and the answer
     * goes in the subtitle.
     */
    private void importModel()
    {
        java.nio.file.Path chosen =
            de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.choose();
        if(chosen == null)
        {
            // Cancelled, or there is no native dialog on this machine. The two
            // are indistinguishable from here, so this says the thing that is
            // useful in the second case and harmless in the first.
            say("cancelled - or drop .glb files in the models folder", false);
            return;
        }
        String taken = de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.take(chosen);
        if(taken == null)
        {
            say("could not copy that file - see the log", false);
            return;
        }
        setModel(taken);
        apply();

        var mesh = de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.get(taken);
        say(mesh == null ? taken + " will not load - see the log"
            : taken + ": " + mesh.parts().size() + " parts, "
                + mesh.animationNames().size() + " anims", mesh != null);
    }

    private void body()
    {
        EditBox box = new EditBox(font, left(), rowY(), full() - RESET_W - GAP, ROW_H,
            Component.literal("Sheet"));
        box.setBordered(false);
        box.setY(box.getY() + (box.getHeight() - 8) / 2);
        box.setMaxLength(128);
        box.setValue(sheet);
        box.setResponder(text ->
        {
            sheet = text.trim();
            apply();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("↺"), pressed ->
        {
            sheet = "";
            apply();
            rebuildWidgets();
        }).bounds(left() + full() - RESET_W, rowY(), RESET_W, ROW_H).build());
        row++;

        pair(count("Cols", 1, 16, 4, () -> bcolumns, value -> bcolumns = value),
            count("Rows", 1, 16, 1, () -> brows, value -> brows = value));
        pair(count("From", 0, 63, 0, () -> bfirst, value -> bfirst = value),
            count("Frames", 1, 64, 4, () -> bframes, value -> bframes = value));
        pair(count("Speed", 1, 20, MonsterSprites.DEFAULT_TICKS, () -> bticks,
                value -> bticks = value),
            loop(() -> bloop, value -> bloop = value, MonsterSprites.Loop.PING_PONG));
        // Beside the size rather than on a row of its own, because the sheet
        // preview underneath is worth more than the tidiness of one control per
        // line -- and because both of these are "how big and how alive".
        pair(amount("Size", 0.1F, 4F, 1F, () -> scale, value -> scale = value),
            count("Bob", 0, 32, 0, () -> bbob, value -> bbob = value));
        pair(count("Crop x", 0, 1024, 0, () -> bx, value -> bx = value),
            count("Crop y", 0, 1024, 0, () -> by, value -> by = value));
        pair(count("Crop w", 0, 1024, 0, () -> bw, value -> bw = value),
            count("Crop h", 0, 1024, 0, () -> bh, value -> bh = value));
        pair(count("Trim x", 0, 128, 0, () -> btrimX, value -> btrimX = value),
            count("Trim y", 0, 128, 0, () -> btrimY, value -> btrimY = value));
    }

    /**
     * The 3D alternative to a sprite sheet.
     * <p>
     * A separate tab rather than more rows on Body, because almost none of Body
     * applies: a model has no grid, no cells and no frame count, and putting
     * "Cols" beside "Model" would invite the reading that one sets up the other.
     * <p>
     * The sprite settings on the other tabs stay live and are still saved. A
     * model that will not load falls back to them, so they are the monster's
     * other half rather than the half it replaced.
     */
    /**
     * Names the model, dropping the animation when it is a different one.
     * <p>
     * An animation is chosen by NAME out of one particular file's list, so it
     * means nothing once the file changes — and it fails worse than meaning
     * nothing: a name that happens to exist in both files plays something nobody
     * picked, which looks like the picker choosing at random. Four things set
     * the model — the box, its reset, the file picker and an import — and they
     * all come through here so that none of them can be the one that forgets.
     */
    private void setModel(String named)
    {
        if(!named.equals(model))
        {
            modelAnimation = "";
        }
        model = named;
    }

    private void modelTab()
    {
        EditBox box = new EditBox(font, left(), rowY(), full() - RESET_W - GAP, ROW_H,
            Component.literal("Model"));
        box.setBordered(false);
        box.setY(box.getY() + (box.getHeight() - 8) / 2);
        box.setMaxLength(128);
        box.setValue(model);
        box.setResponder(text ->
        {
            // Named rather than loaded. This used to forget every baked model on
            // every keystroke so that an edited file would be re-read -- but
            // "every keystroke" is the problem: the board behind this panel is
            // still drawing, and it asks for its models by name each frame, so
            // typing six characters re-read and re-baked every model in sight
            // six times over, and printed a warning for each half-typed name on
            // the way. Re-reading a changed file is what the Reload button is
            // for, and pressing it is a deliberate act rather than a side effect
            // of naming something.
            setModel(text.trim());
            apply();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("↺"), pressed ->
        {
            setModel("");
            apply();
            rebuildWidgets();
        }).bounds(left() + full() - RESET_W, rowY(), RESET_W, ROW_H).build());
        row++;

        wide(filePicker());

        // The two adjustable factors a model has. Height is shared with the
        // sprite deliberately: it means the same thing for both -- how tall the
        // monster stands -- and a monster that changed size when it changed
        // representation would be a worse answer than one number.
        pair(amount("Size", 0.1F, 4F, 1F, () -> scale, value -> scale = value),
            animationPicker());
        // How high and which way, beside each other because they are the two
        // halves of "where does this thing stand" and are tuned together while
        // walking round it.
        pair(amount("Lift", -1F, 4F, 0F, () -> elevation, value -> elevation = value),
            amount("Turn", 0F, 360F, 0F, () -> turn, value -> turn = value));
        // The other two axes. Paired with each other rather than with Lift,
        // because these two are read together -- they are one position, nudged
        // by looking at it from above -- while height is judged from the side.
        pair(amount("Off x", -2F, 2F, 0F, () -> offsetX, value -> offsetX = value),
            amount("Off z", -2F, 2F, 0F, () -> offsetZ, value -> offsetZ = value));

        wide(modelSummary());
    }

    /**
     * Cycles the {@code .glb} files that are actually in the folder.
     * <p>
     * The box above takes a name, and a name typed from memory is a name that
     * can be typed wrongly — after which the monster stays a sprite and nothing
     * on screen distinguishes "spelled it wrong" from "that file is broken".
     * This offers only files that exist, so the box is for when you know what
     * you want and this is for when you want to see what there is.
     * <p>
     * A cycling button rather than a list, which is what every other choice on
     * this screen is, and what the panel has room for.
     */
    private Field filePicker()
    {
        java.util.List<String> names =
            de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.names();
        String label;
        if(names.isEmpty())
        {
            label = "no .glb found - Import";
        }
        else
        {
            int at = names.indexOf(model);
            label = at < 0 ? "Pick 1 of " + names.size()
                : "File " + (at + 1) + " of " + names.size();
        }
        Button button = Button.builder(Component.literal(label), pressed ->
        {
            java.util.List<String> options =
                de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.names();
            if(options.isEmpty())
            {
                return;
            }
            // -1 lands on 0, so a box holding a name that is not a file steps to
            // the first one that is.
            setModel(options.get((options.indexOf(model) + 1) % options.size()));
            apply();
            // The box above has to be rebuilt to show the new name; an EditBox
            // does not learn its value from outside.
            rebuildWidgets();
        }).bounds(0, 0, 10, ROW_H).build();
        button.active = !names.isEmpty();
        return new Field(button, () -> model = "");
    }

    /**
     * Cycles the animations the loaded file actually declares.
     * <p>
     * Read off the model rather than typed, because their names carry no
     * meaning: this dragon's are slot_0, slot_2, slot_4, slot_5 and slot_6 --
     * the slot indices of the game it came from. Nothing in the file says which
     * is idle and which is an attack, so the only way to assign them is to look
     * at each one.
     */
    /**
     * A slot, and what the game uses it for where that is known.
     * <p>
     * The bare name otherwise. An unlabelled slot is not a slot with no purpose
     * — it is one whose purpose nobody has read out of the executable yet — so
     * it is shown as it is rather than dressed up.
     */
    private static String label(String slot)
    {
        String meaning = de.cas_ual_ty.dueldimension.clientutil.model.ModelSkeleton
            .meaning(slot);
        return meaning == null ? slot : slot + " (" + meaning + ")";
    }

    private Field animationPicker()
    {
        java.util.List<String> names = animationNames();
        // "idle", not "none": leaving this unset no longer means the bind pose,
        // it means slot_0, and a control that says none while the monster is
        // visibly moving is a control nobody will trust again.
        //
        // A named slot says what it is. Only three of the eight are named, and
        // that is the point of showing them: the rest are animations a duellist
        // has to identify by watching, so the ones that need no watching should
        // not look the same.
        String shown = names.isEmpty() ? "no anims"
            : (modelAnimation.isBlank() ? "idle" : label(modelAnimation));
        Button button = Button.builder(Component.literal("Anim " + shown), pressed ->
        {
            java.util.List<String> options = animationNames();
            if(options.isEmpty())
            {
                return;
            }
            int at = options.indexOf(modelAnimation);
            // -1 lands on 0, so "none" steps to the first one.
            modelAnimation = options.get((at + 1) % options.size());
            pressed.setMessage(Component.literal("Anim " + label(modelAnimation)));
            apply();
        }).bounds(0, 0, 10, ROW_H).build();
        // Back to the idle, which is what an unset animation now means. The
        // reset arrow applies and rebuilds around this, so it only has to say
        // what the value becomes.
        return new Field(button, () -> modelAnimation = "");
    }

    /** What the loaded model actually is, so a typo reads as a typo. */
    private Field modelSummary()
    {
        String text;
        if(model.isBlank())
        {
            text = "sprite (no model)";
        }
        else
        {
            var mesh = de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.get(model);
            text = mesh == null ? "not loaded - see the log"
                : mesh.parts().size() + " parts, "
                    + mesh.parts().stream().mapToInt(
                        de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh.Part::vertexCount)
                        .sum() + " verts, "
                    + mesh.animationNames().size() + " anims";
        }
        Button button = Button.builder(Component.literal(text), pressed ->
        {
            de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.clear();
            rebuildWidgets();
        }).bounds(0, 0, 10, ROW_H).build();
        button.active = !model.isBlank();
        return new Field(button, () -> { });
    }

    private java.util.List<String> animationNames()
    {
        if(model.isBlank())
        {
            return java.util.List.of();
        }
        var mesh = de.cas_ual_ty.dueldimension.clientutil.model.MonsterModels.get(model);
        return mesh == null ? java.util.List.of() : mesh.animationNames();
    }

    private void poseTab()
    {
        wide(toggle("Defence pose", () -> pose, value -> pose = value, false));
        wide(count("Pose cell", 0, 63, bcolumns * brows - 1, () -> dfirst,
            value -> dfirst = value));
        wide(picker());
        pair(count("Off x", -64, 64, 0, () -> offsetOf(cellPick).x(),
                value -> nudge(cellPick, value, offsetOf(cellPick).y())),
            count("Off y", -64, 64, 0, () -> offsetOf(cellPick).y(),
                value -> nudge(cellPick, offsetOf(cellPick).x(), value)));
    }

    /**
     * Which cell the nudges point at, as a button rather than a slider.
     * <p>
     * Changing it has to rebuild the two nudge controls, since a slider knows
     * its own position and will not learn a new one from outside -- and a
     * slider that rebuilt the panel underneath the mouse mid-drag would tear
     * itself out from under the drag. A button is pressed once and released.
     */
    private Field picker()
    {
        int cells = Math.max(1, bcolumns * brows);
        Button button = Button.builder(
            Component.literal("Cell " + (cellPick + 1) + " of " + cells), pressed ->
            {
                cellPick = (cellPick + 1) % cells;
                rebuildWidgets();
            }).bounds(0, 0, 10, ROW_H).build();
        return new Field(button, () -> cellPick = 0);
    }

    private SpriteLayer.Offset offsetOf(int cell)
    {
        return cell >= 0 && cell < boffsets.size() ? boffsets.get(cell) : SpriteLayer.SQUARE;
    }

    /** Grows the list to reach the cell, so a nudge to cell 7 does not need 0..6 set. */
    private void nudge(int cell, int dx, int dy)
    {
        while(boffsets.size() <= cell)
        {
            boffsets.add(SpriteLayer.SQUARE);
        }
        boffsets.set(cell, new SpriteLayer.Offset(dx, dy));
    }

    private void wings()
    {
        wide(toggle("Wings", () -> winged, value -> winged = value, false));
        pair(count("Cols", 1, 16, 4, () -> wcolumns, value -> wcolumns = value),
            count("Rows", 1, 16, 1, () -> wrows, value -> wrows = value));
        pair(count("From", 0, 63, 0, () -> wfirst, value -> wfirst = value),
            count("Frames", 1, 64, 4, () -> wframes, value -> wframes = value));
        pair(count("Speed", 1, 20, MonsterSprites.DEFAULT_TICKS, () -> wticks,
                value -> wticks = value),
            loop(() -> wloop, value -> wloop = value, MonsterSprites.Loop.LOOP));
        pair(count("Crop x", 0, 1024, 0, () -> wx, value -> wx = value),
            count("Crop y", 0, 1024, 0, () -> wy, value -> wy = value));
        pair(count("Crop w", 0, 1024, 0, () -> ww, value -> ww = value),
            count("Crop h", 0, 1024, 0, () -> wh, value -> wh = value));
        pair(count("Trim x", 0, 128, 0, () -> wtrimX, value -> wtrimX = value),
            count("Trim y", 0, 128, 0, () -> wtrimY, value -> wtrimY = value));
        wide(amount("Size", 0.05F, 2F, Wings.DEFAULT_SCALE, () -> wscale,
            value -> wscale = value));
        wide(amount("Height", 0F, 1.5F, Wings.DEFAULT_ANCHOR, () -> anchor,
            value -> anchor = value));
        wide(amount("Gap", -0.4F, 0.8F, Wings.DEFAULT_SPACING, () -> spacing,
            value -> spacing = value));
    }

    // ------------------------------------------------------------ drawing --

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        extractor.fill(panelX(), 0, width, height, 0xC0101014);
        extractor.fill(panelX(), 0, panelX() + 1, height, 0x60FFD700);

        Properties card = DdDatabase.PROPERTIES_LIST.get(code);
        String name = card == null ? Long.toString(code) : card.getName();
        extractor.text(font, font.plainSubstrByWidth(name, full()), left(), 7, 0xFFF4D089, true);
        extractor.text(font, notice != null ? notice
                : MonsterSprites.has(code) ? "editing" : "no billboard yet",
            left(), 17,
            notice == null ? 0xFF9A9A9A : noticeGood ? 0xFF7CE38B : 0xFFE0704C, true);

        drawSlices(extractor);

        super.render(extractor.vanilla(), mouseX, mouseY, partialTick);
    }

    /** The sheet's own accent, and the wings' -- blue, as asked for. */
    private static final int BODY_LINE = 0xFFF4D089;
    private static final int BODY_FILL = 0x33F4D089;
    private static final int WING_LINE = 0xFF63C8FF;
    private static final int WING_FILL = 0x3363C8FF;
    private static final int POSE_LINE = 0xFF7CE38B;
    private static final int PICK_LINE = 0xFFFFFFFF;

    /**
     * The sheet, with the cuts drawn on it.
     * <p>
     * Six numbers describe how a sheet is sliced -- a region and a grid -- and
     * six numbers are six things to hold in your head while looking at a
     * picture that is not in front of you. Drawing the grid ON the sheet turns
     * all of them into one glance: the cells either land on the sprites or they
     * do not, and when they do not it is obvious which way to nudge them.
     * <p>
     * The frames actually in the run are tinted, so a first-cell or frame-count
     * that runs off the end of the art shows up as tinted emptiness rather than
     * as a monster that mysteriously freezes.
     */
    private void drawSlices(GuiGraphicsExtractor extractor)
    {
        if(sheet.isBlank())
        {
            return;
        }
        SpriteLayer body = new SpriteLayer(sheet, bx, by, bw, bh, bcolumns, brows, bfirst,
            bframes, bticks, bloop, btrimX, btrimY);
        int[] size = MonsterSprites.sizeOf(body.texture());
        if(size[0] <= 0 || size[1] <= 0)
        {
            return;
        }

        int room = height - 52 - contentBottom - 6;
        if(room < 20)
        {
            return;
        }
        int drawW = full();
        int drawH = Math.round(drawW * size[1] / (float)size[0]);
        if(drawH > room)
        {
            drawH = room;
            drawW = Math.round(drawH * size[0] / (float)size[1]);
        }
        int x = left() + (full() - drawW) / 2;
        int y = contentBottom + 4;

        // Something behind it, because a sprite sheet is mostly transparent and
        // a grid drawn over nothing is a grid you cannot line anything up with.
        extractor.fill(x - 1, y - 1, x + drawW + 1, y + drawH + 1, 0xFF202028);
        DdBlitUtil.fullBlit(extractor, body.texture(), x, y, drawW, drawH);

        if(!BillboardOutline.shown())
        {
            return;
        }
        grid(extractor, body, size, x, y, drawW, drawH, BODY_LINE, BODY_FILL,
            pose ? dfirst : -1, tab == Tab.POSE ? cellPick : -1);
        if(winged)
        {
            grid(extractor, new SpriteLayer(sheet, wx, wy, ww, wh, wcolumns, wrows, wfirst,
                wframes, wticks, wloop, wtrimX, wtrimY), size, x, y, drawW, drawH, WING_LINE,
                WING_FILL, -1, -1);
        }
    }

    /** One layer's region and cells, drawn over the sheet. */
    private void grid(GuiGraphicsExtractor extractor, SpriteLayer layer, int[] size, int x, int y,
        int drawW, int drawH, int line, int fill, int poseCell, int picked)
    {
        float scaleX = drawW / (float)size[0];
        float scaleY = drawH / (float)size[1];
        float regionW = layer.w() > 0 ? layer.w() : size[0] - layer.x();
        float regionH = layer.h() > 0 ? layer.h() : size[1] - layer.y();
        float cellW = regionW / layer.columns();
        float cellH = regionH / layer.rows();

        for(int cell = 0; cell < layer.columns() * layer.rows(); cell++)
        {
            int column = cell % layer.columns();
            int rowOf = cell / layer.columns();
            // The box actually SAMPLED, trim included, because a box drawn
            // where the cell is rather than where the crop is would show a
            // trim as having done nothing. Asked of the layer rather than
            // worked out again here, so that the grid on the sheet and the box
            // round the hologram cannot come to disagree about where the crop
            // is -- including that it only bites at the region's outer edge.
            float[] window = layer.windowAt(cell);
            int x0 = x + Math.round((layer.x() + (column + window[0]) * cellW) * scaleX);
            int x1 = x + Math.round((layer.x() + (column + window[2]) * cellW) * scaleX);
            int y0 = y + Math.round((layer.y() + (rowOf + window[1]) * cellH) * scaleY);
            int y1 = y + Math.round((layer.y() + (rowOf + window[3]) * cellH) * scaleY);

            boolean inRun = cell >= layer.first() && cell < layer.first() + layer.frames();
            if(inRun)
            {
                extractor.fill(x0, y0, x1, y1, fill);
            }
            // The cell being nudged wins the colour, because while that tab is
            // open it is the one thing you are looking for.
            int edge = cell == picked ? PICK_LINE : cell == poseCell ? POSE_LINE : line;
            if(inRun || cell == poseCell || cell == picked)
            {
                extractor.fill(x0, y0, x1, y0 + 1, edge);
                extractor.fill(x0, y1 - 1, x1, y1, edge);
                extractor.fill(x0, y0, x0 + 1, y1, edge);
                extractor.fill(x1 - 1, y0, x1, y1, edge);
            }
            else
            {
                // Outside the run: the cut is still shown, faintly, so a frame
                // count that is one short is one obvious empty box.
                int faint = line & 0x40FFFFFF;
                extractor.fill(x0, y0, x1, y0 + 1, faint);
                extractor.fill(x0, y0, x0 + 1, y1, faint);
            }
        }
    }

    /** No dim and no blur: the world behind this panel is the preview. */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private static String outlineLabel()
    {
        return BillboardOutline.shown() ? "[Outline]" : "Outline";
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    /**
     * Stops outlining when this screen goes away.
     * <p>
     * {@code removed} rather than {@code onClose}, because the screen can leave
     * by routes that never call onClose -- and a box left drawn round a monster
     * nobody is editing is a rendering bug with no way to switch it off.
     */
    @Override
    public void removed()
    {
        BillboardOutline.stop();
        super.removed();
    }

    // ------------------------------------------------------------- widgets --

    private Field count(String label, int min, int max, int fallback, IntSupplier get,
        IntConsumer set)
    {
        IntConsumer write = value ->
        {
            set.accept(value);
            apply();
        };
        return new Field(new Count(label, min, max, get, write), () -> set.accept(fallback));
    }

    private Field amount(String label, float min, float max, float fallback, Supplier<Float> get,
        Consumer<Float> set)
    {
        Consumer<Float> write = value ->
        {
            set.accept(value);
            apply();
        };
        return new Field(new Amount(label, min, max, get, write), () -> set.accept(fallback));
    }

    /**
     * On or off, as a button whose label states which.
     * <p>
     * A button rather than a checkbox because this mod has no checkbox --
     * every yes/no in it is a button that says what it currently is, and one
     * that broke the pattern would be the odd one out rather than an
     * improvement.
     */
    private Field toggle(String label, BooleanSupplier get, Consumer<Boolean> set,
        boolean fallback)
    {
        Button button = Button.builder(
            Component.literal(label + ": " + onOff(get.getAsBoolean())), pressed ->
            {
                set.accept(!get.getAsBoolean());
                apply();
                pressed.setMessage(Component.literal(label + ": " + onOff(get.getAsBoolean())));
            }).bounds(0, 0, 10, ROW_H).build();
        return new Field(button, () -> set.accept(fallback));
    }

    private static String onOff(boolean value)
    {
        return value ? "ON" : "OFF";
    }

    private Field loop(Supplier<MonsterSprites.Loop> get, Consumer<MonsterSprites.Loop> set,
        MonsterSprites.Loop fallback)
    {
        Button button = Button.builder(Component.literal(loopName(get.get())), pressed ->
        {
            set.accept(after(get.get()));
            apply();
            pressed.setMessage(Component.literal(loopName(get.get())));
        }).bounds(0, 0, 10, ROW_H).build();
        return new Field(button, () -> set.accept(fallback));
    }

    /**
     * The other setting. Two again: the bob left this button and became a
     * number of its own, because a monster can be drawn frame by frame AND
     * drift up and down while it happens. Those are two things a sprite does,
     * not two things it chooses between, and while the bob lived here every
     * bobbing monster had to be a still one.
     */
    private static MonsterSprites.Loop after(MonsterSprites.Loop loop)
    {
        return loop == MonsterSprites.Loop.LOOP
            ? MonsterSprites.Loop.PING_PONG : MonsterSprites.Loop.LOOP;
    }

    private static String loopName(MonsterSprites.Loop loop)
    {
        return switch(loop)
        {
            case PING_PONG -> "back/forth";
            // BOB is only still here so an old file reads; it is turned into a
            // loop with a bob the moment it is loaded and never written again.
            default -> "loop";
        };
    }

    /** A whole number, printing its own value so the label is the truth. */
    private class Count extends AbstractSliderButton implements Typed
    {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer set;

        Count(String label, int min, int max, IntSupplier get, IntConsumer set)
        {
            super(0, 0, 10, ROW_H, Component.empty(),
                (double)(get.getAsInt() - min) / Math.max(1, max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.set = set;
            updateMessage();
        }

        /**
         * One step per notch of the wheel.
         * <p>
         * A slider a couple of hundred pixels wide covering a thousand values
         * cannot be dragged to a particular one -- a single pixel is several
         * numbers, and the one you want is between them. The wheel gives the
         * exact number without giving up the drag for getting near it.
         */
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
            double scrollY)
        {
            if(scrollY == 0D)
            {
                return false;
            }
            // setValue is private in 1.21.1; this is its body.
            double moved = net.minecraft.util.Mth.clamp(value + Math.signum(scrollY) / Math.max(1, max - min), 0D, 1D);
            if(moved != value)
            {
                value = moved;
                applyValue();
            }
            updateMessage();
            return true;
        }

        private int value()
        {
            return min + (int)Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal(label + " " + value()));
        }

        @Override
        protected void applyValue()
        {
            set.accept(value());
        }

        @Override
        public String label()
        {
            return label;
        }

        @Override
        public String text()
        {
            return Integer.toString(value());
        }

        @Override
        public void accept(String typed)
        {
            try
            {
                // Clamped rather than refused: a number outside the range is a
                // legible intention -- "as far as this goes" -- and refusing it
                // silently would look like the box had not worked.
                set.accept(Math.clamp(Integer.parseInt(typed), min, max));
            }
            catch(NumberFormatException notANumber)
            {
                // The value stays as it was, and the rebuild puts it back on
                // screen. Nothing to report: the box showed what was typed.
            }
        }

        @Override
        public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
        {
            // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
            de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
            boolean doubled = false;
            if(event.button() == 1 && isMouseOver(event.x(), event.y()))
            {
                type(this);
                return true;
            }
            return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** A fraction, to two places. */
    private class Amount extends AbstractSliderButton implements Typed
    {
        private final String label;
        private final float min;
        private final float max;
        private final Consumer<Float> set;

        Amount(String label, float min, float max, Supplier<Float> get, Consumer<Float> set)
        {
            super(0, 0, 10, ROW_H, Component.empty(), (get.get() - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.set = set;
            updateMessage();
        }

        /** One step per notch, and a step is the last digit this prints. */
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
            double scrollY)
        {
            if(scrollY == 0D)
            {
                return false;
            }
            // setValue is private in 1.21.1; this is its body.
            double moved = net.minecraft.util.Mth.clamp(value + Math.signum(scrollY) * 0.01D / (max - min), 0D, 1D);
            if(moved != value)
            {
                value = moved;
                applyValue();
            }
            updateMessage();
            return true;
        }

        private float value()
        {
            return min + (float)value * (max - min);
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal(label + " " + String.format("%.2f", value())));
        }

        @Override
        protected void applyValue()
        {
            set.accept(value());
        }

        @Override
        public String label()
        {
            return label;
        }

        @Override
        public String text()
        {
            return String.format("%.2f", value());
        }

        @Override
        public void accept(String typed)
        {
            try
            {
                set.accept(Math.clamp(Float.parseFloat(typed), min, max));
            }
            catch(NumberFormatException notANumber)
            {
            }
        }

        @Override
        public boolean mouseClicked(double vanillaX, double vanillaY, int vanillaButton)
        {
            // 26.2 wraps GUI input in records; 1.21.1 passes loose values.
            de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event = new de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent(vanillaX, vanillaY, vanillaButton);
            boolean doubled = false;
            if(event.button() == 1 && isMouseOver(event.x(), event.y()))
            {
                type(this);
                return true;
            }
            return super.mouseClicked(vanillaX, vanillaY, vanillaButton);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }
}
