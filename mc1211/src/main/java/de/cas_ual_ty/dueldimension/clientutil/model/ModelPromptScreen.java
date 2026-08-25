package de.cas_ual_ty.dueldimension.clientutil.model;

import net.minecraft.util.Util;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The offer: monster models are available, would you like them.
 * <p>
 * Shown once, on a fresh install with an empty models folder. Everything works
 * without them — a monster with no model is drawn as its sprite — so this is an
 * offer rather than a warning, and it says what the models ARE rather than
 * treating their absence as a fault.
 * <p>
 * <b>Agreeing is the whole of it.</b> The install downloads, checks what it got
 * against a known digest, unpacks, and gives each model to the card of the same
 * name — so the answer to "yes" is models in use rather than a folder to find.
 * <p>
 * The second button is the way out when that cannot work. Drive enforces a
 * per-file quota, and a link that has been busy answers with a page instead of
 * the archive; the checksum catches that and says so, but a duellist still needs
 * somewhere to go. It hands the link to the browser through Minecraft's own
 * {@link ConfirmLinkScreen}, which shows the destination before the game is
 * left.
 */
public class ModelPromptScreen extends Screen
{
    private static final int ROW_H = 20;
    private static final int GAP = 4;
    private static final int WIDE = 280;

    private final Screen parent;
    private int row;
    private int top;

    public ModelPromptScreen(Screen parent)
    {
        super(Component.literal("Monster models"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        // Derived from the count, so a row added later moves what follows
        // rather than landing on top of it.
        int rows = 4;
        top = height / 2 - (rows * (ROW_H + GAP)) / 2;
        row = 0;

        // Yes, and nothing else to do. The install fetches, checks, unpacks and
        // hands the models to their cards; a duellist who agreed should not then
        // have to find a folder.
        addRenderableWidget(Button.builder(Component.literal("Download and install (273 MB)"),
            pressed ->
            {
                ModelPrompt.notNow();
                ModelInstall.start();
                minecraft.setScreenAndShow(new ModelInstallScreen(parent));
            }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        // The way out when the automatic one cannot work -- Drive enforces a
        // per-file quota, and a link that has been busy answers with a page
        // rather than the archive. Vanilla's own confirmation shows where it
        // goes before the game is left.
        addRenderableWidget(Button.builder(Component.literal("Open the download page instead"),
            pressed -> minecraft.setScreenAndShow(new ConfirmLinkScreen(opened ->
            {
                if(opened)
                {
                    Util.getPlatform().openUri(ModelPrompt.URL);
                }
                ModelPrompt.notNow();
                minecraft.setScreenAndShow(parent);
            }, ModelPrompt.URL, true)))
            .bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Not now"), pressed ->
        {
            ModelPrompt.notNow();
            onClose();
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Don't ask again"), pressed ->
        {
            ModelPrompt.never();
            onClose();
        }).bounds(width / 2 - WIDE / 2, rowY(), WIDE, ROW_H).build());
    }

    private int rowY()
    {
        return top + row++ * (ROW_H + GAP);
    }

    /**
     * Retained mode: {@code render} is {@code extractRenderState} here.
     * <p>
     * The dim is a gradient rather than {@code extractBackground}, which BLURS —
     * and the blur may only run once a frame, so a screen opening over one that
     * already asked for it takes the client down. Every screen in this mod
     * settled on this for that reason.
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
        extractor.centeredText(font, title.getString(), width / 2, top - 40, 0xFFFFD700);
        extractor.centeredText(font,
            "Monsters can stand on their cards as the 3D models from the games.",
            width / 2, top - 26, 0xFFD0D0D0);
        extractor.centeredText(font,
            "Without them they are drawn as sprites, which works just as well.",
            width / 2, top - 15, 0xFFA0A0A0);
        extractor.centeredText(font, "683 models, matched to their cards automatically.",
            width / 2, top + 4 * (ROW_H + GAP) + 8, 0xFFA0A0A0);
    }

    /**
     * Dismissing counts as "not now".
     * <p>
     * Otherwise escape would leave the question unanswered while returning to
     * the title screen -- whose init raises the notice again, which opens this
     * again. Closing a thing should not be the one way to make it come back.
     */
    @Override
    public void onClose()
    {
        ModelPrompt.notNow();
        // setScreenAndShow, not setScreen: renamed in 26.2.
        minecraft.setScreenAndShow(parent);
    }
}
