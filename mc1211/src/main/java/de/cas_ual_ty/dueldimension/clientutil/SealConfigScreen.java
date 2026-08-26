package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.duel.orichalcos.SealSettings;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings, as reached from ModMenu.
 * <p>
 * Deliberately its own screen rather than the in-game hub's settings tab. The
 * hub is built for a player who is in a world — its play-mat picker sends a
 * packet when it changes — and ModMenu's button is reachable from the title
 * screen, where there is no connection to send one on. Pointing ModMenu at the
 * hub would turn a config click on the title screen into a crash.
 */
public class SealConfigScreen extends Screen
{
    private final Screen parent;

    public SealConfigScreen(Screen parent)
    {
        super(Component.literal("Duel Dimension"));
        this.parent = parent;
    }

    private static final int ROW_H = 20;
    private static final int GAP = 4;
    private static final int WIDE = 310;

    /** Where the next row goes, counted rather than written down. */
    private int row;
    private int top;

    @Override
    protected void init()
    {
        // Laid out from a count, not from a column of hand-written offsets.
        // Those offsets had grown to disagree with each other: the caption sat
        // at height/2 + 68 while Done spanned +60 to +80, so the line was drawn
        // straight through the button. Every row below is derived, so adding one
        // moves everything that follows instead of landing on top of it.
        int rows = 6;
        int block = rows * (ROW_H + GAP) + GAP + ROW_H;
        top = height / 2 - block / 2;
        row = 0;

        addRenderableWidget(Button.builder(toggleLabel(), button ->
        {
            SealSettings.setEnabled(!SealSettings.enabled());
            button.setMessage(toggleLabel());
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        addRenderableWidget(Button.builder(hoverLabel(), button ->
        {
            HoverPreviewSettings.setNeedsShift(!HoverPreviewSettings.needsShift());
            button.setMessage(hoverLabel());
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        // Cycles rather than toggles: three positions, and the useful one is the
        // middle. See HologramSettings for why the board is not symmetrical.
        addRenderableWidget(Button.builder(hologramLabel(), button ->
        {
            HologramSettings.setMode(HologramSettings.mode().next());
            button.setMessage(hologramLabel());
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        // Off leaves the idle, which is the animation every monster has.
        addRenderableWidget(Button.builder(animationLabel(), button ->
        {
            de.cas_ual_ty.dueldimension.clientutil.model.AnimationSettings.setExtras(
                !de.cas_ual_ty.dueldimension.clientutil.model.AnimationSettings.extras());
            button.setMessage(animationLabel());
            // The speed row below is only meaningful while these are on.
            rebuildWidgets();
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        // Only worth reaching for when the animations are on, and disabled
        // rather than hidden so its absence is a fact about the row above it.
        Button speed = Button.builder(speedLabel(), button ->
        {
            de.cas_ual_ty.dueldimension.clientutil.model.AnimationSettings.cycleSpeed();
            button.setMessage(speedLabel());
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build();
        speed.active = de.cas_ual_ty.dueldimension.clientutil.model.AnimationSettings.extras();
        addRenderableWidget(speed);

        // The overworld field's dimensions get their own screen: there are four
        // of them and they are sliders, which do not belong in a list of
        // on/off rows.
        addRenderableWidget(Button.builder(
            Component.literal("Overworld duel field size..."),
            button -> minecraft.setScreen(
                new de.cas_ual_ty.dueldimension.clientutil.overworld
                    .OverworldFieldScreen(this)))
            .bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
            button -> onClose()).bounds(width / 2 - 100, doneY(), 200, ROW_H).build());
    }

    private int rowY()
    {
        return top + row++ * (ROW_H + GAP);
    }

    /** A gap wider than the one between rows, so Done reads as separate. */
    private int doneY()
    {
        return top + row * (ROW_H + GAP) + GAP;
    }

    private static Component speedLabel()
    {
        return Component.literal("Attack animation speed: "
            + de.cas_ual_ty.dueldimension.clientutil.model.AnimationSettings.speedLabel());
    }

    private static Component animationLabel()
    {
        return Component.literal("Monster attack and hit animations: "
            + (de.cas_ual_ty.dueldimension.clientutil.model.AnimationSettings.extras()
                ? "ON" : "IDLE ONLY"));
    }

    private static Component hologramLabel()
    {
        return Component.literal("Monster holograms in duels: "
            + HologramSettings.mode().label());
    }

    private static Component hoverLabel()
    {
        return Component.literal("Deck builder card preview: "
            + (HoverPreviewSettings.needsShift() ? "HOLD SHIFT" : "ALWAYS"));
    }

    private static Component toggleLabel()
    {
        return Component.literal("Seal of Orichalcos takes the loser's soul: "
            + (SealSettings.enabled() ? "ON" : "OFF"));
    }

    /**
     * Retained mode: {@code render} is {@code extractRenderState} here.
     * <p>
     * The dim is drawn with {@code fillGradient} rather than by calling
     * {@code extractBackground}, which BLURS — and the blur may only run once a
     * frame, so a screen opening over one that already asked for it takes the
     * client down. Six screens in this mod settled on this same gradient for
     * that reason.
     */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
        // Measured off the same layout the buttons use, so neither can drift
        // into the other. The font is 9 tall, hence the 14 and the 10.
        poseStack.centeredText(font, title.getString(), width / 2, top - 14, 0xFFFFD700);
        poseStack.centeredText(font, "Lose a duel with the field spell up and the seal takes you.",
            width / 2, doneY() + ROW_H + 10, 0xFFA0A0A0);
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
        // setScreenAndShow, not setScreen: renamed in 26.2.
        minecraft.setScreen(parent);
    }
}
