package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.duel.match.LobbyMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        if(!toss.won() || answered)
        {
            return; // nothing to press: this seat is waiting, not choosing
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
        minecraft.gui.setScreen(null);
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
        if(client.gui.screen() instanceof CoinTossScreen)
        {
            client.gui.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
        float partialTick)
    {
        // fillGradient, never extractBackground: that blurs in 26.2 and has
        // taken the client down when one screen opened over another that had
        // already asked for it. Same decision as every other screen here.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int x = panelX();
        int y = panelY();
        NineSlice.draw(extractor, HubTextures.PANEL, x, y, panelW(), panelH());

        String title = "Coin Toss";
        extractor.text(font, title, x + (panelW() - font.width(title)) / 2, y + 10,
            0xFFF4D089, true);

        String result = toss.won() ? "You won the toss" : toss.winnerName() + " won the toss";
        extractor.text(font, result, x + (panelW() - font.width(result)) / 2, y + 30,
            toss.won() ? 0xFF7CE38B : 0xFFC2C9D6, true);

        String line = answered ? "Starting the duel..."
            : toss.won() ? "Who takes the first turn?"
                : "Waiting for " + toss.winnerName() + " to choose";
        extractor.text(font, line, x + (panelW() - font.width(line)) / 2, y + 46,
            0xFFC2C9D6, true);

        // LAST, and it was missing entirely. Retained mode draws what it is
        // described, in the order it is described: without this the two buttons
        // exist, take clicks and are never painted, so the winner sees a
        // question with no answers and the duel appears to have hung. Every
        // other screen in this mod ends this way; this was the one that did
        // not, and the shop had the same fault before it.
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    /**
     * The duel is mid-start and both seats are committed to it, so there is
     * nothing for closing this to mean. The winner's timeout on the server is
     * what stops it waiting forever.
     */
    @Override
    public boolean shouldCloseOnEsc()
    {
        return false;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
