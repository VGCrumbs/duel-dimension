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
    boolean cancelable, BoardSnapshot field, boolean chainWindow)
{
    /**
     * Every prompt except a chain window, which is the overwhelming majority
     * of them. Kept so the eighteen places that build a prompt do not each have
     * to say "not a chain window".
     */
    public EnginePrompt(Kind kind, String title, List<Option> options, int minSelect, int maxSelect,
        boolean cancelable, BoardSnapshot field)
    {
        this(kind, title, options, minSelect, maxSelect, cancelable, field, false);
    }

    /** The same prompt, marked as a chain window. */
    public EnginePrompt asChainWindow()
    {
        return new EnginePrompt(kind, title, options, minSelect, maxSelect, cancelable, field, true);
    }

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
        DECLARE_CARD,
        /**
         * Pick the position one card is placed in.
         * <p>
         * Every option is the same card in a different posture, so the option's
         * {@code zone} carries the POS_* bit rather than a board highlight —
         * nothing highlights the board for this prompt, and the drawing needs
         * to know which way up to show the card.
         */
        POSITION
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
     * @param art      which artwork the copy being offered wears, 0 for the
     *                 printed one. Decided server-side, at the moment the
     *                 option is built, by asking the engine about the very
     *                 (controller, location, sequence) it just named — because
     *                 the client cannot work it out for a card that is in no
     *                 board snapshot, which is every card in a deck. 0 also
     *                 means "this viewer may not identify this card"; see
     *                 {@code BoardObserver.coverOf}.
     */
    public record Option(String label, String detail, int cardCode, int zone, int max,
        int controller, int location, int sequence, int command, int art)
    {
        /**
         * The undressed option, which is most of them: a zone, a phase button,
         * a yes/no. Also the shape a bot or a test builds, neither of which
         * draws anything.
         */
        public Option(String label, String detail, int cardCode, int zone, int max,
            int controller, int location, int sequence, int command)
        {
            this(label, detail, cardCode, zone, max, controller, location, sequence, command, 0);
        }

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

        /** A card the engine named, dressed in the artwork that copy wears. */
        public Option(String label, String detail, int cardCode,
            int controller, int location, int sequence, int art)
        {
            this(label, detail, cardCode, -1, 0, controller, location, sequence, 0, art);
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
            // Only a card carries an artwork, and an option that is not one --
            // a zone, a phase button, a declared number -- has no identity to
            // attach one to. Following BoardSnapshot.Slot: the byte is not on
            // the wire to be filled in.
            if(cardCode != 0)
            {
                buffer.writeVarInt(art);
            }
        }

        public static Option read(FriendlyByteBuf buffer)
        {
            String label = buffer.readUtf(256);
            String detail = buffer.readUtf(256);
            int cardCode = buffer.readVarInt();
            int zone = buffer.readVarInt();
            int max = buffer.readVarInt();
            int controller = buffer.readVarInt() - 1;
            int location = buffer.readVarInt();
            int sequence = buffer.readVarInt() - 1;
            int command = buffer.readVarInt();
            int art = cardCode != 0 ? buffer.readVarInt() : 0;
            return new Option(label, detail, cardCode, zone, max, controller, location, sequence,
                command, art);
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
        buffer.writeBoolean(chainWindow);
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
        int min = buffer.readVarInt();
        int max = buffer.readVarInt();
        boolean cancelable = buffer.readBoolean();
        boolean chainWindow = buffer.readBoolean();
        return new EnginePrompt(kind, title, options, min, max, cancelable,
            BoardSnapshot.read(buffer), chainWindow);
    }

    /** True when exactly one option is expected — the common case, one click. */
    /** The same prompt under a different heading. */
    public EnginePrompt withTitle(String replacement)
    {
        return new EnginePrompt(kind, replacement, options, minSelect, maxSelect, cancelable,
            field, chainWindow);
    }

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
