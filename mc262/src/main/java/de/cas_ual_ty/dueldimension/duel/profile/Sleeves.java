package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * How a sleeve is named, written down, and owned.
 * <p>
 * One place for the three questions the deck, the profile, the shop and the
 * picker all ask, so none of them answers it differently.
 *
 * <h2>Why a name and never an index</h2>
 * {@link CardSleevesType} carries a byte index, and that index is only safe
 * where it is used today: inside a single transient packet, encoded and decoded
 * by the same build within one connection. The moment a sleeve is written to
 * disk the index becomes a promise that the enum's order will never change —
 * and the enum is a list of cosmetics that is expected to grow. A deck saved as
 * "index 27" would silently become a different sleeve the first time a constant
 * was inserted rather than appended. So a sleeve is stored by its {@code name}
 * — the same id its item, its texture and its lang key already use.
 *
 * <h2>Lenient on the way in, strict on the wire</h2>
 * {@link #CODEC} reads an id it does not recognise as the plain card back,
 * exactly as {@link DeckList.Origin#CODEC} reads an unknown origin as SAVED: a
 * sleeve that a build no longer has is not worth refusing to load a player's
 * whole profile over. {@link #byName} instead returns null, because a name that
 * arrives over the wire is a client asserting something and deserves a refusal
 * rather than a silent substitution.
 */
public final class Sleeves
{
    private Sleeves()
    {
    }

    /** What a deck wears until someone says otherwise. */
    public static final CardSleevesType DEFAULT = CardSleevesType.CARD_BACK;

    /**
     * Sleeves nobody has to buy.
     * <p>
     * Just the plain back now. It used to be the back plus the sixteen dye
     * colours, on the reasoning that flat dyed backs were not really art and
     * charging for all sixteen put a wall in front of "my deck looks like mine".
     * Those colours are gone -- the catalogue is Master Duel's protectors, every
     * one of which is drawn art -- so the exception has nothing left to apply
     * to, and the rule it made room for is the whole shop.
     * <p>
     * Free is a <em>rule</em> and not a stored grant: nothing is written to
     * disk, so it cannot be lost, cannot be duplicated by a double grant, and
     * costs a byte of nobody's save file. It is also why a constant appended to
     * the enum later is <b>not</b> free -- new art is paid art unless it is
     * listed here on purpose.
     */
    public static final Set<CardSleevesType> FREE = Collections.unmodifiableSet(EnumSet.of(
        CardSleevesType.CARD_BACK));

    /** The id a sleeve is stored and sent under. */
    public static String nameOf(CardSleevesType sleeve)
    {
        return (sleeve == null ? DEFAULT : sleeve).name;
    }

    /**
     * The sleeve with that id, or null if this build has no such sleeve.
     * <p>
     * The enum's own constant name is accepted too, which costs nothing: every
     * constant's id is its constant name lowercased, so the two can never
     * disagree about which sleeve is meant. It exists so a caller holding
     * {@code sleeve.name()} rather than {@code sleeve.name} gets the sleeve it
     * asked for instead of a refusal it will not understand.
     */
    public static CardSleevesType byName(String name)
    {
        if(name == null || name.isEmpty())
        {
            return null;
        }
        for(CardSleevesType sleeve : CardSleevesType.VALUES)
        {
            if(sleeve.name.equalsIgnoreCase(name) || sleeve.name().equalsIgnoreCase(name))
            {
                return sleeve;
            }
        }
        return null;
    }

    /** Whether this sleeve is owned by everyone, always, without buying it. */
    public static boolean isFree(CardSleevesType sleeve)
    {
        return sleeve == null || FREE.contains(sleeve);
    }

    /**
     * Whether a shop may stock this sleeve at all.
     * <p>
     * A free sleeve has nothing to sell, and a patron's sleeve is a thank-you
     * rather than a product — it is granted, never bought, which is what
     * {@link CardSleevesType#isPatreonReward} has always meant.
     */
    public static boolean isPurchasable(CardSleevesType sleeve)
    {
        return sleeve != null && !isFree(sleeve) && !sleeve.isPatreonReward;
    }

    /** How a sleeve is written down, on disk and on the wire alike. */
    public static final Codec<CardSleevesType> CODEC = Codec.STRING.xmap(name ->
    {
        CardSleevesType sleeve = byName(name);
        return sleeve == null ? DEFAULT : sleeve;
    }, Sleeves::nameOf);
}
