package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.duel.action.ActionIcon;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneInteraction;
import de.cas_ual_ty.dueldimension.duel.screen.IDuelScreenContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class InteractionWidget extends Button
{
    public final ZoneInteraction interaction;
    public final IDuelScreenContext context;

    /** Forge's {@code Button} held this; the constructor argument is unchanged. */
    protected final ITooltip onTooltip;

    public InteractionWidget(ZoneInteraction interaction, IDuelScreenContext context, int x, int y, int width, int height, Component title, Consumer<InteractionWidget> onPress, ITooltip onTooltip)
    {
        super(x, y, width, height, title, (w) -> onPress.accept((InteractionWidget) w), DEFAULT_NARRATION);
        this.onTooltip = onTooltip;
        this.interaction = interaction;
        this.context = context;
    }

    public InteractionWidget(ZoneInteraction interaction, IDuelScreenContext context, int x, int y, int width, int height, Consumer<InteractionWidget> onPress, ITooltip onTooltip)
    {
        super(x, y, width, height, interaction.icon.getLocal(), (w) -> onPress.accept((InteractionWidget) w), DEFAULT_NARRATION);
        this.onTooltip = onTooltip;
        this.interaction = interaction;
        this.context = context;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        // PORT-NOTE: Forge pushed the pose and translated z by 5 around all of
        // this, so the icon sat above anything the duel screen drew later at z 0 —
        // the card info panel among them. The GUI matrix is 2D and the depth test
        // it relied on is gone, so what covers what is now purely the order things
        // are extracted in. The icons are extracted with the other widgets, which
        // is after the field but before the screen's own late draws, so this is
        // unresolved: if an interaction icon ends up underneath the card info
        // panel, the fix belongs in DuelScreenDueling's draw order, not here.

        ActionIcon icon = interaction.icon;

        int iconWidth = icon.iconWidth;
        int iconHeight = icon.iconHeight;
        
        if(iconHeight >= height)
        {
            iconWidth = height * iconWidth / iconHeight;
            iconHeight = height;
        }
        
        if(iconWidth >= width)
        {
            iconHeight = width * iconHeight / iconWidth;
            iconWidth = width;
        }

        // The icon is one cell of a square sheet whose size the icon carries, so
        // the texel region Forge passed becomes a UV window over icon.fileSize.
        DdBlitUtil.blit(ms, icon.sourceFile,
            getX() + (width - iconWidth) / 2, getY() + (height - iconHeight) / 2, iconWidth, iconHeight,
            icon.iconX / (float) icon.fileSize, icon.iconY / (float) icon.fileSize,
            (icon.iconX + icon.iconWidth) / (float) icon.fileSize, (icon.iconY + icon.iconHeight) / (float) icon.fileSize,
            DdBlitUtil.NO_TINT);

        if(isHoveredOrFocused() && active)
        {
            ScreenUtil.renderHoverRect(ms, getX(), getY(), width, height);

            if(onTooltip != null)
            {
                onTooltip.onTooltip(this, ms, mouseX, mouseY);
            }
        }
    }
}