package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.duel.PlayerRole;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class RoleButtonWidget extends Button
{
    public Supplier<Boolean> available;
    public PlayerRole role;

    public RoleButtonWidget(int xIn, int yIn, int widthIn, int heightIn, Component text, Button.OnPress onPress, Supplier<Boolean> available, PlayerRole role)
    {
        super(xIn, yIn, widthIn, heightIn, text, onPress, DEFAULT_NARRATION);
        this.available = available;
        this.role = role;
    }

    @Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partial)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

        // A seat is taken and freed by the other player while this screen is
        // open, so availability is asked every frame rather than at build time.
        // extractContents is the first thing AbstractButton does, so the sprite
        // chosen just below already reflects the answer.
        active = available.get();
        extractDefaultSprite(ms);
    }
}
