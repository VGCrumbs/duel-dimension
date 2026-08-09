"""Fix: a position prompt could not be answered at all.

choose(int) switches on prompt.kind(). It handles CHOOSE, MULTI/PLACES and SORT,
and ends in `default -> { }` -- an empty case that POSITION fell into. So the
click was routed correctly, hit-tested correctly, highlighted correctly, and
then silently thrown away. pickerFooter gives POSITION no Confirm button either
(needsConfirm is `default -> false`) and the prompt is built non-cancelable, so
the panel contained NOTHING that could answer it. The duel could not continue.

The declutter is separate and cosmetic: "Choose a position" was being drawn
three times over -- the hint banner across the top of the screen, the panel's
own header, and the panel's footer hint.
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

# ---------- 1. THE SOFTLOCK ----------
sub("""        switch(prompt.kind())
        {
            case CHOOSE -> answer(new int[] {index}, 0);""",
    """        switch(prompt.kind())
        {
            // POSITION belongs here and was missing, which meant a position
            // click fell through to the empty `default` below and was dropped:
            // the panel drew, the cell highlighted, and nothing happened. It is
            // always exactly one pick -- the translator builds it min 1, max 1 --
            // so it answers on the click like any other single choice.
            case CHOOSE, POSITION -> answer(new int[] {index}, 0);""",
    "choose() handles POSITION")

# ---------- 2. the footer no longer repeats the header ----------
sub("""            case POSITION -> "Choose a position";""",
    """            // Blank: the panel header already asks the question and each
            // cell is labelled with its answer. Saying it a third time was
            // noise in a panel that has room for none.
            case POSITION -> "";""",
    "footer hint dropped")

sub("""        poseStack.text(font, need, at.x() + PICKER_PAD, footerY, 0xFF9FA6B4, true);""",
    """        if(!need.isBlank())
        {
            poseStack.text(font, need, at.x() + PICKER_PAD, footerY, 0xFF9FA6B4, true);
        }""",
    "blank footer draws nothing")

# ---------- 3. the banner does not repeat the panel ----------
sub("""        if(prompt == null || answered || prompt.title() == null || prompt.title().isBlank())
        {
            return;
        }
        String text = prompt.title();""",
    """        if(prompt == null || answered || prompt.title() == null || prompt.title().isBlank())
        {
            return;
        }
        if(picker != null)
        {
            // The modal panel is up and carries the same title in its header.
            // The banner exists to caption a choice made ON the board, where
            // there is no panel to put a heading on.
            return;
        }
        String text = prompt.title();""",
    "banner yields to the panel")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("done")
