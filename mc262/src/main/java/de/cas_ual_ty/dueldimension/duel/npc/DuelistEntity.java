package de.cas_ual_ty.dueldimension.duel.npc;

import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;

/**
 * An NPC you can challenge to a duel. Which duelist it is — name, skin and
 * deck — comes from its profile id, so adding a new opponent is content, not
 * code.
 * <p>
 * The entity itself deliberately knows nothing about the rules engine; it just
 * asks {@link DuelistDuels} to start a duel and reports what comes back.
 */
public class DuelistEntity extends PathfinderMob
{
    private static final EntityDataAccessor<String> PROFILE =
        SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.STRING);

    /**
     * Placed by hand and meant to stay put, rather than a duelist that lives in
     * the world and wanders. Server-side only: the client has no reason to know
     * why a duelist is standing still, only that it is.
     */
    private boolean stationary;

    public DuelistEntity(EntityType<? extends PathfinderMob> type, Level level)
    {
        super(type, level);
        setPersistenceRequired();
    }

    public boolean isStationary()
    {
        return stationary;
    }

    public void setStationary(boolean value)
    {
        stationary = value;
    }

    /** The next starter deck along, wrapping. For testing against several in turn. */
    public void cycleProfile()
    {
        java.util.List<de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks.Entry> all =
            StarterDecks.ALL;
        if(all.isEmpty())
        {
            return;
        }
        int at = 0;
        for(int index = 0; index < all.size(); index++)
        {
            if(all.get(index).id().equals(getProfileId()))
            {
                at = index;
                break;
            }
        }
        setProfileId(all.get((at + 1) % all.size()).id());
    }

    /** What this duelist is called, for a message about it. */
    public String displayName()
    {
        return displayName(getProfileId());
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void registerGoals()
    {
        // A duelist stands around and makes eye contact; it has no business
        // wandering off mid-duel or fighting anyone.
        //
        // The stroll is switched off for the length of a duel. It used to run
        // regardless, so the duellist you were facing would wander away from its
        // own table -- and because the duel is driven by the entity rather than
        // by where it stands, it kept playing from wherever it ended up.
        //
        // Gated by asking the duel registry rather than by a flag on this
        // entity: see DuelistDuels.isDueling for why a flag is the fragile
        // choice. Looking at the player is deliberately NOT gated -- a duelist
        // that keeps eye contact across the table is the point.
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.4)
        {
            @Override
            public boolean canUse()
            {
                return !stationary && !DuelistDuels.isDueling(DuelistEntity.this.getUUID())
                    && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return !stationary && !DuelistDuels.isDueling(DuelistEntity.this.getUUID())
                    && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /**
     * A duelist stands still while duelling.
     * <p>
     * The stroll goal is already gated, but gating goals only stops the movers
     * we know about -- a shove from another mob, a path left over from the tick
     * the duel began on, or any goal added later all move a duelist who is
     * supposed to be standing at a table. This holds the position outright, so
     * "does not wander mid-duel" is one rule in one place rather than a promise
     * every future goal has to remember to keep.
     * <p>
     * Horizontal only: gravity still applies, so a duelist standing on ground
     * that vanishes still falls rather than hanging in the air.
     */
    @Override
    public void aiStep()
    {
        if(!level().isClientSide() && (stationary
            || de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.isDueling(getUUID())))
        {
            getNavigation().stop();
            net.minecraft.world.phys.Vec3 motion = getDeltaMovement();
            setDeltaMovement(0D, motion.y, 0D);
            // Cleared too: a pending "move here" is re-applied by the mover
            // every tick and would fight the line above.
            setZza(0F);
            setXxa(0F);
            faceOpponent();
        }
        super.aiStep();
    }

    /**
     * Turns to face whoever it is duelling.
     * <p>
     * A duelist that has stopped moving otherwise keeps whatever heading it was
     * left on, which for a placed one is wherever it was put down and for a
     * wandering one is wherever it happened to stop. Looking at the person you
     * are duelling is the least a duel deserves, and it costs nothing: the
     * whole body turns, not only the head, so it reads from any angle -- and it
     * matters on the world board, where the duellist is standing across a table
     * looking at them.
     * <p>
     * Asked of the live registry every tick rather than remembered, so a duel
     * that ends by any route leaves the duelist free again.
     */
    /**
     * Overridable so a Duel Bot can decline it. A bot is furniture and stays
     * exactly as it was placed, facing included.
     */
    protected void faceOpponent()
    {
        java.util.UUID opponent = DuelistDuels.opponentOf(getUUID());
        if(opponent == null || !(level() instanceof net.minecraft.server.level.ServerLevel level))
        {
            return;
        }
        net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(opponent);
        if(player == null)
        {
            return;
        }
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        float yaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        // The look control would otherwise spend the next tick turning it back
        // towards whatever it had decided to watch.
        getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
    }

    /**
     * The synched data is declared into a builder now. The map is immutable
     * once the entity is built, which is why {@code entityData.define} could
     * not survive: it mutated the map after the fact.
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(PROFILE, StarterDecks.JOEY.id());
    }

    public String getProfileId()
    {
        return entityData.get(PROFILE);
    }

    public void setProfileId(String profileId)
    {
        entityData.set(PROFILE, profileId);
        if(!level().isClientSide())
        {
            setCustomName(net.minecraft.network.chat.Component.literal(displayName(profileId)));
        }
    }

    private static String displayName(String profileId)
    {
        return StarterDecks.ALL.stream()
            .filter(entry -> entry.id().equals(profileId))
            .map(entry -> entry.displayName().replace("Starter Deck: ", ""))
            .findFirst()
            .orElse(profileId);
    }


    /**
     * The deck this NPC brings to the table.
     * <p>
     * A duelist's deck is its identity -- the profile id picks both the skin and
     * the cards, which is what makes adding an opponent content rather than
     * code. {@link DuelBotEntity} is the exception that needs this to be a
     * question rather than a lookup: a bot has no identity and plays whichever
     * deck it was last told to.
     *
     * @param challenger who asked for the duel, because a bot may be playing a
     *                   deck belonging to them. Null where there is no one to
     *                   ask, which the default ignores.
     */
    public de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner.Deck npcDeck(
        net.minecraft.server.level.ServerPlayer challenger)
    {
        StarterDecks.Entry entry = StarterDecks.find(getProfileId());
        return (entry == null ? StarterDecks.JOEY : entry).load().toRunnerDeck();
    }

    /** What to call that deck in the line announcing the duel. */
    public String npcDeckName(net.minecraft.server.level.ServerPlayer challenger)
    {
        StarterDecks.Entry entry = StarterDecks.find(getProfileId());
        return (entry == null ? StarterDecks.JOEY : entry).displayName();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if(level().isClientSide() || hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.SUCCESS;
        }
        // The placer removes and re-decks duelists; it must not also start a
        // duel with the one it is being pointed at.
        if(player.getItemInHand(hand).getItem()
            instanceof de.cas_ual_ty.dueldimension.duel.npc.DuelistPlacerItem)
        {
            return InteractionResult.PASS;
        }
        // The click ASKS; it does not decide. A duel against a duelist starts
        // the instant it is asked for and there is no lobby to agree anything
        // in, so where it is played has to be chosen here -- and chosen before
        // the duel begins, because once the engine is running the presentation
        // is settled. Sneaking used to be the shortcut for it, which is not the
        // same as being offered a choice.
        if(player instanceof net.minecraft.server.level.ServerPlayer challenger)
        {
            DuelistChallenge.offer(challenger, this);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Entity save data goes through {@link ValueOutput} rather than a raw
     * {@code CompoundTag} -- a typed view that cannot be handed the wrong kind
     * of tag, and that carries its own error reporting.
     */
    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putString("Profile", getProfileId());
        output.putBoolean("Stationary", stationary);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        input.getString("Profile").ifPresent(this::setProfileId);
        stationary = input.getBooleanOr("Stationary", false);
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    /**
     * Duelists settle things with cards.
     * <p>
     * {@code hurt} became {@code hurtServer}: damage is decided on the server
     * and the method name now says so, and it is handed the level it is
     * happening in rather than reaching for one.
     * <p>
     * The Seal of Orichalcos is the one exception, and it is the exception that
     * proves the rule: it is not someone attacking a duelist, it is a duel they
     * already lost. Everything else — swords, arrows, lava, their own duel
     * partner — still bounces off, and a creative player can still remove one.
     * Without this the seal closed on a duelist and then did nothing, because a
     * damage source with no attacker behind it is not a creative player.
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
        net.minecraft.world.damagesource.DamageSource source, float amount)
    {
        return damageAllowed(source) && super.hurtServer(level, source, amount);
    }

    /**
     * Whether this damage gets through. Overridable because a Duel Bot is not a
     * duelist in this respect -- it is equipment, and equipment breaks.
     * <p>
     * A hook rather than a second override in the subclass: Java has no way to
     * skip a superclass implementation, so the only way for a subclass to be
     * MORE damageable than its parent is for the parent to ask.
     */
    protected boolean damageAllowed(net.minecraft.world.damagesource.DamageSource source)
    {
        return source.isCreativePlayer()
            || source.is(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.ORICHALCOS);
    }

    @Override
    public boolean canBeLeashed()
    {
        return false;
    }

    public static boolean isDuelist(LivingEntity entity)
    {
        return entity instanceof DuelistEntity;
    }
}
