"""The box widens for a long name. Only the window caps it."""
import sys

EDITOR_OLD = '''        int panelW = Math.max(
            CardInfoPanel.preferredWidth(font, card, Math.min(Math.max(32, width - 8),
                Math.max(layout.i("preview.width", 78), layout.i("preview.textWidth", 132)) + 10),
                true),
            // Never below the artwork Shift reveals, which is the one thing in
            // here that cannot reflow.
            layout.i("preview.width", 78) + 10);'''

EDITOR_NEW = '''        // THE WINDOW IS THE ONLY CAP.
        //
        // The layout's textWidth used to be one as well, so a name wider than
        // its 132 pixels had nowhere to go but smaller -- which is the wrong
        // trade when there is empty screen either side of the panel. The box
        // grows to whatever the name and the plates need and is clamped to the
        // window when it is placed, a few lines below.
        int panelW = Math.max(
            CardInfoPanel.preferredWidth(font, card, Math.max(32, width - 8), true),
            // Never below the artwork Shift reveals, which is the one thing in
            // here that cannot reflow.
            layout.i("preview.width", 78) + 10);'''

BUBBLE_OLD = '''        // Sized by the header, capped at the width this bubble has always used.
        int panelW = CardInfoPanel.preferredWidth(font, card, WRAP + PAD * 2, true);'''

BUBBLE_NEW = '''        // Sized by the header and capped only by the window: a long name widens
        // the bubble rather than being drawn smaller than the text beneath it.
        // WRAP is what it settles at for an ordinary name, not a ceiling.
        int panelW = CardInfoPanel.preferredWidth(font, card, Math.max(WRAP + PAD * 2,
            screenW - 8), true);'''

NAME_OLD = '''        // Stepped down the same device-pixel grid as everything else, so a
        // shrunk name is still crisp, and only as far as it actually has to go.'''

NAME_NEW = '''        // A LAST RESORT, and it should now be unreachable. Every caller caps the
        // panel at the window rather than at a layout number, so reaching this
        // means the name is wider than the whole window -- at which point there
        // is genuinely nowhere left to widen into. Stepped down the same
        // device-pixel grid as everything else, so if it ever does fire the
        // result is still crisp.'''

for tree in ('mc1211', 'mc262'):
    base = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/'
    for path, edits in (
        (base + 'hub/DeckEditorScreen.java', [(EDITOR_OLD, EDITOR_NEW)]),
        (base + 'overworld/CardBubble.java', [(BUBBLE_OLD, BUBBLE_NEW)]),
        (base + 'hub/CardInfoPanel.java', [(NAME_OLD, NAME_NEW)]),
    ):
        raw = open(path, 'rb').read().decode('utf-8')
        crlf = '\r\n' in raw
        s = raw.replace('\r\n', '\n')
        for old, new in edits:
            if s.count(old) != 1:
                sys.exit('%s: %d matches for %r' % (path, s.count(old), old[:60]))
            s = s.replace(old, new)
        open(path, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
