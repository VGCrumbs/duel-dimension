"""ATK/DEF go back to text -- "A:" and "D:" -- so the sprite machinery goes too."""
import sys

PART_OLD = '''    /**
     * One piece of a fact plate: a run of text, or a sprite standing in for a
     * word.
     * <p>
     * ATK and DEF are a sword and a shield because at this size the words cost
     * more room than the numbers they label, and the numbers are the part
     * anybody is reading. A plate is a list of these so that the two can sit on
     * one line and be measured together -- an icon is as wide as a line is
     * tall, so it scales with the text rather than beside it.
     */
    private record Part(net.minecraft.network.chat.Component text,
        net.minecraft.resources.RESLOC icon)
    {
    }

'''

PARTS_NEW = '''    /**
     * One fact line, abbreviated to fit a plate.
     * <p>
     * "1200 ATK" becomes "A: 1200" and "800 DEF" becomes "D: 800": the label
     * moves in front of the number and loses all but its first letter, because
     * at this size the word costs more room than the number it introduces and
     * the number is the part anybody is reading. "Level 4" is "LV 4" for the
     * same reason.
     * <p>
     * Split on the separator the card itself used, so a Link monster's
     * "800 ATK / LINK-2" keeps its second half untouched -- a Link rating is
     * not a defence, and labelling it "D:" would say something untrue.
     */
    private static net.minecraft.network.chat.Component factLine(String line)
    {
        StringBuilder out = new StringBuilder();
        String[] tokens = line.split(" / ");
        for(int i = 0; i < tokens.length; i++)
        {
            if(i > 0)
            {
                out.append(" / ");
            }
            String token = tokens[i].trim();
            if(token.endsWith(" ATK"))
            {
                out.append("A: ").append(token, 0, token.length() - 4);
            }
            else if(token.endsWith(" DEF"))
            {
                out.append("D: ").append(token, 0, token.length() - 4);
            }
            else
            {
                out.append(token.replace("Level ", "LV "));
            }
        }
        return bold(out.toString());
    }

'''

DRAW_NEW = '''            net.minecraft.network.chat.Component group = b.groups.get(i);
            int textW = Math.round(font.width(group) * b.factScale);
            int textH = Math.round(font.lineHeight * b.factScale);
            g.pose().pushMatrix();
            g.pose().scale(b.factScale, b.factScale);
            g.text(font, group,
                Math.round((px + (pill[2] - textW) / 2) / b.factScale),
                Math.round((py + (b.pillH - textH) / 2) / b.factScale),
                0xFFC8D0DE, MenuInk.shadow());
            g.pose().popMatrix();
'''

SIMPLE = [
    ('        List<List<Part>> groups = new ArrayList<>();',
     '        List<net.minecraft.network.chat.Component> groups = new ArrayList<>();'),
    ('                b.groups.add(parts(text));',
     '                b.groups.add(factLine(text));'),
    ('    private static int layOutPills(Font font, List<List<Part>> groups,',
     '    private static int layOutPills(Font font, List<net.minecraft.network.chat.Component> groups,'),
    ('        for(List<Part> group : groups)',
     '        for(net.minecraft.network.chat.Component group : groups)'),
    ('            int w = Math.min(usable, Math.round(partsWidth(font, group, scale) * scale) + pad * 2);',
     '            int w = Math.min(usable, Math.round(font.width(group) * scale) + pad * 2);'),
]


def cut(s, start, end, path, what):
    """Delete the span from `start` through the first `end` after it."""
    i = s.find(start)
    if i < 0:
        sys.exit('%s: no start for %s' % (path, what))
    j = s.find(end, i)
    if j < 0:
        sys.exit('%s: no end for %s' % (path, what))
    return s[:i] + s[j + len(end):]


for tree in ('mc262',):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/CardInfoPanel.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')

    # The Part record, and the parts()/partsWidth()/icon* helpers it existed for.
    part_old = PART_OLD.replace('RESLOC',
        'Identifier' if tree == 'mc262' else 'ResourceLocation')
    if s.count(part_old) != 1:
        sys.exit('%s: %d matches for the Part record' % (p, s.count(part_old)))
    s = s.replace(part_old, '')
    s = cut(s, '    /**\n     * Splits one fact line into its parts',
            '        return out;\n    }\n\n', p, 'parts()')
    s = s[:s.find("    /** The stat sprites' own resolution.")] + PARTS_NEW \
        + s[s.find("    /** The stat sprites' own resolution."):]
    s = cut(s, "    /** The stat sprites' own resolution.",
            '        return Math.max(1, Math.round(iconScreen(font, scale) / scale));\n    }\n\n',
            p, 'the icon helpers')
    s = cut(s, "    /** How wide one plate's parts come to",
            '        return w;\n    }\n\n', p, 'partsWidth()')

    # The draw loop: one component, one call.
    i = s.find('            List<Part> group = b.groups.get(i);')
    if i < 0:
        sys.exit('%s: no draw loop' % p)
    end = '            g.pose().popMatrix();\n'
    j = s.find(end, i)
    if j < 0:
        sys.exit('%s: no draw loop end' % p)
    s = s[:i] + DRAW_NEW + s[j + len(end):]

    for old, new in SIMPLE:
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (p, s.count(old), old[:60]))
        s = s.replace(old, new)

    if 'Part' in s.replace('Parts', '').replace('part of', ''):
        leftover = [l for l in s.split('\n') if 'Part' in l and 'part' not in l.lower().replace('part', 'PART', 1)]
        for line in leftover[:5]:
            print('  NOTE leftover:', line.strip()[:90])

    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
