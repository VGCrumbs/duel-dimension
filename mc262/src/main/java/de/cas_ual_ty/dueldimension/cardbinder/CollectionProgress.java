package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;
import de.cas_ual_ty.dueldimension.rarity.RarityEntry;
import de.cas_ual_ty.dueldimension.set.CardSet;
import de.cas_ual_ty.dueldimension.set.Distribution;
import de.cas_ual_ty.dueldimension.set.DistributionCardPuller;
import de.cas_ual_ty.dueldimension.set.PullType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How much of each pack the player has collected.
 * <p>
 * Computed on the client from what it already holds — the profile's trunk is
 * synced for the deck editor, and the set database is loaded at startup — so
 * this needs no packet of its own. It is the player's own collection, so there
 * is nothing here they should not see either way.
 *
 * <h2>What counts as a pack</h2>
 * Sets you open and get a random result from. That is the pull type, not the
 * name: {@link PullType#DISTRIBUTION} rolls against a rarity distribution and
 * {@link PullType#COMPOSITION} is built out of other pulls, while
 * {@link PullType#FULL} simply hands over every card it contains — a Structure
 * or Starter Deck, which there is no more skill in completing than in buying.
 * Asking the puller is better than matching names: 254 sets are distribution
 * and 17 composition, across a dozen different type labels.
 * <p>
 * Sub-sets are excluded through the same {@code isIndependentAndItem} test the
 * rest of the mod uses, because their cards are counted again by the set that
 * contains them.
 *
 * <h2>What counts as complete</h2>
 * A printing, not a card. A set lists a card once per rarity it was printed at,
 * and each of those is its own thing to find — so a card printed at Common,
 * Rare and Ultra is three entries, and holding one copy completes one of them.
 * That is why {@link Trunk} records the rarity it pulled.
 */
public final class CollectionProgress
{
    /** One pack's progress. */
    public record Pack(CardSet set, String generation, int held, int total)
    {
        public float fraction()
        {
            return total <= 0 ? 0F : held / (float)total;
        }

        public boolean complete()
        {
            return total > 0 && held >= total;
        }
    }

    /** A group of packs, and their progress together. */
    public record Generation(String name, List<Pack> packs, int held, int total)
    {
        public float fraction()
        {
            return total <= 0 ? 0F : held / (float)total;
        }
    }

    /** The whole collection, grouped. */
    public record Summary(List<Generation> generations, int held, int total)
    {
        public float fraction()
        {
            return total <= 0 ? 0F : held / (float)total;
        }

        public int packCount()
        {
            int count = 0;
            for(Generation generation : generations)
            {
                count += generation.packs().size();
            }
            return count;
        }
    }

    private CollectionProgress()
    {
    }

    /** Whether this set is something you open for a random result. */
    public static boolean isPack(CardSet set)
    {
        if(set == null || !set.isIndependentAndItem() || set.pull == null)
        {
            return false;
        }
        // FULL hands over its whole contents, so there is nothing to collect
        // towards. Everything else rolls for what you get.
        return !(set.pull instanceof de.cas_ual_ty.dueldimension.set.FullCardPuller);
    }

    /**
     * The group a pack belongs to.
     * <p>
     * Taken from the set's own type, which for the booster packs literally
     * names the series — "Booster Pack (Series 9)" — and for everything else
     * names the line it belongs to. Derived from the data rather than a table
     * here, which would drift the moment the database gained a set.
     */
    public static String generationOf(CardSet set)
    {
        String type = set.type;
        if(type == null || type.isBlank())
        {
            return "Other";
        }
        // "Booster Pack (Series 9)" reads better as "Series 9" once the packs
        // are already grouped under a heading.
        int open = type.indexOf('(');
        int close = type.lastIndexOf(')');
        if(type.startsWith("Booster Pack") && open >= 0 && close > open)
        {
            return type.substring(open + 1, close).trim();
        }
        return type;
    }

    /**
     * Which of a card's printings in a set count as collected.
     * <p>
     * An exact match first: the collection says it holds this card at this
     * rarity, so that printing is found.
     * <p>
     * Then the copies whose rarity was never recorded. Every card collected
     * before the trunk started keeping rarities is one of these, and so is
     * anything from a source that has no rarity to give — so without this, a
     * long-standing collection reads as zero percent of everything, which is
     * both wrong and insulting. An unrecorded copy proves the player HAS the
     * card and not which printing, so it satisfies one printing, and two copies
     * satisfy two. It never credits more printings than there are copies, so
     * one card cannot complete a set's every rarity.
     * <p>
     * Which printing an unrecorded copy satisfies is decided by the order the
     * rarities arrive in, and {@link #printingsByCard} hands them over humblest
     * first. It used to be whichever printing the set's JSON happened to list
     * first, and for 429 of the 1377 multi-printing cards in the shipped packs
     * that is the loudest rarity in the set — so a card the player pulled at
     * Common was shown back to them as the Ghost Rare. Crediting the humblest
     * printing it could plausibly be is the claim the evidence supports, and it
     * is the one that cannot flatter.
     */
    public static Set<String> heldPrintings(Trunk trunk, int passcode, List<String> rarities)
    {
        Set<String> held = new java.util.LinkedHashSet<>();
        if(trunk == null)
        {
            return held;
        }
        for(String rarity : rarities)
        {
            if(trunk.has(passcode, rarity))
            {
                held.add(rarity);
            }
        }
        int unrecorded = trunk.countOf(passcode, Trunk.UNKNOWN_RARITY);
        // Humblest first, which is the order printingsByCard put them in.
        for(String rarity : rarities)
        {
            if(unrecorded <= 0)
            {
                break;
            }
            if(held.add(rarity))
            {
                unrecorded--;
            }
        }
        return held;
    }

    /**
     * The printings a set contains, grouped by card, each card's rarities
     * ordered from the humblest printing to the loudest.
     * <p>
     * That order is asked of the set's own pull data rather than listed here:
     * {@link #pullRates} works out how often a pack of THIS set yields a given
     * card at each of its rarities, and the likeliest is the printing an
     * unrecorded copy most plausibly is. Per set, not across the database,
     * because a rarity is only rare relative to what it is packed with —
     * Starfoil Rare is the exotic one almost everywhere, but Star Pack 2013
     * gives two of them per pack against one Common, so there it is the humble
     * one. A table written here could not know that, and would drift the moment
     * the database gained a rarity.
     */
    public static Map<Integer, List<String>> printingsByCard(CardSet set)
    {
        Map<Integer, List<String>> byCard = new LinkedHashMap<>();
        if(set.cards == null)
        {
            return byCard;
        }
        for(CardHolder held : set.cards)
        {
            if(held == null || held.getCard() == null)
            {
                continue;
            }
            String rarity = held.rarity == null ? "" : held.rarity;
            List<String> rarities = byCard.computeIfAbsent((int)held.getCard().getId(),
                code -> new ArrayList<>());
            // A set can list the same card at the same rarity twice, and a
            // player cannot tell those apart, so it is one thing to find.
            if(!rarities.contains(rarity))
            {
                rarities.add(rarity);
            }
        }

        Map<String, Double> rates = pullRates(set);
        for(List<String> rarities : byCard.values())
        {
            rarities.sort((left, right) ->
            {
                // Commonest first. A rarity the distribution never pulls scores
                // zero and lands at the back, which is right: an unrecorded copy
                // cannot have come out of this pack at that printing.
                int byRate = Double.compare(rates.getOrDefault(right, 0D), rates.getOrDefault(left, 0D));
                if(byRate != 0)
                {
                    return byRate;
                }
                // Then the foil the rarity is drawn with, fewest layers first --
                // a rarity the database has no entry for is printed plain, which
                // is as humble as a printing gets. This is what decides a set
                // with no distribution to ask. Then the name, so the answer does
                // not depend on the order the database happened to load in.
                int byFoil = Integer.compare(foilLayers(left), foilLayers(right));
                return byFoil != 0 ? byFoil : left.compareTo(right);
            });
        }
        return byCard;
    }

    /** How many foil layers a rarity is composited from; none if it has no entry. */
    private static int foilLayers(String rarity)
    {
        RarityEntry entry = DdDatabase.getRarity(rarity);
        return entry == null || entry.layers == null ? 0 : entry.layers.size();
    }

    /**
     * How often a pack of this set yields ONE GIVEN card at each rarity.
     * <p>
     * Straight out of the set's own distribution, which is the data the puller
     * rolls against: a pull is taken with odds {@code weight / totalWeight},
     * and each of its entries draws {@code count} cards from a pool holding
     * every card the set prints at any of that entry's rarities
     * ({@code DistributionCardPuller.makeCardPool}). The draw is uniform over
     * that pool, so each card in it is worth {@code count / poolSize} — which
     * is why this divides by the pool rather than counting the rarity's share
     * of it. Per card is the question being asked: whether the copy in hand is
     * more likely to be this printing or that one.
     * <p>
     * Battle Pack 3 is where the difference shows. It gives one Rare and one
     * Shatterfoil Rare per pack, so by the rarity those are equal — but it
     * prints 55 cards at Rare against 237 at Shatterfoil, so any particular
     * card is four times likelier to be the Rare.
     * <p>
     * Empty for a set with no distribution to ask. A COMPOSITION set carries no
     * cards of its own — all 17 shipped ones delegate to sub-sets — so it never
     * gets this far, and a FULL set is not a pack. Both then order on the foil
     * alone.
     */
    private static Map<String, Double> pullRates(CardSet set)
    {
        Map<String, Double> rates = new LinkedHashMap<>();
        if(!(set.pull instanceof DistributionCardPuller puller) || puller.distribution == null
            || puller.distribution.totalWeight <= 0)
        {
            return rates;
        }

        // What the puller would find in the pool: how many cards this set
        // prints at each rarity.
        Map<String, Integer> pool = new LinkedHashMap<>();
        for(CardHolder held : set.cards)
        {
            if(held != null && held.rarity != null)
            {
                pool.merge(held.rarity, 1, Integer::sum);
            }
        }

        Distribution distribution = puller.distribution;
        for(Distribution.Pull pull : distribution.pulls)
        {
            double odds = pull.weight / (double)distribution.totalWeight;
            for(Distribution.Pull.PullEntry entry : pull.pullEntries)
            {
                int poolSize = 0;
                for(String rarity : entry.rarities)
                {
                    poolSize += pool.getOrDefault(rarity, 0);
                }
                if(poolSize <= 0)
                {
                    // The set prints nothing at these rarities, so the entry
                    // yields nothing. The puller skips it for the same reason.
                    continue;
                }
                for(String rarity : entry.rarities)
                {
                    if(pool.getOrDefault(rarity, 0) > 0)
                    {
                        rates.merge(rarity, odds * entry.count / poolSize, Double::sum);
                    }
                }
            }
        }
        return rates;
    }

    /**
     * Works out where the collection stands.
     *
     * @param trunk the player's collection; null yields an empty summary rather
     *              than an exception, because the profile syncs after the screen
     *              can first be opened
     */
    public static Summary summarise(Trunk trunk)
    {
        Map<String, List<Pack>> grouped = new LinkedHashMap<>();
        int heldTotal = 0;
        int allTotal = 0;

        for(CardSet set : DdDatabase.SETS_LIST)
        {
            if(!isPack(set))
            {
                continue;
            }
            Map<Integer, List<String>> byCard = printingsByCard(set);
            if(byCard.isEmpty())
            {
                continue;   // nothing to collect; not worth a row
            }

            int held = 0;
            int printings = 0;
            for(Map.Entry<Integer, List<String>> card : byCard.entrySet())
            {
                printings += card.getValue().size();
                held += heldPrintings(trunk, card.getKey(), card.getValue()).size();
            }

            String generation = generationOf(set);
            grouped.computeIfAbsent(generation, name -> new ArrayList<>())
                .add(new Pack(set, generation, held, printings));
            heldTotal += held;
            allTotal += printings;
        }

        List<Generation> generations = new ArrayList<>();
        grouped.forEach((name, packs) ->
        {
            // Newest first inside a group: a player is likelier to be working on
            // something recent, and the database's order is arbitrary.
            packs.sort((left, right) ->
            {
                if(left.set().date == null || right.set().date == null)
                {
                    return left.set().name.compareToIgnoreCase(right.set().name);
                }
                return right.set().date.compareTo(left.set().date);
            });
            int held = 0;
            int total = 0;
            for(Pack pack : packs)
            {
                held += pack.held();
                total += pack.total();
            }
            generations.add(new Generation(name, packs, held, total));
        });
        generations.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));

        return new Summary(generations, heldTotal, allTotal);
    }
}
