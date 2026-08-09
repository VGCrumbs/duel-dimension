"""/dueldimension preload: the command, the packet, and the HUD registration.

The command is SERVER-side, under the existing /dueldimension tree, and sends a
packet to the player who ran it. A Fabric CLIENT command would have been the
more natural home for a client-side cache, but it would have had to claim the
same /dueldimension root the server tree already owns, and which of the two wins
depends on parse order. One tree, no shadowing.

The work itself is entirely the client's -- card art is downloaded and scaled by
whoever is going to draw it -- so the packet carries only "start" or "stop".
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

MSG = "src/main/java/de/cas_ual_ty/dueldimension/net/PreloadMessages.java"
io.open(MSG, "w", encoding="utf-8", newline="\n").write('''package de.cas_ual_ty.dueldimension.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server telling one client to start or stop preloading card art.
 * <p>
 * The work is the client's alone — it downloads and scales the pictures it is
 * going to draw — so this carries nothing but the instruction. The command lives
 * on the server only because that is where the {@code /dueldimension} tree is.
 */
public final class PreloadMessages
{
    private PreloadMessages()
    {
    }

    /** @param start true to begin, false to stop early */
    public record Preload(boolean start) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Preload> TYPE =
            DdNetwork.type("preload_control");

        public static final StreamCodec<ByteBuf, Preload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, Preload::start, Preload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
''')
print("  ok: PreloadMessages")

N = "src/main/java/de/cas_ual_ty/dueldimension/net/DdNetwork.java"
sub(N, "        clientbound(MenuData.TYPE, MenuData.CODEC);",
    "        clientbound(MenuData.TYPE, MenuData.CODEC);\n"
    "        clientbound(PreloadMessages.Preload.TYPE, PreloadMessages.Preload.CODEC);",
    "preload registered")

sub(N, """        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));""",
    """        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            PreloadMessages.Preload.TYPE, (payload, context) ->
            {
                if(payload.start())
                {
                    de.cas_ual_ty.dueldimension.clientutil.CardPreloadJob.start();
                }
                else
                {
                    de.cas_ual_ty.dueldimension.clientutil.CardPreloadJob.stop();
                }
            });""", "preload receiver")

C = "src/main/java/de/cas_ual_ty/dueldimension/serverutil/DdCommand.java"
sub(C, """                .then(Commands.literal("seal")""",
    """                .then(Commands.literal("preload")
                        .executes((context) -> DdCommand.preload(context, true))
                        .then(Commands.literal("stop")
                                .executes((context) -> DdCommand.preload(context, false))
                        )
                )
                .then(Commands.literal("seal")""", "command node")

sub(C, """    /**
     * Marks the caller for the Seal of Orichalcos, without a duel.""",
    """    /**
     * Downloads every card's full art, then scales it, with a progress bar.
     * <p>
     * Opt-in and not cheap: the full raws run to well over a gigabyte, which is
     * why this is a command the player types rather than something the mod
     * decides to do to their disk. Anyone may run it — it costs the caller's own
     * storage and nobody else's — so it takes no permission level.
     */
    private static int preload(CommandContext<CommandSourceStack> context, boolean start)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        net.minecraft.server.level.ServerPlayer player =
            context.getSource().getPlayerOrException();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new de.cas_ual_ty.dueldimension.net.PreloadMessages.Preload(start));
        context.getSource().sendSuccess(() -> Component.literal(start
            ? "Preloading card art. Progress is shown above your hotbar; "
                + "run /dueldimension preload stop to cancel."
            : "Stopping the card art preload. What has already downloaded is kept."), false);
        return 1;
    }

    /**
     * Marks the caller for the Seal of Orichalcos, without a duel.""", "preload handler")

CL = "src/main/java/de/cas_ual_ty/dueldimension/fabric/DuelDimensionFabricClient.java"
sub(CL, "            de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer.tick();",
    "            de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer.tick();\n"
    "            de.cas_ual_ty.dueldimension.clientutil.CardPreloadJob.tick();",
    "job ticked")

sub(CL, """        // The seal is drawn in the world, not in a GUI.""",
    """        // The preload's progress bar, put where the experience bar is rather
        // than painted over whatever happens to be on screen.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.EXPERIENCE_LEVEL,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "preload_progress"),
            new de.cas_ual_ty.dueldimension.clientutil.PreloadHud());

        // The seal is drawn in the world, not in a GUI.""", "hud registered")

print("done")
