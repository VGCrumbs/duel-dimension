"""Four fixes: deck row padding, phase/draw volume, and a trunk size slider.

1. Each deck part reserved a whole spare row even when its last row already had
   empty slots, so a one-card Extra Deck drew a container two rows tall. The
   spare exists so there is somewhere to drop a card; it is only actually
   needed when the last row is FULL.

2. Every duel effect sound played at 0.6. The engine announcing a phase change
   is the one the player is waiting on, so it gets its own level -- while the
   phase BUTTON keeps its own sound and volume, which is what makes pressing a
   phase and the phase actually turning sound like two different things.

3. The trunk gets a size slider above Save and Exit. Card size already drives
   the column count and the visible rows, so scaling it reflows the grid with
   no other change.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


E = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckEditorScreen.java"

# ---------- 1. no spare row when the last one has gaps ----------
sub(E, """    private int rowsFor(DeckList.Part part)
    {
        int columns = partColumns(part);
        int max = (int)Math.ceil(part.capacity() / (double)columns);
        int used = (int)Math.ceil(EditorState.deck().partFor(part).size() / (double)columns);
        return Math.max(1, Math.min(max, used + 1));
    }""",
    """    private int rowsFor(DeckList.Part part)
    {
        int columns = partColumns(part);
        int max = (int)Math.ceil(part.capacity() / (double)columns);
        int held = EditorState.deck().partFor(part).size();
        int used = (int)Math.ceil(held / (double)columns);
        // The spare row is a place to drop a card, and it is only needed when
        // the last row is FULL. Reserving one unconditionally meant a part
        // holding a single card drew a container two rows tall with an empty
        // row under it -- and did the same to every part, so the three
        // sections between them wasted three rows of the panel.
        int rows = held % columns == 0 ? used + 1 : used;
        return Math.max(1, Math.min(max, rows));
    }""", "rowsFor spare row")

# ---------- 3. the trunk size slider ----------
sub(E, "    private int trunkCardW;",
    """    private int trunkCardW;

    /**
     * How large the collection draws its cards, as a multiple of the size it
     * would pick on its own.
     * <p>
     * Static so it survives closing and reopening the editor: it is a way of
     * working, not a property of a deck. Everything else follows from the card
     * size -- the column count and the visible row count are both derived from
     * it -- so this one number reflows the whole grid.
     */
    private static float trunkScale = 1F;

    private static final float TRUNK_SCALE_MIN = 0.6F;
    private static final float TRUNK_SCALE_MAX = 2.2F;""", "trunkScale field")

sub(E, """        trunkCardH = Math.max(8, Math.round(trunkCardW / aspect));
        // The budget is a hard ceiling, so it is enforced on the number that
        // actually decides the row pitch rather than trusted to the arithmetic
        // above. Costs at most one pixel of aspect accuracy.
        if(trunkCardH > fitH)""",
    """        // The player's own scale, last, so it multiplies the size the
        // collection would otherwise have chosen. Clamped to the same minimum
        // the automatic sizing uses, and to the panel's width so the largest
        // setting still leaves at least one column.
        trunkCardW = Math.max(layout.i("card.minWidth", 10),
            Math.round(trunkCardW * trunkScale));
        trunkCardW = Math.min(trunkCardW, Math.max(layout.i("card.minWidth", 10),
            rightW - pad * 2));
        trunkCardH = Math.max(8, Math.round(trunkCardW / aspect));
        // The budget is a hard ceiling, so it is enforced on the number that
        // actually decides the row pitch rather than trusted to the arithmetic
        // above. Costs at most one pixel of aspect accuracy. Skipped once the
        // player has scaled up on purpose -- the collection scrolls, so its
        // cards are allowed to be taller than one deck row's share.
        if(trunkScale <= 1F && trunkCardH > fitH)""", "trunkScale applied")

# The slider, on the controls row beside Save and Exit.
sub(E, """        Component leave = Component.literal("Save and Exit");
        int leaveW = Math.max(80, font.width(leave) + 16);
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - leaveW,
            controlsY, leaveW, 20, leave, pressed -> saveAndExit()));""",
    """        Component leave = Component.literal("Save and Exit");
        int leaveW = Math.max(80, font.width(leave) + 16);
        addRenderableWidget(new HubWidgets.TextureButton(rightX + rightW - pad - leaveW,
            controlsY, leaveW, 20, leave, pressed -> saveAndExit()));

        // Card size for the collection, directly above Save and Exit. A slider
        // rather than a cycle of fixed sizes: the useful size depends on how
        // many cards you own and how big the window is, and neither is
        // something a fixed list can answer.
        addRenderableWidget(new TrunkSizeSlider(rightX + rightW - pad - leaveW,
            controlsY - 22, leaveW, 16));""", "slider widget added")

sub(E, """    /** Whatever card is under the cursor, in either panel. */""",
    """    /**
     * The collection's card size, as a slider.
     * <p>
     * Rebuilds the screen on every change rather than only on release, because
     * the grid reflowing under the cursor IS the feedback -- a size you cannot
     * see until you let go is one you have to guess at.
     */
    private class TrunkSizeSlider extends net.minecraft.client.gui.components.AbstractSliderButton
    {
        TrunkSizeSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(),
                (trunkScale - TRUNK_SCALE_MIN) / (TRUNK_SCALE_MAX - TRUNK_SCALE_MIN));
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal("Card Size  "
                + Math.round((TRUNK_SCALE_MIN
                    + (float)value * (TRUNK_SCALE_MAX - TRUNK_SCALE_MIN)) * 100F) + "%"));
        }

        @Override
        protected void applyValue()
        {
            trunkScale = TRUNK_SCALE_MIN + (float)value * (TRUNK_SCALE_MAX - TRUNK_SCALE_MIN);
            // The row the scroll sits on has changed size, so where it points
            // has to be re-clamped; resize() does that for both grids.
            resize();
        }

        /** Silent: a slider that clicks on every step of a drag is noise. */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** Whatever card is under the cursor, in either panel. */""", "slider class")

# ---------- 2. the automatic phase change, and the draw, get their own level ----------
A = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelAnimations.java"
sub(A, """        if(sound != null)
        {
            // Master volume applies; these are UI sounds with no position.
            Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, 1F, 0.6F));
        }""",
    """        if(sound != null)
        {
            // Master volume applies; these are UI sounds with no position.
            Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, 1F, volumeFor(event.kind())));
        }
    }

    /** The general level for a duel effect sound. */
    private static final float EFFECT_VOLUME = 0.6F;

    /**
     * How loud one kind of event is.
     * <p>
     * Two are louder than the rest because they are the beats a player is
     * actually waiting on: the engine announcing that the phase has turned,
     * and a card being drawn. Note this is the AUTOMATIC phase change --
     * MSG_NEW_PHASE, the duel moving on by itself. Pressing a phase on the
     * phase bar is a different sound entirely
     * ({@code EngineDuelScreen.SlimPhaseButton.playDownSound}) at its own
     * level, which is what makes asking for a phase and the phase arriving
     * sound like two different things.
     */
    private static float volumeFor(DuelEvent.Kind kind)
    {
        return switch(kind)
        {
            case PHASE, DRAW -> 1F;
            default -> EFFECT_VOLUME;
        };""", "per-kind effect volume")

print("done")
