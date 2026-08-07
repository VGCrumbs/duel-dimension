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
    private static Banlist banlist = Banlist.none();
    private static CardQuery<Properties> query;
    private static List<Properties> visible = new ArrayList<>();
    private static boolean dirty = true;

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
            return card.getText();
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
         */
        @Override
        public String subType(Properties card)
        {
            if(card instanceof MonsterProperties monster)
            {
                de.cas_ual_ty.dueldimension.card.properties.MonsterType type = monster.getMonsterType();
                if(type != null)
                {
                    return type.name;
                }
                return monster.hasEffect ? "Effect" : "Normal";
            }
            if(card instanceof de.cas_ual_ty.dueldimension.card.properties.SpellProperties spell)
            {
                return spell.spellType == null ? null : spell.spellType.name;
            }
            if(card instanceof de.cas_ual_ty.dueldimension.card.properties.TrapProperties trap)
            {
                return trap.trapType == null ? null : trap.trapType.name;
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
            send(new ProfilePayloads.SaveDeck(name, copy.main(), copy.extra(), copy.side()));
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
        if(profile.savedRecipes().size() <= 1)
        {
            // Clearing it is the same outcome and leaves somewhere to build.
            String name = target.name();
            target.main().clear();
            target.extra().clear();
            target.side().clear();
            send(new ProfilePayloads.SaveDeck(name, List.of(), List.of(), List.of()));
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

    public static Banlist banlist()
    {
        return banlist;
    }

    public static void setBanlist(Banlist value)
    {
        banlist = value;
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
        if(freeMode())
        {
            for(Properties card : DdDatabase.PROPERTIES_LIST)
            {
                if(card != null && card.getId() > 0 && !card.getIllegal())
                {
                    pool.add(card);
                }
            }
            return pool;
        }
        for(int code : trunk().all().keySet())
        {
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card != null)
            {
                pool.add(card);
            }
        }
        return pool;
    }

    /** Whether the server has free mode on. */
    public static boolean freeMode()
    {
        return de.cas_ual_ty.dueldimension.duel.profile.FreeMode.clientBelief();
    }

    /**
     * Whether the player is short of this card for the deck they have built.
     * <p>
     * Counts the whole deck rather than asking whether the card is owned at
     * all: two copies of a card owned once is the same problem as one copy of
     * a card owned never, and the editor should mark both.
     */
    public static boolean isShortOf(int passcode)
    {
        int used = deck().copiesOf(passcode);
        return used > trunk().countOf(passcode);
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
        send(new ProfilePayloads.SaveDeck(open.name(), open.main(), open.extra(), open.side()));
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
        send(new ProfilePayloads.SaveDeck(open.name(), open.main(), open.extra(), open.side()));
    }

    /**
     * What the server is believed to hold for the open deck.
     * <p>
     * Set whenever the two are known to agree — on a sync, and on switching
     * decks. Without that, a sync would look like a change, be sent straight
     * back, and be answered with another sync.
     */
    private static String agreed = "";

    private static String contentsOf(DeckList deck)
    {
        return deck.name() + "|" + deck.main() + deck.extra() + deck.side();
    }

    public static boolean isFavourite(int passcode)
    {
        return profile.isFavourite(passcode);
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

    /**
     * Asks to be seen in this outfit.
     * <p>
     * Applied here as well as asked for, so the hub's mark moves on the click
     * rather than on the round trip; the sync that follows is what makes it
     * true, and would correct this if the server said no.
     */
    public static void wear(String outfitId)
    {
        profile.setOutfit(outfitId);
        send(new de.cas_ual_ty.dueldimension.duel.outfit.OutfitMessages.Wear(outfitId));
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
