package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdContainerTypes;
import de.cas_ual_ty.dueldimension.cardbinder.CardBinderScreen;
import de.cas_ual_ty.dueldimension.carditeminventory.CIIContainer;
import de.cas_ual_ty.dueldimension.carditeminventory.CIIScreen;
import de.cas_ual_ty.dueldimension.cardsupply.CardSupplyScreen;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxScreen;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Binds each container {@link net.minecraft.world.inventory.MenuType} to the
 * client screen that draws it — the client half of {@link DdContainerTypes},
 * called from the client initialiser.
 * <p>
 * Forge's {@code MenuScreens.register} is private on 26.2 and its
 * {@code ScreenConstructor} factory package-private, so both are reached through
 * this mod's access widener (see {@code dueldimension.accesswidener}); the
 * Fabric client screen-handler helper is intermediary-mapped and unusable here,
 * the same disappearance the server side already worked around.
 * <p>
 * The two duel menus that the Forge {@code ClientProxy} also bound
 * ({@code DUEL_BLOCK_CONTAINER}, {@code DUEL_ENTITY_CONTAINER}) belong to Casual
 * mode and are deferred; the card set, its contents view and the simple binder
 * all use the generic {@link CIIScreen}, exactly as Forge did.
 */
public final class DdScreens
{
    private DdScreens()
    {
    }

    public static void register()
    {
        MenuScreens.register(DdContainerTypes.CARD_BINDER, CardBinderScreen::new);
        MenuScreens.register(DdContainerTypes.DECK_BOX, DeckBoxScreen::new);
        MenuScreens.register(DdContainerTypes.CARD_SUPPLY, CardSupplyScreen::new);
        MenuScreens.<CIIContainer, CIIScreen<CIIContainer>>register(
            DdContainerTypes.CARD_SET, CIIScreen::new);
        MenuScreens.<CIIContainer, CIIScreen<CIIContainer>>register(
            DdContainerTypes.CARD_SET_CONTENTS, CIIScreen::new);
        MenuScreens.<CIIContainer, CIIScreen<CIIContainer>>register(
            DdContainerTypes.SIMPLE_BINDER, CIIScreen::new);
    }
}
