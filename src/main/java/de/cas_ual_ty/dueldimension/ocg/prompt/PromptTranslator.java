package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.msg.CardLocation;
import de.cas_ual_ty.dueldimension.ocg.msg.DeclarableFilter;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.msg.Responses;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the engine's prompts and a player's clicks.
 * <p>
 * Both directions live here on purpose: {@link #toPrompt} flattens a decoded
 * message into labelled options, and {@link #toResponse} turns what came back
 * into the exact bytes the engine expects. Keeping the pair adjacent means the
 * option a player sees at index N and the response encoded for index N can
 * never drift apart. Covers every player-facing prompt type the core emits.
 */
public class PromptTranslator
{
    /** System-string bases in strings.conf: attributes at 1010+bit, races at 1020+bit. */
    private static final int STRING_ATTRIBUTE_BASE = 1010;
    private static final int STRING_RACE_BASE = 1020;

    private final OcgDuel.CardProvider cards;
    private final DescriptionTable text;

    public PromptTranslator(OcgDuel.CardProvider cards, DescriptionTable text)
    {
        this.cards = cards;
        this.text = text;
    }

    // ---- engine -> screen ----

    public EnginePrompt toPrompt(DuelMessage message, BoardSnapshot field)
    {
        if(message instanceof DuelMessage.SelectIdleCmd idle)
        {
            // duelclient.cpp sets one cmdFlag bit per list; the option order
            // here must stay the order the response encoder expects.
            List<EnginePrompt.Option> options = new ArrayList<>();
            idle.summonable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_SUMMON, card, field)));
            idle.spSummonable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_SPSUMMON, card, field)));
            idle.repositionable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_REPOS, card, field)));
            idle.monsterSettable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_MSET, card, field)));
            idle.spellSettable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_SSET, card, field)));
            idle.activatable().forEach(card -> options.add(new EnginePrompt.Option(
                CardCommands.label(CardCommands.COMMAND_ACTIVATE, typeOf(card.code()), 0, text),
                text.describe(card.description()), card.code(), -1, 0,
                card.controller(), card.location(), card.sequence(), CardCommands.COMMAND_ACTIVATE)));
            if(idle.toBattle())
            {
                options.add(phaseOption("Battle Phase", CardCommands.PHASE_TO_BATTLE));
            }
            if(idle.toEnd())
            {
                options.add(phaseOption("End Turn", CardCommands.PHASE_END_TURN));
            }
            if(idle.canShuffle())
            {
                options.add(phaseOption("Shuffle hand", CardCommands.PHASE_SHUFFLE));
            }
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "", options, 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectBattleCmd battle)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            battle.activatable().forEach(card -> options.add(new EnginePrompt.Option(
                CardCommands.label(CardCommands.COMMAND_ACTIVATE, typeOf(card.code()), 0, text),
                text.describe(card.description()), card.code(), -1, 0,
                card.controller(), card.location(), card.sequence(), CardCommands.COMMAND_ACTIVATE)));
            battle.attackable().forEach(card -> options.add(new EnginePrompt.Option(
                CardCommands.label(CardCommands.COMMAND_ATTACK, typeOf(card.code()), 0, text),
                card.canDirect() ? "can attack directly" : "", card.code(), -1, 0,
                card.controller(), card.location(), card.sequence(), CardCommands.COMMAND_ATTACK)));
            if(battle.toMain2())
            {
                options.add(phaseOption("Main Phase 2", CardCommands.PHASE_TO_MAIN2));
            }
            if(battle.toEnd())
            {
                options.add(phaseOption("End Turn", CardCommands.PHASE_END_TURN));
            }
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "", options, 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectCard select)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            select.cards().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                where(card.loc()), card.code(),
                card.loc() == null ? -1 : card.loc().controller(),
                card.loc() == null ? 0 : card.loc().location(),
                card.loc() == null ? -1 : card.loc().sequence())));
            return new EnginePrompt(EnginePrompt.Kind.MULTI, selectTitle(select.min(), select.max()),
                options, select.min(), select.max(), select.cancelable(), field);
        }

        if(message instanceof DuelMessage.SelectChain chain)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            chain.chains().forEach(option -> options.add(new EnginePrompt.Option(
                "Chain: " + cardName(option.code()), text.describe(option.description()), option.code(),
                option.loc() == null ? -1 : option.loc().controller(),
                option.loc() == null ? 0 : option.loc().location(),
                option.loc() == null ? -1 : option.loc().sequence())));
            EnginePrompt window = new EnginePrompt(EnginePrompt.Kind.CHOOSE,
                chain.forced() ? "You must respond" : "Respond to the chain?",
                options, chain.forced() ? 1 : 0, 1, !chain.forced(), field);
            // A forced response is not a window anyone may skip: the core will
            // refuse an empty answer, so it is deliberately not marked.
            return chain.forced() ? window : window.asChainWindow();
        }

        if(message instanceof DuelMessage.SelectPosition position)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(int pos : positionsOf(position.positions()))
            {
                // The posture rides in `zone`: see EnginePrompt.Kind.POSITION.
                options.add(new EnginePrompt.Option(positionName(pos), cardName(position.code()),
                    position.code(), pos, 0, -1, 0, -1));
            }
            return new EnginePrompt(EnginePrompt.Kind.POSITION, "Choose a position",
                options, 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectPlace place)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(Responses.Place zone : allowedPlaces(place))
            {
                boolean monsterZone = zone.location() == OcgConstants.LOCATION_MZONE;
                boolean opponent = zone.player() != place.player();
                options.add(new EnginePrompt.Option(
                    (monsterZone ? "Monster zone " : "Spell/Trap zone ") + (zone.sequence() + 1),
                    opponent ? "opponent's side" : "your side", 0,
                    EnginePrompt.zoneRef(opponent, monsterZone, zone.sequence()), 0,
                    zone.player(), zone.location(), zone.sequence()));
            }
            return new EnginePrompt(EnginePrompt.Kind.PLACES, "Choose a zone", options,
                place.count(), place.count(), false, field);
        }

        if(message instanceof DuelMessage.SelectOption option)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(long description : option.options())
            {
                options.add(new EnginePrompt.Option(text.describe(description)));
            }
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Choose an effect", options, 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectEffectYesNo effect)
        {
            String question = formatEffectQuestion(text.describe(effect.description()),
                cardName(effect.code()), promptLocation(effect.loc()));
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, question,
                List.of(new EnginePrompt.Option("Yes", cardName(effect.code()), effect.code()),
                    new EnginePrompt.Option("No")), 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectYesNo yesNo)
        {
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, text.describe(yesNo.description()),
                List.of(new EnginePrompt.Option("Yes"), new EnginePrompt.Option("No")), 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectTribute tribute)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            tribute.cards().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "counts as " + card.releaseParam(), card.code(),
                card.controller(), card.location(), card.sequence())));
            return new EnginePrompt(EnginePrompt.Kind.MULTI, "Select tributes", options,
                tribute.min(), tribute.max(), tribute.cancelable(), field);
        }

        if(message instanceof DuelMessage.SelectSum sum)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            sum.selectable().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "value " + card.primary() + (card.alternate() != 0 ? " or " + card.alternate() : ""), card.code(),
                card.loc() == null ? -1 : card.loc().controller(),
                card.loc() == null ? 0 : card.loc().location(),
                card.loc() == null ? -1 : card.loc().sequence())));
            return new EnginePrompt(EnginePrompt.Kind.MULTI, "Select cards totalling " + sum.acc(), options,
                Math.max(sum.min(), 1), Math.max(sum.max(), options.size()), false, field);
        }

        if(message instanceof DuelMessage.SelectUnselectCard unselect)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            unselect.selectable().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                where(card.loc()), card.code(),
                card.loc() == null ? -1 : card.loc().controller(),
                card.loc() == null ? 0 : card.loc().location(),
                card.loc() == null ? -1 : card.loc().sequence())));
            unselect.unselectable().forEach(card -> options.add(
                new EnginePrompt.Option("Deselect " + cardName(card.code()), where(card.loc()), card.code(),
                    card.loc() == null ? -1 : card.loc().controller(),
                    card.loc() == null ? 0 : card.loc().location(),
                    card.loc() == null ? -1 : card.loc().sequence())));
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Select a card", options, 1, 1,
                unselect.finishable() || unselect.cancelable(), field);
        }

        if(message instanceof DuelMessage.SortCard sort)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            sort.cards().forEach(card -> options.add(
                new EnginePrompt.Option(cardName(card.code()), where(card.controller(),
                    card.location(), card.sequence()), card.code(),
                    card.controller(), card.location(), card.sequence())));
            return new EnginePrompt(EnginePrompt.Kind.SORT,
                sort.chain() ? "Order the chain" : "Order the cards", options,
                options.size(), options.size(), true, field);
        }

        if(message instanceof DuelMessage.SelectCounter counter)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            counter.cards().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "has " + card.counters(), card.code(), -1, card.counters(),
                card.controller(), card.location(), card.sequence())));
            return new EnginePrompt(EnginePrompt.Kind.COUNTERS,
                "Remove " + counter.count() + " " + text.counterName(counter.counterType()) + " counter(s)",
                options, counter.count(), counter.count(), false, field);
        }

        if(message instanceof DuelMessage.AnnounceBits announce)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(int bit = 0; bit < 64; bit++)
            {
                if((announce.available() >>> bit & 1) != 0)
                {
                    options.add(new EnginePrompt.Option(text.systemString(
                        (announce.race() ? STRING_RACE_BASE : STRING_ATTRIBUTE_BASE) + bit)));
                }
            }
            return new EnginePrompt(EnginePrompt.Kind.MULTI,
                "Declare " + announce.count() + " " + (announce.race() ? "Type(s)" : "Attribute(s)"),
                options, announce.count(), announce.count(), false, field);
        }

        if(message instanceof DuelMessage.AnnounceCard)
        {
            // No option list: the client searches its own card database by
            // name and answers with a code, which the server re-validates.
            return new EnginePrompt(EnginePrompt.Kind.DECLARE_CARD, "Declare a card name",
                List.of(), 1, 1, false, field);
        }

        if(message instanceof DuelMessage.AnnounceNumber announce)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(long value : announce.options())
            {
                options.add(new EnginePrompt.Option(Long.toString(value)));
            }
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Declare a number", options, 1, 1, false, field);
        }

        if(message instanceof DuelMessage.RockPaperScissors)
        {
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Rock, paper, scissors",
                List.of(new EnginePrompt.Option("Rock"), new EnginePrompt.Option("Paper"),
                    new EnginePrompt.Option("Scissors")), 1, 1, false, field);
        }

        return null;
    }

    /**
     * Prompts with nothing to decide (an empty chain window, say) are answered
     * automatically rather than opening a screen with no buttons.
     */
    public byte[] autoAnswer(DuelMessage message)
    {
        return autoAnswer(message, ChainPreference.DEFAULT);
    }

    public byte[] autoAnswer(DuelMessage message, ChainPreference chainPreference)
    {
        if(message instanceof DuelMessage.SelectChain chain)
        {
            // duelclient.cpp decides this before ever showing the window.
            if(chainPreference.declinesWithoutAsking(chain))
            {
                return Responses.chainDecline();
            }
            if(chain.chains().isEmpty())
            {
                return chain.forced() ? null : Responses.chainDecline();
            }
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
     * @param chosen       option indices; for COUNTERS, the amount per option;
     *                     for SORT, the click order per option; empty = cancel
     * @param declaredCode DECLARE_CARD answer, 0 otherwise
     * @return the engine response, or null if the choice is invalid (the
     *         prompt should be re-sent, not aborted)
     */
    public byte[] toResponse(DuelMessage message, int[] chosen, int declaredCode)
    {
        boolean cancelled = chosen.length == 0;
        int first = cancelled ? -1 : chosen[0];

        if(message instanceof DuelMessage.SelectIdleCmd idle)
        {
            return idleResponse(idle, first);
        }
        if(message instanceof DuelMessage.SelectBattleCmd battle)
        {
            return battleResponse(battle, first);
        }
        if(message instanceof DuelMessage.SelectCard)
        {
            return cancelled ? Responses.selectCardsCancel() : Responses.selectCards(chosen);
        }
        if(message instanceof DuelMessage.SelectChain)
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
        if(message instanceof DuelMessage.SortCard sort)
        {
            if(cancelled)
            {
                return Responses.sortDecline();
            }
            // chosen[i] = click order of option i; any permutation is legal.
            if(chosen.length != sort.cards().size())
            {
                return null;
            }
            boolean[] used = new boolean[chosen.length];
            for(int value : chosen)
            {
                if(value < 0 || value >= chosen.length || used[value])
                {
                    return null;
                }
                used[value] = true;
            }
            return Responses.sort(chosen);
        }
        if(message instanceof DuelMessage.SelectCounter counter)
        {
            if(chosen.length != counter.cards().size())
            {
                return null;
            }
            int total = 0;
            for(int i = 0; i < chosen.length; i++)
            {
                if(chosen[i] < 0 || chosen[i] > counter.cards().get(i).counters())
                {
                    return null;
                }
                total += chosen[i];
            }
            return total == counter.count() ? Responses.counters(chosen) : null;
        }
        if(message instanceof DuelMessage.AnnounceBits announce)
        {
            List<Integer> bits = new ArrayList<>();
            for(int bit = 0; bit < 64; bit++)
            {
                if((announce.available() >>> bit & 1) != 0)
                {
                    bits.add(bit);
                }
            }
            long mask = 0;
            for(int index : chosen)
            {
                if(index < 0 || index >= bits.size())
                {
                    return null;
                }
                mask |= 1L << bits.get(index);
            }
            if(Long.bitCount(mask) != announce.count())
            {
                return null;
            }
            return announce.race() ? Responses.announceRace(mask) : Responses.announceAttribute((int)mask);
        }
        if(message instanceof DuelMessage.AnnounceCard announce)
        {
            OcgCard card = cards.get(declaredCode);
            if(card == null || !DeclarableFilter.isDeclarable(card, announce.filter()))
            {
                return null; // client suggested something illegal: re-prompt
            }
            return Responses.announceCard(declaredCode);
        }
        if(message instanceof DuelMessage.AnnounceNumber announce)
        {
            return first >= 0 && first < announce.options().length ? Responses.announceNumber(first) : null;
        }
        if(message instanceof DuelMessage.RockPaperScissors)
        {
            return first >= 0 && first < 3 ? Responses.rockPaperScissors(first + 1) : null;
        }
        return null;
    }

    private byte[] idleResponse(DuelMessage.SelectIdleCmd idle, int index)
    {
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

    private byte[] battleResponse(DuelMessage.SelectBattleCmd battle, int index)
    {
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

    /**
     * The caption EDOPro puts on a card selection, from duelclient.cpp:
     * <pre>
     * stHintMsg->setText(format(L"{}({}-{})",
     *     GetDesc(select_hint ? select_hint : 531), select_min, select_max));
     * </pre>
     * {@code select_hint} is whatever the core last sent as HINT_SELECTMSG, so
     * a prompt says what it is actually for -- "select a card to discard" --
     * instead of a generic line. System string 531 is the fallback the
     * reference uses when the core sent no hint.
     */
    private String selectTitle(int min, int max)
    {
        long hintId = takeSelectHint();
        String hint = hintId != 0 ? text.describe(hintId) : text.systemString(DEFAULT_SELECT_HINT);
        return hint + "(" + min + "-" + max + ")";
    }

    /** strings.conf 531, the reference's default selection caption. */
    private static final int DEFAULT_SELECT_HINT = 531;

    /**
     * The core's HINT_SELECTMSG for the selection about to be asked for.
     * Cleared once used, as duelclient.cpp clears select_hint at the top of
     * each select handler.
     */
    private long selectHint;

    public void noteSelectHint(long description)
    {
        this.selectHint = description;
    }

    private long takeSelectHint()
    {
        long hint = selectHint;
        selectHint = 0;
        return hint;
    }

    /**
     * An idle-command option carrying its COMMAND_* bit and the caption
     * ShowMenu would give it (which for REPOS and SSET depends on the card's
     * current position and type).
     */
    private EnginePrompt.Option commandOption(int command, DuelMessage.IdleOption idle, BoardSnapshot field)
    {
        OcgCard card = cards.get(idle.code());
        String detail = card == null ? "" : card.attack() + " ATK / " + card.defense() + " DEF";
        int position = positionAt(field, idle.controller(), idle.location(), idle.sequence());
        return new EnginePrompt.Option(
            CardCommands.label(command, card == null ? 0 : card.type(), position, text),
            detail, idle.code(), -1, 0,
            idle.controller(), idle.location(), idle.sequence(), command);
    }

    private static EnginePrompt.Option phaseOption(String label, int command)
    {
        return new EnginePrompt.Option(label, "", 0, -1, 0, -1, 0, -1, command);
    }

    private int typeOf(int code)
    {
        OcgCard card = cards.get(code);
        return card == null ? 0 : card.type();
    }

    /** The POS_* of a card on the field, for the reposition caption. */
    private static int positionAt(BoardSnapshot field, int controller, int location, int sequence)
    {
        if(field == null)
        {
            return 0;
        }
        BoardSnapshot.Side side = controller == 0 ? field.self() : field.opponent();
        List<BoardSnapshot.Slot> slots = location == OcgConstants.LOCATION_MZONE ? side.monsters()
            : location == OcgConstants.LOCATION_SZONE ? side.spells() : List.of();
        if(sequence < 0 || sequence >= slots.size())
        {
            return 0;
        }
        BoardSnapshot.Slot slot = slots.get(sequence);
        if(!slot.present())
        {
            return 0;
        }
        if(slot.faceDown())
        {
            return OcgConstants.POS_FACEDOWN;
        }
        return slot.defence() ? OcgConstants.POS_FACEUP_DEFENSE : OcgConstants.POS_FACEUP_ATTACK;
    }

    private String cardName(int code)
    {
        String name = text.cardName(code);
        return name.startsWith("?") ? "Card " + code : name;
    }

    private static String where(int controller, int location, int sequence)
    {
        return where(new CardLocation(controller, location, sequence, 0));
    }

    private static String where(CardLocation location)
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

    /** Location wording used by strings.conf's {@code [%ls]} prompt slot. */
    private static String promptLocation(CardLocation location)
    {
        if(location == null)
        {
            return "Unknown location";
        }
        return switch(location.location())
        {
            case OcgConstants.LOCATION_DECK -> "Deck";
            case OcgConstants.LOCATION_HAND -> "Hand";
            case OcgConstants.LOCATION_MZONE -> "Monster Zone " + (location.sequence() + 1);
            case OcgConstants.LOCATION_SZONE -> "Spell/Trap Zone " + (location.sequence() + 1);
            case OcgConstants.LOCATION_GRAVE -> "Graveyard";
            case OcgConstants.LOCATION_REMOVED -> "Banished";
            case OcgConstants.LOCATION_EXTRA -> "Extra Deck";
            default -> "Zone " + location.location();
        };
    }

    /**
     * EDOPro's localized system strings use C++ wide-string placeholders.
     * MSG_SELECT_EFFECTYN supplies exactly the two values required by those
     * templates: the effect card and its source location.
     */
    static String formatEffectQuestion(String template, String cardName, String location)
    {
        return replaceFirstLiteral(replaceFirstLiteral(template, "%ls", cardName),
            "%ls", location);
    }

    private static String replaceFirstLiteral(String text, String target, String replacement)
    {
        int at = text.indexOf(target);
        return at < 0 ? text : text.substring(0, at) + replacement
            + text.substring(at + target.length());
    }
}
