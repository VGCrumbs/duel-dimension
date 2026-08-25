package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;
import de.cas_ual_ty.dueldimension.duel.playfield.Zone;
import de.cas_ual_ty.dueldimension.duel.screen.IDuelScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public class StackZoneWidget extends ZoneWidget
{
    // this does not render counters

    public StackZoneWidget(Zone zone, IDuelScreenContext context, int width, int height, Component title, Consumer<ZoneWidget> onPress, ITooltip onTooltip)
    {
        super(zone, context, width, height, title, onPress, onTooltip);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;

        // PORT-NOTE: as in ZoneWidget.extractContents, the fade that opened this
        // method has nowhere to go -- neither ScreenUtil's rectangles nor
        // CardRenderUtil's cards take a tint, and fadeTint() is what they want.

        renderZoneSelectRect(ms, zone, getX(), getY(), width, height);

        hoverCard = renderCards(ms, mouseX, mouseY);

        if(zone.getCardsAmount() > 0)
        {
            // The count used to be pushed towards the viewer so the cards under it
            // could not swallow it. The GUI matrix is 2D and needs no such trick:
            // what is described later draws later, and the cards are above.
            ms.centeredText(fontrenderer, Component.literal(String.valueOf(zone.getCardsAmount())),
                    getX() + width / 2, getY() + height / 2 - fontrenderer.lineHeight / 2,
                    16777215 | Mth.ceil(alpha * 255.0F) << 24);
        }

        if(active)
        {
            if(isHoveredOrFocused())
            {
                if(zone.getCardsAmount() == 0)
                {
                    ScreenUtil.renderHoverRect(ms, getX(), getY(), width, height);
                }

                extractTooltip(ms, mouseX, mouseY);
            }
        }
        else
        {
            ScreenUtil.renderDisabledRect(ms, getX(), getY(), width, height);
        }
    }

    @Override
    public boolean openAdvancedZoneView()
    {
        return !zone.getType().getIsSecret() && zone.getCardsAmount() > 0;
    }
}
