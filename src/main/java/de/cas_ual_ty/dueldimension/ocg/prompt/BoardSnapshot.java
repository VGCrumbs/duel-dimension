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
 * viewer isn't entitled to know — a face-down card arrives here with code 0
 * and {@code faceDown} set, so no client tampering can recover what it was.
 * <p>
 * Zone sequences follow the core: monster 0–4 main row, 5–6 extra monster
 * zones; spell 0–4 backrow, 5 field spell, 6–7 pendulum zones.
 *
 * @param turn       turn number (from MSG_NEW_TURN count)
 * @param phase      PHASE_* value of the current phase
 * @param turnPlayer 0/1 whose turn it is, from the viewer's seat numbering
 */
public record BoardSnapshot(Side self, Side opponent, int turn, int phase, int turnPlayer)
{
    public static final BoardSnapshot EMPTY = new BoardSnapshot(Side.empty(), Side.empty(), 0, 0, 0);

    /** A card in a zone, or an empty zone when {@code present} is false. */
    public record Slot(boolean present, int code, boolean faceDown, boolean defence, int attack, int defense,
        int baseAttack, int baseDefense, int overlays)
    {
        public static final Slot EMPTY = new Slot(false, 0, false, false, 0, 0, 0, 0, 0);

        /** True if an effect has moved this stat off its printed value. */
        public boolean attackBoosted()
        {
            return attack > baseAttack;
        }

        public boolean attackWeakened()
        {
            return attack < baseAttack;
        }

        public boolean defenseBoosted()
        {
            return defense > baseDefense;
        }

        public boolean defenseWeakened()
        {
            return defense < baseDefense;
        }

        public static Slot of(CardView card)
        {
            if(card == null)
            {
                return EMPTY;
            }
            return new Slot(true, card.code(), !card.isFaceUp(), !card.isAttackPosition(),
                Math.max(card.attack(), 0), Math.max(card.defense(), 0),
                Math.max(card.baseAttack(), 0), Math.max(card.baseDefense(), 0), 0);
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
                buffer.writeVarInt(baseAttack);
                buffer.writeVarInt(baseDefense);
                buffer.writeVarInt(overlays);
            }
        }

        public static Slot read(FriendlyByteBuf buffer)
        {
            if(!buffer.readBoolean())
            {
                return EMPTY;
            }
            return new Slot(true, buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt());
        }
    }

    public record Side(int lifePoints, List<Slot> monsters, List<Slot> spells, List<Slot> hand,
        List<Slot> grave, List<Slot> banished, List<Slot> extra, int deckCount)
    {
        public static Side empty()
        {
            return new Side(0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        }

        public static Side of(BoardState.PlayerBoard board)
        {
            return new Side(board.lifePoints(),
                board.monsters().stream().map(Slot::of).toList(),
                board.spells().stream().map(Slot::of).toList(),
                board.hand().stream().map(Slot::of).toList(),
                board.grave().stream().map(Slot::of).toList(),
                board.banished().stream().map(Slot::of).toList(),
                board.extra().stream().map(Slot::of).toList(),
                board.deckCount());
        }

        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(lifePoints);
            writeSlots(buffer, monsters);
            writeSlots(buffer, spells);
            writeSlots(buffer, hand);
            writeSlots(buffer, grave);
            writeSlots(buffer, banished);
            writeSlots(buffer, extra);
            buffer.writeVarInt(deckCount);
        }

        public static Side read(FriendlyByteBuf buffer)
        {
            return new Side(buffer.readVarInt(), readSlots(buffer), readSlots(buffer), readSlots(buffer),
                readSlots(buffer), readSlots(buffer), readSlots(buffer), buffer.readVarInt());
        }
    }

    public static BoardSnapshot of(BoardState state)
    {
        return of(state, 0, 0, 0);
    }

    public static BoardSnapshot of(BoardState state, int turn, int phase, int turnPlayer)
    {
        if(state == null)
        {
            return EMPTY;
        }
        return new BoardSnapshot(Side.of(state.self()), Side.of(state.opponent()), turn, phase, turnPlayer);
    }

    public void write(FriendlyByteBuf buffer)
    {
        self.write(buffer);
        opponent.write(buffer);
        buffer.writeVarInt(turn);
        buffer.writeVarInt(phase);
        buffer.writeVarInt(turnPlayer);
    }

    public static BoardSnapshot read(FriendlyByteBuf buffer)
    {
        return new BoardSnapshot(Side.read(buffer), Side.read(buffer),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
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
