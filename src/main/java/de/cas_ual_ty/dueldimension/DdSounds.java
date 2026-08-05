package de.cas_ual_ty.dueldimension;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Duel sound effects, converted from Project Ignis: EDOPro's own set so a
 * duel sounds like the reference client. Attribution for each clip is kept
 * verbatim beside the files in
 * {@code assets/dueldimension/sounds/duel/EDOPRO_SOUND_CREDITS.md} — they are
 * a mix of YGOPro Percy effects and Creative Commons recordings, several of
 * which require attribution.
 */
public class DdSounds
{
    private static final DeferredRegister<SoundEvent> DEFERRED_REGISTER =
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DuelDimension.MOD_ID);

    public static final RegistryObject<SoundEvent> SUMMON = register("duel.summon");
    public static final RegistryObject<SoundEvent> SPECIAL_SUMMON = register("duel.specialsummon");
    public static final RegistryObject<SoundEvent> SET = register("duel.set");
    public static final RegistryObject<SoundEvent> ACTIVATE = register("duel.activate");
    public static final RegistryObject<SoundEvent> ATTACK = register("duel.attack");
    public static final RegistryObject<SoundEvent> DAMAGE = register("duel.damage");
    public static final RegistryObject<SoundEvent> DESTROYED = register("duel.destroyed");
    public static final RegistryObject<SoundEvent> DRAW = register("duel.draw");
    public static final RegistryObject<SoundEvent> FLIP = register("duel.flip");
    public static final RegistryObject<SoundEvent> GAIN_LP = register("duel.gainlp");
    public static final RegistryObject<SoundEvent> NEXT_TURN = register("duel.nextturn");
    public static final RegistryObject<SoundEvent> PHASE = register("duel.phase");
    public static final RegistryObject<SoundEvent> SHUFFLE = register("duel.shuffle");
    public static final RegistryObject<SoundEvent> ADD_COUNTER = register("duel.addcounter");
    public static final RegistryObject<SoundEvent> COIN_FLIP = register("duel.coinflip");
    public static final RegistryObject<SoundEvent> DICE_ROLL = register("duel.diceroll");
    public static final RegistryObject<SoundEvent> EQUIP = register("duel.equip");

    private static RegistryObject<SoundEvent> register(String name)
    {
        return DEFERRED_REGISTER.register(name,
            () -> new SoundEvent(new ResourceLocation(DuelDimension.MOD_ID, name)));
    }

    public static void register(IEventBus bus)
    {
        DEFERRED_REGISTER.register(bus);
    }
}
