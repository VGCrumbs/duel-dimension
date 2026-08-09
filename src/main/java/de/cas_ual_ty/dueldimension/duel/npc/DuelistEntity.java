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

    public DuelistEntity(EntityType<? extends PathfinderMob> type, Level level)
    {
        super(type, level);
        setPersistenceRequired();
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
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
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

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if(level().isClientSide() || hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.SUCCESS;
        }
        DuelistDuels.challenge(this, player);
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        input.getString("Profile").ifPresent(this::setProfileId);
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
        boolean allowed = source.isCreativePlayer()
            || source.is(de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.ORICHALCOS);
        return allowed && super.hurtServer(level, source, amount);
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
