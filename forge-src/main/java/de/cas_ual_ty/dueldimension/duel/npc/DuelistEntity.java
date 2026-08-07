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

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        entityData.define(PROFILE, StarterDecks.JOEY.id());
    }

    public String getProfileId()
    {
        return entityData.get(PROFILE);
    }

    public void setProfileId(String profileId)
    {
        entityData.set(PROFILE, profileId);
        if(!level.isClientSide)
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
        if(level.isClientSide || hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        DuelistDuels.challenge(this, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString("Profile", getProfileId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if(tag.contains("Profile"))
        {
            setProfileId(tag.getString("Profile"));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount)
    {
        // Duelists settle things with cards.
        return source.isCreativePlayer() && super.hurt(source, amount);
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    public static boolean isDuelist(LivingEntity entity)
    {
        return entity instanceof DuelistEntity;
    }
}
