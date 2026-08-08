"""Fixes the three defects the verifiers found in the dynamic-scaling change.

1. Double rounding. fitW = round(fitH * aspect) could round UP, and then
   cardH = round(cardW / aspect) rounded up again, so cardH could exceed the
   per-row budget by a pixel. Ten rows of that is ten pixels over, which brings
   the scrollbar back for exactly the full deck the change exists to fit. The
   card is now floored into the budget and cardH is capped at it outright.

2. trunkScroll was never re-clamped. It only ever shrank when the trunk's own
   scroll handler ran, but cardH now changes as the deck grows, so the trunk's
   visible-row count changes underneath a scroll position taken at the old one.

3. PackOpeningScreen's class javadoc still claimed the scissor kept Forge's
   bottom-origin framebuffer convention. It does not, and must not -- that was
   the bug fixed in clipToSummary.
"""
import io

P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DeckEditorScreen.java"
s = io.open(P, encoding="utf-8").read()

old = """        int rowsTotal = rowsFor(DeckList.Part.MAIN)
            + rowsFor(DeckList.Part.EXTRA) + rowsFor(DeckList.Part.SIDE);
        int heightBudget = deckViewHeight() - (headerH + sectionGap) * 3 - rowsTotal * gap;
        if(rowsTotal > 0 && heightBudget > 0)
        {
            int fitH = heightBudget / rowsTotal;
            int fitW = Math.max(layout.i("card.minWidth", 10), Math.round(fitH * aspect));
            cardW = Math.min(cardW, fitW);
        }
        cardH = Math.max(8, Math.round(cardW / aspect));"""

new = """        int rowsTotal = rowsFor(DeckList.Part.MAIN)
            + rowsFor(DeckList.Part.EXTRA) + rowsFor(DeckList.Part.SIDE);
        int heightBudget = deckViewHeight() - (headerH + sectionGap) * 3 - rowsTotal * gap;
        int fitH = Integer.MAX_VALUE;
        if(rowsTotal > 0 && heightBudget > 0)
        {
            fitH = heightBudget / rowsTotal;
            // FLOOR, not round. Rounding the width up and then deriving the
            // height from it rounds up a second time, so a card could end up a
            // pixel taller than its share of the budget -- and a pixel per row
            // over ten rows is ten pixels, which is the scrollbar back on
            // exactly the full deck this exists to fit.
            int fitW = Math.max(layout.i("card.minWidth", 10), (int)Math.floor(fitH * aspect));
            cardW = Math.min(cardW, fitW);
        }
        cardH = Math.max(8, Math.round(cardW / aspect));
        // The budget is a hard ceiling, so it is enforced on the number that
        // actually decides the row pitch rather than trusted to the arithmetic
        // above. Costs at most one pixel of aspect accuracy.
        if(cardH > fitH)
        {
            cardH = Math.max(8, fitH);
        }"""
assert old in s, "resize height-fit anchor"
s = s.replace(old, new, 1)

old = """        mainRows = rowsFor(DeckList.Part.MAIN);

        deckScroll = Math.max(0, Math.min(deckScroll, maxDeckScroll()));"""
new = """        mainRows = rowsFor(DeckList.Part.MAIN);

        deckScroll = Math.max(0, Math.min(deckScroll, maxDeckScroll()));
        // The trunk too. Its row height is cardH, and cardH now moves as the
        // deck grows -- so a scroll position taken when cards were small can
        // point past the end once they are large, and the grid draws rows that
        // are not there while the ones that are cannot be reached.
        trunkScroll = Math.max(0, Math.min(trunkScroll, maxTrunkScroll()));"""
assert old in s, "resize tail anchor"
s = s.replace(old, new, 1)

# A named bound, so the scroll handler and resize cannot disagree about it.
old = """    private int maxDeckScroll()
    {"""
new = """    /** How far the trunk can scroll, in rows. */
    private int maxTrunkScroll()
    {
        int rows = (EditorState.visible().size() + trunkColumns - 1) / trunkColumns;
        return Math.max(0, rows - trunkVisibleRows());
    }

    private int maxDeckScroll()
    {"""
assert old in s, "maxDeckScroll anchor"
s = s.replace(old, new, 1)

old = """            int rows = (EditorState.visible().size() + trunkColumns - 1) / trunkColumns;
            int visibleRows = trunkVisibleRows();
            trunkScroll = Math.max(0, Math.min(Math.max(0, rows - visibleRows),
                trunkScroll - (int)Math.signum(delta)));"""
new = """            trunkScroll = Math.max(0, Math.min(maxTrunkScroll(),
                trunkScroll - (int)Math.signum(delta)));"""
assert old in s, "trunk scroll handler anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("DeckEditorScreen: floor+cap, trunk scroll clamped")

P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/PackOpeningScreen.java"
s = io.open(P, encoding="utf-8").read()
old = """ * blit with a tint carrying the alpha. Scissor clipping moved from
 * {@code RenderSystem.enableScissor} to the extractor's own scissor, keeping the
 * bottom-origin framebuffer-pixel convention the Forge code used."""
new = """ * blit with a tint carrying the alpha. Scissor clipping moved from
 * {@code RenderSystem.enableScissor} to the extractor's own scissor, which
 * takes GUI-space CORNERS -- not the bottom-origin framebuffer pixels Forge's
 * did. Converting to the old convention is a real bug; see
 * {@code DeckEditorScreen.clipToDeckView}."""
assert old in s, "PackOpeningScreen javadoc anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("PackOpeningScreen: javadoc corrected")
