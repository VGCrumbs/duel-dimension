"""The panel is sized by its header: the name, or the fact plates, whichever is longer."""
import sys

ANCHOR = '''    /** Everything measuring and drawing both need, worked out once. */'''

HELPER = '''    /** No panel gets narrower than this, whatever the card is called. */
    private static final int MIN_WIDTH = 120;

    /**
     * The width this card's HEADER needs: the name, or the row of fact plates,
     * whichever is longer.
     *
     * <h2>Why the header and not the description</h2>
     * A description always fills whatever it is given -- it wraps -- so it can
     * never ask for a width, and a panel sized to it is really a panel sized to
     * a number somebody picked. The name and the plates are the two things that
     * have a natural width and get clipped or cramped without it, so they are
     * what the panel is measured against and the description wraps to whatever
     * they come to.
     * <p>
     * <b>This can widen a panel as well as narrow one.</b> A card whose name
     * fills the window gets the room to print it; a card called "Kuriboh" stops
     * getting the same slab as one that does not.
     *
     * @param maxWidth the most the caller can spare -- the result never exceeds
     *                 it, so a long name is still clipped rather than hung off
     *                 the side of the window
     */
    public static int preferredWidth(Font font, Properties card, int maxWidth, boolean compact)
    {
        float scale = MenuText.oneStepSmaller();
        float factScale = compact ? MenuText.smaller(2) : scale;
        int pad = Math.max(2, Math.round(PILL_PAD * (factScale / scale)));

        String name = card.getName() == null ? "" : card.getName();
        int wanted = Math.round(font.width(name) * scale);

        // The plates as they would be on ONE row, which is the arrangement the
        // layout already tries hardest to reach. Measured from the same
        // factLine the draw uses, so an abbreviation that saves room here saves
        // it there too.
        List<net.minecraft.network.chat.Component> facts = new ArrayList<>();
        CardPresentation.addFacts(card, facts);
        int row = 0;
        int plates = 0;
        for(net.minecraft.network.chat.Component fact : facts)
        {
            String text = fact.getString();
            if(text.isBlank())
            {
                continue;
            }
            row += Math.round(font.width(factLine(text)) * factScale) + pad * 2;
            plates++;
        }
        if(plates > 1)
        {
            row += PILL_GAP * (plates - 1);
        }
        // Six a side, which is where the name starts and where the text wraps.
        return Math.max(MIN_WIDTH, Math.min(maxWidth, Math.max(wanted, row) + 12));
    }

'''

PACK_OLD = '''        int panelW = Math.min(width - 40, 340);'''
PACK_NEW = '''        // AS WIDE AS THE CARD NEEDS, rather than a fixed 340. The panel takes
        // the longer of the name and the row of fact plates and the description
        // wraps to that, so a long name gets the room to print it and a short
        // one stops being given a slab it does not fill.
        int panelW = CardInfoPanel.preferredWidth(font, peek, width - 40, false);'''

EDITOR_OLD = '''        int panelW = Math.min(Math.max(32, width - 8),
            Math.max(layout.i("preview.width", 78), layout.i("preview.textWidth", 132)) + 10);'''
EDITOR_NEW = '''        int panelW = Math.max(
            CardInfoPanel.preferredWidth(font, card, Math.min(Math.max(32, width - 8),
                Math.max(layout.i("preview.width", 78), layout.i("preview.textWidth", 132)) + 10),
                true),
            // Never below the artwork Shift reveals, which is the one thing in
            // here that cannot reflow.
            layout.i("preview.width", 78) + 10);'''

BUBBLE_OLD = '''        int panelW = WRAP + PAD * 2;'''
BUBBLE_NEW = '''        // Sized by the header, capped at the width this bubble has always used.
        int panelW = CardInfoPanel.preferredWidth(font, card, WRAP + PAD * 2, true);'''

for tree in ('mc1211', 'mc262'):
    base = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/'
    for path, edits in (
        (base + 'hub/CardInfoPanel.java', [(ANCHOR, HELPER + ANCHOR)]),
        (base + 'hub/PackOpeningScreen.java', [(PACK_OLD, PACK_NEW)]),
        (base + 'hub/DeckEditorScreen.java', [(EDITOR_OLD, EDITOR_NEW)]),
        (base + 'overworld/CardBubble.java', [(BUBBLE_OLD, BUBBLE_NEW)]),
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
