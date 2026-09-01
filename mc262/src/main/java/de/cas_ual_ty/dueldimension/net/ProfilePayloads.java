package de.cas_ual_ty.dueldimension.net;

import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import de.cas_ual_ty.dueldimension.duel.match.Banlists;
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
    private static final int DECK_BOX_LIMIT = 32;

    /**
     * How long a banlist id may be. EDOPro's longest is around 24 characters,
     * so this is room rather than a fit.
     */
    private static final int BANLIST_ID_LIMIT = 64;

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
        // OFF the server thread, and that is the whole point of this method
        // existing separately from the one below.
        //
        // The first call after a launch pays for the whole ocgcore runtime:
        // JNA loads a 1.4 MB native, then two full-table reads run over 8.4 MB
        // of EDOPro's SQLite databases -- measured at 226-240 ms across three
        // runs. It was doing that inline in the JOIN handler, so every world
        // load blocked the server thread on it, and the answer is only ever read
        // by the deck editor, which a player may never open.
        //
        // A fresh thread rather than a pooled one: it runs once per join, and
        // Paths.defaults() joins the engine installer thread if an install is in
        // flight, so this must never occupy a worker something else is waiting
        // on. It must also never be called FROM the installer, which would make
        // that thread join itself.
        // Read here, on the server thread, and captured. Reaching through the
        // player for its level from the worker would be touching entity state
        // off-thread for no reason -- the server does not change identity.
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if(server == null)
        {
            return;
        }
        Thread worker = new Thread(() -> sendEngineUnknown(player, server),
            "dueldimension-engine-unknown");
        worker.setDaemon(true);
        // Below the server thread. Nothing waits on this, and a join should
        // never be slower because of it -- which was the original complaint.
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /**
     * The actual work, on whatever thread called it.
     * <p>
     * Split out so the cost is visible in a stack trace under its own name, and
     * so a caller that already has a thread to spare can run it directly.
     */
    private static void sendEngineUnknown(ServerPlayer player,
        net.minecraft.server.MinecraftServer server)
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
        // Back onto the server thread to send. Packets go out from there, and by
        // now the player may have left -- a join immediately abandoned is
        // exactly the case a background thread introduces. Asked of the player
        // LIST rather than of the player, because that is the server's own
        // answer to "is this still a connected player" and is read on the thread
        // that owns it.
        server.execute(() ->
        {
            if(server.getPlayerList().getPlayer(player.getUUID()) != player)
            {
                return;
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new EngineUnknown(unknown));
        });
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

    /**
     * "Send me my profile again."
     * <p>
     * A manual resync, behind a shift-held button in the deck list. It carries
     * nothing: the server already knows who asked, and the answer is the same
     * {@link Sync} every other change sends.
     * <p>
     * It exists because a client whose deck list has gone wrong has, until now,
     * had no way to ask for the truth again short of relogging. The underlying
     * causes are being fixed -- see {@code DuelProfiles.LAST_SEEN} for the
     * mid-respawn one -- but a button that costs one packet is worth having
     * regardless, and it turns "my decks vanished" into something a player can
     * act on and a report that says whether the data or only the view was lost.
     */
    public record RefreshProfile() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<RefreshProfile> TYPE =
            DdNetwork.type("profile_refresh");

        public static final StreamCodec<RegistryFriendlyByteBuf, RefreshProfile> CODEC =
            StreamCodec.unit(new RefreshProfile());

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

    /**
     * Which cards in a deck a Destiny Draw may reach.
     *
     * <h2>Its own message rather than a field on SaveDeck</h2>
     * The same reason the sleeve, the case and the banlist have theirs: it is a
     * property of the deck rather than of its contents, and it changes on its
     * own. Practically it also stays clear of SaveDeck's hand-written codec,
     * which exists because {@code StreamCodec.composite} stops at six
     * components and SaveDeck already has seven -- an eighth would have to be
     * threaded through two mirrored lists that only fail over the network.
     */
    public record SetDeckDestiny(String name, List<Integer> codes) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetDeckDestiny> TYPE =
            DdNetwork.type("deck_destiny");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetDeckDestiny> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), SetDeckDestiny::name,
                ByteBufCodecs.<RegistryFriendlyByteBuf, Integer>list(PART_LIMIT)
                    .apply(ByteBufCodecs.VAR_INT.cast()), SetDeckDestiny::codes,
                SetDeckDestiny::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Chooses one of the built-in basic-colour cases for a deck. */
    public record SetDeckBox(String name, String deckBox) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetDeckBox> TYPE =
            DdNetwork.type("deck_box");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetDeckBox> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), SetDeckBox::name,
                ByteBufCodecs.stringUtf8(DECK_BOX_LIMIT), SetDeckBox::deckBox,
                SetDeckBox::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record MoveDeck(String name, String targetName) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<MoveDeck> TYPE =
            DdNetwork.type("deck_move");

        public static final StreamCodec<RegistryFriendlyByteBuf, MoveDeck> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), MoveDeck::name,
                ByteBufCodecs.stringUtf8(NAME_LIMIT), MoveDeck::targetName,
                MoveDeck::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Client to server: star this sealed product, or unstar it. */
    public record ToggleFavouritePack(String code) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<ToggleFavouritePack> TYPE =
            DdNetwork.type("toggle_favourite_pack");
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFavouritePack> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ToggleFavouritePack::code,
                ToggleFavouritePack::new);

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

    /**
     * Every forbidden/limited list this server offers, limits and all.
     *
     * <h2>Why the whole list and not just its name</h2>
     * Because the deck editor has to ENFORCE it, not merely display it. Copy
     * limits, the dimming of a card already at its maximum, and the legality
     * check on a finished deck all go through
     * {@link de.cas_ual_ty.dueldimension.duel.profile.DeckLimits}, which needs
     * the passcode-to-limit map itself. Sending ids and display names alone --
     * which is what the duel lobby is sent, because a lobby only has to NAME the
     * list it is playing under -- would leave the editor able to show a list and
     * unable to apply one.
     * <p>
     * The lists live on the server: {@link Banlists} reads EDOPro's own
     * {@code .lflist.conf} files, and a client has no reason to have EDOPro
     * installed. So this is the only way the editor can know them, and it is
     * still the server's answer that counts -- a deck save is re-checked there.
     *
     * <h2>Size</h2>
     * A current TCG list is a few hundred entries and a server offers a few
     * dozen lists, so this is tens of kilobytes. It rides along with
     * {@link Sync}, which carries the player's whole collection and is larger
     * again; sending it separately on join would save nothing worth the second
     * code path that could go out of step.
     */
    public record BanlistCatalogue(List<String> ids, List<String> names,
        List<java.util.Map<Integer, Integer>> limits) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<BanlistCatalogue> TYPE =
            DdNetwork.type("profile_banlists");

        public static final StreamCodec<RegistryFriendlyByteBuf, BanlistCatalogue> CODEC =
            CustomPacketPayload.codec(BanlistCatalogue::encode, BanlistCatalogue::decode);

        /** What the server has, in the order {@link Banlists#all} offers it. */
        public static BanlistCatalogue of(List<Banlist> lists)
        {
            List<String> ids = new java.util.ArrayList<>(lists.size());
            List<String> names = new java.util.ArrayList<>(lists.size());
            List<java.util.Map<Integer, Integer>> limits =
                new java.util.ArrayList<>(lists.size());
            for(Banlist list : lists)
            {
                ids.add(list.id());
                names.add(list.displayName());
                limits.add(list.limits());
            }
            return new BanlistCatalogue(ids, names, limits);
        }

        /** Back into the objects the editor works with. */
        public List<Banlist> toBanlists()
        {
            int count = Math.min(ids.size(), Math.min(names.size(), limits.size()));
            List<Banlist> lists = new java.util.ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                lists.add(new Banlist(ids.get(i), names.get(i), limits.get(i)));
            }
            return lists;
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void encode(BanlistCatalogue message, RegistryFriendlyByteBuf buffer)
        {
            int count = Math.min(message.ids().size(),
                Math.min(message.names().size(), message.limits().size()));
            buffer.writeVarInt(count);
            for(int i = 0; i < count; i++)
            {
                buffer.writeUtf(message.ids().get(i), BANLIST_ID_LIMIT);
                buffer.writeUtf(message.names().get(i), BANLIST_ID_LIMIT);
                java.util.Map<Integer, Integer> entries = message.limits().get(i);
                buffer.writeVarInt(entries.size());
                entries.forEach((passcode, limit) ->
                {
                    buffer.writeVarInt(passcode);
                    buffer.writeVarInt(limit);
                });
            }
        }

        public static BanlistCatalogue decode(RegistryFriendlyByteBuf buffer)
        {
            int count = buffer.readVarInt();
            List<String> ids = new java.util.ArrayList<>(count);
            List<String> names = new java.util.ArrayList<>(count);
            List<java.util.Map<Integer, Integer>> limits = new java.util.ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                ids.add(buffer.readUtf(BANLIST_ID_LIMIT));
                names.add(buffer.readUtf(BANLIST_ID_LIMIT));
                int entries = buffer.readVarInt();
                // Insertion-ordered, so a list decoded here iterates the way the
                // .conf file wrote it. Banlist keeps the map it is handed in the
                // same order, which is what makes two catalogues comparable when
                // one of them is wrong.
                java.util.Map<Integer, Integer> map =
                    new java.util.LinkedHashMap<>(Math.max(4, entries));
                for(int entry = 0; entry < entries; entry++)
                {
                    map.put(buffer.readVarInt(), buffer.readVarInt());
                }
                limits.add(map);
            }
            return new BanlistCatalogue(ids, names, limits);
        }
    }

    /**
     * "Build this deck to this list."
     * <p>
     * A request, not a statement, for the reason {@link SetDeckSleeve} gives:
     * the id is checked against what this server actually offers, in
     * {@link DeckEdits#setDeckBanlist}, because a client naming a list is
     * naming a set of copy limits.
     */
    public record SetDeckBanlist(String name, String banlist) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetDeckBanlist> TYPE =
            DdNetwork.type("deck_banlist");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetDeckBanlist> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.stringUtf8(NAME_LIMIT), SetDeckBanlist::name,
                ByteBufCodecs.stringUtf8(BANLIST_ID_LIMIT), SetDeckBanlist::banlist,
                SetDeckBanlist::new);

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
        DdNetwork.clientbound(BanlistCatalogue.TYPE, BanlistCatalogue.CODEC);

        DdNetwork.serverbound(SaveDeck.TYPE, SaveDeck.CODEC);
        DdNetwork.serverbound(CreateDeck.TYPE, CreateDeck.CODEC);
        DdNetwork.serverbound(RenameDeck.TYPE, RenameDeck.CODEC);
        DdNetwork.serverbound(MoveDeck.TYPE, MoveDeck.CODEC);
        DdNetwork.serverbound(DeleteDeck.TYPE, DeleteDeck.CODEC);
        DdNetwork.serverbound(CopyRecipe.TYPE, CopyRecipe.CODEC);
        DdNetwork.serverbound(SetActiveDeck.TYPE, SetActiveDeck.CODEC);
        DdNetwork.serverbound(RefreshProfile.TYPE, RefreshProfile.CODEC);
        DdNetwork.serverbound(PublishRecipe.TYPE, PublishRecipe.CODEC);
        DdNetwork.serverbound(SetDeckSleeve.TYPE, SetDeckSleeve.CODEC);
        DdNetwork.serverbound(SetDeckBox.TYPE, SetDeckBox.CODEC);
        DdNetwork.serverbound(SetDeckBanlist.TYPE, SetDeckBanlist.CODEC);
        DdNetwork.serverbound(SetDeckDestiny.TYPE, SetDeckDestiny.CODEC);
        DdNetwork.serverbound(ToggleFavourite.TYPE, ToggleFavourite.CODEC);
        DdNetwork.serverbound(ToggleFavouritePack.TYPE, ToggleFavouritePack.CODEC);
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
        DdNetwork.onServer(MoveDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.moveDeck(player, message.name(), message.targetName())));
        DdNetwork.onServer(DeleteDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.deleteDeck(player, message.name())));
        DdNetwork.onServer(CopyRecipe.TYPE, (message, player) ->
            answer(player, DeckEdits.copyRecipe(player, message.recipe(), message.name())));
        // Reads nothing and writes nothing -- it re-sends what the server
        // already holds. Safe to spam, which matters for a button whose whole
        // purpose is being pressed when something looks wrong.
        DdNetwork.onServer(RefreshProfile.TYPE, (message, player) -> sync(player));

        DdNetwork.onServer(SetActiveDeck.TYPE, (message, player) ->
            answer(player, DeckEdits.setActive(player, message.name())));
        DdNetwork.onServer(PublishRecipe.TYPE, (message, player) ->
            answer(player, DeckEdits.publishRecipe(player, message.name(), message.asRecipe())));
        DdNetwork.onServer(SetDeckSleeve.TYPE, (message, player) ->
            answer(player, DeckEdits.setDeckSleeve(player, message.name(), message.sleeve())));
        DdNetwork.onServer(SetDeckBox.TYPE, (message, player) ->
            answer(player, DeckEdits.setDeckBox(player, message.name(), message.deckBox())));
        DdNetwork.onServer(SetDeckBanlist.TYPE, (message, player) ->
            answer(player, DeckEdits.setDeckBanlist(player, message.name(), message.banlist())));
        DdNetwork.onServer(SetDeckDestiny.TYPE, (message, player) ->
            answer(player, DeckEdits.setDeckDestiny(player, message.name(), message.codes())));
        DdNetwork.onServer(ToggleFavourite.TYPE, (message, player) ->
            answer(player, DeckEdits.toggleFavourite(player, message.passcode())));
        DdNetwork.onServer(ToggleFavouritePack.TYPE, (message, player) ->
            answer(player, DeckEdits.toggleFavouritePack(player, message.code())));
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
        syncBanlists(player);
    }

    /**
     * Sends the lists this server offers, so the editor can enforce one.
     * <p>
     * Part of {@link #sync} rather than a join-only message: the editor cannot
     * apply a list it has not been told, and a catalogue that arrived once would
     * be one reconnect away from an editor silently enforcing nothing.
     */
    public static void syncBanlists(ServerPlayer player)
    {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
            BanlistCatalogue.of(Banlists.all()));
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
