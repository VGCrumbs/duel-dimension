"""Destiny Draw with nothing nominated is no longer silent."""
import sys

HELPER_ANCHOR = '''    private static HeadlessDuelRunner.Deck withChaosDiskPromise(ServerPlayer player,'''

HELPER = '''    /**
     * Says so when a duel is arranged with Destiny Draw on and the deck has
     * nothing nominated.
     *
     * <h2>Why this exists</h2>
     * A deck with no Destiny Cards registers no rule at all -- {@code
     * DestinyDrawScript.chunk} returns null for an empty list, deliberately, so
     * a duel nobody nominated anything for costs the engine nothing. The
     * trouble was that this looked identical to the feature being broken: the
     * bot's menu said Destiny Draws were on, the duellist dropped below 4000
     * with no monster to answer the board, and nothing happened, because there
     * was nothing that COULD happen. Nowhere in the game said so.
     * <p>
     * It is not an error and it is not stopped -- the duel plays perfectly well
     * under ordinary rules. It is a mismatch between what was switched on and
     * what the deck can do, and the player is the only one who can fix it.
     */
    private static void reportDestiny(ServerPlayer player, boolean on,
        HeadlessDuelRunner.Deck deck, String deckName)
    {
        if(player == null || deck == null || !on)
        {
            return;
        }
        if(deck.destiny().isEmpty())
        {
            player.sendSystemMessage(Component.literal("Destiny Draw is on, but no cards in \\""
                + deckName + "\\" are marked as Destiny Cards, so there is nothing for it to"
                + " draw. Mark some in the deck editor."));
            return;
        }
        // And the other half of the same problem: a report of "it never fired"
        // could not be told from "it was never registered" without this line.
        de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny Draw: "
            + player.getGameProfile().getName() + " nominated " + deck.destiny().size()
            + " card(s) in \\"" + deckName + "\\"");
    }

'''

BOT_OLD = '''        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(serverPlayer, playerDeck.cards());
        HeadlessDuelRunner.Deck deck1 = duelist.npcDeck(serverPlayer);'''

BOT_NEW = '''        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(serverPlayer, playerDeck.cards());
        HeadlessDuelRunner.Deck deck1 = duelist.npcDeck(serverPlayer);
        reportDestiny(serverPlayer, botDestiny, deck0, playerDeck.displayName());'''

PVP_OLD = '''        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(first, deckA.cards());
        HeadlessDuelRunner.Deck deck1 = withChaosDiskPromise(second, deckB.cards());'''

PVP_NEW = '''        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(first, deckA.cards());
        HeadlessDuelRunner.Deck deck1 = withChaosDiskPromise(second, deckB.cards());
        reportDestiny(first, config.destinyDraw(), deck0, deckA.displayName());
        reportDestiny(second, config.destinyDraw(), deck1, deckB.displayName());'''

for tree in ('mc1211', 'mc262'):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/duel/npc/DuelistDuels.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in ((HELPER_ANCHOR, HELPER + HELPER_ANCHOR), (BOT_OLD, BOT_NEW),
                     (PVP_OLD, PVP_NEW)):
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (p, s.count(old), old[:60]))
        s = s.replace(old, new)
    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
