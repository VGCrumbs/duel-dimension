package de.cas_ual_ty.dueldimension.clientutil.model;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The install, while it happens.
 * <p>
 * A 273MB download is minutes rather than seconds, so the one thing this screen
 * owes a duellist is an honest answer to "is it still going" -- a bar that moves,
 * a count of megabytes, and a way out that does not leave a half-file behind.
 * <p>
 * The bar is drawn rather than a texture: this is a utility screen like the
 * settings, not part of the duel presentation the art rule is about.
 */
public class ModelInstallScreen extends Screen
{
    private static final int BAR_W = 300;
    private static final int BAR_H = 10;

    private final Screen parent;

    public ModelInstallScreen(Screen parent)
    {
        super(Component.literal("Installing monster models"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        addRenderableWidget(Button.builder(Component.literal("Cancel"), pressed ->
        {
            ModelInstall.cancel();
            onClose();
        }).bounds(width / 2 - 100, height / 2 + 30, 200, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, pressed -> onClose())
            .bounds(width / 2 - 100, height / 2 + 54, 200, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(extractor.vanilla(), mouseX, mouseY, partialTick);

        int centre = width / 2;
        int barY = height / 2 - 6;
        extractor.centeredText(font, title.getString(), centre, height / 2 - 46, 0xFFFFD700);
        extractor.centeredText(font, stageLine(), centre, height / 2 - 30, 0xFFD0D0D0);

        // The trough, then however much of it is filled.
        extractor.fill(centre - BAR_W / 2 - 1, barY - 1, centre + BAR_W / 2 + 1, barY + BAR_H + 1,
            0xFF202028);
        float progress = ModelInstall.progress();
        if(progress > 0F)
        {
            int filled = Math.round(BAR_W * Math.min(1F, progress));
            extractor.fill(centre - BAR_W / 2, barY, centre - BAR_W / 2 + filled, barY + BAR_H,
                0xFF7CE38B);
        }

        extractor.centeredText(font, detailLine(), centre, barY + BAR_H + 6, 0xFFA0A0A0);
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


    private static String stageLine()
    {
        return switch(ModelInstall.stage())
        {
            case DOWNLOADING -> "Downloading...";
            case CHECKING -> "Checking the download...";
            case UNPACKING -> "Unpacking...";
            case ASSIGNING -> "Matching models to cards...";
            case DONE -> "Done. They are in use now.";
            case FAILED -> "It did not work.";
            default -> "";
        };
    }

    private static String detailLine()
    {
        ModelInstall.Stage stage = ModelInstall.stage();
        if(stage == ModelInstall.Stage.DONE || stage == ModelInstall.Stage.FAILED)
        {
            return ModelInstall.message();
        }
        if(stage == ModelInstall.Stage.DOWNLOADING)
        {
            return megabytes(ModelInstall.bytesDone()) + " of "
                + megabytes(ModelInstall.bytesTotal()) + " MB";
        }
        return "";
    }

    private static String megabytes(long bytes)
    {
        return String.format("%.0f", bytes / (1024.0 * 1024.0));
    }

    /**
     * Closing does not stop the install.
     * <p>
     * It runs on its own thread and finishes whether or not anybody is watching,
     * which is what a duellist who wandered off to the title screen would expect.
     * Cancelling is the button that stops it.
     */
    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }
}
