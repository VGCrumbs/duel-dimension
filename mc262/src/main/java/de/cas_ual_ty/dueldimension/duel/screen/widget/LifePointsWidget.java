package de.cas_ual_ty.dueldimension.duel.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

public class LifePointsWidget extends AbstractWidget
{
    public static final Identifier DUEL_WIDGETS = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/duel_widgets.png");
    
    public Supplier<Integer> lpGetter;
    public int maxLP;
    public ITooltip tooltip;
    
    public LifePointsWidget(int x, int y, int width, int height, Supplier<Integer> lpGetter, int maxLP, ITooltip tooltip)
    {
        super(x, y, width, height, Component.empty());
        this.lpGetter = lpGetter;
        this.maxLP = maxLP;
        this.tooltip = tooltip;
    }
    
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Font fontrenderer = minecraft.font;
        
        int lp = lpGetter.get();
        float relativeLP = Math.min(1F, lp / (float) maxLP);
        
        final int margin = 1;
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        
        // Three layers: the empty bar, the filled part clipped to the current
        // life points, and the frame over both. The fade that was a shader
        // colour before all three is now each one's tint. The renderBg call
        // that followed them is dropped: it was vanilla's empty extension hook
        // and nothing in this hierarchy ever overrode it.
        int tint = DdBlitUtil.alpha(alpha);
        DdBlitUtil.blitTexels(ms, DUEL_WIDGETS, x, y, 0, 1 * 8, width, height, tint);
        DdBlitUtil.blitTexels(ms, DUEL_WIDGETS, x, y, 0, 0,
            Mth.ceil(width * relativeLP), height, tint);
        DdBlitUtil.blitTexels(ms, DUEL_WIDGETS, x, y, 0, 2 * 8, width, height, tint);

        x = getX() + width / 2;
        y = getY() + height / 2;

        ms.pose().pushMatrix();

        ms.pose().scale(0.5F, 0.5F);

        int j = getFGColor();
        ms.centeredText(fontrenderer, Component.literal(String.valueOf(lp)), x * 2, y * 2 - fontrenderer.lineHeight / 2, j | Mth.ceil(alpha * 255.0F) << 24);
        
        ms.pose().popMatrix();
        
        if(isHoveredOrFocused())
        {
            tooltip.onTooltip(this, ms, mouseX, mouseY);
        }
    }

    /**
     * The body of Forge's {@code AbstractWidget.getFGColor}, which 26.2 has no
     * counterpart for. Its other branch read a {@code packedFGColor} that was a
     * Forge addition and that nothing in this mod ever set, so it is left out.
     */
    public int getFGColor()
    {
        return active ? 16777215 : 10526880;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput)
    {
    
    }
}
