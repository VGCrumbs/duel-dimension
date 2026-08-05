package de.cas_ual_ty.dueldimension.ocg.msg;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed views of core messages. Byte layouts are transcribed from the
 * ygopro-core source (playerop.cpp, processor.cpp, field.cpp, operations.cpp
 * — the code that writes each message); every decoder must consume its
 * payload exactly.
 * <p>
 * {@link #decode} returns {@link Unknown} for message types without a decoder
 * yet, but throws {@link MsgReader.MsgFormatException} for a known type whose
 * payload doesn't parse — that always indicates a layout bug on our side.
 */
public sealed interface DuelMessage
{
    /** A message that awaits a response from a specific player. */
    sealed interface Prompt extends DuelMessage
    {
        int player();
    }

    // ---- element types ----

    /** Entry of the idle summon/spsummon/reposition/mset/sset lists. */
    record IdleOption(int code, int controller, int location, int sequence)
    {
    }

    /** Entry of an activatable-effects list (idle or battle). */
    record ActivatableOption(int code, int controller, int location, int sequence, long description, int clientMode)
    {
    }

    /** Entry of the battle attackable list. */
    record AttackOption(int code, int controller, int location, int sequence, boolean canDirect)
    {
    }

    record SelectableCard(int code, CardLocation loc)
    {
    }

    record ChainOption(int code, CardLocation loc, long description, int clientMode)
    {
    }

    record DrawnCard(int code, int position)
    {
    }

    // ---- interactive messages ----

    record SelectIdleCmd(int player, List<IdleOption> summonable, List<IdleOption> spSummonable,
        List<IdleOption> repositionable, List<IdleOption> monsterSettable, List<IdleOption> spellSettable,
        List<ActivatableOption> activatable, boolean toBattle, boolean toEnd, boolean canShuffle) implements Prompt
    {
    }

    record SelectBattleCmd(int player, List<ActivatableOption> activatable, List<AttackOption> attackable,
        boolean toMain2, boolean toEnd) implements Prompt
    {
    }

    record SelectCard(int player, boolean cancelable, int min, int max, List<SelectableCard> cards) implements Prompt
    {
    }

    record SelectChain(int player, int specialCount, boolean forced, long hintTimingSelf, long hintTimingOther,
        List<ChainOption> chains) implements Prompt
    {
    }

    /**
     * Zone selection. The mask has a bit SET for each zone that is
     * FORBIDDEN (the response reader retries on flagged zones): bits 0-6
     * own monster zones, 8-15 own spell/pendulum zones, same shifted
     * left 16 for the opponent.
     */
    record SelectPlace(int player, int count, long forbiddenMask, boolean disfield) implements Prompt
    {
    }

    record SelectPosition(int player, int code, int positions) implements Prompt
    {
    }

    record SelectOption(int player, long[] options) implements Prompt
    {
    }

    record SelectYesNo(int player, long description) implements Prompt
    {
    }

    record SelectEffectYesNo(int player, int code, CardLocation loc, long description) implements Prompt
    {
    }

    /** A card offered for tribute; {@code releaseParam} is how much it counts toward the required total. */
    record TributeCard(int code, int controller, int location, int sequence, int releaseParam)
    {
    }

    record SelectTribute(int player, boolean cancelable, int min, int max, List<TributeCard> cards) implements Prompt
    {
    }

    /** A card offered to a sum selection; {@code sumParam} packs two alternative values (low/high 16 bits). */
    record SumCard(int code, CardLocation loc, int sumParam)
    {
        public int primary()
        {
            return sumParam & 0xFFFF;
        }

        /** Second acceptable value (e.g. Double Tribute / level-changing effects), 0 if none. */
        public int alternate()
        {
            return sumParam >>> 16;
        }
    }

    /**
     * Pick cards whose parameters sum to {@code acc}.
     * <p>
     * {@code exactCount} false is the core's "max == 0" mode: the selection
     * must reach {@code acc} without any member being redundant, rather than
     * matching a card count.
     */
    record SelectSum(int player, boolean exactCount, int acc, int min, int max,
        List<SumCard> mustSelect, List<SumCard> selectable) implements Prompt
    {
    }

    /** Incremental select/unselect loop: answer with ONE index, repeatedly, until finished. */
    record SelectUnselectCard(int player, boolean finishable, boolean cancelable, int min, int max,
        List<SelectableCard> selectable, List<SelectableCard> unselectable) implements Prompt
    {
        /** Combined index space of the response: selectable first, then unselectable. */
        public int optionCount()
        {
            return selectable.size() + unselectable.size();
        }
    }

    record CounterCard(int code, int controller, int location, int sequence, int counters)
    {
    }

    record SelectCounter(int player, int counterType, int count, List<CounterCard> cards) implements Prompt
    {
    }

    /** MSG_SORT_CARD / MSG_SORT_CHAIN. Location is a u32 here, not the usual loc_info. */
    record SortableCard(int code, int controller, int location, int sequence)
    {
    }

    record SortCard(int player, boolean chain, List<SortableCard> cards) implements Prompt
    {
    }

    /** MSG_ANNOUNCE_RACE (u64 mask) / MSG_ANNOUNCE_ATTRIB (u32 mask): pick exactly {@code count} bits of {@code available}. */
    record AnnounceBits(int player, int count, long available, boolean race) implements Prompt
    {
    }

    /** Declare a card name; {@code filter} is an RPN opcode program (see {@link DeclarableFilter}). */
    record AnnounceCard(int player, long[] filter) implements Prompt
    {
    }

    record AnnounceNumber(int player, long[] options) implements Prompt
    {
    }

    record RockPaperScissors(int player) implements Prompt
    {
    }

    // ---- informational messages ----

    record Move(int code, CardLocation from, CardLocation to, int reason) implements DuelMessage
    {
    }

    /**
     * An attack declaration. The core writes the attacker's loc_info followed
     * by the target's; for a direct attack it writes a zeroed loc_info instead
     * (processor.cpp: {@code message->write(loc_info{})}), which is what
     * {@link #isDirect()} tests for.
     */
    record Attack(CardLocation attacker, CardLocation target) implements DuelMessage
    {
        public boolean isDirect()
        {
            return target.location() == 0;
        }
    }

    record Draw(int player, List<DrawnCard> cards) implements DuelMessage
    {
    }

    /**
     * An effect is going onto the chain. processor.cpp writes the handler's
     * code and location, then the triggering state, the effect description and
     * the new chain size.
     */
    record Chaining(int code, CardLocation card, int triggeringController, int triggeringLocation,
        int triggeringSequence, long description, int chainSize) implements DuelMessage
    {
    }

    /** Cards an effect has just targeted. libduel.cpp: a count then that many loc_infos. */
    record BecomeTarget(List<CardLocation> targets) implements DuelMessage
    {
    }

    /** A monster is being flip summoned. operations.cpp: code then loc_info. */
    record FlipSummoning(int code, CardLocation card) implements DuelMessage
    {
    }

    record NewTurn(int player) implements DuelMessage
    {
    }

    record NewPhase(int phase) implements DuelMessage
    {
    }

    record Damage(int player, int amount) implements DuelMessage
    {
    }

    record Recover(int player, int amount) implements DuelMessage
    {
    }

    record ShuffleDeck(int player) implements DuelMessage
    {
    }

    record Hint(int hintType, int player, long description) implements DuelMessage
    {
    }

    record Win(int winner, int reason) implements DuelMessage
    {
    }

    /** A type we have no decoder for yet; the raw message is preserved. */
    record Unknown(RawMessage raw) implements DuelMessage
    {
    }

    // ---- decoding ----

    static DuelMessage decode(RawMessage message)
    {
        MsgReader in = new MsgReader(message.payload());
        try
        {
            DuelMessage decoded = decode(message, in);
            if(!(decoded instanceof Unknown))
            {
                in.expectEnd();
            }
            return decoded;
        }
        catch(MsgReader.MsgFormatException e)
        {
            throw e;
        }
        catch(RuntimeException e)
        {
            throw new MsgReader.MsgFormatException("Failed decoding " + message.name(), e);
        }
    }

    private static DuelMessage decode(RawMessage message, MsgReader in)
    {
        return switch(message.type())
        {
            case OcgConstants.MSG_SELECT_IDLECMD -> new SelectIdleCmd(in.u8(),
                idleList(in, true), idleList(in, true), idleList(in, false), idleList(in, true), idleList(in, true),
                activatableList(in), in.flag(), in.flag(), in.flag());
            case OcgConstants.MSG_SELECT_BATTLECMD -> new SelectBattleCmd(in.u8(),
                activatableList(in), attackList(in), in.flag(), in.flag());
            case OcgConstants.MSG_SELECT_CARD ->
            {
                int player = in.u8();
                boolean cancelable = in.flag();
                int min = in.u32();
                int max = in.u32();
                int count = in.u32();
                List<SelectableCard> cards = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                {
                    cards.add(new SelectableCard(in.u32(), in.loc()));
                }
                yield new SelectCard(player, cancelable, min, max, cards);
            }
            case OcgConstants.MSG_SELECT_CHAIN ->
            {
                int player = in.u8();
                int specialCount = in.u8();
                boolean forced = in.flag();
                long hintSelf = in.u32() & 0xFFFFFFFFL;
                long hintOther = in.u32() & 0xFFFFFFFFL;
                int count = in.u32();
                List<ChainOption> chains = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                {
                    chains.add(new ChainOption(in.u32(), in.loc(), in.u64(), in.u8()));
                }
                yield new SelectChain(player, specialCount, forced, hintSelf, hintOther, chains);
            }
            case OcgConstants.MSG_SELECT_PLACE -> new SelectPlace(in.u8(), in.u8(), in.u32() & 0xFFFFFFFFL, false);
            case OcgConstants.MSG_SELECT_DISFIELD -> new SelectPlace(in.u8(), in.u8(), in.u32() & 0xFFFFFFFFL, true);
            case OcgConstants.MSG_SELECT_POSITION -> new SelectPosition(in.u8(), in.u32(), in.u8());
            case OcgConstants.MSG_SELECT_OPTION ->
            {
                int player = in.u8();
                long[] options = new long[in.u8()];
                for(int i = 0; i < options.length; i++)
                {
                    options[i] = in.u64();
                }
                yield new SelectOption(player, options);
            }
            case OcgConstants.MSG_SELECT_YESNO -> new SelectYesNo(in.u8(), in.u64());
            case OcgConstants.MSG_SELECT_EFFECTYN -> new SelectEffectYesNo(in.u8(), in.u32(), in.loc(), in.u64());
            case OcgConstants.MSG_SELECT_TRIBUTE ->
            {
                int player = in.u8();
                boolean cancelable = in.flag();
                int min = in.u32();
                int max = in.u32();
                int count = in.u32();
                List<TributeCard> cards = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                {
                    cards.add(new TributeCard(in.u32(), in.u8(), in.u8(), in.u32(), in.u8()));
                }
                yield new SelectTribute(player, cancelable, min, max, cards);
            }
            case OcgConstants.MSG_SELECT_SUM ->
            {
                int player = in.u8();
                boolean exactCount = !in.flag(); // core writes 0 when a max count applies, 1 when not
                int acc = in.u32();
                int min = in.u32();
                int max = in.u32();
                yield new SelectSum(player, exactCount, acc, min, max, sumList(in), sumList(in));
            }
            case OcgConstants.MSG_SELECT_UNSELECT_CARD ->
            {
                int player = in.u8();
                boolean finishable = in.flag();
                boolean cancelable = in.flag();
                int min = in.u32();
                int max = in.u32();
                yield new SelectUnselectCard(player, finishable, cancelable, min, max,
                    selectableList(in), selectableList(in));
            }
            case OcgConstants.MSG_SELECT_COUNTER ->
            {
                int player = in.u8();
                int counterType = in.u16();
                int count = in.u16();
                int size = in.u32();
                List<CounterCard> cards = new ArrayList<>(size);
                for(int i = 0; i < size; i++)
                {
                    cards.add(new CounterCard(in.u32(), in.u8(), in.u8(), in.u8(), in.u16()));
                }
                yield new SelectCounter(player, counterType, count, cards);
            }
            case OcgConstants.MSG_SORT_CARD, OcgConstants.MSG_SORT_CHAIN ->
            {
                int player = in.u8();
                int count = in.u32();
                List<SortableCard> cards = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                {
                    cards.add(new SortableCard(in.u32(), in.u8(), in.u32(), in.u32()));
                }
                yield new SortCard(player, message.type() == OcgConstants.MSG_SORT_CHAIN, cards);
            }
            case OcgConstants.MSG_ANNOUNCE_RACE -> new AnnounceBits(in.u8(), in.u8(), in.u64(), true);
            case OcgConstants.MSG_ANNOUNCE_ATTRIB -> new AnnounceBits(in.u8(), in.u8(), in.u32() & 0xFFFFFFFFL, false);
            case OcgConstants.MSG_ANNOUNCE_CARD -> new AnnounceCard(in.u8(), longList(in));
            case OcgConstants.MSG_ANNOUNCE_NUMBER -> new AnnounceNumber(in.u8(), longList(in));
            case OcgConstants.MSG_ROCK_PAPER_SCISSORS -> new RockPaperScissors(in.u8());
            case OcgConstants.MSG_MOVE -> new Move(in.u32(), in.loc(), in.loc(), in.u32());
            case OcgConstants.MSG_ATTACK -> new Attack(in.loc(), in.loc());
            case OcgConstants.MSG_CHAINING -> new Chaining(in.u32(), in.loc(), in.u8(), in.u8(),
                in.u32(), in.u64(), in.u32());
            case OcgConstants.MSG_BECOME_TARGET ->
            {
                int count = in.u32();
                List<CardLocation> targets = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                {
                    targets.add(in.loc());
                }
                yield new BecomeTarget(targets);
            }
            case OcgConstants.MSG_FLIPSUMMONING -> new FlipSummoning(in.u32(), in.loc());
            case OcgConstants.MSG_DRAW ->
            {
                int player = in.u8();
                int count = in.u32();
                List<DrawnCard> cards = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                {
                    cards.add(new DrawnCard(in.u32(), in.u32()));
                }
                yield new Draw(player, cards);
            }
            case OcgConstants.MSG_NEW_TURN -> new NewTurn(in.u8());
            case OcgConstants.MSG_NEW_PHASE -> new NewPhase(in.u16());
            case OcgConstants.MSG_DAMAGE -> new Damage(in.u8(), in.u32());
            case OcgConstants.MSG_RECOVER -> new Recover(in.u8(), in.u32());
            case OcgConstants.MSG_SHUFFLE_DECK -> new ShuffleDeck(in.u8());
            case OcgConstants.MSG_HINT -> new Hint(in.u8(), in.u8(), in.u64());
            case OcgConstants.MSG_WIN -> new Win(in.u8(), in.u8());
            default -> new Unknown(message);
        };
    }

    private static List<SumCard> sumList(MsgReader in)
    {
        int count = in.u32();
        List<SumCard> list = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            list.add(new SumCard(in.u32(), in.loc(), in.u32()));
        }
        return list;
    }

    private static List<SelectableCard> selectableList(MsgReader in)
    {
        int count = in.u32();
        List<SelectableCard> list = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            list.add(new SelectableCard(in.u32(), in.loc()));
        }
        return list;
    }

    private static long[] longList(MsgReader in)
    {
        long[] values = new long[in.u8()];
        for(int i = 0; i < values.length; i++)
        {
            values[i] = in.u64();
        }
        return values;
    }

    /** The idle lists share their layout except reposition, whose sequence is u8 (see playerop.cpp). */
    private static List<IdleOption> idleList(MsgReader in, boolean wideSequence)
    {
        int count = in.u32();
        List<IdleOption> list = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            list.add(new IdleOption(in.u32(), in.u8(), in.u8(), wideSequence ? in.u32() : in.u8()));
        }
        return list;
    }

    private static List<ActivatableOption> activatableList(MsgReader in)
    {
        int count = in.u32();
        List<ActivatableOption> list = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            list.add(new ActivatableOption(in.u32(), in.u8(), in.u8(), in.u32(), in.u64(), in.u8()));
        }
        return list;
    }

    /** Attackable entries use a u8 sequence (see playerop.cpp SelectBattleCmd). */
    private static List<AttackOption> attackList(MsgReader in)
    {
        int count = in.u32();
        List<AttackOption> list = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            list.add(new AttackOption(in.u32(), in.u8(), in.u8(), in.u8(), in.flag()));
        }
        return list;
    }
}
