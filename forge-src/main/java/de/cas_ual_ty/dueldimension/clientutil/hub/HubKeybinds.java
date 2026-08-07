package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The {@code Y} key, which opens the {@link DuelHubScreen}.
 * <p>
 * Bound in its own category so a player can rebind it, and restricted to the
 * in-game context so it does not fire while a screen or a chat box already has
 * the keyboard.
 */
public final class HubKeybinds
{
    public static final KeyMapping OPEN_HUB = new KeyMapping(
        "key.dueldimension.duel_hub",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y),
        "key.categories.dueldimension");

    private HubKeybinds()
    {
    }

    public static void register(RegisterKeyMappingsEvent event)
    {
        event.register(OPEN_HUB);
    }

    /**
     * Opens the hub on a press. consumeClick drains the queued presses, so
     * holding the key opens it once rather than every tick.
     */
    public static void onKeyInput(InputEvent.Key event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.screen != null || minecraft.player == null)
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
            minecraft.setScreen(new DuelHubScreen());
        }
    }
}
