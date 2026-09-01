"""A long name shrinks instead of bleeding, and the plates shrink instead of wrapping."""
import sys

FIELD_ANCHOR = '''        /** The plates' own scale, which is the body's unless they had to shrink. */'''
FIELD_NEW = '''        /** The name's own scale, which is the body's unless it had to shrink. */
        float nameScale;
'''

SCALE_OLD = '''        b.scale = MenuText.oneStepSmaller();'''
SCALE_NEW = '''        b.scale = MenuText.oneStepSmaller();
        // THE NAME SHRINKS RATHER THAN BLEEDS.
        //
        // It is the one string in here that can neither wrap nor be
        // abbreviated, and card names run long: "Awakening of the Possessed -
        // Gagigobyte" overhangs a panel that an ordinary name sits well inside,
        // and what the player saw was the tail of their card's name painted
        // over the border. Widening the panel is tried first -- see
        // preferredWidth -- and this is what happens when the window has no
        // more width to give.
        //
        // Stepped down the same device-pixel grid as everything else, so a
        // shrunk name is still crisp, and only as far as it actually has to go.
        String name = card.getName() == null ? "" : card.getName();
        b.nameScale = b.scale;
        for(int steps = 2; Math.round(font.width(name) * b.nameScale) > width - 12; steps++)
        {
            float next = MenuText.smaller(steps);
            if(next >= b.nameScale)
            {
                // The GUI scale has no smaller step left to give. The name is
                // as small as this window can draw it, and the clip that
                // follows is the honest outcome rather than a hidden one.
                break;
            }
            b.nameScale = next;
        }'''

PAD_OLD = '''        // Padding first and the text last, because margin is the cheapest thing
        // on the plate to lose: the letters are already at the smallest size
        // that lands on whole pixels, and taking them below that trades a row
        // of description for text nobody can read.
        for(int pad = b.pillPad - 1; b.factRows > 1 && pad >= 1; pad--)
        {
            List<int[]> tighter = new ArrayList<>();
            int rows = layOutPills(font, b.groups, width, b.factScale, b.pillH, pad, tighter);
            if(rows < b.factRows)
            {
                b.pillPad = pad;
                b.factRows = rows;
                b.pills = tighter;
            }
        }
'''

PAD_NEW = '''        // Padding first, because margin is the cheapest thing on the plate to
        // lose -- a pixel of it costs nothing anybody can read.
        for(int pad = b.pillPad - 1; b.factRows > 1 && pad >= 1; pad--)
        {
            List<int[]> tighter = new ArrayList<>();
            int rows = layOutPills(font, b.groups, width, b.factScale, b.pillH, pad, tighter);
            if(rows < b.factRows)
            {
                b.pillPad = pad;
                b.factRows = rows;
                b.pills = tighter;
            }
        }
        // AND THEN THE TEXT, which the padding alone cannot always save.
        //
        // This used to stop above, on the reasoning that the letters were
        // already at the smallest size landing on whole pixels. That is true of
        // the compact panels and NOT of the wide ones, where the plates run at
        // the body's own scale -- so a long attribute line wrapped onto a second
        // row while there was still room to shrink into, and the second row cost
        // the description a line to hold three words that nearly fitted.
        //
        // Two steps at most, and only ever to reach ONE row. Past that the row
        // really is cheaper than the reading, which is what the old comment here
        // was right about.
        int from = compact ? 2 : 1;
        for(int steps = from + 1; b.factRows > 1 && steps <= from + 2; steps++)
        {
            float scale = MenuText.smaller(steps);
            if(scale >= b.factScale)
            {
                break;
            }
            int pillH = Math.max(6, Math.round(font.lineHeight * scale) + PILL_LEADING);
            int pad = Math.max(2, Math.round(PILL_PAD * (scale / b.scale)));
            List<int[]> shrunk = new ArrayList<>();
            int rows = layOutPills(font, b.groups, width, scale, pillH, pad, shrunk);
            if(rows < b.factRows)
            {
                b.factScale = scale;
                b.pillH = pillH;
                b.pillPad = pad;
                b.factRows = rows;
                b.pills = shrunk;
            }
        }
'''

DRAW_OLD = '''        String name = card.getName() == null ? "" : card.getName();
        g.pose().pushMatrix();
        g.pose().scale(b.scale, b.scale);
        g.text(font, name, Math.round((x + 6) / b.scale),
            Math.round((y + lift + 5) / b.scale), MenuInk.title(), MenuInk.shadow());
        g.pose().popMatrix();'''

DRAW_NEW = '''        String name = card.getName() == null ? "" : card.getName();
        g.pose().pushMatrix();
        // nameScale, not scale: a name too long for the panel was measured in
        // body() and stepped down until it fits.
        g.pose().scale(b.nameScale, b.nameScale);
        g.text(font, name, Math.round((x + 6) / b.nameScale),
            Math.round((y + lift + 5) / b.nameScale), MenuInk.title(), MenuInk.shadow());
        g.pose().popMatrix();'''

for tree in ('mc1211', 'mc262'):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/CardInfoPanel.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in ((FIELD_ANCHOR, FIELD_NEW + FIELD_ANCHOR), (SCALE_OLD, SCALE_NEW),
                     (PAD_OLD, PAD_NEW), (DRAW_OLD, DRAW_NEW)):
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (p, s.count(old), old[:60]))
        s = s.replace(old, new)
    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
