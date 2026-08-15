package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.card.properties.MonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.Races;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A card's facts, with the duel's opinion laid over the card's own.
 * <p>
 * The printed type is a fact about the card and the current one is a fact about
 * the duel, and the description has to say the second while the first is what
 * the database knows. Mimicking Man-Eater Bug is an Insect until it takes the
 * type of what it destroyed, and a description that keeps saying Insect is
 * describing a card that is no longer on the field.
 * <p>
 * Blue, because that is what this board already means by a value an effect has
 * raised -- the atk and def overlays use the same colour for the same reason,
 * and a player who has learnt it once should not have to learn it again for a
 * different row.
 */
public final class CardFacts
{
    private CardFacts()
    {
    }

    /** The same blue the stat overlays use for a value an effect has changed. */
    private static final int CHANGED = 0x66B2FF;

    /**
     * The card's facts, with its live race in place of the printed one when an
     * effect has moved it.
     *
     * @param liveRace the engine's race mask for this copy, or 0 when there is
     *                 none to be had -- a card off the field, a concealed one,
     *                 or a screen with no duel behind it
     */
    public static List<Component> of(Properties card, long liveRace)
    {
        List<Component> facts = new ArrayList<>();
        if(card == null)
        {
            return facts;
        }
        card.addFacts(facts);
        if(liveRace == 0L || !(card instanceof MonsterProperties monster))
        {
            return facts;
        }
        String printed = monster.species;
        String live = Races.name(liveRace);
        if(printed == null || live.isEmpty() || live.equals(printed))
        {
            return facts;
        }
        // The race opens the line the card builds -- "Insect / Effect" -- so
        // the change is made by swapping that opening rather than by adding a
        // row. One line that is true beats two lines that disagree, and the
        // rest of the line is the card's own words, kept.
        for(int line = 0; line < facts.size(); line++)
        {
            String flat = facts.get(line).getString();
            if(!flat.startsWith(printed))
            {
                continue;
            }
            facts.set(line, Component.literal(live)
                .withStyle(style -> style.withColor(CHANGED))
                .append(Component.literal(flat.substring(printed.length()))));
            break;
        }
        return facts;
    }

    /**
     * The race the engine currently gives the card in this zone, or 0.
     * <p>
     * Looked up from the board rather than carried on the thing that was
     * clicked. Every screen that shows a description already knows which square
     * it is describing, and the board is the only place the live answer exists
     * -- threading it through the two different records that name a square
     * would be two more places for the printed and the current to drift apart.
     */
    public static long liveRace(int controller, int location, int sequence)
    {
        BoardSnapshot board = DuelClientState.board;
        if(board == null || sequence < 0)
        {
            return 0L;
        }
        BoardSnapshot.Side side = controller == 0 ? board.self() : board.opponent();
        if(side == null)
        {
            return 0L;
        }
        List<BoardSnapshot.Slot> slots = switch(location)
        {
            case OcgConstants.LOCATION_MZONE -> side.monsters();
            case OcgConstants.LOCATION_SZONE -> side.spells();
            case OcgConstants.LOCATION_HAND -> side.hand();
            default -> null;
        };
        if(slots == null || sequence >= slots.size())
        {
            return 0L;
        }
        BoardSnapshot.Slot slot = slots.get(sequence);
        // faceDown is checked by the slot itself: a set monster's race is not
        // sent, and a zero here means exactly that nothing is known.
        return slot == null ? 0L : slot.race();
    }
}
