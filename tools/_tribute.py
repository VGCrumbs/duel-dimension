"""A tribute stops being a destruction: its own event, sound and dissolve."""
import io, json, os

ROOT = "C:/Users/Admin/Desktop/YGO/CrumbyDuelingMulti/"


def edit(path, pairs, once=True):
    s = io.open(ROOT + path, encoding="utf-8").read()
    for old, new in pairs:
        assert old in s, "%s: %s" % (path, old[:60])
        s = s.replace(old, new, 1 if once else -1)
    io.open(ROOT + path, "w", encoding="utf-8", newline="\n").write(s)


# --- 1. the engine's reasons, from EDOPro's own constant.lua -----------------
edit("common/src/main/java/de/cas_ual_ty/dueldimension/ocg/OcgConstants.java", [(
    "    public static final int QUERY_REASON = 0x1000;",
    '''    /**
     * Why a card moved, as {@code MSG_MOVE}'s fourth field.
     * <p>
     * Taken from EDOPro's own {@code script/constant.lua}, which is where the
     * card scripts read them from and therefore the definition the engine and
     * every card agree on. Only the ones this mod actually asks about are here;
     * the file has twenty or so more.
     * <p>
     * These are FLAGS and arrive combined -- a monster tributed for a summon
     * carries {@code REASON_RELEASE | REASON_SUMMON | REASON_COST} -- so they
     * are tested with a mask and never compared for equality.
     */
    public static final int REASON_DESTROY = 0x1;
    /** A tribute. The card was released, not destroyed. */
    public static final int REASON_RELEASE = 0x2;
    public static final int REASON_MATERIAL = 0x8;
    public static final int REASON_SUMMON = 0x10;
    public static final int REASON_BATTLE = 0x20;
    public static final int REASON_EFFECT = 0x40;
    public static final int REASON_COST = 0x80;

    public static final int QUERY_REASON = 0x1000;''')])

# --- 2. the event kind -------------------------------------------------------
edit("common/src/main/java/de/cas_ual_ty/dueldimension/ocg/prompt/DuelEvent.java", [(
    "        DESTROY,",
    '''        DESTROY,
        /**
         * A card released as a tribute, which is NOT a destruction.
         * <p>
         * Both end up in the graveyard, and until now both were told apart by
         * exactly that -- so a monster given up to summon a bigger one shattered
         * like glass, which is the wrong story. The engine has always said
         * which is which; nothing was reading it. See {@code REASON_RELEASE}.
         */
        TRIBUTE,''')])

# --- 3. read the reason when the event is built ------------------------------
edit("mc262/src/main/java/de/cas_ual_ty/dueldimension/duel/npc/DuelistDuels.java", [(
    """            DuelEvent.Kind kind =
                move.to().location() == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE
                    ? DuelEvent.Kind.DESTROY : DuelEvent.Kind.MOVE;""",
    """            // A card reaching the graveyard is not necessarily a card that
            // was destroyed. MSG_MOVE carries the REASON, and a tribute says
            // so -- so a monster released for a summon gets its own event
            // rather than being smashed like one that lost a battle.
            boolean toGrave = move.to().location()
                == de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_GRAVE;
            boolean released = (move.reason()
                & de.cas_ual_ty.dueldimension.ocg.OcgConstants.REASON_RELEASE) != 0;
            DuelEvent.Kind kind = !toGrave ? DuelEvent.Kind.MOVE
                : released ? DuelEvent.Kind.TRIBUTE : DuelEvent.Kind.DESTROY;""")])

# --- 4. its own animation list, timing and sound -----------------------------
edit("mc262/src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelAnimations.java", [
    ("    private final List<Playing> shatters = new ArrayList<>();",
     """    private final List<Playing> shatters = new ArrayList<>();

    /**
     * Tributes in flight, kept apart from {@link #shatters}.
     * <p>
     * A separate list rather than a flag on the shatter, because the two are
     * drawn by different code and share nothing but their timing: one breaks a
     * card into pieces of itself, the other lifts it away as light.
     */
    private final List<Playing> releases = new ArrayList<>();"""),
    ("            case DESTROY -> shatters.add(new Playing(event, now, duration));",
     """            case DESTROY -> shatters.add(new Playing(event, now, duration));
            case TRIBUTE -> releases.add(new Playing(event, now, duration));"""),
    ("            case DESTROY -> SHATTER_MS;",
     """            case DESTROY -> SHATTER_MS;
            // The same span as a shatter: it replaces one in the sequence, and
            // a different length would change the pacing of every summon that
            // follows a tribute.
            case TRIBUTE -> SHATTER_MS;"""),
    ("        shatters.removeIf(animation -> animation.done(now));",
     """        shatters.removeIf(animation -> animation.done(now));
        releases.removeIf(animation -> animation.done(now));"""),
    ("        shatters.clear();", "        shatters.clear();\n        releases.clear();"),
    ("            || !shatters.isEmpty() || !tosses.isEmpty()",
     "            || !shatters.isEmpty() || !releases.isEmpty() || !tosses.isEmpty()"),
])

# The view list, beside the shatter's own.
s = io.open(ROOT + "mc262/src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelAnimations.java",
            encoding="utf-8").read()
anchor = "    public java.util.List<ShatterView> shattersInFlight(long now)"
at = s.index(anchor)
s = s[:at] + '''    /**
     * Tributes in flight, in the same shape a shatter reports.
     * <p>
     * The same record on purpose: the board draws them differently but needs
     * exactly the same facts to do it -- which card, which zone, and how far
     * through.
     */
    public java.util.List<ShatterView> releasesInFlight(long now)
    {
        java.util.List<ShatterView> views = new ArrayList<>(releases.size());
        for(Playing animation : releases)
        {
            DuelEvent event = animation.event();
            Identifier texture = faceFor(event);
            float[] uv = uvFor(event);
            views.add(new ShatterView(event.code(), event.fromZone(),
                animation.progress(now), texture, uv[0], uv[1], uv[2], uv[3]));
        }
        return views;
    }

''' + s[at:]
io.open(ROOT + "mc262/src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelAnimations.java",
        "w", encoding="utf-8", newline="\n").write(s)

# --- 5. the sound ------------------------------------------------------------
edit("mc262/src/main/java/de/cas_ual_ty/dueldimension/DdSounds.java", [(
    '    public static final SoundEvent DESTROYED = register("duel.destroyed");',
    '''    public static final SoundEvent DESTROYED = register("duel.destroyed");
    /** A monster given up as a tribute, which does not break. */
    public static final SoundEvent TRIBUTE = register("duel.tribute");''')])

p = ROOT + "shared/resources/assets/dueldimension/sounds.json"
d = json.load(io.open(p, encoding="utf-8"))
d["duel.tribute"] = {"category": "player",
                     "sounds": [{"name": "dueldimension:duel/tribute", "stream": False}]}
io.open(p, "w", encoding="utf-8", newline="\n").write(
    json.dumps(d, indent=2, ensure_ascii=False) + "\n")
print("  sounds.json: duel.tribute")
print("  patched constants, event, animations, DdSounds")
