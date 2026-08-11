"""Make the deck's sleeve the back of YOUR cards in a duel.

Only yours. The opponent keeps the plain back, which is the point: a sleeve you
paid for is a thing you see across the table from your own seat, and replacing
the opponent's too would just make the field uniform again.

WHY THE SERVER HAS TO SAY WHICH. The client knows which deck it made active, but
DuelistDuels.deckFor may quietly play something else -- if the active deck is
illegal under the duel's banlist it falls back to a starter deck and only says so
in chat. A client guessing from its own active deck would then show the sleeve of
a deck that is not on the table, and it would be wrong in exactly the case the
player is already confused about. So the sleeve travels with the duel, decided
where the deck was decided.

THE UVs LOOK AFTER THEMSELVES. BoardRenderer's edoproArt tests ask whether the
texture IS DuelTextures.COVER / COVER_OPPONENT / UNKNOWN -- identity, not shape.
A sleeve is none of those, so it falls to the letterboxed branch and is sampled
through CARD_U0..CARD_V1, which is exactly how the sleeve PNGs are built (square
canvas, art inside that window). Nothing else needs to change.
"""
import io

def patch(path, pairs):
    s = io.open(path, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, (path, s.count(old), old[:80])
        s = s.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(s)
    print("patched", path.split("/")[-1])


B = "src/main/java/de/cas_ual_ty/dueldimension/"

# ---- 1. the payload, modelled on OpponentPlayMat ---------------------------
patch(B + "ocg/prompt/PromptMessages.java", [(
"""    /** Server -> client: the mat the opponent is playing on. */
    public record OpponentPlayMat(String matId) implements CustomPacketPayload
    {""",
"""    /**
     * Server -> client: the sleeve on the deck THIS player is duelling with.
     * <p>
     * Sent once as the duel starts. It names the deck actually in play, which is
     * not always the one the client made active — an illegal deck is swapped for
     * a starter deck server-side, and the back on the table should be that
     * deck's, not the one that was refused.
     */
    public record OwnSleeve(String sleeve) implements CustomPacketPayload
    {
        /** Names this message on the wire. */
        public static final CustomPacketPayload.Type<OwnSleeve> TYPE =
            DdNetwork.type("prompt_own_sleeve");

        public static final StreamCodec<RegistryFriendlyByteBuf, OwnSleeve> CODEC =
            CustomPacketPayload.codec(OwnSleeve::encode, OwnSleeve::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(OwnSleeve message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.sleeve(), 64);
        }

        public static OwnSleeve decode(FriendlyByteBuf buffer)
        {
            return new OwnSleeve(buffer.readUtf(64));
        }
    }

    /** Server -> client: the mat the opponent is playing on. */
    public record OpponentPlayMat(String matId) implements CustomPacketPayload
    {""")])

# ---- 2. registration + client handler --------------------------------------
patch(B + "net/DdNetwork.java", [
("""        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.CODEC);""",
"""        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnSleeve.TYPE,
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnSleeve.CODEC);"""),
("""        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.TYPE,""",
"""        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OwnSleeve.TYPE,
            (payload, context) -> de.cas_ual_ty.dueldimension.DuelDimension.proxy
                .setOwnSleeve(payload.sleeve()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.TYPE,"""),
])

# ---- 3. the proxy seam -----------------------------------------------------
patch(B + "util/ISidedProxy.java", [(
"""    default void setOpponentPlayMat(String matId)
    {
    }""",
"""    default void setOpponentPlayMat(String matId)
    {
    }

    /** The sleeve on the deck this player is duelling with; server side does nothing. */
    default void setOwnSleeve(String sleeve)
    {
    }""")])

patch(B + "clientutil/ClientProxy.java", [(
"""    @Override
    public void setOpponentPlayMat(String matId)
    {""",
"""    @Override
    public void setOwnSleeve(String sleeve)
    {
        // byName returns null for a name this build does not know, rather than
        // guessing. An unknown sleeve is simply the plain back.
        de.cas_ual_ty.dueldimension.card.CardSleevesType named =
            de.cas_ual_ty.dueldimension.duel.profile.Sleeves.byName(sleeve);
        DuelClientState.ownSleeve = named == null
            ? de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT : named;
    }

    @Override
    public void setOpponentPlayMat(String matId)
    {""")])

# ---- 4. client state -------------------------------------------------------
patch(B + "clientutil/DuelClientState.java", [
("""    public static volatile PlayMats opponentMat = PlayMats.CLASSIC;""",
"""    public static volatile PlayMats opponentMat = PlayMats.CLASSIC;

    /**
     * The sleeve on the deck this player is duelling with, or the plain back.
     * <p>
     * Only ever this player's. The opponent's cards keep the standard back, so a
     * sleeve marks which side of the table is yours rather than restyling the
     * whole field.
     */
    public static volatile de.cas_ual_ty.dueldimension.card.CardSleevesType ownSleeve =
        de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT;"""),
("""        opponentMat = PlayMats.CLASSIC;""",
"""        opponentMat = PlayMats.CLASSIC;
        // Cleared with the rest of the duel, so the next one does not inherit
        // the sleeve of the deck that played the last.
        ownSleeve = de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT;"""),
])

# ---- 5. the server decides and says ----------------------------------------
patch(B + "duel/npc/DuelistDuels.java", [
("""    private record ChosenDeck(HeadlessDuelRunner.Deck cards, String displayName)
    {
    }""",
"""    private record ChosenDeck(HeadlessDuelRunner.Deck cards, String displayName,
        de.cas_ual_ty.dueldimension.card.CardSleevesType sleeve)
    {
    }"""),
("""                return new ChosenDeck(
                    new HeadlessDuelRunner.Deck(chosen.main(), chosen.extra()), chosen.name());""",
"""                return new ChosenDeck(
                    new HeadlessDuelRunner.Deck(chosen.main(), chosen.extra()), chosen.name(),
                    chosen.sleeve());"""),
("""        return new ChosenDeck(fallback.load().toRunnerDeck(), fallback.displayName());""",
"""        // A starter deck has no sleeve of its own, and this is the branch where
        // the player's chosen deck was refused -- so the back is the plain one,
        // which is also the honest signal that the deck on the table is not theirs.
        return new ChosenDeck(fallback.load().toRunnerDeck(), fallback.displayName(),
            de.cas_ual_ty.dueldimension.duel.profile.Sleeves.DEFAULT);"""),
])

print("done")
