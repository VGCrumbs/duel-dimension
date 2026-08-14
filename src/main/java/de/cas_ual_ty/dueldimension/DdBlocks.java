package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.cardsupply.CardSupplyBlock;
import de.cas_ual_ty.dueldimension.duel.overworld.arena.ArenaMarkerBlock;
import de.cas_ual_ty.dueldimension.duel.overworld.arena.ArenaMarkerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

/**
 * The mod's blocks.
 * <p>
 * Same three registry changes as {@link DdItems} — direct
 * {@link Registry#register}, the field is the block, {@code setId} is required —
 * plus two that are the block layer's own. {@code Material}/{@code MaterialColor}
 * were removed, so a block states its map colour, sound and strength
 * separately; and every block now supplies a {@code MapCodec} through
 * {@code codec()} (see {@link CardSupplyBlock}). Registering a block also
 * registers its {@link BlockItem}, which needs its own id in the same way.
 */
public final class DdBlocks
{
    // The two surfaces a duel is played on. One block entity serves both --
    // a playmat and a table are the same duel at different heights -- so their
    // only real difference is the shape, which is why DuelBlock takes one.
    public static final de.cas_ual_ty.dueldimension.duel.block.DuelBlock DUEL_PLAYMAT =
        register("duel_playmat", key -> new de.cas_ual_ty.dueldimension.duel.block.DuelBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F)
                .sound(SoundType.METAL).setId(key),
            Block.box(2D, 0, 2D, 14D, 1D, 14D)));

    public static final de.cas_ual_ty.dueldimension.duel.block.DuelBlock DUEL_TABLE =
        register("duel_table", key -> new de.cas_ual_ty.dueldimension.duel.block.DuelBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F)
                .sound(SoundType.METAL).setId(key),
            net.minecraft.world.phys.shapes.Shapes.or(
                Block.box(4, 3, 4, 12, 12.5, 12),
                Block.box(1, 0, 1, 15, 3, 15),
                Block.box(0, 13, 0, 16, 15, 16),
                Block.box(1, 12.5, 1, 15, 15.5, 15))));

    public static final CardSupplyBlock CARD_SUPPLY = register("card_supply",
        key -> new CardSupplyBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).setId(key)));

    // Forge's Material.METAL + MaterialColor.COLOR_BLUE became a MapColor plus
    // separate sound/strength. COLOR_BLUE is the shop's original map colour.
    public static final de.cas_ual_ty.dueldimension.shop.CardShopBlock CARD_SHOP = register("card_shop",
        key -> new de.cas_ual_ty.dueldimension.shop.CardShopBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE).strength(5.0F, 6.0F).sound(SoundType.METAL).setId(key)));

    // The counter beside it, selling sleeves rather than packs. The same
    // properties as the card shop down to the map colour: on a map the two are
    // one shop, and giving the second counter a different colour would draw a
    // seam through a building that does not have one.
    public static final de.cas_ual_ty.dueldimension.shop.SleeveShopBlock SLEEVE_SHOP =
        register("sleeve_shop",
            key -> new de.cas_ual_ty.dueldimension.shop.SleeveShopBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE).strength(5.0F, 6.0F).sound(SoundType.METAL)
                .setId(key)));

    /**
     * The four corners of a hand-built duel arena, and the two squares its
     * duellists stand on.
     * <p>
     * Registered with an item that refuses to place outside creative mode
     * rather than merely being hard to obtain: see {@link ArenaMarkerItem}.
     */
    public static final ArenaMarkerBlock ARENA_CORNER = registerMarker("arena_corner");

    public static final ArenaMarkerBlock ARENA_POINT = registerMarker("arena_point");

    private DdBlocks()
    {
    }

    private static ArenaMarkerBlock registerMarker(String name)
    {
        Identifier id = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ArenaMarkerBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new ArenaMarkerBlock(ArenaMarkerBlock.properties(blockKey)));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new ArenaMarkerItem(block,
            new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

        return block;
    }

    private static <T extends Block> T register(String name, Function<ResourceKey<Block>, T> factory)
    {
        Identifier id = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        T block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(blockKey));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

        return block;
    }

    /** Touching this class registers everything in it. */
    public static void register()
    {
    }
}
