package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import de.cas_ual_ty.dueldimension.duel.screen.IDuelScreenContext;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class NonSecretStackZoneWidget extends StackZoneWidget
{
    public NonSecretStackZoneWidget(Zone zone, IDuelScreenContext context, int width, int height, Component title, Consumer<ZoneWidget> onPress, ITooltip onTooltip)
    {
        super(zone, context, width, height, title, onPress, onTooltip);
    }

    @Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTicks)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The
        // body is unchanged -- it is handed the compatibility surface over
        // the real GuiGraphics.
        GuiGraphicsExtractor ms = new GuiGraphicsExtractor(vanillaGraphics);

        super.renderWidget(ms.vanilla(), mouseX, mouseY, partialTicks);
        hoverCard = null; // dont select top card when clicking on it, ever
    }
}
