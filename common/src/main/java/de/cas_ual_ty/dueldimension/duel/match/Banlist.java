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

    /**
     * The id meaning "whatever this server currently considers standard", which
     * is the most recent TCG list.
     *
     * <h2>Why a sentinel and not the real id</h2>
     * Because a default has to be answerable in two places that know different
     * things. A deck is decoded on the CLIENT as well as the server, and a
     * client has no EDOPro install and therefore no idea which lists exist —
     * so the constant a {@code DeckList} falls back to cannot be the result of
     * looking at any. It has to be a name for the question rather than an
     * answer to it.
     * <p>
     * It also keeps two states apart that would otherwise collapse into one:
     * <b>never chose</b> and <b>chose no list</b>. Those are different answers.
     * A deck carrying this id follows the server's current TCG list and keeps
     * following it when the lists are updated; a deck carrying
     * {@link #NO_BANLIST_ID} has been deliberately set to no list and stays
     * there. Picking anything in the editor stores that list's own id, which
     * pins the deck to a format rather than to whatever is current.
     *
     * @see #mostRecentTcg the rule both sides resolve it with
     */
    public static final String DEFAULT_ID = "default";

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

    /**
     * The newest TCG list out of a set, or null if there is not one.
     *
     * <h2>The rule, and why it is here rather than on the server</h2>
     * {@link #DEFAULT_ID} has to resolve to the SAME list on both sides — the
     * server enforces it and the editor draws badges from it, and a default that
     * meant different things in those two places would be worse than no default.
     * The server resolves it against the lists it read off disk and the client
     * against the catalogue it was sent, which hold the same lists; putting the
     * rule in {@code common} is what makes those two resolutions one rule rather
     * than two that agree today.
     *
     * <h2>How "newest TCG" is decided</h2>
     * By the DATE at the front of the display name, not by position in the list.
     * EDOPro's headers are {@code !2026.05 TCG}, {@code !2005.4 GOAT},
     * {@code !2026.07 OCG} — a date, then the format — and the files they come
     * out of are read in filename order, which has nothing to do with recency.
     * <p>
     * A name containing "TCG" is a candidate. That is a narrower test than it
     * looks: "OCG" does not contain it, and neither do "Traditional", "Worlds",
     * "Speed Duel" or "Rush Duel". It does admit a name like "2015.11 TCG Goat
     * Format", which is a real TCG list and simply not the most recent one —
     * which is exactly what comparing the dates settles.
     * <p>
     * Dates are compared component by component as numbers, so 2026.05 beats
     * 2005.4 without either being padded, and a three-part date like
     * {@code 2026.07.01} compares against a two-part one on the parts they
     * share. A candidate with no leading date sorts below every dated one rather
     * than throwing.
     */
    public static Banlist mostRecentTcg(List<Banlist> lists)
    {
        Banlist best = null;
        int[] bestDate = null;
        for(Banlist list : lists)
        {
            if(!list.displayName().toLowerCase(java.util.Locale.ROOT).contains("tcg"))
            {
                continue;
            }
            int[] date = leadingDate(list.displayName());
            if(best == null || compareDates(date, bestDate) > 0)
            {
                best = list;
                bestDate = date;
            }
        }
        return best;
    }

    /**
     * The dotted number a display name opens with, as its parts.
     * <p>
     * Empty when there is none, which {@link #compareDates} treats as older than
     * anything — a list whose name does not start with a date cannot be shown to
     * be the most recent one.
     */
    private static int[] leadingDate(String displayName)
    {
        List<Integer> parts = new ArrayList<>(3);
        int i = 0;
        while(i < displayName.length())
        {
            int start = i;
            while(i < displayName.length() && Character.isDigit(displayName.charAt(i)))
            {
                i++;
            }
            if(i == start)
            {
                break;
            }
            parts.add(Integer.parseInt(displayName.substring(start, i)));
            if(i < displayName.length() && displayName.charAt(i) == '.')
            {
                i++;
            }
            else
            {
                break;
            }
        }
        int[] date = new int[parts.size()];
        for(int part = 0; part < parts.size(); part++)
        {
            date[part] = parts.get(part);
        }
        return date;
    }

    /** Component by component; a missing component counts as 0. */
    private static int compareDates(int[] left, int[] right)
    {
        int length = Math.max(left.length, right.length);
        for(int i = 0; i < length; i++)
        {
            int compared = Integer.compare(i < left.length ? left[i] : 0,
                i < right.length ? right[i] : 0);
            if(compared != 0)
            {
                return compared;
            }
        }
        return 0;
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
