package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.duel.overworld.FieldSpec;
import de.cas_ual_ty.dueldimension.duel.overworld.OverworldSettings;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The size of the duel field built in the world, adjustable without a rebuild.
 * <p>
 * Sizing something drawn in world space is a thing you settle by standing next
 * to it and looking, and a recompile between every look makes that a slow loop.
 * Every slider here writes straight through to {@link OverworldSettings}, and
 * the next duel sited picks it up.
 * <p>
 * The board's size is offered as a share of the ground rather than as blocks
 * per field unit, because that is the number that cannot be set wrongly: a
 * board is never allowed to outgrow the ground that gets validated for it, and
 * a percentage of the fitting size makes that impossible to ask for rather than
 * merely refused afterwards.
 */
public class OverworldFieldScreen extends Screen
{
    private final Screen parent;

    public OverworldFieldScreen(Screen parent)
    {
        super(Component.literal("Overworld Duel Field"));
        this.parent = parent;
    }

    /** Widest a span may be set to from here; the record allows more from a file. */
    private static final int SPAN_MIN = 5;
    private static final int SPAN_MAX = 31;
    private static final int CLEARANCE_MIN = 1;
    private static final int CLEARANCE_MAX = 8;
    private static final float FILL_MIN = 0.25F;

    private static final int ROW_H = 24;
    private static final int WIDGET_W = 310;

    private int rowWidth()
    {
        // Measured against the window rather than assumed: this screen is
        // reachable from the title screen at any size, and an authored width
        // used as an absolute is how six other screens in this mod ended up
        // cropped at low resolutions.
        return Math.min(WIDGET_W, width - 40);
    }

    @Override
    protected void init()
    {
        int x = (width - rowWidth()) / 2;
        int y = Math.max(40, height / 2 - 90);

        addRenderableWidget(new SpanSlider(x, y, rowWidth(), 20, true));
        y += ROW_H;
        addRenderableWidget(new SpanSlider(x, y, rowWidth(), 20, false));
        y += ROW_H;
        addRenderableWidget(new FillSlider(x, y, rowWidth(), 20));
        y += ROW_H;
        addRenderableWidget(new ClearanceSlider(x, y, rowWidth(), 20));
        y += ROW_H;
        addRenderableWidget(new LiftSlider(x, y, rowWidth(), 20));
        y += ROW_H + 8;

        addRenderableWidget(Button.builder(Component.literal("Reset to 9 x 9"), button ->
        {
            OverworldSettings.reset();
            rebuildWidgets();
        }).bounds(x, y, rowWidth(), 20).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(x + (rowWidth() - 200) / 2, y, Math.min(200, rowWidth()), 20).build());
    }

    /** Replaces the spec, keeping everything this screen does not offer. */
    private static void apply(int areaWidth, int areaDepth, int clearance, float fill)
    {
        FieldSpec now = OverworldSettings.spec();
        float fitting = FieldSpec.fittingScale(odd(areaWidth), odd(areaDepth));
        OverworldSettings.set(new FieldSpec(areaWidth, areaDepth, clearance, fitting * fill,
            now.matLift(), now.lateralTolerance(), now.elevationTolerance(), now.maxSeparation(),
            now.searchRadius(), now.verticalSearch()));
    }

    private static int odd(int value)
    {
        return (value & 1) == 1 ? value : value + 1;
    }

    /** How much of the fitting size the board is currently drawn at. */
    private static float fill()
    {
        FieldSpec spec = OverworldSettings.spec();
        float fitting = FieldSpec.fittingScale(spec.areaWidth(), spec.areaDepth());
        return fitting <= 0F ? 1F : Mth.clamp(spec.matScale() / fitting, FILL_MIN, 1F);
    }

    /** The area across or between the duellists, in blocks; always odd. */
    private class SpanSlider extends AbstractSliderButton
    {
        private final boolean across;

        SpanSlider(int x, int y, int w, int h, boolean across)
        {
            super(x, y, w, h, Component.empty(), fraction(across));
            this.across = across;
            updateMessage();
        }

        private static double fraction(boolean across)
        {
            FieldSpec spec = OverworldSettings.spec();
            int span = across ? spec.areaWidth() : spec.areaDepth();
            return (double)(span - SPAN_MIN) / (SPAN_MAX - SPAN_MIN);
        }

        private int span()
        {
            return odd(SPAN_MIN + (int)Math.round(value * (SPAN_MAX - SPAN_MIN)));
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal((across ? "Field width:  " : "Field depth:  ")
                + span() + " blocks"));
        }

        @Override
        protected void applyValue()
        {
            FieldSpec spec = OverworldSettings.spec();
            apply(across ? span() : spec.areaWidth(), across ? spec.areaDepth() : span(),
                spec.clearance(), fill());
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** How much of the available ground the board itself covers. */
    private class FillSlider extends AbstractSliderButton
    {
        FillSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(), (fill() - FILL_MIN) / (1F - FILL_MIN));
            updateMessage();
        }

        private float chosen()
        {
            return FILL_MIN + (float)value * (1F - FILL_MIN);
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal("Board size:  "
                + Math.round(chosen() * 100F) + "% of the field"));
        }

        @Override
        protected void applyValue()
        {
            FieldSpec spec = OverworldSettings.spec();
            apply(spec.areaWidth(), spec.areaDepth(), spec.clearance(), chosen());
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** How much air the field needs above it before it counts as obstructed. */
    private class ClearanceSlider extends AbstractSliderButton
    {
        ClearanceSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(),
                (double)(OverworldSettings.spec().clearance() - CLEARANCE_MIN)
                    / (CLEARANCE_MAX - CLEARANCE_MIN));
            updateMessage();
        }

        private int clearance()
        {
            return CLEARANCE_MIN + (int)Math.round(value * (CLEARANCE_MAX - CLEARANCE_MIN));
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal("Headroom needed:  " + clearance() + " blocks"));
        }

        @Override
        protected void applyValue()
        {
            FieldSpec spec = OverworldSettings.spec();
            apply(spec.areaWidth(), spec.areaDepth(), clearance(), fill());
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** How high the board floats above the ground it was validated on. */
    private class LiftSlider extends AbstractSliderButton
    {
        LiftSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(),
                (OverworldSettings.spec().matLift() + LIFT_RANGE) / (LIFT_RANGE * 2F));
            updateMessage();
        }

        private float lift()
        {
            // Snapped to halves here as well as in the record, so the label a
            // player reads while dragging is the value they will get.
            return Math.round((-LIFT_RANGE + (float)value * LIFT_RANGE * 2F) * 2F) / 2F;
        }

        @Override
        protected void updateMessage()
        {
            float lift = lift();
            setMessage(Component.literal("Board height:  "
                + (lift == 0F ? "on the ground"
                    : (lift > 0F ? "+" : "") + lift + (Math.abs(lift) == 1F ? " block" : " blocks"))));
        }

        @Override
        protected void applyValue()
        {
            OverworldSettings.set(OverworldSettings.spec().withLift(lift()));
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** How far the board may be nudged either way from this screen. */
    private static final float LIFT_RANGE = 3F;

    /**
     * The dim is a gradient rather than {@code extractBackground}, which BLURS
     * and may only run once a frame -- a screen opening over one that already
     * asked for it took the client down. Every screen in this mod settled here.
     */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(extractor.vanilla(), mouseX, mouseY, partialTick);

        FieldSpec spec = OverworldSettings.spec();
        int top = Math.max(40, height / 2 - 90);
        extractor.centeredText(font, title.getString(), width / 2, top - 28, 0xFFFFD700);

        // The numbers that actually result, because a percentage of a fitting
        // size is not a length and the person adjusting this wants a length.
        String board = String.format("Board: %.1f x %.1f blocks, cards %.2f wide",
            spec.matWidth(), spec.matDepth(), 0.7F * spec.matScale());
        extractor.centeredText(font, board, width / 2, top - 16, 0xFFC2C9D6);

        String duellists = "Duellists stand " + spec.separation() + " blocks apart";
        extractor.centeredText(font, duellists, width / 2,
            top + 4 * ROW_H + 34 + ROW_H + 26, 0xFFA0A0A0);
        extractor.centeredText(font, "Applies to duels this game hosts. Takes effect next duel.",
            width / 2, top + 4 * ROW_H + 34 + ROW_H + 38, 0xFF7A8394);
    }

    /**
     * No background from vanilla, because this screen draws before
     * {@code super.render} and vanilla draws the background from inside it.
     * <p>
     * In a level that background is the BLUR and nothing else -- the panorama
     * and {@code renderMenuBackground} are both gated on there being no level --
     * so leaving it in place blurs everything this screen has already put down,
     * which is the whole interface. 26.2 refuses it too, in the same words:
     * <blockquote>fillGradient, not extractBackground: that one blurs.</blockquote>
     * The dim, where this screen wants one, is its own and goes down first.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }


    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }
}
