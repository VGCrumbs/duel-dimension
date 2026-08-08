package de.cas_ual_ty.dueldimension.cardbinder;

import javax.annotation.Nullable;
import java.util.UUID;

public interface IUUIDHolder
{
    @Nullable
    UUID getUUID();

    void setUUID(UUID uuid);
}
