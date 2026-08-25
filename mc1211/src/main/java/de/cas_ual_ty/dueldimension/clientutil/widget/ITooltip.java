package de.cas_ual_ty.dueldimension.clientutil.widget;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * A tooltip hook for the mod's widgets.
 * <p>
 * On Forge this took a {@code PoseStack} and a {@code Widget}; both are gone in
 * 26.2 — a widget describes itself to a {@link GuiGraphicsExtractor}, and the
 * {@code Widget} marker interface was removed, so the hook is handed the
 * concrete {@link AbstractWidget} (which every widget that uses this extends).
 * <p>
 * It survives at all because vanilla's replacement does not fit. {@code
 * setTooltip(Tooltip)} suits a button whose tooltip is settled when it is built;
 * a duel screen's is not. A scroll arrow explains itself differently depending on
 * how much chat is above it, and a zone's depends on what the player is currently
 * dragging — so the answer has to be asked for at the moment of drawing.
 */
public interface ITooltip
{
    void onTooltip(AbstractWidget widget, GuiGraphicsExtractor extractor, int mouseX, int mouseY);
}
