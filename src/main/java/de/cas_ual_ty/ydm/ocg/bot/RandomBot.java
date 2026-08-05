package de.cas_ual_ty.ydm.ocg.bot;

import de.cas_ual_ty.ydm.ocg.OcgConstants;
import de.cas_ual_ty.ydm.ocg.RawMessage;
import de.cas_ual_ty.ydm.ocg.ResponseSource;
import de.cas_ual_ty.ydm.ocg.msg.DuelMessage;
import de.cas_ual_ty.ydm.ocg.msg.Responses;

import java.util.ArrayList;
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

    public RandomBot(long seed)
    {
        random = new Random(seed);
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

    private byte[] pick(List<Supplier<byte[]>> choices)
    {
        return choices.isEmpty() ? null : choices.get(random.nextInt(choices.size())).get();
    }
}
