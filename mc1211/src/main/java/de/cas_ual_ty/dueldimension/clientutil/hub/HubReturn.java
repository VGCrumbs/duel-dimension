package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Where a hub menu goes when it closes.
 *
 * <h2>The problem is that the server opens these</h2>
 * Most of the hub's screens are handed a {@code parent} by whoever opened them
 * and return to it — {@link CharacterScreen}, {@link DeckEditorScreen} and
 * {@link DeckBoxPickerScreen} all do. The shops cannot: they are opened from a
 * NETWORK HANDLER, because the hub's button asks the server for the stock and
 * the screen is built when the reply lands. By then the code doing the building
 * is a packet handler that knows what to show and nothing whatever about where
 * the player was.
 * <p>
 * So they closed to the world, and a player who wanted two things from the hub
 * had to walk back into it between them.
 *
 * <h2>Asking the screen stack instead of threading it through</h2>
 * The answer is the one {@link PackOpeningScreen} already uses: at the moment a
 * screen is CONSTRUCTED, the screen it is about to replace is still the one on
 * show, so it can simply be read. Nothing has to be carried through the request,
 * the packet, or the handler — none of which have any business knowing about
 * screens.
 * <p>
 * Restricted to the hub's own screens rather than taking whatever happens to be
 * open. A shop only ever opens from the hub, so in practice it is the same
 * answer; the restriction is there so that the day something else opens one, it
 * closes to the world rather than dropping the player into a menu they had
 * finished with.
 */
public final class HubReturn
{
    private HubReturn()
    {
    }

    /**
     * The hub screen to come back to, or null if this was not opened from one.
     * <p>
     * Call it from a constructor and keep the answer. Called later it would
     * report the screen doing the asking, which is itself.
     */
    public static Screen parent()
    {
        Screen open = Minecraft.getInstance().screen;
        // By package, because that is what "one of the hub's screens" means and
        // the alternative is a list that goes stale the next time one is added.
        return open != null && open.getClass().getPackageName()
            .equals(DuelHubScreen.class.getPackageName()) ? open : null;
    }

    /**
     * Goes back, and says whether it did.
     *
     * @return false when there is nowhere to go, which is the caller's cue to
     *         close normally
     */
    public static boolean back(Screen parent)
    {
        if(parent == null)
        {
            return false;
        }
        Minecraft.getInstance().setScreen(parent);
        return true;
    }
}
