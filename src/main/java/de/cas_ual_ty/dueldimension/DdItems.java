package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.card.CardItem;
import de.cas_ual_ty.dueldimension.cardbinder.CardBinderItem;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxItem;
import de.cas_ual_ty.dueldimension.deckbox.PatreonDeckBoxItem;
import de.cas_ual_ty.dueldimension.set.CardSetItem;
import de.cas_ual_ty.dueldimension.set.OpenedCardSetItem;
import de.cas_ual_ty.dueldimension.simplebinder.SimpleBinderItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

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
 * What is new is {@code setId}: an item has to be told its own registry key
 * before it is built, so the key is made first and used twice.
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

    // A simple binder's size is six rows of nine per page, so binderSize is
    // 6 * 9 * pages. The Forge SimpleBinderItem.makeItem factory set the tab and
    // stack size; those are the register helper's job now (setId, tabs live in
    // DdItemGroup), so the item just takes its size.
    public static final SimpleBinderItem SIMPLE_BINDER_3 = register("simple_binder_3",
        properties -> new SimpleBinderItem(properties.stacksTo(1), 6 * 9 * 3));
    public static final SimpleBinderItem SIMPLE_BINDER_9 = register("simple_binder_9",
        properties -> new SimpleBinderItem(properties.stacksTo(1), 6 * 9 * 9));
    public static final SimpleBinderItem SIMPLE_BINDER_27 = register("simple_binder_27",
        properties -> new SimpleBinderItem(properties.stacksTo(1), 6 * 9 * 27));

    /**
     * Right-click any living thing to run the Seal of Orichalcos sequence on
     * it. A testing aid -- see {@link
     * de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem}.
     */
    public static final de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem
        ORICHALCOS_DEBUG = register("orichalcos_debug",
            properties -> new de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem(
                properties.stacksTo(1)));

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
        Identifier id = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        // The key goes into the properties as well as into the registry call:
        // an item is required to know its own id now, and building one without
        // saying so fails at construction rather than at registration.
        return Registry.register(BuiltInRegistries.ITEM, key,
            factory.apply(new Item.Properties().setId(key)));
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
