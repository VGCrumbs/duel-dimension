package de.cas_ual_ty.dueldimension.duel.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every card a player owns, how many of each, at which rarity and in which
 * artwork.
 * <p>
 * The trunk is a <em>collection</em>, not a container: taking a card out to
 * build a deck does not remove it, so the same three copies of a card can sit
 * in every deck the player owns simultaneously. That is the whole point of the
 * shared pool, and it is why decks store passcodes while this stores counts.
 * <p>
 * Cards arrive here by being registered — opening a pack, unlocking a structure
 * deck, or picking up a physical card. Nothing ever needs to be spent to build
 * with it afterwards.
 *
 * <h2>Rarity, and why the save format grew rather than changed</h2>
 * A card is printed at several rarities, and the collection now remembers which
 * one it holds, so a set can be completed printing by printing rather than card
 * by card. The rarity was always known at the moment of acquisition — a pack
 * pull produces {@code CardHolder}s that carry it — and was simply thrown away
 * on the way in.
 * <p>
 * The obvious way to store it would have been to re-key the saved map. That is
 * deliberately not what happens. The old field is still written, still keyed by
 * bare passcode, still holding the total count, so a build WITHOUT this change
 * reads a collection written BY it and sees every card — just without knowing
 * the rarities. The detail rides alongside in a second, optional field that
 * older code never looks at.
 * <p>
 * That is what makes this feature safe to abandon. Re-keying would have meant
 * {@code Integer.parseInt("12345|Super Rare")} throwing in the old loader,
 * which drops the entry — so reverting the code would have silently deleted
 * cards from every collection that had been played with in the meantime.
 * Growing the format costs a few hundred bytes and takes that off the table.
 *
 * <h2>Artwork, and why it is not hung off the rarity</h2>
 * A printing also names an artwork — {@code image_index} in the set files — and
 * that too was known at the moment of acquisition and thrown away. It is
 * recorded here beside the rarity, as part of the same key, so a copy pulled
 * from MVP1 is remembered as the copy that wears artwork 2.
 * <p>
 * <b>It is part of the key rather than a fact about the rarity, because the
 * rarity does not determine it.</b> Measured over the shipped set files, 94
 * (passcode, rarity) pairs carry conflicting artworks, and Blue-Eyes White
 * Dragon at Ultra Rare spans six different ones. A map from rarity to artwork
 * would have to pick one of those and lie about the other five, and a player
 * holding two differently-arted Ultra Rares could only have recorded one of
 * them.
 * <p>
 * The third saved field follows the same additive rule as the second, and for
 * the same reason: {@code Cards} and {@code CardPrintings} are written exactly
 * as they were before, so a build without this change reads an unchanged
 * collection with unchanged rarities. Re-keying {@code CardPrintings} to
 * {@code passcode|rarity|art} instead would not have lost a card, but the old
 * loader's {@code key.substring(split + 1)} would have read the rarity as
 * "Ultra Rare|2" — set completion would silently stop matching and the invented
 * rarity would be written straight back to disk. Only rows whose artwork is not
 * the printed one are written at all, so a collection with no alternate art
 * costs zero extra bytes; that is the overwhelming majority of them, since
 * around a hundred and twenty cards in the whole database have a second artwork.
 */
public final class Trunk
{
    /**
     * The rarity recorded for a card that arrived before rarities were kept, or
     * from a source that has none.
     * <p>
     * Empty rather than "Unknown" so it cannot collide with a real rarity name
     * out of the database, and so it reads as absent rather than as something a
     * player might think they can collect.
     */
    public static final String UNKNOWN_RARITY = "";

    /** Separates passcode, rarity and artwork in the saved key. No rarity name has one. */
    private static final char KEY_SEPARATOR = '|';

    /**
     * One printing as the collection tells them apart: the rarity it was pulled
     * at, and the artwork that printing prints.
     * <p>
     * Both, because neither alone identifies a printing. Two Ultra Rares of the
     * same card can wear different artwork, and two printings of the same
     * artwork can be different rarities — so a player who owns both is holding
     * two distinct things and the collection has to be able to say so.
     */
    private record Printing(String rarity, int art)
    {
    }

    /**
     * The collection: passcode, to printing, to count.
     * <p>
     * This is the authority. The flat passcode-to-total map that is also written
     * is derived from it at save time rather than held beside it, because two
     * structures holding the same fact drift apart.
     */
    private final Map<Integer, Map<Printing, Integer>> owned = new LinkedHashMap<>();

    /** How many copies of this card the player owns, at any rarity. */
    public int countOf(int passcode)
    {
        int total = 0;
        for(int count : owned.getOrDefault(passcode, Map.of()).values())
        {
            total += count;
        }
        return total;
    }

    /** How many copies of this card at this exact rarity, in any artwork. */
    public int countOf(int passcode, String rarity)
    {
        String wanted = rarity == null ? UNKNOWN_RARITY : rarity;
        int total = 0;
        for(Map.Entry<Printing, Integer> entry : owned.getOrDefault(passcode, Map.of()).entrySet())
        {
            if(entry.getKey().rarity().equals(wanted))
            {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** How many copies of this exact printing: this rarity in this artwork. */
    public int countOf(int passcode, String rarity, int art)
    {
        return owned.getOrDefault(passcode, Map.of())
            .getOrDefault(new Printing(rarity == null ? UNKNOWN_RARITY : rarity,
                Math.max(0, art)), 0);
    }

    public boolean has(int passcode)
    {
        return countOf(passcode) > 0;
    }

    /** Whether this exact printing is held, which is what set completion asks. */
    public boolean has(int passcode, String rarity)
    {
        return countOf(passcode, rarity) > 0;
    }

    /**
     * Every rarity of this card the player holds, and how many of each.
     * <p>
     * Collapsed over the artwork on purpose. Set completion is measured in
     * rarities — a set lists one Obelisk at Ultra Rare, not one per artwork —
     * so this answers the question it has always answered even though the
     * collection now records something finer underneath.
     */
    public Map<String, Integer> printingsOf(int passcode)
    {
        Map<String, Integer> byRarity = new LinkedHashMap<>();
        owned.getOrDefault(passcode, Map.of()).forEach((printing, count) ->
            byRarity.merge(printing.rarity(), count, Integer::sum));
        return Collections.unmodifiableMap(byRarity);
    }

    /** One printing the player actually holds, and how many of it. */
    public record Held(int passcode, String rarity, int art, int count)
    {
    }

    /**
     * Every distinct printing of this card the player holds.
     * <p>
     * The finest view of the collection there is, and the only one a
     * <b>trade</b> can use: {@link #printingsOf} collapses over the artwork and
     * {@link #artsOwned} collapses over the rarity, so neither can name a
     * single printing to hand over. A trade has to say exactly which copy is
     * being offered, because that is the copy the other side is being shown.
     */
    public List<Held> heldPrintings(int passcode)
    {
        Map<Printing, Integer> printings = owned.get(passcode);
        if(printings == null)
        {
            return List.of();
        }
        List<Held> held = new ArrayList<>(printings.size());
        printings.forEach((printing, count) ->
            held.add(new Held(passcode, printing.rarity(), printing.art(), count)));
        return List.copyOf(held);
    }

    /**
     * The artwork of every copy of this card the player owns, one entry per
     * copy, fanciest first.
     * <p>
     * Descending because that is the only total order over artworks this
     * codebase can honestly claim. {@code Properties.addArtwork} appends and
     * never inserts, so a higher index is an artwork added later — an alternate
     * rather than the printed one. The rarity names carry no rank anywhere in
     * the database, so "rarest first" is not an order that exists to sort by.
     * <p>
     * One entry per <em>copy</em> rather than per printing, so a caller dealing
     * artwork out to deck positions can simply take the next one and stop when
     * it runs out.
     */
    public List<Integer> artsOwned(int passcode)
    {
        Map<Printing, Integer> printings = owned.get(passcode);
        if(printings == null)
        {
            return List.of();
        }
        List<Integer> arts = new ArrayList<>();
        printings.forEach((printing, count) ->
        {
            for(int copy = 0; copy < count; copy++)
            {
                arts.add(printing.art());
            }
        });
        arts.sort(Comparator.reverseOrder());
        return Collections.unmodifiableList(arts);
    }

    /**
     * The artwork the n-th copy of this card wears; 0 once the player's own
     * copies run out.
     * <p>
     * Deals the fanciest copies first: owning one BP01 Obelisk and one MVP1
     * Obelisk and putting two in a deck shows artwork 2 and artwork 1, which is
     * the pair of cards actually owned. An ordinal past the end answers 0,
     * which is also what an unowned card answers — a copy nobody can point at
     * in the collection wears the printed art.
     * <p>
     * Checks for an alternate before building anything. Almost every collection
     * is entirely printed art, and this is asked once per click in the editor.
     */
    public int artForCopy(int passcode, int ordinal)
    {
        if(ordinal < 0)
        {
            return 0;
        }
        Map<Printing, Integer> printings = owned.get(passcode);
        if(printings == null)
        {
            return 0;
        }
        boolean dressed = false;
        for(Printing printing : printings.keySet())
        {
            if(printing.art() > 0)
            {
                dressed = true;
                break;
            }
        }
        if(!dressed)
        {
            return 0;
        }
        List<Integer> arts = artsOwned(passcode);
        return ordinal < arts.size() ? arts.get(ordinal) : 0;
    }

    /**
     * Adds copies whose rarity is not known.
     * <p>
     * Kept because plenty of sources genuinely have none to give — a structure
     * deck unlock, a command, a test. They land under {@link #UNKNOWN_RARITY}
     * rather than being guessed at.
     *
     * @return how many were actually added, which is all of them: a collection
     *         has no capacity
     */
    public int add(int passcode, int copies)
    {
        return add(passcode, UNKNOWN_RARITY, copies);
    }

    /** Adds copies of one printing, on the artwork that printing has always had. */
    public int add(int passcode, String rarity, int copies)
    {
        return add(passcode, rarity, 0, copies);
    }

    /**
     * Adds copies of one printing, in the artwork that printing specifies.
     * <p>
     * The artwork is an index into the card's own {@code images}, and is stored
     * as given rather than validated: the database is what decides which
     * indices exist, it is not loaded on every side that holds a collection,
     * and {@code Properties.adjustImageIndex} already folds an index a card
     * does not have back to the printed art at the moment of drawing. A stale
     * value therefore degrades to art 0 rather than to nothing.
     */
    public int add(int passcode, String rarity, int art, int copies)
    {
        if(copies <= 0)
        {
            return 0;
        }
        owned.computeIfAbsent(passcode, code -> new LinkedHashMap<>())
            .merge(new Printing(rarity == null ? UNKNOWN_RARITY : rarity, Math.max(0, art)),
                copies, Integer::sum);
        return copies;
    }

    public void addAll(Iterable<Integer> passcodes)
    {
        for(int code : passcodes)
        {
            add(code, 1);
        }
    }

    /**
     * Removes copies, never below zero.
     * <p>
     * Vaguest record first, in two steps. The unknown-rarity pile goes before
     * any named rarity: a player losing a card should lose the least specific
     * record of it before one that says which printing it was. Within one
     * rarity the lowest artwork goes first, for the same reason one step down —
     * a copy on the printed art is the one that says least about itself, and
     * losing the MVP1 Obelisk while keeping an ordinary one would be the
     * collection quietly choosing the more interesting card to destroy.
     *
     * @return how many were actually removed
     */
    public int remove(int passcode, int copies)
    {
        Map<Printing, Integer> printings = owned.get(passcode);
        if(printings == null)
        {
            return 0;
        }
        int wanted = Math.max(0, copies);
        int taken = 0;

        // Rarities keep the order they were first recorded in, so the tie
        // between two equally specific printings breaks the way it always did.
        List<String> rarityOrder = new ArrayList<>();
        for(Printing printing : printings.keySet())
        {
            if(!rarityOrder.contains(printing.rarity()))
            {
                rarityOrder.add(printing.rarity());
            }
        }
        List<Printing> order = new ArrayList<>(printings.keySet());
        order.sort(Comparator
            .comparingInt((Printing printing) -> UNKNOWN_RARITY.equals(printing.rarity()) ? 0 : 1)
            .thenComparingInt(printing -> rarityOrder.indexOf(printing.rarity()))
            .thenComparingInt(Printing::art));

        for(Printing printing : order)
        {
            if(taken >= wanted)
            {
                break;
            }
            int have = printings.getOrDefault(printing, 0);
            int take = Math.min(have, wanted - taken);
            if(take <= 0)
            {
                continue;
            }
            if(have - take <= 0)
            {
                printings.remove(printing);
            }
            else
            {
                printings.put(printing, have - take);
            }
            taken += take;
        }
        if(printings.isEmpty())
        {
            owned.remove(passcode);
        }
        return taken;
    }

    /**
     * Removes copies of ONE exact printing, and no other.
     * <p>
     * {@link #remove(int, int)} deliberately takes the vaguest record first,
     * because a player losing a card should lose the least specific record of
     * it. That is right for a cost and wrong for a <b>trade</b>: a player who
     * offered their Ultra Rare must hand over that Ultra Rare, not whichever
     * copy the collection would rather part with, and the other side must
     * receive what it was shown. This is the only caller that knows exactly
     * which printing is meant, so it is the only one that asks by name.
     *
     * @return how many were actually removed, which is zero if the player does
     *         not hold this printing -- the caller is expected to treat that as
     *         a refusal rather than a partial trade
     */
    public int remove(int passcode, String rarity, int art, int copies)
    {
        Map<Printing, Integer> printings = owned.get(passcode);
        if(printings == null || copies <= 0)
        {
            return 0;
        }
        Printing exact = new Printing(rarity == null ? UNKNOWN_RARITY : rarity,
            Math.max(0, art));
        int have = printings.getOrDefault(exact, 0);
        int taken = Math.min(copies, have);
        if(taken <= 0)
        {
            return 0;
        }
        if(have - taken <= 0)
        {
            printings.remove(exact);
        }
        else
        {
            printings.put(exact, have - taken);
        }
        if(printings.isEmpty())
        {
            owned.remove(passcode);
        }
        return taken;
    }

    /**
     * The whole collection as passcode to total count, for the editor's
     * right-hand panel and everything else that does not care about rarity.
     * <p>
     * <b>Lossy, and not a way to copy a trunk.</b> Feeding this back into
     * {@link #add(int, int)} loses every rarity and every artwork the
     * collection knows — see {@link #copyInto}, which exists because two places
     * were doing exactly that.
     */
    public Map<Integer, Integer> all()
    {
        Map<Integer, Integer> flat = new LinkedHashMap<>();
        owned.forEach((code, printings) ->
        {
            int total = 0;
            for(int count : printings.values())
            {
                total += count;
            }
            if(total > 0)
            {
                flat.put(code, total);
            }
        });
        return Collections.unmodifiableMap(flat);
    }

    /**
     * Copies this whole collection into another, printing for printing.
     * <p>
     * The only correct way to duplicate a trunk. {@code DuelProfile.snapshot()}
     * and the profile Codec's own constructor both used to walk {@link #all()}
     * instead, which collapsed every printing a player owned into a single
     * unknown-rarity pile — and since the snapshot is what persists and the
     * Codec runs on every load and every sync to the client, the detail was
     * being destroyed on the way to disk and on the way back. The counts were
     * right, so nothing ever looked broken.
     */
    public void copyInto(Trunk destination)
    {
        if(destination == null || destination == this)
        {
            return;
        }
        owned.forEach((code, printings) -> printings.forEach((printing, count) ->
            destination.add(code, printing.rarity(), printing.art(), count)));
    }

    /** Distinct CARDS, however many printings of each are held. */
    public int distinctCards()
    {
        return owned.size();
    }

    /**
     * Distinct PRINTINGS, which is what a set's completion is measured in.
     * <p>
     * Counted in rarities rather than in (rarity, artwork) pairs, because that
     * is what a set lists: one Obelisk at Ultra Rare, not one per artwork.
     */
    public int distinctPrintings()
    {
        int total = 0;
        for(int passcode : owned.keySet())
        {
            total += printingsOf(passcode).size();
        }
        return total;
    }

    public int totalCards()
    {
        int total = 0;
        for(Map<Printing, Integer> printings : owned.values())
        {
            for(int count : printings.values())
            {
                total += count;
            }
        }
        return total;
    }

    /**
     * The collection on disk, in three fields.
     * <p>
     * {@code Cards} is the original: the passcode written as a string, mapped to
     * the TOTAL count across every printing. It is what the Forge build wrote and
     * what a build without this change still reads, so a collection stays whole
     * whichever way the code moves.
     * <p>
     * {@code CardPrintings} is the rarity detail, keyed {@code passcode|rarity},
     * and is optional. A save from before rarities has no such field, and every
     * card lands under {@link #UNKNOWN_RARITY} — which is accurate, because
     * nothing ever recorded what those cards were. It is written collapsed over
     * the artwork, exactly as it was before artwork existed, so a build that
     * knows about rarities and not about artwork reads it unchanged.
     * <p>
     * {@code CardArtPrintings} is the artwork detail, keyed
     * {@code passcode|rarity|art}, optional in the same way, and written only
     * for copies that are NOT on the printed art. A collection with no alternate
     * artwork in it writes nothing here at all.
     * <p>
     * On load the most specific field wins and the vaguer ones fill in only what
     * they did not already account for, which is what keeps a half-migrated
     * profile from counting its cards twice or three times. A key that is not a
     * number is dropped rather than failing the profile — the same forgiveness
     * the hand-written loader had, for the same reason: one stray key is not
     * worth a player's whole collection.
     */
    public static final Codec<Trunk> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("Cards")
            .forGetter(Trunk::writtenTotals),
        Codec.unboundedMap(Codec.STRING, Codec.INT)
            .optionalFieldOf("CardPrintings", Map.of())
            .forGetter(Trunk::writtenPrintings),
        Codec.unboundedMap(Codec.STRING, Codec.INT)
            .optionalFieldOf("CardArtPrintings", Map.of())
            .forGetter(Trunk::writtenArtPrintings))
        .apply(instance, Trunk::of));

    private static Trunk of(Map<String, Integer> totals, Map<String, Integer> printings,
        Map<String, Integer> artPrintings)
    {
        Trunk trunk = new Trunk();

        // Most specific first: a row here names a rarity AND an artwork.
        artPrintings.forEach((key, count) ->
        {
            int first = key.indexOf(KEY_SEPARATOR);
            int last = key.lastIndexOf(KEY_SEPARATOR);
            if(first < 0 || last == first)
            {
                // Two separators or it is not one of ours; a rarity with no
                // artwork belongs in CardPrintings and is read from there.
                return;
            }
            try
            {
                trunk.add(Integer.parseInt(key.substring(0, first)),
                    key.substring(first + 1, last),
                    Integer.parseInt(key.substring(last + 1)), count);
            }
            catch(NumberFormatException notAPasscode)
            {
                // Not ours; skip it rather than refusing the whole collection.
            }
        });

        // Then the rarity detail, for the copies the artwork field did not
        // claim. Those are the ones on the printed art, which is why they were
        // never written there in the first place.
        printings.forEach((key, count) ->
        {
            int split = key.indexOf(KEY_SEPARATOR);
            String code = split < 0 ? key : key.substring(0, split);
            String rarity = split < 0 ? UNKNOWN_RARITY : key.substring(split + 1);
            try
            {
                int passcode = Integer.parseInt(code);
                int already = trunk.countOf(passcode, rarity);
                if(count > already)
                {
                    trunk.add(passcode, rarity, 0, count - already);
                }
            }
            catch(NumberFormatException notAPasscode)
            {
                // As above.
            }
        });

        // Only what neither of the detail fields already accounted for. Without
        // this test a profile written by THIS build would load every card
        // several times, once from each field.
        totals.forEach((key, count) ->
        {
            try
            {
                int passcode = Integer.parseInt(key);
                int already = trunk.countOf(passcode);
                if(count > already)
                {
                    trunk.add(passcode, UNKNOWN_RARITY, 0, count - already);
                }
            }
            catch(NumberFormatException notAPasscode)
            {
                // As above.
            }
        });
        return trunk;
    }

    /** The compatibility field: what a build without rarities needs to see. */
    private Map<String, Integer> writtenTotals()
    {
        Map<String, Integer> written = new LinkedHashMap<>();
        all().forEach((code, count) -> written.put(Integer.toString(code), count));
        return written;
    }

    /**
     * The rarity field, byte for byte what it was before artwork was recorded:
     * one row per (passcode, rarity), counting every artwork of it.
     */
    private Map<String, Integer> writtenPrintings()
    {
        Map<String, Integer> written = new LinkedHashMap<>();
        owned.forEach((code, printings) -> printings.forEach((printing, count) ->
            written.merge(UNKNOWN_RARITY.equals(printing.rarity())
                ? Integer.toString(code)
                : code + String.valueOf(KEY_SEPARATOR) + printing.rarity(), count, Integer::sum)));
        return written;
    }

    /**
     * The artwork field: only the copies that are not on the printed art.
     * <p>
     * Skipping art 0 is what makes this cost nothing for the collections that
     * do not use it, and it is not merely an optimisation — an art-0 row would
     * duplicate a row {@code CardPrintings} already carries, and the loader
     * would have to know to subtract it.
     */
    private Map<String, Integer> writtenArtPrintings()
    {
        Map<String, Integer> written = new LinkedHashMap<>();
        owned.forEach((code, printings) -> printings.forEach((printing, count) ->
        {
            if(printing.art() != 0)
            {
                written.put(code + String.valueOf(KEY_SEPARATOR) + printing.rarity()
                    + String.valueOf(KEY_SEPARATOR) + printing.art(), count);
            }
        }));
        return written;
    }
}
