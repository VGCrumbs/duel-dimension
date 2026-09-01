package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.clientutil.hub.MenuInk;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The preload's progress, drawn over the HUD where the experience bar sits.
 * <p>
 * Registered against {@code VanillaHudElements.EXPERIENCE_LEVEL} so it takes the
 * same place in the stack as the bar it is modelled on, rather than being
 * painted over whatever happens to be there.
 * <p>
 * Every part of it is existing art — the scrollbar's track and thumb, which are
 * a nine-sliced trough and fill and are exactly the right shape lying on their
 * side. The project's rule is that UI is PNG and only text uses the font, so
 * nothing here is a rectangle drawn in code.
 */
public final class PreloadHud implements HudElement
{
    /**
     * Width of the bar.
     * <p>
     * Wider than the vanilla experience bar's 182. Matching that looked
     * deliberate until there was text on it: "Downloading Raws 41 / 10868" and
     * "+12 MB 921 MB on disk" do not fit side by side in 182 pixels and ran
     * straight through each other.
     */
    private static final int BAR_W = 260;
    private static final int BAR_H = 7;
    /** Clear of the hotbar and the experience bar, which sit below this. */
    private static final int ABOVE_HOTBAR = 62;
    private static final int LABEL_GAP = 3;
    /** Clear space demanded between the two labels before they share a line. */
    private static final int LABEL_MIN_GAP = 12;

    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, DeltaTracker delta)
    {
        if(!CardPreloadJob.visible())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // No hide-GUI test: this is registered as a HUD element, and the HUD
        // is not extracted at all while the GUI is hidden. Options.hideGui is
        // also gone in 26.2.
        if(client.font == null)
        {
            return;
        }

        int x = (poseStack.guiWidth() - BAR_W) / 2;
        int y = poseStack.guiHeight() - ABOVE_HOTBAR;

        // Row 0 of the scrollbar art is its trough, row 1 its fill. Sliced the
        // same way it is when it runs vertically, only wider than it is tall.
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, BAR_W, BAR_H, 0, 2);
        int filled = Math.round(BAR_W * CardPreloadJob.progress());
        if(filled > 0)
        {
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, Math.max(4, filled), BAR_H,
                1, 2);
        }

        String left = CardPreloadJob.phase().label();
        if(CardPreloadJob.phase() != CardPreloadJob.Phase.FINISHED)
        {
            left = left + "  " + CardPreloadJob.done() + " / " + CardPreloadJob.total();
        }
        if(CardPreloadJob.failed() > 0)
        {
            // Said out loud. A bar that reaches the end while quietly having
            // skipped two hundred cards is a bar that lied.
            left = left + "  (" + CardPreloadJob.failed() + " failed)";
        }

        // What this is costing, which is the whole reason the command is opt-in.
        String right = CardPreloadJob.human(CardPreloadJob.diskBytes()) + " on disk";
        if(CardPreloadJob.fetchedBytes() > 0L)
        {
            right = "+" + CardPreloadJob.human(CardPreloadJob.fetchedBytes()) + "   " + right;
        }

        // Side by side where they fit, stacked where they do not. A label that
        // overlaps another is worse than one on its own line, and how long
        // these get depends on the phase, the card count and the units the
        // sizes land in -- so it is measured rather than assumed.
        int leftW = client.font.width(left);
        int rightW = client.font.width(right);
        int line = client.font.lineHeight;
        boolean sideBySide = leftW + LABEL_MIN_GAP + rightW <= BAR_W;

        if(sideBySide)
        {
            int textY = y - line - LABEL_GAP;
            poseStack.text(client.font, left, x, textY, MenuInk.label(), MenuInk.shadow());
            poseStack.text(client.font, right, x + BAR_W - rightW, textY, 0xFF9FA6B4, true);
        }
        else
        {
            // The phase goes on top, nearest the bar it describes; the storage
            // line sits above it, where it is still legible but secondary.
            poseStack.text(client.font, left, x, y - line - LABEL_GAP, MenuInk.label(), MenuInk.shadow());
            poseStack.text(client.font, right, x, y - line * 2 - LABEL_GAP * 2,
                0xFF9FA6B4, true);
        }
    }
}
