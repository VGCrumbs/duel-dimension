package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites;
import de.cas_ual_ty.dueldimension.clientutil.overworld.SpriteLayer;
import de.cas_ual_ty.dueldimension.clientutil.overworld.Wings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * <b>Three tabs, and everything on a tab fits without scrolling.</b> Numbers
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
    private static final int ROW_H = 18;
    private static final int RESET_W = 14;
    private static final int GAP = 3;
    private static final int PAD = 7;
    private static final int HEADER = 46;

    private enum Tab
    {
        BODY("Body"),
        POSE("Pose"),
        WINGS("Wings");

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
    private float scale = 1F;

    private boolean pose;
    private int dfirst;

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
    private float wscale = Wings.DEFAULT_SCALE;

    private int row;
    /** Where the controls stopped, which is where the preview begins. */
    private int contentBottom;

    public BillboardEditorScreen(Screen parent, long code)
    {
        super(Component.literal("Billboard"));
        this.parent = parent;
        this.code = code;
        read();
    }

    /** Loads the card's current definition, or sensible starting values. */
    private void read()
    {
        MonsterSprites.Definition definition = MonsterSprites.of(code);
        if(definition == null)
        {
            return;
        }
        SpriteLayer body = definition.body();
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
        scale = definition.scale();

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
            anchor = wings.anchor();
            spacing = wings.spacing();
            wscale = wings.scale();
        }
    }

    /** Writes the definition back and saves it, on every change. */
    private void apply()
    {
        if(sheet.isBlank())
        {
            MonsterSprites.remove(code);
            MonsterSprites.save();
            return;
        }
        SpriteLayer body = new SpriteLayer(sheet, bx, by, bw, bh, bcolumns, brows, bfirst,
            bframes, bticks, bloop);
        SpriteLayer defence = pose
            ? new SpriteLayer(sheet, bx, by, bw, bh, bcolumns, brows, dfirst, 1, bticks,
                MonsterSprites.Loop.LOOP)
            : null;
        Wings wings = winged
            ? new Wings(new SpriteLayer(sheet, wx, wy, ww, wh, wcolumns, wrows, wfirst, wframes,
                wticks, wloop), anchor, spacing, wscale)
            : null;
        MonsterSprites.put(new MonsterSprites.Definition(code, body, defence, wings, scale));
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
        return PANEL_W - PAD * 2;
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
        field.widget().setX(x);
        field.widget().setY(rowY());
        field.widget().setWidth(control);
        addRenderableWidget(field.widget());

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

        int tabW = (full() - GAP * 2) / 3;
        int at = 0;
        for(Tab which : Tab.values())
        {
            Tab chosen = which;
            addRenderableWidget(Button.builder(
                Component.literal(which == tab ? "[" + which.label + "]" : which.label),
                pressed ->
                {
                    tab = chosen;
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
        }
        contentBottom = rowY();

        int sheets = height - 46;
        int third = (full() - GAP * 2) / 3;
        addRenderableWidget(Button.builder(Component.literal("Folder"), pressed ->
                de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSheets.open())
            .bounds(left(), sheets, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Reload"), pressed ->
            {
                de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSheets.reload();
                rebuildWidgets();
            }).bounds(left() + third + GAP, sheets, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Sheets " + de.cas_ual_ty
                .dueldimension.clientutil.overworld.MonsterSheets.names().size()), pressed ->
            {
            }).bounds(left() + (third + GAP) * 2, sheets, third, 18).build());

        int footer = height - 24;
        int half = (full() - GAP) / 2;
        addRenderableWidget(Button.builder(Component.literal("Remove"), pressed ->
        {
            MonsterSprites.remove(code);
            MonsterSprites.save();
            sheet = "";
            rebuildWidgets();
        }).bounds(left(), footer, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), pressed -> onClose())
            .bounds(left() + half + GAP, footer, half, 18).build());
    }

    private void body()
    {
        EditBox box = new EditBox(font, left(), rowY(), full() - RESET_W - GAP, ROW_H,
            Component.literal("Sheet"));
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
        wide(amount("Size", 0.1F, 4F, 1F, () -> scale, value -> scale = value));
        pair(count("Crop x", 0, 1024, 0, () -> bx, value -> bx = value),
            count("Crop y", 0, 1024, 0, () -> by, value -> by = value));
        pair(count("Crop w", 0, 1024, 0, () -> bw, value -> bw = value),
            count("Crop h", 0, 1024, 0, () -> bh, value -> bh = value));
    }

    private void poseTab()
    {
        wide(toggle("Defence pose", () -> pose, value -> pose = value, false));
        wide(count("Pose cell", 0, 63, bcolumns * brows - 1, () -> dfirst,
            value -> dfirst = value));
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
        wide(amount("Size", 0.05F, 2F, Wings.DEFAULT_SCALE, () -> wscale,
            value -> wscale = value));
        wide(amount("Height", 0F, 1.5F, Wings.DEFAULT_ANCHOR, () -> anchor,
            value -> anchor = value));
        wide(amount("Gap", -0.4F, 0.8F, Wings.DEFAULT_SPACING, () -> spacing,
            value -> spacing = value));
    }

    // ------------------------------------------------------------ drawing --

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        extractor.fill(panelX(), 0, width, height, 0xC0101014);
        extractor.fill(panelX(), 0, panelX() + 1, height, 0x60FFD700);

        Properties card = DdDatabase.PROPERTIES_LIST.get(code);
        String name = card == null ? Long.toString(code) : card.getName();
        extractor.text(font, font.plainSubstrByWidth(name, full()), left(), 7, 0xFFF4D089, true);
        extractor.text(font, MonsterSprites.has(code) ? "editing" : "no billboard yet",
            left(), 17, 0xFF9A9A9A, true);

        drawSlices(extractor);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    /** The sheet's own accent, and the wings' -- blue, as asked for. */
    private static final int BODY_LINE = 0xFFF4D089;
    private static final int BODY_FILL = 0x33F4D089;
    private static final int WING_LINE = 0xFF63C8FF;
    private static final int WING_FILL = 0x3363C8FF;
    private static final int POSE_LINE = 0xFF7CE38B;

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
            bframes, bticks, bloop);
        int[] size = MonsterSprites.sizeOf(body.texture());
        if(size[0] <= 0 || size[1] <= 0)
        {
            return;
        }

        int room = height - 52 - contentBottom - 6;
        if(room < 24)
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

        grid(extractor, body, size, x, y, drawW, drawH, BODY_LINE, BODY_FILL,
            pose ? dfirst : -1);
        if(winged)
        {
            grid(extractor, new SpriteLayer(sheet, wx, wy, ww, wh, wcolumns, wrows, wfirst,
                wframes, wticks, wloop), size, x, y, drawW, drawH, WING_LINE, WING_FILL, -1);
        }
    }

    /** One layer's region and cells, drawn over the sheet. */
    private void grid(GuiGraphicsExtractor extractor, SpriteLayer layer, int[] size, int x, int y,
        int drawW, int drawH, int line, int fill, int poseCell)
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
            int x0 = x + Math.round((layer.x() + column * cellW) * scaleX);
            int x1 = x + Math.round((layer.x() + (column + 1) * cellW) * scaleX);
            int y0 = y + Math.round((layer.y() + rowOf * cellH) * scaleY);
            int y1 = y + Math.round((layer.y() + (rowOf + 1) * cellH) * scaleY);

            boolean inRun = cell >= layer.first() && cell < layer.first() + layer.frames();
            if(inRun)
            {
                extractor.fill(x0, y0, x1, y1, fill);
            }
            int edge = cell == poseCell ? POSE_LINE : line;
            if(inRun || cell == poseCell)
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
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void onClose()
    {
        minecraft.gui.setScreen(parent);
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
            set.accept(get.get() == MonsterSprites.Loop.LOOP
                ? MonsterSprites.Loop.PING_PONG : MonsterSprites.Loop.LOOP);
            apply();
            pressed.setMessage(Component.literal(loopName(get.get())));
        }).bounds(0, 0, 10, ROW_H).build();
        return new Field(button, () -> set.accept(fallback));
    }

    private static String loopName(MonsterSprites.Loop loop)
    {
        return loop == MonsterSprites.Loop.LOOP ? "loop" : "back/forth";
    }

    /** A whole number, printing its own value so the label is the truth. */
    private class Count extends AbstractSliderButton
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
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** A fraction, to two places. */
    private class Amount extends AbstractSliderButton
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
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }
}
