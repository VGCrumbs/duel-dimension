package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.duel.npc.DuelistChallengeMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * How would you like to duel this duelist?
 * <p>
 * Asked because there is nowhere else to ask it. A duel between two players is
 * agreed in a lobby with a row for this; a duel against an NPC begins the moment
 * it is asked for, so the click is the only moment the question can be put --
 * and it has to be put before the duel starts, because once the engine is
 * running the presentation is settled.
 * <p>
 * Nothing is chosen for the player and nothing is remembered: closing this walks
 * away without a duel, which is what clicking a duelist by accident should do.
 */
public class DuelTypeScreen extends Screen
{
    private final int duelistId;
    private final String duelistName;

    public DuelTypeScreen(DuelistChallengeMessages.OfferDuel offer)
    {
        super(Component.literal("Duel " + offer.duelistName()));
        this.duelistId = offer.duelistId();
        this.duelistName = offer.duelistName();
    }

    private int panelW()
    {
        return Math.min(280, width - 40);
    }

    private int panelH()
    {
        return 132;
    }

    private int panelX()
    {
        return (width - panelW()) / 2;
    }

    private int panelY()
    {
        return Math.max(20, (height - panelH()) / 2);
    }

    @Override
    protected void init()
    {
        int x = panelX() + 12;
        int w = panelW() - 24;
        int y = panelY() + 40;

        // The board leads, because it is what this mod is for and what a lobby
        // now defaults to. Nothing is preselected -- this is still a question --
        // but the order is the answer most duellists want, and it was the other
        // way round.
        addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
            Component.literal("Overworld board"), pressed -> choose(true)));
        y += 26;
        addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
            Component.literal("Duel screen"), pressed -> choose(false)));
        y += 30;
        addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
            Component.literal("Cancel"), pressed -> onClose()));
    }

    private void choose(boolean overworld)
    {
        ClientPlayNetworking.send(new DuelistChallengeMessages.ChooseDuel(duelistId, overworld));
        onClose();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        // fillGradient, not extractBackground: that one BLURS, the blur may
        // only run once a frame, and a screen opening over one that already
        // asked for it took the client down. Every screen here settled on this.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int x = panelX();
        int y = panelY();
        NineSlice.draw(extractor, HubTextures.PANEL, x, y, panelW(), panelH());

        String title = "Duel " + duelistName;
        extractor.text(font, title, x + (panelW() - font.width(title)) / 2, y + 12,
            MenuInk.title(), MenuInk.shadow());
        String hint = "Where would you like to play?";
        extractor.text(font, hint, x + (panelW() - font.width(hint)) / 2, y + 26,
            MenuInk.body(), MenuInk.shadow());

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


    @Override
    public void onClose()
    {
        minecraft.setScreen(null);
    }

    /** The world carries on behind it; this is a question, not a pause. */
    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
