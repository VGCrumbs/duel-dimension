package de.cas_ual_ty.dueldimension.ocg.prompt;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * A decision, described in terms a screen can draw and a player can click.
 * <p>
 * All engine semantics are resolved server-side: by the time a prompt reaches
 * the client it is a title, a list of labelled options, and how many of them
 * must be picked. The client never sees passcodes, response encodings or
 * hidden information, and answers with nothing but option indices — so a
 * modified client cannot forge an illegal play, only pick from what it was
 * legitimately offered.
 *
 * @param title       what is being asked
 * @param options     the choices, in the order the engine listed them
 * @param minSelect   how many must be chosen
 * @param maxSelect   how many may be chosen
 * @param cancelable  whether the player may decline entirely
 * @param board       a few lines summarising the field, for context
 * @param field       the same field, structured, for the playfield renderer
 */
public record EnginePrompt(String title, List<Option> options, int minSelect, int maxSelect,
    boolean cancelable, List<String> board, BoardSnapshot field)
{
    /**
     * @param label   what the player reads
     * @param detail  optional second line (zone, stats, effect text)
     * @param cardCode passcode for art lookup, 0 when the option isn't a card
     */
    public record Option(String label, String detail, int cardCode)
    {
        public Option(String label)
        {
            this(label, "", 0);
        }

        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeUtf(label, 256);
            buffer.writeUtf(detail, 256);
            buffer.writeVarInt(cardCode);
        }

        public static Option read(FriendlyByteBuf buffer)
        {
            return new Option(buffer.readUtf(256), buffer.readUtf(256), buffer.readVarInt());
        }
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeUtf(title, 256);
        buffer.writeVarInt(options.size());
        options.forEach(option -> option.write(buffer));
        buffer.writeVarInt(minSelect);
        buffer.writeVarInt(maxSelect);
        buffer.writeBoolean(cancelable);
        buffer.writeVarInt(board.size());
        board.forEach(line -> buffer.writeUtf(line, 256));
        field.write(buffer);
    }

    public static EnginePrompt read(FriendlyByteBuf buffer)
    {
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
        int boardLines = buffer.readVarInt();
        List<String> board = new ArrayList<>(boardLines);
        for(int i = 0; i < boardLines; i++)
        {
            board.add(buffer.readUtf(256));
        }
        return new EnginePrompt(title, options, min, max, cancelable, board, BoardSnapshot.read(buffer));
    }

    /** True when exactly one option is expected — the common case, one click. */
    public boolean isSingleChoice()
    {
        return maxSelect <= 1;
    }
}
