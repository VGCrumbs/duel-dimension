package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DuelSuppression;
import de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No hearts, no hunger and no experience while a duel is on — and no hotbar
 * while one is being played in the world.
 * <p>
 * A duel has its own HUD — life points, the phase bar, the turn count — and it
 * has nothing to do with the one underneath it. Neither do the bars: movement is
 * suppressed for the whole duel, so a duellist can neither starve nor fall, and
 * a row of hearts that cannot change is decoration over a board that has to be
 * read.
 * <p>
 * <b>Only these, and deliberately not the whole HUD.</b> Minecraft has a switch
 * for hiding all of it, and using that would take the chat with it — which would
 * be an odd thing to do a few changes after making chat reachable during duels in
 * the first place.
 *
 * <h2>Four hooks here, two on 26.2</h2>
 *
 * The bars are a mixin on both versions. The hotbar and the held-item name are
 * NOT: 26.2 has a HUD element registry, so the client initialiser replaces those
 * two elements with wrappers that skip the original while a duel is locked. There
 * is no such registry on 1.21.1 — the HUD is one {@code Gui.render} that calls
 * private methods — so the same two decisions are made here, from inside, and the
 * initialiser says nothing about them.
 * <p>
 * The CONDITIONS differ between the two halves and that is not an oversight.
 * {@link DuelSuppression#inDuel()} covers a duel in any form; the hotbar goes only
 * while {@link ClientDuelField#locked()}, because it is the world duel that takes
 * the bottom of the screen for the hand.
 */
@Mixin(Gui.class)
public class DuelHudMixin
{
    /**
     * The question the redirect below has to be able to ask for itself.
     * <p>
     * {@code isExperienceBarVisible} is private on {@code Gui}, so the redirect
     * cannot call it through its {@code Gui} parameter. Shadowed instead, which
     * is the same method by the time this class is merged into {@code Gui}.
     */
    @Shadow
    private boolean isExperienceBarVisible()
    {
        throw new AssertionError("replaced by the mixin processor");
    }

    /**
     * Health, hunger, armour and air, which are one method between them.
     * <p>
     * Checked rather than assumed from the name: the call that draws the hunger
     * row sits inside this method with no other method beginning in between.
     */
    @Inject(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideStatusBars(GuiGraphics graphics, CallbackInfo callback)
    {
        if(DuelSuppression.inDuel())
        {
            callback.cancel();
        }
    }

    /** The mount's hearts, which sit in the same block and would be left alone. */
    @Inject(method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideVehicleHearts(GuiGraphics graphics, CallbackInfo callback)
    {
        if(DuelSuppression.inDuel())
        {
            callback.cancel();
        }
    }

    /**
     * The crosshair, while a duel is being played rather than looked around.
     * <p>
     * A duellist points at zones with the board pointer, not down the middle of
     * the screen, so the reticle sits over the mat saying nothing. It comes back
     * while the freelook key is held, because that IS the mode where the player
     * is aiming the view by hand and the one thing a crosshair is for.
     * <p>
     * {@link de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds#freelook()}
     * rather than a second read of the key: that field is written by the one
     * place that decides whether the duel is letting freelook work, so the
     * crosshair and the pointer cannot come to different conclusions about
     * whether the player is looking around.
     * <p>
     * 26.2 has carried this since it was written; 1.21.1 did not, and the
     * reticle sat on the board for the whole port. The method is
     * {@code renderCrosshair} here against {@code extractCrosshair} there, which
     * is the same rename every other draw call in this class took.
     */
    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;"
        + "Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideCrosshair(GuiGraphics graphics,
        net.minecraft.client.DeltaTracker delta, CallbackInfo callback)
    {
        if(DuelSuppression.inDuel()
            && !de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.freelook())
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
     * <p>
     * 26.2 redirects {@code MultiPlayerGameMode.hasExperience()} because that is
     * the call it finds at this spot. 1.21.1 asks the same question one level up,
     * through {@code Gui.isExperienceBarVisible()}, so that is what is redirected
     * here — and scoped to this method, since the same question is asked
     * elsewhere for reasons that are not a duel's business.
     */
    @Redirect(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Gui;isExperienceBarVisible()Z"))
    private boolean dueldimension$hideExperience(Gui gui)
    {
        return isExperienceBarVisible() && !DuelSuppression.inDuel();
    }

    /**
     * The hotbar itself, during a world duel.
     * <p>
     * The bottom of the screen belongs to the hand. Hidden rather than emptied,
     * so everything comes back the moment the duel ends.
     */
    @Inject(method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;"
        + "Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideHotbar(GuiGraphics graphics, DeltaTracker delta,
        CallbackInfo callback)
    {
        if(ClientDuelField.locked())
        {
            callback.cancel();
        }
    }

    /** The name of the held item, which floats above the hotbar that just went. */
    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true)
    private void dueldimension$hideHeldItemName(GuiGraphics graphics, CallbackInfo callback)
    {
        if(ClientDuelField.locked())
        {
            callback.cancel();
        }
    }
}
