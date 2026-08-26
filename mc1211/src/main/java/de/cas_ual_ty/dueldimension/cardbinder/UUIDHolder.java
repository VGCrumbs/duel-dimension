package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Port: on Forge this was the capability that held a binder's id on its stack,
 * an {@code INBTSerializable<StringTag>} reached through
 * {@code stack.getCapability(...)}. Fabric has no capabilities and
 * {@link net.minecraft.world.item.ItemStack} has no tag, so a binder's id lives
 * in the {@link de.cas_ual_ty.dueldimension.DdComponents#BINDER_UUID} component
 * instead and {@link CardBinderItem} reads and writes it directly.
 * <p>
 * The holder is kept for parity, minus the capability/{@code INBTSerializable}
 * machinery there is nothing to attach to any more. Its serialisation now goes
 * through {@link net.minecraft.nbt.CompoundTag}'s {@code Optional} getters,
 * which is the only mechanical drift in it.
 */
public class UUIDHolder implements IUUIDHolder
{
    private static final CompoundTag DUMMY_NBT = new CompoundTag();
    public static final UUIDHolder NULL_HOLDER = new UUIDHolder(() -> DUMMY_NBT)
    {
        @Override
        public UUID getUUID()
        {
            return null;
        }
    };

    protected UUID uuid;
    protected Supplier<CompoundTag> nbtSupplier;

    public UUIDHolder(Supplier<CompoundTag> nbtSupplier)
    {
        uuid = null;
        this.nbtSupplier = nbtSupplier;
    }

    @Override
    @Nullable
    public UUID getUUID()
    {
        load();
        return uuid;
    }

    @Override
    public void setUUID(UUID uuid)
    {
        this.uuid = uuid;
        save();
    }

    public StringTag serializeNBT()
    {
        return uuid == null ? StringTag.valueOf("") : StringTag.valueOf(getUUID().toString());
    }

    public void deserializeNBT(StringTag nbt)
    {
        String uuid = nbt.getAsString();

        if(uuid.isEmpty())
        {
            this.uuid = null;
        }
        else
        {
            this.uuid = UUID.fromString(uuid);
        }
    }

    public void load()
    {
        if(DuelDimension.commonConfig().mohistWorkaround.get())
        {
            UUID old = uuid;
            CompoundTag nbt = nbtSupplier.get();

            if(nbt.contains("uuid_cap"))
            {
                Tag inbt = nbt.get("uuid_cap");

                if(inbt instanceof StringTag)
                {
                    deserializeNBT((StringTag) inbt);
                }
            }

            if(uuid == null)
            {
                uuid = old;
            }

            save();
        }
    }

    public void save()
    {
        if(DuelDimension.commonConfig().mohistWorkaround.get() && uuid != null)
        {
            CompoundTag nbt = nbtSupplier.get();
            nbt.put("uuid_cap", uuid == null ? StringTag.valueOf("") : StringTag.valueOf(uuid.toString()));
        }
    }
}
