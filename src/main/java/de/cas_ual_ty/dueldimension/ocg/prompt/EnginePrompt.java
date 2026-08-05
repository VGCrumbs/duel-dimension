package de.cas_ual_ty.dueldimension.ocg.prompt;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * A decision, described in terms a screen can draw and a player can click.
 * <p>
 * All engine semantics are resolved server-side: by the time a prompt reaches
 * the client it is a title, a list of labelled options, and a {@link Kind}
 * saying how they are answered. The client never sees passcodes it isn't
 * entitled to, response encodings or hidden information, and answers with
 * option indices (plus a card code for the announce search, which the server
 * re-validates) — a modified client can pick among what it was offered, not
 * forge an illegal play.
 */
public record EnginePrompt(Kind kind, String title, List<Option> options, int minSelect, int maxSelect,
    boolean cancelable, BoardSnapshot field)
{
    public enum Kind
    {
        /** Pick exactly one option. */
        CHOOSE,
        /** Pick min..max distinct options, then confirm. */
        MULTI,
        /** Options are zones; pick count of them by clicking the board. */
        PLACES,
        /** Click all options in the order they should end up, or decline. */
        SORT,
        /** Distribute a total over the options; each has a stock (option max). */
        COUNTERS,
        /** Type/search a card name; the answer is a card code. */
        DECLARE_CARD
    }

    /**
     * @param label    what the player reads
     * @param detail   optional second line (zone, stats, effect text)
     * @param cardCode passcode for art lookup, 0 when the option isn't a card
     * @param zone     board-highlight metadata: -1 none, else packed
     *                 (opponent?16:0) | (monsterZone?8:0) | sequence
     * @param max      per-option limit (counter stock); 0 when unused
     * @param controller which player's zone the card sits in, -1 if unknown
     * @param location LOCATION_* of the card, 0 if unknown
     * @param sequence index within that location, -1 if unknown
     * @param command  {@link CardCommands} COMMAND_* bit this option is, or 0
     *                 when it isn't a per-card command
     */
    public record Option(String label, String detail, int cardCode, int zone, int max,
        int controller, int location, int sequence, int command)
    {
        public Option(String label)
        {
            this(label, "", 0, -1, 0, -1, 0, -1, 0);
        }

        public Option(String label, String detail, int cardCode)
        {
            this(label, detail, cardCode, -1, 0, -1, 0, -1, 0);
        }

        public Option(String label, String detail, int cardCode, int controller, int location, int sequence)
        {
            this(label, detail, cardCode, -1, 0, controller, location, sequence, 0);
        }

        public Option(String label, String detail, int cardCode, int zone, int max,
            int controller, int location, int sequence)
        {
            this(label, detail, cardCode, zone, max, controller, location, sequence, 0);
        }

        /** True when this option acts on the given board slot. */
        public boolean isAt(int controller, int location, int sequence)
        {
            return this.controller == controller && this.location == location && this.sequence == sequence;
        }

        public boolean hasSlot()
        {
            return controller >= 0 && sequence >= 0;
        }

        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeUtf(label, 256);
            buffer.writeUtf(detail, 256);
            buffer.writeVarInt(cardCode);
            buffer.writeVarInt(zone);
            buffer.writeVarInt(max);
            buffer.writeVarInt(controller + 1);
            buffer.writeVarInt(location);
            buffer.writeVarInt(sequence + 1);
            buffer.writeVarInt(command);
        }

        public static Option read(FriendlyByteBuf buffer)
        {
            return new Option(buffer.readUtf(256), buffer.readUtf(256), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt() - 1, buffer.readVarInt(), buffer.readVarInt() - 1,
                buffer.readVarInt());
        }
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeEnum(kind);
        buffer.writeUtf(title, 256);
        buffer.writeVarInt(options.size());
        options.forEach(option -> option.write(buffer));
        buffer.writeVarInt(minSelect);
        buffer.writeVarInt(maxSelect);
        buffer.writeBoolean(cancelable);
        field.write(buffer);
    }

    public static EnginePrompt read(FriendlyByteBuf buffer)
    {
        Kind kind = buffer.readEnum(Kind.class);
        String title = buffer.readUtf(256);
        int optionCount = buffer.readVarInt();
        List<Option> options = new ArrayList<>(optionCount);
        for(int i = 0; i < optionCount; i++)
        {
            options.add(Option.read(buffer));
        }
        return new EnginePrompt(kind, title, options, buffer.readVarInt(), buffer.readVarInt(),
            buffer.readBoolean(), BoardSnapshot.read(buffer));
    }

    /** True when exactly one option is expected — the common case, one click. */
    public boolean isSingleChoice()
    {
        return kind == Kind.CHOOSE || (kind == Kind.MULTI && maxSelect <= 1);
    }

    /** Packs the zone-highlight metadata for {@link Option#zone()}. */
    public static int zoneRef(boolean opponent, boolean monsterZone, int sequence)
    {
        return (opponent ? 16 : 0) | (monsterZone ? 8 : 0) | sequence;
    }
}
