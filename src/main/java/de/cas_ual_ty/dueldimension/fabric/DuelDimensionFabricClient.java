package de.cas_ual_ty.dueldimension.fabric;

import net.fabricmc.api.ClientModInitializer;

/** Client entry point; fills in as the client phases of PORTING.md land. */
public class DuelDimensionFabricClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.register();

        // Told what everyone is wearing, and what the server holds for us.
        de.cas_ual_ty.dueldimension.net.DdNetwork.registerClientHandlers();

        // The outfit pass, on every player renderer. Forge subscribed to an
        // event that fired once per renderer type; this fires the same way, and
        // the check is the same one -- players only, because an outfit is drawn
        // on a player model and nothing else has one.
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback
            .EVENT.register((entityType, renderer, helper, context) ->
        {
            if(entityType == net.minecraft.world.entity.EntityTypes.PLAYER)
            {
                helper.register(new de.cas_ual_ty.dueldimension.clientutil.OutfitLayer(
                    (net.minecraft.client.renderer.entity.RenderLayerParent<
                        net.minecraft.client.renderer.entity.state.AvatarRenderState,
                        net.minecraft.client.model.player.PlayerModel>)renderer));
            }
        });
    }
}
