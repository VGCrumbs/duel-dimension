"""Marking a card says what it did, at both ends, so a mark that vanishes can be located."""
import sys

CLIENT_OLD = '''        DeckList open = deck();
        if(open == PLACEHOLDER || !open.main().contains(passcode))
        {
            // Main deck only -- the Extra Deck is never drawn from.
            return;
        }
        open.setDestiny(passcode, !open.isDestiny(passcode));
        send(new ProfilePayloads.SetDeckDestiny(open.name(),
            new ArrayList<>(open.destiny())));'''

CLIENT_NEW = '''        DeckList open = deck();
        if(open == PLACEHOLDER || !open.main().contains(passcode))
        {
            // Main deck only -- the Extra Deck is never drawn from.
            //
            // SAID OUT LOUD, because this return is the one way a click on
            // "Destiny Card" can do nothing at all: the row is only offered for
            // a main-deck card, so reaching it means the card the menu was
            // opened on is not the card the open deck holds, and a mark that
            // silently fails to happen is indistinguishable from the feature
            // being broken further down.
            de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny mark REFUSED for "
                + passcode + ": " + (open == PLACEHOLDER ? "no deck open"
                    : "not in the main deck of \\"" + open.name() + "\\""));
            return;
        }
        boolean now = !open.isDestiny(passcode);
        open.setDestiny(passcode, now);
        de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny mark " + (now ? "SET" : "CLEARED")
            + " for " + passcode + " in \\"" + open.name() + "\\"; sending "
            + open.destiny().size() + " flag(s)");
        send(new ProfilePayloads.SetDeckDestiny(open.name(),
            new ArrayList<>(open.destiny())));'''

SERVER_OLD = '''        deck.setDestiny(kept);
        return null;'''

SERVER_NEW = '''        deck.setDestiny(kept);
        // The other end of the same question. A mark that leaves the client and
        // does not arrive, and one that arrives and is filtered away, look
        // identical from the deck editor.
        de.cas_ual_ty.dueldimension.DuelDimension.log("Destiny flags for \\"" + name
            + "\\": " + (codes == null ? 0 : codes.size()) + " arrived, " + kept.size()
            + " kept");
        return null;'''

for tree in ('mc1211', 'mc262'):
    for path, old, new in (
        (tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/EditorState.java',
         CLIENT_OLD, CLIENT_NEW),
        (tree + '/src/main/java/de/cas_ual_ty/dueldimension/duel/profile/DeckEdits.java',
         SERVER_OLD, SERVER_NEW),
    ):
        raw = open(path, 'rb').read().decode('utf-8')
        crlf = '\r\n' in raw
        s = raw.replace('\r\n', '\n')
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (path, s.count(old), old[:60]))
        s = s.replace(old, new)
        open(path, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
