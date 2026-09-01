package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No hearts, no hunger and no experience while a duel is on.
 * <p>
 * A duel has its own HUD — life points, the phase bar, the turn count — and it
 * has nothing to do with the one underneath it. Neither do the bars: movement is
 * suppressed for the whole duel, so a duellist can neither starve nor fall, and
 * a row of hearts that cannot change is decoration over a board that has to be
 * read.
 * <p>
 * <b>Only the bars, and deliberately not the whole HUD.</b> Minecraft has a
 * switch for hiding all of it, and using that would take the chat with it — which
 * would be an odd thing to do a few changes after making chat reachable during
 * duels in the first place. The hotbar stays for the same reason: nobody asked
 * for it to go, and it is not in the way.
 */
@Mixin(Hud.class)
public class DuelHudMixin
{
    /**
     * Health, hunger, armour and air, which are one method between them.
     * <p>
     * Checked rather than assumed from the name: the call that draws the hunger
     * row sits inside this method with no other method beginning in between.
     */
    @Inject(method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideStatusBars(GuiGraphicsExtractor extractor,
        CallbackInfo callback)
    {
        if(DuelSuppression.inDuel())
        {
            callback.cancel();
        }
    }

    /** The mount's hearts, which sit in the same block and would be left alone. */
    @Inject(method = "extractVehicleHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideVehicleHearts(GuiGraphicsExtractor extractor,
        CallbackInfo callback)
    {
        if(DuelSuppression.inDuel())
        {
            callback.cancel();
        }
    }

    /**
     * The experience bar, through the game's own answer for not having one.
     * <p>
     * There is no method to cancel: the bar is drawn inline among the hotbar and
     * its decorations, so cancelling would take the hotbar too. What there IS is
     * the question the game already asks before drawing it — creative mode
     * answers no and the bar simply is not there — so a duel answers no as well.
     * That reuses a path the game exercises constantly rather than inventing one.
     */
    @Redirect(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z"))
    private boolean dueldimension$hideExperience(MultiPlayerGameMode mode)
    {
        return mode.hasExperience() && !DuelSuppression.inDuel();
    }

    /**
     * The crosshair, which a duel has no use for.
     * <p>
     * A duellist points at zones with the board pointer, not down the middle of
     * the screen, so the reticle sits over the mat saying nothing. It comes back
     * while the freelook key is held, because that IS the mode where the player
     * is aiming the view by hand and the one thing a crosshair is for.
     */
    @Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
        + "Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideCrosshair(GuiGraphicsExtractor extractor,
        net.minecraft.client.DeltaTracker delta, CallbackInfo callback)
    {
        if(DuelSuppression.inDuel()
            && !de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.freelook())
        {
            callback.cancel();
        }
    }
}
