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
    /**
     * The coin toss and the die roll, from Master Duel.
     * <p>
     * Five sounds under eight names in the source: the game calls the opening
     * toss `SE_DUEL_ENTRY_COIN*` and the in-duel coin `SE_COIN_*`, and the two
     * sets point at the same three resource offsets. Deduplicated here, since
     * shipping one sound twice is only a bigger jar.
     * <p>
     * Each prop has a MOVEMENT sound and a SETTLING one, which is why the
     * results are separate clips rather than the tail of the toss: the coin
     * does not know which face it landed on until it lands.
     * <p>
     * {@code DIE_} rather than {@code DICE_}, because {@link #DICE_ROLL} is
     * already taken by EDOPro's own dice sound -- these two sets are different
     * recordings for different places and both are kept.
     */
    public static final SoundEvent COIN_THROW = register("ui.coin_throw");
    public static final SoundEvent COIN_HEADS = register("ui.coin_heads");
    public static final SoundEvent COIN_TAILS = register("ui.coin_tails");
    public static final SoundEvent DIE_ROLL = register("ui.dice_roll");
    public static final SoundEvent DIE_SETTLE = register("ui.dice_decide");

    /** A trade going through, played to both sides at the same moment. */
    public static final SoundEvent TRADE_SUCCESS = register("ui.trade_success");

    public static final SoundEvent SUMMON = register("duel.summon");
    public static final SoundEvent SPECIAL_SUMMON = register("duel.specialsummon");
    public static final SoundEvent SET = register("duel.set");
    public static final SoundEvent ACTIVATE = register("duel.activate");
    public static final SoundEvent ATTACK = register("duel.attack");
    public static final SoundEvent DAMAGE = register("duel.damage");
    public static final SoundEvent DESTROYED = register("duel.destroyed");
    /** A monster given up as a tribute, which does not break. */
    public static final SoundEvent TRIBUTE = register("duel.tribute");
    public static final SoundEvent DRAW = register("duel.draw");
    public static final SoundEvent FLIP = register("duel.flip");
    public static final SoundEvent GAIN_LP = register("duel.gainlp");
    public static final SoundEvent NEXT_TURN = register("duel.nextturn");
    public static final SoundEvent PHASE = register("duel.phase");

    /** The seal closing in: loud at once, decaying across the six seconds. */
    public static final SoundEvent ORICHALCOS_BUZZ = register("duel.orichalcos.buzz");
    /** The soul going up, and the eight seconds the beam takes to go out with it. */
    public static final SoundEvent ORICHALCOS_FADE = register("duel.orichalcos.fade");
    public static final SoundEvent SHUFFLE = register("duel.shuffle");
    public static final SoundEvent ADD_COUNTER = register("duel.addcounter");
    public static final SoundEvent COIN_FLIP = register("duel.coinflip");
    public static final SoundEvent DICE_ROLL = register("duel.diceroll");
    public static final SoundEvent EQUIP = register("duel.equip");

    /**
     * Pressing a phase on the phase bar.
     * <p>
     * Distinct from {@link #PHASE}, which is the engine announcing that the
     * phase HAS changed (MSG_NEW_PHASE). This one is the button answering the
     * press, in place of Minecraft's own click.
     */
    public static final SoundEvent PHASE_CHANGE = register("duel.phasechange");

    /**
     * Duel music. One entry per selectable track; {@code DuelMusic} is what
     * decides which of them plays and remembers the choice.
     */
    /**
     * The reward screen's music: jngl_win2, id 0x11D on the disc.
     * <p>
     * Confirmed by reading the running game's memory while that screen was up,
     * NOT inferred from the filename -- the name-based guess was sys_select and
     * it was wrong.
     */
    public static final SoundEvent STATUE_MUSIC = register("statue.music");

    /**
     * The reward music's INTRO, 0 to 14.762 s of jngl_win2.
     * <p>
     * The disc's track is 41.4 s with its loop marked at 14.762..29.531 s, so
     * nearly fifteen seconds of it play before the loop is ever reached. Only
     * the loop body used to ship, which meant the screen opened partway through
     * a phrase -- audibly abrupt against the original.
     */
    public static final SoundEvent STATUE_MUSIC_INTRO = register("statue.music_intro");

    /**
     * The reward screen's carousel step.
     * <p>
     * <b>Which clip this is has not been established.</b> The rotate handler at
     * VA 0x00100490 does not pass a sound id as an immediate -- it comes from a
     * member field -- and the 160 clips in Static.xau are numbered, not named,
     * so there is nothing to match on. se_154 is chosen on DURATION: at 0.20s it
     * is the second shortest clip on the disc, which is the length a menu blip
     * is. The near neighbours if this one is wrong are se_134 (0.10s) and
     * se_005 (0.32s); swapping is a one-file copy into
     * assets/dueldimension/sounds/statue/rotate.ogg.
     */
    public static final SoundEvent STATUE_ROTATE = register("statue.rotate");

    /**
     * One reward card landing.
     * <p>
     * Chosen on duration the same way {@link #STATUE_ROTATE} was, and with the
     * same caveat: the clips are numbered rather than named and the reveal's own
     * sound id is not in the code as an immediate. se_005 is 0.32s, long enough
     * to read as a card being placed rather than a cursor blip.
     */
    public static final SoundEvent STATUE_CARD = register("statue.card");

    public static final SoundEvent MUSIC_NORMAL = register("duel.music.normal");
    public static final SoundEvent MUSIC_SOMETHING_EVIL =
        register("duel.music.somethingevil");

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
