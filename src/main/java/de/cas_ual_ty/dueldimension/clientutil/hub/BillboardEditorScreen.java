package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.clientutil.overworld.MonsterSprites;
import de.cas_ual_ty.dueldimension.clientutil.overworld.SpriteLayer;
import de.cas_ual_ty.dueldimension.clientutil.overworld.Wings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Building a monster's billboard while looking at it.
 * <p>
 * A panel down one side of the screen and nothing else: no dim, no full-screen
 * background, nothing covering the middle. That is the entire design. Every
 * number here -- how far out a wing sits, how tall the body stands, which cells
 * are frames -- is a number you cannot reason about and can only look at, so
 * the screen's first duty is to stay out of the way of the pedestal it is
 * editing. Every change is applied and saved as it is made, so the monster on
 * the block is always what the panel currently says.
 * <p>
 * The card is whatever the pedestal is showing. That is what makes this an
 * editor for ANY card rather than for a list of them: put a card on the block,
 * open this, and you are editing that card's monster.
 */
public class BillboardEditorScreen extends Screen
{
    private final Screen parent;
    private final long code;

    private static final int PANEL_W = 190;
    private static final int ROW_H = 20;
    private static final int GAP = 2;
    private static final int PAD = 6;
    private static final int HEADER = 30;
    private static final int FOOTER = 26;

    // The definition being built, held in pieces because a record cannot be
    // edited a field at a time and this screen edits nothing else.
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

    private float scale = 1F;

    private final List<net.minecraft.client.gui.components.AbstractWidget> rows = new ArrayList<>();
    private int scroll;
    private EditBox sheetBox;

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

    /**
     * Writes the definition back and saves it, on every change.
     * <p>
     * There is no OK button and no confirmation because there is nothing to
     * confirm: the pedestal behind this panel is showing the result already,
     * and a change you can see is a change you have already approved or
     * already undone.
     */
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
            ? new SpriteLayer(sheet, bx, by, bw, bh, bcolumns, brows, dfirst, 1,
                bticks, MonsterSprites.Loop.LOOP)
            : null;
        Wings wings = winged
            ? new Wings(new SpriteLayer(sheet, wx, wy, ww, wh, wcolumns, wrows, wfirst, wframes,
                wticks, wloop), anchor, spacing, wscale)
            : null;
        MonsterSprites.put(new MonsterSprites.Definition(code, body, defence, wings, scale));
        MonsterSprites.save();
    }

    private int panelX()
    {
        return width - PANEL_W - 4;
    }

    @Override
    protected void init()
    {
        rows.clear();
        int x = panelX() + PAD;
        int w = PANEL_W - PAD * 2;

        sheetBox = new EditBox(font, x, 0, w, 16, Component.literal("Sheet"));
        sheetBox.setMaxLength(128);
        sheetBox.setValue(sheet);
        // Applied on every keystroke, but the box is built ONCE and never
        // rebuilt: a responder that rebuilds the widgets drops focus mid-word.
        sheetBox.setResponder(text ->
        {
            sheet = text.trim();
            apply();
        });
        add(sheetBox);

        add(toggle(x, w, "Defence pose", () -> pose, value ->
        {
            pose = value;
            apply();
        }));
        add(new Count(x, w, "  pose cell", 0, 63, () -> dfirst, value ->
        {
            dfirst = value;
            apply();
        }));

        add(new Count(x, w, "Columns", 1, 16, () -> bcolumns, value ->
        {
            bcolumns = value;
            apply();
        }));
        add(new Count(x, w, "Rows", 1, 16, () -> brows, value ->
        {
            brows = value;
            apply();
        }));
        add(new Count(x, w, "First cell", 0, 63, () -> bfirst, value ->
        {
            bfirst = value;
            apply();
        }));
        add(new Count(x, w, "Frames", 1, 64, () -> bframes, value ->
        {
            bframes = value;
            apply();
        }));
        add(new Count(x, w, "Ticks per frame", 1, 20, () -> bticks, value ->
        {
            bticks = value;
            apply();
        }));
        add(loop(x, w, "Animation", () -> bloop, value ->
        {
            bloop = value;
            apply();
        }));
        add(new Amount(x, w, "Size", 0.1F, 4F, () -> scale, value ->
        {
            scale = value;
            apply();
        }));

        add(new Count(x, w, "Region x", 0, 1024, () -> bx, value ->
        {
            bx = value;
            apply();
        }));
        add(new Count(x, w, "Region y", 0, 1024, () -> by, value ->
        {
            by = value;
            apply();
        }));
        add(new Count(x, w, "Region w", 0, 1024, () -> bw, value ->
        {
            bw = value;
            apply();
        }));
        add(new Count(x, w, "Region h", 0, 1024, () -> bh, value ->
        {
            bh = value;
            apply();
        }));

        add(toggle(x, w, "Wings", () -> winged, value ->
        {
            winged = value;
            apply();
        }));
        add(new Count(x, w, "  wing x", 0, 1024, () -> wx, value ->
        {
            wx = value;
            apply();
        }));
        add(new Count(x, w, "  wing y", 0, 1024, () -> wy, value ->
        {
            wy = value;
            apply();
        }));
        add(new Count(x, w, "  wing w", 0, 1024, () -> ww, value ->
        {
            ww = value;
            apply();
        }));
        add(new Count(x, w, "  wing h", 0, 1024, () -> wh, value ->
        {
            wh = value;
            apply();
        }));
        add(new Count(x, w, "  wing columns", 1, 16, () -> wcolumns, value ->
        {
            wcolumns = value;
            apply();
        }));
        add(new Count(x, w, "  wing rows", 1, 16, () -> wrows, value ->
        {
            wrows = value;
            apply();
        }));
        add(new Count(x, w, "  wing first", 0, 63, () -> wfirst, value ->
        {
            wfirst = value;
            apply();
        }));
        add(new Count(x, w, "  wing frames", 1, 64, () -> wframes, value ->
        {
            wframes = value;
            apply();
        }));
        add(new Count(x, w, "  wing ticks", 1, 20, () -> wticks, value ->
        {
            wticks = value;
            apply();
        }));
        add(loop(x, w, "  wing animation", () -> wloop, value ->
        {
            wloop = value;
            apply();
        }));
        add(new Amount(x, w, "  wing height", 0.05F, 2F, () -> wscale, value ->
        {
            wscale = value;
            apply();
        }));
        add(new Amount(x, w, "  wing anchor", 0F, 1.5F, () -> anchor, value ->
        {
            anchor = value;
            apply();
        }));
        add(new Amount(x, w, "  wing spacing", 0F, 1.5F, () -> spacing, value ->
        {
            spacing = value;
            apply();
        }));

        addRenderableWidget(Button.builder(Component.literal("Remove"), pressed ->
        {
            MonsterSprites.remove(code);
            MonsterSprites.save();
            sheet = "";
            sheetBox.setValue("");
        }).bounds(panelX() + PAD, height - FOOTER, (PANEL_W - PAD * 2 - GAP) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), pressed -> onClose())
            .bounds(panelX() + PAD + (PANEL_W - PAD * 2 - GAP) / 2 + GAP, height - FOOTER,
                (PANEL_W - PAD * 2 - GAP) / 2, 18).build());

        place();
    }

    private void add(net.minecraft.client.gui.components.AbstractWidget widget)
    {
        rows.add(widget);
        addRenderableWidget(widget);
    }

    private int visibleRows()
    {
        return Math.max(1, (height - HEADER - FOOTER - 8) / (ROW_H + GAP));
    }

    private int maxScroll()
    {
        return Math.max(0, rows.size() - visibleRows());
    }

    /**
     * Moves the rows for the current scroll, and hides the ones off the ends.
     * <p>
     * Hidden rather than clipped: a scissor does not clip a widget's hit box,
     * so a slider scrolled out of sight would still take the click of whatever
     * is drawn where it used to be.
     */
    private void place()
    {
        scroll = Math.clamp(scroll, 0, maxScroll());
        int top = HEADER;
        for(int row = 0; row < rows.size(); row++)
        {
            var widget = rows.get(row);
            int at = row - scroll;
            boolean shown = at >= 0 && at < visibleRows();
            widget.visible = shown;
            widget.active = shown;
            widget.setY(top + at * (ROW_H + GAP));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if(mouseX >= panelX())
        {
            scroll = Math.clamp(scroll - (int)Math.signum(scrollY), 0, maxScroll());
            place();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        // The panel only. Nothing over the middle of the screen, because the
        // middle of the screen is the monster being edited.
        extractor.fill(panelX(), 0, width, height, 0xC0101014);
        extractor.fill(panelX(), 0, panelX() + 1, height, 0x60FFD700);

        Properties card = DdDatabase.PROPERTIES_LIST.get(code);
        String name = card == null ? Long.toString(code) : card.getName();
        extractor.text(font, font.plainSubstrByWidth(name, PANEL_W - PAD * 2),
            panelX() + PAD, 6, 0xFFF4D089, true);
        extractor.text(font, MonsterSprites.has(code) ? "editing" : "no billboard yet",
            panelX() + PAD, 17, 0xFF9A9A9A, true);

        if(maxScroll() > 0)
        {
            String more = (scroll + 1) + " / " + (maxScroll() + 1);
            extractor.text(font, more, width - PAD - font.width(more), 17, 0xFF7A8090, true);
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
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

    /** A whole number, shown on its own slider so the label is the truth. */
    private class Count extends AbstractSliderButton
    {
        private final String label;
        private final int min;
        private final int max;
        private final java.util.function.IntConsumer set;

        Count(int x, int w, String label, int min, int max, IntSupplier get,
            java.util.function.IntConsumer set)
        {
            super(x, 0, w, ROW_H, Component.empty(),
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
            setMessage(Component.literal(label + ": " + value()));
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
        private final java.util.function.Consumer<Float> set;

        Amount(int x, int w, String label, float min, float max, Supplier<Float> get,
            java.util.function.Consumer<Float> set)
        {
            super(x, 0, w, ROW_H, Component.empty(), (get.get() - min) / (max - min));
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
            setMessage(Component.literal(label + ": " + String.format("%.2f", value())));
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

    /**
     * On or off, as a button whose label states which.
     * <p>
     * A button rather than a checkbox because this mod has no checkbox --
     * every yes/no in it is a button that says what it currently is, and one
     * that broke the pattern would be the odd one out rather than an
     * improvement. The label is rewritten in place rather than the widget
     * rebuilt, because rebuilding widgets mid-edit drops the text field's
     * focus.
     */
    private Button toggle(int x, int w, String label,
        java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set)
    {
        return Button.builder(Component.literal(label + ": " + onOff(get.getAsBoolean())),
            button ->
            {
                set.accept(!get.getAsBoolean());
                button.setMessage(Component.literal(label + ": " + onOff(get.getAsBoolean())));
            }).bounds(x, 0, w, ROW_H).build();
    }

    private static String onOff(boolean value)
    {
        return value ? "ON" : "OFF";
    }

    /** The animation's direction, as a button that names it. */
    private Button loop(int x, int w, String label, Supplier<MonsterSprites.Loop> get,
        java.util.function.Consumer<MonsterSprites.Loop> set)
    {
        return Button.builder(Component.literal(label + ": " + loopName(get.get())), button ->
        {
            set.accept(get.get() == MonsterSprites.Loop.LOOP
                ? MonsterSprites.Loop.PING_PONG : MonsterSprites.Loop.LOOP);
            button.setMessage(Component.literal(label + ": " + loopName(get.get())));
        }).bounds(x, 0, w, ROW_H).build();
    }

    private static String loopName(MonsterSprites.Loop loop)
    {
        return loop == MonsterSprites.Loop.LOOP ? "loop" : "back and forth";
    }
}
