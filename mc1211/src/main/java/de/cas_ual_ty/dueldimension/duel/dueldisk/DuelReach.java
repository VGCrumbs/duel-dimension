package de.cas_ual_ty.dueldimension.duel.dueldisk;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * How far a duellist can call somebody out from.
 * <p>
 * Challenging is a right-click on the person you want to duel, and vanilla only
 * sends that click at all when the target is inside the player's interaction
 * range -- about three blocks. Shouting a challenge across a courtyard is the
 * normal way this happens, so the range goes up while a duel disk is on.
 * <p>
 * <b>Done with the game's own attribute rather than a bespoke raycast.</b>
 * {@code ENTITY_INTERACTION_RANGE} is what the client uses to decide what it is
 * pointing at AND what the server uses to decide whether to believe the
 * resulting packet, so raising it moves both together and keeps the server
 * authoritative. A hand-rolled "challenge that player over there" message would
 * have had to re-derive the second half, and would have been a new way to reach
 * across the world if it got it wrong.
 * <p>
 * The side effect is real and worth stating: while the disk is on, every entity
 * interaction reaches further, so a duellist can also mount a horse or trade
 * with a villager from further away than usual. That is the price of using the
 * mechanism the game already has, and it lasts only as long as the disk is worn.
 */
public final class DuelReach
{
    private DuelReach()
    {
    }

    /** How far a challenge carries, in blocks. */
    public static final double CHALLENGE_RANGE = 20D;

    private static final ResourceLocation MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "duel_disk_reach");

    private static final Holder<Attribute> RANGE = Attributes.ENTITY_INTERACTION_RANGE;

    /**
     * Keeps every player's reach in step with whether they are wearing a disk.
     * <p>
     * Reconciled each tick rather than applied when the disk goes on, because
     * the disk can also arrive by respawning, by changing dimension, or by a
     * profile sync -- and a modifier that leaks is a player who keeps the extra
     * reach after taking the disk off. Asking is cheap: a map lookup and a
     * boolean per online player.
     */
    public static void tick(MinecraftServer server)
    {
        for(ServerPlayer player : server.getPlayerList().getPlayers())
        {
            apply(player, WornDisks.isWearing(player));
        }
    }

    /** Adds or removes the extra reach, doing nothing when it is already right. */
    public static void apply(ServerPlayer player, boolean wearing)
    {
        AttributeInstance instance = player.getAttribute(RANGE);
        if(instance == null)
        {
            return;
        }
        AttributeModifier present = instance.getModifier(MODIFIER_ID);
        if(wearing == (present != null))
        {
            return;
        }
        if(wearing)
        {
            // Transient: it lives on the live entity and is never written to
            // the player's save data, so a disk taken off while the server is
            // down cannot leave the reach behind.
            instance.addTransientModifier(new AttributeModifier(MODIFIER_ID,
                CHALLENGE_RANGE - instance.getBaseValue(),
                AttributeModifier.Operation.ADD_VALUE));
        }
        else
        {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
