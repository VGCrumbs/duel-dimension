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
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        net.minecraft.resources.Identifier.fromNamespaceAndPath(
            de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "duel_dimension"));

    public static final KeyMapping OPEN_HUB = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.duel_hub",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
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
