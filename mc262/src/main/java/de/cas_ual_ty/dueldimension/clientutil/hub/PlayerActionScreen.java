package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.duel.trade.TradeMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * What to do with the player you just right-clicked: duel them, or trade.
 *
 * <h2>Why this is a screen and not a click</h2>
 * Right-clicking a player used to challenge them outright, which was fine while
 * duelling was the only thing one player could do to another. With two, the
 * click has to ask — and it asks HERE rather than deciding on the server,
 * because a menu is a thing a player reads and dismisses, and dismissing it
 * must cost nothing. Choosing sends one message; closing sends none.
 *
 * <h2>The target is a UUID, not a name</h2>
 * The server hands one over and gets the same one back. A name would be a
 * second way to say who is meant, and the two can disagree — a player can
 * change theirs between the click and the choice.
 */
public class PlayerActionScreen extends Screen
{
    private final UUID target;
    private final String name;

    public PlayerActionScreen(UUID target, String name)
    {
        super(Component.literal(name));
        this.target = target;
        this.name = name;
    }

    @Override
    protected void init()
    {
        int centreX = width / 2;
        int top = height / 2 - 10;
        addRenderableWidget(new HubWidgets.TextureButton(centreX - 100, top, 96, 20,
            Component.literal("Duel"), pressed -> choose(false)));
        addRenderableWidget(new HubWidgets.TextureButton(centreX + 4, top, 96, 20,
            Component.literal("Trade"), pressed -> choose(true)));
    }

    private void choose(boolean trade)
    {
        ClientPlayNetworking.send(new TradeMessages.PlayerAction(target, trade));
        // Closed at once. A trade opens its own screen when the server answers
        // with the table, and a duel invitation has nothing more to show.
        onClose();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY,
        float partialTick)
    {
        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, and the blur is once per frame -- see PackOpeningScreen.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int centreX = width / 2;
        NineSlice.draw(poseStack, HubTextures.PANEL, centreX - 112, height / 2 - 44,
            224, 76);
        poseStack.text(font, name, centreX - font.width(name) / 2, height / 2 - 34,
            MenuInk.title(), MenuInk.shadow());
        String hint = "Escape to do neither";
        poseStack.text(font, hint, centreX - font.width(hint) / 2, height / 2 + 16,
            MenuInk.dim(), MenuInk.shadow());
        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
    }
}
