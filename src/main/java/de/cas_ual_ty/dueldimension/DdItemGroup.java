package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.duel.DeckSource;
import de.cas_ual_ty.dueldimension.deckbox.CustomDecks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
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
 * all.
 * <p>
 * Built with Fabric's builder rather than vanilla's, and that is not a
 * preference. Vanilla's takes a row and a column, and its own fourteen tabs
 * fill both rows of the strip completely -- there is no free slot to ask for.
 * A mod tab given one anyway lands on top of a vanilla tab, which is what the
 * first attempt did: the strip showed our title over the inventory's contents.
 * Fabric's builder puts mod tabs on pages of their own and gives the screen the
 * arrows to reach them, which is the only place they fit.
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
    public static final ResourceKey<CreativeModeTab> SETS = key("sets");

    private DdItemGroup()
    {
    }

    public static void register()
    {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN, FabricCreativeModeTab.builder()
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
                output.accept(DdBlocks.CARD_SUPPLY);
                output.accept(DdBlocks.CARD_SHOP);
                output.accept(DdItems.CARD_BINDER);
                output.accept(DdItems.SIMPLE_BINDER_3);
                output.accept(DdItems.SIMPLE_BINDER_9);
                output.accept(DdItems.SIMPLE_BINDER_27);
                output.accept(DdItems.DECK_BOX);
                output.accept(DdItems.PATREON_DECK_BOX);
                // The patreon decks are built from the database, which is not
                // loaded when the tab is registered. On Forge this walk lived in
                // PatreonDeckBoxItem.fillItemCategory.
                if(DdDatabase.databaseReady)
                {
                    for(DeckSource s : CustomDecks.getAllPatreonDeckSources())
                    {
                        output.accept(DdItems.PATREON_DECK_BOX.makeItemStackFromDeckSource(s));
                    }
                }
            })
            .build());

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CARDS, FabricCreativeModeTab.builder()
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

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SETS, FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(DdItems.BLANC_SET))
            .title(Component.translatable("itemGroup." + DuelDimension.MOD_ID + ".sets"))
            .displayItems((parameters, output) ->
            {
                // One entry per independent set in the database, which is loaded
                // from disk after startup. On Forge this walk lived in
                // CardSetItem.fillItemCategory, reached from the item's side.
                // Deduplicated by code, which Forge did not have to do. The
                // database ships two entries under YS15, and the stack built
                // from a set carries only its code -- so both produce the same
                // item. 1.19.2 listed it twice and shrugged; 26.2 throws
                // "Accidentally adding the same item stack twice" and the tab
                // is lost. One entry per code is what was meant either way.
                java.util.Set<String> listed = new java.util.HashSet<>();
                for(de.cas_ual_ty.dueldimension.set.CardSet set : DdDatabase.SETS_LIST)
                {
                    if(set.isIndependentAndItem() && listed.add(set.code))
                    {
                        output.accept(DdItems.SET.createItemForSet(set));
                    }
                }
            })
            .build());
    }
}
