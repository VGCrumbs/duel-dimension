package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.duel.dueldisk.WornDisks;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.deck.StructureDecks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A duelling opponent you set down and pick back up, and tell which deck to
 * play.
 * <p>
 * The difference from {@link DuelistEntity}, which it otherwise is: a duelist is
 * a character with a deck of its own, chosen by its profile id, and it wanders
 * unless told not to. A bot is furniture. It has no character, it never moves,
 * and its deck is whatever the last player to challenge it asked for -- so one
 * bot in a corner can be a Blue-Eyes deck this duel and the player's own build
 * the next.
 * <p>
 * <b>The disk decides what a click means.</b> Right-clicking without a duel disk
 * active picks the bot up; with one active it asks which program to run. That is
 * the same gesture doing two things, which is normally a bad idea -- but the
 * disk is already the mod's verb for "I am here to duel", so a player wearing
 * one is unambiguously not trying to tidy up, and a player not wearing one is
 * unambiguously not trying to start a duel.
 */
public class DuelBotEntity extends DuelistEntity
{
    /**
     * Which of the three programs is loaded, and which deck within it.
     * <p>
     * Synched because the name is drawn over the bot's head; a client that had
     * to ask the server what a bot was holding would show the wrong deck for a
     * tick every time one changed.
     */
    private static final EntityDataAccessor<String> PROGRAM =
        SynchedEntityData.defineId(DuelBotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DECK =
        SynchedEntityData.defineId(DuelBotEntity.class, EntityDataSerializers.STRING);
    /**
     * Whether a duel against this bot is played with Destiny Draws.
     * <p>
     * Carried by the BOT rather than asked for when the duel starts, because
     * the duel can start in more than one way -- walking up to it, or both
     * players already standing on an arena's marks -- and only one of those
     * goes through a screen. Stored when the program is chosen, which is the
     * one moment the player is definitely being asked something.
     */
    private static final EntityDataAccessor<Boolean> DESTINY =
        SynchedEntityData.defineId(DuelBotEntity.class, EntityDataSerializers.BOOLEAN);

    /** The three programs, as the prompt names them. */
    public static final String STARTER = "starter";
    public static final String STRUCTURE = "structure";
    public static final String CUSTOM = "custom";

    public DuelBotEntity(EntityType<? extends PathfinderMob> type, Level level)
    {
        super(type, level);
        // Furniture. A bot that despawned would take the deck someone chose for
        // it with it, and it was placed by hand in the first place.
        setPersistenceRequired();
        setStationary(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(PROGRAM, STARTER);
        // ON, which is what a bot that was never asked should do: practising
        // against your own Destiny Cards is most of the reason to choose any.
        builder.define(DESTINY, true);
        builder.define(DECK, StarterDecks.YUGI.id());
    }

    /**
     * Never. {@link DuelistEntity} gates its stroll on a flag that can be
     * cleared; this refuses at the source, because a bot that wandered would be
     * a bot somebody has to go and find.
     */
    @Override
    public void setStationary(boolean value)
    {
        super.setStationary(true);
    }

    public String getProgram()
    {
        return entityData.get(PROGRAM);
    }

    public String getDeckId()
    {
        return entityData.get(DECK);
    }

    /** Whether this bot duels with Destiny Draws. */
    public boolean destinyDraw()
    {
        return entityData.get(DESTINY);
    }

    public void setDestinyDraw(boolean value)
    {
        entityData.set(DESTINY, value);
    }

    public void setProgram(String program, String deckId)
    {
        entityData.set(PROGRAM, program == null ? STARTER : program);
        entityData.set(DECK, deckId == null ? "" : deckId);
    }

    /**
     * The deck this bot plays.
     * <p>
     * A custom deck is resolved from the profile of the player who chose it,
     * which is why the chooser's uuid is carried rather than a copy of the
     * cards: a deck the player then edits should be the deck the bot plays next
     * time, and a snapshot would quietly go stale.
     */
    @Override
    public HeadlessDuelRunner.Deck npcDeck(ServerPlayer challenger)
    {
        String id = getDeckId();
        if(STRUCTURE.equals(getProgram()))
        {
            StructureDecks.Entry entry = StructureDecks.byId(id);
            if(entry != null)
            {
                return entry.load().toRunnerDeck();
            }
        }
        else if(CUSTOM.equals(getProgram()) && challenger != null)
        {
            HeadlessDuelRunner.Deck own = DuelBotPrograms.customDeck(challenger, id);
            if(own != null)
            {
                return own;
            }
        }
        else
        {
            StarterDecks.Entry entry = StarterDecks.find(id);
            if(entry != null)
            {
                return entry.load().toRunnerDeck();
            }
        }
        // A program that no longer resolves -- a custom deck that was deleted,
        // a structure deck removed from the database -- still has to duel with
        // something rather than fail at the table.
        return StarterDecks.YUGI.load().toRunnerDeck();
    }

    @Override
    public String npcDeckName(ServerPlayer challenger)
    {
        return programName();
    }

    /** What the chosen program is called, for the message that announces it. */
    public String programName()
    {
        String id = getDeckId();
        if(STRUCTURE.equals(getProgram()))
        {
            StructureDecks.Entry entry = StructureDecks.byId(id);
            return entry == null ? "Structure Deck" : entry.displayName();
        }
        if(CUSTOM.equals(getProgram()))
        {
            return id == null || id.isBlank() ? "Custom Deck" : id;
        }
        StarterDecks.Entry entry = StarterDecks.find(id);
        return entry == null ? "Starter Deck" : entry.displayName();
    }


    /**
     * One hit point, and it never fights back.
     * <p>
     * A bot is a placed object, so removing one should be as ordinary as
     * breaking anything else you put down -- and it comes back as the item it
     * was placed from, so nothing is lost by doing it.
     */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder
        createBotAttributes()
    {
        return net.minecraft.world.entity.Mob.createMobAttributes()
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 1)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 16);
    }

    /**
     * It watches, but it does not turn.
     * <p>
     * The look goal is kept and the stroll is not: a bot tracking you with its
     * head reads as attentive, while a bot swivelling its whole body to follow
     * a passer-by reads as alive, which it is not. The body stays exactly as it
     * was placed -- see {@link #createBodyControl} and {@link #getMaxHeadYRot}.
     */
    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(
            this, Player.class, 10F));
    }

    /**
     * The head may turn all the way without the body being dragged after it.
     * <p>
     * Vanilla clamps head yaw to this and then rotates the BODY to make up the
     * difference, which is exactly the behaviour a fixed bot must not have.
     * Raising the limit removes the reason the body would ever turn.
     */
    @Override
    public int getMaxHeadYRot()
    {
        return 180;
    }

    /**
     * The body rotation control, doing nothing.
     * <p>
     * <b>Stopping the turn beats undoing it.</b> This used to pin the body in
     * {@code aiStep}: let {@code super.aiStep()} run the control, then write
     * {@code yBodyRot} and {@code yBodyRotO} back to the placed angle. It
     * worked in the sense that the bot ended each tick facing the right way,
     * and it looked terrible -- the control turns the body a little towards the
     * head every tick and the pin threw it back, so the two fought, once per
     * tick, forever. Forcing the PREVIOUS angle as well is what made it visible
     * rather than merely wasteful: the renderer interpolates between
     * {@code yBodyRotO} and {@code yBodyRot} for the partial tick, so rewriting
     * both mid-fight gave it two different stories about where the body was and
     * the bot shook in place.
     * <p>
     * A control that never turns the body has nothing to undo. Nothing else
     * writes {@code yBodyRot} for a mob, so the angle it was placed at is
     * simply the angle it keeps, and no per-tick correction is needed at all.
     * The head still turns, by {@link #getMaxHeadYRot}.
     */
    @Override
    protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl()
    {
        return new net.minecraft.world.entity.ai.control.BodyRotationControl(this)
        {
            @Override
            public void clientTick()
            {
            }
        };
    }

    /** Declined, for the reason {@link #registerGoals} gives. */
    @Override
    protected void faceOpponent()
    {
    }

    /**
     * Breakable, unlike a duelist.
     * <p>
     * {@link DuelistEntity} bounces everything off, because a duelist is a
     * person who settles things with cards. A bot is equipment, and the way you
     * pick equipment up when your hands are full is to knock it over.
     */
    @Override
    protected boolean damageAllowed(net.minecraft.world.damagesource.DamageSource source)
    {
        return true;
    }

    /**
     * Dies back into the item it was placed from, program and all.
     * <p>
     * Dropped rather than granted, because unlike the pick-up gesture this one
     * can happen with nobody nearby -- a stray arrow, a creeper, a mob that
     * wandered in. The thing that removed it may not have hands.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source)
    {
        if(!level().isClientSide())
        {
            net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(DdItems.DUEL_BOT);
            DuelBotItem.storeProgram(stack, getProgram(), getDeckId());
            spawnAtLocation(stack);
        }
        super.die(source);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if(level().isClientSide() || hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.SUCCESS;
        }
        if(!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }
        // The placer is still the placer: it must not be shadowed by pickup.
        if(player.getItemInHand(hand).getItem() instanceof DuelistPlacerItem)
        {
            return InteractionResult.PASS;
        }

        // Not mid-duel. The duel is driven by the entity, so pocketing one
        // that is playing would leave a duel running with nothing standing at
        // the other end of it -- and the gesture is a tidy-up, which is never
        // urgent. Asked of the duel registry rather than a flag on the entity,
        // for the reason registerGoals gives.
        if(DuelistDuels.isDueling(getUUID()))
        {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component
                .literal("That Duel Bot is in the middle of a duel.")
                .withStyle(net.minecraft.ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        if(WornDisks.active(serverPlayer) == null)
        {
            return pickUp(serverPlayer);
        }
        DuelBotPrograms.offer(serverPlayer, this);
        return InteractionResult.CONSUME;
    }

    /**
     * Back into an item, carrying its program with it.
     * <p>
     * Given rather than dropped where possible: a bot picked up on a cliff edge
     * that fell off it would be a bot lost, and the gesture that removed it was
     * a deliberate one.
     */
    private InteractionResult pickUp(ServerPlayer player)
    {
        ItemStack stack = new ItemStack(DdItems.DUEL_BOT);
        DuelBotItem.storeProgram(stack, getProgram(), getDeckId());
        if(!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag output)
    {
        super.addAdditionalSaveData(output);
        output.putString("Program", getProgram());
        output.putString("Deck", getDeckId());
        output.putBoolean("DestinyDraw", destinyDraw());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag input)
    {
        super.readAdditionalSaveData(input);
        if(input.contains("Program"))
        {
            entityData.set(PROGRAM, input.getString("Program"));
        }
        if(input.contains("Deck"))
        {
            entityData.set(DECK, input.getString("Deck"));
        }
        // Absent reads as ON -- a bot placed before this existed was never
        // asked, and the answer for a bot nobody asked is the default.
        entityData.set(DESTINY, !input.contains("DestinyDraw")
            || input.getBoolean("DestinyDraw"));
    }
}
