"""The actual crowding, and the same softlock in the Forge tree.

The panel looked cramped because the defence cell was genuinely drawing outside
its cell. drawPickerCard rotated a 62x90 quad a quarter turn about the cell
CENTRE, which lands a 90x62 footprint on that same centre -- 14px wider than the
cell on each side, so it overlapped the face-up card's edge and spilled past the
panel. A quarter turn swaps width and height, so the quad drawn BEFORE the turn
has to be the one whose TURNED footprint fits.

That is the same invariant DdBlitUtil documents: rotation and bounds agree only
when the region is square, and every other rotating caller squares up first.
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

E = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java"
FORGE = ("C:/Users/Admin/Desktop/YGO/CrumbyDueling/src/main/java/de/cas_ual_ty/"
         "dueldimension/clientutil/EngineDuelScreen.java")

# ---------- D3: the defence cell stops spilling out of its cell ----------
sub(E, """        poseStack.pose().pushMatrix();
        if(defence)
        {
            // Turned a quarter about the cell's own middle, as a defending
            // card lies on the table. rotate() takes radians; the Forge
            // quaternion took degrees.
            poseStack.pose().translate(cardX + at.cardW() / 2F, cardY + at.cardH() / 2F);
            poseStack.pose().rotate((float)Math.toRadians(90D));
            poseStack.pose().translate(-(cardX + at.cardW() / 2F), -(cardY + at.cardH() / 2F));
        }
        if(back)
        {
            DdBlitUtil.fullBlit(poseStack, texture, cardX, cardY, at.cardW(), at.cardH());
        }
        else
        {
            // The UV window is absolute now, not an offset and a span.
            DdBlitUtil.blit(poseStack, texture, cardX, cardY, at.cardW(), at.cardH(),
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
        }
        poseStack.pose().popMatrix();""",
    """        int drawX = cardX;
        int drawY = cardY;
        int drawW = at.cardW();
        int drawH = at.cardH();

        poseStack.pose().pushMatrix();
        if(defence)
        {
            // Turned a quarter about the cell's own middle, as a defending
            // card lies on the table. rotate() takes radians; the Forge
            // quaternion took degrees.
            poseStack.pose().translate(cardX + at.cardW() / 2F, cardY + at.cardH() / 2F);
            poseStack.pose().rotate((float)Math.toRadians(90D));
            poseStack.pose().translate(-(cardX + at.cardW() / 2F), -(cardY + at.cardH() / 2F));

            // A quarter turn swaps a quad's width and height, so the quad drawn
            // BEFORE the turn has to be the one whose TURNED footprint fits the
            // cell. Turning the full 62x90 card put a 90x62 picture on the same
            // centre: 14px past the cell on either side, over its neighbour's
            // edge and out through the panel. That is the invariant DdBlitUtil
            // spells out -- rotation and bounds agree only on a square region --
            // and it is why every other rotating caller squares up first.
            drawW = Math.round(at.cardW() * DuelTextures.CARD_ASPECT);   // turned HEIGHT
            drawH = at.cardW();                                          // turned WIDTH
            drawX = cardX + (at.cardW() - drawW) / 2;
            drawY = cardY + (at.cardH() - drawH) / 2;
        }
        if(back)
        {
            DdBlitUtil.fullBlit(poseStack, texture, drawX, drawY, drawW, drawH);
        }
        else
        {
            // The UV window is absolute now, not an offset and a span.
            DdBlitUtil.blit(poseStack, texture, drawX, drawY, drawW, drawH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
        }
        poseStack.pose().popMatrix();""", "D3 defence cell fits its cell")

# ---------- D4: the caption stops being cut in half ----------
sub(E, """        int gap = 6;
        int cardW = 62;""",
    """        int gap = 6;
        // A position choice is captioned by its ANSWER ("Face-up Attack"), not
        // by a card name, and 62px cut that to "Face-up Att" with no ellipsis.
        // Only this one kind pays for the extra width.
        int cardW = shownPrompt != null && shownPrompt.kind() == EnginePrompt.Kind.POSITION
            ? 78 : 62;""", "D4 wider position cell")

# ---------- D5: the empty footer earns its space ----------
sub(E, """            // Blank: the panel header already asks the question and each
            // cell is labelled with its answer. Saying it a third time was
            // noise in a panel that has room for none.
            case POSITION -> "";""",
    """            // Not a third "Choose a position" -- the header already asks and
            // each cell is labelled with its answer. The one thing the panel
            // never said is WHICH card is being placed, and the translator has
            // been putting that in every option's detail all along with nothing
            // drawing it. Any option will do; they are all the same card.
            case POSITION -> prompt.options().isEmpty() ? ""
                : prompt.options().get(0).detail();""", "D5 footer names the card")

# ---------- the same softlock in the canonical tree ----------
sub(FORGE, """            case CHOOSE -> answer(new int[] {index}, 0);""",
    """            // POSITION belongs here and was missing in BOTH trees: the
            // commit that gave position choices a picker never taught choose()
            // about them, so the click fell into the empty `default` below and
            // was dropped. The panel has no Confirm and no Cancel -- the prompt
            // is built non-cancelable -- so there was no other way to answer it.
            case CHOOSE, POSITION -> answer(new int[] {index}, 0);""",
    "FORGE tree softlock")

print("done")
