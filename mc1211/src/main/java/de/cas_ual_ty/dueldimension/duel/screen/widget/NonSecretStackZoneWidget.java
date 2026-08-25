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
    protected void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        super.extractContents(ms, mouseX, mouseY, partialTicks);
        hoverCard = null; // dont select top card when clicking on it, ever
    }
}
