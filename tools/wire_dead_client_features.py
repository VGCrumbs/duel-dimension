"""Wires four features the sweep found dead: every piece ported, nothing calling it.

They share one shape. A payload TYPE was registered (so a "is every message
registered" check passed) or a proxy hook existed as an interface default (so
every call site compiled) -- but the body that does the work was left parked in
a comment, and the feature silently did nothing.

1. The PvP lobby. OpenLobby/CloseLobby are sent by the server and decode fine;
   no client receiver existed, so DuelLobbyScreen -- fully ported -- was never
   constructed. The whole player-vs-player flow was dead.
2. Card inspect. CardItem.use calls proxy.openCardInspectScreen; ClientProxy
   never overrode it, so right-clicking a card did nothing.
3. The ten image-path hooks. These build the texture path for every card and
   set image OUTSIDE the duel screen -- binder, supply, shop, card items. The
   defaults return null, so those paths became "textures/item/null.png" and the
   art was neither drawn nor downloaded.
"""
import io

# ---------- 1. ISidedProxy: unpark openDuelLobby ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/util/ISidedProxy.java"
s = io.open(P, encoding="utf-8").read()
old = """    /** Opens or refreshes the duel lobby. Client only. */
    // Parked until the client phase; see above.
    // default void openDuelLobby(
    // de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby room)
    // {
    // }"""
new = """    /** Opens or refreshes the duel lobby. Client only. */
    default void openDuelLobby(
        de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby room)
    {
    }"""
assert old in s, "openDuelLobby anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("1. ISidedProxy: openDuelLobby unparked")

# ---------- 2. ClientProxy: the thirteen missing overrides ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ClientProxy.java"
s = io.open(P, encoding="utf-8").read()
anchor = "    /**\n     * The shop, with the stock and the balance the server just sent."
BODY = '''    // ---- where a card or set image lives ----
    //
    // Every one of these is the Forge ClientProxy's body. They are how a
    // Properties, a CardSet or a RarityLayer turns into a texture path, and
    // the interface's defaults return NULL -- so with them missing the path
    // came out "textures/item/null.png", every binder, supply, shop and card
    // item drew the missing-texture checkerboard, and ImageHandler was never
    // asked to fetch the real image either. The duel screen was unaffected
    // only because it goes through DuelTextures instead, which is why this
    // survived so long.

    @Override
    public String addCardInfoTag(String imageName)
    {
        return ClientProxy.activeCardInfoImageSize + "/" + imageName;
    }

    @Override
    public String addCardItemTag(String imageName)
    {
        return ClientProxy.activeCardItemImageSize + "/" + imageName;
    }

    @Override
    public String addCardMainTag(String imageName)
    {
        return ClientProxy.activeCardMainImageSize + "/" + imageName;
    }

    @Override
    public String addSetInfoTag(String imageName)
    {
        return ClientProxy.activeSetInfoImageSize + "/" + imageName;
    }

    @Override
    public String addSetItemTag(String imageName)
    {
        return ClientProxy.activeSetItemImageSize + "/" + imageName;
    }

    @Override
    public String getCardInfoReplacementImage(
        de.cas_ual_ty.dueldimension.card.properties.Properties properties, byte imageIndex)
    {
        return ImageHandler.getInfoReplacementImage(properties, imageIndex);
    }

    @Override
    public String getCardMainReplacementImage(
        de.cas_ual_ty.dueldimension.card.properties.Properties properties, byte imageIndex)
    {
        return ImageHandler.getMainReplacementImage(properties, imageIndex);
    }

    @Override
    public String getSetInfoReplacementImage(de.cas_ual_ty.dueldimension.set.CardSet set)
    {
        return ImageHandler.getInfoReplacementImage(set);
    }

    @Override
    public String getRarityMainImage(de.cas_ual_ty.dueldimension.rarity.RarityLayer layer)
    {
        return ImageHandler.getRarityMainImage(layer);
    }

    @Override
    public String getRarityInfoImage(de.cas_ual_ty.dueldimension.rarity.RarityLayer layer)
    {
        return ImageHandler.getRarityInfoImage(layer);
    }

    /** Reading a card item. Ported, reachable, and never called until now. */
    @Override
    public void openCardInspectScreen(de.cas_ual_ty.dueldimension.card.CardHolder card)
    {
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.card.InspectCardScreen(card));
    }

    /**
     * The PvP lobby, opened or refreshed.
     * <p>
     * Every change re-sends the whole room, so an open lobby is updated in
     * place rather than replaced: rebuilding the screen would drop focus and
     * flicker on every click either player made.
     */
    @Override
    public void openDuelLobby(de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby room)
    {
        if(getMinecraft().gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.hub.DuelLobbyScreen open)
        {
            open.update(room);
            return;
        }
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.DuelLobbyScreen(room));
    }

    @Override
    public void closeDuelLobby()
    {
        if(getMinecraft().gui.screen()
            instanceof de.cas_ual_ty.dueldimension.clientutil.hub.DuelLobbyScreen)
        {
            getMinecraft().gui.setScreen(null);
        }
    }

'''
assert anchor in s, "ClientProxy anchor"
s = s.replace(anchor, BODY + anchor, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("2. ClientProxy: 13 overrides (10 image paths, inspect, lobby open/close)")

# ---------- 3. DdNetwork: the two lobby receivers ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/net/DdNetwork.java"
s = io.open(P, encoding="utf-8").read()
old = """        // The shop: opened by the server (clicking the counter is answered"""
new = """        // The PvP lobby. Sent on every change to the room, so the handler
        // updates an open screen rather than replacing it; see the proxy.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openDuelLobby(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .closeDuelLobby());

        // The shop: opened by the server (clicking the counter is answered"""
assert old in s, "lobby receiver anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("3. DdNetwork: OpenLobby + CloseLobby receivers")
