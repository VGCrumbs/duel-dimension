"""Turns on bilinear filtering for the large item textures.

Minecraft samples a GUI texture NEAREST unless its `.mcmeta` says otherwise. That
is right for the hub's furniture, which is drawn at its authored size and wants
crisp pixels -- and wrong for anything drawn SMALLER than it was authored, which
is every card face, pack icon and preview in this mod. A 512-pixel card art
sampled nearest into a 60-pixel shop tile keeps one pixel in eight and drops the
rest, which is the shimmer and the jagged edges.

`blur: true` is bilinear. `clamp: true` stops the sampler reaching past an edge
and wrapping around to the far side, which shows up as a one-pixel fringe of the
opposite edge's colour on art drawn through a UV window -- which is how every
card is drawn here.

Only the tiers a card is ever DOWNSCALED from get it: 128 and up. The 16 to 64
tiers are already at or below the size they are drawn at, and blurring those
would soften art that is currently sharp.

The sleeves already ship these files; this covers everything else, which is
where the pack icons live.
"""
import io
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
ITEM = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                    "textures", "item")

# Below 128 a texture is drawn at about its own size or larger, where nearest is
# the sharper choice; above it, it is always being reduced.
BLUR_FROM = 128

MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


# Textures that are always drawn much smaller than they are authored, and are
# GRADIENTS rather than pixel art -- so nearest sampling shows as banding rather
# than as crispness. The colour wheel is 192px drawn at about 112, and a mat is
# 1024x448 drawn into a 210-wide preview and onto the table.
SMOOTH = [
    ("gui", "settings", "colour_wheel.png"),
    ("gui", "settings", "value_slider.png"),
]


def smooth_folder(*parts):
    """Every png in a folder, for the mats, which are all the same case."""
    folder = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                          "textures", *parts)
    if not os.path.isdir(folder):
        return []
    return [parts + (name,) for name in sorted(os.listdir(folder))
            if name.endswith(".png")]


def main():
    written = 0
    for tier in sorted(os.listdir(ITEM)):
        folder = os.path.join(ITEM, tier)
        if not (tier.isdigit() and os.path.isdir(folder)):
            continue
        if int(tier) < BLUR_FROM:
            continue
        for name in os.listdir(folder):
            if not name.endswith(".png"):
                continue
            meta = os.path.join(folder, name + ".mcmeta")
            if os.path.exists(meta):
                continue
            io.open(meta, "w", encoding="utf-8", newline="\n").write(MCMETA)
            written += 1
        print("  %-5s %d png, %d mcmeta"
              % (tier, len([f for f in os.listdir(folder) if f.endswith(".png")]),
                 len([f for f in os.listdir(folder) if f.endswith(".mcmeta")])))
    # The deck cases too. They are 422x512 art drawn into a 30-unit shop tile,
    # which is the same downscale the card faces get and the same shimmer.
    hub = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                       "textures", "gui", "hub")
    if os.path.isdir(hub):
        for name in os.listdir(hub):
            if not name.startswith("deck_box_") or not name.endswith(".png"):
                continue
            meta = os.path.join(hub, name + ".mcmeta")
            if os.path.exists(meta):
                continue
            io.open(meta, "w", encoding="utf-8", newline="\n").write(MCMETA)
            written += 1
        print("  deck boxes covered")

    written += smooth()
    print("wrote %d new .mcmeta" % written)


def smooth():
    """Bilinear for the gradients: the picker, the sliders and every mat."""
    count = 0
    for parts in SMOOTH + smooth_folder("duel", "mats"):
        png = os.path.join(REPO, "shared", "resources", "assets", "dueldimension",
                           "textures", *parts)
        if not os.path.isfile(png):
            print("  missing %s" % os.path.join(*parts))
            continue
        meta = png + ".mcmeta"
        if os.path.exists(meta):
            continue
        io.open(meta, "w", encoding="utf-8", newline="\n").write(MCMETA)
        print("  smooth %s" % "/".join(parts))
        count += 1
    return count


if __name__ == "__main__":
    main()
