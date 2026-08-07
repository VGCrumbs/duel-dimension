package de.cas_ual_ty.dueldimension;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Duel sound effects, converted from Project Ignis: EDOPro's own set so a
 * duel sounds like the reference client. Attribution for each clip is kept
 * verbatim beside the files in
 * {@code assets/dueldimension/sounds/duel/EDOPRO_SOUND_CREDITS.md} — they are
 * a mix of YGOPro Percy effects and Creative Commons recordings, several of
 * which require attribution.
 * <p>
 * The fields are the sound events themselves rather than {@code RegistryObject}
 * wrappers: Fabric registers eagerly, so there is nothing to wait for.
 */
public final class DdSounds
{
    public static final SoundEvent SUMMON = register("duel.summon");
    public static final SoundEvent SPECIAL_SUMMON = register("duel.specialsummon");
    public static final SoundEvent SET = register("duel.set");
    public static final SoundEvent ACTIVATE = register("duel.activate");
    public static final SoundEvent ATTACK = register("duel.attack");
    public static final SoundEvent DAMAGE = register("duel.damage");
    public static final SoundEvent DESTROYED = register("duel.destroyed");
    public static final SoundEvent DRAW = register("duel.draw");
    public static final SoundEvent FLIP = register("duel.flip");
    public static final SoundEvent GAIN_LP = register("duel.gainlp");
    public static final SoundEvent NEXT_TURN = register("duel.nextturn");
    public static final SoundEvent PHASE = register("duel.phase");
    public static final SoundEvent SHUFFLE = register("duel.shuffle");
    public static final SoundEvent ADD_COUNTER = register("duel.addcounter");
    public static final SoundEvent COIN_FLIP = register("duel.coinflip");
    public static final SoundEvent DICE_ROLL = register("duel.diceroll");
    public static final SoundEvent EQUIP = register("duel.equip");

    private DdSounds()
    {
    }

    /**
     * A sound with the default falloff.
     * <p>
     * {@code new SoundEvent(location)} became a factory, and the choice it asks
     * for is the one Forge made silently: variable range, meaning the sound
     * carries as far as whoever plays it says. Every one of these is played at
     * the duel, so that is the right one.
     */
    private static SoundEvent register(String name)
    {
        Identifier id = Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
            SoundEvent.createVariableRangeEvent(id));
    }

    /** Touching this class registers everything in it. */
    public static void register()
    {
    }
}
