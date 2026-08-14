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
        // The screen stays up rather than closing: the duel screen opens itself
        // when the first board arrives, and closing to the world in between
        // would put the player back in the field for a moment for no reason.
        rebuildWidgets();
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
