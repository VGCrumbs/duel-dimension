package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem;

import java.util.List;
import java.util.Set;

/**
 * How a duel disk is named, written down, and owned.
 * <p>
 * The same three questions {@link Sleeves} answers for sleeves, answered once
 * here so the profile, the shop, the hotkey and the picker cannot disagree.
 *
 * <h2>Names all the way down, items only at the edges</h2>
 * Everything here is a {@code String} id -- the same id the item is registered,
 * modelled and translated under. Nothing in this class touches the item
 * registry until someone actually asks for an {@link DuelDiskItem}, and that
 * matters for a reason found the hard way: {@link DuelProfile}'s Codec
 * references this class, so a static field here that read {@code DdItems} made
 * the whole profile unloadable anywhere the game's registries are not up --
 * which is every pure-Java unit test in this repo.
 * <p>
 * It is also the same argument {@link Sleeves} makes against indices. A name
 * survives a list being reordered, a build being downgraded, and a registry
 * that has not been populated yet.
 */
public final class DuelDisks
{
    private DuelDisks()
    {
    }

    /** What every duelist carries until they buy otherwise. */
    public static final String DEFAULT = "duel_disk";

    /**
     * Every disk, in the order the shop lays them out. The plain one leads
     * because it is the one everybody already has; the rest are grouped by the
     * series they came from rather than by price, so the grid reads as a
     * collection instead of a ladder.
     */
    public static final List<String> ALL = List.of(
        "duel_disk",
        "chaos_disk",
        "academia_disk",
        "academia_disk_red",
        "academia_disk_blue",
        "academia_disk_yellow",
        "rock_spirit_disk",
        "trueman_disk",
        "jewel_disk",
        "kaibaman_disk");

    /**
     * Disks nobody has to buy.
     * <p>
     * Exactly one, and deliberately: a player who owns no disk cannot duel at
     * all, so the plain disk is not a cosmetic but the entry ticket. Free is a
     * <em>rule</em> and never a stored grant -- nothing is written to disk, so
     * it cannot be lost, cannot be double-granted, and a profile that somehow
     * lost every grant still has this one.
     */
    public static final Set<String> FREE = Set.of(DEFAULT);

    public static boolean isKnown(String name)
    {
        return name != null && ALL.contains(name);
    }

    public static boolean isFree(String name)
    {
        return FREE.contains(name);
    }

    /** Everything this build knows except the free one is stock. */
    public static boolean isPurchasable(String name)
    {
        return isKnown(name) && !isFree(name);
    }

    /**
     * The item for this id, or null. Resolved through the registry, so this is
     * the one method here that may not be called before the game has one.
     */
    public static DuelDiskItem item(String name)
    {
        if(!isKnown(name))
        {
            return null;
        }
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, name));
        return item instanceof DuelDiskItem disk ? disk : null;
    }

    /** The id this disk is registered under, or the default if it is not ours. */
    public static String nameOf(DuelDiskItem disk)
    {
        if(disk == null)
        {
            return DEFAULT;
        }
        net.minecraft.resources.Identifier id =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(disk);
        return id == null || !isKnown(id.getPath()) ? DEFAULT : id.getPath();
    }

    /**
     * Lenient on the way in, as {@link Sleeves#CODEC} is: an id this build does
     * not know loads as the plain disk rather than failing the whole profile.
     */
    public static final Codec<String> CODEC = Codec.STRING.xmap(
        name -> isKnown(name) ? name : DEFAULT,
        name -> name);
}
