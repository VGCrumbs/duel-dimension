package de.cas_ual_ty.dueldimension.util;

import net.minecraft.nbt.CompoundTag;

/**
 * Something that counts down.
 * <p>
 * It used to extend Forge's {@code INBTSerializable}, an interface whose only
 * purpose was to let a capability be saved. There are no capabilities here and
 * nothing asks for the interface generically, so the two methods are declared
 * directly and the dependency goes away with nothing lost.
 */
public interface ICooldownHolder
{
    void tick();

    boolean isOffCooldown();

    void setCooldown(int cooldown);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag nbt);
}
