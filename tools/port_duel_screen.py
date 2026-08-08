"""The repeated conversions the duel screen cluster needs.

Everything here is a rename or a one-to-one substitution that appears many
times. Anything needing a judgement per site is deliberately left for the
compiler to point at.

    ms.pushPose()                       -> ms.pose().pushMatrix()
    ms.popPose()                        -> ms.pose().popMatrix()
    ms.translate(x, y, z)               -> ms.pose().translate(x, y)
    ms.mulPose(new Quaternion(0,0,r,b)) -> ms.pose().rotate(r)

The GUI matrix is 2D now (`Matrix3x2f`), which sounds like a loss and is not:
every one of these was a 2D transform written through a 3D API. A translate
always passed 0 for z, and every rotation was about Z, which is the only axis a
screen has. `rotate(float)` says that; `mulPose(new Quaternion(0, 0, r, false))`
said it in a way that needed reading twice.

    Widget                              -> Renderable
    ScreenUtil.white()                  -> nothing
    RenderSystem.enableDepthTest() etc  -> nothing

`Widget` was vanilla's "something that draws itself"; it is `Renderable` now.
The rest is immediate-mode state with no meaning in a retained-mode pass.
"""
import io
import os
import re
import sys

MATRIX = [
    (re.compile(r"\b(\w+)\.pushPose\(\)"), r"\1.pose().pushMatrix()"),
    (re.compile(r"\b(\w+)\.popPose\(\)"), r"\1.pose().popMatrix()"),
    # translate always passed 0 for z; the 2D matrix has no z to pass.
    (re.compile(r"\b(\w+)\.translate\(([^;]+?),\s*([^,;]+?),\s*[^,;]+?\)"),
     r"\1.pose().translate(\2, \3)"),
    # Every rotation here is about Z, which is the only axis a screen has.
    (re.compile(r"\b(\w+)\.mulPose\(new Quaternion\(\s*0\s*,\s*0\s*,\s*([^,]+?)\s*,[^)]*\)\)"),
     r"\1.pose().rotate(\2)"),
]

RENAMES = [
    (re.compile(r"\bimport net\.minecraft\.client\.gui\.components\.Widget;"),
     "import net.minecraft.client.gui.components.Renderable;"),
    (re.compile(r"(?<![.\w])Widget(?![\w.])"), "Renderable"),
]

DEAD = re.compile(
    r"^[ \t]*(?:ScreenUtil\.white\(\)|"
    r"(?:com\.mojang\.blaze3d\.systems\.)?RenderSystem\.(?:enableDepthTest|disableDepthTest"
    r"|enableBlend|disableBlend|defaultBlendFunc)\(\)|"
    r"GlStateManager\.[A-Za-z_]+\([^;]*\));[ \t]*\n", re.M)

DEAD_IMPORTS = re.compile(
    r"^import (?:com\.mojang\.math\.Quaternion|com\.mojang\.blaze3d\.platform\.GlStateManager)"
    r";[ \t]*\n", re.M)


def convert(path):
    src = io.open(path, encoding="utf-8").read()
    before = src

    for pattern, replacement in MATRIX:
        src = pattern.sub(replacement, src)
    # Only rename Widget where the Forge/vanilla one was actually imported;
    # ZoneWidget, InteractionWidget and friends must not be touched.
    if "components.Widget;" in before:
        for pattern, replacement in RENAMES:
            src = pattern.sub(replacement, src)
    src = DEAD.sub("", src)
    src = DEAD_IMPORTS.sub("", src)

    if src != before:
        io.open(path, "w", encoding="utf-8", newline="\n").write(src)
        return True
    return False


if __name__ == "__main__":
    changed = 0
    for root in sys.argv[1:]:
        if os.path.isfile(root):
            changed += 1 if convert(root) else 0
            continue
        for dirpath, _, names in os.walk(root):
            for name in names:
                if name.endswith(".java"):
                    changed += 1 if convert(os.path.join(dirpath, name)) else 0
    print("changed %d files" % changed)
