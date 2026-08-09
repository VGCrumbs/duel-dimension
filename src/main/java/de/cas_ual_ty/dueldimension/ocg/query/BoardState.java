package de.cas_ual_ty.dueldimension.ocg.query;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.OcgDuel;
import de.cas_ual_ty.dueldimension.ocg.OcgStructs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of the field <em>as one player is entitled to see it</em>.
 * <p>
 * The engine runs in our process, so the query API can hand us the opponent's
 * hand and face-down cards. Building strategy on that would make every NPC a
 * silent cheat: they would never walk into a set Mirror Force, difficulty
 * tuning would be fiction, and players would feel it. So the only public
 * factory takes a viewer, hides what that viewer may not know, and requires an
 * explicit opt-in to see everything ({@link #observeOmnisciently}) — reserved
 * for a deliberate "this boss cheats" profile setting.
 *
 * @param viewer     the player this snapshot belongs to (0 or 1)
 * @param omniscient true if hidden information was NOT stripped
 */
public record BoardState(int viewer, boolean omniscient, PlayerBoard self, PlayerBoard opponent)
{
    /** Zone counts are fixed by the core: 7 monster zones, 8 spell/trap zones. */
    public static final int MONSTER_ZONES = 7;
    public static final int SPELL_ZONES = 8;

    /**
     * @param monsters   per-zone, null where empty
     * @param spells     per-zone, null where empty
     * @param hand       cards in hand (hidden ones have no identity)
     * @param grave      graveyard contents (always public knowledge)
     * @param deckCount  cards left in the main deck
     * @param extraCount cards left in the extra deck
     */
    public record PlayerBoard(int lifePoints, List<CardView> monsters, List<CardView> spells, List<CardView> hand,
        List<CardView> grave, List<CardView> banished, List<CardView> extra, int deckCount, int extraCount)
    {
        public int monsterCount()
        {
            return (int)monsters.stream().filter(java.util.Objects::nonNull).count();
        }

        /** Highest ATK among face-up monsters; 0 if none can be seen. */
        public int strongestFaceUpAttack()
        {
            int best = 0;
            for(CardView card : monsters)
            {
                if(card != null && card.isFaceUp() && card.attack() > best)
                {
                    best = card.attack();
                }
            }
            return best;
        }

        /**
         * Highest ATK among monsters that can actually swing — face-up
         * <em>and</em> in attack position. A face-up defender is neither a
         * threat to us nor an attacker for us, so this is the number that
         * matters for both "can I attack" and "what am I afraid of".
         */
        public int strongestAttacker()
        {
            int best = 0;
            for(CardView card : monsters)
            {
                if(card != null && card.isFaceUp() && card.isAttackPosition() && card.attack() > best)
                {
                    best = card.attack();
                }
            }
            return best;
        }

        /**
         * Whether any monster of this side could declare an attack.
         * <p>
         * WindBot's {@code ClientField.HasAttackingMonster()}:
         * <pre>
         *     foreach (ClientCard card in MonsterZone)
         *         if (card != null &amp;&amp; card.IsAttack()) return true;
         *     return false;
         * </pre>
         * Existence, not size. {@link #strongestAttacker()} answers a different
         * question — how hard we can hit — and using it for this one made a
         * zero-attack monster look like no monster at all, so the bot spent a
         * boost in Main 1 and then ended the turn without swinging.
         */
        public boolean hasAttackPositionMonster()
        {
            for(CardView card : monsters)
            {
                if(card != null && card.isFaceUp() && card.isAttackPosition())
                {
                    return true;
                }
            }
            return false;
        }

        /** The monster occupying a monster zone sequence, or null. */
        public CardView monsterAt(int sequence)
        {
            return sequence >= 0 && sequence < monsters.size() ? monsters.get(sequence) : null;
        }
    }

    /** The honest view: everything this player is entitled to know, nothing more. */
    public static BoardState observe(OcgDuel duel, int viewer)
    {
        return build(duel, viewer, false);
    }

    /**
     * The cheating view: hidden cards keep their identity. Only for duelists
     * explicitly configured to cheat, and for tests that verify the honest
     * view actually hides something.
     */
    public static BoardState observeOmnisciently(OcgDuel duel, int viewer)
    {
        return build(duel, viewer, true);
    }

    private static BoardState build(OcgDuel duel, int viewer, boolean omniscient)
    {
        int[] counts = fieldCounts(duel);
        return new BoardState(viewer, omniscient,
            playerBoard(duel, viewer, viewer, omniscient, counts),
            playerBoard(duel, 1 - viewer, viewer, omniscient, counts));
    }

    private static PlayerBoard playerBoard(OcgDuel duel, int player, int viewer, boolean omniscient, int[] counts)
    {
        boolean own = player == viewer;
        return new PlayerBoard(counts[player * 3],
            zone(duel, player, viewer, OcgConstants.LOCATION_MZONE, own, omniscient, MONSTER_ZONES),
            zone(duel, player, viewer, OcgConstants.LOCATION_SZONE, own, omniscient, SPELL_ZONES),
            // A player always knows their own hand; an opponent's hand is
            // known only for cards the core marks public.
            zone(duel, player, viewer, OcgConstants.LOCATION_HAND, own, omniscient, -1),
            zone(duel, player, viewer, OcgConstants.LOCATION_GRAVE, true, omniscient, -1),
            // Banished face-up is public; face-down banished stays hidden.
            zone(duel, player, viewer, OcgConstants.LOCATION_REMOVED, own, omniscient, -1),
            // Your own extra deck is known to you; the opponent's is not.
            zone(duel, player, viewer, OcgConstants.LOCATION_EXTRA, own, omniscient, -1),
            counts[player * 3 + 1], counts[player * 3 + 2]);
    }

    private static List<CardView> zone(OcgDuel duel, int player, int viewer, int location, boolean ownerIsViewer,
        boolean omniscient, int expectedSize)
    {
        OcgStructs.OcgQueryInfo info = new OcgStructs.OcgQueryInfo();
        info.flags = QueryParser.BOARD_FLAGS;
        info.con = (byte)player;
        info.loc = location;
        info.seq = 0;
        info.overlay_seq = 0;

        List<CardView> cards = QueryParser.parseLocation(duel.queryLocation(info));
        List<CardView> result = new ArrayList<>(cards.size());
        for(CardView card : cards)
        {
            result.add(card == null ? null : relative(conceal(card, ownerIsViewer, omniscient), viewer));
        }
        while(expectedSize > 0 && result.size() < expectedSize)
        {
            result.add(null); // core omits trailing empty zones in some builds
        }
        return result;
    }

    private static CardView conceal(CardView card, boolean ownerIsViewer, boolean omniscient)
    {
        if(omniscient || ownerIsViewer || card.isPublic())
        {
            return card;
        }
        // Keep the position (that a card is set there is public) but strip
        // identity, including the base stats a set card must not reveal -- and
        // the scales, which would name a set pendulum card outright.
        // Status and overlays go too. Neither can legitimately be set on a card
        // this branch reaches -- the core only permits them on face-up on-field
        // cards, which are public by its own definition and returned above --
        // so this is belt and braces rather than a live case. It is written
        // anyway because "conceal" should be the one place that decides what a
        // stranger may see, without needing that argument re-derived.
        return new CardView(0, card.position(), 0, 0, -1, -1, -1, -1, -1, -1, false, true, null,
            0, 0);
    }

    /**
     * Restates an equip target in the viewer's own numbering, where 0 is
     * always this player's side. Everything else on a board reaches the screen
     * that way already; leaving this one field in the core's absolute
     * numbering would point the opponent's equip links at the wrong half of
     * the table.
     */
    private static CardView relative(CardView card, int viewer)
    {
        CardView.Equip equip = card.equip();
        if(equip == null)
        {
            return card;
        }
        int controller = equip.controller() == viewer ? 0 : 1;
        return controller == equip.controller() ? card
            : new CardView(card.code(), card.position(), card.type(), card.level(), card.attack(),
                card.defense(), card.baseAttack(), card.baseDefense(),
                card.leftScale(), card.rightScale(), card.isPublic(), card.hidden(),
                new CardView.Equip(controller, equip.location(), equip.sequence()),
                // Carried through: this rewrites WHERE an equip points, not
                // what the card is.
                card.status(), card.overlays());
    }

    /** Life points and pile sizes, from OCG_DuelQueryField. Layout: ocgapi.cpp. */
    private static int[] fieldCounts(OcgDuel duel)
    {
        ByteBuffer buffer = ByteBuffer.wrap(duel.queryField()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.getInt(); // duel options
        int[] counts = new int[6]; // lp, deck, extra per player
        for(int player = 0; player < 2; player++)
        {
            counts[player * 3] = buffer.getInt(); // lp
            skipZones(buffer, MONSTER_ZONES);
            skipZones(buffer, SPELL_ZONES);
            counts[player * 3 + 1] = buffer.getInt(); // main deck
            buffer.getInt(); // hand
            buffer.getInt(); // grave
            buffer.getInt(); // removed
            counts[player * 3 + 2] = buffer.getInt(); // extra deck
            buffer.getInt(); // extra pendulum count
        }
        return counts;
    }

    private static void skipZones(ByteBuffer buffer, int zones)
    {
        for(int i = 0; i < zones; i++)
        {
            if(buffer.get() != 0)
            {
                buffer.get(); // position
                buffer.getInt(); // xyz material count
            }
        }
    }
}
