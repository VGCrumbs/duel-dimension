"""Gives the client a client proxy, and gives that proxy its sided hooks.

Two bugs, one root. `DuelDimension.proxy` is a `ServerProxy` and its javadoc says
"DuelDimensionFabricClient replaces it on the way up" -- but that line was never
written. So every sided hook was answering with the server's no-op even on a
client: a duel message could not be sent, a duel update could not reach a screen,
and a prompt could not open one. Clicking a duelist started a real duel on the
server whose packets then arrived and did nothing.

The hooks themselves were also still the interface defaults, because
EngineDuelScreen did not exist when ClientProxy was written. It does now.
"""
import io

PROXY = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ClientProxy.java"
ENTRY = "src/main/java/de/cas_ual_ty/dueldimension/fabric/DuelDimensionFabricClient.java"

HOOKS = '''
    // ---- what only a client can do ----
    //
    // These were the interface's no-op defaults until the duel screen existed.
    // Every one of them is the Forge ClientProxy's body, with the two calls that
    // changed name in 26.2 brought up to date.

    /**
     * Sends a duel message to the server.
     * <p>
     * Common code reaches this through the proxy so that Fabric's client
     * networking class never has to be on a dedicated server's classpath.
     */
    @Override
    public void sendDuelMessage(de.cas_ual_ty.dueldimension.duel.network.DuelMessage message)
    {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.duel.network.DuelPayloads.ToServer(message));
    }

    /**
     * Wraps a duel manager so updates also reach the screen.
     * <p>
     * The server talks to the manager directly; a client needs the wrapper, and
     * this is the side that knows which it is.
     */
    @Override
    public de.cas_ual_ty.dueldimension.duel.network.IDuelManagerProvider duelProvider(
        de.cas_ual_ty.dueldimension.duel.DuelManager duelManager)
    {
        return new de.cas_ual_ty.dueldimension.duel.network.ClientDuelManagerProvider(duelManager);
    }

    @Override
    public void setOpponentPlayMat(String matId)
    {
        DuelClientState.opponentMat = PlayMats.byId(matId);
        // The id may be a colour now rather than a mat name.
        DuelClientState.opponentMatColour =
            DuelClientState.parseMatColour(matId, DuelClientState.DEFAULT_MAT_COLOUR);
    }

    @Override
    public void showEnginePrompt(de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt prompt,
        int serial)
    {
        // Into the playback queue, never straight onto the screen: the prompt
        // was sent after the events it concludes, and it must not be seen
        // before they have PLAYED. Applying it here let it jump the queue.
        synchronized(DuelClientState.class)
        {
            DuelClientState.pending.add(DuelClientState.PendingUpdate.ofPrompt(prompt, serial));
        }
    }

    @Override
    public void updateEngineDuel(
        de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate update)
    {
        update.log().forEach(DuelClientState::addLog);
        DuelClientState.warmUpArt(update.warmUp());
        synchronized(DuelClientState.class)
        {
            // Board and events travel together and are played in order, so the
            // field advances at the pace of the animation rather than jumping
            // to the settled state the moment the packet lands.
            DuelClientState.pending.add(
                DuelClientState.PendingUpdate.ofUpdate(update.events(), update.board()));
            if(update.over())
            {
                // The result rides the stream too, after the win animation.
                boolean won = update.result() != null
                    && update.result().toLowerCase(java.util.Locale.ROOT).contains("winner: you");
                DuelClientState.pending.add(
                    DuelClientState.PendingUpdate.ofOver(won, update.result()));
            }
        }
        // The opponent's whole turn arrives as updates with no prompt attached.
        // Only opening the screen for prompts meant those events queued up
        // unseen and then replayed in a rush at the next prompt, so a duel
        // update reopens the screen too -- that is what makes an opponent's
        // sequence watchable rather than something that happens off-screen.
        if(!update.over() && !update.events().isEmpty()
            && !(getMinecraft().gui.screen() instanceof EngineDuelScreen))
        {
            // gui.setScreen, not setScreenAndShow: the latter forces a frame,
            // and this runs while the update batch is still being applied.
            getMinecraft().gui.setScreen(new EngineDuelScreen());
        }
        if(update.board() != null)
        {
            DuelClientState.warmUpBoard(update.board());
        }
    }
'''

src = io.open(PROXY, encoding="utf-8").read()
anchor = "    public static Minecraft getMinecraft()"
assert anchor in src, "ClientProxy anchor moved"
src = src.replace(anchor, HOOKS + "\n" + anchor, 1)
io.open(PROXY, "w", encoding="utf-8", newline="\n").write(src)
print("ClientProxy: sided hooks implemented")

src = io.open(ENTRY, encoding="utf-8").read()
anchor = "        // Settings first: the image pipeline and the animations read them."
assert anchor in src, "client entrypoint anchor moved"
src = src.replace(anchor, """        // The client's proxy, FIRST. DuelDimension holds a ServerProxy until
        // something replaces it, and every sided hook -- sending a duel
        // message, wrapping a duel manager, opening the duel screen -- answers
        // with the server's no-op until it is. Nothing below this line works
        // without it.
        de.cas_ual_ty.dueldimension.DuelDimension.proxy =
            new de.cas_ual_ty.dueldimension.clientutil.ClientProxy();

""" + anchor, 1)
io.open(ENTRY, "w", encoding="utf-8", newline="\n").write(src)
print("client entrypoint: ClientProxy installed")
