package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.duel.dueldisk.DuelReach;
import de.cas_ual_ty.dueldimension.duel.profile.DeckList;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfile;
import de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.deck.StructureDecks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Which deck a Duel Bot is running, and choosing one.
 * <p>
 * Two questions get asked in a row and they are different questions: this one
 * settles WHAT the bot plays, and {@link DuelistChallenge} then settles WHERE
 * the duel happens. Folding them into one menu was tempting and wrong -- the
 * second question already has a shortcut (two duellists standing on a built
 * arena's marks have answered it by walking there), and a combined menu would
 * have to ask it anyway.
 * <p>
 * <b>The reply is re-checked, never trusted.</b> A client sends an entity id, a
 * program name and a deck name. This looks the entity up, confirms it is a bot,
 * confirms it is still in reach, and confirms the deck name against the
 * player's OWN profile -- so a crafted packet cannot make a bot play a deck
 * belonging to somebody else.
 */
public final class DuelBotPrograms
{
    private DuelBotPrograms()
    {
    }

    /** As {@link DuelistChallenge}, for the same reason: menus take time. */
    private static final double REPLY_SLACK = 4D;

    /** Asks which program to run. */
    public static void offer(ServerPlayer player, DuelBotEntity bot)
    {
        ServerPlayNetworking.send(player, new DuelBotMessages.OfferProgram(
            bot.getId(), bot.displayName(), ownDeckNames(player)));
    }

    /** The challenger's own builds, by name, for the custom-deck list. */
    public static List<String> ownDeckNames(ServerPlayer player)
    {
        DuelProfile profile = DuelProfiles.get(player);
        List<String> names = new ArrayList<>();
        for(DeckList deck : profile.ownDecks())
        {
            names.add(deck.name());
        }
        return names;
    }

    /**
     * One of the player's decks as the engine wants it.
     * <p>
     * Resolved fresh at the table rather than copied when it was chosen, so a
     * deck edited between duels is the deck the bot actually plays. Returns null
     * when the name no longer names anything, which is the caller's cue to fall
     * back rather than fail.
     */
    public static HeadlessDuelRunner.Deck customDeck(ServerPlayer player, String name)
    {
        if(name == null || name.isBlank())
        {
            return null;
        }
        DeckList chosen = DuelProfiles.get(player).deckNamed(name);
        if(chosen == null)
        {
            return null;
        }
        // The artwork each copy wears travels with the deck order, exactly as
        // it does for a player's own deck -- see DuelistDuels.deckFor. Past
        // here a deck is a list of passcodes and one Dark Magician is every
        // other Dark Magician.
        return new HeadlessDuelRunner.Deck(chosen.main(), chosen.extra())
            .wearing(chosen.artsFor(chosen.main()), chosen.artsFor(chosen.extra()));
    }

    /**
     * Loads the chosen program, then asks the other question.
     * <p>
     * An empty deck id from the starter or structure buttons means "you pick" --
     * those buttons choose a program, not a specific list, so the server takes
     * one from the pool. Which one is announced, because a bot that silently
     * played a different deck each time would read as the same deck behaving
     * inconsistently.
     */
    public static void begin(ServerPlayer player, int botId, String program, String deckId,
        boolean destinyDraw)
    {
        Entity entity = player.level().getEntity(botId);
        if(!(entity instanceof DuelBotEntity bot))
        {
            de.cas_ual_ty.dueldimension.DuelDimension.warn(
                "duel bot reply named entity " + botId + ", which is "
                    + (entity == null ? "not there" : entity.getType().toString()));
            return;
        }
        double reach = DuelReach.CHALLENGE_RANGE + REPLY_SLACK;
        if(player.distanceToSqr(bot) > reach * reach)
        {
            player.sendSystemMessage(Component.literal("That Duel Bot is too far away now")
                .withStyle(ChatFormatting.RED));
            return;
        }

        String resolved = resolve(player, program, deckId);
        if(resolved == null)
        {
            player.sendSystemMessage(Component.literal(
                    "That deck is not one of yours any more.")
                .withStyle(ChatFormatting.RED));
            return;
        }
        bot.setProgram(normalise(program), resolved);
        // Whatever the player last chose, which the client remembers for them.
        bot.setDestinyDraw(destinyDraw);
        de.cas_ual_ty.dueldimension.DuelDimension.log("duel bot program: "
            + player.getGameProfile().name() + " set " + bot.getProgram()
            + "/" + resolved);
        player.sendSystemMessage(Component.literal("Program loaded: ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(bot.programName())
                .withStyle(ChatFormatting.LIGHT_PURPLE)));

        // Now the other question. It answers itself when both are standing on
        // a built arena's marks, which is the case this shortcut exists for.
        DuelistChallenge.offer(player, bot);
    }

    private static String normalise(String program)
    {
        if(DuelBotEntity.STRUCTURE.equals(program) || DuelBotEntity.CUSTOM.equals(program))
        {
            return program;
        }
        return DuelBotEntity.STARTER;
    }

    /**
     * The deck id to store, or null if the client named one it may not have.
     * <p>
     * A custom deck is checked against the player's own profile HERE rather
     * than at the table, because this is the moment the name arrived from
     * outside; by the time the duel starts it is a name the server wrote down.
     */
    private static String resolve(ServerPlayer player, String program, String deckId)
    {
        String kind = normalise(program);
        if(DuelBotEntity.CUSTOM.equals(kind))
        {
            if(deckId == null || deckId.isBlank())
            {
                return null;
            }
            return DuelProfiles.get(player).deckNamed(deckId) == null ? null : deckId;
        }
        if(DuelBotEntity.STRUCTURE.equals(kind))
        {
            if(deckId != null && !deckId.isBlank() && StructureDecks.byId(deckId) != null)
            {
                return deckId;
            }
            List<StructureDecks.Entry> all = StructureDecks.ALL;
            if(all.isEmpty())
            {
                return StarterDecks.YUGI.id();
            }
            return all.get(pick(player, all.size())).id();
        }
        if(deckId != null && !deckId.isBlank() && StarterDecks.find(deckId) != null)
        {
            return deckId;
        }
        List<StarterDecks.Entry> all = StarterDecks.ALL;
        return all.isEmpty() ? StarterDecks.YUGI.id()
            : all.get(pick(player, all.size())).id();
    }

    /**
     * A deck from the pool, varying per player and per moment.
     * <p>
     * Deliberately not {@code Random}: the level's game time and the player's
     * uuid are both to hand and give a different answer each time without a
     * field to own. The same mix DuelistDuels seeds its duels from.
     */
    private static int pick(ServerPlayer player, int size)
    {
        long mix = player.level().getGameTime()
            ^ player.getUUID().getLeastSignificantBits();
        return (int) Math.floorMod(mix, size);
    }
}
