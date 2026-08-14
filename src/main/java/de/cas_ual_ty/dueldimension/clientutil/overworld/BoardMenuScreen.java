package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.BoardTarget;
import de.cas_ual_ty.dueldimension.clientutil.DuelActionController;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PromptOptions;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubWidgets;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The contextual menu for a card on the world board.
 * <p>
 * <b>A Screen, deliberately, and the only one.</b> §9 asks for two input states:
 * the mouse turning the camera while looking around the field, and the mouse
 * freed to point at a menu once one is open. Vanilla already switches between
 * exactly those two the moment a screen opens or closes -- it releases the
 * cursor, stops feeding the mouse to the camera, and puts both back on close.
 * Reimplementing that against {@code MouseHandler} would be fighting the game
 * for something it already does correctly. What must NOT be a screen is the
 * board itself, and it is not: the board is drawn in the world and the duellist
 * looks around it freely with nothing open.
 * <p>
 * The target is captured when the menu opens and held, so the card the menu is
 * about cannot change under it while the cursor moves -- which is §9's
 * "preserve the card that was being targeted".
 * <p>
 * Every row here came from the engine. This screen chooses none of them: it
 * lists what {@link PromptOptions} found and sends back an index.
 */
public class BoardMenuScreen extends Screen
{
    private final BoardTarget target;
    private final EnginePrompt prompt;
    private final List<Integer> options;

    public BoardMenuScreen(BoardTarget target, EnginePrompt prompt, List<Integer> options)
    {
        super(Component.literal(target == null || target.label() == null
            ? (prompt == null || prompt.title() == null || prompt.title().isEmpty() ? "Duel"
                : prompt.title())
            : target.label()));
        this.target = target;
        this.prompt = prompt;
        this.options = options;
    }

    /**
     * Opens the menu for whatever the act key was pressed at.
     * <p>
     * Aimed at a card, it offers that card's actions. Aimed at nothing, it
     * offers the options that are not about anything on the board -- ending a
     * phase, going to battle, declining a chain. Without that second half a
     * duellist could play cards from the board and then have no way to end
     * their turn without the screen, which would make the board a half-place
     * to duel from.
     */
    public static void openIfActionable(net.minecraft.client.Minecraft client)
    {
        BoardTarget target = ClientDuelTargeting.looking();
        EnginePrompt prompt = DuelClientState.prompt;
        List<Integer> options = PromptOptions.optionsFor(prompt, false, target);
        if(options.isEmpty())
        {
            target = null;
            options = PromptOptions.looseOptions(prompt, false);
        }
        if(options.isEmpty())
        {
            return;
        }
        client.setScreenAndShow(new BoardMenuScreen(target, prompt, options));
    }

    private static final int ROW_H = 22;
    private static final int WIDTH = 180;

    @Override
    protected void init()
    {
        int width = Math.min(WIDTH, this.width - 40);
        int x = (this.width - width) / 2;
        // Below the crosshair rather than over it: the card being acted on is
        // under the crosshair, and a menu covering it would hide the thing the
        // menu is about.
        int y = this.height / 2 + 16;

        for(int index : options)
        {
            EnginePrompt.Option option = prompt.options().get(index);
            addRenderableWidget(new HubWidgets.TextureButton(x, y, width, 20,
                Component.literal(option.label()), pressed -> choose(index)));
            y += ROW_H;
        }
        addRenderableWidget(new HubWidgets.TextureButton(x, y, width, 20,
            Component.literal("Close"), pressed -> onClose()));
    }

    private void choose(int index)
    {
        // Straight to the shared sender, which quotes the prompt's serial back.
        // An answer with a stale serial is dropped in silence and the duel
        // thread stays parked, so this is not a place to invent a shortcut.
        DuelActionController.answer(new int[] {index}, 0);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        // No dim at all: the board is the thing being acted on and it is behind
        // this menu. Every other screen in the mod dims because it replaces
        // what is behind it; this one annotates it.
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        String heading = title.getString();
        extractor.centeredText(font, heading, width / 2, height / 2 - 4, 0xFFF4D089);
    }

    /** The board stays visible, so the world keeps running behind this. */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void onClose()
    {
        minecraft.setScreenAndShow(null);
    }
}
