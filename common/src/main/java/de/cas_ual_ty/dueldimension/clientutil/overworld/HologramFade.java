package de.cas_ual_ty.dueldimension.clientutil.overworld;

import java.util.HashMap;
import java.util.Map;

/**
 * How solid each monster is right now, on its way to how solid it should be.
 * <p>
 * A hologram's visibility is not constant: your own thin out and then vanish
 * while you look across the table, and the opponent's thin out while you read
 * their back row. Both are the right behaviour — a Blue-Eyes standing in front
 * of the card you are trying to click on is worse than no Blue-Eyes — but both
 * were applied the instant the condition changed, so glancing along a row made
 * the monsters above it snap in and out. A duel board is a thing you look
 * around, and looking around it should not make it flash.
 * <p>
 * <b>Keyed by zone, which is the only identity a monster has here.</b> A zone is
 * a square rather than a creature, so a monster summoned into a square its
 * predecessor just left inherits its fade — which is right: the fade describes
 * how visible that PLACE is, and the new arrival is in the same place under the
 * same eye.
 * <p>
 * Driven off the wall clock rather than ticks, because it is a property of what
 * is being looked at rather than of the duel, and it should stay smooth while
 * the game is paused or the duel is waiting.
 */
public final class HologramFade
{
    /**
     * How long a full fade takes.
     * <p>
     * Short enough not to lag behind a head turn, long enough to read as a fade
     * rather than as a fast pop.
     */
    private static final float MILLIS = 180F;

    private record Entry(float value, long at)
    {
    }

    private static final Map<Integer, Entry> FADES = new HashMap<>();

    private HologramFade()
    {
    }

    /**
     * Moves this zone's monster towards {@code target} and says where it got to.
     *
     * @param ref    the packed zone, as {@code EnginePrompt.zoneRef} builds it
     * @param target 0 for gone, 1 for solid, anything between for faint
     * @return the value to actually draw at, 0 to 1
     */
    public static float toward(int ref, float target, long now)
    {
        Entry entry = FADES.get(ref);
        if(entry == null)
        {
            // First sight of this square: start where it is meant to be, so a
            // duel does not open with every monster fading up out of nothing.
            FADES.put(ref, new Entry(target, now));
            return target;
        }
        long elapsed = Math.max(0L, now - entry.at());
        float step = elapsed / MILLIS;
        float value = entry.value();
        // Linear, not eased. These are half-second glances and an ease-in would
        // spend the first third of it looking like nothing had happened.
        if(value < target)
        {
            value = Math.min(target, value + step);
        }
        else
        {
            value = Math.max(target, value - step);
        }
        FADES.put(ref, new Entry(value, now));
        return value;
    }

    /** Forgets every square, so a new duel does not inherit the last one's. */
    public static void clear()
    {
        FADES.clear();
    }
}
