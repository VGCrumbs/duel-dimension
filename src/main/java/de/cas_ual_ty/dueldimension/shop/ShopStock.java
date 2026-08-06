package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the card shop sells, and what a pack costs.
 * <p>
 * The stock is the card database's own sets rather than a second list to keep
 * in step: a set that exists is a pack that can be bought. Only sets that pull
 * randomly are offered — a structure deck has fixed contents and is a different
 * kind of product, not a booster.
 */
public final class ShopStock
{
    /** The base price of a pack, matching the reference's 150 DP. */
    public static final int BASE_PRICE = 150;

    /** One purchasable pack, as the shop screen needs it. */
    public record Pack(String code, String name, String type, int price, int cardsPerPack,
        int distinctCards, String description)
    {
        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeUtf(code, 32);
            buffer.writeUtf(name, 128);
            buffer.writeUtf(type, 64);
            buffer.writeVarInt(price);
            buffer.writeVarInt(cardsPerPack);
            buffer.writeVarInt(distinctCards);
            buffer.writeUtf(description, 256);
        }

        public static Pack read(FriendlyByteBuf buffer)
        {
            return new Pack(buffer.readUtf(32), buffer.readUtf(128), buffer.readUtf(64),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(256));
        }
    }

    private ShopStock()
    {
    }

    /** Every pack on sale, newest first, since that is what a shop leads with. */
    public static List<Pack> available()
    {
        List<Pack> packs = new ArrayList<>();
        for(CardSet set : DdDatabase.SETS_LIST)
        {
            if(set == null || set == CardSet.DUMMY || !set.isIndependentAndItem())
            {
                continue;
            }
            // A pack is something you open for a random handful. A structure or
            // starter deck has fixed contents, so it belongs in a different
            // part of the shop rather than being sold as a booster.
            if(set.pull == null || set.cards == null || set.cards.isEmpty())
            {
                continue;
            }
            if(set.type != null && (set.type.contains("Structure Deck") || set.type.contains("Starter Deck")))
            {
                continue;
            }
            packs.add(new Pack(set.code, set.name, set.type == null ? "" : set.type,
                priceOf(set), cardsPerPack(set), distinctCards(set), describe(set)));
        }
        packs.sort((left, right) -> right.code().compareTo(left.code()));
        return packs;
    }

    public static CardSet setOf(String code)
    {
        for(CardSet set : DdDatabase.SETS_LIST)
        {
            if(set != null && code.equals(set.code))
            {
                return set;
            }
        }
        return null;
    }

    /**
     * A pack's price. Flat by default, as the reference is, with bigger packs
     * costing proportionally more so a 60-card set is not the same 150 as a
     * five-card one.
     */
    public static int priceOf(CardSet set)
    {
        int perPack = cardsPerPack(set);
        return Math.max(50, BASE_PRICE * Math.max(1, perPack) / 5);
    }

    /**
     * How many cards a pack yields, read from the distribution that actually
     * rolls it rather than assumed to be five.
     */
    public static int cardsPerPack(CardSet set)
    {
        try
        {
            List<net.minecraft.world.item.ItemStack> sample = set.open(new java.util.Random(0));
            if(sample != null && !sample.isEmpty())
            {
                return sample.size();
            }
        }
        catch(Exception cannotRoll)
        {
            // A set whose distribution will not roll is described by its own
            // card count instead of failing the whole shop listing.
        }
        return 5;
    }

    /** Distinct cards in the set, which is the denominator of the completion figure. */
    public static int distinctCards(CardSet set)
    {
        Set<Long> ids = new LinkedHashSet<>();
        for(CardHolder card : set.cards)
        {
            if(card != null && card.getCard() != null)
            {
                ids.add(card.getCard().getId());
            }
        }
        return ids.size();
    }

    /** Every distinct card id in the set, for measuring collection completeness. */
    public static List<Integer> cardIds(CardSet set)
    {
        Set<Integer> ids = new LinkedHashSet<>();
        for(CardHolder card : set.cards)
        {
            if(card != null && card.getCard() != null)
            {
                ids.add((int)card.getCard().getId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * The shop blurb. The reference teases two cards by name — "This Volume has
     * Powerful Cards such as Trap Hole and Fissure!" — so this does the same,
     * naming the rarest two rather than the first two found.
     */
    private static String describe(CardSet set)
    {
        List<String> headliners = new ArrayList<>();
        for(CardHolder card : set.cards)
        {
            if(card == null || card.getCard() == null)
            {
                continue;
            }
            String rarity = card.getRarity();
            if(rarity != null && !rarity.equalsIgnoreCase("Common") && !rarity.equalsIgnoreCase("C"))
            {
                headliners.add(card.getCard().getName());
            }
            if(headliners.size() >= 2)
            {
                break;
            }
        }
        if(headliners.size() < 2)
        {
            for(CardHolder card : set.cards)
            {
                if(card != null && card.getCard() != null && !headliners.contains(card.getCard().getName()))
                {
                    headliners.add(card.getCard().getName());
                }
                if(headliners.size() >= 2)
                {
                    break;
                }
            }
        }
        if(headliners.isEmpty())
        {
            return "A selection of cards.";
        }
        if(headliners.size() == 1)
        {
            return "This set has powerful cards such as " + headliners.get(0) + "!";
        }
        return "This set has powerful cards such as " + headliners.get(0)
            + " and " + headliners.get(1) + "!";
    }
}
