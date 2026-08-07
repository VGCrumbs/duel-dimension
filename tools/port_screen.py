"""Converts a Forge screen's drawing to 26.2's retained-mode GUI.

The GUI stopped being immediate-mode twice over. A screen used to take a
PoseStack and draw into it; now it takes a GuiGraphicsExtractor and describes
itself, and the game draws everything afterwards in one pass. Every call in
between changed name, shape, or both.

What is mechanical, and therefore here:

    render(PoseStack, ...)          -> extractRenderState(GuiGraphicsExtractor, ...)
    renderButton(PoseStack, ...)    -> extractContents(GuiGraphicsExtractor, ...)
    font.drawShadow(pose, s, x, y, c) -> graphics.text(font, s, x, y, c, true)
    font.draw(pose, s, x, y, c)       -> graphics.text(font, s, x, y, c, false)
    fill(pose, ...)                 -> graphics.fill(...)
    RenderSystem.enableScissor(...)  -> graphics.enableScissor(...)
    RenderSystem.setShader/enableBlend/setShaderTexture -> deleted
    widget.x / widget.y             -> getX() / getY()

What is NOT here, because it needs a decision per call site:

    RenderSystem.setShaderColor before a draw. There is no draw-time colour
    any more -- a tint is an argument to the blit itself -- so the colour has
    to be carried to whichever blit it was modifying. That is reading code,
    not matching a pattern, and every one is left for hand-porting.

    DdBlitUtil.blit, for the same reason: the texture used to be bound
    separately by setShaderTexture and is now an argument.

    python tools/port_screen.py <file>...
"""
import io
import re
import sys

# --- signatures -------------------------------------------------------------
SIGNATURES = [
    (re.compile(r"public void render\(PoseStack (\w+), int (\w+), int (\w+), float (\w+)\)"),
     r"public void extractRenderState(GuiGraphicsExtractor \1, int \2, int \3, float \4)"),
    (re.compile(r"public void renderButton\(PoseStack (\w+), int (\w+), int (\w+), float (\w+)\)"),
     r"protected void extractContents(GuiGraphicsExtractor \1, int \2, int \3, float \4)"),
    # Every other helper that was handed a PoseStack now takes the extractor.
    (re.compile(r"\bPoseStack (\w+)"), r"GuiGraphicsExtractor \1"),
]

# --- text -------------------------------------------------------------------
# drawShadow and draw differ only in whether the shadow is drawn, which the new
# call takes as its last argument. The x and y were floats and are ints now.
TEXT = [
    (re.compile(r"\bfont\.drawShadow\(\s*(\w+)\s*,\s*"), r"@@TEXT@@true@@\1@@"),
    (re.compile(r"\bfont\.draw\(\s*(\w+)\s*,\s*"), r"@@TEXT@@false@@\1@@"),
]

# --- input -----------------------------------------------------------------
# Mouse and keyboard events became records rather than loose arguments, which
# is a real improvement: a modifier key used to be a bare int that every
# handler re-decoded, and `hasShiftDown` was a static on Screen reaching for
# global state. Now the event carries it.
#
# The bodies keep their old parameter NAMES by unpacking the record on the
# first line, so nothing inside a handler has to change.
INPUT = [
    (re.compile(r"public boolean mouseClicked\(double (\w+), double (\w+), int (\w+)\)\s*\n(\s*)\{"),
     r"public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,\n"
     r"        boolean doubleClick)\n\4{\n"
     r"\4    double \1 = event.x();\n"
     r"\4    double \2 = event.y();\n"
     r"\4    int \3 = event.button();"),
    (re.compile(r"public boolean mouseReleased\(double (\w+), double (\w+), int (\w+)\)\s*\n(\s*)\{"),
     r"public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)\n\4{\n"
     r"\4    double \1 = event.x();\n"
     r"\4    double \2 = event.y();\n"
     r"\4    int \3 = event.button();"),
    (re.compile(r"public boolean mouseScrolled\(double (\w+), double (\w+), double (\w+)\)"),
     r"public boolean mouseScrolled(double \1, double \2, double scrollX, double \3)"),
    (re.compile(r"public boolean keyPressed\(int (\w+), int (\w+), int (\w+)\)\s*\n(\s*)\{"),
     r"public boolean keyPressed(net.minecraft.client.input.KeyEvent event)\n\4{\n"
     r"\4    int \1 = event.key();\n"
     r"\4    int \2 = event.scancode();\n"
     r"\4    int \3 = event.modifiers();"),
    (re.compile(r"public boolean charTyped\(char (\w+), int (\w+)\)\s*\n(\s*)\{"),
     r"public boolean charTyped(net.minecraft.client.input.CharacterEvent event)\n\3{\n"
     r"\3    char \1 = event.codepoint();\n"
     r"\3    int \2 = event.modifiers();"),
]

MISC = [
    # Delegating to a child widget passes the event on rather than the numbers.
    (re.compile(r"\.mouseClicked\((\w+), (\w+), (\w+)\)"), r".mouseClicked(event, false)"),
    (re.compile(r"\.mouseReleased\((\w+), (\w+), (\w+)\)"), r".mouseReleased(event)"),
    (re.compile(r"\.keyPressed\((\w+), (\w+), (\w+)\)"), r".keyPressed(event)"),
    (re.compile(r"\.charTyped\((\w+), (\w+)\)"), r".charTyped(event)"),
    # hasShiftDown was a static on Screen; it belongs to the event now.
    (re.compile(r"(?:Screen\.)?hasShiftDown\(\)"), r"event.hasShiftDown()"),
    # EditBox renamed its focus setter.
    (re.compile(r"\.setFocus\("), r".setFocused("),
    # super.render inside a screen is the extract now.
    (re.compile(r"super\.render\((\w+), (\w+), (\w+), (\w+)\)"),
     r"super.extractRenderState(\1, \2, \3, \4)"),
    (re.compile(r"\brenderBackground\((\w+)\)"), r"renderBackground(\1, 0, 0, 0F)"),
    # fill moved onto the extractor.
    (re.compile(r"(?<![.\w])fill\(\s*(\w+)\s*,\s*"), r"\1.fill("),
    # Scissors moved with it.
    (re.compile(r"RenderSystem\.enableScissor\("), r"@@SCISSOR@@("),
    (re.compile(r"RenderSystem\.disableScissor\(\)"), r"@@UNSCISSOR@@"),
    # Widget fields became accessors.
    (re.compile(r"(?<![.\w])\bthis\.x\b(?!\w)"), "getX()"),
    (re.compile(r"(?<![.\w])\bthis\.y\b(?!\w)"), "getY()"),
]

# Immediate-mode state that has no meaning in a retained-mode pass. Removing the
# line is correct: the renderer sets up its own pipeline per element now.
DEAD_LINES = re.compile(
    r"^[ \t]*(?:com\.mojang\.blaze3d\.systems\.)?RenderSystem\."
    r"(?:setShader|enableBlend|disableBlend|setShaderTexture|defaultBlendFunc)\([^;]*\);[ \t]*\n",
    re.M)


def fix_text_calls(src, graphics_name_default="graphics"):
    """Rewrites the marked text calls, moving the receiver into the argument."""
    out = []
    i = 0
    while True:
        at = src.find("@@TEXT@@", i)
        if at < 0:
            out.append(src[i:])
            return "".join(out)
        out.append(src[i:at])
        rest = src[at + len("@@TEXT@@"):]
        shadow, rest = rest.split("@@", 1)
        receiver, rest = rest.split("@@", 1)
        # Find the end of the call so the remaining arguments can be kept.
        depth = 1
        j = 0
        while j < len(rest) and depth > 0:
            if rest[j] == "(":
                depth += 1
            elif rest[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        args = rest[:j]
        out.append("%s.text(font, %s, %s)" % (receiver, args, shadow))
        i = at + len("@@TEXT@@") + len(shadow) + 2 + len(receiver) + 2 + j + 1
        src_len = len(src)
        if i > src_len:
            return "".join(out)


def convert(path):
    src = io.open(path, encoding="utf-8").read()

    for pattern, replacement in SIGNATURES:
        src = pattern.sub(replacement, src)
    for pattern, replacement in INPUT:
        src = pattern.sub(replacement, src)
    for pattern, replacement in TEXT:
        src = pattern.sub(replacement, src)
    for pattern, replacement in MISC:
        src = pattern.sub(replacement, src)
    src = DEAD_LINES.sub("", src)
    src = fix_text_calls(src)

    src = src.replace("import com.mojang.blaze3d.vertex.PoseStack;",
                      "import net.minecraft.client.gui.GuiGraphicsExtractor;")

    io.open(path, "w", encoding="utf-8", newline="\n").write(src)
    left = len(re.findall(r"setShaderColor|DdBlitUtil\.|@@SCISSOR@@|@@UNSCISSOR@@", src))
    print("%s: converted; %d sites left for hand-porting"
          % (path.replace("\\", "/").split("/")[-1], left))


if __name__ == "__main__":
    for target in sys.argv[1:]:
        convert(target)
