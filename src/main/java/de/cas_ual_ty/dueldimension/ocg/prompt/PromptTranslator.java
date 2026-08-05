package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.msg.Responses;
import de.cas_ual_ty.dueldimension.ocg.query.BoardState;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the engine's prompts and a player's clicks.
 * <p>
 * Both directions live here on purpose: {@link #toPrompt} flattens a decoded
 * message into labelled options, and {@link #toResponse} turns the indices
 * that come back into the exact bytes the engine expects. Keeping the pair
 * adjacent means the option a player sees at index N and the response encoded
 * for index N can never drift apart.
 */
public class PromptTranslator
{
    private final OcgDuel.CardProvider cards;
    private final DescriptionTable text;

    public PromptTranslator(OcgDuel.CardProvider cards, DescriptionTable text)
    {
        this.cards = cards;
        this.text = text;
    }

    // ---- engine -> screen ----

    public EnginePrompt toPrompt(DuelMessage message, BoardState board)
    {
        List<String> summary = summarise(board);

        if(message instanceof DuelMessage.SelectIdleCmd idle)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            idle.summonable().forEach(card -> options.add(cardOption("Summon", card.code())));
            idle.spSummonable().forEach(card -> options.add(cardOption("Special Summon", card.code())));
            idle.repositionable().forEach(card -> options.add(cardOption("Change position", card.code())));
            idle.monsterSettable().forEach(card -> options.add(cardOption("Set", card.code())));
            idle.spellSettable().forEach(card -> options.add(cardOption("Set", card.code())));
            idle.activatable().forEach(card -> options.add(
                new EnginePrompt.Option("Activate: " + cardName(card.code()),
                    text.describe(card.description()), card.code())));
            if(idle.toBattle())
            {
                options.add(new EnginePrompt.Option("Go to Battle Phase"));
            }
            if(idle.toEnd())
            {
                options.add(new EnginePrompt.Option("End Turn"));
            }
            if(idle.canShuffle())
            {
                options.add(new EnginePrompt.Option("Shuffle hand"));
            }
            return new EnginePrompt("Main Phase", options, 1, 1, false, summary);
        }

        if(message instanceof DuelMessage.SelectBattleCmd battle)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            battle.activatable().forEach(card -> options.add(
                new EnginePrompt.Option("Activate: " + cardName(card.code()),
                    text.describe(card.description()), card.code())));
            battle.attackable().forEach(card -> options.add(
                new EnginePrompt.Option("Attack with " + cardName(card.code()),
                    card.canDirect() ? "can attack directly" : "", card.code())));
            if(battle.toMain2())
            {
                options.add(new EnginePrompt.Option("Go to Main Phase 2"));
            }
            if(battle.toEnd())
            {
                options.add(new EnginePrompt.Option("End Turn"));
            }
            return new EnginePrompt("Battle Phase", options, 1, 1, false, summary);
        }

        if(message instanceof DuelMessage.SelectCard select)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            select.cards().forEach(card -> options.add(
                new EnginePrompt.Option(cardName(card.code()), where(card.loc()), card.code())));
            String title = select.min() == select.max()
                ? "Select " + select.min() + " card" + (select.min() == 1 ? "" : "s")
                : "Select " + select.min() + " to " + select.max() + " cards";
            return new EnginePrompt(title, options, select.min(), select.max(), select.cancelable(), summary);
        }

        if(message instanceof DuelMessage.SelectChain chain)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            chain.chains().forEach(option -> options.add(
                new EnginePrompt.Option("Chain: " + cardName(option.code()),
                    text.describe(option.description()), option.code())));
            return new EnginePrompt(chain.forced() ? "You must respond" : "Respond to the chain?",
                options, chain.forced() ? 1 : 0, 1, !chain.forced(), summary);
        }

        if(message instanceof DuelMessage.SelectPosition position)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(int pos : positionsOf(position.positions()))
            {
                options.add(new EnginePrompt.Option(positionName(pos), cardName(position.code()), position.code()));
            }
            return new EnginePrompt("Choose a position", options, 1, 1, false, summary);
        }

        if(message instanceof DuelMessage.SelectPlace place)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(Responses.Place zone : allowedPlaces(place))
            {
                options.add(new EnginePrompt.Option(
                    (zone.location() == OcgConstants.LOCATION_MZONE ? "Monster zone " : "Spell/Trap zone ")
                        + (zone.sequence() + 1),
                    zone.player() == place.player() ? "your side" : "opponent's side", 0));
            }
            return new EnginePrompt("Choose a zone", options, place.count(), place.count(), false, summary);
        }

        if(message instanceof DuelMessage.SelectOption option)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(long description : option.options())
            {
                options.add(new EnginePrompt.Option(text.describe(description)));
            }
            return new EnginePrompt("Choose an effect", options, 1, 1, false, summary);
        }

        if(message instanceof DuelMessage.SelectEffectYesNo effect)
        {
            return new EnginePrompt(text.describe(effect.description()),
                List.of(new EnginePrompt.Option("Yes", cardName(effect.code()), effect.code()),
                    new EnginePrompt.Option("No")), 1, 1, false, summary);
        }

        if(message instanceof DuelMessage.SelectYesNo yesNo)
        {
            return new EnginePrompt(text.describe(yesNo.description()),
                List.of(new EnginePrompt.Option("Yes"), new EnginePrompt.Option("No")), 1, 1, false, summary);
        }

        if(message instanceof DuelMessage.SelectTribute tribute)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            tribute.cards().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "counts as " + card.releaseParam(), card.code())));
            return new EnginePrompt("Select tributes", options, tribute.min(), tribute.max(),
                tribute.cancelable(), summary);
        }

        if(message instanceof DuelMessage.SelectSum sum)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            sum.selectable().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "value " + card.primary() + (card.alternate() != 0 ? " or " + card.alternate() : ""),
                card.code())));
            return new EnginePrompt("Select cards totalling " + sum.acc(), options,
                Math.max(sum.min(), 1), Math.max(sum.max(), options.size()), false, summary);
        }

        if(message instanceof DuelMessage.SelectUnselectCard unselect)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            unselect.selectable().forEach(card -> options.add(
                new EnginePrompt.Option(cardName(card.code()), where(card.loc()), card.code())));
            unselect.unselectable().forEach(card -> options.add(
                new EnginePrompt.Option("Deselect " + cardName(card.code()), where(card.loc()), card.code())));
            return new EnginePrompt("Select a card", options, 1, 1,
                unselect.finishable() || unselect.cancelable(), summary);
        }

        return null; // prompt types the screen cannot present yet
    }

    /**
     * Some prompts reach a player with nothing to decide — most often a chain
     * window where they hold no activatable card. Showing an empty screen and
     * demanding a click would be nonsense, so these are answered for them.
     *
     * @return the response to send automatically, or null if the player really
     *         does need to choose
     */
    public byte[] autoAnswer(DuelMessage message)
    {
        if(message instanceof DuelMessage.SelectChain chain && chain.chains().isEmpty())
        {
            return chain.forced() ? null : Responses.chainDecline();
        }
        if(message instanceof DuelMessage.SelectUnselectCard unselect && unselect.optionCount() == 0)
        {
            return unselect.finishable() || unselect.cancelable() ? Responses.selectUnselectFinish() : null;
        }
        if(message instanceof DuelMessage.SelectCard select && select.cards().isEmpty() && select.min() == 0)
        {
            return Responses.selectCardsCancel();
        }
        return null;
    }

    // ---- screen -> engine ----

    /**
     * @param chosen indices into the prompt's option list, or empty for cancel
     * @return the engine response, or null if the choice makes no sense
     */
    public byte[] toResponse(DuelMessage message, int[] chosen)
    {
        boolean cancelled = chosen.length == 0;
        int first = cancelled ? -1 : chosen[0];

        if(message instanceof DuelMessage.SelectIdleCmd idle)
        {
            int index = first;
            int size;
            if(index < (size = idle.summonable().size()))
            {
                return Responses.idleSummon(index);
            }
            index -= size;
            if(index < (size = idle.spSummonable().size()))
            {
                return Responses.idleSpSummon(index);
            }
            index -= size;
            if(index < (size = idle.repositionable().size()))
            {
                return Responses.idleReposition(index);
            }
            index -= size;
            if(index < (size = idle.monsterSettable().size()))
            {
                return Responses.idleMonsterSet(index);
            }
            index -= size;
            if(index < (size = idle.spellSettable().size()))
            {
                return Responses.idleSpellSet(index);
            }
            index -= size;
            if(index < (size = idle.activatable().size()))
            {
                return Responses.idleActivate(index);
            }
            index -= size;
            if(idle.toBattle() && index-- == 0)
            {
                return Responses.idleToBattle();
            }
            if(idle.toEnd() && index-- == 0)
            {
                return Responses.idleToEnd();
            }
            if(idle.canShuffle() && index == 0)
            {
                return Responses.idleShuffleHand();
            }
            return null;
        }

        if(message instanceof DuelMessage.SelectBattleCmd battle)
        {
            int index = first;
            int size;
            if(index < (size = battle.activatable().size()))
            {
                return Responses.battleActivate(index);
            }
            index -= size;
            if(index < (size = battle.attackable().size()))
            {
                return Responses.battleAttack(index);
            }
            index -= size;
            if(battle.toMain2() && index-- == 0)
            {
                return Responses.battleToMain2();
            }
            if(battle.toEnd() && index == 0)
            {
                return Responses.battleToEnd();
            }
            return null;
        }

        if(message instanceof DuelMessage.SelectCard select)
        {
            return cancelled ? Responses.selectCardsCancel() : Responses.selectCards(chosen);
        }

        if(message instanceof DuelMessage.SelectChain chain)
        {
            return cancelled ? Responses.chainDecline() : Responses.chain(first);
        }

        if(message instanceof DuelMessage.SelectPosition position)
        {
            int[] positions = positionsOf(position.positions());
            return first >= 0 && first < positions.length ? Responses.position(positions[first]) : null;
        }

        if(message instanceof DuelMessage.SelectPlace place)
        {
            List<Responses.Place> allowed = allowedPlaces(place);
            List<Responses.Place> picked = new ArrayList<>();
            for(int index : chosen)
            {
                if(index < 0 || index >= allowed.size())
                {
                    return null;
                }
                picked.add(allowed.get(index));
            }
            return picked.isEmpty() ? null : Responses.places(picked.toArray(Responses.Place[]::new));
        }

        if(message instanceof DuelMessage.SelectOption)
        {
            return Responses.option(first);
        }

        if(message instanceof DuelMessage.SelectEffectYesNo || message instanceof DuelMessage.SelectYesNo)
        {
            return first == 0 ? Responses.yes() : Responses.no();
        }

        if(message instanceof DuelMessage.SelectTribute)
        {
            return cancelled ? Responses.selectCardsCancel() : Responses.selectTribute(chosen);
        }

        if(message instanceof DuelMessage.SelectSum)
        {
            return cancelled ? null : Responses.selectSum(chosen);
        }

        if(message instanceof DuelMessage.SelectUnselectCard)
        {
            return cancelled ? Responses.selectUnselectFinish() : Responses.selectUnselect(first);
        }

        return null;
    }

    // ---- helpers ----

    private List<Responses.Place> allowedPlaces(DuelMessage.SelectPlace place)
    {
        List<Responses.Place> allowed = new ArrayList<>();
        for(int bit = 0; bit < 32; bit++)
        {
            if((place.forbiddenMask() >> bit & 1) != 0)
            {
                continue; // a set bit marks a zone that may NOT be used
            }
            int half = bit & 15;
            boolean monsterZone = half < 8;
            int sequence = half & 7;
            if(monsterZone && sequence > 6)
            {
                continue;
            }
            allowed.add(new Responses.Place(bit < 16 ? place.player() : 1 - place.player(),
                monsterZone ? OcgConstants.LOCATION_MZONE : OcgConstants.LOCATION_SZONE, sequence));
        }
        return allowed;
    }

    private static int[] positionsOf(int mask)
    {
        List<Integer> found = new ArrayList<>(4);
        for(int position : new int[] {OcgConstants.POS_FACEUP_ATTACK, OcgConstants.POS_FACEDOWN_ATTACK,
            OcgConstants.POS_FACEUP_DEFENSE, OcgConstants.POS_FACEDOWN_DEFENSE})
        {
            if((mask & position) != 0)
            {
                found.add(position);
            }
        }
        return found.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String positionName(int position)
    {
        return switch(position)
        {
            case OcgConstants.POS_FACEUP_ATTACK -> "Face-up Attack";
            case OcgConstants.POS_FACEDOWN_ATTACK -> "Face-down Attack";
            case OcgConstants.POS_FACEUP_DEFENSE -> "Face-up Defence";
            case OcgConstants.POS_FACEDOWN_DEFENSE -> "Face-down Defence";
            default -> "Position " + position;
        };
    }

    private EnginePrompt.Option cardOption(String verb, int code)
    {
        OcgCard card = cards.get(code);
        String detail = card == null ? "" : card.attack() + " ATK / " + card.defense() + " DEF";
        return new EnginePrompt.Option(verb + ": " + cardName(code), detail, code);
    }

    private String cardName(int code)
    {
        String name = text.cardName(code);
        return name.startsWith("?") ? "Card " + code : name;
    }

    private static String where(de.cas_ual_ty.dueldimension.ocg.msg.CardLocation location)
    {
        if(location == null)
        {
            return "";
        }
        String zone = switch(location.location())
        {
            case OcgConstants.LOCATION_DECK -> "Deck";
            case OcgConstants.LOCATION_HAND -> "Hand";
            case OcgConstants.LOCATION_MZONE -> "Monster zone";
            case OcgConstants.LOCATION_SZONE -> "Spell/Trap zone";
            case OcgConstants.LOCATION_GRAVE -> "Graveyard";
            case OcgConstants.LOCATION_REMOVED -> "Banished";
            case OcgConstants.LOCATION_EXTRA -> "Extra Deck";
            default -> "Zone " + location.location();
        };
        return zone + " " + (location.sequence() + 1);
    }

    private List<String> summarise(BoardState board)
    {
        List<String> lines = new ArrayList<>();
        if(board == null)
        {
            return lines;
        }
        lines.add("You: " + board.self().lifePoints() + " LP   |   Opponent: "
            + board.opponent().lifePoints() + " LP");
        lines.add("Your field: " + describeZones(board.self().monsters(), board.self().spells()));
        lines.add("Their field: " + describeZones(board.opponent().monsters(), board.opponent().spells()));
        lines.add("Hand: " + board.self().hand().size() + "   Deck: " + board.self().deckCount()
            + "   Grave: " + board.self().grave().size());
        return lines;
    }

    private String describeZones(List<CardView> monsters, List<CardView> spells)
    {
        List<String> parts = new ArrayList<>();
        for(CardView card : monsters)
        {
            if(card == null)
            {
                continue;
            }
            parts.add(card.hidden() ? "[face-down]"
                : cardName(card.code()) + " (" + card.attack() + "/" + card.defense() + ")");
        }
        long backrow = spells.stream().filter(java.util.Objects::nonNull).count();
        if(backrow > 0)
        {
            parts.add(backrow + " spell/trap");
        }
        return parts.isEmpty() ? "empty" : String.join(", ", parts);
    }
}
