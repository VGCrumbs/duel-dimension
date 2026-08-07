package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.card.properties.Properties;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The mod's creative tabs.
 * <p>
 * Inverted from the Forge version, and this is the interesting part of the
 * whole registry port. There, an item named its tab in its properties
 * ({@code .tab(DuelDimension.ydmItemGroup)}) and a tab that wanted more than
 * its members -- every card in the database, say -- overrode
 * {@code fillItemCategory} on the item.
 * <p>
 * Now a tab supplies its own contents, and an item says nothing about tabs at
 * all. Built with vanilla's own {@code CreativeModeTab.builder}: Fabric API
 * used to ship a {@code FabricItemGroup} helper for this and has dropped it,
 * because vanilla's builder now does the whole job.
 * <p>
 * Which is the better way round for this mod in particular: the cards tab holds
 * one entry per card in a database that is read from disk after startup, so
 * "what is in this tab" was never really a property of the item, and is now not
 * modelled as one.
 */
public final class DdItemGroup
{
    private static ResourceKey<CreativeModeTab> key(String name)
    {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name));
    }

    public static final ResourceKey<CreativeModeTab> MAIN = key("main");
    public static final ResourceKey<CreativeModeTab> CARDS = key("cards");

    private DdItemGroup()
    {
    }

    public static void register()
    {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN, CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .icon(() -> new ItemStack(DdItems.MILLENIUM_PUZZLE))
            .title(Component.translatable("itemGroup." + DuelDimension.MOD_ID + ".main"))
            .displayItems((parameters, output) ->
            {
                output.accept(DdItems.BLANC_CARD);
                output.accept(DdItems.CARD_BACK);
                output.accept(DdItems.BLANC_SET);
                output.accept(DdItems.MILLENIUM_EYE);
                output.accept(DdItems.MILLENIUM_KEY);
                output.accept(DdItems.MILLENIUM_NECKLACE);
                output.accept(DdItems.MILLENIUM_PUZZLE);
                output.accept(DdItems.MILLENIUM_RING);
                output.accept(DdItems.MILLENIUM_ROD);
                output.accept(DdItems.MILLENIUM_SCALE);
            })
            .build());

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CARDS, CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .icon(() -> new ItemStack(DdItems.CARD))
            .title(Component.translatable("itemGroup." + DuelDimension.MOD_ID + ".cards"))
            .displayItems((parameters, output) ->
            {
                // Built from the database each time the tab is opened, because
                // the database is not loaded when the tab is registered. On
                // Forge this was CardItem.fillItemCategory doing the same walk
                // from the item's side.
                for(Properties card : DdDatabase.PROPERTIES_LIST)
                {
                    if(card == null || card.getId() <= 0)
                    {
                        continue;
                    }
                    output.accept(DdItems.CARD.createItemForCard(card));
                }
            })
            .build());
    }
}
