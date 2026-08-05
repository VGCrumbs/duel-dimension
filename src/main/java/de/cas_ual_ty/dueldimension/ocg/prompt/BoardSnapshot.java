package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.query.BoardState;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The field as one player may see it, flattened for the wire and for drawing.
 * <p>
 * Built from {@link BoardState}, which has already stripped anything the
 * viewer isn't entitled to know — so a face-down card arrives here with code 0
 * and {@code faceDown} set, and no amount of client tampering can recover what
 * it was.
 */
public record BoardSnapshot(Side self, Side opponent)
{
    public static final BoardSnapshot EMPTY =
        new BoardSnapshot(Side.empty(), Side.empty());

    /** A card in a zone, or an empty zone when {@code present} is false. */
    public record Slot(boolean present, int code, boolean faceDown, boolean defence, int attack, int defense)
    {
        public static final Slot EMPTY = new Slot(false, 0, false, false, 0, 0);

        public static Slot of(CardView card)
        {
            if(card == null)
            {
                return EMPTY;
            }
            return new Slot(true, card.code(), !card.isFaceUp(), !card.isAttackPosition(),
                Math.max(card.attack(), 0), Math.max(card.defense(), 0));
        }

        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeBoolean(present);
            if(present)
            {
                buffer.writeVarInt(code);
                buffer.writeBoolean(faceDown);
                buffer.writeBoolean(defence);
                buffer.writeVarInt(attack);
                buffer.writeVarInt(defense);
            }
        }

        public static Slot read(FriendlyByteBuf buffer)
        {
            if(!buffer.readBoolean())
            {
                return EMPTY;
            }
            return new Slot(true, buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record Side(int lifePoints, List<Slot> monsters, List<Slot> spells, List<Slot> hand,
        int deckCount, int graveCount, int extraCount)
    {
        public static Side empty()
        {
            return new Side(0, List.of(), List.of(), List.of(), 0, 0, 0);
        }

        public static Side of(BoardState.PlayerBoard board)
        {
            return new Side(board.lifePoints(),
                board.monsters().stream().map(Slot::of).toList(),
                board.spells().stream().map(Slot::of).toList(),
                board.hand().stream().map(Slot::of).toList(),
                board.deckCount(), board.grave().size(), board.extraCount());
        }

        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(lifePoints);
            writeSlots(buffer, monsters);
            writeSlots(buffer, spells);
            writeSlots(buffer, hand);
            buffer.writeVarInt(deckCount);
            buffer.writeVarInt(graveCount);
            buffer.writeVarInt(extraCount);
        }

        public static Side read(FriendlyByteBuf buffer)
        {
            return new Side(buffer.readVarInt(), readSlots(buffer), readSlots(buffer), readSlots(buffer),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public static BoardSnapshot of(BoardState state)
    {
        if(state == null)
        {
            return EMPTY;
        }
        return new BoardSnapshot(Side.of(state.self()), Side.of(state.opponent()));
    }

    public void write(FriendlyByteBuf buffer)
    {
        self.write(buffer);
        opponent.write(buffer);
    }

    public static BoardSnapshot read(FriendlyByteBuf buffer)
    {
        return new BoardSnapshot(Side.read(buffer), Side.read(buffer));
    }

    private static void writeSlots(FriendlyByteBuf buffer, List<Slot> slots)
    {
        buffer.writeVarInt(slots.size());
        slots.forEach(slot -> slot.write(buffer));
    }

    private static List<Slot> readSlots(FriendlyByteBuf buffer)
    {
        int count = buffer.readVarInt();
        List<Slot> slots = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            slots.add(Slot.read(buffer));
        }
        return slots;
    }
}
