package de.cas_ual_ty.dueldimension.duel.match;

import net.minecraft.network.FriendlyByteBuf;

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
 */
public record MatchConfig(String banlistId, int lifePoints, Format format, int turnSeconds)
{
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

    public static final MatchConfig DEFAULT =
        new MatchConfig(Banlist.NO_BANLIST_ID, 8000, Format.SINGLE, 180);

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
            lp, format == null ? Format.SINGLE : format, timer);
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

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeUtf(banlistId);
        buffer.writeVarInt(lifePoints);
        buffer.writeEnum(format);
        buffer.writeVarInt(turnSeconds);
    }

    public static MatchConfig read(FriendlyByteBuf buffer)
    {
        return new MatchConfig(buffer.readUtf(), buffer.readVarInt(),
            buffer.readEnum(Format.class), buffer.readVarInt());
    }
}
