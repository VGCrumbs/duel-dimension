package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.duel.match.LobbyMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The opening toss, and the choice it hands its winner.
 * <p>
 * Both players see this screen: the winner is asked, the loser is told who is
 * being asked. A loser left staring at an unexplained wait would read as the
 * game having hung, which is the whole reason the result is sent to both.
 * <p>
 * Nothing here decides anything. The flip already happened on the server and
 * this screen only reports it; the buttons send an answer that the server
 * believes only from the player who actually won.
 */
public class CoinTossScreen extends Screen
{
    private final LobbyMessages.CoinToss toss;
    /** Set once an answer is away, so the choice cannot be sent twice. */
    private boolean answered;

    public CoinTossScreen(LobbyMessages.CoinToss toss)
    {
        super(Component.literal("Coin Toss"));
        this.toss = toss;
    }

    private int panelW()
    {
        return Math.min(240, width - 40);
    }

    private int panelH()
    {
        return 96;
    }

    private int panelX()
    {
        return (width - panelW()) / 2;
    }

    private int panelY()
    {
        return (height - panelH()) / 2;
    }

    @Override
    protected void init()
    {
        if(answered)
        {
            return; // already sent; nothing left to press
        }
        int w = (panelW() - 36) / 2;
        int x = panelX() + 12;
        int y = panelY() + panelH() - 30;
        addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Go First"), pressed -> answer(true)));
        addRenderableWidget(new HubWidgets.TextureButton(x + w + 12, y, w, 18,
            Component.literal("Go Second"), pressed -> answer(false)));
    }

    private void answer(boolean goFirst)
    {
        if(answered)
        {
            return;
        }
        answered = true;
        ClientPlayNetworking.send(new LobbyMessages.TurnChoice(goFirst));
        // And gives the world back, exactly as the duel-type menu does. It used
        // to stay up on the reasoning that the duel screen would open itself
        // when the first board arrived -- which is true of a duel played on the
        // screen and false of one played on a board, where that opener is
        // deliberately suppressed. On a board there was nothing to replace it,
        // and it is modal: the very next thing an overworld duel asks is that
        // both players WALK to their marks, which nobody can do from behind a
        // screen that will not close. The duel then timed out waiting for a
        // walk that this screen was preventing.
        minecraft.setScreen(null);
    }

    /**
     * Closes a coin toss that is still up when the duel it announced arrives.
     * <p>
     * For the seat that did not win there is nothing to press and nothing to
     * wait for once the duel has started, and no other screen will replace this
     * one on a board. Called from the client's handlers for the first thing a
     * starting duel sends, whichever that is.
     */
    public static void dismiss()
    {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if(client.screen instanceof CoinTossScreen)
        {
            client.setScreen(null);
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        // fillGradient, never extractBackground: that blurs in 26.2 and has
        // taken the client down when one screen opened over another that had
        // already asked for it. Same decision as every other screen here.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int x = panelX();
        int y = panelY();
        NineSlice.draw(extractor, HubTextures.PANEL, x, y, panelW(), panelH());

        String title = "Coin Toss";
        extractor.text(font, title, x + (panelW() - font.width(title)) / 2, y + 10,
            MenuInk.title(), MenuInk.shadow());

        String result = "You won the toss";
        extractor.text(font, result, x + (panelW() - font.width(result)) / 2, y + 30,
            0xFF7CE38B, true);

        // No "waiting" state any more: this screen only ever belongs to the
        // seat with a choice to make, and that seat is never waiting on
        // anybody. The other one is told in chat and left free to walk.
        String line = answered ? "Starting the duel..." : "Who takes the first turn?";
        extractor.text(font, line, x + (panelW() - font.width(line)) / 2, y + 46,
            MenuInk.body(), MenuInk.shadow());

        // LAST, and it was missing entirely. Retained mode draws what it is
        // described, in the order it is described: without this the two buttons
        // exist, take clicks and are never painted, so the winner sees a
        // question with no answers and the duel appears to have hung. Every
        // other screen in this mod ends this way; this was the one that did
        // not, and the shop had the same fault before it.
        super.render(extractor.vanilla(), mouseX, mouseY, partialTick);
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


    /**
     * Escapable, and that is a correction.
     * <p>
     * It used to refuse, reasoning that both seats are committed and closing
     * would mean nothing. But a screen that cannot be closed is a screen that
     * can hold a player forever, and this one did: it is modal, it sat over an
     * overworld duel that was waiting for both players to WALK to their marks,
     * and there was no route out of it at all. "Nothing to mean" is not worth
     * one softlock, let alone the several this had.
     * <p>
     * Closing it early costs nothing that matters. The server keeps its own
     * thirty second deadline on the choice and starts the duel without one, so
     * leaving early is answering late -- and the duel arriving takes this away
     * regardless.
     */
    @Override
    public boolean shouldCloseOnEsc()
    {
        return true;
    }

    /**
     * And it lets go by itself, whatever else happens.
     * <p>
     * Belt and braces over the three places that already close it -- answering,
     * the board arriving, the first duel update. Each of those is a message
     * that has to arrive, and this screen's entire history is of messages that
     * did not. A deadline needs nothing to arrive: past the point where the
     * server has certainly decided without us, there is no reading of this
     * screen under which it should still be up.
     */
    @Override
    public void tick()
    {
        if(answered || ++ticks > STANDS_FOR_TICKS)
        {
            minecraft.setScreen(null);
        }
    }

    /** Longer than the server's own thirty seconds, so it is never the first to give up. */
    private static final int STANDS_FOR_TICKS = 35 * 20;

    private int ticks;

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
