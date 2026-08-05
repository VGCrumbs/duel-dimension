package de.cas_ual_ty.ydm.ocg.msg;

import de.cas_ual_ty.ydm.ocg.OcgConstants;
import de.cas_ual_ty.ydm.ocg.RawMessage;

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

    // ---- informational messages ----

    record Move(int code, CardLocation from, CardLocation to, int reason) implements DuelMessage
    {
    }

    record Draw(int player, List<DrawnCard> cards) implements DuelMessage
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
            case OcgConstants.MSG_MOVE -> new Move(in.u32(), in.loc(), in.loc(), in.u32());
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
