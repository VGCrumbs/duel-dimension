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

    /**
     * Where the artwork of a card the engine has just named comes from.
     * <p>
     * Passed per call and never stored, because ONE translator serves BOTH
     * seats (DuelistDuels builds a single instance and hands it to both
     * response sources). A lookup kept as a field would dress seat 1's prompts
     * with seat 0's entitlement.
     */
    @FunctionalInterface
    public interface CoverLookup
    {
        /** @see de.cas_ual_ty.dueldimension.ocg.query.BoardObserver#coverOf */
        int coverOf(int controller, int location, int sequence, int expectedCode);
    }

    /**
     * Without a lookup and for seat 0: bots and tests, which draw nothing and
     * for which the core's numbering and the viewer's already coincide.
     */
    public EnginePrompt toPrompt(DuelMessage message, BoardSnapshot field)
    {
        return toPrompt(message, field, null, 0);
    }

    /**
     * @param covers where a named card's artwork comes from, or null for the
     *               printed one everywhere. Every card option is dressed from
     *               here rather than left for the client to work out: the deck
     *               is not in {@code field} at all -- it is only counted -- so
     *               a card picked out of it has nothing on the client side to
     *               read an artwork from.
     * @param viewer the seat this prompt is being built for, in the CORE's
     *               numbering. Every controller an option carries is rebased
     *               against it, because the client's board is relative -- it
     *               draws controller 0 nearest and hit-tests clicks in those
     *               terms (BoardRenderer.layoutZone). Handed the core's
     *               absolute byte, seat 1 found no option matching any card it
     *               clicked and could do nothing but end its turn. Seat 0 never
     *               showed it: for that seat the two numberings are the same.
     *               <p>
     *               It is a parameter and not a field for the same reason
     *               {@code covers} is: ONE translator may serve BOTH seats.
     */
    public EnginePrompt toPrompt(DuelMessage message, BoardSnapshot field, CoverLookup covers, int viewer)
    {
        if(message instanceof DuelMessage.SelectIdleCmd idle)
        {
            // duelclient.cpp sets one cmdFlag bit per list; the option order
            // here must stay the order the response encoder expects.
            List<EnginePrompt.Option> options = new ArrayList<>();
            idle.summonable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_SUMMON, card, field, covers, viewer)));
            idle.spSummonable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_SPSUMMON, card, field, covers, viewer)));
            idle.repositionable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_REPOS, card, field, covers, viewer)));
            idle.monsterSettable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_MSET, card, field, covers, viewer)));
            idle.spellSettable().forEach(card ->
                options.add(commandOption(CardCommands.COMMAND_SSET, card, field, covers, viewer)));
            idle.activatable().forEach(card -> options.add(new EnginePrompt.Option(
                CardCommands.label(CardCommands.COMMAND_ACTIVATE, typeOf(card.code()), 0, text),
                text.describe(card.description()), card.code(), -1, 0,
                side(card.controller(), viewer), card.location(), card.sequence(),
                CardCommands.COMMAND_ACTIVATE,
                // The lookup queries the CORE, so it keeps the core's own
                // controller; only what the client will hit-test is rebased.
                artAt(covers, card.controller(), card.location(), card.sequence(), card.code()))));
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
                side(card.controller(), viewer), card.location(), card.sequence(),
                CardCommands.COMMAND_ACTIVATE,
                artAt(covers, card.controller(), card.location(), card.sequence(), card.code()))));
            battle.attackable().forEach(card -> options.add(new EnginePrompt.Option(
                CardCommands.label(CardCommands.COMMAND_ATTACK, typeOf(card.code()), 0, text),
                card.canDirect() ? "can attack directly" : "", card.code(), -1, 0,
                side(card.controller(), viewer), card.location(), card.sequence(),
                CardCommands.COMMAND_ATTACK,
                artAt(covers, card.controller(), card.location(), card.sequence(), card.code()))));
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
                side(card.loc(), viewer),
                card.loc() == null ? 0 : card.loc().location(),
                card.loc() == null ? -1 : card.loc().sequence(),
                artAt(covers, card.loc(), card.code()))));
            return new EnginePrompt(EnginePrompt.Kind.MULTI, selectTitle(select.min(), select.max()),
                options, select.min(), select.max(), select.cancelable(), field);
        }

        if(message instanceof DuelMessage.SelectChain chain)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            chain.chains().forEach(option -> options.add(new EnginePrompt.Option(
                "Chain: " + cardName(option.code()), text.describe(option.description()), option.code(),
                side(option.loc(), viewer),
                option.loc() == null ? 0 : option.loc().location(),
                option.loc() == null ? -1 : option.loc().sequence(),
                artAt(covers, option.loc(), option.code()))));
            EnginePrompt window = new EnginePrompt(EnginePrompt.Kind.CHOOSE,
                chain.forced() ? "You must respond" : "Respond to the chain?",
                options, chain.forced() ? 1 : 0, 1, !chain.forced(), field);
            // Both are marked now. The flag says "these options activate
            // something", which is what decides whether a click needs a
            // confirmation -- and a forced chain activates just as hard as an
            // optional one. Skippability is asked separately and everywhere it
            // matters: both existing readers test cancelable() alongside this,
            // and a forced chain is not cancelable, so nothing that used this
            // to mean "may be passed" changes its answer.
            return window.asChainWindow();
        }

        if(message instanceof DuelMessage.SelectPosition position)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            for(int pos : positionsOf(position.positions()))
            {
                // The posture rides in `zone`: see EnginePrompt.Kind.POSITION.
                //
                // The artwork stays printed here, alone among the card prompts,
                // because MSG_SELECT_POSITION carries no location: the core
                // writes a code and a position mask and nothing else, so there
                // is no copy to ask the engine about. Guessing which one is
                // being summoned would be exactly the guesswork this codebase
                // does not do.
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
                // Which half of the table this zone is on, to the seat being
                // asked -- the same question `side` answers everywhere else.
                // The absolute player survives untouched in allowedPlaces,
                // which is what the response is encoded from.
                int controller = side(zone.player(), viewer);
                boolean opponent = controller == 1;
                options.add(new EnginePrompt.Option(
                    (monsterZone ? "Monster zone " : "Spell/Trap zone ") + (zone.sequence() + 1),
                    opponent ? "opponent's side" : "your side", 0,
                    EnginePrompt.zoneRef(opponent, monsterZone, zone.sequence()), 0,
                    controller, zone.location(), zone.sequence()));
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
            // The "Yes" row is the effect's own card and the message says where
            // it sits, so the artwork can be looked up -- but the option keeps
            // its slotless shape on purpose. hasSlot() is what sends an option
            // to the field instead of the bottom strip (EngineDuelScreen's
            // buildBottomStrip), and a Yes/No the player has to find on the
            // board -- or worse, inside a deck -- is not answerable at all.
            // Only the artwork travels; where the card is stays the question's
            // own business.
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, question,
                List.of(new EnginePrompt.Option("Yes", cardName(effect.code()), effect.code(),
                        -1, 0, -1, 0, -1, 0, artAt(covers, effect.loc(), effect.code())),
                    new EnginePrompt.Option("No")), 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectYesNo yesNo)
        {
            // THE DESTINY DRAW ARRIVES HERE, as a yes/no rather than a chain.
            //
            // Its effect is CONTINUOUS -- see DestinyDrawScript for why a
            // trigger could never be collected -- so the engine never offers it
            // as a chain. The choice is the SelectYesNo its operation asks, and
            // the description is the value the script set so this line can tell
            // it from every other yes/no in the game.
            if(yesNo.description() == de.cas_ual_ty.dueldimension.ocg
                .DestinyDrawScript.DESCRIPTION)
            {
                return new EnginePrompt(EnginePrompt.Kind.DESTINY, "Destiny Draw",
                    List.of(new EnginePrompt.Option("Destiny Draw",
                            "Draw one of the cards you nominated", 0),
                        new EnginePrompt.Option("Normal Draw",
                            "Draw whatever is on top", 0)),
                    1, 1, false, field);
            }
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, text.describe(yesNo.description()),
                List.of(new EnginePrompt.Option("Yes"), new EnginePrompt.Option("No")), 1, 1, false, field);
        }

        if(message instanceof DuelMessage.SelectTribute tribute)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            tribute.cards().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "counts as " + card.releaseParam(), card.code(),
                side(card.controller(), viewer), card.location(), card.sequence(),
                artAt(covers, card.controller(), card.location(), card.sequence(), card.code()))));
            return new EnginePrompt(EnginePrompt.Kind.MULTI, "Select tributes", options,
                tribute.min(), tribute.max(), tribute.cancelable(), field);
        }

        if(message instanceof DuelMessage.SelectSum sum)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            sum.selectable().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "value " + card.primary() + (card.alternate() != 0 ? " or " + card.alternate() : ""), card.code(),
                side(card.loc(), viewer),
                card.loc() == null ? 0 : card.loc().location(),
                card.loc() == null ? -1 : card.loc().sequence(),
                artAt(covers, card.loc(), card.code()))));
            return new EnginePrompt(EnginePrompt.Kind.MULTI, "Select cards totalling " + sum.acc(), options,
                Math.max(sum.min(), 1), Math.max(sum.max(), options.size()), false, field);
        }

        if(message instanceof DuelMessage.SelectUnselectCard unselect)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            unselect.selectable().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                where(card.loc()), card.code(),
                side(card.loc(), viewer),
                card.loc() == null ? 0 : card.loc().location(),
                card.loc() == null ? -1 : card.loc().sequence(),
                artAt(covers, card.loc(), card.code()))));
            unselect.unselectable().forEach(card -> options.add(
                new EnginePrompt.Option("Deselect " + cardName(card.code()), where(card.loc()), card.code(),
                    side(card.loc(), viewer),
                    card.loc() == null ? 0 : card.loc().location(),
                    card.loc() == null ? -1 : card.loc().sequence(),
                    artAt(covers, card.loc(), card.code()))));
            return new EnginePrompt(EnginePrompt.Kind.CHOOSE, "Select a card", options, 1, 1,
                unselect.finishable() || unselect.cancelable(), field);
        }

        if(message instanceof DuelMessage.SortCard sort)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            sort.cards().forEach(card -> options.add(
                new EnginePrompt.Option(cardName(card.code()), where(card.controller(),
                    card.location(), card.sequence()), card.code(),
                    side(card.controller(), viewer), card.location(), card.sequence(),
                    artAt(covers, card.controller(), card.location(), card.sequence(), card.code()))));
            return new EnginePrompt(EnginePrompt.Kind.SORT,
                sort.chain() ? "Order the chain" : "Order the cards", options,
                options.size(), options.size(), true, field);
        }

        if(message instanceof DuelMessage.SelectCounter counter)
        {
            List<EnginePrompt.Option> options = new ArrayList<>();
            counter.cards().forEach(card -> options.add(new EnginePrompt.Option(cardName(card.code()),
                "has " + card.counters(), card.code(), -1, card.counters(),
                side(card.controller(), viewer), card.location(), card.sequence(), 0,
                artAt(covers, card.controller(), card.location(), card.sequence(), card.code()))));
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
    private EnginePrompt.Option commandOption(int command, DuelMessage.IdleOption idle, BoardSnapshot field,
        CoverLookup covers, int viewer)
    {
        OcgCard card = cards.get(idle.code());
        String detail = card == null ? "" : card.attack() + " ATK / " + card.defense() + " DEF";
        // The snapshot is already this seat's own view, so it is indexed with
        // the REBASED controller. The lookup below is the core's, so it is not.
        int controller = side(idle.controller(), viewer);
        int position = positionAt(field, controller, idle.location(), idle.sequence());
        return new EnginePrompt.Option(
            CardCommands.label(command, card == null ? 0 : card.type(), position, text),
            detail, idle.code(), -1, 0,
            controller, idle.location(), idle.sequence(), command,
            artAt(covers, idle.controller(), idle.location(), idle.sequence(), idle.code()));
    }

    /**
     * The core's controller byte as the seat being asked sees it: 0 is always
     * their own side, which is the only numbering the client has. The same
     * convention, and the same name, as {@code DuelistDuels.side} -- an option
     * and the animation of the card it acts on have to agree on which half of
     * the table they mean.
     * <p>
     * -1 is "no controller" (a phase button, a yes/no) and stays -1: rebasing
     * it would give a slotless option a slot.
     */
    private static int side(int controller, int viewer)
    {
        return controller < 0 ? controller : controller == viewer ? 0 : 1;
    }

    /** The same, for the messages that carry the location as one value. */
    private static int side(CardLocation location, int viewer)
    {
        return location == null ? -1 : side(location.controller(), viewer);
    }

    /**
     * The artwork of the copy sitting where the message says, 0 when there is
     * no lookup, no location, or no entitlement. The lookup decides the last of
     * those; this only forwards what the core wrote into the message.
     */
    private static int artAt(CoverLookup covers, CardLocation loc, int code)
    {
        return covers == null || loc == null ? 0
            : covers.coverOf(loc.controller(), loc.location(), loc.sequence(), code);
    }

    /** The same, for the messages that carry the triple unpacked. */
    private static int artAt(CoverLookup covers, int controller, int location, int sequence, int code)
    {
        return covers == null ? 0 : covers.coverOf(controller, location, sequence, code);
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

    /**
     * The POS_* of a card on the field, for the reposition caption.
     *
     * @param controller the VIEWER-relative side, because {@code field} is a
     *                   viewer-relative snapshot: {@code self()} is whichever
     *                   seat this prompt is being built for. Handed the core's
     *                   absolute byte, seat 1 captioned its own cards from the
     *                   opponent's zones.
     */
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
