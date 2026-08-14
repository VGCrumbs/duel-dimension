package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.duel.orichalcos.SealSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    @Override
    protected void init()
    {
        int centreX = width / 2;

        addRenderableWidget(Button.builder(toggleLabel(), button ->
        {
            SealSettings.setEnabled(!SealSettings.enabled());
            button.setMessage(toggleLabel());
        }).bounds(centreX - 155, height / 2 - 44, 310, 20).build());

        addRenderableWidget(Button.builder(hoverLabel(), button ->
        {
            HoverPreviewSettings.setNeedsShift(!HoverPreviewSettings.needsShift());
            button.setMessage(hoverLabel());
        }).bounds(centreX - 155, height / 2 - 20, 310, 20).build());

        addRenderableWidget(Button.builder(hologramLabel(), button ->
        {
            HologramSettings.setEnabled(!HologramSettings.enabled());
            button.setMessage(hologramLabel());
        }).bounds(centreX - 155, height / 2 + 4, 310, 20).build());

        // The overworld field's dimensions get their own screen: there are four
        // of them and they are sliders, which do not belong in a list of
        // on/off rows.
        addRenderableWidget(Button.builder(
            Component.literal("Overworld duel field size..."),
            button -> minecraft.setScreenAndShow(
                new de.cas_ual_ty.dueldimension.clientutil.overworld
                    .OverworldFieldScreen(this)))
            .bounds(centreX - 155, height / 2 + 28, 310, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
            button -> onClose()).bounds(centreX - 100, height / 2 + 60, 200, 20).build());
    }

    private static Component hologramLabel()
    {
        return Component.literal("Monster holograms in duels: "
            + (HologramSettings.enabled() ? "ON" : "OFF"));
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
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY,
        float partialTick)
    {
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        poseStack.centeredText(font, title.getString(), width / 2, height / 2 - 60, 0xFFFFD700);
        poseStack.centeredText(font, "Lose a duel with the field spell up and the seal takes you.",
            width / 2, height / 2 + 68, 0xFFA0A0A0);
    }

    @Override
    public void onClose()
    {
        // setScreenAndShow, not setScreen: renamed in 26.2.
        minecraft.setScreenAndShow(parent);
    }
}
