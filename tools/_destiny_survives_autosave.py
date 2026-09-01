"""saveDeck REPLACES the stored deck. Anything it does not carry over is wiped."""
import sys

OLD = '''            // And the list it is built to, which SetDeckBanlist sets and this
            // payload does not carry. Without this line every autosave -- one
            // per card added -- would reset the deck to no list, so the setting
            // would appear to work and then quietly undo itself.
            candidate.setBanlistId(existing.banlistId());'''

NEW = '''            // And the list it is built to, which SetDeckBanlist sets and this
            // payload does not carry. Without this line every autosave -- one
            // per card added -- would reset the deck to no list, so the setting
            // would appear to work and then quietly undo itself.
            candidate.setBanlistId(existing.banlistId());
            // AND THE DESTINY CARD FLAGS, for exactly the same reason, which
            // this method predicted and then did not do.
            //
            // They travel on SetDeckDestiny, so they are not in this payload
            // either -- and every autosave was therefore erasing them. The
            // symptom was that marks worked until the game was closed: the flag
            // reached the server and was stored, and then the next card added,
            // moved or removed replaced the deck with one that had never heard
            // of it. The mark was gone long before anything was written to disk.
            //
            // Filtered to what the NEW main deck holds, not copied wholesale: a
            // flagged card the player has just taken out of the deck is not a
            // Destiny Card any more, and keeping its passcode would leave a flag
            // on a card that is no longer there for the engine to find.
            List<Integer> flags = new java.util.ArrayList<>();
            for(Integer code : existing.destiny())
            {
                if(code != null && candidate.main().contains(code))
                {
                    flags.add(code);
                }
            }
            candidate.setDestiny(flags);'''

for tree in ('mc1211', 'mc262'):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/duel/profile/DeckEdits.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    if s.count(OLD) != 1:
        sys.exit('%s: %d matches' % (p, s.count(OLD)))
    s = s.replace(OLD, NEW)
    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
