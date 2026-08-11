"""Give the Settings tab sub-sections, the way the Decks tab already has them.

Settings had grown to hold three unrelated things -- the duel mat, the music and
the card back -- all fighting over one 440x200 body. The signs of the squeeze
were in the code: the mat preview had been cut from 210x92 to 150x66 purely to
free a strip on the right, and the card backs were down to two visible tiles with
a scroll counter under them.

Splitting them costs nothing new to learn, because the Decks tab already does
exactly this: a row of TabButtons at bodyTop + 3, 68x16 on a 70 pitch, with the
content starting at bodyTop + 22. This follows that, so the two tabs behave the
same way rather than each having its own idea.

Each view now owns the whole body, so the mat preview goes back to full size and
the card-back strip shows seven tiles instead of two.
"""
import io

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DuelHubScreen.java"
s = io.open(p, encoding="utf-8").read()


def once(old, new):
    global s
    assert s.count(old) == 1, (s.count(old), old[:90])
    s = s.replace(old, new, 1)


# ---- 1. the enum, beside DeckView which it mirrors -------------------------
once("""    private enum DeckView
    {""",
"""    /**
     * The Settings tab's own sub-sections.
     * <p>
     * Three unrelated preferences were sharing one body and crowding each other
     * out — the mat preview had been shrunk to make room for the card backs, and
     * the backs were down to two visible tiles. Each of these now owns the whole
     * body instead. Same shape as {@link DeckView}, deliberately: the Decks tab
     * already introduced sub-tabs and a second idiom would only be a second
     * thing to learn.
     */
    private enum SettingsView
    {
        MAT("Duel Mat"),
        CARDS("Cards"),
        AUDIO("Audio");

        private final String label;

        SettingsView(String label)
        {
            this.label = label;
        }
    }

    private SettingsView settingsView = SettingsView.MAT;

    private enum DeckView
    {""")

# ---- 2. the sub-tab row + per-view widget building -------------------------
once("""        matPicker = null;
        if(section == Section.SETTINGS)
        {
            matPicker = new MatColourPicker(left + PAD + 6, bodyTop + 26, 120, 14,
                de.cas_ual_ty.dueldimension.clientutil.DuelClientState.matColour());
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                96, 20, Component.literal("Apply"), pressed -> applyMat()));
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(de.cas_ual_ty.dueldimension.clientutil.DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));
""",
"""        matPicker = null;
        if(section == Section.SETTINGS)
        {
            int viewX = left + PAD + 4;
            for(SettingsView candidate : SettingsView.values())
            {
                SettingsView targetView = candidate;
                addRenderableWidget(new HubWidgets.TabButton(viewX, bodyTop + 3, 68, 16,
                    Component.literal(candidate.label), () -> settingsView == targetView,
                    pressed ->
                {
                    settingsView = targetView;
                    backScroll = 0;
                    rebuild();
                }));
                viewX += 70;
            }
        }
        if(section == Section.SETTINGS && settingsView == SettingsView.MAT)
        {
            matPicker = new MatColourPicker(left + PAD + 6, bodyTop + 48, 120, 14,
                de.cas_ual_ty.dueldimension.clientutil.DuelClientState.matColour());
            // Apply and Reset belong to the mat alone -- a colour is dragged and
            // needs settling on. They used to be built for the whole tab, so the
            // music and card-back views offered an Apply that applied nothing.
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                96, 20, Component.literal("Apply"), pressed -> applyMat()));
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(de.cas_ual_ty.dueldimension.clientutil.DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));
        }
        if(section == Section.SETTINGS && settingsView == SettingsView.AUDIO)
        {
""")

# The music block's two buttons move to the left of a body they now have to
# themselves.
once("""            HubWidgets.TextureButton trackButton = new HubWidgets.TextureButton(
                left + PAD + 168, bodyTop + 162, 130, 18,""",
"""            HubWidgets.TextureButton trackButton = new HubWidgets.TextureButton(
                left + PAD + 6, bodyTop + 48, 130, 18,""")
once("""            HubWidgets.TextureButton muteButton = new HubWidgets.TextureButton(
                left + PAD + 302, bodyTop + 162, 68, 18,""",
"""            HubWidgets.TextureButton muteButton = new HubWidgets.TextureButton(
                left + PAD + 140, bodyTop + 48, 68, 18,""")

once("""            addRenderableWidget(muteButton);

            // ---- card back, in the strip beside the mat ----
            // On click, for the reason the two music buttons are: a back is a
            // single named choice and is its own confirmation. Apply belongs to
            // the mat, whose colour is dragged and needs settling on.
            buildCardBackTiles(bodyTop);
        }
""",
"""            addRenderableWidget(muteButton);
        }
        if(section == Section.SETTINGS && settingsView == SettingsView.CARDS)
        {
            // On click, for the reason the two music buttons are: a back is a
            // single named choice and is its own confirmation. Apply belongs to
            // the mat, whose colour is dragged and needs settling on.
            buildCardBackTiles(bodyTop);
        }
""")

# ---- 3. the strip owns the body now ---------------------------------------
once("""    private int backStripX()
    {
        return left + PAD + 326;
    }""",
"""    private int backStripX()
    {
        return left + PAD + 6;
    }""")
once("    private static final int BACK_TILES = 2;",
     "    private static final int BACK_TILES = 7;")

# ---- 4. drawing, per view --------------------------------------------------
once("""        int x = left + PAD + 10;
        graphics.text(font, "Duel Mat", x, bodyTop + 10, 0xFFF4D089, true);
        if(matPicker != null)
        {
            matPicker.render(graphics);
            // The preview is the real mat texture under the chosen tint, so
            // what is shown here is exactly what reaches the table.
            //
            // 150x66 rather than the 210x92 it was, to leave the strip on the
            // right for the card backs. custom.png is 1024x448, so 2.27 keeps
            // its 2.29 -- the mat is smaller here, not squashed.
            matPicker.renderPreview(graphics, left + PAD + 168, bodyTop + 30, 150, 66);
            String hex = String.format("#%06X", matPicker.colour());
            graphics.text(font, hex, left + PAD + 168, bodyTop + 102, 0xFFC2C9D6, true);
        }
        // The heading its two buttons sit under. Drawn here rather than built
        // as a widget because it is a label, and this panel draws its own.
        graphics.text(font, "Duel Music", left + PAD + 168, bodyTop + 150, 0xFFF4D089, true);

        // ---- card back ----
        // Only the words: each tile paints its own back, because this runs
        // before the widgets and would otherwise be covered by them. The names
        // are safe here -- they sit below the tiles rather than inside them.
        graphics.text(font, "Card Back", backStripX(), bodyTop + 10, 0xFFF4D089, true);""",
"""        // Everything below the sub-tab row, which occupies bodyTop + 3 to + 19.
        int headingY = bodyTop + 32;
        if(settingsView == SettingsView.MAT && matPicker != null)
        {
            graphics.text(font, "Duel Mat", left + PAD + 6, headingY, 0xFFF4D089, true);
            matPicker.render(graphics);
            // The preview is the real mat texture under the chosen tint, so
            // what is shown here is exactly what reaches the table. Back to
            // 210x92 now that the card backs are not sharing this body --
            // custom.png is 1024x448 and 2.28 keeps its 2.29.
            matPicker.renderPreview(graphics, left + PAD + 168, bodyTop + 48, 210, 92);
            String hex = String.format("#%06X", matPicker.colour());
            graphics.text(font, hex, left + PAD + 168, bodyTop + 146, 0xFFC2C9D6, true);
            return;
        }
        if(settingsView == SettingsView.AUDIO)
        {
            // Drawn here rather than built as a widget because it is a label,
            // and this panel draws its own.
            graphics.text(font, "Duel Music", left + PAD + 6, headingY, 0xFFF4D089, true);
            return;
        }

        // ---- card back ----
        // Only the words: each tile paints its own back, because this runs
        // before the widgets and would otherwise be covered by them. The names
        // are safe here -- they sit below the tiles rather than inside them.
        graphics.text(font, "Card Back", backStripX(), headingY, 0xFFF4D089, true);""")

# The tiles and their captions move down with the heading.
once("            CardBackTile tile = new CardBackTile(backStripX() + slot * BACK_PITCH, bodyTop + 26,",
     "            CardBackTile tile = new CardBackTile(backStripX() + slot * BACK_PITCH, bodyTop + 48,")
once("""                backStripX() + slot * BACK_PITCH + (BACK_W - font.width(name)) / 2,
                bodyTop + 26 + BACK_H + 4, on ? 0xFFF4D089 : 0xFFC2C9D6, true);""",
"""                backStripX() + slot * BACK_PITCH + (BACK_W - font.width(name)) / 2,
                bodyTop + 48 + BACK_H + 4, on ? 0xFFF4D089 : 0xFFC2C9D6, true);""")
once("""                backStripX(), bodyTop + 26 + BACK_H + 18, 0xFF7A8090, true);""",
"""                backStripX(), bodyTop + 48 + BACK_H + 18, 0xFF7A8090, true);""")

# ---- 5. the wheel only scrolls on the tab that shows it --------------------
once("        if(section == Section.SETTINGS && CardBacks.ALL.size() > BACK_TILES)",
     "        if(section == Section.SETTINGS && settingsView == SettingsView.CARDS\n            && CardBacks.ALL.size() > BACK_TILES)")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("Settings tab split into Duel Mat / Cards / Audio")
