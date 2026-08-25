package de.cas_ual_ty.dueldimension.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * The client entrypoint, deliberately empty while the port is under way.
 * <p>
 * <b>This is a stub, not the real one.</b> The 26.2 version of this class is
 * 600-odd lines that reach into every part of the client — screens, entity
 * renderers, the card image pipeline, the picture-in-picture board, keybinds,
 * item models — so porting it first would drag the entire rendering layer across
 * before any of it could compile, which is the opposite of an order.
 * <p>
 * So the server half goes first and this holds its place. Each client subsystem
 * gets wired in here as it lands, and the file is finished when it matches
 * {@code mc262}'s. Until then a 1.21.1 client loads the mod, registers its
 * items, blocks and packets, and draws none of its own UI.
 *
 * @see de.cas_ual_ty.dueldimension.fabric.DuelDimensionFabric the common entrypoint,
 *      which is being ported for real
 */
public class DuelDimensionFabricClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        // Nothing yet, and nothing that pretends otherwise: a stub that logged
        // "client ready" would be a claim the port has not earned.
    }
}
