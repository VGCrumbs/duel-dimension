package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.card.CardItem;
import de.cas_ual_ty.dueldimension.card.CardSleevesItem;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.cardbinder.CardBinderItem;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxItem;
import de.cas_ual_ty.dueldimension.deckbox.PatreonDeckBoxItem;
import de.cas_ual_ty.dueldimension.set.CardSetItem;
import de.cas_ual_ty.dueldimension.set.OpenedCardSetItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

/**
 * The mod's items.
 * <p>
 * Three things changed from the Forge version, and all three are the same
 * change: registration got simpler and more explicit.
 * <ul>
 * <li>No {@code DeferredRegister}. Registries are open while a mod initialiser
 *     runs, so an item is registered by calling {@link Registry#register}.</li>
 * <li>No {@code RegistryObject} to unwrap. The field IS the item, so a call
 *     that read {@code DdItems.CARD.get()} is now just {@code DdItems.CARD} —
 *     the indirection existed because Forge could not hand you the object
 *     yet.</li>
 * <li>No {@code .tab(...)}. An item does not choose its creative tab any more;
 *     a tab chooses its items. See {@link DdItemGroup}.</li>
 * </ul>
 * The registry key is built first because {@link Registry#register} takes one.
 * It is not handed to the item: {@code Item.Properties.setId} arrived in
 * 1.21.2, and here an item learns its id by being registered under it.
 */
public final class DdItems
{
    public static final CardItem CARD = register("card",
        properties -> new CardItem(properties.stacksTo(1)));

    public static final Item BLANC_CARD = register("blanc_card", CosmeticItem::new);
    public static final Item CARD_BACK = register("card_back", CosmeticItem::new);
    public static final Item BLANC_SET = register("blanc_set", CosmeticItem::new);

    public static final CardSetItem SET = register("set",
        properties -> new CardSetItem(properties.stacksTo(1)));
    public static final OpenedCardSetItem OPENED_SET = register("opened_set",
        properties -> new OpenedCardSetItem(properties.stacksTo(1)));

    public static final Item MILLENIUM_EYE = register("millennium_eye", one(CosmeticItem::new));
    public static final Item MILLENIUM_KEY = register("millennium_key", one(CosmeticItem::new));
    public static final Item MILLENIUM_NECKLACE =
        register("millennium_necklace", one(CosmeticItem::new));
    public static final Item MILLENIUM_PUZZLE = register("millennium_puzzle", one(CosmeticItem::new));
    public static final Item MILLENIUM_RING = register("millennium_ring", one(CosmeticItem::new));
    public static final Item MILLENIUM_ROD = register("millennium_rod", one(CosmeticItem::new));
    public static final Item MILLENIUM_SCALE = register("millennium_scale", one(CosmeticItem::new));

    /**
     * A testing tool: places duelists that stand still and duel on a world
     * board. Not obtainable outside creative, and it says what it is on the
     * tin -- see {@link de.cas_ual_ty.dueldimension.duel.npc.DuelistPlacerItem}.
     */
    public static final de.cas_ual_ty.dueldimension.duel.npc.DuelistPlacerItem DUELIST_PLACER =
        register("duelist_placer",
            de.cas_ual_ty.dueldimension.duel.npc.DuelistPlacerItem::new);

    /**
     * The Duel Bot, carried. One item is one bot: placing it puts that bot down
     * and picking it up gives this back, program and all.
     */
    public static final de.cas_ual_ty.dueldimension.duel.npc.DuelBotItem DUEL_BOT =
        register("duel_bot", de.cas_ual_ty.dueldimension.duel.npc.DuelBotItem::new);

    public static final DeckBoxItem DECK_BOX = register("deck_box",
        properties -> new DeckBoxItem(properties.stacksTo(1)));
    public static final PatreonDeckBoxItem PATREON_DECK_BOX = register("patreon_deck_box",
        properties -> new PatreonDeckBoxItem(properties.stacksTo(1)));

    // The duel disks. A disk held in the OFF hand is what opens a duel --
    // see DuelDiskItem.use -- so they stack to one, as the Forge tree had
    // them, and carry its names verbatim.
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem DUEL_DISK =
        register("duel_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem CHAOS_DISK =
        register("chaos_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem ACADEMIA_DISK =
        register("academia_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem ACADEMIA_DISK_RED =
        register("academia_disk_red", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem ACADEMIA_DISK_BLUE =
        register("academia_disk_blue", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem ACADEMIA_DISK_YELLOW =
        register("academia_disk_yellow", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem ROCK_SPIRIT_DISK =
        register("rock_spirit_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem TRUEMAN_DISK =
        register("trueman_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem JEWEL_DISK =
        register("jewel_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));
    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem KAIBAMAN_DISK =
        register("kaibaman_disk", one(de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem::new));

    public static final CardBinderItem CARD_BINDER = register("card_binder",
        properties -> new CardBinderItem(properties.stacksTo(1)));

// The three simple binders are gone. They differed only in page count and
    // all three were storage; the one binder left is a collection TRACKER, which
    // is a different job and does not want three sizes of itself.

    /**
     * Right-click any living thing to run the Seal of Orichalcos sequence on
     * it. A testing aid -- see {@link
     * de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem}.
     */
    public static final de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem
        ORICHALCOS_DEBUG = register("orichalcos_debug",
            properties -> new de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem(
                properties.stacksTo(1)));

    /**
     * Every sleeve in {@link CardSleevesType} except {@code CARD_BACK}, which is
     * the absence of sleeves rather than a design of them.
     * <p>
     * Ported from the Forge tree's static block verbatim in shape: the enum is
     * the list, so a new design is five words in one enum and nothing here.
     * Registering them by hand would be thirty-seven fields whose only job is to
     * repeat what the enum already says, and the first one anybody forgot would
     * be a sleeve that exists on the wire and nowhere in the registry.
     * <p>
     * Two Forge idioms had to move rather than translate. {@code .tab(...)} is
     * gone entirely -- a tab picks its items now -- and these are purchased
     * cosmetics, so no tab offers them. And {@code getRarity(ItemStack)} is no
     * longer an override point: rarity is a data component, so the patreon
     * sleeves get theirs from {@code Properties} here, once, at construction.
     */
    static
    {
        for(CardSleevesType sleeve : CardSleevesType.VALUES)
        {
            if(!sleeve.isCardBack())
            {
                register(sleeve.getResourceName(), properties -> new CardSleevesItem(
                    properties.stacksTo(1)
                        .rarity(sleeve.isPatreonReward ? Rarity.RARE : Rarity.COMMON),
                    sleeve));
            }
        }
    }

    private DdItems()
    {
    }

    /** Wraps a factory so the item stacks to one, which most of these do. */
    private static <T extends Item> Function<Item.Properties, T> one(
        Function<Item.Properties, T> factory)
    {
        return properties -> factory.apply(properties.stacksTo(1));
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory)
    {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        // 1.21.1 takes the id from the registry call alone: Item.Properties has
        // no setId until 1.21.2, and an item learns its own key by being
        // registered under it.
        return Registry.register(BuiltInRegistries.ITEM, key,
            factory.apply(new Item.Properties()));
    }

    /**
     * Touching this class registers everything in it. Called from the mod
     * initialiser so that happens at a known moment rather than whenever
     * something first mentions an item.
     */
    public static void register()
    {
    }
}
