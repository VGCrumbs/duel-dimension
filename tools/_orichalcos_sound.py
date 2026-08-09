"""Register the two seal sounds and play them with the animation.

magic_buzz is loudest at its start and has decayed to silence by six seconds,
which is exactly the seal's expansion -- so it plays as the seal closes in and
runs out as the seal lands. It was trimmed to 6.000s, cutting only the silent
tail.

magic_fade is 8.098s, so the visual fade moves from 160 ticks to 162 to match
it. That is the "time the fade to the sound" part: the beam and the seal now go
out on the sound's last breath rather than 0.1s before it.
"""
import io, json, collections

ASSETS = "src/main/resources/assets/dueldimension"

# ---------- sounds.json ----------
p = ASSETS + "/sounds.json"
d = json.loads(io.open(p, encoding="utf-8").read(),
               object_pairs_hook=collections.OrderedDict)
for key, path in (("duel.orichalcos.buzz", "duel/orichalcos/buzz"),
                  ("duel.orichalcos.fade", "duel/orichalcos/fade")):
    d[key] = collections.OrderedDict([
        ("category", "player"),
        ("sounds", [collections.OrderedDict([("name", "dueldimension:" + path),
                                             ("stream", False)])]),
    ])
io.open(p, "w", encoding="utf-8", newline="\n").write(
    json.dumps(d, indent=2, ensure_ascii=False) + "\n")
print("sounds.json:", len(d), "entries")

# ---------- DdSounds ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/DdSounds.java"
s = io.open(p, encoding="utf-8").read()
anchor = '    public static final SoundEvent PHASE = register("duel.phase");'
add = anchor + """

    /** The seal closing in: loud at once, decaying across the six seconds. */
    public static final SoundEvent ORICHALCOS_BUZZ = register("duel.orichalcos.buzz");
    /** The soul going up, and the eight seconds the beam takes to go out with it. */
    public static final SoundEvent ORICHALCOS_FADE = register("duel.orichalcos.fade");"""
assert anchor in s, "DdSounds anchor"
assert "ORICHALCOS_BUZZ" not in s, "already registered"
io.open(p, "w", encoding="utf-8", newline="\n").write(s.replace(anchor, add, 1))
print("DdSounds: two events registered")

# ---------- timings + playback ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/duel/orichalcos/OrichalcosSouls.java"
s = io.open(p, encoding="utf-8").read()

old = """    /** How long the beam lingers after the kill. Eight seconds. */
    public static final int FADE_TICKS = 160;"""
new = """    /**
     * How long the beam lingers after the kill.
     * <p>
     * 162 rather than a round 160 because it is timed to the sound: the fade
     * clip runs 8.098 seconds, and the beam and seal are meant to go out on its
     * last breath rather than a tenth of a second before it.
     */
    public static final int FADE_TICKS = 162;"""
assert old in s, "FADE_TICKS anchor"
s = s.replace(old, new, 1)

old = """    private static void begin(LivingEntity target)
    {
        DuelDimension.log("Orichalcos: the seal closes on " + target.getName().getString());
"""
new = """    private static void begin(LivingEntity target)
    {
        DuelDimension.log("Orichalcos: the seal closes on " + target.getName().getString());

        // The buzz runs the length of the expansion -- it is loudest as the
        // seal appears and has decayed to nothing by the time it lands.
        playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_BUZZ);
"""
assert old in s, "begin anchor"
s = s.replace(old, new, 1)

old = """        Holder<DamageType> type = level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ORICHALCOS);"""
new = """        // With the beam, not after it: this is the sound of the soul leaving,
        // and it lasts exactly as long as the beam takes to fade.
        playAt(target, de.cas_ual_ty.dueldimension.DdSounds.ORICHALCOS_FADE);

        Holder<DamageType> type = level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ORICHALCOS);"""
assert old in s, "kill anchor"
s = s.replace(old, new, 1)

old = """    /**
     * One corner"""
# (not in this file -- the helper goes at the end instead)
anchor = """    /**
     * The kill."""
add = """    /**
     * Plays a sound where the seal is, for everyone near enough to see it.
     * <p>
     * Both clips are mono, which is what makes them positional: this version
     * only applies 3D attenuation and panning to mono audio, and a stereo clip
     * would play at the same volume however far away you stood.
     */
    private static void playAt(LivingEntity target, net.minecraft.sounds.SoundEvent sound)
    {
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
            sound, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1F);
    }

""" + anchor
assert anchor in s, "kill javadoc anchor"
s = s.replace(anchor, add, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("OrichalcosSouls: FADE_TICKS 160 -> 162, both sounds played")
