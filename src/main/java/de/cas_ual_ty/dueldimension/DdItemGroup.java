package de.cas_ual_ty.dueldimension;

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
    // The CARDS and SETS tab keys are gone with their tabs. Nothing else
    // referenced them; a registry key kept for a tab that is never registered
    // is a name that resolves to nothing.

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
                output.accept(DdItems.MILLENIUM_EYE);
                output.accept(DdItems.MILLENIUM_KEY);
                output.accept(DdItems.MILLENIUM_NECKLACE);
                output.accept(DdItems.MILLENIUM_PUZZLE);
                output.accept(DdItems.MILLENIUM_RING);
                output.accept(DdItems.MILLENIUM_ROD);
                output.accept(DdItems.MILLENIUM_SCALE);
                output.accept(DdBlocks.CARD_SUPPLY);
                output.accept(DdBlocks.CARD_SHOP);
                output.accept(DdBlocks.SLEEVE_SHOP);
                output.accept(DdItems.DUEL_DISK);
                output.accept(DdItems.CHAOS_DISK);
                output.accept(DdItems.ACADEMIA_DISK);
                output.accept(DdItems.ACADEMIA_DISK_RED);
                output.accept(DdItems.ACADEMIA_DISK_BLUE);
                output.accept(DdItems.ACADEMIA_DISK_YELLOW);
                output.accept(DdItems.ROCK_SPIRIT_DISK);
                output.accept(DdItems.TRUEMAN_DISK);
                output.accept(DdItems.JEWEL_DISK);
                output.accept(DdItems.KAIBAMAN_DISK);
                output.accept(DdItems.CARD_BINDER);
                output.accept(DdItems.ORICHALCOS_DEBUG);
            })
            .build());

        // The CARDS and SETS tabs are gone, along with the blank card, the
        // blank pack, the deck boxes and the patreon deck walk.
        //
        // Every one of them was a way to conjure something the mod now has a
        // system for: cards come from packs and the shop, packs come from the
        // card supply, decks are built in the deck editor. A creative tab
        // holding one entry per card in a ten-thousand card database was also
        // the slowest thing in the menu, rebuilt from the database on every
        // open, and it is where the missing-texture squares were coming from --
        // a set whose art had not been derived yet has nothing to draw.
        //
        // What stays is what has no other source: the Millennium items, the
        // duel disks, the two blocks, the binders and the debug item.
    }
}
