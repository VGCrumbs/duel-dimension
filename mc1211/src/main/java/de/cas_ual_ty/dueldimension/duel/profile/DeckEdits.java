package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.duel.match.Banlist;
import de.cas_ual_ty.dueldimension.duel.match.Banlists;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * What a player is allowed to do to their own decks, decided on the server.
 * <p>
 * Until now these rules only existed on the client, inside the editor screen
 * that was applying them. That is the wrong place for them to live alone: the
 * editor is the one part of the game a player can replace. Each method here
 * returns the reason it refused, or null if it did not, and the caller reports
 * that reason and re-syncs either way.
 * <p>
 * The rules themselves are not new — {@link DeckLimits} has always held them,
 * and it is the same call the editor makes. What is new is that the answer now
 * comes from the side that owns the cards.
 */
public final class DeckEdits
{
    private DeckEdits()
    {
    }

    /**
     * The list a STORED deck must be legal against: none, deliberately.
     *
     * <h2>This stayed none() when decks gained a list of their own</h2>
     * A deck now carries {@code banlistId}, so passing it here would be the
     * obvious change and it is the wrong one. Saving is not the moment a format
     * is enforced:
     * <ul>
     * <li><b>It would make a deck uneditable by labelling it.</b> Build three
     *     copies under no list, then set the deck to a list that limits one, and
     *     every later autosave -- one per card added -- is refused for a card
     *     the player is not touching. The way out is to remove the extras, which
     *     they cannot do while the save that removes them is being rejected for
     *     containing them.</li>
     * <li><b>The editor already says it.</b> Copy limits, the dimming and the
     *     forbidden/limited badges are all drawn from the deck's own list, so a
     *     fourth copy cannot be added there in the first place.</li>
     * <li><b>And a duel already checks it.</b> {@code DuelLobby} validates
     *     against the ROOM's list -- {@code Banlists.byId(config.banlistId())},
     *     not the deck's -- which is the check that actually decides anything,
     *     and it is on the server where a replaced client cannot reach it.</li>
     * </ul>
     * What this call still enforces is structural and format-independent: deck
     * capacities, and the hard three-copy ceiling that no list may exceed. Those
     * are true of every deck under every list, which is why they belong on save
     * and the format does not.
     */
    private static Banlist banlist()
    {
        return Banlist.none();
    }

    /**
     * Stores a deck's contents, creating it if the player has no deck by that
     * name.
     * <p>
     * A deck may be saved half-built or with missing cards because the editor
     * is also a planning tool. Draft limits still apply; ownership is checked
     * later, when the deck is selected or used for a duel.
     *
     * @param mainArts one entry per position in {@code main}, 0 for the printed
     *                 art; short lists and out-of-range entries both read as
     *                 printed art, so a deck that sends none is not an error
     */
    public static String saveDeck(ServerPlayer player, String name, List<Integer> main,
        List<Integer> extra, List<Integer> side, List<Integer> mainArts,
        List<Integer> extraArts, List<Integer> sideArts)
    {
        String clean = clean(name);
        if(clean.isEmpty())
        {
            return "A deck needs a name.";
        }

        DuelProfile profile = DuelProfiles.get(player);
        // Own decks only. A granted deck is the record of what was opened and
        // is never edited in place -- the editor offers "use", which copies it.
        // Looking it up here at all is what made a deck named after the recipe
        // it came from unsaveable.
        DeckList existing = profile.savedNamed(clean);

        DeckList candidate = new DeckList(clean, DeckList.Origin.SAVED, main, extra, side);
        // From the payload, and never from `existing` -- see the sleeve note
        // below for the trick this deliberately does NOT copy. An art belongs to
        // a position, this message is what moves the positions, and the arts the
        // stored deck held describe an arrangement that no longer exists.
        // Sanitised here rather than trusted: the lists came off the wire.
        candidate.setArts(canonicalArts(main, mainArts), canonicalArts(extra, extraArts),
            canonicalArts(side, sideArts));
        if(existing != null)
        {
            // Saving a deck does not withdraw it from the recipe list.
            candidate.publish(existing.published());
            // Nor does it undress it. The payload carries cards and a name, and
            // this method REPLACES the stored deck with one built from exactly
            // that -- so anything the editor does not send has to be carried
            // over here or it is wiped on every autosave. The line above is that
            // same dance for `published`; this is it for the sleeve.
            candidate.setSleeve(existing.sleeve());
            candidate.setDeckBox(existing.deckBox());
            // And the list it is built to, which SetDeckBanlist sets and this
            // payload does not carry. Without this line every autosave -- one
            // per card added -- would reset the deck to no list, so the setting
            // would appear to work and then quietly undo itself.
            candidate.setBanlistId(existing.banlistId());
            // AND THE DESTINY CARD FLAGS, for exactly the same reason, which
            // this method predicted and then did not do.
            //
            // They travel on SetDeckDestiny, so they are not in this payload
            // either -- and every autosave was therefore erasing them. The
            // symptom was that marks worked until the game was closed: the flag
            // reached the server and was stored, and then the next card added,
            // moved or removed replaced the deck with one that had never heard
            // of it. The mark was gone long before anything was written to disk.
            //
            // Filtered to what the NEW main deck holds, not copied wholesale: a
            // flagged card the player has just taken out of the deck is not a
            // Destiny Card any more, and keeping its passcode would leave a flag
            // on a card that is no longer there for the engine to find.
            List<Integer> flags = new java.util.ArrayList<>();
            for(Integer code : existing.destiny())
            {
                if(code != null && candidate.main().contains(code))
                {
                    flags.add(code);
                }
            }
            candidate.setDestiny(flags);
        }
        for(int code : candidate.counts().keySet())
        {
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
            if(card == null || card.getIllegal())
            {
                return "Card " + code + " is not available for deck building.";
            }
        }
        String refusal = refusalFor(profile.trunk(), candidate, banlist(),
            FreeMode.isEnabled(player));
        if(refusal != null)
        {
            return refusal;
        }

        if(existing != null)
        {
            profile.removeDeck(clean);
        }
        profile.addDeck(candidate);
        return null;
    }

    /**
     * An art list as it is stored: one entry per position that needs one.
     * <p>
     * Three things at once, and all three are needed on both sides of the wire,
     * which is why this is one method rather than a rule written twice.
     * <ul>
     * <li>It is cut to the length of the cards it describes. A longer list is a
     * client asserting artwork for copies that are not there; a shorter one is
     * the normal case and simply means the rest are on the printed art.</li>
     * <li>Every entry is checked against the artwork the card at that position
     * actually has, and anything else folds to 0. {@code Properties.getImages()}
     * is the only authority on that — around a hundred and twenty cards in the
     * whole database have more than one, and for every other card the only legal
     * answer is 0.</li>
     * <li>Trailing zeros are dropped, so a deck with no alternate artwork
     * anywhere stores and sends an empty list. The feature costs nothing for
     * the cards that do not use it.</li>
     * </ul>
     * Canonical in both directions: the client puts a list through this before
     * sending, so the list the server keeps is the one the client believes it
     * has and an autosave does not fire again on the difference.
     */
    public static List<Integer> canonicalArts(List<Integer> cards, List<Integer> arts)
    {
        List<Integer> canonical = new java.util.ArrayList<>();
        int lastChosen = -1;
        for(int index = 0; index < cards.size(); index++)
        {
            Integer code = cards.get(index);
            Integer art = arts != null && index < arts.size() ? arts.get(index) : null;
            int chosen = code == null || art == null ? 0 : artOrPrinted(code, art);
            canonical.add(chosen);
            if(chosen != 0)
            {
                lastChosen = index;
            }
        }
        return new java.util.ArrayList<>(canonical.subList(0, lastChosen + 1));
    }

    /** The asked-for artwork if the card has one, else the printed art. */
    private static int artOrPrinted(int code, int art)
    {
        if(art <= 0)
        {
            return 0;
        }
        de.cas_ual_ty.dueldimension.card.properties.Properties card =
            de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)code);
        String[] images = card == null ? null : card.getImages();
        return images != null && art < images.length ? art : 0;
    }

    /**
     * The artwork a copy about to be added to this deck should wear.
     * <p>
     * The one rule behind "owning the MVP1 printing means that copy wears
     * artwork 2". The copy being added is the deck's n-th of that card, and it
     * wears the n-th of the player's own copies, fanciest first — so a player
     * holding one BP01 Obelisk (artwork 1) and one MVP1 Obelisk (artwork 2) who
     * puts two in a deck gets artwork 2 and artwork 1, which is the pair of
     * cards they actually own. A third copy, or a card they own none of, is the
     * printed art.
     * <p>
     * Counted with {@link DeckList#copiesOf}, across main, extra and side, for
     * the same reason the copy limit is counted that way: they are all copies of
     * one card out of one collection.
     * <p>
     * Free mode is not a special case here, deliberately. It widens what may go
     * in a deck, not what is in the collection — so a card the player owns is
     * still dressed in the printing they own, and the cards free mode conjured
     * up are unowned and answer 0 by the ordinary rule.
     * <p>
     * <b>A default, applied once, at the moment a copy is created.</b> That is
     * how a deliberate choice is told from an undecided one, and it is the whole
     * of the mechanism: nothing recomputes the art of a position that already
     * exists. The Alt Arts picker writes through {@code EditorState.setArt} and
     * whatever it writes stays written, artwork 0 included — a player who looks
     * at their MVP1 Obelisk and picks the printed art keeps the printed art,
     * because no later code path asks this question about that position again.
     * The same is what leaves every deck built before this change exactly as it
     * was: its positions were created long ago and are never re-dressed.
     */
    public static int artForNewCopy(Trunk trunk, DeckList deck, int passcode)
    {
        return artOwnedAt(trunk, passcode, deck == null ? 0 : deck.copiesOf(passcode));
    }

    /**
     * The artwork of the player's n-th copy of a card, folded to the printed art
     * if this build's database does not have it.
     * <p>
     * Answers 0 before touching the collection for any card with a single
     * artwork, which is 13,740 of the 13,862 in the database. The editor asks
     * this on every add and once per collection tile per frame, so the cards
     * that cannot use the feature must not pay for it.
     */
    public static int artOwnedAt(Trunk trunk, int passcode, int ordinal)
    {
        if(trunk == null)
        {
            return 0;
        }
        de.cas_ual_ty.dueldimension.card.properties.Properties card =
            de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get((long)passcode);
        if(card == null || card.getImageIndicesAmt() <= 1)
        {
            return 0;
        }
        int art = trunk.artForCopy(passcode, ordinal);
        // The collection stores what the set file said, and a set file can name
        // an artwork this build's database no longer has. Folded here rather
        // than trusted, the same way canonicalArts folds what comes off the wire.
        return art > 0 && art < card.getImageIndicesAmt() ? art : 0;
    }

    public static String createDeck(ServerPlayer player, String name)
    {
        String clean = clean(name);
        if(clean.isEmpty())
        {
            return "A deck needs a name.";
        }
        DuelProfile profile = DuelProfiles.get(player);
        if(profile.savedNamed(clean) != null)
        {
            return "You already have a deck called \"" + clean + "\".";
        }
        profile.addDeck(new DeckList(clean, DeckList.Origin.SAVED));
        return null;
    }

    public static String renameDeck(ServerPlayer player, String from, String to)
    {
        String cleanTo = clean(to);
        if(cleanTo.isEmpty())
        {
            return "A deck needs a name.";
        }

        DuelProfile profile = DuelProfiles.get(player);
        DeckList deck = profile.savedNamed(from);
        if(deck == null)
        {
            return "You have no deck called \"" + from + "\".";
        }
        if(deck.origin().isGranted())
        {
            return "A granted deck keeps its name.";
        }
        if(!from.equals(cleanTo) && profile.savedNamed(cleanTo) != null)
        {
            return "You already have a deck called \"" + cleanTo + "\".";
        }

        boolean wasActive = profile.activeDeck().equals(deck.name());
        deck.rename(cleanTo);
        if(wasActive)
        {
            // The active deck is remembered by name, so renaming it without
            // this would silently leave the player with no deck selected.
            profile.setActiveDeck(cleanTo);
        }
        return null;
    }

    /** Reorders two positions in the player's saved-deck list. */
    public static String moveDeck(ServerPlayer player, String name, String targetName)
    {
        DuelProfile profile = DuelProfiles.get(player);
        if(profile.savedNamed(name) == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        if(profile.savedNamed(targetName) == null)
        {
            return "You have no deck called \"" + targetName + "\".";
        }
        if(name.equals(targetName))
        {
            return null;
        }
        return profile.moveSavedDeck(name, targetName) ? null : "That deck could not be moved.";
    }

    public static String deleteDeck(ServerPlayer player, String name)
    {
        DuelProfile profile = DuelProfiles.get(player);
        DeckList deck = profile.savedNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        profile.removeDeck(name);
        if(profile.activeDeck().equals(name))
        {
            profile.setActiveDeck("");
        }
        return null;
    }

    public static String copyRecipe(ServerPlayer player, String recipe, String name)
    {
        String clean = clean(name);
        if(clean.isEmpty())
        {
            return "A deck needs a name.";
        }
        DuelProfile profile = DuelProfiles.get(player);
        if(profile.savedNamed(clean) != null)
        {
            return "You already have a deck called \"" + clean + "\".";
        }
        DeckList source = profile.deckNamed(recipe);
        if(source == null)
        {
            return "You have no recipe called \"" + recipe + "\".";
        }
        DeckList made = profile.copyAsRecipe(recipe, clean);
        if(made == null)
        {
            return "That recipe could not be copied.";
        }
        // Safe to copy the arts wholesale here, unlike on save: nothing moved.
        // The copy has the recipe's cards in the recipe's order, so position i
        // of one is position i of the other. A recipe is usually a granted deck
        // with every copy on its printed art, but a player's own published deck
        // is a recipe too, and that one can be full of chosen artwork.
        made.setArts(source.mainArts(), source.extraArts(), source.sideArts());
        // A copy is a deck, not another recipe. Publishing is the player's to
        // say, and saying it once for the source should not say it for every
        // deck ever built from it.
        made.publish(false);
        return null;
    }

    /**
     * Offers one of the player's decks as a recipe, or withdraws it.
     * <p>
     * Granted decks are refused rather than ignored: they are recipes already,
     * and a button that appeared to turn one off would be lying.
     */
    public static String publishRecipe(ServerPlayer player, String name, boolean asRecipe)
    {
        DuelProfile profile = DuelProfiles.get(player);
        DeckList deck = profile.savedNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        deck.publish(asRecipe);
        return null;
    }

    /**
     * Dresses one of the player's decks in a sleeve they own.
     * <p>
     * The client asks by name and the server decides, which is the whole reason
     * this is not simply a field the editor writes: sleeves are bought, so
     * "which do I own" is an answer only the side holding the money may give. A
     * picker that greyed out what was not owned would be doing decoration, not
     * enforcement — the request it declines to send is one a modified client
     * sends anyway.
     */
    public static String setDeckSleeve(ServerPlayer player, String name, String sleeveName)
    {
        CardSleevesType sleeve = Sleeves.byName(sleeveName);
        if(sleeve == null)
        {
            // Strict here where the Codec is lenient: an unknown id off the disk
            // is an old save worth forgiving, but an unknown id off the wire is
            // a client asserting something this build does not have.
            return "There is no such sleeve.";
        }
        return setDeckSleeve(DuelProfiles.get(player), name, sleeve);
    }

    /** The rule itself, on a profile rather than a player, so it can be tested. */
    public static String setDeckSleeve(DuelProfile profile, String name, CardSleevesType sleeve)
    {
        // Any deck the player has, not only their own builds. The granted-deck
        // rule exists to protect the RECORD of what was opened -- its name and
        // its cards -- and a sleeve is neither; a granted deck can also be the
        // active one, so refusing here would leave a duellist unable to dress
        // the deck they actually duel with.
        DeckList deck = profile.deckNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        if(sleeve == null)
        {
            return "There is no such sleeve.";
        }
        if(!profile.ownsSleeve(sleeve))
        {
            return "You do not own those sleeves.";
        }
        deck.setSleeve(sleeve);
        return null;
    }

    /** Chooses an owned deck case for a deck. */
    public static String setDeckBox(ServerPlayer player, String name, String boxName)
    {
        DeckBoxStyle box = DeckBoxStyle.known(boxName);
        if(box == null)
        {
            return "There is no such deck box.";
        }
        return setDeckBox(DuelProfiles.get(player), name, box);
    }

    static String setDeckBox(DuelProfile profile, String name, DeckBoxStyle box)
    {
        DeckList deck = profile.deckNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        if(box == null)
        {
            return "There is no such deck box.";
        }
        if(!profile.ownsDeckBox(box))
        {
            return "You do not own that deck box.";
        }
        deck.setDeckBox(box);
        return null;
    }

    /**
     * Builds a deck to one of the lists this server offers.
     * <p>
     * The id is checked against {@link Banlists} rather than taken on trust, for
     * the reason {@link #setDeckSleeve} gives about ids off the wire: a lenient
     * codec forgives an old save, but a client naming a list that does not exist
     * is asserting something, and what it would assert here is a set of copy
     * limits. An unknown id is refused rather than silently becoming "no list",
     * because those two answers look identical in the editor and only one of
     * them is what the player asked for.
     * <p>
     * "No list" itself is always accepted: {@link Banlists#all} puts it first
     * and it needs no files on disk, so it is the one id a server without a
     * reference install can still honour.
     */
    public static String setDeckBanlist(ServerPlayer player, String name, String banlistId)
    {
        return setDeckBanlist(DuelProfiles.get(player), name, banlistId);
    }

    /** The rule itself, on a profile rather than a player, so it can be tested. */
    public static String setDeckBanlist(DuelProfile profile, String name, String banlistId)
    {
        DeckList deck = profile.deckNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        String wanted = banlistId == null || banlistId.isBlank()
            ? Banlist.DEFAULT_ID : banlistId;
        // Two ids are always acceptable and are in neither the catalogue nor the
        // files: "no list", which needs nothing to exist, and the default
        // sentinel, which is a question rather than a list.
        if(!Banlist.NO_BANLIST_ID.equals(wanted) && !Banlist.DEFAULT_ID.equals(wanted)
            && Banlists.all().stream().noneMatch(list -> list.id().equals(wanted)))
        {
            return "This server does not have a banlist called \"" + wanted + "\".";
        }
        deck.setBanlistId(wanted);
        return null;
    }

    public static String setDeckDestiny(ServerPlayer player, String name, List<Integer> codes)
    {
        return setDeckDestiny(DuelProfiles.get(player), name, codes);
    }

    /**
     * The rule itself, on a profile rather than a player, so it can be tested.
     *
     * <h2>Only cards that are actually in the main deck</h2>
     * A packet is data, not an instruction. A flag on a card the player does
     * not have would be a flag the engine could never act on -- harmless, but
     * it would also be a way to write arbitrary numbers into somebody's saved
     * profile. Filtering to the main deck is both the check and the correct
     * behaviour: the Extra Deck is never drawn from, so an Extra card cannot be
     * a Destiny Card however it got flagged.
     */
    public static String setDeckDestiny(DuelProfile profile, String name, List<Integer> codes)
    {
        DeckList deck = profile.deckNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        List<Integer> kept = new java.util.ArrayList<>();
        if(codes != null)
        {
            for(Integer code : codes)
            {
                if(code != null && deck.main().contains(code) && !kept.contains(code))
                {
                    kept.add(code);
                }
            }
        }
        deck.setDestiny(kept);
        // The other end of the same question. A mark that leaves the client and
        // does not arrive, and one that arrives and is filtered away, look
        // identical from the deck editor.
        de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny flags for \"" + name
            + "\": " + (codes == null ? 0 : codes.size()) + " arrived, " + kept.size()
            + " kept");
        return null;
    }

    public static String setActive(ServerPlayer player, String name)
    {
        DuelProfile profile = DuelProfiles.get(player);
        DeckList deck = name.isEmpty() ? null : profile.deckNamed(name);
        if(!name.isEmpty() && deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        // Choosing a favourite deck is a preference, not starting a duel.
        // Duel entry performs the legality/ownership check against its actual
        // rules and banlist; blocking the preference here left every in-progress
        // build permanently greyed in the selector.
        profile.setActiveDeck(name);
        return null;
    }

    /**
     * How many cards one player may star. Not a game rule — a bound on what a
     * client can make the server write to disk, now that ownership no longer
     * bounds it.
     */
    public static final int MAX_FAVOURITES = 1024;

    /**
     * Stars a card, or unstars one already starred.
     * <p>
     * Ownership is deliberately NOT required. Requiring it made the star on the
     * card info page do nothing at all: that page is reached by following
     * related cards, which are precisely the cards a player does not have yet,
     * and the star would light up locally and then be undone by the next sync.
     * A favourite is as much a wishlist as a shortcut, so an unowned card may be
     * starred and simply waits in the filter until the player owns it.
     */
    /**
     * Stars a sealed product, or unstars one.
     * <p>
     * Unbounded, where cards are capped at {@code MAX_FAVOURITES}: the cap
     * exists because the trunk holds thousands of cards and a starred list is
     * meant to be a shortlist. There are a few dozen products, so a player who
     * stars all of them has expressed a preference rather than defeated one.
     */
    public static String toggleFavouritePack(ServerPlayer player, String code)
    {
        return toggleFavouritePack(DuelProfiles.get(player), code);
    }

    /** The rule itself, on a profile rather than a player, so it can be tested. */
    public static String toggleFavouritePack(DuelProfile profile, String code)
    {
        if(code == null || code.isEmpty())
        {
            return "That is not a product.";
        }
        profile.toggleFavouritePack(code);
        return null;
    }

    public static String toggleFavourite(ServerPlayer player, int passcode)
    {
        return toggleFavourite(DuelProfiles.get(player), passcode);
    }

    /** The rule itself, on a profile rather than a player, so it can be tested. */
    public static String toggleFavourite(DuelProfile profile, int passcode)
    {
        if(passcode <= 0)
        {
            return "That is not a card.";
        }
        if(!profile.isFavourite(passcode) && profile.favourites().size() >= MAX_FAVOURITES)
        {
            return "You cannot star more than " + MAX_FAVOURITES + " cards.";
        }
        profile.toggleFavourite(passcode);
        return null;
    }

    /**
     * Why this deck will not do under this banlist, empty if it will.
     * <p>
     * Takes the list rather than reaching for one, because the lobby asks about
     * a list nobody has committed to yet: the whole point of showing it is to
     * find out before anyone does.
     */
    public static List<String> problemsUnder(DeckList deck, Trunk trunk, Banlist banlist)
    {
        return problemsUnder(deck, trunk, banlist, false);
    }

    public static List<String> problemsUnder(DeckList deck, Trunk trunk, Banlist banlist,
        boolean freeMode)
    {
        return DeckLimits.validate(deck, trunk, banlist == null ? Banlist.none() : banlist,
            freeMode);
    }

    /**
     * Whether a deck is fit to duel with, as opposed to merely saveable.
     *
     * @return the reasons it is not, empty if it is
     */
    public static List<String> duelReadiness(ServerPlayer player, String name)
    {
        DuelProfile profile = DuelProfiles.get(player);
        DeckList deck = profile.deckNamed(name);
        if(deck == null)
        {
            return List.of("You have no deck called \"" + name + "\".");
        }
        return DeckLimits.validate(deck, profile.trunk(), banlist(), FreeMode.isEnabled(player));
    }

    /** Trims and collapses whitespace, so " " is not a deck name. */
    private static String clean(String name)
    {
        return name == null ? "" : name.trim();
    }

    /**
     * Why this draft may not be stored, or null if it may be.
     * <p>
     * Ownership is intentionally absent here. It is a duel-readiness rule,
     * enforced by {@link #duelReadiness} and the actual duel entry points.
     */
    public static String refusalFor(Trunk trunk, DeckList deck, Banlist banlist)
    {
        return refusalFor(trunk, deck, banlist, false);
    }

    /** Kept as an overload for callers that also know the current mode. */
    public static String refusalFor(Trunk trunk, DeckList deck, Banlist banlist, boolean freeMode)
    {
        for(DeckList.Part part : DeckList.Part.values())
        {
            if(deck.partFor(part).size() > part.capacity())
            {
                return part + " exceeds its " + part.capacity() + " card capacity.";
            }
        }
        for(Map.Entry<Integer, Integer> entry : deck.counts().entrySet())
        {
            int used = entry.getValue();
            int allowed = DeckLimits.maxCopies(entry.getKey(), trunk, banlist, true);
            if(used > allowed)
            {
                return "Card " + entry.getKey() + " is limited to " + allowed + " copies.";
            }
        }
        return null;
    }
}
