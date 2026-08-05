package de.cas_ual_ty.dueldimension.ocg.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the engine's numeric {@code description} values into text a player can
 * read. Every prompt carries one — "activate what?", "select what?", "yes or
 * no to what?" — and without this a duel GUI can only show numbers.
 * <p>
 * The encoding comes from the card scripts themselves
 * ({@code aux.Stringid(code, id) = (id & 0xFFFFF) | code << 20} in
 * utility.lua): anything below 2^20 is a system string, anything above packs a
 * card passcode in the high bits and the index of one of that card's own
 * strings in the low bits.
 * <p>
 * Two sources feed it: EDOPro's {@code strings.conf} (system strings, counter
 * and archetype names) and the card database's {@code texts} table (per-card
 * strings {@code str1..str16}). Both are data files we already depend on.
 */
public class DescriptionTable
{
    /** Card strings are indexed in the low 20 bits; the passcode occupies the rest. */
    private static final int CARD_STRING_SHIFT = 20;
    private static final long CARD_STRING_MASK = 0xFFFFFL;
    private static final int MAX_CARD_STRINGS = 16;

    private final Map<Integer, String> system = new HashMap<>();
    private final Map<Integer, String> victory = new HashMap<>();
    private final Map<Long, String> counters = new HashMap<>();
    private final Map<Long, String> archetypes = new HashMap<>();
    private final Map<Integer, String[]> cardStrings = new HashMap<>();
    private final Map<Integer, String> cardNames = new HashMap<>();

    /** An empty table: every lookup falls back to a debug label. */
    public DescriptionTable()
    {
    }

    /**
     * @param stringsConf EDOPro's config/strings.conf (system strings)
     * @param cdbFiles    card databases supplying per-card strings
     */
    public DescriptionTable(Path stringsConf, List<Path> cdbFiles) throws IOException, SQLException
    {
        if(stringsConf != null && Files.isRegularFile(stringsConf))
        {
            loadStrings(stringsConf);
        }
        for(Path cdb : cdbFiles)
        {
            if(Files.isRegularFile(cdb))
            {
                loadCardStrings(cdb);
            }
        }
    }

    private void loadStrings(Path file) throws IOException
    {
        for(String raw : Files.readAllLines(file, StandardCharsets.UTF_8))
        {
            String line = raw.trim();
            if(!line.startsWith("!"))
            {
                continue; // comments and blank lines; the format ignores them too
            }
            String[] parts = line.split("\\s+", 3);
            if(parts.length < 3)
            {
                continue;
            }
            String kind = parts[0];
            String key = parts[1];
            String text = parts[2];
            try
            {
                switch(kind)
                {
                    case "!system" -> system.put(Integer.parseInt(key), text);
                    case "!victory" -> victory.put(Integer.parseInt(key, 16), text);
                    case "!counter" -> counters.put(Long.parseLong(key, 16), text);
                    case "!setname" -> archetypes.put(Long.parseLong(key, 16), text);
                    default ->
                    {
                    }
                }
            }
            catch(NumberFormatException ignored)
            {
                // A malformed entry should not take out the whole table.
            }
        }
    }

    private void loadCardStrings(Path cdb) throws SQLException
    {
        StringBuilder columns = new StringBuilder("id, name");
        for(int i = 1; i <= MAX_CARD_STRINGS; i++)
        {
            columns.append(", str").append(i);
        }
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + cdb.toAbsolutePath());
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT " + columns + " FROM texts"))
        {
            while(rows.next())
            {
                int code = rows.getInt(1);
                cardNames.put(code, rows.getString(2));
                String[] strings = new String[MAX_CARD_STRINGS];
                boolean any = false;
                for(int i = 0; i < MAX_CARD_STRINGS; i++)
                {
                    String value = rows.getString(3 + i);
                    strings[i] = value == null ? "" : value;
                    any |= !strings[i].isEmpty();
                }
                if(any)
                {
                    cardStrings.put(code, strings);
                }
            }
        }
    }

    /** True if this description refers to one of a specific card's own strings. */
    public static boolean isCardString(long description)
    {
        return (description >>> CARD_STRING_SHIFT) != 0;
    }

    public static int cardOf(long description)
    {
        return (int)(description >>> CARD_STRING_SHIFT);
    }

    public static int indexOf(long description)
    {
        return (int)(description & CARD_STRING_MASK);
    }

    /**
     * @return readable text for a prompt description, or a {@code ?}-prefixed
     *         debug label when the tables have no entry (never null, so a GUI
     *         always has something to draw)
     */
    public String describe(long description)
    {
        if(description == 0)
        {
            return "";
        }
        if(!isCardString(description))
        {
            String text = system.get((int)description);
            return text != null ? text : "?system:" + description;
        }

        // Above 2^20 the value is either aux.Stringid(code, index) or a bare
        // passcode (scripts pass one directly to mean "…for this card"). Only
        // one of the two readings names a real card: a Stringid's high bits
        // are a passcode, and no passcode is small enough to be one itself.
        int code = cardOf(description);
        int index = indexOf(description);
        String[] strings = cardStrings.get(code);
        if(strings != null && index < strings.length && !strings[index].isEmpty())
        {
            return strings[index];
        }

        String bareCard = cardNames.get((int)description);
        if(bareCard != null)
        {
            return bareCard;
        }

        // Fall back to naming the card: "Dark Magician (effect 2)" still tells
        // the player which card is asking.
        String name = cardNames.get(code);
        return name != null ? name + " (effect " + (index + 1) + ")" : "?card:" + code + ":" + index;
    }

    /**
     * Renders a MSG_HINT payload, whose meaning depends on the hint type: some
     * carry a string description, others a card passcode, a race/attribute
     * bitmask, a zone mask or a plain number. Feeding all of them through
     * {@link #describe} would print passcodes as nonsense card strings.
     */
    public String describeHint(int hintType, long value)
    {
        return switch(hintType)
        {
            case OcgHints.EVENT, OcgHints.MESSAGE, OcgHints.SELECT_MESSAGE,
                OcgHints.OP_SELECTED, OcgHints.EFFECT -> describe(value);
            case OcgHints.CODE, OcgHints.CARD -> cardName((int)value);
            case OcgHints.NUMBER -> Long.toString(value);
            case OcgHints.RACE -> maskNames(value, archetypes.isEmpty() ? counters : counters, "race");
            case OcgHints.ATTRIBUTE -> maskNames(value, counters, "attribute");
            case OcgHints.ZONE -> "zones 0x" + Long.toHexString(value);
            default -> "?hint" + hintType + ":" + value;
        };
    }

    private String maskNames(long mask, Map<Long, String> ignored, String kind)
    {
        // Race/attribute names live in the client's own tables, not in
        // strings.conf; until those are wired up, show the mask honestly
        // rather than inventing a label.
        return kind + " mask 0x" + Long.toHexString(mask);
    }

    /** MSG_HINT type ids (see OcgConstants.HINT_*), named for readability here. */
    public static final class OcgHints
    {
        public static final int EVENT = 1;
        public static final int MESSAGE = 2;
        public static final int SELECT_MESSAGE = 3;
        public static final int OP_SELECTED = 4;
        public static final int EFFECT = 5;
        public static final int RACE = 6;
        public static final int ATTRIBUTE = 7;
        public static final int CODE = 8;
        public static final int NUMBER = 9;
        public static final int CARD = 10;
        public static final int ZONE = 11;

        private OcgHints()
        {
        }
    }

    public String systemString(int id)
    {
        return system.getOrDefault(id, "?system:" + id);
    }

    public String victoryReason(int reason)
    {
        return victory.getOrDefault(reason, "?victory:" + reason);
    }

    public String counterName(long counterType)
    {
        return counters.getOrDefault(counterType, "?counter:" + counterType);
    }

    public String cardName(int code)
    {
        return cardNames.getOrDefault(code, "?card:" + code);
    }

    public int systemStringCount()
    {
        return system.size();
    }

    public int cardStringCount()
    {
        return cardStrings.size();
    }
}
