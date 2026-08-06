package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The deck editor's traffic.
 * <p>
 * The shape is deliberately the same as the shop's: the client is told what it
 * owns and asks for changes, and the server decides. A client can therefore ask
 * to build a deck out of cards it does not have, and be refused — which is the
 * entire point, since the client is the one place a player can edit freely.
 * <p>
 * Every change is answered with a fresh {@link Sync}, so the client's copy is
 * always the server's copy rather than whatever it hoped would happen.
 */
public final class ProfileMessages
{
    /** A deck name that cannot be sent, so a malicious one cannot be huge. */
    private static final int NAME_LIMIT = 64;
    /** More cards than any legal deck, so a legal edit always fits. */
    private static final int PART_LIMIT = 128;

    private ProfileMessages()
    {
    }

    /** Server to client: this is your collection. Sent to that player alone. */
    public record Sync(CompoundTag profile)
    {
        public static void encode(Sync message, FriendlyByteBuf buffer)
        {
            buffer.writeNbt(message.profile());
        }

        public static Sync decode(FriendlyByteBuf buffer)
        {
            return new Sync(buffer.readNbt());
        }

        public static void handle(Sync message, Supplier<NetworkEvent.Context> context)
        {
            context.get().enqueueWork(() -> DuelDimension.proxy.setDuelProfile(message.profile()));
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Client to server: this is what my deck now contains.
     * <p>
     * Carries the whole deck rather than a change to it. An edit is a handful
     * of cards and the deck is at most ninety, so describing the result costs
     * little and removes a whole class of problem: there is no sequence of
     * changes to get out of step, and the server checks the thing it is about
     * to store rather than trusting that a series of steps added up.
     */
    public record SaveDeck(String name, List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        public static void encode(SaveDeck message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.name(), NAME_LIMIT);
            writePart(buffer, message.main());
            writePart(buffer, message.extra());
            writePart(buffer, message.side());
        }

        public static SaveDeck decode(FriendlyByteBuf buffer)
        {
            return new SaveDeck(buffer.readUtf(NAME_LIMIT), readPart(buffer), readPart(buffer),
                readPart(buffer));
        }

        public static void handle(SaveDeck message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DeckEdits.saveDeck(player, message.name(), message.main(),
                message.extra(), message.side()));
        }
    }

    /** Client to server: create an empty deck under this name. */
    public record CreateDeck(String name)
    {
        public static void encode(CreateDeck message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.name(), NAME_LIMIT);
        }

        public static CreateDeck decode(FriendlyByteBuf buffer)
        {
            return new CreateDeck(buffer.readUtf(NAME_LIMIT));
        }

        public static void handle(CreateDeck message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DeckEdits.createDeck(player, message.name()));
        }
    }

    /** Client to server: rename one of my own decks. */
    public record RenameDeck(String from, String to)
    {
        public static void encode(RenameDeck message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.from(), NAME_LIMIT);
            buffer.writeUtf(message.to(), NAME_LIMIT);
        }

        public static RenameDeck decode(FriendlyByteBuf buffer)
        {
            return new RenameDeck(buffer.readUtf(NAME_LIMIT), buffer.readUtf(NAME_LIMIT));
        }

        public static void handle(RenameDeck message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DeckEdits.renameDeck(player, message.from(), message.to()));
        }
    }

    /** Client to server: delete one of my own decks. */
    public record DeleteDeck(String name)
    {
        public static void encode(DeleteDeck message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.name(), NAME_LIMIT);
        }

        public static DeleteDeck decode(FriendlyByteBuf buffer)
        {
            return new DeleteDeck(buffer.readUtf(NAME_LIMIT));
        }

        public static void handle(DeleteDeck message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DeckEdits.deleteDeck(player, message.name()));
        }
    }

    /** Client to server: copy a granted recipe into a deck of my own. */
    public record CopyRecipe(String recipe, String name)
    {
        public static void encode(CopyRecipe message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.recipe(), NAME_LIMIT);
            buffer.writeUtf(message.name(), NAME_LIMIT);
        }

        public static CopyRecipe decode(FriendlyByteBuf buffer)
        {
            return new CopyRecipe(buffer.readUtf(NAME_LIMIT), buffer.readUtf(NAME_LIMIT));
        }

        public static void handle(CopyRecipe message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DeckEdits.copyRecipe(player, message.recipe(), message.name()));
        }
    }

    /** Client to server: this is the deck I duel with. */
    public record SetActiveDeck(String name)
    {
        public static void encode(SetActiveDeck message, FriendlyByteBuf buffer)
        {
            buffer.writeUtf(message.name(), NAME_LIMIT);
        }

        public static SetActiveDeck decode(FriendlyByteBuf buffer)
        {
            return new SetActiveDeck(buffer.readUtf(NAME_LIMIT));
        }

        public static void handle(SetActiveDeck message, Supplier<NetworkEvent.Context> context)
        {
            server(context, player -> DeckEdits.setActive(player, message.name()));
        }
    }

    /**
     * Runs a change on the server thread for the player who asked, and tells
     * them the result. Every client-to-server message here does exactly this,
     * so the sync-on-change rule is in one place rather than repeated six times
     * and eventually forgotten in one of them.
     */
    private static void server(Supplier<NetworkEvent.Context> context,
        java.util.function.Function<ServerPlayer, String> change)
    {
        context.get().enqueueWork(() ->
        {
            ServerPlayer player = context.get().getSender();
            if(player == null)
            {
                return;
            }
            String refusal = change.apply(player);
            if(refusal != null && !refusal.isEmpty())
            {
                player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            }
            // Sent either way. A refused change still ends with the client
            // holding what the server holds, which is how an editor that
            // guessed wrong corrects itself.
            DuelProfiles.saveAndSync(player);
        });
        context.get().setPacketHandled(true);
    }

    private static void writePart(FriendlyByteBuf buffer, List<Integer> part)
    {
        List<Integer> cards = part == null ? List.of() : part;
        buffer.writeVarInt(Math.min(cards.size(), PART_LIMIT));
        for(int i = 0; i < cards.size() && i < PART_LIMIT; i++)
        {
            buffer.writeVarInt(cards.get(i));
        }
    }

    private static List<Integer> readPart(FriendlyByteBuf buffer)
    {
        int count = buffer.readVarInt();
        if(count < 0 || count > PART_LIMIT)
        {
            // A length is the one field that can make a decoder allocate, so it
            // is bounded before it is believed.
            throw new IllegalArgumentException("Deck part length out of range: " + count);
        }
        List<Integer> cards = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            cards.add(buffer.readVarInt());
        }
        return cards;
    }
}
