"""Make the deck editor's scrollbar a control instead of a readout.

I fixed the duel screen's bar last turn and never checked this one. The
collection's bar is drawn from HubTextures.SCROLLBAR with a properly sized
thumb, but nothing recorded where it was and DeckEditorScreen has no
mouseDragged at all -- so it reported the scroll position and accepted nothing.
It looked exactly like a control, which is worse than not drawing one.

The bar now records its own geometry as it draws, which keeps the hit box and
the picture derived from the same numbers and unable to drift apart.
"""
import io

def sub(old, new, label):
    global s
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    s = s.replace(old, new, 1)
    print("  ok:", label)

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckEditorScreen.java"
s = io.open(p, encoding="utf-8").read()

# ---------- state ----------
sub("""    private int trunkScroll;""",
    """    private int trunkScroll;
    /**
     * The collection scrollbar's track, recorded as it is drawn.
     * <p>
     * Taken from the draw rather than recomputed, so the box you can click can
     * never drift from the bar you can see.
     */
    private int trunkBarX;
    private int trunkBarY;
    private int trunkBarH;
    private int trunkBarThumbH;
    private int trunkBarRows;
    private int trunkBarVisibleRows;
    /** Where in the thumb the drag took hold, or -1 when not dragging. */
    private int trunkBarGrab = -1;""", "bar state")

# ---------- record as it draws ----------
sub("""        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        int thumbH = Math.max(12, height * visible / Math.max(1, total));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
    }""",
    """        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        int thumbH = Math.max(12, height * visible / Math.max(1, total));
        int thumbY = y + (height - thumbH) * offset / overflow;
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);

        // Remember what was drawn, so the click test and the picture are the
        // same numbers rather than two independent calculations.
        trunkBarX = x;
        trunkBarY = y;
        trunkBarH = height;
        trunkBarThumbH = thumbH;
        trunkBarRows = total;
        trunkBarVisibleRows = visible;
    }""", "record geometry")

# ---------- grab / drag ----------
sub("""    /** A scrollbar over a run of pixels rather than a count of rows. */""",
    """    /**
     * Takes hold of the collection's scrollbar.
     *
     * @return whether the bar took this click
     */
    private boolean grabTrunkBar(double mouseX, double mouseY)
    {
        if(trunkBarH <= 0 || maxTrunkScroll() <= 0
            || mouseX < trunkBarX - BAR_GRAB || mouseX >= trunkBarX + 4 + BAR_GRAB
            || mouseY < trunkBarY || mouseY >= trunkBarY + trunkBarH)
        {
            return false;
        }
        int overflow = Math.max(1, trunkBarRows - trunkBarVisibleRows);
        int thumbY = trunkBarY
            + (trunkBarH - trunkBarThumbH) * Math.min(trunkScroll, overflow) / overflow;
        // Grab the thumb where it was taken hold of; clicking bare track puts
        // the thumb's middle under the cursor, as every other bar does.
        trunkBarGrab = mouseY >= thumbY && mouseY < thumbY + trunkBarThumbH
            ? (int)(mouseY - thumbY) : trunkBarThumbH / 2;
        dragTrunkBar(mouseY);
        return true;
    }

    /** Scrubs the collection to wherever the thumb has been dragged. */
    private void dragTrunkBar(double mouseY)
    {
        int travel = trunkBarH - trunkBarThumbH;
        int max = maxTrunkScroll();
        if(travel <= 0 || max <= 0)
        {
            trunkScroll = 0;
            return;
        }
        double top = mouseY - trunkBarGrab - trunkBarY;
        trunkScroll = (int)Math.clamp(Math.round(top / travel * max), 0, max);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(trunkBarGrab >= 0)
        {
            dragTrunkBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /** A scrollbar over a run of pixels rather than a count of rows. */""",
    "grab + drag + mouseDragged")

# ---------- route the click, and release ----------
sub("""    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {""",
    """    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        // The scrollbar before anything else: it sits over the collection grid,
        // whose card hit test would otherwise swallow the click.
        if(event.button() == 0 && grabTrunkBar(event.x(), event.y()))
        {
            return true;
        }""", "click routes to the bar")

sub("""    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        double mouseX = event.x();""",
    """    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        trunkBarGrab = -1;
        double mouseX = event.x();""", "release")

sub("""    private int trunkScroll;
    /**
     * The collection scrollbar's track""",
    """    /** Slack either side of a scrollbar, so catching it does not need pixel aim. */
    private static final int BAR_GRAB = 3;

    private int trunkScroll;
    /**
     * The collection scrollbar's track""", "BAR_GRAB")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("done")
