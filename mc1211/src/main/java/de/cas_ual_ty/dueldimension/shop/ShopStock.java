package de.cas_ual_ty.dueldimension.shop;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.set.CardSet;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the card shop sells, and what a pack costs.
 * <p>
 * The stock is the card database's own sets rather than a second list to keep
 * in step: a set that exists is a pack that can be bought. Only sets that pull
 * randomly are offered — a structure deck has fixed contents and is a different
 * kind of product, not a booster.
 * <p>
 * All of this is derived from a database that is loaded once and does not
 * change afterwards, so it is derived once and kept. That matters more than it
 * looks: working out a pack's size means actually rolling one, and building the
 * catalogue rolls every set in the game. Doing that on the server thread each
 * time a player opened the shop meant several hundred pack rolls per click,
 * with every other player waiting. {@link #invalidate()} exists for the one
 * moment the assumption stops holding — the database being reloaded.
 */
public final class ShopStock
{
    /** The base price of a pack, matching the reference's 150 DP. */
    public static final int BASE_PRICE = 150;

    /**
     * What a sleeve costs, flat.
     * <p>
     * <b>Its own constant, deliberately not {@link #BASE_PRICE}.</b> That number
     * is not a pack price either — {@link #priceOf} scales it by how many cards
     * a pack yields, so it is really a price per five cards. Borrowing it would
     * tie a cosmetic's price to the size of the game's booster packs, and a
     * database change that made packs bigger would silently reprice every
     * sleeve. A sleeve is one thing and costs one number.
     */
    public static final int SLEEVE_PRICE = 500;
    /** Premium deck cases are a larger cosmetic purchase than sleeves. */
    public static final int DECK_BOX_PRICE = 1000;

    /**
     * The catalogue, built on first use. Volatile because the server thread
     * builds it and the client thread of an integrated server may read it.
     */
    private static volatile List<Pack> catalogue;

    /** Pack size per set code; the value is the expensive part, not the lookup. */
    private static final Map<String, Integer> PACK_SIZES = new ConcurrentHashMap<>();

    /** Distinct card ids per set code, read every frame by the completion figure. */
    private static final Map<String, List<Integer>> CARD_IDS = new ConcurrentHashMap<>();

    /**
     * Forget everything derived from the database. Call when the database
     * itself has been rebuilt, which is the only way these answers can change.
     */
    public static void invalidate()
    {
        catalogue = null;
        PACK_SIZES.clear();
        CARD_IDS.clear();
        // A composition's contents are its sub-sets', so a rebuilt database can
        // change them without this set's own file changing at all.
        CONTENTS.clear();
    }

    /**
     * One purchasable product.
     *
     * @param deck     true for a structure or starter deck, which has fixed
     *                 contents rather than a random pull -- the shop shows that
     *                 difference rather than pretending everything is a booster
     * @param released when the set was printed, in epoch milliseconds, or 0 for
     *                 a set whose data carries no date. Sent as the instant
     *                 rather than as text so the client can write it the way
     *                 its own locale writes a date.
     */
    public record Pack(String code, String name, String type, int price, int cardsPerPack,
        int distinctCards, String description, boolean deck, long released)
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
            buffer.writeBoolean(deck);
            buffer.writeLong(released);
        }

        public static Pack read(FriendlyByteBuf buffer)
        {
            return new Pack(buffer.readUtf(32), buffer.readUtf(128), buffer.readUtf(64),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(256),
                buffer.readBoolean(), buffer.readLong());
        }
    }

    /**
     * One sleeve on sale.
     * <p>
     * Carries the sleeve's <b>id</b> and its price and nothing else. No display
     * name: a sleeve already has a translation key
     * ({@code item.dueldimension.sleeves_<name>}), so sending text would send
     * the server's language to a client that has its own. And no "owned" flag:
     * what a player owns lives on their profile, which is already synced to
     * them, and a second copy of a fact is a second copy to keep in step.
     *
     * @param sleeve {@link de.cas_ual_ty.dueldimension.duel.profile.Sleeves#nameOf}
     *               — the id, never the enum index, which is only ever safe
     *               inside a single connection
     */
    public record SleeveOffer(String sleeve, int price)
    {
        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeUtf(sleeve, 64);
            buffer.writeVarInt(price);
        }

        public static SleeveOffer read(FriendlyByteBuf buffer)
        {
            return new SleeveOffer(buffer.readUtf(64), buffer.readVarInt());
        }
    }

    /**
     * The sleeve catalogue, built on first use. Volatile for the reason
     * {@link #catalogue} is.
     */
    private static volatile List<SleeveOffer> sleeveCatalogue;

    /** A premium deck case on sale. */
    public record DeckBoxOffer(String deckBox, int price)
    {
        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeUtf(deckBox, 64);
            buffer.writeVarInt(price);
        }

        public static DeckBoxOffer read(FriendlyByteBuf buffer)
        {
            return new DeckBoxOffer(buffer.readUtf(64), buffer.readVarInt());
        }
    }

    private static volatile List<DeckBoxOffer> deckBoxCatalogue;

    /** A disk on sale: the id it is registered under, and what it costs. */
    public record DiskOffer(String disk, int price)
    {
        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeUtf(disk, 64);
            buffer.writeVarInt(price);
        }

        public static DiskOffer read(FriendlyByteBuf buffer)
        {
            return new DiskOffer(buffer.readUtf(64), buffer.readVarInt());
        }
    }

    private static volatile List<DiskOffer> diskCatalogue;

    /**
     * What a disk costs.
     * <p>
     * Priced in tiers rather than flat, because these are not interchangeable
     * recolours the way the sleeve set is: the Academia disks are a uniform a
     * player picks a house from, while Trueman and Kaibaman are the ones worth
     * saving for. The free plain disk is priced at nothing here as well as
     * being refused by the shop, so the two answers cannot drift apart.
     * <p>
     * The one place the figure is worked out, called both to fill the shop
     * window and to take the money.
     */
    public static int priceOfDisk(String name)
    {
        if(!de.cas_ual_ty.dueldimension.duel.profile.DuelDisks.isPurchasable(name))
        {
            return 0;
        }
        return switch(name)
        {
            case "academia_disk", "academia_disk_red", "academia_disk_blue",
                "academia_disk_yellow" -> 1500;
            case "rock_spirit_disk", "jewel_disk" -> 2500;
            case "chaos_disk" -> 4000;
            case "trueman_disk", "kaibaman_disk" -> 6000;
            // A disk added later is stock at the middle price rather than free:
            // new art is paid art unless DuelDisks.FREE says otherwise.
            default -> 2500;
        };
    }

    /**
     * Every disk on sale, in the order {@code DuelDisks.ALL} lists them, which
     * is the order the grid lays them out. Built from the registry so a disk
     * added there appears here without this file mentioning it.
     */
    public static List<DiskOffer> disks()
    {
        List<DiskOffer> known = diskCatalogue;
        if(known != null)
        {
            return known;
        }
        List<DiskOffer> offers = new ArrayList<>();
        for(String disk : de.cas_ual_ty.dueldimension.duel.profile.DuelDisks.ALL)
        {
            offers.add(new DiskOffer(disk, priceOfDisk(disk)));
        }
        known = List.copyOf(offers);
        diskCatalogue = known;
        return known;
    }

    private ShopStock()
    {
    }

    /**
     * Every sleeve on sale, in enum order.
     * <p>
     * Enum order rather than by price or by name: they are all the same price,
     * and the enum's order is how the art was authored — the metals together,
     * the series art together, the Millenium set together. A shop that
     * reshuffled them would be harder to shop in, not easier.
     * <p>
     * Derived from {@link de.cas_ual_ty.dueldimension.duel.profile.Sleeves#isPurchasable},
     * which is the one place that decides what may be sold: the free dye
     * colours are not stock, and a patron's sleeve is a thank-you rather than a
     * product. Nothing here enumerates sleeves by hand, so the five Millenium
     * constants appear in the shop without this file mentioning them.
     */
    public static List<SleeveOffer> sleeves()
    {
        List<SleeveOffer> known = sleeveCatalogue;
        if(known != null)
        {
            return known;
        }
        List<SleeveOffer> offers = new ArrayList<>();
        for(de.cas_ual_ty.dueldimension.card.CardSleevesType sleeve
            : de.cas_ual_ty.dueldimension.card.CardSleevesType.VALUES)
        {
            if(de.cas_ual_ty.dueldimension.duel.profile.Sleeves.isPurchasable(sleeve))
            {
                offers.add(new SleeveOffer(
                    de.cas_ual_ty.dueldimension.duel.profile.Sleeves.nameOf(sleeve),
                    priceOfSleeve(sleeve)));
            }
        }
        known = List.copyOf(offers);
        sleeveCatalogue = known;
        return known;
    }

    /**
     * What the server will charge for a sleeve.
     * <p>
     * The one place the figure is worked out, called both to fill the shop
     * window and to take the money, so what is shown and what is charged cannot
     * drift apart. Nothing a client sends reaches this — the purchase names a
     * sleeve and the price is looked up here.
     */
    public static int priceOfSleeve(de.cas_ual_ty.dueldimension.card.CardSleevesType sleeve)
    {
        return de.cas_ual_ty.dueldimension.duel.profile.Sleeves.isPurchasable(sleeve)
            ? SLEEVE_PRICE : 0;
    }

    public static int priceOfDeckBox(
        de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle style)
    {
        return style != null && style.isPurchasable() ? DECK_BOX_PRICE : 0;
    }

    public static List<DeckBoxOffer> deckBoxes()
    {
        List<DeckBoxOffer> known = deckBoxCatalogue;
        if(known != null)
        {
            return known;
        }
        List<DeckBoxOffer> offers = new ArrayList<>();
        for(de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle style
            : de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle.values())
        {
            if(style.isPurchasable())
            {
                offers.add(new DeckBoxOffer(style.name(), priceOfDeckBox(style)));
            }
        }
        // By the Master Duel texture id, not by enum order. The enum is already
        // declared in that order, but an insertion in the wrong place would
        // silently unsort the shelf and nothing would fail -- so the order is
        // stated where it is relied on rather than assumed from the declaration.
        offers.sort(java.util.Comparator.comparing(offer ->
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle style =
                de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle.known(offer.deckBox());
            return style == null ? "" : style.deckCaseId();
        }));
        // By the Master Duel texture id, not by enum order. The enum is already
        // declared in that order, but an insertion in the wrong place would
        // silently unsort the shelf and nothing would fail -- so the order is
        // stated where it is relied on rather than assumed from the declaration.
        offers.sort(java.util.Comparator.comparing(offer ->
        {
            de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle style =
                de.cas_ual_ty.dueldimension.duel.profile.DeckBoxStyle.known(offer.deckBox());
            return style == null ? "" : style.deckCaseId();
        }));
        known = List.copyOf(offers);
        deckBoxCatalogue = known;
        return known;
    }

    /** Every pack on sale, newest first, since that is what a shop leads with. */
    public static List<Pack> available()
    {
        List<Pack> known = catalogue;
        if(known != null)
        {
            return known;
        }
        // Two players opening the shop in the same tick could both find this
        // empty and both build it. That is wasteful once and harmless — the
        // answer is the same either way — which is cheaper than holding a lock
        // across several hundred pack rolls.
        known = build();
        catalogue = known;
        return known;
    }

    private static List<Pack> build()
    {
        List<Pack> packs = new ArrayList<>();
        for(CardSet set : DdDatabase.SETS_LIST)
        {
            if(set == null || set == CardSet.DUMMY || !set.isIndependentAndItem())
            {
                continue;
            }
            // Asked of the CONTENTS, not of the set's own card list: a
            // composition -- a tin, a box, a movie pack -- carries none of its
            // own and would be skipped here for being empty when it is not.
            if(set.pull == null || contentsOf(set).isEmpty())
            {
                continue;
            }
            // Decks are sold too, they simply are not boosters: fixed contents,
            // and opening one grants the deck and its recipe as well as the
            // cards. The flag lets the shop say which it is.
            boolean deck = isDeckProduct(set);
            packs.add(new Pack(set.code, set.name, set.type == null ? "" : set.type,
                priceOf(set), cardsPerPack(set), distinctCards(set), describe(set), deck,
                set.date == null ? 0L : set.date.getTime()));
        }
        packs.sort((left, right) -> right.code().compareTo(left.code()));
        // Shared and long-lived, so it is handed out read-only rather than
        // trusting every caller not to sort it.
        return List.copyOf(packs);
    }

    /**
     * Whether this product is a deck rather than a booster.
     * <p>
     * Asked in one place because two places were asking it: the shop, to label
     * the product, and nothing at all on the selling side -- which is how a
     * bought deck came out as a pile of its own cards and no deck. A test that
     * decides what something IS should not be written twice.
     */
    public static boolean isDeckProduct(CardSet set)
    {
        return set != null && set.type != null
            && (set.type.contains("Structure Deck") || set.type.contains("Starter Deck"));
    }

    /** Which group a granted deck belongs to; starter decks have their own. */
    public static boolean isStarterProduct(CardSet set)
    {
        return set != null && set.type != null && set.type.contains("Starter Deck");
    }

    public static CardSet setOf(String code)
    {
        // The set list is keyed by code and kept sorted, so this is a binary
        // search rather than the walk over every set it used to be. The shop
        // screen asks per pack per frame, which made the difference visible.
        return code == null ? null : DdDatabase.SETS_LIST.get(code);
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

    /** True for a fixed-contents product rather than a random pull. */
    public static boolean isDeck(CardSet set)
    {
        return set != null && set.type != null
            && (set.type.contains("Structure Deck") || set.type.contains("Starter Deck"));
    }

    /**
     * How many cards a pack yields, read from the distribution that actually
     * rolls it rather than assumed to be five.
     */
    public static int cardsPerPack(CardSet set)
    {
        if(set == null || set.code == null)
        {
            return 5;
        }
        return PACK_SIZES.computeIfAbsent(set.code, code -> roll(set));
    }

    private static int roll(CardSet set)
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
    /**
     * Every card this product can yield, its sub-sets' cards included.
     * <p>
     * <b>A composition holds no cards of its own.</b> Its contents are whatever
     * its sub-sets contain -- a tin's cards live in the five boosters it lists,
     * and {@code MVP1_SE_B} has never had a {@code cards} array either. Four
     * things here read {@code set.cards} directly and so saw every one of those
     * products as empty: the shelf skipped them entirely, and had they got
     * past, the blurb would have named nothing and completion would have read
     * zero out of zero.
     * <p>
     * Cached by code, because the shelf asks once per set when it builds and
     * the completion figure asks every frame for the selected one; the union
     * walks each sub-set in turn and is not something to do sixty times a
     * second.
     */
    public static List<CardHolder> contentsOf(CardSet set)
    {
        if(set == null || set.code == null)
        {
            return List.of();
        }
        if(!(set.pull instanceof de.cas_ual_ty.dueldimension.set.CompositionCardPuller))
        {
            return set.cards == null ? List.of() : set.cards;
        }
        return CONTENTS.computeIfAbsent(set.code,
            code -> List.copyOf(set.getAllCardEntries()));
    }

    private static final java.util.Map<String, List<CardHolder>> CONTENTS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static int distinctCards(CardSet set)
    {
        Set<Long> ids = new LinkedHashSet<>();
        for(CardHolder card : contentsOf(set))
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
        if(set == null || set.code == null)
        {
            return List.of();
        }
        // The shop draws a completion percentage for the selected pack every
        // frame, so this used to rebuild a set of ids sixty times a second.
        return CARD_IDS.computeIfAbsent(set.code, code ->
        {
            Set<Integer> ids = new LinkedHashSet<>();
            for(CardHolder card : contentsOf(set))
            {
                if(card != null && card.getCard() != null)
                {
                    ids.add((int)card.getCard().getId());
                }
            }
            return List.copyOf(ids);
        });
    }

    /**
     * The shop blurb. The reference teases two cards by name — "This Volume has
     * Powerful Cards such as Trap Hole and Fissure!" — so this does the same,
     * naming the rarest two rather than the first two found.
     */
    private static String describe(CardSet set)
    {
        List<String> headliners = new ArrayList<>();
        for(CardHolder card : contentsOf(set))
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
