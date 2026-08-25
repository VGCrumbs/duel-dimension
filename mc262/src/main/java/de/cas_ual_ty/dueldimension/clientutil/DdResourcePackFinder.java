package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Offers the card-image pack to the game's pack list.
 * <p>
 * {@code loadPacks} lost its second argument: Forge's version was handed a
 * {@code Pack.PackConstructor} and used it to build the pack from metadata read
 * out of the pack itself. Here the pack is constructed directly, with its
 * metadata stated rather than parsed — which suits a pack that has no
 * {@code pack.mcmeta} to parse.
 * <p>
 * Fixed at the top of the list and required, as on Forge: this is not a
 * cosmetic pack a player chooses, it is where every card image comes from, and
 * a player who turned it off would see a screen of missing textures.
 */
public class DdResourcePackFinder implements RepositorySource
{
    /**
     * A pack id of its own, and this is not cosmetic.
     * <p>
     * {@code PackRepository.discoverAvailable()} keys packs by id in a map, and
     * Fabric already registers the mod's own resources under the bare mod id.
     * Reusing it here silently <b>replaced</b> that pack rather than adding to
     * it: every GUI texture, the player skins and the language file vanished,
     * and item names rendered as raw keys. The pack list is the place that shows
     * it -- the mod's own entry simply stops appearing.
     */
    private static final String PACK_ID = DuelDimension.MOD_ID + "_images";

    private static final PackLocationInfo LOCATION = new PackLocationInfo(
        PACK_ID,
        Component.literal("Duel Dimension Images"),
        PackSource.BUILT_IN,
        Optional.empty());

    private static final Pack.Metadata METADATA = new Pack.Metadata(
        Component.literal("All dynamically downloaded images of Duel Dimension."),
        PackCompatibility.COMPATIBLE,
        FeatureFlagSet.of(),
        List.of());

    @Override
    public void loadPacks(Consumer<Pack> consumer)
    {
        consumer.accept(new Pack(LOCATION, new Supplier(), METADATA,
            new PackSelectionConfig(true, Pack.Position.BOTTOM, false)));
    }

    /**
     * Opens the pack.
     * <p>
     * Both halves return the same thing. The split exists for packs that layer
     * an overlay on top of themselves depending on the game version; this one
     * is a folder, and a folder has no overlays.
     */
    private static final class Supplier implements Pack.ResourcesSupplier
    {
        @Override
        public PackResources openPrimary(PackLocationInfo location)
        {
            return new DdCardResourcePack(location);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata)
        {
            return new DdCardResourcePack(location);
        }
    }
}
