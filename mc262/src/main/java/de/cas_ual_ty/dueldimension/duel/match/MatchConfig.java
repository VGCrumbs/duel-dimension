package de.cas_ual_ty.dueldimension.duel.match;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * What the two players agreed to play. Set in the lobby, frozen when the match
 * leaves {@link MatchState#CONFIGURING}.
 * <p>
 * This travels to both clients so the lobby can show the same thing to both,
 * and it is re-validated server side on every change: a client may propose a
 * configuration but never impose one.
 *
 * @param banlistId    id of the {@link Banlist} both decks are checked against
 * @param lifePoints   starting LP, 8000 by convention
 * @param format       single duel or best of three
 * @param turnSeconds  how long a player may take over one decision, 0 for no limit
 * @param presentation whether the duel is played on a screen or on a board
 *                     built in the world between the two duellists
 */
public record MatchConfig(String banlistId, int lifePoints, Format format, int turnSeconds,
    Presentation presentation, boolean destinyDraw)
{
    /**
     * Where the duel is played. Both are the same duel -- the same engine, the
     * same rules, the same prompts -- and differ only in where it is drawn and
     * how a card is pointed at.
     */
    public enum Presentation
    {
        /** The 2D duel screen. */
        SCREEN,
        /** A board built in the world, with both duellists standing at it. */
        OVERWORLD;

        /** What the lobby row reads. */
        public String label()
        {
            return this == SCREEN ? "Duel screen" : "Overworld board";
        }
    }

    /** How many duels a match is played over. */
    public enum Format
    {
        /** One duel decides it. */
        SINGLE(1),
        /** First to two duels. */
        MATCH_BEST_OF_THREE(2);

        private final int winsNeeded;

        Format(int winsNeeded)
        {
            this.winsNeeded = winsNeeded;
        }

        /** Duels one side must take to win the match. */
        public int winsNeeded()
        {
            return winsNeeded;
        }

        public int maxDuels()
        {
            return winsNeeded * 2 - 1;
        }
    }

    /** Life-point totals the lobby offers. */
    public static final int[] LIFE_POINT_CHOICES = {8000, 4000, 2000, 16000};
    /** Turn-timer choices in seconds; 0 means no limit. */
    public static final int[] TIMER_CHOICES = {0, 60, 120, 180, 300};
    /** The threshold at which the HUD timer turns bold red. */
    public static final int TIMER_WARNING_SECONDS = 30;

    /**
     * What a lobby opens on.
     * <p>
     * <b>The board, not the screen.</b> A duel played on the ground between two
     * people standing at it is what this mod is for, and a default is what most
     * duels are played under -- nobody changes a setting they were not looking
     * for. It cost nothing to make it the default, either: siting the board is
     * allowed to fail, and {@link
     * de.cas_ual_ty.dueldimension.duel.overworld.OverworldDuels.Outcome#refused}
     * starts the duel on the screen when it does. So this reads "on the ground
     * where there is room for it", not "on the ground or not at all".
     */
    /**
     * Destiny Draw is OFF here, and that is a decision rather than an oversight.
     * <p>
     * It is a real change to the rules -- it puts a chosen card on top of a
     * deck -- and this mod's whole premise is that it plays actual Yu-Gi-Oh!
     * through the actual engine. A rule from a PSP spin-off is a thing to opt
     * into, not something to find in a duel you did not ask for it in. The same
     * reasoning turned the monster holograms off by default: a feature everyone
     * pays for by default should be the one everyone expects.
     * <p>
     * One word to change if that turns out to be the wrong call.
     */
    public static final MatchConfig DEFAULT =
        new MatchConfig(Banlist.NO_BANLIST_ID, 8000, Format.SINGLE, 180, Presentation.OVERWORLD,
            false);

    /**
     * Clamps a client-proposed configuration to something legal. A packet is
     * data, not an instruction, so every field is re-derived from the offered
     * choices rather than trusted.
     */
    public MatchConfig sanitised()
    {
        int lp = closest(lifePoints, LIFE_POINT_CHOICES);
        int timer = closest(turnSeconds, TIMER_CHOICES);
        return new MatchConfig(banlistId == null ? Banlist.NO_BANLIST_ID : banlistId,
            lp, format == null ? Format.SINGLE : format, timer,
            // Absent means unset, and unset means the default -- which is the
            // board. A packet that omits this must land where a lobby nobody
            // touched would have landed, or the default is only a default for
            // clients that bother to state it.
            presentation == null ? DEFAULT.presentation() : presentation,
            // A boolean needs no clamping: both values are legal, and absent
            // decodes as false, which is the default anyway.
            destinyDraw);
    }

    // One wither per field, so a caller changing one setting cannot silently
    // drop another. The lobby used to rebuild this record positionally at four
    // call sites; adding a field there is a compile error at best and a lost
    // setting at worst, and this feature added a field.

    public MatchConfig withBanlist(String id)
    {
        return new MatchConfig(id, lifePoints, format, turnSeconds, presentation, destinyDraw);
    }

    public MatchConfig withLifePoints(int points)
    {
        return new MatchConfig(banlistId, points, format, turnSeconds, presentation, destinyDraw);
    }

    public MatchConfig withFormat(Format value)
    {
        return new MatchConfig(banlistId, lifePoints, value, turnSeconds, presentation, destinyDraw);
    }

    public MatchConfig withTurnSeconds(int seconds)
    {
        return new MatchConfig(banlistId, lifePoints, format, seconds, presentation, destinyDraw);
    }

    public MatchConfig withPresentation(Presentation value)
    {
        return new MatchConfig(banlistId, lifePoints, format, turnSeconds, value, destinyDraw);
    }

    /**
     * Whether a duellist in a pinch may draw a card they nominated.
     * <p>
     * Settled when the duel is arranged and not afterwards, which is the rule
     * Tag Force itself has: it can be switched on or off, but never during a
     * duel that has already started. Here that falls out of the design rather
     * than needing enforcing -- the rule is registered into the engine before
     * the first turn, and there is no way to register one later.
     */
    public MatchConfig withDestinyDraw(boolean value)
    {
        return new MatchConfig(banlistId, lifePoints, format, turnSeconds, presentation, value);
    }

    /** Is this duel meant to be played on a board in the world? */
    public boolean isOverworld()
    {
        return presentation == Presentation.OVERWORLD;
    }

    private static int closest(int value, int[] allowed)
    {
        for(int candidate : allowed)
        {
            if(candidate == value)
            {
                return value;
            }
        }
        return allowed[0];
    }

    public boolean hasTimer()
    {
        return turnSeconds > 0;
    }

    /**
     * For remembering a player's last-arranged match on their profile.
     * <p>
     * Every field optional and defaulting to {@link #DEFAULT}'s, so a profile
     * saved before a challenger's settings were remembered loads as one that
     * has never arranged a duel -- which is exactly what it is. Run through
     * {@link #sanitised} on the way out of the profile rather than trusted, for
     * the same reason a packet is: a hand-edited save is data too.
     */
    public static final com.mojang.serialization.Codec<MatchConfig> CODEC =
        com.mojang.serialization.codecs.RecordCodecBuilder.create(instance ->
            instance.group(
                com.mojang.serialization.Codec.STRING
                    .optionalFieldOf("Banlist", DEFAULT.banlistId())
                    .forGetter(MatchConfig::banlistId),
                com.mojang.serialization.Codec.INT
                    .optionalFieldOf("LifePoints", DEFAULT.lifePoints())
                    .forGetter(MatchConfig::lifePoints),
                com.mojang.serialization.Codec.STRING
                    .optionalFieldOf("Format", DEFAULT.format().name())
                    .forGetter(config -> config.format().name()),
                com.mojang.serialization.Codec.INT
                    .optionalFieldOf("TurnSeconds", DEFAULT.turnSeconds())
                    .forGetter(MatchConfig::turnSeconds),
                com.mojang.serialization.Codec.STRING
                    .optionalFieldOf("Presentation", DEFAULT.presentation().name())
                    .forGetter(config -> config.presentation().name()),
                com.mojang.serialization.Codec.BOOL
                    .optionalFieldOf("DestinyDraw", DEFAULT.destinyDraw())
                    .forGetter(MatchConfig::destinyDraw)
            ).apply(instance, MatchConfig::fromNames));

    /**
     * Enum names rather than the enums themselves, so a value this build no
     * longer has loads as the default instead of refusing the whole profile.
     */
    private static MatchConfig fromNames(String banlistId, int lifePoints, String format,
        int turnSeconds, String presentation, boolean destinyDraw)
    {
        return new MatchConfig(banlistId, lifePoints, named(Format.class, format, DEFAULT.format()),
            turnSeconds, named(Presentation.class, presentation, DEFAULT.presentation()),
            destinyDraw).sanitised();
    }

    private static <E extends Enum<E>> E named(Class<E> type, String name, E fallback)
    {
        for(E candidate : type.getEnumConstants())
        {
            if(candidate.name().equals(name))
            {
                return candidate;
            }
        }
        return fallback;
    }

    public void write(RegistryFriendlyByteBuf buffer)
    {
        buffer.writeUtf(banlistId);
        buffer.writeVarInt(lifePoints);
        buffer.writeEnum(format);
        buffer.writeVarInt(turnSeconds);
        buffer.writeEnum(presentation);
        buffer.writeBoolean(destinyDraw);
    }

    public static MatchConfig read(RegistryFriendlyByteBuf buffer)
    {
        return new MatchConfig(buffer.readUtf(), buffer.readVarInt(),
            buffer.readEnum(Format.class), buffer.readVarInt(),
            buffer.readEnum(Presentation.class), buffer.readBoolean());
    }
}
