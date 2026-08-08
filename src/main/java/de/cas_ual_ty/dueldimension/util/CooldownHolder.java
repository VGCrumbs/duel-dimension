package de.cas_ual_ty.dueldimension.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.nbt.CompoundTag;

public class CooldownHolder implements ICooldownHolder
{
    /**
     * How this is stored, and why the clock is part of it.
     * <p>
     * A cooldown counts down in ticks while the player is on the server. The
     * saved timestamp is what lets it keep counting while they are <em>off</em>
     * it: on load, the seconds since the save are taken off, unless
     * {@code cooldownOnlyWhileOnServer} says a cooldown should only run while
     * someone is actually playing.
     * <p>
     * So the encoder stamps the current time rather than reading a field —
     * there is nothing to read, the timestamp only ever means "when this was
     * written". That is exactly what {@code serializeNBT} did; this is the same
     * behaviour behind a Codec, which is what a data attachment persists
     * through.
     */
    public static final Codec<CooldownHolder> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("cooldown").forGetter(holder -> holder.cooldown),
            Codec.LONG.fieldOf("time").forGetter(holder -> System.currentTimeMillis())
        ).apply(instance, CooldownHolder::loaded));

    private int cooldown;
    
    public CooldownHolder()
    {
        cooldown = 0;
    }

    /** A holder read back from storage, with the time it spent away taken off. */
    private static CooldownHolder loaded(int cooldown, long savedTime)
    {
        CooldownHolder holder = new CooldownHolder();
        holder.cooldown = cooldown;
        holder.applyElapsed(savedTime);
        return holder;
    }

    private void applyElapsed(long savedTime)
    {
        long deltaTime = System.currentTimeMillis() - savedTime;

        if(!DuelDimension.commonConfig().cooldownOnlyWhileOnServer.get())
        {
            cooldown = Math.max(0, cooldown - (int)(deltaTime / 1000L));
        }
    }
    
    @Override
    public void tick()
    {
        if(cooldown > 0)
        {
            cooldown--;
        }
    }
    
    @Override
    public boolean isOffCooldown()
    {
        return cooldown <= 0;
    }
    
    @Override
    public void setCooldown(int cooldown)
    {
        this.cooldown = cooldown;
    }
    
    @Override
    public CompoundTag serializeNBT()
    {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("cooldown", cooldown);
        nbt.putLong("time", System.currentTimeMillis());
        return nbt;
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        cooldown = nbt.getIntOr("cooldown", 0);
        applyElapsed(nbt.getLongOr("time", 0L));
    }
}
