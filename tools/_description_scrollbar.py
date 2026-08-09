"""Scroll the card text from anywhere in the sidebar, and drag its bar.

Two complaints, one cause: the only way to move the text was to put the cursor
inside the text well itself and turn the wheel. The well is a narrow band near
the bottom of a tall sidebar, so the gesture had to be aimed, and the bar drawn
beside it looked like a control while being purely decorative.

Now:
 - the wheel works anywhere over the sidebar, which is the panel the text
   belongs to and which nothing else scrolls;
 - the bar is a real control: click the track to jump, drag the thumb to scrub.
   Dragging grabs the thumb at the point it was taken hold of, so it does not
   jump under the cursor on the first pixel of movement.
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

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java"
s = io.open(p, encoding="utf-8").read()

# ---------- state for the bar ----------
sub("""    private int descriptionX0;
    private int descriptionY0;
    private int descriptionX1;
    private int descriptionY1;""",
    """    private int descriptionX0;
    private int descriptionY0;
    private int descriptionX1;
    private int descriptionY1;
    /** The scroll bar's track, in GUI coordinates, so it can be clicked. */
    private int barX0;
    private int barY0;
    private int barX1;
    private int barY1;
    /** Height of the thumb, and where in it the drag was taken hold of. */
    private int barThumbH;
    private int barGrabOffset = -1;""", "bar state")

# ---------- the wheel: the whole sidebar, not just the well ----------
sub("""        // Reading a long effect. Tested before the picker is ruled out only in
        // that it needs the wheel to be over the sidebar, which the picker
        // never covers -- so the two can never both claim a scroll.
        if(descriptionMaxScroll > 0 && mouseX >= descriptionX0 && mouseX < descriptionX1
            && mouseY >= descriptionY0 && mouseY < descriptionY1)""",
    """        // Reading a long effect. Anywhere over the SIDEBAR, not just inside
        // the text well: the well is a narrow band low in a tall panel, and
        // requiring the cursor to be in it made reading a card a matter of
        // aiming. Nothing else in the sidebar scrolls, so there is nothing for
        // this to be confused with.
        if(descriptionMaxScroll > 0 && mouseX < SIDEBAR_W)""", "wheel over the whole sidebar")

# ---------- the bar becomes a control ----------
sub("""        // A thumb, and only when there is somewhere to scroll to -- otherwise
        // every short card grows a scrollbar that does nothing.
        if(descriptionMaxScroll > 0)
        {
            int trackX = scaledX + scaledW;
            int trackTop = wellTop + 1;
            int trackH = limit + 3 - trackTop;
            int thumbH = Math.max(6, Math.round(
                trackH * (float)viewH / (textLines.size() * DESCRIPTION_LINE_H)));
            int thumbY = trackTop + Math.round((trackH - thumbH)
                * (descriptionScroll / (float)descriptionMaxScroll));
            poseStack.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0x50000000);
            poseStack.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFB08A2A);
        }""",
    """        // A thumb, and only when there is somewhere to scroll to -- otherwise
        // every short card grows a scrollbar that does nothing.
        if(descriptionMaxScroll > 0)
        {
            int trackX = scaledX + scaledW;
            int trackTop = wellTop + 1;
            int trackH = limit + 3 - trackTop;
            int thumbH = Math.max(6, Math.round(
                trackH * (float)viewH / (textLines.size() * DESCRIPTION_LINE_H)));
            int thumbY = trackTop + Math.round((trackH - thumbH)
                * (descriptionScroll / (float)descriptionMaxScroll));
            // Wide enough to hit. Two pixels inside a 0.75 scale is a pixel and
            // a half on screen, which is a bar you can see but not catch.
            poseStack.fill(trackX, trackTop, trackX + BAR_W, trackTop + trackH, 0x50000000);
            poseStack.fill(trackX, thumbY, trackX + BAR_W, thumbY + thumbH, 0xFFB08A2A);

            // In GUI coordinates for the mouse, which does not live in the
            // 0.75 pose everything above is drawn in.
            barX0 = Math.round(trackX * 0.75F);
            barY0 = Math.round(trackTop * 0.75F);
            barX1 = Math.round((trackX + BAR_W) * 0.75F);
            barY1 = Math.round((trackTop + trackH) * 0.75F);
            barThumbH = Math.max(1, Math.round(thumbH * 0.75F));
        }
        else
        {
            barY1 = barY0;   // nothing to grab
        }""", "bar drawn and measured")

# ---------- click / drag / release ----------
sub("""    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)""",
    """    /**
     * Takes hold of the description's scroll bar.
     *
     * @return whether the bar took this click
     */
    private boolean grabDescriptionBar(double mouseX, double mouseY)
    {
        if(descriptionMaxScroll <= 0 || barY1 <= barY0
            || mouseX < barX0 - BAR_GRAB || mouseX >= barX1 + BAR_GRAB
            || mouseY < barY0 || mouseY >= barY1)
        {
            return false;
        }
        int thumbY = barY0 + Math.round((barY1 - barY0 - barThumbH)
            * (descriptionScroll / (float)descriptionMaxScroll));
        // Grabbing the thumb keeps the offset, so it does not jump under the
        // cursor. Clicking the track anywhere else centres the thumb there,
        // which is what every other scroll bar does.
        barGrabOffset = mouseY >= thumbY && mouseY < thumbY + barThumbH
            ? (int)(mouseY - thumbY) : barThumbH / 2;
        dragDescriptionBar(mouseY);
        return true;
    }

    /** Scrubs the text to wherever the thumb has been dragged. */
    private void dragDescriptionBar(double mouseY)
    {
        int travel = barY1 - barY0 - barThumbH;
        if(travel <= 0)
        {
            descriptionScroll = 0;
            return;
        }
        double top = mouseY - barGrabOffset - barY0;
        descriptionScroll = Math.clamp(
            Math.round(top / travel * descriptionMaxScroll), 0, descriptionMaxScroll);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(barGrabOffset >= 0)
        {
            dragDescriptionBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        barGrabOffset = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)""",
    "grab / drag / release")

sub("""        int button = event.button();
        if(button == 0 && clickPicker(mouseX, mouseY))
        {
            return true;
        }""",
    """        int button = event.button();
        // The scroll bar first: it sits over the sidebar, which the board hit
        // test would otherwise happily claim.
        if(button == 0 && grabDescriptionBar(mouseX, mouseY))
        {
            return true;
        }
        if(button == 0 && clickPicker(mouseX, mouseY))
        {
            return true;
        }""", "click routes to the bar")

sub("""    /** Lines the wheel moves the effect text by, in those same units. */
    private static final int DESCRIPTION_SCROLL_STEP = DESCRIPTION_LINE_H * 2;""",
    """    /** Lines the wheel moves the effect text by, in those same units. */
    private static final int DESCRIPTION_SCROLL_STEP = DESCRIPTION_LINE_H * 2;
    /** Width of the description's scroll bar, inside the sidebar's 0.75 scale. */
    private static final int BAR_W = 4;
    /** Slack either side of the bar, so catching it does not need pixel aim. */
    private static final int BAR_GRAB = 3;""", "bar constants")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("done")
