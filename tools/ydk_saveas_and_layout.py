"""Export through a real Save As dialog, and two editor layout fixes.

1. Exporting asked nothing and wrote into ydm_decks. A deck is most useful in
   whatever folder the player's other client reads, so it asks.
2. Sort Deck / Import / Export were fixed widths that did not match their
   labels. Each is now measured from its own text, as Save and Exit already is.
3. The owned-card count sat at a fixed offset from the panel bottom, which left
   it floating well below the grid. It now sits against the grid's bottom edge.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


F = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckFiles.java"

# ---------- 1. Save As ----------
sub(F, """            Path file = folder().resolve(safe + ".ydk");""",
    """            Path file = chooseSaveTarget(safe + ".ydk");
            if(file == null)
            {
                return new Result(false, "");   // cancelled, which is an answer
            }""", "export asks where")

sub(F, """            return new Result(true, "Exported to " + file);""",
    """            return new Result(true, "Exported to " + file.getFileName());""",
    "export message")

sub(F, """    /**
     * Asks the system for a .ydk.""",
    """    /**
     * Asks where to write a deck, suggesting our own folder and name.
     * <p>
     * A real Save As dialog, so the file can go wherever the player keeps
     * their decks -- an EDOPro deck folder, most usefully -- rather than only
     * into ours. Falls back to {@link #FOLDER} when the native dialog is
     * unavailable, so exporting still works on a build without the library.
     *
     * @return where to write, or null if the player cancelled
     */
    private static Path chooseSaveTarget(String suggested) throws IOException
    {
        try
        {
            org.lwjgl.PointerBuffer filters = org.lwjgl.BufferUtils.createPointerBuffer(1);
            filters.put(org.lwjgl.system.MemoryUtil.memUTF8("*.ydk"));
            filters.flip();
            String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
                "Export deck", folder() + File.separator + suggested, filters, "YGOPro deck");
            if(picked == null)
            {
                return null;
            }
            // Someone who typed a bare name still means a .ydk.
            return picked.toLowerCase(Locale.ROOT).endsWith(".ydk")
                ? Path.of(picked) : Path.of(picked + ".ydk");
        }
        catch(Throwable unavailable)
        {
            return folder().resolve(suggested);
        }
    }

    /**
     * Asks the system for a .ydk.""", "chooseSaveTarget")

E = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckEditorScreen.java"

# ---------- 2. buttons measured from their own labels ----------
sub(E, """        addRenderableWidget(new HubWidgets.TextureButton(leftX + pad, controlsY, 92, 20,
            Component.literal("Sort Deck"), pressed ->
        {
            sortDeck();
            refusal = "";
        }));

        // .ydk, beside the deck they act on. Import makes a NEW deck rather
        // than overwriting the open one: a file arriving is not a reason to
        // lose what is already being built.
        addRenderableWidget(new HubWidgets.TextureButton(leftX + pad + 96, controlsY, 62, 20,
            Component.literal("Import"), pressed -> importDeck()));
        addRenderableWidget(new HubWidgets.TextureButton(leftX + pad + 162, controlsY, 62, 20,
            Component.literal("Export"), pressed ->
        {
            DeckFiles.Result result = DeckFiles.export(EditorState.deck());
            refusal = result.message();
        }));""",
    """        // Each sized from its own label rather than from a number picked to
        // suit the longest, so they read as one row instead of three
        // differently-padded boxes. Same measure as Save and Exit uses.
        Component sortLabel = Component.literal("Sort Deck");
        Component importLabel = Component.literal("Import");
        Component exportLabel = Component.literal("Export");
        int rowX = leftX + pad;

        int sortW = buttonWidth(sortLabel);
        addRenderableWidget(new HubWidgets.TextureButton(rowX, controlsY, sortW, 20,
            sortLabel, pressed ->
        {
            sortDeck();
            refusal = "";
        }));
        rowX += sortW + BUTTON_GAP;

        // .ydk, beside the deck they act on. Import makes a NEW deck rather
        // than overwriting the open one: a file arriving is not a reason to
        // lose what is already being built.
        int importW = buttonWidth(importLabel);
        addRenderableWidget(new HubWidgets.TextureButton(rowX, controlsY, importW, 20,
            importLabel, pressed -> importDeck()));
        rowX += importW + BUTTON_GAP;

        addRenderableWidget(new HubWidgets.TextureButton(rowX, controlsY,
            buttonWidth(exportLabel), 20, exportLabel, pressed ->
        {
            DeckFiles.Result result = DeckFiles.export(EditorState.deck());
            refusal = result.message();
        }));""", "deck buttons fit their text")

sub(E, """    /**
     * Reads a .ydk into a new deck, and says what happened either way.""",
    """    /** Space between the buttons on a control row. */
    private static final int BUTTON_GAP = 6;

    /**
     * How wide a button has to be for its label.
     * <p>
     * A floor as well as a measure, so a one-word button is still big enough
     * to aim at rather than shrinking to the width of its text.
     */
    private int buttonWidth(Component label)
    {
        return Math.max(56, font.width(label) + 16);
    }

    /**
     * Reads a .ydk into a new deck, and says what happened either way.""",
    "buttonWidth helper")

# ---------- 3. the count sits under the grid ----------
sub(E, """    private int trunkVisibleRows()""",
    """    /**
     * Where the collection's card count goes: against the bottom edge of the
     * grid it counts.
     * <p>
     * It used to be placed from the panel's bottom, so it floated in whatever
     * space happened to be left under the last row -- and moved further away
     * as the card size changed, since the row count changes with it.
     */
    private int trunkCountY()
    {
        return trunkGridTop() + trunkVisibleRows() * (trunkCardH + gap) + 3;
    }

    private int trunkVisibleRows()""", "trunkCountY helper")

for label in ("filters-open count", "grid count"):
    s = io.open(E, encoding="utf-8").read()
    old = '(int)(panelTop + panelH - pad - 34), 0xFF7A8090, true);'
    assert old in s, label
    s = s.replace(old, '(int)trunkCountY(), 0xFF7A8090, true);', 1)
    io.open(E, "w", encoding="utf-8", newline="\n").write(s)
    print("   ok:", label)
