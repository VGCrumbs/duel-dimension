package de.cas_ual_ty.dueldimension.ocg.msg;

import de.cas_ual_ty.dueldimension.ocg.OcgCard;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Evaluates the RPN opcode program carried by MSG_ANNOUNCE_CARD, deciding
 * whether a given card may be declared. Port of is_declarable() in
 * ygopro-core playerop.cpp — the core re-runs the same check on our answer
 * and emits MSG_RETRY if it disagrees.
 * <p>
 * Opcodes are u64 sentinel values; anything else is a literal pushed on the
 * stack. Two flag opcodes (allow aliases / allow tokens) relax the trailing
 * eligibility rules rather than touching the stack.
 */
public final class DeclarableFilter
{
    // ocgapi_constants.h "Announce Card Opcodes"
    private static final long ADD = 0x4000000000000000L;
    private static final long SUB = 0x4000000100000000L;
    private static final long MUL = 0x4000000200000000L;
    private static final long DIV = 0x4000000300000000L;
    private static final long AND = 0x4000000400000000L;
    private static final long OR = 0x4000000500000000L;
    private static final long NEG = 0x4000000600000000L;
    private static final long NOT = 0x4000000700000000L;
    private static final long BAND = 0x4000000800000000L;
    private static final long BOR = 0x4000000900000000L;
    private static final long BNOT = 0x4000001000000000L;
    private static final long BXOR = 0x4000001100000000L;
    private static final long LSHIFT = 0x4000001200000000L;
    private static final long RSHIFT = 0x4000001300000000L;
    private static final long ALLOW_ALIASES = 0x4000001400000000L;
    private static final long ALLOW_TOKENS = 0x4000001500000000L;
    private static final long ISCODE = 0x4000010000000000L;
    private static final long ISSETCARD = 0x4000010100000000L;
    private static final long ISTYPE = 0x4000010200000000L;
    private static final long ISRACE = 0x4000010300000000L;
    private static final long ISATTRIBUTE = 0x4000010400000000L;
    private static final long GETCODE = 0x4000010500000000L;
    private static final long GETTYPE = 0x4000010600000000L;
    private static final long GETRACE = 0x4000010700000000L;
    private static final long GETATTRIBUTE = 0x4000010800000000L;

    private static final int TYPE_MONSTER = 0x1;
    private static final int TYPE_TOKEN = 0x4000;

    // Two cards the core always allows regardless of the filter.
    private static final int CARD_MARINE_DOLPHIN = 78734254;
    private static final int CARD_TWINKLE_MOSS = 13857930;

    private DeclarableFilter()
    {
    }

    public static boolean isDeclarable(OcgCard card, long[] opcodes)
    {
        Deque<Long> stack = new ArrayDeque<>();
        boolean allowAliases = false;
        boolean allowTokens = false;

        for(long opcode : opcodes)
        {
            if(opcode == ALLOW_ALIASES)
            {
                allowAliases = true;
            }
            else if(opcode == ALLOW_TOKENS)
            {
                allowTokens = true;
            }
            else if(opcode == ADD)
            {
                binary(stack, (a, b) -> a + b);
            }
            else if(opcode == SUB)
            {
                binary(stack, (a, b) -> a - b);
            }
            else if(opcode == MUL)
            {
                binary(stack, (a, b) -> a * b);
            }
            else if(opcode == DIV)
            {
                binary(stack, (a, b) -> b == 0 ? 0 : a / b);
            }
            else if(opcode == AND)
            {
                binary(stack, (a, b) -> bool(a != 0 && b != 0));
            }
            else if(opcode == OR)
            {
                binary(stack, (a, b) -> bool(a != 0 || b != 0));
            }
            else if(opcode == NEG)
            {
                unary(stack, a -> -a);
            }
            else if(opcode == NOT)
            {
                unary(stack, a -> bool(a == 0));
            }
            else if(opcode == BAND)
            {
                binary(stack, (a, b) -> a & b);
            }
            else if(opcode == BOR)
            {
                binary(stack, (a, b) -> a | b);
            }
            else if(opcode == BXOR)
            {
                binary(stack, (a, b) -> a ^ b);
            }
            else if(opcode == BNOT)
            {
                unary(stack, a -> ~a);
            }
            else if(opcode == LSHIFT)
            {
                binary(stack, (a, b) -> a << b);
            }
            else if(opcode == RSHIFT)
            {
                binary(stack, (a, b) -> a >> b);
            }
            else if(opcode == ISCODE)
            {
                unary(stack, a -> bool(card.code() == (int)a));
            }
            else if(opcode == ISTYPE)
            {
                unary(stack, a -> card.type() & a);
            }
            else if(opcode == ISRACE)
            {
                unary(stack, a -> card.race() & a);
            }
            else if(opcode == ISATTRIBUTE)
            {
                unary(stack, a -> card.attribute() & a);
            }
            else if(opcode == ISSETCARD)
            {
                unary(stack, a -> bool(hasSetcode(card, (int)a)));
            }
            else if(opcode == GETCODE)
            {
                stack.push((long)card.code());
            }
            else if(opcode == GETTYPE)
            {
                stack.push((long)card.type());
            }
            else if(opcode == GETRACE)
            {
                stack.push(card.race());
            }
            else if(opcode == GETATTRIBUTE)
            {
                stack.push((long)card.attribute());
            }
            else
            {
                stack.push(opcode);
            }
        }

        if(stack.size() != 1 || stack.peek() == 0)
        {
            return false;
        }
        if(card.code() == CARD_MARINE_DOLPHIN || card.code() == CARD_TWINKLE_MOSS)
        {
            return true;
        }
        boolean aliasOk = allowAliases || card.alias() == 0;
        boolean tokenOk = allowTokens || (card.type() & (TYPE_MONSTER | TYPE_TOKEN)) != (TYPE_MONSTER | TYPE_TOKEN);
        return aliasOk && tokenOk;
    }

    private static boolean hasSetcode(OcgCard card, int setCode)
    {
        int type = setCode & 0xFFF;
        int subType = setCode & 0xF000;
        for(int sc : card.setcodes())
        {
            if((sc & 0xFFF) == type && (sc & 0xF000 & subType) == subType)
            {
                return true;
            }
        }
        return false;
    }

    private static long bool(boolean value)
    {
        return value ? 1 : 0;
    }

    private interface BinaryOp
    {
        long apply(long lhs, long rhs);
    }

    private interface UnaryOp
    {
        long apply(long value);
    }

    private static void binary(Deque<Long> stack, BinaryOp op)
    {
        if(stack.size() >= 2)
        {
            long rhs = stack.pop();
            long lhs = stack.pop();
            stack.push(op.apply(lhs, rhs));
        }
    }

    private static void unary(Deque<Long> stack, UnaryOp op)
    {
        if(!stack.isEmpty())
        {
            stack.push(op.apply(stack.pop()));
        }
    }
}
