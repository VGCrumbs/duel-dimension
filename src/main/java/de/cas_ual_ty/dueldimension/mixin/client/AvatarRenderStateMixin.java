package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.OutfitCarrier;
import de.cas_ual_ty.dueldimension.duel.outfit.Outfits;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives a player's render state somewhere to carry their outfit. */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements OutfitCarrier
{
    @Unique
    private Outfits.Outfit dueldimension$outfit = Outfits.NONE;

    @Override
    public Outfits.Outfit dueldimension$outfit()
    {
        return dueldimension$outfit;
    }

    @Override
    public void dueldimension$setOutfit(Outfits.Outfit outfit)
    {
        dueldimension$outfit = outfit == null ? Outfits.NONE : outfit;
    }
}
