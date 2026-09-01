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
            freelook = false;
            return;
        }
        boolean camera = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            minecraft.getWindow(),
            ((de.cas_ual_ty.dueldimension.mixin.client.KeyMappingAccessor)(Object)TOGGLE_DISK)
                .dueldimension$key().getValue())
            // Unless the duel is waiting on a question only the cursor can
            // answer. Holding the camera key through a Yes/No would leave a
            // duellist looking at a board that had simply stopped, with the one
            // thing that could restart it refusing to appear.
            && !de.cas_ual_ty.dueldimension.clientutil.PromptOptions.needsList(
                de.cas_ual_ty.dueldimension.clientutil.DuelClientState.prompt)
            // Same for a picker full of cards nobody can see: it is the only
            // thing that can answer, and it needs a cursor to be answered with.
            && !de.cas_ual_ty.dueldimension.clientutil.PromptOptions.needsPicker(
                de.cas_ual_ty.dueldimension.clientutil.DuelClientState.prompt);
        freelook = camera;

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
        // Only over a bare world, and only if the board is what this player
        // asked to play on. Any other screen -- the duel screen, the deck list,
        // the pause menu -- is one they opened, and is theirs.
        if(!camera && minecraft.gui.screen() == null
            && !de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.screenPreferred())
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
     * Shows the coin and the die, for looking at the ported motion.
     * <p>
     * Development only, on right bracket. Both props came from Master Duel with
     * animation of their own, and a clip that converted wrongly does not throw
     * -- it plays a toss that turns the wrong way. Watching it is the check.
     */
    public static final KeyMapping TOSS_TEST = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.toss_test",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_RIGHT_BRACKET,
        CATEGORY));

    /**
     * Opens a trade against a stand-in, for looking at the interface.
     * <p>
     * Development only -- the server refuses the message outside a dev
     * environment -- and bound to backslash, which nothing else in the game
     * uses. A trade needs two sides to be worth looking at and a second player
     * is not always to hand, so the stand-in offers one card and is always
     * ready: every state the screen can reach is reachable by one person.
     */
    public static final KeyMapping TRADE_TEST = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.dueldimension.trade_test",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_BACKSLASH,
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

    /**
     * The overhead view of the board, on {@code V}.
     * <p>
     * A duel is read off a board that a standing player sees edge on. This puts
     * the camera where the flat duel screen has always put it -- straight down,
     * square on, the whole mat in frame -- without leaving the world for a
     * screen. See {@code DuelCamera}, which owns the placement.
     */
    public static final KeyMapping OVERHEAD_VIEW = KeyMappingHelper.registerKeyMapping(
        new KeyMapping(
            "key.dueldimension.overhead_view",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY));

    /**
     * Whether the freelook key is down and the duel is letting it work.
     * <p>
     * Written by {@link #cameraHeld} rather than asked for again, so the
     * crosshair and the pointer cannot disagree about whether the player is
     * looking around -- they were two reads of the same key with two sets of
     * conditions, which is one refactor away from drifting apart.
     */
    private static boolean freelook;

    public static boolean freelook()
    {
        return freelook;
    }

    private HubKeybinds()
    {
    }

    /** Whether the overhead key was down last tick, so a hold is not a repeat. */
    private static boolean overheadWasDown;

    /**
     * The overhead toggle, POLLED rather than consumed.
     *
     * <h2>Why a keybind does not work here</h2>
     * Minecraft only feeds key presses to {@link KeyMapping} while no screen is
     * open, and a duel is played with the board pointer open almost all of the
     * time -- so {@code consumeClick} never returned true and the key did
     * nothing at all. That is not a new discovery: {@link #cameraHeld} polls the
     * window directly for exactly this reason, and this is the same answer to
     * the same problem.
     * <p>
     * The mapping still exists, so the key is rebindable in the controls screen
     * and is read from wherever the player moved it. What is dropped is only
     * Minecraft's delivery of it.
     *
     * <h2>Edge, not level</h2>
     * A held key would toggle sixty times a second. The previous state is kept
     * and cleared whenever the duel is not on, so a key held through the end of
     * one duel cannot fire into the next.
     */
    private static void overheadKey(net.minecraft.client.Minecraft minecraft)
    {
        boolean locked = de.cas_ual_ty.dueldimension.clientutil.overworld
            .ClientDuelField.locked();
        if(!locked)
        {
            overheadWasDown = false;
            duelWasOn = false;
            de.cas_ual_ty.dueldimension.clientutil.DuelCamera.off();
            return;
        }
        // The duel just started, so it opens in whichever view was asked for in
        // the settings. On the RISING edge only: doing it every tick would
        // undo the key half a frame after it was pressed.
        if(!duelWasOn)
        {
            duelWasOn = true;
            de.cas_ual_ty.dueldimension.clientutil.DuelCamera.beginDuel();
        }
        // Not while something is being typed into. The board pointer and the
        // camera's own editor are the two screens a duellist has open while
        // still watching the board; anything else -- chat above all -- wants
        // the letter V and not a camera.
        net.minecraft.client.gui.screens.Screen open = minecraft.gui.screen();
        boolean typing = open != null
            && !(open instanceof de.cas_ual_ty.dueldimension.clientutil.overworld
                .BoardPointerScreen)
            && !(open instanceof DuelCameraScreen);
        boolean down = !typing && com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            minecraft.getWindow(),
            ((de.cas_ual_ty.dueldimension.mixin.client.KeyMappingAccessor)
                (Object)OVERHEAD_VIEW).dueldimension$key().getValue());
        if(down && !overheadWasDown)
        {
            de.cas_ual_ty.dueldimension.clientutil.DuelCamera.toggle();
        }
        overheadWasDown = down;

        // Backslash opens the editor, polled for the same reason and only from
        // the view it edits.
        boolean editor = !typing
            && de.cas_ual_ty.dueldimension.clientutil.DuelCamera.active()
            && com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSLASH);
        if(editor && !editorWasDown)
        {
            minecraft.setScreenAndShow(new DuelCameraScreen());
        }
        editorWasDown = editor;
    }

    private static boolean editorWasDown;

    /** Whether a duel was on last tick, so its start can be noticed. */
    private static boolean duelWasOn;

    /** Whether the anchor key was down last tick, so a hold is not a repeat. */
    private static boolean anchorWasDown;

    /**
     * The item anchor editor, on the numpad's decimal point.
     * <p>
     * POLLED rather than consumed, for the reason {@link #overheadKey} is: a
     * mapping is not delivered while a screen is open, and this screen is one of
     * the things it has to close. Not bound to a {@code KeyMapping} at all --
     * unlike the overhead view it is a development tool, and a rebindable key in
     * the controls list for placing a sword in a hand would be a control nobody
     * wants and everybody has to scroll past.
     * <p>
     * Only while a character is actually being worn, so on a vanilla body the
     * key does nothing and the numpad is left alone.
     */
    /**
     * Which anchor editor this key opens, which depends on what you are doing.
     * <p>
     * Riding opens the seat, everything else opens the grip. One key rather than
     * two because the editors are the same idea pointed at different numbers,
     * and the thing being tuned is always the thing on screen -- a duellist in a
     * saddle has no item grip to look at, and a duellist on foot has no seat.
     */
    private static net.minecraft.client.gui.screens.Screen anchorEditor(net.minecraft.client.Minecraft minecraft)
    {
        return minecraft.player != null && minecraft.player.isPassenger()
            ? new RideAnchorScreen() : new ItemAnchorScreen();
    }

    private static void anchorKey(net.minecraft.client.Minecraft minecraft)
    {
        boolean typing = minecraft.gui.screen() != null
            && !(minecraft.gui.screen() instanceof ItemAnchorScreen)
            && !(minecraft.gui.screen() instanceof RideAnchorScreen);
        boolean down = !typing
            && de.cas_ual_ty.dueldimension.clientutil.character.CharacterEdits.worn()
            && com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                minecraft.getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_KP_DECIMAL);
        if(down && !anchorWasDown)
        {
            boolean open = minecraft.gui.screen() instanceof ItemAnchorScreen
                || minecraft.gui.screen() instanceof RideAnchorScreen;
            minecraft.setScreenAndShow(open ? null : anchorEditor(minecraft));
        }
        anchorWasDown = down;
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

        overheadKey(minecraft);

        anchorKey(minecraft);

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

        boolean toss = false;
        while(TOSS_TEST.consumeClick())
        {
            toss = true;
        }
        if(toss)
        {
            minecraft.setScreenAndShow(
                new de.cas_ual_ty.dueldimension.clientutil.hub.TossTestScreen());
        }

        boolean trade = false;
        while(TRADE_TEST.consumeClick())
        {
            trade = true;
        }
        // Backslash outside a duel is still the trade stand-in. Inside one it
        // opens the camera editor instead -- see overheadKey, which has to poll
        // for it because a keybind cannot be heard through an open screen.
        if(trade && minecraft.getConnection() != null)
        {
            // Asked for, not opened: the table is the server's, so the screen
            // appears when it answers with one -- the same path a real trade
            // takes, which is what makes this worth testing with.
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new de.cas_ual_ty.dueldimension.duel.trade.TradeMessages.OpenDebug());
        }
    }
}
