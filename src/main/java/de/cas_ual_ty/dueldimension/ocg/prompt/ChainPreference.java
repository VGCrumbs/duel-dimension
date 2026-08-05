package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;

/**
 * The chain-response policy EDOPro exposes as btnChainIgnore /
 * btnChainAlways / btnChainWhenAvail, ported from the decision in
 * {@code gframe/duelclient.cpp} (MSG_SELECT_CHAIN handler):
 *
 * <pre>
 * if(!select_trigger &amp;&amp; !chain_forced
 *    &amp;&amp; (ignore_chain || ((count == 0 || specount == 0) &amp;&amp; !always_chain))
 *    &amp;&amp; (count == 0 || !chain_when_avail))
 *        respond -1;   // decline without asking
 * </pre>
 *
 * This matters for how often a player is interrupted: with the default
 * policy, a chain window is only shown when the core reports a non-zero
 * {@code spe_count}, so ordinary windows where nothing timing-relevant is
 * available pass by silently — exactly as they do in the reference client.
 */
public enum ChainPreference
{
    /** btnChainIgnore: never ask, always decline (unless forced). */
    IGNORE,
    /** No toggle pressed: ask only when the core flags the window as special. */
    DEFAULT,
    /** btnChainAlways: ask whenever anything is chainable. */
    ALWAYS,
    /** btnChainWhenAvail: ask when at least one card is chainable. */
    WHEN_AVAILABLE;

    /** duelclient.cpp: spe_count 0x7f marks the "select trigger" variant. */
    private static final int SELECT_TRIGGER = 0x7F;

    public ChainPreference next()
    {
        return values()[(ordinal() + 1) % values().length];
    }

    public String label()
    {
        return switch(this)
        {
            case IGNORE -> "Chain: OFF";
            case DEFAULT -> "Chain: default";
            case ALWAYS -> "Chain: ON";
            case WHEN_AVAILABLE -> "Chain: when available";
        };
    }

    /**
     * @return true when this window should be answered "no chain" without
     *         asking the player
     */
    public boolean declinesWithoutAsking(DuelMessage.SelectChain chain)
    {
        int count = chain.chains().size();
        int specialCount = chain.specialCount();

        if(specialCount == SELECT_TRIGGER || chain.forced())
        {
            return false; // must be answered by the player
        }
        boolean ignoreChain = this == IGNORE;
        boolean alwaysChain = this == ALWAYS;
        boolean chainWhenAvail = this == WHEN_AVAILABLE;

        return (ignoreChain || ((count == 0 || specialCount == 0) && !alwaysChain))
            && (count == 0 || !chainWhenAvail);
    }
}
