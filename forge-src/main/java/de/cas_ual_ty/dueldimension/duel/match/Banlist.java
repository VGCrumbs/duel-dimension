package de.cas_ual_ty.dueldimension.duel.match;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A forbidden/limited list, and the rules for checking a deck against it.
 * <p>
 * Parsed from EDOPro's own {@code .lflist.conf} files rather than transcribed,
 * so the lists stay exactly what the reference plays with. The format is one
 * list per {@code !name} header, then one card per line:
 * <pre>
 * #[2026.05 TCG]
 * !2026.05 TCG
 * #Forbidden
 * 21044178 0 --Abyss Dweller
 * </pre>
 * The number after the passcode is how many copies are allowed: 0 forbidden,
 * 1 limited, 2 semi-limited. <b>A card that is not listed at all is unlimited</b>,
 * which is why {@link #limitFor} defaults to {@link #UNLIMITED} rather than
 * treating absence as a ban.
 */
public final class Banlist
{
    /** The most copies of one card a deck may ever hold. */
    public static final int UNLIMITED = 3;

    /** The id used when no list is applied and only deck-size rules are checked. */
    public static final String NO_BANLIST_ID = "none";

    /** Deck-size rules, from the official floor rules the core also enforces. */
    public static final int MAIN_MIN = 40;
    public static final int MAIN_MAX = 60;
    public static final int EXTRA_MAX = 15;
    public static final int SIDE_MAX = 15;

    private final String id;
    private final String displayName;
    private final Map<Integer, Integer> limits;

    public Banlist(String id, String displayName, Map<Integer, Integer> limits)
    {
        this.id = id;
        this.displayName = displayName;
        this.limits = Collections.unmodifiableMap(new LinkedHashMap<>(limits));
    }

    /** The permissive list: deck-size rules only. */
    public static Banlist none()
    {
        return new Banlist(NO_BANLIST_ID, "No Banlist", Map.of());
    }

    public String id()
    {
        return id;
    }

    public String displayName()
    {
        return displayName;
    }

    public Map<Integer, Integer> limits()
    {
        return limits;
    }

    /** How many copies of this card the list allows. */
    public int limitFor(int passcode)
    {
        return limits.getOrDefault(passcode, UNLIMITED);
    }

    /**
     * Reads every list in one {@code .lflist.conf}. A file may hold several,
     * each introduced by its own {@code !} header, so this returns a list.
     */
    public static List<Banlist> parse(Reader source) throws IOException
    {
        List<Banlist> lists = new ArrayList<>();
        String currentName = null;
        Map<Integer, Integer> current = new LinkedHashMap<>();

        try(BufferedReader reader = new BufferedReader(source))
        {
            String line;
            while((line = reader.readLine()) != null)
            {
                line = line.strip();
                if(line.isEmpty())
                {
                    continue;
                }
                if(line.startsWith("!"))
                {
                    // A new header closes the list before it.
                    if(currentName != null)
                    {
                        lists.add(new Banlist(idOf(currentName), currentName, current));
                    }
                    currentName = line.substring(1).strip();
                    current = new LinkedHashMap<>();
                    continue;
                }
                if(line.startsWith("#"))
                {
                    continue; // section comment, e.g. "#Forbidden"
                }
                if(currentName == null)
                {
                    continue; // entries before any header belong to no list
                }
                // "<passcode> <limit> --<name>"; the trailing name is a comment.
                String[] parts = line.split("\\s+");
                if(parts.length < 2)
                {
                    continue;
                }
                try
                {
                    int passcode = Integer.parseInt(parts[0]);
                    int limit = Integer.parseInt(parts[1]);
                    if(limit >= 0 && limit < UNLIMITED)
                    {
                        current.put(passcode, limit);
                    }
                }
                catch(NumberFormatException malformed)
                {
                    // A line we cannot read is skipped rather than failing the
                    // whole list: these files are edited by hand upstream.
                }
            }
        }
        if(currentName != null)
        {
            lists.add(new Banlist(idOf(currentName), currentName, current));
        }
        return lists;
    }

    /** A stable id for a display name, for use over the wire and in config. */
    public static String idOf(String displayName)
    {
        StringBuilder id = new StringBuilder(displayName.length());
        for(char c : displayName.toLowerCase(java.util.Locale.ROOT).toCharArray())
        {
            id.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return id.toString();
    }

    /**
     * Why a deck is not legal, or an empty list if it is.
     * <p>
     * Reported as reasons rather than as a boolean because the lobby shows the
     * player WHY a deck is greyed out; "invalid" on its own is not actionable.
     */
    public List<String> validate(List<Integer> main, List<Integer> extra, List<Integer> side)
    {
        List<String> problems = new ArrayList<>();
        if(main.size() < MAIN_MIN || main.size() > MAIN_MAX)
        {
            problems.add("Main deck must hold " + MAIN_MIN + "-" + MAIN_MAX
                + " cards (has " + main.size() + ")");
        }
        if(extra.size() > EXTRA_MAX)
        {
            problems.add("Extra deck may hold at most " + EXTRA_MAX + " (has " + extra.size() + ")");
        }
        if(side.size() > SIDE_MAX)
        {
            problems.add("Side deck may hold at most " + SIDE_MAX + " (has " + side.size() + ")");
        }

        // Copies are counted across main, extra and side together, which is how
        // the limit is defined -- three of a card in the side deck plus one in
        // the main is four copies.
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for(List<Integer> part : List.of(main, extra, side))
        {
            for(int code : part)
            {
                counts.merge(code, 1, Integer::sum);
            }
        }
        for(Map.Entry<Integer, Integer> entry : counts.entrySet())
        {
            int allowed = limitFor(entry.getKey());
            if(entry.getValue() > allowed)
            {
                problems.add(allowed == 0
                    ? "Card " + entry.getKey() + " is forbidden"
                    : "Card " + entry.getKey() + " is limited to " + allowed
                        + " (deck has " + entry.getValue() + ")");
            }
        }
        return problems;
    }
}
