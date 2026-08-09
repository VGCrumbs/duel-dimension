"""Make the seal actually fire, and make it take duelists too.

FOUR separate reasons it did nothing:

1. The mark sat INSIDE payOut's reward loop, which `continue`s on
   `watcher == null` -- and a duelist's seat IS null (the field is documented
   "null where a bot sits"). So an NPC could never be marked. It also skips
   anyone failing canReward(), i.e. a player who conceded, who should certainly
   not be spared.
2. Nothing ever told the server the player had closed the duel screen, so
   playerLeftDuel() was dead code and every death waited out the full 20-second
   fallback. Twenty seconds after a duel reads as "nothing happened".
3. RunningDuel had no reference to the duelist at all, so there was nothing to
   mark even once the seat problem was fixed.
4. The rule defaulted to OFF, so even a correct implementation did nothing until
   someone found the toggle. The feature was asked for; the toggle is there to
   turn it off, not to turn it on.
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

S = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosSouls.java"
M = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosMessages.java"
D = "src/main/java/de/cas_ual_ty/dueldimension/duel/npc/DuelistDuels.java"
N = "src/main/java/de/cas_ual_ty/dueldimension/net/DdNetwork.java"
E = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java"
C = "src/main/java/de/cas_ual_ty/dueldimension/CommonConfig.java"

# ---------- 1. mark any living thing, not just a player ----------
sub(S, """    public static void mark(ServerPlayer loser)
    {
        if(loser == null)
        {
            return;
        }""",
    """    public static void mark(LivingEntity loser)
    {
        if(loser == null)
        {
            return;
        }""", "mark takes LivingEntity")

sub(S, """    /**
     * Marks any living entity, starting immediately.""",
    """    /** Marks whatever is behind this id, wherever in the world it is. */
    public static void markById(MinecraftServer server, UUID id)
    {
        if(id != null)
        {
            mark(resolve(server, id));
        }
    }

    /**
     * Marks any living entity, starting immediately.""", "markById")

# ---------- 2. the duel end: out of the reward loop, and duelists included ----------
sub(D, """            if(outcome == de.cas_ual_ty.dueldimension.shop.DuelReward.Outcome.LOSS
                && duel.sealOnField)
            {
                // The anime's forfeit. A draw has no loser, which the LOSS test
                // already excludes. mark() decides whether the rule is on.
                de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.mark(player);
            }

            int before""", "            int before", "mark leaves the reward loop")

sub(D, """        duel.paid = true;""",
    """        duel.paid = true;

        // Before the rewards, and deliberately outside their loop. That loop
        // skips a seat whose Watcher is null -- which is exactly a duelist's
        // seat -- and skips anyone who failed canReward(), i.e. conceded.
        // Neither of those should save you from the seal.
        if(duel.sealOnField)
        {
            markSealLoser(server, duel);
        }""", "markSealLoser called")

sub(D, """    private static void announceToBoth(""",
    """    /**
     * Takes the loser's soul, player or duelist alike.
     * <p>
     * Called once per contest from {@link #payOut}, after {@code wins[]} is
     * final. A duelist has no {@link Watcher} — its seat is null — so it is
     * found through the id captured when the challenge was made.
     */
    private static void markSealLoser(net.minecraft.server.MinecraftServer server,
        RunningDuel duel)
    {
        if(duel.wins[0] == duel.wins[1])
        {
            return;   // a draw has no loser
        }
        int loser = duel.wins[0] > duel.wins[1] ? 1 : 0;
        Watcher watcher = duel.seats[loser];
        if(watcher == null)
        {
            // The duelist lost. Its soul goes the same way a player's does, and
            // on the same schedule: the player is still watching the duel
            // screen, and should be back in the world to see it happen.
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
                .markById(server, duel.duelistId);
            return;
        }
        if(watcher.console())
        {
            return;
        }
        de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
            .markById(server, watcher.playerId());
    }

    private static void announceToBoth(""", "markSealLoser body")

# ---------- 3. remember which duelist is in the duel ----------
sub(D, """        /** A player who concedes this contest can never receive its DP packet. */
        final boolean[] rewardEligible = {true, true};""",
    """        /** A player who concedes this contest can never receive its DP packet. */
        final boolean[] rewardEligible = {true, true};
        /**
         * The duelist across the table, or null in a duel between two players.
         * <p>
         * Held as an id rather than the entity: a duel outlives a chunk unload,
         * and the seal has to be able to find whoever lost it afterwards.
         */
        UUID duelistId;""", "RunningDuel.duelistId")

sub(D, """        ACTIVE.put(Watcher.of(serverPlayer), new RunningDuel(session,
            Watcher.of(serverPlayer), null, engine.cards()));""",
    """        RunningDuel npcDuel = new RunningDuel(session,
            Watcher.of(serverPlayer), null, engine.cards());
        npcDuel.duelistId = duelist.getUUID();
        ACTIVE.put(Watcher.of(serverPlayer), npcDuel);""", "capture duelistId")

# ---------- 4. the client tells the server it has left the duel ----------
sub(M, """    /**
     * @param entityId   the network id of whatever the seal is closing on""",
    """    /**
     * The player saying they have closed the duel and have their character back.
     * <p>
     * Carries nothing: the sender is the message. It only ever SHORTENS the
     * wait — see {@link OrichalcosSouls#playerLeftDuel} — so a client that never
     * sends it, or sends it early, changes nothing it should not.
     */
    public record LeftDuel() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<LeftDuel> TYPE =
            de.cas_ual_ty.dueldimension.net.DdNetwork.type("orichalcos_left_duel");

        public static final StreamCodec<ByteBuf, LeftDuel> CODEC =
            StreamCodec.unit(new LeftDuel());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * @param entityId   the network id of whatever the seal is closing on""",
    "LeftDuel payload")

sub(N, """        clientbound(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.SealBegin.TYPE,""",
    """        serverbound(de.cas_ual_ty.dueldimension.duel.orichalcos
            .OrichalcosMessages.LeftDuel.TYPE,
            de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.LeftDuel.CODEC);
        clientbound(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.SealBegin.TYPE,""",
    "LeftDuel registered")

sub(N, """        onServer(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexDropped.TYPE,""",
    """        onServer(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.LeftDuel.TYPE,
            (message, player) -> de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls
                .playerLeftDuel(player));
        onServer(de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages.IndexDropped.TYPE,""",
    "LeftDuel handler")

sub(E, """    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)""",
    """    /**
     * Leaving the duel screen.
     * <p>
     * Tells the server the player has their character back, which is what the
     * Seal of Orichalcos waits for. Without this the server had no idea the
     * screen had closed and every seal death sat out its full fallback timer.
     */
    @Override
    public void onClose()
    {
        ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.LeftDuel());
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)""",
    "EngineDuelScreen.onClose")

# ---------- 5. on by default ----------
sub(C, 'sealOfOrichalcosDeath = bool(json, "sealOfOrichalcosDeath", false);',
    'sealOfOrichalcosDeath = bool(json, "sealOfOrichalcosDeath", true);',
    "default on")

sub(C, """     * Whether losing a duel with The Seal of Orichalcos on the field takes the
     * loser's soul — the anime's forfeit, and off by default because it kills
     * a player over a card game.
     */""",
    """     * Whether losing a duel with The Seal of Orichalcos on the field takes the
     * loser's soul — the anime's forfeit.
     * <p>
     * On by default. It only fires when that specific field spell is face-up
     * when the contest ends, which is a thing a player has to go out of their
     * way to make happen, and there is an in-game toggle for turning it off.
     * Defaulting it off meant the card did nothing until someone found a
     * setting they had no reason to look for.
     */""", "default-on rationale")

print("done")
