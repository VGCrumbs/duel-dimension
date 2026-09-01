package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.LevelMonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.MonsterProperties;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.card.properties.Type;
import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import de.cas_ual_ty.dueldimension.duel.profile.CardQuery;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.net.ProfilePayloads;
import de.cas_ual_ty.dueldimension.duel.profile.Trunk;

import java.util.ArrayList;
import java.util.List;

/**
 * What the deck editor is working on: a collection, a deck, and a query.
 * <p>
 * A <em>copy</em> of what the server holds. It used to be the original — a
 * collection invented on the client and seeded from the whole card database —
 * which made the editor usable but meant nothing it did was real. Now the
 * server sends the profile on join and after every change, and this holds the
 * latest one.
 * <p>
 * Changes are applied here immediately <em>and</em> asked for over the wire, so
 * the editor stays responsive while the server decides. Every reply is a whole
 * profile, so a change the server refuses simply does not survive the next
 * message: the guess is corrected rather than argued with.
 */
public final class EditorState
{
    /** Every deck the player has, and which one the editor is on. */
    private static de.cas_ual_ty.dueldimension.duel.profile.DuelProfile profile =
        new de.cas_ual_ty.dueldimension.duel.profile.DuelProfile();
    /**
     * True once the server has actually told us something. Before that the
     * profile above is an empty placeholder rather than the player's.
     */
    private static boolean synced;
    private static int current;
    /**
     * Every list the server offers. Empty until the profile syncs.
     * <p>
     * This replaced a single {@code Banlist} field that nothing ever assigned:
     * {@code setBanlist} had no callers, so the editor's copy limits were
     * checked against {@link Banlist#none()} forever and a deck could hold three
     * of anything regardless of the list it was meant for. The catalogue and the
     * per-deck id together are what make the answer a real one.
     */
    private static java.util.List<Banlist> banlists = java.util.List.of();
    private static CardQuery<Properties> query;
    private static List<Properties> visible = new ArrayList<>();
    /**
     * Passcodes the duel engine does not know, as the SERVER reported them.
     * <p>
     * Hidden from the editor entirely: a card the engine has never heard of is
     * not refused at duel start, it is silently relocated -- an unknown Xyz
     * ends up in the main deck and is drawn like a normal card. Offering one
     * for deck building is offering a deck that will not work.
     */
    private static java.util.Set<Integer> engineUnknown = java.util.Set.of();

    public static void setEngineUnknown(java.util.List<Integer> codes)
    {
        engineUnknown = codes == null || codes.isEmpty()
            ? java.util.Set.of() : java.util.Set.copyOf(codes);
        // The pool is memoised behind a dirty flag; without this the cards stay
        // visible until something else happens to invalidate it.
        invalidate();
    }

    /** Whether the engine can actually play this card. */
    public static boolean engineKnows(int code)
    {
        return !engineUnknown.contains(code);
    }
    private static boolean dirty = true;
    /** Include legal database cards absent from the player's collection. */
    private static boolean showUnowned;

    private EditorState()
    {
    }

    /**
     * Reads the mod's own card model for the query. Kept here rather than in
     * {@link CardQuery} so that class stays testable without a card database.
     */
    private static final CardQuery.Facets<Properties> FACETS = new CardQuery.Facets<>()
    {
        @Override
        public String name(Properties card)
        {
            return card.getName();
        }

        @Override
        public String text(Properties card)
        {
            // What CardQuery matches against, and so the deck editor's search
            // box. getSearchText rather than getText: the preview beside the
            // box shows a Pendulum Effect, and a box that cannot find what the
            // panel next to it is displaying reads as broken.
            return card.getSearchText();
        }

        @Override
        public CardQuery.Kind kind(Properties card)
        {
            Type type = card.getType();
            if(type == Type.SPELL)
            {
                return CardQuery.Kind.SPELL;
            }
            return type == Type.TRAP ? CardQuery.Kind.TRAP : CardQuery.Kind.MONSTER;
        }

        @Override
        public String attribute(Properties card)
        {
            return card instanceof MonsterProperties monster ? monster.getAttribute() : null;
        }

        @Override
        public String species(Properties card)
        {
            return card instanceof MonsterProperties monster ? monster.getSpecies() : null;
        }

        @Override
        public int level(Properties card)
        {
            return card instanceof LevelMonsterProperties levelled ? levelled.level : 0;
        }

        @Override
        public int attack(Properties card)
        {
            return card instanceof MonsterProperties monster ? monster.getAtk() : 0;
        }

        @Override
        public int defence(Properties card)
        {
            // Not every monster has a DEF (Link monsters do not), so this is
            // read off the subclass that actually carries one.
            return card instanceof de.cas_ual_ty.dueldimension.card.properties.DefMonsterProperties def
                ? def.def : 0;
        }

        @Override
        public long id(Properties card)
        {
            return card.getId();
        }

        /**
         * How the official editors group a card within its kind. A monster
         * with no explicit type is Normal or Effect depending on whether it
         * has one, which is the distinction those editors draw.
         * <p>
         * Qualified by kind, because the names collide across kinds -- see
         * {@link CardQuery#subTypeKey}. A card with no sub-type still answers
         * null, which is what makes an unset axis mean "do not narrow".
         */
        @Override
        public String subType(Properties card)
        {
            if(card instanceof MonsterProperties monster)
            {
                de.cas_ual_ty.dueldimension.card.properties.MonsterType type = monster.getMonsterType();
                if(type != null)
                {
                    return CardQuery.subTypeKey(kind(card), type.name);
                }
                return CardQuery.subTypeKey(kind(card), monster.hasEffect ? "Effect" : "Normal");
            }
            if(card instanceof de.cas_ual_ty.dueldimension.card.properties.SpellProperties spell)
            {
                return spell.spellType == null ? null
                    : CardQuery.subTypeKey(kind(card), spell.spellType.name);
            }
            if(card instanceof de.cas_ual_ty.dueldimension.card.properties.TrapProperties trap)
            {
                return trap.trapType == null ? null
                    : CardQuery.subTypeKey(kind(card), trap.trapType.name);
            }
            return null;
        }

        /**
         * Pendulum is carried here beside Flip, Gemini and the rest. It is a
         * flag on the card rather than one of the ability names, but a player
         * looking for pendulums is doing the same thing as one looking for
         * toons, so it belongs in the same row.
         */
        @Override
        public java.util.Set<String> abilities(Properties card)
        {
            if(!(card instanceof MonsterProperties monster))
            {
                return java.util.Set.of();
            }
            java.util.Set<String> carried = new java.util.LinkedHashSet<>();
            String ability = monster.getAbility();
            if(ability != null && !ability.isEmpty())
            {
                carried.add(ability);
            }
            if(monster.getIsPendulum())
            {
                carried.add(PENDULUM);
            }
            // Tuner is a flag rather than an ability name, exactly as Pendulum
            // is, and it lives on LEVEL monsters only -- Xyz and Link carry no
            // such field and their JSON has no such key, which is why this is a
            // type test on the subclass that has it rather than on
            // MonsterProperties. The query ANDs it rather than reading it as
            // one of the alternatives here; see CardQuery.tunersOnly.
            if(card instanceof LevelMonsterProperties levelled && levelled.getIsTuner())
            {
                carried.add(CardQuery.TUNER);
            }
            return carried;
        }
    };

    /** Not one of the ability names, but filtered alongside them. */
    public static final String PENDULUM = "Pendulum";

    /**
     * Takes the profile the server just sent.
     * <p>
     * The open deck is kept by <em>name</em> rather than by position: the list
     * can come back in a different order, or a deck short, and a player who was
     * editing "Burn" should still be editing "Burn" rather than whatever is now
     * third in the list.
     */
    public static void accept(de.cas_ual_ty.dueldimension.duel.profile.DuelProfile sent)
    {
        String open = synced && current >= 0 && current < profile.decks().size()
            ? profile.decks().get(current).name() : "";

        // The sync carries a profile rather than a CompoundTag to unpack here:
        // the message and the disk share one Codec, so the client cannot read
        // the format differently from the way the server wrote it.
        profile = sent;
        synced = true;

        current = 0;
        List<DeckList> all = profile.decks();
        for(int i = 0; i < all.size(); i++)
        {
            if(all.get(i).name().equals(open))
            {
                current = i;
                break;
            }
        }
        // What we now hold came from the server, so there is nothing to send.
        agreed = contentsOf(deck());
        syncFavourites();
        dirty = true;
    }

    // ---- positions, and the artwork each one wears ----

    /**
     * One position in a deck: which card, and which artwork that copy wears.
     * <p>
     * The pair exists so a reorder cannot separate them. Sorting a list of
     * passcodes and then sorting a list of arts the same way is two operations
     * that have to agree, and the second one has no idea what the first did.
     */
    public record Copy(int code, int art)
    {
    }

    /**
     * The artwork the copy at this position wears; 0 is the printed art.
     * <p>
     * Answers 0 for a position with no art recorded, which is every position of
     * every deck built before this existed. That is the whole reason the art
     * lists are allowed to be short.
     */
    public static int artAt(DeckList.Part part, int index)
    {
        DeckList deck = deck();
        return deck.artAt(deck.partFor(part), index);
    }

    /**
     * Dresses one copy in one artwork.
     * <p>
     * Says nothing over the wire, like every other edit here: {@link #flush()}
     * compares the deck against what the server acknowledged once a tick and
     * sends it if it moved, and the comparison includes the arts. An edit that
     * announced itself would be a second thing to remember to do.
     */
    public static void setArt(DeckList.Part part, int index, int art)
    {
        DeckList deck = deck();
        List<Integer> cards = deck.partFor(part);
        if(index < 0 || index >= cards.size())
        {
            return;
        }
        deck.setArtAt(cards, index, Math.max(0, art));
    }

    /**
     * Appends a new copy, dressed in the printing the player owns.
     * <p>
     * The overload for a copy that did not exist a moment ago — a shift-click
     * out of the collection, a "+1" from the card info page. It is the ONLY
     * place the default is applied, and deliberately not {@link #insertCard}:
     * that one is also how a carried card is put back down and how a move
     * within a grid lands, where the art being handed to it is a fact being
     * restored rather than a blank to fill in. Defaulting there would repaint a
     * deliberately chosen artwork on every drag.
     */
    public static void addCard(DeckList.Part part, int code)
    {
        addCard(part, code, defaultArtFor(code));
    }

    /**
     * The artwork the next copy of this card added to the open deck should
     * wear; 0 for the printed art.
     * <p>
     * The rule itself lives in {@code DeckEdits} beside the one that sanitises
     * arts off the wire, so it can be tested without a client and so there is
     * one answer to it rather than one per caller. This is simply the editor's
     * way of asking, with the collection and the open deck filled in.
     */
    public static int defaultArtFor(int code)
    {
        return de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.artForNewCopy(trunk(), deck(),
            code);
    }

    /**
     * The artwork the fanciest copy of this card in the collection wears.
     * <p>
     * What the right-hand panel draws. A tile there is a CARD rather than a
     * copy, so it shows the best the player owns and keeps showing it while a
     * deck is built — asking for the next copy's art instead would make the
     * collection repaint itself as cards were dragged out of it.
     */
    public static int bestArtOwned(Properties card)
    {
        // Takes the card rather than its passcode because the panel already
        // holds it, and the answer for a card with one artwork is 0 without
        // looking anything up at all -- which is 13,740 of the 13,862 in the
        // database, asked once per tile per frame.
        return card == null || card.getImageIndicesAmt() <= 1 ? 0
            : de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.artOwnedAt(trunk(),
                (int)card.getId(), 0);
    }

    /** Appends a copy already dressed — what dropping a carried card does. */
    public static void addCard(DeckList.Part part, int code, int art)
    {
        insertCard(part, deck().partFor(part).size(), code, art);
    }

    /**
     * Puts a copy at a position, pushing everything from there along.
     * <p>
     * The art goes in at the same index rather than being written after the
     * fact, because writing it after would set the art of whichever copy now
     * happens to sit there.
     */
    public static void insertCard(DeckList.Part part, int index, int code, int art)
    {
        DeckList deck = deck();
        List<Integer> cards = deck.partFor(part);
        List<Integer> arts = deck.artsFor(cards);
        int at = Math.min(Math.max(0, index), cards.size());
        cards.add(at, code);
        // The art list is allowed to be short, so the insert point may not
        // exist yet; the positions in between are all on the printed art.
        while(arts.size() < at)
        {
            arts.add(0);
        }
        arts.add(at, Math.max(0, art));
    }

    /**
     * Takes a copy out, and its artwork with it.
     * <p>
     * <b>This is the one that would have been silently wrong.</b> Removing card
     * 3 of a forty card deck moves cards 4..39 back one place, so an art list
     * left alone would leave all thirty-six of them wearing the artwork of the
     * copy in front — a bug that shows up nowhere near the click that caused it.
     *
     * @return the artwork the removed copy was wearing, so a card picked up and
     *         put down elsewhere keeps it
     */
    public static int removeCard(DeckList.Part part, int index)
    {
        DeckList deck = deck();
        List<Integer> cards = deck.partFor(part);
        if(index < 0 || index >= cards.size())
        {
            return 0;
        }
        cards.remove(index);
        List<Integer> arts = deck.artsFor(cards);
        return index < arts.size() ? arts.remove(index) : 0;
    }

    /** One part as (card, artwork) pairs, in deck order. */
    public static List<Copy> copiesIn(DeckList.Part part)
    {
        DeckList deck = deck();
        List<Integer> cards = deck.partFor(part);
        List<Copy> copies = new ArrayList<>(cards.size());
        for(int index = 0; index < cards.size(); index++)
        {
            copies.add(new Copy(cards.get(index), deck.artAt(cards, index)));
        }
        return copies;
    }

    /**
     * Lays a part out again, exactly as given.
     * <p>
     * What a sort is: {@link #copiesIn} hands out the pairs, the caller puts
     * them in the order it wants, and this writes both lists back together.
     */
    public static void reorder(DeckList.Part part, List<Copy> copies)
    {
        DeckList deck = deck();
        List<Integer> cards = deck.partFor(part);
        List<Integer> arts = deck.artsFor(cards);
        cards.clear();
        arts.clear();
        for(Copy copy : copies)
        {
            cards.add(copy.code());
            arts.add(Math.max(0, copy.art()));
        }
    }

    /** Empties a part, artwork and all. */
    public static void clear(DeckList.Part part)
    {
        DeckList deck = deck();
        List<Integer> cards = deck.partFor(part);
        deck.artsFor(cards).clear();
        cards.clear();
    }

    /** True once the server has sent this player's collection. */
    public static boolean isSynced()
    {
        return synced;
    }

    public static Trunk trunk()
    {
        return profile.trunk();
    }

    public static de.cas_ual_ty.dueldimension.duel.profile.DuelProfile profile()
    {
        return profile;
    }

    /** The player's own builds -- what the Decks view lists. */
    /**
     * Appends a card to a deck that is not the one being edited, and saves it.
     *
     * <p>Used by the pack opening screen, where the player is nowhere near the
     * editor. It writes straight to that deck and sends the whole thing,
     * because DeckEdits.saveDeck REBUILDS a deck from the payload -- anything
     * left out of the message is wiped, so a partial "just add this one card"
     * message would empty the deck it was meant to add to.
     *
     * <p>Extra-deck monsters go to the extra deck. Putting a Fusion in the main
     * deck would make the deck illegal the moment it was saved.
     *
     * @return null if it went in, else why it did not
     */
    public static String addToDeck(DeckList target, int code)
    {
        if(target == null || target.origin().isGranted())
        {
            return "That deck cannot be changed";
        }
        Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
        if(card == null)
        {
            return "Unknown card";
        }
        DeckList.Part part = card.getIsInExtraDeck() ? DeckList.Part.EXTRA : DeckList.Part.MAIN;
        List<Integer> cards = target.partFor(part);
        int limit = part == DeckList.Part.EXTRA ? 15 : 60;
        if(cards.size() >= limit)
        {
            return target.name() + "'s " + (part == DeckList.Part.EXTRA ? "extra" : "main")
                + " deck is full";
        }
        // Three of a card is the game's own limit, and the editor enforces it
        // too; adding a fourth here would build a deck the editor then refuses.
        int copies = 0;
        for(DeckList.Part any : DeckList.Part.values())
        {
            for(int held : target.partFor(any))
            {
                if(held == code)
                {
                    copies++;
                }
            }
        }
        if(copies >= 3)
        {
            return "Already three in " + target.name();
        }
        cards.add(code);
        target.artsFor(cards).add(0);
        send(new ProfilePayloads.SaveDeck(target.name(),
            List.copyOf(target.main()), List.copyOf(target.extra()), List.copyOf(target.side()),
            List.copyOf(target.mainArts()), List.copyOf(target.extraArts()),
            List.copyOf(target.sideArts())));
        return null;
    }

    public static List<DeckList> ownDecks()
    {
        return new ArrayList<>(profile.ownDecks());
    }

    /** Every deck, in the order they were made. */
    public static List<DeckList> decks()
    {
        return profile.decks();
    }

    /**
     * A deck to show before the server has said anything, so the editor opened
     * early has something to draw rather than an index out of an empty list.
     * Detached on purpose: edits to it go nowhere, which is right, because
     * there is nothing yet to edit.
     */
    private static final DeckList PLACEHOLDER = new DeckList("...", DeckList.Origin.SAVED);

    /** The deck being edited. */
    public static DeckList deck()
    {
        List<DeckList> all = decks();
        if(all.isEmpty())
        {
            return PLACEHOLDER;
        }
        current = Math.max(0, Math.min(current, all.size() - 1));
        return all.get(current);
    }

    public static int currentIndex()
    {
        return current;
    }

    /**
     * Where a deck sits in the profile's list, or -1.
     * <p>
     * By identity first, then by name. A screen holds the deck objects it built
     * its rows from, and a profile sync replaces every one of them with a fresh
     * object carrying the same deck — so a row built before the sync and
     * clicked after it was looking up an object no longer in the list. That
     * returned -1, which {@link #select(int)} used to clamp to zero, and the
     * player got the first deck instead of the one they clicked.
     */
    public static int indexOf(DeckList deck)
    {
        List<DeckList> all = decks();
        int at = all.indexOf(deck);
        if(at >= 0)
        {
            return at;
        }
        for(int i = 0; i < all.size(); i++)
        {
            if(all.get(i).origin() == deck.origin() && all.get(i).name().equals(deck.name()))
            {
                return i;
            }
        }
        return -1;
    }

    public static void select(int index)
    {
        List<DeckList> all = decks();
        if(all.isEmpty())
        {
            current = 0;
            return;
        }
        if(index < 0)
        {
            // "No such deck" is not "the first deck". Clamping a failed lookup
            // up to zero is how a click on one deck opened another.
            return;
        }
        // Leaving a deck is the last chance to save what was done to it.
        flush();
        current = Math.max(0, Math.min(index, all.size() - 1));
        agreed = contentsOf(deck());
    }

    /** Adds a deck and switches to it, with a name that is not already taken. */
    public static DeckList newDeck()
    {
        String name = "New Deck";
        for(int suffix = 2; profile.savedNamed(name) != null; suffix++)
        {
            name = "New Deck " + suffix;
        }
        DeckList made = new DeckList(name, DeckList.Origin.SAVED);
        profile.addDeck(made);
        current = profile.decks().size() - 1;
        send(new ProfilePayloads.CreateDeck(name));
        return made;
    }

    /**
     * Copies a deck as a new build of the player's own and switches to it.
     * <p>
     * A copy of a granted structure deck becomes SAVED rather than STRUCTURE:
     * it is the player's build from that point, editable and deletable, while
     * the original stays as the record of what was opened.
     */
    public static DeckList duplicate(int index)
    {
        List<DeckList> all = decks();
        DeckList source = all.get(Math.max(0, Math.min(index, all.size() - 1)));
        String name = source.name() + " copy";
        for(int suffix = 2; profile.savedNamed(name) != null; suffix++)
        {
            name = source.name() + " copy " + suffix;
        }
        DeckList copy = source.copy(name, DeckList.Origin.SAVED);
        // DeckList.copy carries the sleeve but not the arts, and a copy of a
        // deck is that deck: duplicating one full of chosen artwork should not
        // hand back forty cards on their printed art. Position for position,
        // because the copy has the same cards in the same order.
        copy.setArts(source.mainArts(), source.extraArts(), source.sideArts());
        profile.addDeck(copy);
        current = profile.decks().size() - 1;
        // A copy of a granted deck is a recipe being used; a copy of a build is
        // a new deck with the same cards. The server tells them apart by which
        // message arrives, so the right one is sent.
        if(source.origin().isGranted())
        {
            send(new ProfilePayloads.CopyRecipe(source.name(), name));
        }
        else
        {
            send(new ProfilePayloads.CreateDeck(name));
            // The arts ride along in the payload, so unlike the sleeve below
            // they need no message of their own -- which is the point of having
            // put them there rather than leaving the server to carry them over.
            send(saveMessage(name, copy));
            // And the sleeve, which neither of those carries.
            //
            // CreateDeck makes a deck at the default sleeve, and SaveDeck's
            // carry-over then reads the sleeve off "the existing deck" -- which
            // here is the blank one CreateDeck just made, so it faithfully
            // preserves the default over the copied sleeve. The client kept it
            // (DeckList.copy does carry it), so the two sides disagreed and the
            // paid cosmetic quietly vanished at the next sync.
            //
            // Safe to send: the player owns the source deck's sleeve, which is
            // what the server checks before accepting.
            send(new ProfilePayloads.SetDeckSleeve(name,
                de.cas_ual_ty.dueldimension.duel.profile.Sleeves.nameOf(copy.sleeve())));
            send(new ProfilePayloads.SetDeckBox(name, copy.deckBox().name()));
            // And the list it was built to, for the same reason: SaveDeck reads
            // its carry-over off the blank deck CreateDeck just made, so without
            // this a duplicate of a TCG-legal deck comes back unrestricted.
            send(new ProfilePayloads.SetDeckBanlist(name, copy.banlistId()));
        }
        return copy;
    }

    /**
     * Builds a new deck from a recipe and switches to it.
     * <p>
     * Named after the recipe, which is what a player expects, but suffixed if
     * that name is already taken -- the recipe itself still holds it, and two
     * decks alike could not be told apart. The assigned name is what the rename
     * prompt is pre-filled with, so the default shown is the one that will
     * actually be kept.
     */
    public static DeckList useRecipe(int index)
    {
        List<DeckList> all = decks();
        DeckList source = all.get(Math.max(0, Math.min(index, all.size() - 1)));
        String name = source.name();
        for(int suffix = 2; profile.savedNamed(name) != null; suffix++)
        {
            name = source.name() + " " + suffix;
        }
        DeckList made = source.copy(name, DeckList.Origin.SAVED);
        // Matching what DeckEdits.copyRecipe does with the same two decks. A
        // granted recipe is all printed art anyway, but a player's own
        // published deck is a recipe too and can be full of chosen artwork.
        made.setArts(source.mainArts(), source.extraArts(), source.sideArts());
        profile.addDeck(made);
        current = profile.decks().size() - 1;
        send(new ProfilePayloads.CopyRecipe(source.name(), name));
        return made;
    }

    /**
     * Renames the current deck. Refused if the name is blank or taken, since
     * decks are found by name and two alike could not be told apart.
     */
    public static boolean rename(String name)
    {
        String trimmed = name == null ? "" : name.strip();
        if(trimmed.isEmpty())
        {
            return false;
        }
        DeckList existing = profile.savedNamed(trimmed);
        if(existing != null && existing != deck())
        {
            return false;
        }
        DeckList target = deck();
        String was = target.name();
        target.rename(trimmed);
        send(new ProfilePayloads.RenameDeck(was, trimmed));
        return true;
    }

    /** Moves a saved deck to another tile and persists that list order. */
    public static boolean moveDeck(DeckList source, DeckList target)
    {
        if(source == null || target == null || source == target)
        {
            return false;
        }
        DeckList selected = deck();
        String sourceName = source.name();
        String targetName = target.name();
        if(!profile.moveSavedDeck(sourceName, targetName))
        {
            return false;
        }
        current = profile.decks().indexOf(selected);
        send(new ProfilePayloads.MoveDeck(sourceName, targetName));
        return true;
    }

    /**
     * Deletes the current deck. A granted structure deck is left alone: it is
     * the recipe the cards came with, and deleting it would lose the record of
     * what was opened.
     *
     * @return null on success, else why not
     */
    public static String deleteCurrent()
    {
        DeckList target = deck();
        if(target.origin().isGranted())
        {
            return "Granted decks cannot be deleted";
        }
        // savedDecks(), not savedRecipes(): the latter also requires
        // published(), so a player whose decks were all private counted ZERO
        // and every delete fell into the clear-it branch below -- the deck
        // emptied and its entry stayed. This is the count that was meant.
        if(profile.savedDecks().size() <= 1)
        {
            // Clearing it is the same outcome and leaves somewhere to build.
            String name = target.name();
            target.main().clear();
            target.extra().clear();
            target.side().clear();
            // And the arts, which describe positions that no longer exist. Left
            // behind they would dress whatever the player builds here next.
            target.setArts(List.of(), List.of(), List.of());
            send(new ProfilePayloads.SaveDeck(name, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()));
            if(!"New Deck".equals(name))
            {
                target.rename("New Deck");
                send(new ProfilePayloads.RenameDeck(name, "New Deck"));
            }
            return null;
        }
        String name = target.name();
        profile.removeDeck(name);
        current = Math.max(0, current - 1);
        send(new ProfilePayloads.DeleteDeck(name));
        return null;
    }

    /**
     * The list the OPEN DECK is built to.
     *
     * <h2>Resolved every time, not stored</h2>
     * The deck holds an id and the catalogue holds the lists, so the object is
     * derived from those two rather than kept beside them. That is what makes
     * switching decks work without anything having to remember to update a
     * cached list -- and it is why a catalogue arriving after the editor is
     * already open takes effect on the next frame instead of needing the screen
     * reopened.
     * <p>
     * An id this server does not offer falls back to no list, the same way
     * {@code Banlists.byId} does on the server: a deck built on another server
     * is still a deck, and refusing to open it would be worse than opening it
     * unrestricted and saying so on the button.
     */
    public static Banlist banlist()
    {
        return banlistById(deck().banlistId());
    }

    /** A list by id out of what the server offered, or none. */
    public static Banlist banlistById(String id)
    {
        if(id == null || Banlist.NO_BANLIST_ID.equals(id))
        {
            return Banlist.none();
        }
        if(Banlist.DEFAULT_ID.equals(id))
        {
            // Resolved with Banlist.mostRecentTcg -- the same call the server
            // makes in Banlists.current, against the same lists. The rule lives
            // in common precisely so these two cannot drift: the editor draws
            // its badges from this answer and the server enforces from that one.
            Banlist tcg = Banlist.mostRecentTcg(banlists());
            return tcg == null ? Banlist.none() : tcg;
        }
        for(Banlist list : banlists)
        {
            if(list.id().equals(id))
            {
                return list;
            }
        }
        return Banlist.none();
    }

    /**
     * Every list this server offers, "No Banlist" first.
     * <p>
     * Empty until the profile syncs. The editor treats that as "only no list",
     * which is honest: before the server has said, the client genuinely does not
     * know of any.
     */
    public static java.util.List<Banlist> banlists()
    {
        return banlists.isEmpty() ? java.util.List.of(Banlist.none()) : banlists;
    }

    /** The server's catalogue, as it arrives. See ProfilePayloads.BanlistCatalogue. */
    public static void setBanlists(java.util.List<Banlist> lists)
    {
        banlists = lists == null || lists.isEmpty()
            ? java.util.List.of(Banlist.none()) : java.util.List.copyOf(lists);
        // Copy limits and the dimming that follows from them are computed off
        // this, and the visible collection is cached. Without the invalidation
        // an editor open when the catalogue lands keeps showing the limits it
        // had -- which, before any catalogue, means none.
        invalidate();
    }

    /**
     * Builds the open deck to a list, and tells the server.
     * <p>
     * Applied locally first, as every other edit in this screen is: the server
     * re-checks it and syncs the whole profile back if it refuses, so an
     * optimistic apply cannot leave the two disagreeing for longer than the
     * round trip. See {@code ProfilePayloads.answer}.
     */
    public static void setDeckBanlist(String id)
    {
        DeckList open = deck();
        open.setBanlistId(id);
        send(new ProfilePayloads.SetDeckBanlist(open.name(), open.banlistId()));
        invalidate();
    }

    /**
     * Steps the open deck to the next list the server offers, wrapping.
     * <p>
     * The same gesture the duel lobby's banlist control uses, and for the same
     * reason: a handful of lists is a cycle, not a menu.
     */
    public static void cycleDeckBanlist(int direction)
    {
        java.util.List<Banlist> lists = banlists();
        if(lists.size() < 2)
        {
            return;
        }
        int index = 0;
        String current = deck().banlistId();
        for(int i = 0; i < lists.size(); i++)
        {
            if(lists.get(i).id().equals(current))
            {
                index = i;
                break;
            }
        }
        int next = Math.floorMod(index + direction, lists.size());
        setDeckBanlist(lists.get(next).id());
    }

    public static CardQuery<Properties> query()
    {
        if(query == null)
        {
            query = new CardQuery<>(FACETS);
        }
        return query;
    }

    /** Marks the trunk view stale, so the next render re-runs the query. */
    public static void invalidate()
    {
        dirty = true;
    }

    /**
     * The trunk as the right-hand panel shows it: filtered and sorted.
     * <p>
     * Cached, because the query runs over the whole collection and the panel
     * asks for it every frame. Anything that changes the query calls
     * {@link #invalidate()}.
     */
    public static List<Properties> visible()
    {
        if(dirty)
        {
            visible = query().apply(pool());
            dirty = false;
        }
        return visible;
    }

    /**
     * What the editor may build from: the whole database in free mode, and the
     * player's own cards otherwise.
     * <p>
     * Free mode shows every legal card rather than every row in the database --
     * an illegal card is not something the game will let anyone play, free mode
     * or not, so offering it would only produce decks that cannot be used.
     */
    private static List<Properties> pool()
    {
        List<Properties> pool = new ArrayList<>();
        if(freeMode() || showUnowned)
        {
            for(Properties card : DdDatabase.PROPERTIES_LIST)
            {
                if(card != null && card.getId() > 0 && !card.getIllegal()
                    && engineKnows((int)card.getId()))
                {
                    pool.add(card);
                }
            }
            return pool;
        }
        for(int code : trunk().all().keySet())
        {
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
            // Owned but unplayable is still hidden: the collection screen is
            // where a deck is built, and a card that cannot be duelled with has
            // no business being offered there. It stays in the Trunk, so it
            // returns the moment the engine learns it.
            if(card != null && engineKnows(code))
            {
                pool.add(card);
            }
        }
        return pool;
    }

    public static boolean showUnowned()
    {
        return showUnowned;
    }

    public static void setShowUnowned(boolean value)
    {
        if(showUnowned != value)
        {
            showUnowned = value;
            invalidate();
        }
    }

    public static boolean owns(int passcode)
    {
        return trunk().countOf(passcode) > 0;
    }

    /** Whether the server has free mode on. */
    public static boolean freeMode()
    {
        return de.cas_ual_ty.dueldimension.duel.profile.FreeMode.clientBelief();
    }

    /** Cards in this deck the player does not have enough of. */
    public static java.util.List<Integer> missingFrom(DeckList deck)
    {
        java.util.List<Integer> missing = new ArrayList<>();
        deck.counts().forEach((code, count) ->
        {
            if(count > trunk().countOf(code))
            {
                missing.add(code);
            }
        });
        return missing;
    }

    /**
     * Sends the open deck if it differs from what the server last had.
     * <p>
     * Driven by comparison rather than by each edit announcing itself. A deck
     * is edited from a dozen places — click, shift-click, drag, drop, the
     * right-click menu — and a scheme where every one of them has to remember
     * to say so is a scheme where one of them eventually does not. Comparing
     * ninety numbers once a tick cannot miss one.
     * <p>
     * Called from the editor's tick and when it closes, so a burst of clicks
     * costs one message rather than one each.
     */
    /**
     * Sends the open deck whether or not it looks changed.
     * <p>
     * What "Save and Exit" means. {@link #flush()} is the autosave and skips a
     * deck that matches what the server acknowledged, which is the right thing
     * for a tick and the wrong thing for a button a player pressed on purpose.
     */
    public static void save()
    {
        if(!synced)
        {
            return;
        }
        DeckList open = deck();
        if(open == PLACEHOLDER)
        {
            return;
        }
        agreed = contentsOf(open);
        send(saveMessage(open.name(), open));
    }

    public static void flush()
    {
        if(!synced)
        {
            return;
        }
        DeckList open = deck();
        if(open == PLACEHOLDER)
        {
            return;
        }
        String now = contentsOf(open);
        if(now.equals(agreed))
        {
            return;
        }
        agreed = now;
        send(saveMessage(open.name(), open));
    }

    /**
     * A deck as the wire carries it: three lists of cards and, beside them, the
     * artwork each copy wears.
     * <p>
     * The arts go through {@code DeckEdits.canonicalArts} — the same method the
     * server puts them through on arrival — so what is sent is already what will
     * be stored. Skipping that would make the two sides disagree about a deck
     * neither of them changed: the server would trim an art off a card that has
     * only one, and the next comparison would see a difference and send again.
     */
    private static ProfilePayloads.SaveDeck saveMessage(String name, DeckList deck)
    {
        return new ProfilePayloads.SaveDeck(name, deck.main(), deck.extra(), deck.side(),
            de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.canonicalArts(deck.main(),
                deck.mainArts()),
            de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.canonicalArts(deck.extra(),
                deck.extraArts()),
            de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.canonicalArts(deck.side(),
                deck.sideArts()));
    }

    /**
     * What the server is believed to hold for the open deck.
     * <p>
     * Set whenever the two are known to agree — on a sync, and on switching
     * decks. Without that, a sync would look like a change, be sent straight
     * back, and be answered with another sync.
     */
    private static String agreed = "";

    /**
     * Everything about a deck the server needs told, as one string to compare.
     * <p>
     * The arts are in it, and they have to be: this comparison is the <em>only</em>
     * thing that decides whether {@link #flush()} sends. Choosing an artwork
     * changes no card and no name, so without them an art-only edit would look
     * like nothing happened and would be lost when the editor closed.
     * <p>
     * Canonical, for the same reason {@link #saveMessage} is: what is compared
     * has to be what is sent, or a deck the server accepted would keep looking
     * different from the one it stored.
     */
    private static String contentsOf(DeckList deck)
    {
        // The Destiny flags are deliberately NOT part of this. They travel on
        // their own message, so a flag change must not look like a contents
        // change -- that would make flush() send a SaveDeck that says nothing
        // new, on every single flag.
        return deck.name() + "|" + deck.main() + deck.extra() + deck.side()
            + de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.canonicalArts(deck.main(),
                deck.mainArts())
            + de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.canonicalArts(deck.extra(),
                deck.extraArts())
            + de.cas_ual_ty.dueldimension.duel.profile.DeckEdits.canonicalArts(deck.side(),
                deck.sideArts());
    }

    /**
     * DE and the win/loss record, as the server last stated them.
     * <p>
     * Held here rather than on the profile because they change at the end of
     * every duel and the profile travels whole; see
     * {@code StatsMessages}. Zero until the first sync, which is
     * indistinguishable from a new player -- {@link #statsKnown} is what tells
     * the panel which it is looking at.
     */
    private static int duelEnergy;
    private static int duelWins;
    private static int duelLosses;
    private static int npcWins;
    private static int npcLosses;
    private static boolean statsKnown;

    /**
     * Which record the profile panel is showing.
     * <p>
     * A view, not a preference: it lives here rather than in a settings file
     * because it is a way of looking at one screen and costs nothing to set
     * again. Player first, because that is the record the split exists to stop
     * the bots diluting.
     */
    private static boolean showingNpcRecord;

    public static void setStats(int gp, int wins, int losses, int npcWon, int npcLost)
    {
        duelEnergy = gp;
        duelWins = wins;
        duelLosses = losses;
        npcWins = npcWon;
        npcLosses = npcLost;
        statsKnown = true;
    }

    public static int npcWins()
    {
        return npcWins;
    }

    public static int npcLosses()
    {
        return npcLosses;
    }

    /** True while the panel is showing the NPC record rather than the player one. */
    public static boolean showingNpcRecord()
    {
        return showingNpcRecord;
    }

    public static void toggleRecord()
    {
        showingNpcRecord = !showingNpcRecord;
    }

    /** Whichever record is on show: {@code {won, lost}}. */
    public static int[] shownRecord()
    {
        return showingNpcRecord ? new int[] {npcWins, npcLosses}
            : new int[] {duelWins, duelLosses};
    }

    public static int duelEnergy()
    {
        return duelEnergy;
    }

    public static int duelWins()
    {
        return duelWins;
    }

    public static int duelLosses()
    {
        return duelLosses;
    }

    public static boolean statsKnown()
    {
        return statsKnown;
    }

    public static boolean isFavourite(int passcode)
    {
        return profile.isFavourite(passcode);
    }

    /** Whether this card in the open deck is flagged as a Destiny Card. */
    public static boolean isDestiny(int passcode)
    {
        return deck().isDestiny(passcode);
    }

    /**
     * Flags or unflags a card, and tells the server at once.
     * <p>
     * Sent immediately rather than left to {@link #flush}, because flush only
     * sends when the deck's CONTENTS changed and a flag changes none of them --
     * so a flag left to flush would be applied on screen, agree with itself,
     * and never reach the server. The same reasoning {@code toggleFavouritePack}
     * spells out: applied here as well as sent, since an accepted edit is
     * expected to be already applied on this side.
     */
    public static void toggleDestiny(int passcode)
    {
        DeckList open = deck();
        if(open == PLACEHOLDER || !open.main().contains(passcode))
        {
            // Main deck only -- the Extra Deck is never drawn from.
            //
            // SAID OUT LOUD, because this return is the one way a click on
            // "Destiny Card" can do nothing at all: the row is only offered for
            // a main-deck card, so reaching it means the card the menu was
            // opened on is not the card the open deck holds, and a mark that
            // silently fails to happen is indistinguishable from the feature
            // being broken further down.
            de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny mark REFUSED for "
                + passcode + ": " + (open == PLACEHOLDER ? "no deck open"
                    : "not in the main deck of \"" + open.name() + "\""));
            return;
        }
        boolean now = !open.isDestiny(passcode);
        open.setDestiny(passcode, now);
        de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny mark " + (now ? "SET" : "CLEARED")
            + " for " + passcode + " in \"" + open.name() + "\"; sending "
            + open.destiny().size() + " flag(s)");
        send(new ProfilePayloads.SetDeckDestiny(open.name(),
            new ArrayList<>(open.destiny())));
    }

    public static boolean isFavouritePack(String code)
    {
        return code != null && profile.isFavouritePack(code);
    }

    /**
     * Stars a sealed product, or unstars it.
     * <p>
     * <b>Applied here as well as sent</b>, which is the half that was missing.
     * {@code ProfilePayloads.answer} syncs the authoritative profile back only
     * when an edit is REFUSED -- an accepted one is expected to be already
     * applied on this side. A shop star that merely sent the message therefore
     * changed nothing on screen and looked broken, because the answer it was
     * waiting for is one the server deliberately never sends.
     */
    public static void toggleFavouritePack(String code)
    {
        if(code == null || code.isEmpty())
        {
            return;
        }
        profile.toggleFavouritePack(code);
        dirty = true;
        send(new ProfilePayloads.ToggleFavouritePack(code));
    }

    /** Stars a card, or unstars it. Applied here and asked for over the wire. */
    public static void toggleFavourite(int passcode)
    {
        profile.toggleFavourite(passcode);
        syncFavourites();
        dirty = true;
        send(new ProfilePayloads.ToggleFavourite(passcode));
    }

    /**
     * Hands the query the current stars.
     * <p>
     * Copied rather than shared: the query filters against what it was told,
     * and a set that changed underneath it mid-render would show a card the
     * filter had already rejected.
     */
    private static void syncFavourites()
    {
        java.util.Set<Long> stars = new java.util.LinkedHashSet<>();
        profile.favourites().forEach(code -> stars.add((long)(int)code));
        query().setFavourites(stars);
    }

    /**
     * Offers a deck as a recipe, or withdraws it.
     * <p>
     * Applied here and asked for over the wire, like every other edit: the
     * list redraws on the click rather than on the round trip.
     */
    public static void publish(DeckList deck, boolean asRecipe)
    {
        if(deck == null || deck.origin().isGranted())
        {
            return;
        }
        deck.publish(asRecipe);
        send(new ProfilePayloads.PublishRecipe(deck.name(), asRecipe));
    }

    /** Tells the server which deck this player duels with. */
    public static void setActiveDeck(String name)
    {
        profile.setActiveDeck(name);
        send(new ProfilePayloads.SetActiveDeck(name));
    }

    /**
     * Asks the server for something.
     * <p>
     * Typed as a payload rather than as {@code Object}, which is what Forge's
     * channel took: a message that was never registered is now a compile error
     * instead of a packet that vanishes at runtime.
     */
    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload
        message)
    {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(message);
    }
}
