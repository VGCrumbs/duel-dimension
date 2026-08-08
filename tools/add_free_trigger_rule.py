"""A general rule for triggers that cost nothing, as a stated deviation.

Windbot only activates effects it has an explicit per-card rule for: GameAI's
OnSelectEffectYn returns false when nothing in the executor list matches, and
DefaultExecutor registers four Activate rules in total. A card it has never
heard of -- Mystic Tomato, say -- is declined. That is its design, not a gap.

The deviation is narrow on purpose. "Activate anything offered" would make the
bot chain every trap at the first opportunity, which is worse play than not
chaining. What is safe is a trigger on a card that is ALREADY IN THE GRAVEYARD:
the card is spent either way, so there is no copy to hold back and no later
moment to prefer. Declining cannot be better than activating.

The ported DefaultExecutor is left exactly as it is; the rule lives in
DuelistExecutor, which is ours.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


# ---------- 1. the hook, defaulting to Windbot's behaviour ----------
X = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/Executor.java"
sub(X, "    /** {@code OnSelectPosition}: 0 to let the engine's own default stand. */",
    """    /**
     * Whether to activate an optional effect no rule in the executor list
     * covers.
     * <p>
     * <b>Not Windbot.</b> Upstream there is no such question: GameAI's
     * OnSelectEffectYn returns false the moment the list runs out, so a card
     * without a rule is always declined. This is the one place the duelists
     * are allowed to differ, and the default here is the reference's answer --
     * only {@code DuelistExecutor} says otherwise, and only for the case where
     * declining cannot be the better play.
     *
     * @param location where the card is now, as a LOCATION_* constant
     */
    public boolean activateUnlistedOptional(BotCard card, int location)
    {
        return false;
    }

    /** {@code OnSelectPosition}: 0 to let the engine's own default stand. */""",
    "Executor hook")

# ---------- 2. the duelists' answer ----------
D = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/DuelistExecutor.java"
sub(D, "public abstract class DuelistExecutor extends DefaultExecutor\n{",
    """public abstract class DuelistExecutor extends DefaultExecutor
{
    /**
     * Use a trigger on a card that is already in the graveyard.
     * <p>
     * The card is spent whether or not the effect is used, so there is no copy
     * being held back and no later moment being preferred: declining is
     * strictly worse. That is what makes this safe to answer generally when
     * Windbot would not, and why it is limited to the graveyard rather than
     * applied to everything offered -- a trigger on a card still on the field
     * or in the hand may well be worth saving, and answering yes to those
     * would have the bot chain every trap the moment it could.
     * <p>
     * Mystic Tomato is the case that prompted it: destroyed by battle, sitting
     * in the graveyard, offering a free body from the deck, and declined
     * because no rule named it.
     */
    @Override
    public boolean activateUnlistedOptional(BotCard card, int location)
    {
        return location == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE;
    }
""", "DuelistExecutor answer")

# ---------- 3. both prompts fall back to it ----------
B = "src/main/java/de/cas_ual_ty/dueldimension/ocg/bot/executor/ExecutorBot.java"
sub(B, """        return chain.forced() && !chain.chains().isEmpty()
            ? Responses.chain(0) : Responses.chainDecline();""",
    """        // Nothing in the list wanted any of them. Before declining, the one
        // question Windbot does not ask: is any of these free anyway?
        for(int i = 0; i < chain.chains().size(); i++)
        {
            DuelMessage.ChainOption option = chain.chains().get(i);
            if(executor.activateUnlistedOptional(
                cardOf(option.code(), option.loc().controller(),
                    option.loc().location(), option.loc().sequence()),
                option.loc().location()))
            {
                return Responses.chain(i);
            }
        }
        return chain.forced() && !chain.chains().isEmpty()
            ? Responses.chain(0) : Responses.chainDecline();""", "chain fallback")

sub(B, """            if(shouldExecute(exec, card, ExecutorType.ACTIVATE))
            {
                return Responses.yes();
            }
        }
        return Responses.no();""",
    """            if(shouldExecute(exec, card, ExecutorType.ACTIVATE))
            {
                return Responses.yes();
            }
        }
        // As above: a trigger that costs nothing is taken even unlisted.
        return executor.activateUnlistedOptional(card, effect.loc().location())
            ? Responses.yes() : Responses.no();""", "effect-yn fallback")

print("done")
