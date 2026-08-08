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

    // ---- client to server ----

    public record SaveDeck(String name, List<Integer> main, List<Integer> extra,
        List<Integer> side) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SaveDeck> TYPE = DdNetwork.type("deck_save");

        /**
         * A deck part: passcodes, capped so a client cannot send a huge one.
         * <p>
         * {@code ByteBufCodecs.VAR_INT} is typed on plain {@code ByteBuf}, and
         * the payload travels on a {@code RegistryFriendlyByteBuf}, so the
         * element codec is widened to the buffer this message actually uses.
         * Every codec that touches no registry has the same shape.
         */
        private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> PART =
            ByteBufCodecs.<RegistryFriendlyByteBuf, Integer>list(PART_LIMIT)
                .apply(ByteBufCodecs.VAR_INT.cast());

        public static final StreamCodec<RegistryFriendlyByteBuf, SaveDeck> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(NAME_LIMIT), SaveDeck::name,
            PART, SaveDeck::main,
            PART, SaveDeck::extra,
            PART, SaveDeck::side,
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

        DdNetwork.serverbound(SaveDeck.TYPE, SaveDeck.CODEC);
        DdNetwork.serverbound(CreateDeck.TYPE, CreateDeck.CODEC);
        DdNetwork.serverbound(RenameDeck.TYPE, RenameDeck.CODEC);
        DdNetwork.serverbound(DeleteDeck.TYPE, DeleteDeck.CODEC);
        DdNetwork.serverbound(CopyRecipe.TYPE, CopyRecipe.CODEC);
        DdNetwork.serverbound(SetActiveDeck.TYPE, SetActiveDeck.CODEC);
        DdNetwork.serverbound(PublishRecipe.TYPE, PublishRecipe.CODEC);
        DdNetwork.serverbound(ToggleFavourite.TYPE, ToggleFavourite.CODEC);
    }

    public static void registerServerHandlers()
    {
        DdNetwork.onServer(SaveDeck.TYPE, (message, player) -> answer(player,
            DeckEdits.saveDeck(player, message.name(), message.main(), message.extra(),
                message.side())));
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
