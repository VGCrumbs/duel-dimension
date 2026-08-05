package de.cas_ual_ty.ydm.ocg.bot;

import de.cas_ual_ty.ydm.ocg.msg.DuelMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Finds selections the core will accept for MSG_SELECT_SUM — tribute/material
 * selections where levels (or other parameters) must add up: Ritual Summons,
 * Synchro materials, "Tribute monsters whose total Level is exactly X".
 * <p>
 * Both acceptance rules are ports of ygopro-core playerop.cpp (SelectSum and
 * select_sum_check1); the core re-checks our answer and emits MSG_RETRY if it
 * disagrees, so these must match exactly.
 */
public final class SumSolver
{
    /** Enumerating every subset is 2^n; above this we sample randomly instead. */
    private static final int EXHAUSTIVE_LIMIT = 20;
    private static final int SAMPLE_ATTEMPTS = 20000;
    private static final int MAX_COLLECTED = 64;

    private SumSolver()
    {
    }

    /**
     * @return indices into {@code prompt.selectable()} forming an acceptable
     *         selection, or null if none was found
     */
    public static int[] solve(DuelMessage.SelectSum prompt, Random random)
    {
        int n = prompt.selectable().size();
        List<int[]> found = new ArrayList<>();

        if(n <= EXHAUSTIVE_LIMIT)
        {
            for(int mask = 0; mask < (1 << n) && found.size() < MAX_COLLECTED; mask++)
            {
                int[] candidate = indices(mask, n);
                if(accepts(prompt, candidate))
                {
                    found.add(candidate);
                }
            }
        }
        else
        {
            for(int attempt = 0; attempt < SAMPLE_ATTEMPTS && found.size() < MAX_COLLECTED; attempt++)
            {
                int[] candidate = indices(random.nextInt() & ((1 << EXHAUSTIVE_LIMIT) - 1), Math.min(n, EXHAUSTIVE_LIMIT));
                if(accepts(prompt, candidate))
                {
                    found.add(candidate);
                }
            }
        }

        return found.isEmpty() ? null : found.get(random.nextInt(found.size()));
    }

    /** Exactly the core's post-response validation for a candidate selection. */
    public static boolean accepts(DuelMessage.SelectSum prompt, int[] selected)
    {
        List<DuelMessage.SumCard> must = prompt.mustSelect();

        if(prompt.exactCount())
        {
            if(selected.length < prompt.min() || selected.length > prompt.max())
            {
                return false;
            }
            int[] params = new int[must.size() + selected.length];
            int at = 0;
            for(DuelMessage.SumCard card : must)
            {
                params[at++] = card.sumParam();
            }
            for(int index : selected)
            {
                params[at++] = prompt.selectable().get(index).sumParam();
            }
            return checkExact(params, 0, prompt.acc());
        }

        // "max == 0" mode: reachable total must cover acc, and dropping the
        // smallest contributor must fall short (no redundant members).
        if(selected.length == 0 && must.isEmpty())
        {
            return false;
        }
        long sum = 0;
        long maxSum = 0;
        long smallest = Long.MAX_VALUE;
        for(int i = 0; i < must.size() + selected.length; i++)
        {
            DuelMessage.SumCard card = i < must.size()
                ? must.get(i)
                : prompt.selectable().get(selected[i - must.size()]);
            int first = card.primary();
            int second = card.alternate();
            int smaller = (second != 0 && second < first) ? second : first;
            sum += smaller;
            maxSum += Math.max(first, second);
            smallest = Math.min(smallest, smaller);
        }
        return maxSum >= prompt.acc() && sum - smallest < prompt.acc();
    }

    /**
     * Port of select_sum_check1: every listed card contributes one of its two
     * parameter values and the total must land on acc exactly.
     */
    private static boolean checkExact(int[] params, int index, long acc)
    {
        if(acc == 0 || index == params.length)
        {
            return false;
        }
        int first = params[index] & 0xFFFF;
        int second = params[index] >>> 16;
        if(index == params.length - 1)
        {
            return acc == first || acc == second;
        }
        return (acc > first && checkExact(params, index + 1, acc - first))
            || (second > 0 && acc > second && checkExact(params, index + 1, acc - second));
    }

    private static int[] indices(int mask, int n)
    {
        int count = Integer.bitCount(mask & ((1 << n) - 1));
        int[] result = new int[count];
        int at = 0;
        for(int i = 0; i < n; i++)
        {
            if((mask & (1 << i)) != 0)
            {
                result[at++] = i;
            }
        }
        return result;
    }
}
