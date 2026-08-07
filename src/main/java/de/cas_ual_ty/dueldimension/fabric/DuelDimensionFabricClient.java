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
    }
}
