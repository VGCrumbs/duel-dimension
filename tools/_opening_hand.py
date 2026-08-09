"""Draw six on turn one, and stack a guaranteed card sixth from the top.

TWO changes, and the second is only possible because of what registerDecks
already documents: ocgcore does NOT shuffle the opening deck. It shuffles only
when a card effect raises shuffle_deck_check; shuffling at the start is the
host's job, and this mod does it in Java. So a card can be placed at a known
depth AFTER a genuinely random shuffle, with no PSEUDO_SHUFFLE and no change to
how the engine plays the duel. registerDecks also states that element 0 of the
list ends up on top of the deck, which is what makes index 5 the sixth card.

1. DUEL_1ST_TURN_DRAW. Under MR5 the player going first does not draw, so they
   play turn one off five cards. The flag is ocgcore's own -- it is already part
   of DUEL_MODE_MR1, MR2 and RUSH here -- and turns that into a normal draw, so
   whoever goes first opens their first turn holding six.

2. A guaranteed card at OPENING_SLOT. The opening hand is the first five cards,
   so index 5 is the very next one drawn: the first player draws it on turn one,
   and the second player draws it on theirs. Either way it is their sixth card,
   which is the same promise for both seats.
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

H = "src/main/java/de/cas_ual_ty/dueldimension/ocg/HeadlessDuelRunner.java"
E = "src/main/java/de/cas_ual_ty/dueldimension/ocg/session/EngineRuntime.java"

# ---------- 1. six cards on turn one ----------
sub(E, """    public long defaultFlags()
    {
        return OcgConstants.DUEL_MODE_MR5;
    }""",
    """    public long defaultFlags()
    {
        // MR5 plus the first-turn draw. Modern rules have the player going
        // first skip their draw, opening on five cards while the second player
        // reaches six; this hands that draw back, so whoever goes first begins
        // their first turn holding six. DUEL_1ST_TURN_DRAW is ocgcore's own
        // option -- MR1, MR2 and Rush all carry it -- so the engine applies it
        // rather than anything here counting cards.
        return OcgConstants.DUEL_MODE_MR5 | OcgConstants.DUEL_1ST_TURN_DRAW;
    }""", "DUEL_1ST_TURN_DRAW")

# ---------- 2. the guaranteed card ----------
sub(H, """    public record Deck(List<Integer> main, List<Integer> extra)
    {""",
    """    /**
     * A deck, and optionally one card it promises to open with.
     *
     * @param guaranteed a passcode to place at {@link #OPENING_SLOT}, or 0 for
     *                   an untouched deck. The card must already be in
     *                   {@code main} — this moves a card, it never adds one, so
     *                   the deck list stays exactly as legal as it was.
     */
    public record Deck(List<Integer> main, List<Integer> extra, int guaranteed)
    {
        /** A deck that promises nothing, which is nearly all of them. */
        public Deck(List<Integer> main, List<Integer> extra)
        {
            this(main, extra, 0);
        }

        /** The same deck, promising to draw this card. */
        public Deck guaranteeing(int passcode)
        {
            return new Deck(main, extra, passcode);
        }
    """, "Deck.guaranteed")

sub(H, """            for(int i = main.size() - 1; i >= 0; i--)
            {
                duel.newCard(player, 0, main.get(i), player, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }""",
    """            placeGuaranteed(main, deck.guaranteed());

            for(int i = main.size() - 1; i >= 0; i--)
            {
                duel.newCard(player, 0, main.get(i), player, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
            }""", "placeGuaranteed call")

sub(H, """    /** Mixes the four seed words and the seat into one shuffle stream. */""",
    """    /**
     * Where a guaranteed card sits: the sixth from the top.
     * <p>
     * The opening hand is the first five, so index 5 is the very next card
     * drawn. With the first-turn draw on, that is the card the player going
     * first draws on turn one — and the player going second draws it on theirs.
     * The same promise either way, without the seat changing what it means.
     */
    private static final int OPENING_SLOT = 5;

    /**
     * Moves one card to {@link #OPENING_SLOT}, after the shuffle.
     * <p>
     * After, not instead of: the deck is still shuffled properly and every
     * other card is where chance put it. This is possible at all only because
     * the core does not shuffle the opening deck — see {@link #registerDecks} —
     * so an order set here is the order that is played.
     * <p>
     * Moves rather than inserts, so the deck holds exactly the cards it held
     * before; a deck without the card is left alone rather than given one.
     */
    private static void placeGuaranteed(List<Integer> main, int passcode)
    {
        if(passcode == 0)
        {
            return;
        }
        int at = main.indexOf(passcode);
        if(at < 0)
        {
            return;   // not in this deck; nothing was promised
        }
        main.remove(at);
        // A deck shorter than the opening draw keeps the card as deep as it can.
        main.add(Math.min(OPENING_SLOT, main.size()), passcode);
    }

    /** Mixes the four seed words and the seat into one shuffle stream. */""",
    "placeGuaranteed body")

print("done")
