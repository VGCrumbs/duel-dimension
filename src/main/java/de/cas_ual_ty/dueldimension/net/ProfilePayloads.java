package de.cas_ual_ty.dueldimension.net;

import de.cas_ual_ty.dueldimension.duel.profile.DeckEdits;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.duel.profile.FreeMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The deck editor talking to the server, and the server answering.
 * <p>
 * The rules are unchanged and still live in {@link DeckEdits} — a client says
 * what it would like, the server decides, and what comes back is what the
 * server holds. These are only the envelopes.
 * <p>
 * One improvement falls out of the new API for free. The Forge version sent a
 * profile as a raw {@code CompoundTag} and trusted both ends to agree on what
 * was in it; a profile has a Codec now, so the same message carries a
 * {@link DuelProfile} and the wire format is the one thing neither side can get
 * wrong.
 */
public final class ProfilePayloads
{
    private ProfilePayloads()
    {
    }

    /** How long a deck name may be on the wire. */
    private static final int NAME_LIMIT = 64;

    /** A deck's three parts, each capped so a client cannot send a huge one. */
    private static final int PART_LIMIT = 256;

    /** How long a sleeve id may be. The longest this build has is 23 characters. */
    private static final int SLEEVE_LIMIT = 64;

    // ---- server to client ----

    /** The whole profile: what this player owns, has built and has starred. */
    public record Sync(DuelProfile profile) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<Sync> TYPE = DdNetwork.type("profile_sync");

        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.fromCodec(DuelProfile.CODEC), Sync::profile, Sync::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Whether the deck builder ignores what the player owns. */
    public record SyncFreeMode(boolean enabled) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SyncFreeMode> TYPE =
            DdNetwork.type("profile_free_mode");

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncFreeMode> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SyncFreeMode::enabled, SyncFreeMode::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * Server to client: passcodes the DUEL ENGINE does not know.
     * <p>
     * The mod's card database and ocgcore's are separate, and ours runs ahead
     * of it -- a set imported from YGOPRODeck can contain cards EDOPro has not
     * shipped yet. A card the engine has never heard of is not refused when the
     * duel starts: {@code field::add_card} rewrites its location, so an unknown
     * Xyz is silently moved into the MAIN deck and drawn as a normal card.
     * <p>
     * Only the server can answer this -- the engine is its -- so it says so
     * once on join and the editor hides them. Empty when no engine is installed,
     * in which case nothing is hidden rather than everything.
     */
    public record EngineUnknown(List<Integer> codes) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<EngineUnknown> TYPE =
            DdNetwork.type("profile_engine_unknown");

        public static final StreamCodec<RegistryFriendlyByteBuf, EngineUnknown> CODEC =
            CustomPacketPayload.codec(EngineUnknown::encode, EngineUnknown::decode);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(EngineUnknown message, RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(message.codes().size());
            message.codes().forEach(buffer::writeVarInt);
        }

        public static EngineUnknown decode(RegistryFriendlyByteBuf buffer)
        {
            int count = buffer.readVarInt();
            List<Integer> codes = new java.util.ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                codes.add(buffer.readVarInt());
            }
            return new EngineUnknown(codes);
        }
    }

    /**
     * Works out which of our cards the engine lacks, and tells this player.
     * <p>
     * Computed per call rather than cached: it is one map lookup per card over
     * a database that is already in memory, it happens once per join, and a
     * cache would be a third copy of a fact that two databases already disagree
     * about.
     */
    public static void syncEngineUnknown(ServerPlayer player)
    {
        List<Integer> unknown = new java.util.ArrayList<>();
        de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.Paths paths =
            de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.Paths.defaults();
        de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime engine =
            paths.missing() != null ? null
                : de.cas_ual_ty.dueldimension.ocg.session.EngineRuntime.get(paths);
        if(engine != null)
        {
            for(de.cas_ual_ty.dueldimension.card.properties.Properties card
                : de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST)
            {
                if(card != null && card.getId() > 0
                    && engine.cards().get((int)card.getId()) == null)
                {
                    unknown.add((int)card.getId());
                }
            }
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new EngineUnknown(unknown));
    }

    // ---- client to server ----

    /**
     * A deck's contents: three lists of passcodes and, beside each, which
     * artwork the copy at that position wears.
     * <p>
     * The arts travel <em>inside</em> this message rather than being carried
     * over server side the way the sleeve is, and that is not a preference. A
     * sleeve belongs to the deck; an art belongs to a <em>position</em>, and
     * this message is what changes the positions — remove one card and every
     * copy behind it shifts, so arts read off the old stored deck would land on
     * the wrong copies. They only mean anything next to the lists they describe.
     * <p>
     * Each art list may be shorter than the part it belongs to, and usually is:
     * a trailing run of printed-art copies says nothing, so it is not sent. A
     * deck with no alternate artwork anywhere sends three empty lists.
     */
    public record SaveDeck(String name, List<Integer> main, List<Integer> extra,
        List<Integer> side, List<Integer> mainArts, List<Integer> extraArts,
        List<Integer> sideArts) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SaveDeck> TYPE = DdNetwork.type("deck_save");

        /**
         * A deck part: passcodes, capped so a client cannot send a huge one.
         * <p>
         * {@code ByteBufCodecs.VAR_INT} is typed on plain {@code ByteBuf}, and
         * the payload travels on a {@code RegistryFriendlyByteBuf}, so the
         * element codec is widened to the buffer this message actually uses.
         * Every codec that touches no registry has the same shape.
         * <p>
         * The art lists reuse it, and therefore the same cap: an art list can
         * never usefully be longer than the part it describes, so the bound
         * that stops a huge deck stops a huge art list too.
         */
        private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> PART =
            ByteBufCodecs.<RegistryFriendlyByteBuf, Integer>list(PART_LIMIT)
                .apply(ByteBufCodecs.VAR_INT.cast());

        public static final StreamCodec<RegistryFriendlyByteBuf, SaveDeck> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(NAME_LIMIT), SaveDeck::name,
            PART, SaveDeck::main,
            PART, SaveDeck::extra,
            PART, SaveDeck::side,
            PART, SaveDeck::mainArts,
            PART, SaveDeck::extraArts,
            PART, SaveDeck::sideArts,
            SaveDeck::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record CreateDeck(String name) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<CreateDeck> TYPE =
            DdNetwork.type("deck_create");

        public static final StreamCodec<RegistryFriendlyByteBuf, CreateDeck> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(NAME_LIMIT), CreateDeck::name,
                CreateDeck::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record RenameDeck(String from, String to) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<RenameDeck> TYPE =
            DdNetwork.type("deck_rename");

        public static final StreamCodec<RegistryFriendlyByteBuf, RenameDeck> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), RenameDeck::from,
                ByteBufCodecs.stringUtf8(NAME_LIMIT), RenameDeck::to,
                RenameDeck::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record DeleteDeck(String name) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<DeleteDeck> TYPE =
            DdNetwork.type("deck_delete");

        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteDeck> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(NAME_LIMIT), DeleteDeck::name,
                DeleteDeck::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record CopyRecipe(String recipe, String name) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<CopyRecipe> TYPE =
            DdNetwork.type("recipe_copy");

        public static final StreamCodec<RegistryFriendlyByteBuf, CopyRecipe> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), CopyRecipe::recipe,
                ByteBufCodecs.stringUtf8(NAME_LIMIT), CopyRecipe::name,
                CopyRecipe::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record SetActiveDeck(String name) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetActiveDeck> TYPE =
            DdNetwork.type("deck_active");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetActiveDeck> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(NAME_LIMIT), SetActiveDeck::name,
                SetActiveDeck::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record PublishRecipe(String name, boolean asRecipe) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PublishRecipe> TYPE =
            DdNetwork.type("recipe_publish");

        public static final StreamCodec<RegistryFriendlyByteBuf, PublishRecipe> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), PublishRecipe::name,
                ByteBufCodecs.BOOL, PublishRecipe::asRecipe,
                PublishRecipe::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * "Dress this deck in these sleeves."
     * <p>
     * Carries the sleeve's <em>name</em> and not its enum index, for the reason
     * {@link de.cas_ual_ty.dueldimension.duel.profile.Sleeves} gives: the index
     * is a position in a list of cosmetics that grows, and this request ends in
     * something written to disk. It is also a request rather than a statement —
     * ownership is checked server side, in {@link DeckEdits#setDeckSleeve},
     * because a client that could assert what it owns owns everything.
     */
    public record SetDeckSleeve(String name, String sleeve) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetDeckSleeve> TYPE =
            DdNetwork.type("deck_sleeve");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetDeckSleeve> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), SetDeckSleeve::name,
                ByteBufCodecs.stringUtf8(SLEEVE_LIMIT), SetDeckSleeve::sleeve,
                SetDeckSleeve::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record ToggleFavourite(int passcode) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ToggleFavourite> TYPE =
            DdNetwork.type("card_favourite");

        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFavourite> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ToggleFavourite::passcode,
                ToggleFavourite::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ---- registration ----

    public static void register()
    {
        DdNetwork.clientbound(Sync.TYPE, Sync.CODEC);
        DdNetwork.clientbound(SyncFreeMode.TYPE, SyncFreeMode.CODEC);
        DdNetwork.clientbound(EngineUnknown.TYPE, EngineUnknown.CODEC);

        DdNetwork.serverbound(SaveDeck.TYPE, SaveDeck.CODEC);
        DdNetwork.serverbound(CreateDeck.TYPE, CreateDeck.CODEC);
        DdNetwork.serverbound(RenameDeck.TYPE, RenameDeck.CODEC);
        DdNetwork.serverbound(DeleteDeck.TYPE, DeleteDeck.CODEC);
        DdNetwork.serverbound(CopyRecipe.TYPE, CopyRecipe.CODEC);
        DdNetwork.serverbound(SetActiveDeck.TYPE, SetActiveDeck.CODEC);
        DdNetwork.serverbound(PublishRecipe.TYPE, PublishRecipe.CODEC);
        DdNetwork.serverbound(SetDeckSleeve.TYPE, SetDeckSleeve.CODEC);
        DdNetwork.serverbound(ToggleFavourite.TYPE, ToggleFavourite.CODEC);
    }

    public static void registerServerHandlers()
    {
        DdNetwork.onServer(SaveDeck.TYPE, (message, player) -> answer(player,
            DeckEdits.saveDeck(player, message.name(), message.main(), message.extra(),
                message.side(), message.mainArts(), message.extraArts(), message.sideArts())));
        DdNetwork.onServer(CreateDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.createDeck(player, message.name())));
        DdNetwork.onServer(RenameDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.renameDeck(player, message.from(), message.to())));
        DdNetwork.onServer(DeleteDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.deleteDeck(player, message.name())));
        DdNetwork.onServer(CopyRecipe.TYPE, (message, player) ->
            answer(player, DeckEdits.copyRecipe(player, message.recipe(), message.name())));
        DdNetwork.onServer(SetActiveDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.setActive(player, message.name())));
        DdNetwork.onServer(PublishRecipe.TYPE, (message, player) ->
            answer(player, DeckEdits.publishRecipe(player, message.name(), message.asRecipe())));
        DdNetwork.onServer(SetDeckSleeve.TYPE, (message, player) ->
            answer(player, DeckEdits.setDeckSleeve(player, message.name(), message.sleeve())));
        DdNetwork.onServer(ToggleFavourite.TYPE, (message, player) ->
            answer(player, DeckEdits.toggleFavourite(player, message.passcode())));
    }

    /**
     * Tells the player why an edit was refused and restores the authoritative
     * profile only in that case.
     * <p>
     * The editor applies accepted changes optimistically and therefore already
     * holds exactly what the server stored. Echoing the whole profile after
     * every successful autosave races with the next click: an older success
     * can replace a newer local deck before that newer version is sent. A
     * refusal still needs the full sync so an invalid client guess is undone.
     */
    private static void answer(ServerPlayer player, String refusal)
    {
        if(refusal != null && !refusal.isEmpty())
        {
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
        DuelProfiles.save(player);
        if(refusal != null)
        {
            sync(player);
        }
    }

    /** Sends the player their own profile, and only their own. */
    public static void sync(ServerPlayer player)
    {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new Sync(DuelProfiles.get(player)));
        syncFreeMode(player);
        syncEngineUnknown(player);
    }

    /** Sends the world-wide free-mode switch to one client. */
    public static void syncFreeMode(ServerPlayer player)
    {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            new SyncFreeMode(FreeMode.isEnabled(player)));
    }

    /** Updates every connected editor immediately after the switch changes. */
    public static void syncFreeMode(net.minecraft.server.MinecraftServer server)
    {
        for(ServerPlayer player : server.getPlayerList().getPlayers())
        {
            syncFreeMode(player);
        }
    }
}
