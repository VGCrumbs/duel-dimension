import sys

HELPER_OLD = '''    /**
     * How big a stat sprite is drawn, and it is NOT the line height.
     * <p>
     * Minecraft draws its glyphs eight pixels tall inside a nine-pixel line,
     * sitting at the top with the ninth left for descenders -- so text centres
     * on {@code y + 4}. A nine-pixel icon at the same y centres on 4.5 and sits
     * visibly low against the digits beside it. Eight centres on 4 exactly,
     * which is the same place the numbers do.
     */
    private static int iconSize(Font font)
    {
        return Math.max(1, font.lineHeight - 1);
    }
'''

HELPER_NEW = '''    /** The stat sprites' own resolution. Both files are square and this size. */
    private static final int ICON_SOURCE = 32;
    /**
     * The smallest the sprites are allowed to get, in device pixels.
     * <p>
     * A 4:1 reduction is the point where bilinear starts dropping detail rather
     * than averaging it -- there are no mipmaps on a GUI texture, so each
     * destination pixel takes four texels out of the sixteen it covers. Below
     * this the sword and the shield stop being a sword and a shield.
     */
    private static final int ICON_FLOOR = 8;

    /**
     * How big a stat sprite is drawn, in DEVICE pixels, and always an exact
     * power-of-two reduction of {@link #ICON_SOURCE}.
     *
     * <h2>Why the device grid and not the line height</h2>
     * Sizing the sprite to the text meant sizing a 32-pixel image to whatever
     * number of device pixels the current GUI scale happened to produce -- 24 at
     * one scale, 27 at another. Neither divides 32, so every destination pixel
     * was a blend of a fractional number of texels and the blade came out soft
     * while the digits beside it stayed sharp. Halving from the source instead
     * (32, 16, 8) makes every destination pixel an exact block of texels, which
     * is what integer scaling means here and the only way this image reduces
     * cleanly.
     * <p>
     * The largest such size that still fits inside the text's own line, floored
     * at {@link #ICON_FLOOR} so the compact panels shrink the words without
     * shrinking the sprite into mush.
     */
    private static int iconDevice(Font font, float scale)
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        int line = Math.max(1, (int) Math.round(font.lineHeight * scale * Math.max(1D, gui)));
        int size = ICON_SOURCE;
        while(size > ICON_FLOOR && (size >> 1) >= line)
        {
            size >>= 1;
        }
        return size;
    }

    /** {@link #iconDevice} in the screen units a scaled matrix is built from. */
    private static float iconScreen(Font font, float scale)
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (iconDevice(font, scale) / Math.max(1D, gui));
    }

    /**
     * The room a sprite takes in the line, in the pill matrix's own units.
     * <p>
     * Layout has to be whole numbers, so this rounds -- but the DRAW does not
     * use it for size, only for where the pen goes next. The sprite itself is
     * drawn from {@link #iconScreen} in its own matrix, so rounding here can
     * cost a little air beside the number and can never blur the image.
     */
    private static int iconSize(Font font, float scale)
    {
        return Math.max(1, Math.round(iconScreen(font, scale) / scale));
    }
'''

W_OLD = '''            w += part.icon() != null ? iconSize(font) + 1 : font.width(part.text());'''
W_NEW = '''            w += part.icon() != null ? iconSize(font, scale) + 1 : font.width(part.text());'''

SIG_OLD = '''    private static int partsWidth(Font font, List<Part> parts)'''
SIG_NEW = '''    private static int partsWidth(Font font, List<Part> parts, float scale)'''

CALL_OLD = '''            int textW = Math.round(partsWidth(font, group) * b.factScale);'''
CALL_NEW = '''            int textW = Math.round(partsWidth(font, group, b.factScale) * b.factScale);'''

LAY_OLD = '''            int w = Math.min(usable, Math.round(partsWidth(font, group) * scale) + pad * 2);'''
LAY_NEW = '''            int w = Math.min(usable, Math.round(partsWidth(font, group, scale) * scale) + pad * 2);'''

D_OLD = '''                if(part.icon() != null)
                {
                    // Square on the line's own height, so the sword and the
                    // digits beside it are the same size whatever the scale.
                    // Square, and on the text's own optical centre -- see
                    // iconSize for why that is a pixel short of the line.
                    int size = iconSize(font);
                    de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(g, part.icon(),
                        penX, penY, size, size, 0F, 0F, 1F, 1F,
                        de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.NO_TINT);
                    penX += size + 1;
                }'''

D_NEW = '''                if(part.icon() != null)
                {
                    // The sprite gets its OWN matrix rather than riding the
                    // pill's, because the pill's scale is whatever fraction the
                    // text wanted and this image needs a power of two. Undo that
                    // scale to reach screen units, then map all 32 source pixels
                    // onto the size iconDevice picked.
                    float side = iconScreen(font, b.factScale);
                    // Minecraft draws its glyphs eight pixels tall inside a
                    // nine-pixel line, sitting at the top with the ninth left for
                    // descenders, so the text's optical centre is four pixels
                    // down -- not four and a half. The sprite is centred on that
                    // rather than on the line, so it sits with the digits
                    // whatever size the two of them end up.
                    float midY = (penY + (font.lineHeight - 1) / 2F) * b.factScale;
                    g.pose().pushMatrix();
                    g.pose().scale(1F / b.factScale, 1F / b.factScale);
                    g.pose().translate(penX * b.factScale, midY - side / 2F);
                    g.pose().scale(side / ICON_SOURCE, side / ICON_SOURCE);
                    de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(g, part.icon(),
                        0, 0, ICON_SOURCE, ICON_SOURCE, 0F, 0F, 1F, 1F,
                        de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.NO_TINT);
                    g.pose().popMatrix();
                    penX += iconSize(font, b.factScale) + 1;
                }'''

for tree in ('mc1211', 'mc262'):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/CardInfoPanel.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in ((HELPER_OLD, HELPER_NEW), (SIG_OLD, SIG_NEW), (W_OLD, W_NEW),
                     (CALL_OLD, CALL_NEW), (LAY_OLD, LAY_NEW), (D_OLD, D_NEW)):
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (p, s.count(old), old[:60]))
        s = s.replace(old, new)
    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)
