"""Name scaling goes. The bot menu's height comes from its rows, and the setting is a tick box."""
import sys

# ---- 1: the name is drawn at the body scale, full stop -------------------
FIELD_OLD = '''        /** The name's own scale, which is the body's unless it had to shrink. */
        float nameScale;
'''

DRAW_OLD = '''        g.pose().pushMatrix();
        // nameScale, not scale: a name too long for the panel was measured in
        // body() and stepped down until it fits.
        g.pose().scale(b.nameScale, b.nameScale);
        g.text(font, name, Math.round((x + 6) / b.nameScale),
            Math.round((y + lift + 5) / b.nameScale), MenuInk.title(), MenuInk.shadow());'''

DRAW_NEW = '''        g.pose().pushMatrix();
        g.pose().scale(b.scale, b.scale);
        g.text(font, name, Math.round((x + 6) / b.scale),
            Math.round((y + lift + 5) / b.scale), MenuInk.title(), MenuInk.shadow());'''

# ---- 2: the bot menu ----------------------------------------------------
PANEL_OLD = '''    /** Tall enough for the rows it has, but never taller than the window. */
    private int panelH()
    {
        if(openList == null)
        {
            return 158;
        }'''

PANEL_NEW = '''    /** Where each root row starts, measured down from the panel's top. */
    private static final int ROOT_FIRST = 40;
    /** The ordinary step from one root row to the next. */
    private static final int ROOT_STEP = 26;
    /** A wider step, once, to set the setting apart from the three decks. */
    private static final int ROOT_SPLIT = 30;
    private static final int BUTTON_H = 20;
    /** Air under the last root row. */
    private static final int ROOT_FOOT = 12;

    /**
     * The root menu's rows, in the order {@code init} lays them out.
     *
     * <h2>Why this is a list and not five numbers in two places</h2>
     * The height used to be the literal 158, worked out by hand for the four
     * rows there were at the time. Destiny Draws made it five and nothing
     * recomputed anything, so Cancel hung ten pixels below the panel it was
     * supposed to be inside. Now the layout and the height read the same array
     * and a sixth row cannot desynchronise them.
     */
    private static int[] rootRows()
    {
        return new int[] {
            ROOT_FIRST,
            ROOT_FIRST + ROOT_STEP,
            ROOT_FIRST + ROOT_STEP * 2,
            ROOT_FIRST + ROOT_STEP * 2 + ROOT_SPLIT,
            ROOT_FIRST + ROOT_STEP * 3 + ROOT_SPLIT};
    }

    /** Tall enough for the rows it has, but never taller than the window. */
    private int panelH()
    {
        if(openList == null)
        {
            int[] rows = rootRows();
            return rows[rows.length - 1] + BUTTON_H + ROOT_FOOT;
        }'''

INIT_OLD = '''        int x = panelX() + 12;
        int w = panelW() - 24;
        int y = panelY() + 40;

        if(openList == null)
        {
            addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
                Component.literal("Starter Deck"),
                pressed -> open(DuelBotEntity.STARTER)));
            y += 26;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
                Component.literal("Structure Deck"),
                pressed -> open(DuelBotEntity.STRUCTURE)));
            y += 26;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
                Component.literal("Custom Deck"),
                pressed -> open(DuelBotEntity.CUSTOM)));
            y += 30;
            // The bot's own setting, on the bot's own screen. Remembered
            // between bots by the client, because the answer is about the
            // player and not about which block they happened to place.
            addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
                Component.literal("Destiny Draws: " + (destinyDraw() ? "ON" : "OFF")),
                pressed ->
                {
                    de.cas_ual_ty.dueldimension.clientutil.DestinyDrawSettings
                        .setVersusBots(!destinyDraw());
                    rebuildWidgets();
                }));
            y += 26;
            addRenderableWidget(new HubWidgets.TextureButton(x, y, w, 20,
                Component.literal("Cancel"), pressed -> onClose()));
            return;
        }'''

INIT_NEW = '''        int x = panelX() + 12;
        int w = panelW() - 24;
        int y = panelY() + 40;

        if(openList == null)
        {
            int[] rows = rootRows();
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[0], w, BUTTON_H,
                Component.literal("Starter Deck"),
                pressed -> open(DuelBotEntity.STARTER)));
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[1], w, BUTTON_H,
                Component.literal("Structure Deck"),
                pressed -> open(DuelBotEntity.STRUCTURE)));
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[2], w, BUTTON_H,
                Component.literal("Custom Deck"),
                pressed -> open(DuelBotEntity.CUSTOM)));
            // The bot's own setting, on the bot's own screen. Remembered
            // between bots by the client, because the answer is about the
            // player and not about which block they happened to place.
            //
            // A TICK BOX rather than a button reading "ON". A button is a thing
            // you press to make something happen; this is a state that is either
            // set or not, and the box says which at a glance without having to
            // read a word at the end of a sentence.
            addRenderableWidget(new HubWidgets.CheckBox(x, panelY() + rows[3], w, BUTTON_H,
                Component.literal("Destiny Draws"), ChooseProgramScreen::destinyDraw,
                pressed ->
                {
                    de.cas_ual_ty.dueldimension.clientutil.DestinyDrawSettings
                        .setVersusBots(!destinyDraw());
                    rebuildWidgets();
                }));
            addRenderableWidget(new HubWidgets.TextureButton(x, panelY() + rows[4], w, BUTTON_H,
                Component.literal("Cancel"), pressed -> onClose()));
            return;
        }'''

# ---- 3: the tick box ----------------------------------------------------
CHECKBOX_ANCHOR = '''    /**
     * A button whose label is a picture.'''

CHECKBOX = '''    /**
     * A tick box: a small square that is either ticked or not, and a label
     * beside it.
     *
     * <h2>Why not a button that says ON</h2>
     * A button is a thing you press to make something happen. A setting is a
     * state that is either set or not, and reading it off the end of a label --
     * "Destiny Draws: ON" -- means reading a sentence to answer a yes/no. The
     * box answers it before the words are read, and the words then say what the
     * answer is about.
     * <p>
     * The state is a SUPPLIER rather than a stored flag, so a box whose setting
     * is changed from somewhere else is right on the next frame instead of on
     * the next rebuild.
     */
    public static class CheckBox extends TextureButton
    {
        private final java.util.function.BooleanSupplier ticked;

        public CheckBox(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier ticked, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.ticked = ticked;
        }

        @Override
        RENDER_SIGNATURE
        {
            RENDER_PREAMBLE
            int box = Math.min(getHeight(), 14);
            int boxY = getY() + (getHeight() - box) / 2;
            NineSlice.draw(graphics, HubTextures.SLOT, getX(), boxY, box, box);
            if(ticked.getAsBoolean())
            {
                // CHECK is white art meant to be tinted; green is the same
                // "yes, this one" the shops mark a collected tile with.
                int tick = box - 4;
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                    HubTextures.CHECK, getX() + 2, boxY + 2, tick, tick,
                    de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.tint(
                        0.49F, 0.89F, 0.55F, 1F));
            }
            // Beside the box and LEFT aligned, not centred in the row: a label
            // that centred itself would drift as the word changed length and
            // would sometimes sit under its own tick.
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            String text = getMessage().getString();
            graphics.text(font, text, getX() + box + 6, getY() + (getHeight() - 8) / 2,
                !active ? MenuInk.dim()
                    : isHoveredOrFocused() ? MenuInk.title() : MenuInk.label(),
                MenuInk.shadow());
        }
    }

'''

SIGNATURES = {
    'mc1211': ('''protected void renderWidget(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
            int mouseX, int mouseY, float partialTick)''',
               '''// 26.2 describes itself into a render state; 1.21.1 draws now.
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);
'''),
    'mc262': ('''protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)''', ''),
}


def cut(s, start, end, path, what):
    i = s.find(start)
    if i < 0:
        sys.exit('%s: no start for %s' % (path, what))
    j = s.find(end, i)
    if j < 0:
        sys.exit('%s: no end for %s' % (path, what))
    return s[:i] + s[j + len(end):]


def edit(path, pairs):
    raw = open(path, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (path, s.count(old), old[:60]))
        s = s.replace(old, new)
    open(path, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))


for tree in ('mc1211', 'mc262'):
    base = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/'

    panel = base + 'CardInfoPanel.java'
    raw = open(panel, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    if s.count(FIELD_OLD) != 1:
        sys.exit('%s: %d matches for the nameScale field' % (panel, s.count(FIELD_OLD)))
    s = s.replace(FIELD_OLD, '')
    s = cut(s, '        // THE NAME SHRINKS RATHER THAN BLEEDS.',
            '            b.nameScale = next;\n        }\n', panel, 'the name shrink')
    if s.count(DRAW_OLD) != 1:
        sys.exit('%s: %d matches for the name draw' % (panel, s.count(DRAW_OLD)))
    s = s.replace(DRAW_OLD, DRAW_NEW)
    open(panel, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))

    sig, preamble = SIGNATURES[tree]
    box = CHECKBOX.replace('RENDER_SIGNATURE', sig).replace('RENDER_PREAMBLE', preamble)
    edit(base + 'HubWidgets.java', [(CHECKBOX_ANCHOR, box + CHECKBOX_ANCHOR)])
    edit(base + 'ChooseProgramScreen.java', [(PANEL_OLD, PANEL_NEW), (INIT_OLD, INIT_NEW)])
    print('patched', tree)
