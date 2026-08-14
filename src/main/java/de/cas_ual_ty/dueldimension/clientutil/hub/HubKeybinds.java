package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The {@code Y} key, which opens the duel hub.
 * <p>
 * Bound in its own category so a player can rebind it.
 * <p>
 * Two Forge things have no counterpart here, and neither is a loss.
 * {@code KeyConflictContext.IN_GAME} is gone — Fabric has no conflict contexts.
 * Nor is one needed: the game only accumulates key clicks while no screen has
 * the keyboard, so a keybind polled on the tick cannot fire under a chat box.
 * The Forge version's own {@code screen != null} check was belt and braces, and
 * {@code Minecraft.screen} is no longer reachable anyway.
 * <p>
 * {@code InputEvent.Key} becomes that polling, which is how
 * {@code consumeClick} was always meant to be read: it drains the queued
 * presses, so holding the key opens the hub once rather than every tick.
 */
public final class HubKeybinds
{
    /**
     * The mod's own key category.
     * <p>
     * A category is a registered object with an id now, not a translation key
     * passed as a string -- so two mods cannot accidentally share one by
     * spelling it the same way, and the id is what gets translated.
     */
    /**
     * Holds the CAMERA for as long as the key is down, and gives the cursor
     * back the moment it is let go.
     * <p>
     * The other way round from how this started, and the right way round. A
     * duel is an hour of pointing at cards and a few seconds of looking around,
     * so pointing is what a duellist should get for free -- asking for the
     * common thing and being given the rare one is backwards. Holding is still
     * what makes it a reflex rather than a decision: look, let go, click.
     * <p>
     * The key's raw state is read from the window rather than through the key
     * mapping, because a mapping is not polled at all while a screen is open --
     * and the cursor IS a screen, so a mapping would see the press that closes
     * it and never the release that should bring it back.
     */
    public static void cameraHeld(net.minecraft.client.Minecraft minecraft)
    {
        if(!de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked())
        {
            return;
        }
        boolean camera = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            minecraft.getWindow(),
            ((de.cas_ual_ty.dueldimension.mixin.client.KeyMappingAccessor)(Object)TOGGLE_DISK)
                .dueldimension$key().getValue());

        if(minecraft.gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.overworld.BoardPointerScreen open)
        {
            // A pointer borrowed to answer one question keeps the cursor until
            // it has been answered, even with the key down: it exists BECAUSE
            // the player clicked something while holding it.
            if(camera && !open.isPinned())
            {
                open.onClose();
            }
            return;
        }
        // Only over a bare world. Any other screen -- the duel screen, the deck
        // list, the pause menu -- is one the player opened, and is theirs.
        if(!camera && minecraft.gui.screen() == null)
        {
            // gui.setScreen: setScreenAndShow forces a frame, and forcing one
            // while the cursor is being handed over is what made the swap
            // flicker.
            minecraft.gui.setScreen(
                new de.cas_ual_ty.dueldimension.clientutil.overworld.BoardPointerScreen());
        }
    }

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        net.minecraft.resources.Identifier.fromNamespaceAndPath(
            de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "duel_dimension"));

    public static final KeyMapping OPEN_HUB = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.duel_hub",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
        CATEGORY));

    /**
     * Swaps between the board in the world and the duel screen, mid-duel.
     * <p>
     * Both are views of one duel -- the same client state, the same engine, the
     * same prompt -- so this is a change of where you are looking and not of
     * what is happening. Worth having because some things really are easier on
     * the screen: reading a long chain, or a selection with a dozen candidates.
     */
    public static final KeyMapping DUEL_VIEW = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.duel_view",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        CATEGORY));

    /**
     * Acts on the card being looked at during an overworld duel.
     * <p>
     * Its own binding rather than the vanilla use key, which is already
     * committed to the item in hand and whose click queue is drained by the
     * game itself in an order this mod does not control. A duellist is locked
     * in place with nothing else to do with their hands, so a spare key costs
     * them nothing and can be rebound.
     */
    public static final KeyMapping DUEL_ACT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.duel_act",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        CATEGORY));

    /**
     * Opens the foil blend test. Temporary, and goes when the question it asks
     * is answered -- see {@code FoilTestScreen}.
     */
    public static final KeyMapping FOIL_TEST = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.foil_test",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        CATEGORY));

    /**
     * Puts the active duel disk on, or takes it off.
     * <p>
     * The disk is worn in the off-hand, which is where every other part of the
     * mod already looks for it -- the duel start, the Chaos Disk promise and
     * challenging a player by clicking them. So this asks the SERVER to move
     * it: the client may not conjure an item into a slot, and the server is
     * the side that knows which disk the player owns and has active.
     */
    public static final KeyMapping TOGGLE_DISK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.toggle_disk",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        CATEGORY));

    private HubKeybinds()
    {
    }

    /** Called once from the client initialiser. */
    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(HubKeybinds::tick);
    }

    private static void tick(Minecraft minecraft)
    {
        if(minecraft.player == null)
        {
            return;
        }
        boolean pressed = false;
        while(OPEN_HUB.consumeClick())
        {
            pressed = true;
        }
        if(pressed)
        {
            minecraft.setScreenAndShow(new DuelHubScreen());
        }

        boolean disk = false;
        while(TOGGLE_DISK.consumeClick())
        {
            disk = true;
        }
        // Not during a duel. The disk cannot come off mid-duel anyway, so the
        // key is free -- and during a duel it does something a duellist wants
        // constantly instead: see cameraHeld.
        if(disk && !de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked())
        {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.duel.dueldisk.DiskMessages.ToggleDisk());
        }
        cameraHeld(minecraft);

        boolean foil = false;
        while(FOIL_TEST.consumeClick())
        {
            foil = true;
        }
        if(foil)
        {
            minecraft.setScreenAndShow(
                new de.cas_ual_ty.dueldimension.clientutil.FoilTestScreen());
        }
    }
}
