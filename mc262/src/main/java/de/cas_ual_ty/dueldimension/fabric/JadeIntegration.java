package de.cas_ual_ty.dueldimension.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import snownee.jade.api.Accessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.ui.BoxElement;
import snownee.jade.api.ui.TooltipAnimation;

/**
 * Keeps Jade's tooltip out of a duel.
 * <p>
 * A duellist standing at a board is looking at cards, and Jade is looking at
 * whatever block happens to be behind them -- so its panel sits in the middle
 * of the screen naming a slab while the duel is being played through it. It is
 * suppressed for the whole duel, on the board and on the screen alike.
 * <p>
 * <b>Nothing else in the mod references this class, and nothing should.</b>
 * Jade is a compile-only dependency, so these imports do not resolve at runtime
 * unless it is installed. That is safe only because the loader keeps entrypoint
 * values as plain strings and classloads them on demand -- with Jade absent
 * this class is never loaded and the missing types are never looked up. One
 * reference from a class that IS loaded would turn that into a
 * NoClassDefFoundError on startup for everyone without Jade. Same arrangement,
 * and same reasoning, as {@code ModMenuIntegration}.
 */
@WailaPlugin
public class JadeIntegration implements IWailaPlugin
{
    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.addBeforeRenderCallback(new DuelSuppressor());
    }

    /**
     * Returns true to cancel, which is Jade's own convention rather than an
     * assumption: {@code OverlayRenderer} walks its before-render callbacks and
     * RETURNS from the draw the moment one answers true.
     */
    private static class DuelSuppressor implements snownee.jade.api.callback.JadeBeforeRenderCallback
    {
        @Override
        public boolean beforeRender(BoxElement box, TooltipAnimation animation,
            GuiGraphicsExtractor extractor, Accessor<?> accessor)
        {
            // Either presentation of a duel counts. The board hides it because
            // the board is what the player is looking at; the screen hides it
            // because Jade would otherwise draw over a screen the duel owns.
            return de.cas_ual_ty.dueldimension.clientutil.DuelSuppression.hudHidden();
        }
    }
}
