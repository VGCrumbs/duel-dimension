package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.cardbinder.CardBinderContainer;
import de.cas_ual_ty.dueldimension.cardsupply.CardSupplyContainer;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxContainer;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxItem;
import de.cas_ual_ty.dueldimension.net.MenuData;
import de.cas_ual_ty.dueldimension.set.CardSetContainer;
import de.cas_ual_ty.dueldimension.set.CardSetContentsContainer;
import de.cas_ual_ty.dueldimension.simplebinder.SimpleBinderContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * The mod's menu types.
 * <p>
 * Forge's {@code DeferredRegister} plus {@code IContainerFactory} became two
 * plain things: {@link Registry#register} for the id, and {@link MenuData} for
 * the extra data an {@code IContainerFactory} used to carry. Every menu that
 * needs data reads it from {@link MenuData#pending} in its factory here, so the
 * container's own {@code FriendlyByteBuf} constructor is unchanged.
 * <p>
 * The {@link MenuType} constructor is private on 26.2, so it is reached through
 * this mod's access widener (see {@code dueldimension.accesswidener}); there is
 * no Fabric helper for a plain menu on this version.
 */
public final class DdContainerTypes
{
    public static final MenuType<CardSupplyContainer> CARD_SUPPLY = register("card_supply",
        (id, inv) -> new CardSupplyContainer(DdContainerTypes.CARD_SUPPLY, id, inv,
            MenuData.pending(inv.player.registryAccess())));

    // The deck box takes no extra buffer data: its active stack is resolved
    // server-side from the player, so no MenuData is needed ahead of it.
    public static final MenuType<DeckBoxContainer> DECK_BOX = register("deck_box",
        (id, inv) -> new DeckBoxContainer(DdContainerTypes.DECK_BOX, id, inv,
            DeckBoxItem.getActiveDeckBox(inv.player)));

    // Both set menus carry the handler size (and, for the held card set, which
    // hand) in the MenuData buffer, read here into the FriendlyByteBuf
    // constructor exactly as the Forge IContainerFactory buffer was.
    public static final MenuType<CardSetContainer> CARD_SET = register("card_set",
        (id, inv) -> new CardSetContainer(DdContainerTypes.CARD_SET, id, inv,
            MenuData.pending(inv.player.registryAccess())));

    public static final MenuType<CardSetContentsContainer> CARD_SET_CONTENTS = register("card_set_contents",
        (id, inv) -> new CardSetContentsContainer(DdContainerTypes.CARD_SET_CONTENTS, id, inv,
            MenuData.pending(inv.player.registryAccess())));

    // The card binder takes no extra buffer data: like the deck box, its active
    // stack and manager are resolved server-side from the player, so no MenuData
    // is needed ahead of it.
    public static final MenuType<CardBinderContainer> CARD_BINDER = register("card_binder",
        (id, inv) -> new CardBinderContainer(DdContainerTypes.CARD_BINDER, id, inv));

    // The simple binder carries the handler size and which hand in the MenuData
    // buffer (via HeldCIIContainer), read here into the FriendlyByteBuf
    // constructor exactly as the Forge IContainerFactory buffer was.
    public static final MenuType<SimpleBinderContainer> SIMPLE_BINDER = register("simple_binder",
        (id, inv) -> new SimpleBinderContainer(DdContainerTypes.SIMPLE_BINDER, id, inv,
            MenuData.pending(inv.player.registryAccess())));

    // The two duel surfaces. Both take their target out of the MenuData buffer
    // the same way the binders do: a block position for the block, an entity id
    // for the disk.
    public static final MenuType<de.cas_ual_ty.dueldimension.duel.block.DuelBlockContainer>
        DUEL_BLOCK_CONTAINER = register("duel_block_container",
            (id, inv) -> new de.cas_ual_ty.dueldimension.duel.block.DuelBlockContainer(
                DdContainerTypes.DUEL_BLOCK_CONTAINER, id, inv,
                MenuData.pending(inv.player.registryAccess())));

    public static final MenuType<de.cas_ual_ty.dueldimension.duel.dueldisk.DuelEntityContainer>
        DUEL_ENTITY_CONTAINER = register("duel_entity_container",
            (id, inv) -> new de.cas_ual_ty.dueldimension.duel.dueldisk.DuelEntityContainer(
                DdContainerTypes.DUEL_ENTITY_CONTAINER, id, inv,
                MenuData.pending(inv.player.registryAccess())));

    private DdContainerTypes()
    {
    }

    private static <T extends AbstractContainerMenu> MenuType<T> register(
        String name, MenuType.MenuSupplier<T> supplier)
    {
        MenuType<T> menuType = new MenuType<>(supplier, FeatureFlags.VANILLA_SET);
        Registry.register(BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name), menuType);
        return menuType;
    }

    /** Touching this class registers everything in it. */
    public static void register()
    {
    }
}
