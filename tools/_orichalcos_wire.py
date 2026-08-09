import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    assert new not in s, "already applied: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

N = "src/main/java/de/cas_ual_ty/dueldimension/net/DdNetwork.java"

# 1. clientbound type registration
sub(N, "        clientbound(MenuData.TYPE, MenuData.CODEC);",
    "        clientbound(MenuData.TYPE, MenuData.CODEC);\n"
    "        clientbound(de.cas_ual_ty.dueldimension.duel.orichalcos"
    ".OrichalcosMessages.SealBegin.TYPE,\n"
    "            de.cas_ual_ty.dueldimension.duel.orichalcos"
    ".OrichalcosMessages.SealBegin.CODEC);",
    "clientbound SealBegin")

# 2. client receiver
sub(N, """        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));""",
    """        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));

        // The Seal of Orichalcos closing on someone. The whole six seconds run
        // from this one message; see OrichalcosRenderer.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.SealBegin.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer
                .begin(payload.entityId(), payload.growTicks(), payload.fadeTicks()));""",
    "client receiver")

# 3. the server actually sending it
S = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosSouls.java"
sub(S, """    private static void begin(LivingEntity target)
    {
        DuelDimension.log("Orichalcos: the seal closes on " + target.getName().getString());
    }""",
    """    private static void begin(LivingEntity target)
    {
        DuelDimension.log("Orichalcos: the seal closes on " + target.getName().getString());

        OrichalcosMessages.SealBegin message =
            new OrichalcosMessages.SealBegin(target.getId(), GROW_TICKS, FADE_TICKS);
        // Everyone who can see it, plus the target if the target is a player --
        // PlayerLookup.tracking does not include a player in its own tracker
        // set, so the one person the seal is actually taking would otherwise be
        // the one person who could not see it.
        for(ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup
            .tracking(target))
        {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, message);
        }
        if(target instanceof ServerPlayer self)
        {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(self, message);
        }
    }""", "server send")

# 4. client tick + render hook
C = "src/main/java/de/cas_ual_ty/dueldimension/fabric/DuelDimensionFabricClient.java"
sub(C, "            CardImagePreloader.tick();",
    "            CardImagePreloader.tick();\n"
    "            de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer.tick();",
    "client tick")

sub(C, "        ClientPlayConnectionEvents.DISCONNECT.register(",
    """        // The seal is drawn in the world, not in a GUI. WorldRenderEvents is
        // gone in 26.2; COLLECT_SUBMITS is where geometry is handed to the
        // renderer for the frame, and its context carries both the pose stack
        // and the submit collector.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS
            .register(de.cas_ual_ty.dueldimension.clientutil.OrichalcosRenderer::render);

        ClientPlayConnectionEvents.DISCONNECT.register(""", "render hook")

print("done")
