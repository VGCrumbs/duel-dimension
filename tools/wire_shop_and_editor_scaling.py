"""Dynamic deck-editor scaling, and the card shop's missing wiring.

The editor sized cards from width alone and let the deck scroll; the extra and
side decks therefore lived below the fold. Columns are fixed, so the row count
depends only on the deck's contents -- the height the sections need is known
before a size is chosen, and the card can shrink until every row fits.

The shop was ported whole -- screen, block, stock, points, messages -- but
none of its handlers were registered: the block sent OpenShop into a void, and
Buy had no receiver. The handler bodies were parked as comments inside
ShopMessages; this moves them to the registration sites, which is where the
comments said they belong.
"""
import io

# ---------- A. deck editor: dynamic scaling ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckEditorScreen.java"
s = io.open(p, encoding="utf-8").read()
old = """        float aspect = layout.f("card.aspect", 480F / 700F);
        int usableW = leftW - pad * 2;
        cardW = (usableW - (mainColumns - 1) * gap) / mainColumns;
        cardW = Math.max(layout.i("card.minWidth", 10),
            Math.min(layout.i("card.maxWidth", 72), cardW));
        cardH = Math.max(8, Math.round(cardW / aspect));"""
new = """        float aspect = layout.f("card.aspect", 480F / 700F);
        int usableW = leftW - pad * 2;
        cardW = (usableW - (mainColumns - 1) * gap) / mainColumns;
        cardW = Math.max(layout.i("card.minWidth", 10),
            Math.min(layout.i("card.maxWidth", 72), cardW));

        // Height decides too, now. Width alone gave the biggest card ten
        // columns allow and let the deck scroll -- which meant the extra and
        // side decks lived below the fold and reaching them was a scroll every
        // time. The columns are fixed, so the row count depends only on what
        // the deck holds; the height the three sections may use is therefore
        // known before a card size is chosen, and the card shrinks (never
        // grows) until every row fits. A small deck still gets full-width
        // cards; a 60-card deck with a full extra and side gets smaller ones
        // and the whole thing on screen at once.
        int rowsTotal = rowsFor(DeckList.Part.MAIN)
            + rowsFor(DeckList.Part.EXTRA) + rowsFor(DeckList.Part.SIDE);
        int heightBudget = deckViewHeight() - (headerH + sectionGap) * 3 - rowsTotal * gap;
        if(rowsTotal > 0 && heightBudget > 0)
        {
            int fitH = heightBudget / rowsTotal;
            int fitW = Math.max(layout.i("card.minWidth", 10), Math.round(fitH * aspect));
            cardW = Math.min(cardW, fitW);
        }
        cardH = Math.max(8, Math.round(cardW / aspect));"""
assert old in s, "resize anchor"
s = s.replace(old, new, 1)

s = s.replace("""        // Width decides the size, not height. Shrinking cards until every row
        // of every part fitted on screen at once is what made them small: ten
        // columns then used barely half the panel's width and the deck sat in
        // the corner of a mostly empty container. The deck scrolls instead,
        // which is what the trunk beside it already does.
""", """        // Width sets the ceiling; height may lower it (below). The scroll
        // strip is still there for the case the minimum card size cannot fit,
        // but with dynamic sizing it should stay unused.
""")
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("A. editor: dynamic scaling")

# ---------- B1. ISidedProxy: unpark openCardShop ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/util/ISidedProxy.java"
s = io.open(p, encoding="utf-8").read()
old = """    /** Opens the card shop. Client only. */
    // Parked until the client phase; see above.
    // default void openCardShop(int points,
    // java.util.List<de.cas_ual_ty.dueldimension.shop.ShopStock.Pack> packs)
    // {
    // }"""
new = """    /** Opens the card shop. Client only. */
    default void openCardShop(int points,
        java.util.List<de.cas_ual_ty.dueldimension.shop.ShopStock.Pack> packs)
    {
    }"""
assert old in s, "ISidedProxy anchor"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("B1. ISidedProxy: openCardShop unparked")

# ---------- B2. ClientProxy overrides ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ClientProxy.java"
s = io.open(p, encoding="utf-8").read()
anchor = "    public void updateEngineDuel("
BODY = '''    /**
     * The shop, with the stock and the balance the server just sent.
     * <p>
     * Both facts arrive in the packet rather than being read from anywhere on
     * the client, because both are the server's: the balance is spendable and
     * the stock is what is actually for sale.
     */
    @Override
    public void openCardShop(int points,
        java.util.List<de.cas_ual_ty.dueldimension.shop.ShopStock.Pack> packs)
    {
        de.cas_ual_ty.dueldimension.clientutil.hub.CardShopScreen.setPoints(points);
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.CardShopScreen(packs));
    }

    @Override
    public void setDuelPoints(int points)
    {
        de.cas_ual_ty.dueldimension.clientutil.hub.CardShopScreen.setPoints(points);
    }

    /** The pack-opening reveal, over whatever screen asked for the packs. */
    @Override
    public void openPackReveal(String setName, java.util.List<Integer> codes,
        java.util.List<String> rarities)
    {
        getMinecraft().gui.setScreen(
            new de.cas_ual_ty.dueldimension.clientutil.hub.PackOpeningScreen(
                setName, codes, rarities));
    }

'''
assert anchor in s, "ClientProxy anchor"
i = s.index(anchor)
j = s.rindex("\n\n", 0, i) + 2
s = s[:j] + BODY + s[j:]
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("B2. ClientProxy: three overrides")

# ---------- B3. sell made reachable ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/shop/ShopMessages.java"
s = io.open(p, encoding="utf-8").read()
old = "        private static void sell(ServerPlayer player, String code, int requested)"
new = ("        // Public for the receiver in DdNetwork; the checks inside are what\n"
       "        // keep it safe, not the visibility.\n"
       "        public static void sell(ServerPlayer player, String code, int requested)")
assert old in s, "sell anchor"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("B3. sell public")

# ---------- B4. DdNetwork receivers ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/net/DdNetwork.java"
s = io.open(p, encoding="utf-8").read()

old = "        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt.TYPE,"
new = """        // The shop's one purchase message. The Forge handler unwrapped the
        // sender and enqueued; onServer already did both.
        onServer(de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy
                .sell(player, message.code(), message.count()));

        onServer(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt.TYPE,"""
assert old in s, "onServer anchor"
s = s.replace(old, new, 1)

old = """        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));"""
new = """        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            MenuData.TYPE, (payload, context) -> MenuData.receive(payload.data()));

        // The shop: opened by the server (clicking the counter is answered
        // with stock and balance), kept honest by it (every purchase comes
        // back as a new balance), and the pack reveal rides the same flow.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openCardShop(payload.points(), payload.packs()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .setDuelPoints(payload.points()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .openPackReveal(payload.setName(), payload.codes(), payload.rarities()));"""
assert old in s, "client receiver anchor"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("B4. DdNetwork: 3 client + 1 server receiver")
