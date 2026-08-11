package de.cas_ual_ty.dueldimension.ocg;

import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the artwork a player chose for each copy in their deck onto the
 * engine's own card objects, as a Lua chunk run once before the duel starts.
 * <p>
 * <b>Why the engine has to hold this and not us.</b> ocgcore identifies a card
 * by (controller, location, sequence), and all three change constantly: a
 * shuffle permutes a pile and reports only the player it happened to
 * ({@code field.cpp}'s MSG_SHUFFLE_DECK is one byte), {@code reset_sequence}
 * silently renumbers every card behind one pulled out of a pile, and a zone
 * move that changes controller emits no MSG_MOVE at all. A Java-side tracker
 * keyed on any of that drifts within a turn. So the choice is stored where the
 * core already carries it for us: {@code card::cover}, a uint32 on the card
 * object that moves with the card, survives every shuffle, and is reported
 * back through QUERY_COVER.
 * <p>
 * {@code cover} is free for this. Grepping ygopro-core finds three mentions —
 * the field, its QUERY_COVER emission in {@code card::get_infos}, and the
 * {@code Card.Cover} accessor — and grepping the whole CardScripts tree finds
 * two writers, both in {@code proc_skill.lua}, the Speed Duel skill procedure,
 * which never runs under DUEL_MODE_MR5.
 * <p>
 * The chunk is handed to {@code OCG_LoadScript}, which loads <em>and calls</em>
 * it immediately ({@code interpreter.cpp}). That call is wrapped in
 * {@code ++no_action}, so any Lua reaching {@code check_action_permission}
 * would fail with "Action is not allowed here." Every function used below was
 * read and has no such check: {@code Duel.GetFieldGroup} (libduel.cpp),
 * {@code Card.Cover} and {@code Card.GetSequence} (libcard.cpp),
 * {@code Group.GetFirst}/{@code GetNext} (libgroup.cpp).
 */
public final class AltArtScript
{
    private AltArtScript()
    {
    }

    /**
     * The chunk that dresses one player's opening piles, or null when that
     * player chose nothing — which is the overwhelmingly common case, since
     * only about 122 of the 13,826 cards have a second artwork at all. A null
     * return means no script is generated, nothing is loaded, and the engine
     * does no work: a deck of printed artwork costs exactly one map lookup
     * per card at registration and not a byte more.
     *
     * @param player          absolute player id, 0 or 1
     * @param deckBySequence  core deck sequence to artwork index, non-zero
     *                        entries only
     * @param extraBySequence the same for the extra deck
     */
    public static String chunk(int player, Map<Integer, Integer> deckBySequence,
        Map<Integer, Integer> extraBySequence)
    {
        boolean anyDeck = deckBySequence != null && !deckBySequence.isEmpty();
        boolean anyExtra = extraBySequence != null && !extraBySequence.isEmpty();
        if(!anyDeck && !anyExtra)
        {
            return null;
        }
        StringBuilder chunk = new StringBuilder();
        if(anyDeck)
        {
            pile(chunk, player, "LOCATION_DECK", deckBySequence);
        }
        if(anyExtra)
        {
            pile(chunk, player, "LOCATION_EXTRA", extraBySequence);
        }
        return chunk.toString();
    }

    /**
     * One pile's loop. Everything is {@code local} so the chunk leaves nothing
     * behind in the duel's Lua globals for a card script to trip over, and the
     * sequences are walked in order purely so the generated text is stable and
     * a test can read it.
     */
    private static void pile(StringBuilder chunk, int player, String location, Map<Integer, Integer> bySequence)
    {
        chunk.append("local a={");
        boolean first = true;
        for(Map.Entry<Integer, Integer> entry : new TreeMap<>(bySequence).entrySet())
        {
            if(!first)
            {
                chunk.append(',');
            }
            first = false;
            chunk.append('[').append(entry.getKey().intValue()).append("]=").append(entry.getValue().intValue());
        }
        chunk.append("}\n");
        // GetFieldGroup rather than GetFieldCard per sequence: one call, and
        // filter_field_card inserts the whole pile (field.cpp), so no card is
        // missed. The group's iteration order is the core's own and is not
        // relied on -- each card is asked its own sequence.
        chunk.append("local g=Duel.GetFieldGroup(").append(player).append(',').append(location).append(",0)\n");
        chunk.append("local c=g:GetFirst()\n");
        chunk.append("while c do local v=a[c:GetSequence()] if v then c:Cover(v) end c=g:GetNext() end\n");
    }
}
