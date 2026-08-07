package de.cas_ual_ty.dueldimension.duel.profile;

import de.cas_ual_ty.dueldimension.duel.match.Banlist;
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

    /** The list a deck must be legal against. Not yet chosen per duel, so none. */
    private static Banlist banlist()
    {
        return Banlist.none();
    }

    /**
     * Stores a deck's contents, creating it if the player has no deck by that
     * name.
     * <p>
     * The check is ownership, not legality: a deck may be saved half-built or
     * over the limit, because a player mid-edit has not done anything wrong.
     * What they may not do is put in a card they do not own. Whether the deck
     * can be <em>duelled</em> with is asked later, when it is used.
     */
    public static String saveDeck(ServerPlayer player, String name, List<Integer> main,
        List<Integer> extra, List<Integer> side)
    {
        String clean = clean(name);
        if(clean.isEmpty())
        {
            return "A deck needs a name.";
        }

        DuelProfile profile = DuelProfiles.get(player);
        DeckList existing = profile.deckNamed(clean);
        if(existing != null && existing.origin().isGranted())
        {
            // A granted deck is the record of what was opened. Editing it would
            // quietly rewrite history, and the editor offers "use" -- which
            // copies it -- for exactly this reason.
            return "\"" + clean + "\" is a granted deck and cannot be edited. Use it to make a copy.";
        }

        DeckList candidate = new DeckList(clean, DeckList.Origin.SAVED, main, extra, side);
        String refusal = refusalFor(profile.trunk(), candidate, banlist());
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

    public static String createDeck(ServerPlayer player, String name)
    {
        String clean = clean(name);
        if(clean.isEmpty())
        {
            return "A deck needs a name.";
        }
        DuelProfile profile = DuelProfiles.get(player);
        if(profile.deckNamed(clean) != null)
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
        DeckList deck = profile.deckNamed(from);
        if(deck == null)
        {
            return "You have no deck called \"" + from + "\".";
        }
        if(deck.origin().isGranted())
        {
            return "A granted deck keeps its name.";
        }
        if(!from.equals(cleanTo) && profile.deckNamed(cleanTo) != null)
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

    public static String deleteDeck(ServerPlayer player, String name)
    {
        DuelProfile profile = DuelProfiles.get(player);
        DeckList deck = profile.deckNamed(name);
        if(deck == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        if(deck.origin().isGranted())
        {
            return "A granted deck cannot be deleted.";
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
        if(profile.deckNamed(clean) != null)
        {
            return "You already have a deck called \"" + clean + "\".";
        }
        if(profile.deckNamed(recipe) == null)
        {
            return "You have no recipe called \"" + recipe + "\".";
        }
        return profile.copyAsRecipe(recipe, clean) == null
            ? "That recipe could not be copied." : null;
    }

    public static String setActive(ServerPlayer player, String name)
    {
        DuelProfile profile = DuelProfiles.get(player);
        if(!name.isEmpty() && profile.deckNamed(name) == null)
        {
            return "You have no deck called \"" + name + "\".";
        }
        profile.setActiveDeck(name);
        return null;
    }

    /**
     * Stars a card, or unstars one already starred.
     * <p>
     * A player may only star a card they own. The list is meant to be a
     * shortcut into their own collection, so a favourite they cannot see would
     * be a filter that hides everything.
     */
    public static String toggleFavourite(ServerPlayer player, int passcode)
    {
        DuelProfile profile = DuelProfiles.get(player);
        if(!profile.trunk().has(passcode) && !profile.isFavourite(passcode))
        {
            return "You do not own that card.";
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
        return DeckLimits.validate(deck, trunk, banlist == null ? Banlist.none() : banlist);
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
        return DeckLimits.validate(deck, profile.trunk(), banlist());
    }

    /** Trims and collapses whitespace, so " " is not a deck name. */
    private static String clean(String name)
    {
        return name == null ? "" : name.trim();
    }

    /**
     * Why this deck may not be stored against this collection, or null if it
     * may be.
     * <p>
     * {@link DeckLimits#maxCopies} already caps at what the trunk holds, so a
     * card owned zero times allows zero copies and this one check covers both
     * "you do not have that card" and "you do not have that many".
     * <p>
     * Pure, and public for the same reason {@code DuelPoints.canAfford} is: it
     * is the line between a player and cards they did not earn, so it is worth
     * being able to check it directly rather than only through a live server.
     */
    public static String refusalFor(Trunk trunk, DeckList deck, Banlist banlist)
    {
        for(Map.Entry<Integer, Integer> entry : deck.counts().entrySet())
        {
            int used = entry.getValue();
            int allowed = DeckLimits.maxCopies(entry.getKey(), trunk, banlist);
            if(used > allowed)
            {
                int owned = trunk.countOf(entry.getKey());
                return used > owned
                    ? "That deck uses " + used + " of card " + entry.getKey()
                        + " but you own " + owned + "."
                    : "Card " + entry.getKey() + " is limited to " + allowed + " copies.";
            }
        }
        return null;
    }
}
