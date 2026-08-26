package de.cas_ual_ty.dueldimension.util;

import com.google.common.base.Suppliers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * A bag of item slots.
 * <p>
 * On Forge this was an {@code ItemStackHandler}: a capability, reached by
 * asking an item or a block entity whether it happened to have one, and
 * attached to a slot through {@code SlotItemHandler}. Fabric has no
 * capabilities and vanilla's own {@link net.minecraft.world.Container} does the
 * same job — a plain {@code Slot} works with it directly, with no adapter — so
 * that is what this is now.
 * <p>
 * The method names are the handler's rather than the container's, because
 * thirty-odd call sites across the binders, the deck boxes and the sets use
 * them and a port should not be renaming things it is not changing. They
 * delegate; nothing here decides anything.
 * <p>
 * {@code load} and {@code save} were already empty on Forge — a Mohist
 * workaround that had been commented out — and are kept as the no-ops they were
 * so the call sites do not have to change either.
 */
public class YDMItemHandler extends SimpleContainer
{
    /** Which slot list a serialised handler writes into and reads back from. */
    private static final String ITEMS = "Items";

    protected Supplier<CompoundTag> nbtSupplier;

    /**
     * The stack this handler writes through to, or null for a free-standing one.
     * When set, every change is persisted into the stack's
     * {@link de.cas_ual_ty.dueldimension.DdComponents#CARD_INVENTORY} component —
     * the component being immutable, this is what replaces the Forge capability
     * that was itself the live storage.
     */
    protected ItemStack boundStack;

    public YDMItemHandler(Supplier<CompoundTag> nbtSupplier)
    {
        this(0, nbtSupplier);
    }

    public YDMItemHandler(int size)
    {
        this(size, Suppliers.memoize(CompoundTag::new));
    }

    public YDMItemHandler(int size, Supplier<CompoundTag> nbtSupplier)
    {
        super(size);
        this.nbtSupplier = nbtSupplier;
    }

    public YDMItemHandler(NonNullList<ItemStack> stacks, Supplier<CompoundTag> nbtSupplier)
    {
        this(stacks.size(), nbtSupplier);
        for(int slot = 0; slot < stacks.size(); slot++)
        {
            setItem(slot, stacks.get(slot));
        }
    }

    /**
     * A handler seeded from a stack's {@code CARD_INVENTORY} component that
     * writes every subsequent change back into that stack. Seeding happens
     * before the stack is bound, so it does not trigger a redundant write.
     */
    public static YDMItemHandler boundTo(ItemStack stack, int size)
    {
        YDMItemHandler handler = new YDMItemHandler(size);
        java.util.List<ItemStack> stored =
            stack.getOrDefault(de.cas_ual_ty.dueldimension.DdComponents.CARD_INVENTORY,
                java.util.List.of());
        for(int slot = 0; slot < size && slot < stored.size(); slot++)
        {
            handler.setItem(slot, stored.get(slot).copy());
        }
        handler.boundStack = stack;
        return handler;
    }

    @Override
    public void setChanged()
    {
        super.setChanged();
        if(boundStack != null)
        {
            // Copy the stacks, not just the list: the component must not alias
            // slots this handler will keep mutating in place.
            java.util.List<ItemStack> snapshot = new java.util.ArrayList<>(getContainerSize());
            for(ItemStack stack : getItems())
            {
                snapshot.add(stack.copy());
            }
            boundStack.set(de.cas_ual_ty.dueldimension.DdComponents.CARD_INVENTORY, snapshot);
        }
    }

    // ---- the handler's names, on the container's behaviour ----

    public int getSlots()
    {
        return getContainerSize();
    }

    public ItemStack getStackInSlot(int slot)
    {
        return getItem(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack)
    {
        setItem(slot, stack);
    }

    /**
     * Puts what fits in and hands back what did not.
     * <p>
     * The handler's contract, which is not the container's: {@code addItem}
     * mutates the stack it is given and returns the leftover, so this copies
     * first. A caller that passed a stack it still owns would otherwise find it
     * emptied.
     */
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
    {
        if(stack.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        ItemStack existing = getItem(slot);
        if(!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack))
        {
            return stack;
        }
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        int room = limit - existing.getCount();
        if(room <= 0)
        {
            return stack;
        }
        int moved = Math.min(room, stack.getCount());
        if(!simulate)
        {
            ItemStack put = existing.isEmpty() ? stack.copyWithCount(moved) : existing.copy();
            if(!existing.isEmpty())
            {
                put.grow(moved);
            }
            setItem(slot, put);
            setChanged();
        }
        return moved >= stack.getCount() ? ItemStack.EMPTY
            : stack.copyWithCount(stack.getCount() - moved);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate)
    {
        if(simulate)
        {
            ItemStack held = getItem(slot);
            return held.isEmpty() ? ItemStack.EMPTY
                : held.copyWithCount(Math.min(amount, held.getCount()));
        }
        return removeItem(slot, amount);
    }

    /**
     * Resizes, keeping what still fits.
     * <p>
     * {@code SimpleContainer} is fixed at construction, so this rebuilds the
     * list behind it. Only one caller resizes -- a deck box changing capacity --
     * and it expects the contents to survive.
     */
    public void setSize(int size)
    {
        NonNullList<ItemStack> kept = getItems();
        java.util.List<ItemStack> copy = new java.util.ArrayList<>(kept);
        kept.clear();
        for(int slot = 0; slot < size; slot++)
        {
            kept.add(slot < copy.size() ? copy.get(slot) : ItemStack.EMPTY);
        }
    }

    // ---- storage ----

    /**
     * The slots as a tag.
     * <p>
     * The registries are a parameter, and that is the one thing about this
     * class a caller cannot ignore. An {@code ItemStack} is written through its
     * codec now rather than by hand, and that codec resolves the item against
     * the registry -- so writing one is no longer something an object can do
     * alone, the way {@code serializeNBT()} assumed. Every call site has a
     * level or a player in reach and can answer; making them say so is better
     * than reaching for a static and being wrong on a server with a data pack.
     */
    public CompoundTag serializeNBT(HolderLookup.Provider registries)
    {
        return ContainerHelper.saveAllItems(new CompoundTag(), getItems(), true, registries);
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag)
    {
        ContainerHelper.loadAllItems(tag, getItems(), registries);
    }

    public void load()
    {
    }

    public void save()
    {
    }
}
