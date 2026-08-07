package de.cas_ual_ty.dueldimension.ocg.bot;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.msg.DeclarableFilter;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.msg.Responses;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Layer 1 bot: picks a uniformly random LEGAL response to every prompt it
 * understands, and aborts the run (null) on any prompt it doesn't — an
 * unknown prompt must surface as a failure, not a guess.
 * <p>
 * Deterministic per seed. No strategy, no memory: this is the protocol
 * exerciser and fuzz driver, and the baseline the arena measures
 * HeuristicBot against.
 */
public class RandomBot implements ResponseSource
{
    private final Random random;
    private final Collection<OcgCard> declarableIndex;

    public RandomBot(long seed)
    {
        this(seed, List.of());
    }

    /**
     * @param declarableIndex cards this bot may declare for MSG_ANNOUNCE_CARD
     *                        (usually every card in the cdb); empty means the
     *                        bot aborts on that prompt instead of guessing
     */
    public RandomBot(long seed, Collection<OcgCard> declarableIndex)
    {
        random = new Random(seed);
        this.declarableIndex = declarableIndex;
    }

    @Override
    public byte[] respond(RawMessage prompt)
    {
        DuelMessage decoded = DuelMessage.decode(prompt);
        if(decoded instanceof DuelMessage.SelectIdleCmd idle)
        {
            return idle(idle);
        }
        if(decoded instanceof DuelMessage.SelectBattleCmd battle)
        {
            return battle(battle);
        }
        if(decoded instanceof DuelMessage.SelectCard select)
        {
            return selectCards(select);
        }
        if(decoded instanceof DuelMessage.SelectChain chain)
        {
            return chain(chain);
        }
        if(decoded instanceof DuelMessage.SelectPlace place)
        {
            return place(place);
        }
        if(decoded instanceof DuelMessage.SelectPosition position)
        {
            return position(position);
        }
        if(decoded instanceof DuelMessage.SelectOption option)
        {
            return Responses.option(random.nextInt(option.options().length));
        }
        if(decoded instanceof DuelMessage.SelectYesNo || decoded instanceof DuelMessage.SelectEffectYesNo)
        {
            return random.nextBoolean() ? Responses.yes() : Responses.no();
        }
        if(decoded instanceof DuelMessage.SelectTribute tribute)
        {
            return tribute(tribute);
        }
        if(decoded instanceof DuelMessage.SelectSum sum)
        {
            int[] solution = SumSolver.solve(sum, random);
            return solution == null ? null : Responses.selectSum(solution);
        }
        if(decoded instanceof DuelMessage.SelectUnselectCard unselect)
        {
            return unselect(unselect);
        }
        if(decoded instanceof DuelMessage.SelectCounter counter)
        {
            return counters(counter);
        }
        if(decoded instanceof DuelMessage.SortCard sort)
        {
            return Responses.sortDecline(); // any order is legal; declining is simplest
        }
        if(decoded instanceof DuelMessage.AnnounceBits announce)
        {
            return announceBits(announce);
        }
        if(decoded instanceof DuelMessage.AnnounceCard announce)
        {
            return announceCard(announce);
        }
        if(decoded instanceof DuelMessage.AnnounceNumber announce)
        {
            return announce.options().length == 0 ? null
                : Responses.announceNumber(random.nextInt(announce.options().length));
        }
        if(decoded instanceof DuelMessage.RockPaperScissors)
        {
            return Responses.rockPaperScissors(1 + random.nextInt(3));
        }
        return null; // no decoder / no legal-move logic yet: abort loudly
    }

    private byte[] idle(DuelMessage.SelectIdleCmd idle)
    {
        List<Supplier<byte[]>> choices = new ArrayList<>();
        for(int i = 0; i < idle.summonable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.idleSummon(index));
        }
        for(int i = 0; i < idle.spSummonable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.idleSpSummon(index));
        }
        for(int i = 0; i < idle.repositionable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.idleReposition(index));
        }
        for(int i = 0; i < idle.monsterSettable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.idleMonsterSet(index));
        }
        for(int i = 0; i < idle.spellSettable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.idleSpellSet(index));
        }
        for(int i = 0; i < idle.activatable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.idleActivate(index));
        }
        if(idle.toBattle())
        {
            choices.add(Responses::idleToBattle);
        }
        if(idle.toEnd())
        {
            choices.add(Responses::idleToEnd);
        }
        return pick(choices);
    }

    private byte[] battle(DuelMessage.SelectBattleCmd battle)
    {
        List<Supplier<byte[]>> choices = new ArrayList<>();
        for(int i = 0; i < battle.activatable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.battleActivate(index));
        }
        for(int i = 0; i < battle.attackable().size(); i++)
        {
            int index = i;
            choices.add(() -> Responses.battleAttack(index));
        }
        if(battle.toMain2())
        {
            choices.add(Responses::battleToMain2);
        }
        if(battle.toEnd())
        {
            choices.add(Responses::battleToEnd);
        }
        return pick(choices);
    }

    private byte[] selectCards(DuelMessage.SelectCard select)
    {
        // Occasionally cancel when allowed, else pick a random legal amount
        // of distinct random cards.
        if(select.cancelable() && select.min() == 0 && random.nextBoolean())
        {
            return Responses.selectCardsCancel();
        }
        int count = select.cards().size();
        int max = Math.min(select.max(), count);
        int min = Math.min(select.min(), max);
        int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
        if(amount == 0)
        {
            return Responses.selectCardsCancel();
        }

        List<Integer> indices = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            indices.add(i);
        }
        Collections.shuffle(indices, random);
        int[] chosen = new int[amount];
        for(int i = 0; i < amount; i++)
        {
            chosen[i] = indices.get(i);
        }
        return Responses.selectCards(chosen);
    }

    private byte[] chain(DuelMessage.SelectChain chain)
    {
        if(chain.chains().isEmpty())
        {
            return chain.forced() ? null : Responses.chainDecline();
        }
        if(chain.forced() || random.nextBoolean())
        {
            return Responses.chain(random.nextInt(chain.chains().size()));
        }
        return Responses.chainDecline();
    }

    private byte[] place(DuelMessage.SelectPlace place)
    {
        // Mask semantics (playerop.cpp): bit SET = zone forbidden. Bits 0-6
        // own monster zones, 8-15 own spell/pendulum zones; opponent's zones
        // are the same shifted left 16. "Own" is relative to the prompted
        // player.
        List<Responses.Place> allowed = new ArrayList<>();
        for(int bit = 0; bit < 32; bit++)
        {
            if((place.forbiddenMask() >> bit & 1) != 0)
            {
                continue;
            }
            int half = bit & 15;
            boolean mzone = half < 8;
            int sequence = half & 7;
            if(mzone && sequence > 6)
            {
                continue;
            }
            int player = bit < 16 ? place.player() : 1 - place.player();
            allowed.add(new Responses.Place(player,
                mzone ? OcgConstants.LOCATION_MZONE : OcgConstants.LOCATION_SZONE, sequence));
        }
        if(allowed.size() < place.count())
        {
            return null;
        }
        Collections.shuffle(allowed, random);
        return Responses.places(allowed.subList(0, place.count()).toArray(Responses.Place[]::new));
    }

    private byte[] position(DuelMessage.SelectPosition position)
    {
        List<Integer> options = new ArrayList<>(4);
        for(int pos : new int[] {OcgConstants.POS_FACEUP_ATTACK, OcgConstants.POS_FACEDOWN_ATTACK,
            OcgConstants.POS_FACEUP_DEFENSE, OcgConstants.POS_FACEDOWN_DEFENSE})
        {
            if((position.positions() & pos) != 0)
            {
                options.add(pos);
            }
        }
        return options.isEmpty() ? null : Responses.position(options.get(random.nextInt(options.size())));
    }

    /**
     * Tributes are counted by release_param (a Double Tribute monster counts
     * twice), so the core's rule is "total param at least min, card count at
     * most max" — not a plain count.
     */
    private byte[] tribute(DuelMessage.SelectTribute tribute)
    {
        List<Integer> indices = shuffledIndices(tribute.cards().size());
        List<Integer> chosen = new ArrayList<>();
        int total = 0;
        for(int index : indices)
        {
            if(total >= tribute.min() || chosen.size() >= tribute.max())
            {
                break;
            }
            chosen.add(index);
            total += tribute.cards().get(index).releaseParam();
        }
        if(total < tribute.min())
        {
            return tribute.cancelable() ? Responses.selectCardsCancel() : null;
        }
        return Responses.selectTribute(chosen.stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * The core answers this one card at a time and re-prompts, so a single
     * index (or "finish") is the whole response.
     */
    private byte[] unselect(DuelMessage.SelectUnselectCard unselect)
    {
        int options = unselect.optionCount();
        if(options == 0)
        {
            return unselect.finishable() || unselect.cancelable() ? Responses.selectUnselectFinish() : null;
        }
        if((unselect.finishable() || unselect.cancelable()) && random.nextInt(4) == 0)
        {
            return Responses.selectUnselectFinish();
        }
        return Responses.selectUnselect(random.nextInt(options));
    }

    /** Spreads the required counter total over the offered cards, respecting each card's stock. */
    private byte[] counters(DuelMessage.SelectCounter counter)
    {
        int[] taken = new int[counter.cards().size()];
        int remaining = counter.count();
        for(int index : shuffledIndices(counter.cards().size()))
        {
            if(remaining <= 0)
            {
                break;
            }
            int take = Math.min(remaining, counter.cards().get(index).counters());
            taken[index] = take;
            remaining -= take;
        }
        return remaining > 0 ? null : Responses.counters(taken);
    }

    /** Picks exactly {@code count} distinct bits out of the allowed mask. */
    private byte[] announceBits(DuelMessage.AnnounceBits announce)
    {
        List<Integer> available = new ArrayList<>();
        for(int bit = 0; bit < 64; bit++)
        {
            if((announce.available() >>> bit & 1) != 0)
            {
                available.add(bit);
            }
        }
        if(available.size() < announce.count())
        {
            return null;
        }
        Collections.shuffle(available, random);
        long mask = 0;
        for(int i = 0; i < announce.count(); i++)
        {
            mask |= 1L << available.get(i);
        }
        return announce.race() ? Responses.announceRace(mask) : Responses.announceAttribute((int)mask);
    }

    /** Scans the card index for a name this filter permits (Prohibition-style declares). */
    private byte[] announceCard(DuelMessage.AnnounceCard announce)
    {
        List<OcgCard> declarable = new ArrayList<>();
        for(OcgCard card : declarableIndex)
        {
            if(DeclarableFilter.isDeclarable(card, announce.filter()))
            {
                declarable.add(card);
                if(declarable.size() >= 256) // enough for a random pick; the full scan is wasteful
                {
                    break;
                }
            }
        }
        return declarable.isEmpty() ? null
            : Responses.announceCard(declarable.get(random.nextInt(declarable.size())).code());
    }

    private List<Integer> shuffledIndices(int count)
    {
        List<Integer> indices = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            indices.add(i);
        }
        Collections.shuffle(indices, random);
        return indices;
    }

    private byte[] pick(List<Supplier<byte[]>> choices)
    {
        return choices.isEmpty() ? null : choices.get(random.nextInt(choices.size())).get();
    }
}
